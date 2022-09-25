package org.gentle.deploy.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Stack;
import java.util.concurrent.TimeUnit;

/**
 * @author xiangqian
 * @date 12:32 2022/09/25
 */
@Slf4j
@Component
public class ServerManager implements ApplicationRunner {

    private final int MAX_STACK = 2;
    private Stack<ServerAddr> stack;

    @Autowired
    private ThreadExecutor threadExecutor;

    @Autowired
    private RouteService routeService;

    @PostConstruct
    public void init() {
        stack = new Stack<>();
    }

    public synchronized List<ServerAddr> list() {
        return stack;
    }

    public synchronized boolean add(ServerAddr serverAddr) {
        // 栈已超过规定大小，清理栈中不可达服务；若是都可达，则清理栈底元素
        if (stack.size() >= MAX_STACK) {
            int count = stack.size() - MAX_STACK;
            while (count-- >= 0) {
                int removeIndex = -1;
                for (int i = 0, size = stack.size(); i < size; i++) {
                    if (!isReachable(stack.get(i))) {
                        removeIndex = i;
                        break;
                    }
                }

                if (removeIndex == -1) {
                    removeIndex = 0;
                }
                ServerAddr removeServerAddr = stack.remove(removeIndex);
                log.info("栈已超过规定大小，清理栈中不可达服务；若是都可达，则清理栈底元素: [{}] -> {}", removeIndex, removeServerAddr);
            }
        }

        // push
        stack.push(serverAddr);

        // 刷新路由定义
        refreshRouteDefinition();

        return true;
    }

    public synchronized ServerAddr get() {
        if (stack.isEmpty()) {
            return null;
        }

        return stack.peek();
    }

    /**
     * 检测栈顶服务是否可达
     */
    public synchronized boolean checkStackTopReachable() {
        // 判断栈是否为空
        if (stack.isEmpty()) {
            return false;
        }

        // 窥视栈顶元素
        ServerAddr serverAddr = stack.peek();
        log.debug("检测栈顶服务({})是否可达 ...", serverAddr);
        if (isReachable(serverAddr)) {
            log.debug("栈顶服务({})可达", serverAddr);
            return true;
        }

        // 弹出栈顶元素
        log.debug("栈顶服务不可达，将移除不可达的栈顶服务: {}", stack.pop());

        // 继续检测栈顶服务是否可达
        checkStackTopReachable();

        return false;
    }

    /**
     * 刷新路由定义
     *
     * @throws URISyntaxException
     */
    private void refreshRouteDefinition() {
        ServerAddr serverAddr = get();
        if (Objects.isNull(serverAddr)) {
            return;
        }

        URI routeUri = null;
        try {
            routeUri = new URI(String.format("http://%s:%s%s", serverAddr.getHost(), serverAddr.getPort(), StringUtils.trimToEmpty(serverAddr.getPath())));
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        RouteDefinition routeDefinition = routeService.createRouteDefinition(routeUri, "/**", null);
        threadExecutor.execute(() -> routeService.saveAndOverwriteIfExists(routeDefinition));
    }

    public boolean isReachable(ServerAddr serverAddr) {
        return isReachable(serverAddr.getHost(), serverAddr.getPort(), 30, TimeUnit.SECONDS);
    }

    /**
     * host:port 是否可达
     *
     * @param host
     * @param port
     * @param timeout
     * @param timeUnit
     * @return
     */
    public boolean isReachable(String host, int port, long timeout, TimeUnit timeUnit) {
        Socket socket = null;
        try {
            socket = new Socket();
            InetSocketAddress address = new InetSocketAddress(host, port);
            socket.connect(address, (int) timeUnit.toMillis(timeout));
            return socket.isConnected();
        } catch (Exception e) {
//            log.error("", e);
            return false;
        } finally {
            IOUtils.closeQuietly(socket);
        }
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        Runnable runnable = () -> {
            while (true) {
                try {
                    TimeUnit.SECONDS.sleep(10);
                    if (!checkStackTopReachable()) {
                        refreshRouteDefinition();
                    }
                } catch (Exception e) {
                    log.error("", e);
                }
            }
        };
        new Thread(runnable).start();
    }

}
