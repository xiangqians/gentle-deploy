package org.gentle.deploy.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 路由服务
 *
 * @author xiangqian
 * @date 10:26:14 2022/03/19
 */
@Slf4j
@Service
public class RouteService implements ApplicationEventPublisherAware, ApplicationRunner {

    @Autowired
    private RouteDefinitionRepository routeDefinitionRepository;

    private ApplicationEventPublisher applicationEventPublisher;

    @Override
    public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    public synchronized List<RouteDefinition> list() {
        Flux<RouteDefinition> flux = routeDefinitionRepository.getRouteDefinitions();
        return flux.toStream().collect(Collectors.toList());
    }

    public synchronized Map<String, RouteDefinition> map() {
        Flux<RouteDefinition> flux = routeDefinitionRepository.getRouteDefinitions();
        return flux.toStream().collect(Collectors.toMap(RouteDefinition::getId, routeDefinition -> routeDefinition));
    }

    public synchronized void delete(String... ids) {
        if (ArrayUtils.isEmpty(ids)) {
            return;
        }

        // delete
        StringBuilder messageBuilder = new StringBuilder();
        messageBuilder.setLength(0);
        Map<String, RouteDefinition> routeDefinitionMap = map();
        for (String id : ids) {
            RouteDefinition routeDefinition = routeDefinitionMap.get(id);
            if (routeDefinition != null) {
                routeDefinitionRepository.delete(Mono.just(id));
                messageBuilder.append('\n').append('\t').append("[-] ").append(routeDefinition);
            }
        }

        // publish event ?
        if (messageBuilder.length() > 0) {
            applicationEventPublisher.publishEvent(new RefreshRoutesEvent(this));
            log.info("已发布删除路由事件:{}\n", messageBuilder);
            printRouteInfo();
        }
    }

    public synchronized void saveAndOverwriteIfExists(RouteDefinition... routeDefinitions) {
        if (ArrayUtils.isEmpty(routeDefinitions)) {
            return;
        }

        // save
        StringBuilder messageBuilder = new StringBuilder();
        messageBuilder.setLength(0);
        Map<String, RouteDefinition> routeDefinitionMap = map();
        for (RouteDefinition routeDefinition : routeDefinitions) {
            RouteDefinition routeDefinitionOld = routeDefinitionMap.get(routeDefinition.getId());
            // save
            if (Objects.isNull(routeDefinitionOld)) {
                routeDefinitionRepository.save(Mono.just(routeDefinition)).subscribe();
                messageBuilder.append('\n').append('\t').append("[ +] ").append(routeDefinition);
                continue;
            }

            // delete & save
            if (!routeDefinition.equals(routeDefinitionOld)) {
                routeDefinitionRepository.delete(Mono.just(routeDefinition.getId()));
                routeDefinitionRepository.save(Mono.just(routeDefinition)).subscribe();
                messageBuilder.append('\n').append('\t').append("[-+] ").append(routeDefinition);
                continue;
            }
        }

        // publish event ?
        if (messageBuilder.length() > 0) {
            applicationEventPublisher.publishEvent(new RefreshRoutesEvent(this));
            log.info("已发布新增路由事件:{}\n", messageBuilder);
            printRouteInfo();
        }
    }

    public void printRouteInfo() {
        StringBuilder messageBuilder = new StringBuilder();
        List<RouteDefinition> routeDefinitionList = list();
        for (RouteDefinition routeDefinition : routeDefinitionList) {
            messageBuilder.append('\n').append('\t').append(routeDefinition);
        }
        log.info("已配置的路由列表：{}\n", messageBuilder);
    }

    public RouteDefinition createRouteDefinition(URI uri, String path, Integer stripPrefix) throws URISyntaxException {
        return createRouteDefinition("test", uri, path, stripPrefix);
    }

    /**
     * 创建路由定义
     *
     * @param id
     * @param uri
     * @param path
     * @param stripPrefix
     * @return
     * @throws URISyntaxException
     */
    private RouteDefinition createRouteDefinition(String id, URI uri, String path, Integer stripPrefix) throws URISyntaxException {
        // RouteDefinition
        RouteDefinition routeDefinition = new RouteDefinition();

        // id
        routeDefinition.setId(id);

        // uri，要代理的服务
        routeDefinition.setUri(uri);

        // predicates
        PredicateDefinition predicateDefinition = new PredicateDefinition();
        // 设置转发路径
        predicateDefinition.setName("Path");
        predicateDefinition.setArgs(Map.of("_genkey_0", path));
        routeDefinition.setPredicates(List.of(predicateDefinition));

        // filters
        if (Objects.nonNull(stripPrefix)) {
            FilterDefinition filterDefinition = new FilterDefinition();
            // StripPrefix的作用是去掉前缀的，值即对应层数
            filterDefinition.setName("StripPrefix");
            filterDefinition.setArgs(Map.of("_genkey_0", String.valueOf(stripPrefix)));
            routeDefinition.setFilters(List.of(filterDefinition));
        }

        //spring:
        //  cloud:
        //    gateway:
        //      routes:
        //        - id: EXAMPLE-SERVICE
        //          uri: http://example:8080
        //          predicates:
        //            - Path=/example/**
        //          filters:
        //            - StripPrefix=1

        return routeDefinition;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        RouteDefinition routeDefinition = createRouteDefinition(new URI("http://fr.rpmfind.net/linux/"), "/**", null);
        saveAndOverwriteIfExists(routeDefinition);
    }

}
