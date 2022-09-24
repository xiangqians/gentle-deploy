package org.gentle.deploy.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author xiangqian
 * @date 20:57:10 2022/03/20
 */
@Slf4j
@Component
public class ReqGlobalFilter implements GlobalFilter, Ordered, ApplicationListener<ContextClosedEvent> {

    @Value("${server.secret}")
    private String secret;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RouteService routeService;

    private ScheduledThreadPoolExecutor scheduledThreadPoolExecutor;

    @PostConstruct
    public void init() {
        scheduledThreadPoolExecutor = new ScheduledThreadPoolExecutor(2);
    }

    @SneakyThrows
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        URI uri = request.getURI();
        String rawPath = uri.getRawPath();
        if (rawPath.equals("/_api") || rawPath.startsWith("/_api/")) {
            return _api(exchange);
        }

        // 执行到下一个filter
        return chain.filter(exchange);
    }

    private Mono<Void> _api(ServerWebExchange exchange) throws Exception {
        ServerHttpRequest request = exchange.getRequest();
        URI uri = request.getURI();
        String rawPath = uri.getRawPath();

        // http://localhost:9999/_api?secret=3a5f0c4a-3bc7-11ed-911e-0242ac110002
        if (StringUtils.equalsAny(rawPath, "/_api", "/_api/")) {
            URL url = ReqGlobalFilter.class.getClassLoader().getResource("static/api.html");
            byte[] body = IOUtils.toString(url).getBytes(StandardCharsets.UTF_8);
            return response(exchange.getResponse(), HttpStatus.OK, body);
        }

        // org.springframework.web.HttpRequestMethodNotSupportedException: Request method 'GET' not supported

        MultiValueMap<String, String> queryParams = request.getQueryParams();
        String secret = Optional.ofNullable(queryParams.get("secret")).filter(CollectionUtils::isNotEmpty).map(list -> StringUtils.trim(list.get(0))).orElse(null);
        if (!this.secret.equals(secret)) {
            return response(exchange.getResponse(), HttpStatus.UNAUTHORIZED, null);
        }

        // http://localhost:9999/_api/server/register?secret=3a5f0c4a-3bc7-11ed-911e-0242ac110002
        if (StringUtils.equalsAny(rawPath, "/_api/server/register", "/_api/server/register/")) {
            String host = Optional.ofNullable(queryParams.get("host")).filter(CollectionUtils::isNotEmpty).map(list -> StringUtils.trim(list.get(0))).orElse(null);
            Integer port = Optional.ofNullable(queryParams.get("port")).filter(CollectionUtils::isNotEmpty).map(list -> NumberUtils.toInt(StringUtils.trim(list.get(0)), -1)).orElse(null);
            if (Objects.nonNull(port) && port == -1) {
                return response(exchange.getResponse(), HttpStatus.OK, "Failure".getBytes(StandardCharsets.UTF_8));
            }

            if (StringUtils.isEmpty(host)) {
                ReqAddr reqAddr = ReqAddr.get(request);
                host = reqAddr.getHost();
            }
            URI routeUri = new URI(String.format("http://%s:%s", host, port));
            log.debug("{} -> {}", rawPath, routeUri);
            RouteDefinition routeDefinition = routeService.createRouteDefinition(routeUri, "/**", null);
            scheduledThreadPoolExecutor.schedule(() -> routeService.saveAndOverwriteIfExists(routeDefinition), 2, TimeUnit.SECONDS);
            return response(exchange.getResponse(), HttpStatus.OK, "Success".getBytes(StandardCharsets.UTF_8));
        }

        // http://localhost:9999/_api/server/list?secret=3a5f0c4a-3bc7-11ed-911e-0242ac110002
        if (StringUtils.equalsAny(rawPath, "/_api/server/list", "/_api/server/list/")) {
        }

        // http://localhost:9999/_api/routes?secret=3a5f0c4a-3bc7-11ed-911e-0242ac110002
        if (StringUtils.equalsAny(rawPath, "/_api/routes", "/_api/routes/")) {
            // java.lang.IllegalStateException: Iterating over a toIterable() / toStream() is blocking, which is not supported in thread reactor-http-nio-3
//            List<RouteDefinition> routeDefinitions = routeService.list();
            ScheduledFuture<List<RouteDefinition>> scheduledFuture = scheduledThreadPoolExecutor.schedule(() -> routeService.list(), 0, TimeUnit.SECONDS);
            List<RouteDefinition> routeDefinitions = scheduledFuture.get();
            return response(exchange.getResponse(), HttpStatus.OK, objectMapper.writeValueAsBytes(routeDefinitions));
        }

        return response(exchange.getResponse(), HttpStatus.NOT_FOUND, null);
    }

    private Mono<Void> response(ServerHttpResponse response, HttpStatus httpStatus, byte[] body) {
        response.setStatusCode(httpStatus);
        if (ArrayUtils.isEmpty(body)) {
            return response.setComplete();
        }

        // warp
        DataBufferFactory dataBufferFactory = response.bufferFactory();
        DataBuffer dataBuffer = dataBufferFactory.wrap(body);
        return response.writeWith(Mono.fromSupplier(() -> dataBuffer));
    }

    @Override
    public int getOrder() {
        return -Integer.MAX_VALUE;
    }

    @Override
    public void onApplicationEvent(ContextClosedEvent event) {
        if (Objects.nonNull(scheduledThreadPoolExecutor)) {
            scheduledThreadPoolExecutor.shutdown();
            scheduledThreadPoolExecutor = null;
        }
    }

}
