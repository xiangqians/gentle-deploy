package org.gentle.deploy.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.server.reactive.ServerHttpRequest;

import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.function.Function;

/**
 * @author xiangqian
 * @date 20:36 2022/09/24
 */
@Data
@Slf4j
@AllArgsConstructor
public class ReqAddr {

    private String host;
    private Integer port;

    public static ReqAddr get(ServerHttpRequest request) {
        Function<String, String> function = headerName -> {
            String headerValue = request.getHeaders().getFirst(headerName);
            log.debug("header--> {}: {}", headerName, headerValue);
            return StringUtils.isEmpty(headerValue) || "unknown".equalsIgnoreCase(headerValue) ? null : headerValue;
        };

        String[] headerNames = {"X-Forwarded-For",  // Squid服务代理
                "Proxy-Client-IP",  // apache服务代理
                "WL-Proxy-Client-IP", // weblogic服务代理
                "HTTP_CLIENT_IP", // 有些代理服务器
                "X-Real-IP", // nginx服务代理
        };

        String host = null;
        for (String headerName : headerNames) {
            if (Objects.nonNull(host = function.apply(headerName))) {
                break;
            }
        }

        // 有些网络通过多层代理，那么获取到的ip就会有多个，一般都是通过逗号（,）分割开来，并且第一个ip为客户端的真实IP
        if (Objects.nonNull(host)) {
            log.debug("多层代理ip: {}", host);
            host = host.split(",")[0];
        }
        // 如果没有转发的ip，则取当前通信的请求端的ip
        else {
            InetSocketAddress inetSocketAddress = request.getRemoteAddress();
            host = inetSocketAddress.getAddress().getHostAddress();
            log.debug("没有转发的ip，取当前通信的请求端的ip: {}", host);
        }

        // 0:0:0:0:0:0:0:1
        if (StringUtils.equalsAny(host, "0:0:0:0:0:0:0:1", "[0:0:0:0:0:0:0:1]")) {
            host = "127.0.0.1";
        }

        // 如果是127.0.0.1，则取本地真实ip
        // ...

        // port
        int port = request.getRemoteAddress().getPort();

        return new ReqAddr(host, port);
    }

}
