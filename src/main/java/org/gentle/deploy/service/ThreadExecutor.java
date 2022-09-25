package org.gentle.deploy.service;

import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Objects;
import java.util.concurrent.*;

/**
 * @author xiangqian
 * @date 13:15 2022/09/25
 */
@Component
public class ThreadExecutor implements ApplicationListener<ContextClosedEvent> {

    private ThreadPoolExecutor threadPoolExecutor;

    @PostConstruct
    public void init() {
        int corePoolSize = 2;
        int maximumPoolSize = 4;
        long keepAliveTime = 5;
        TimeUnit unit = TimeUnit.MINUTES;
        BlockingQueue<Runnable> workQueue = new ArrayBlockingQueue<>(16);
        ThreadFactory threadFactory = Executors.defaultThreadFactory();
        RejectedExecutionHandler handler = new ThreadPoolExecutor.AbortPolicy();
        threadPoolExecutor = new ThreadPoolExecutor(corePoolSize,
                maximumPoolSize,
                keepAliveTime,
                unit,
                workQueue,
                threadFactory,
                handler);
    }

    public void execute(Runnable command) {
        threadPoolExecutor.execute(command);
    }

    public <T> Future<T> submit(Callable<T> task) {
        return threadPoolExecutor.submit(task);
    }

    @Override
    public void onApplicationEvent(ContextClosedEvent event) {
        if (Objects.nonNull(threadPoolExecutor)) {
            threadPoolExecutor.shutdown();
            threadPoolExecutor = null;
        }
    }

}
