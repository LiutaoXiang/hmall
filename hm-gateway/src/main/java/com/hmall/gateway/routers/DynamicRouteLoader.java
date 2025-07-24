package com.hmall.gateway.routers;


import cn.hutool.json.JSONUtil;
import com.alibaba.cloud.nacos.NacosConfigManager;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionWriter;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;

@Slf4j
@Component
@RequiredArgsConstructor
public class DynamicRouteLoader { //动态路由加载器

    private final NacosConfigManager nacosConfigManager;

    private final RouteDefinitionWriter writer;

    private final String dataId = "gateway-routes.json";

    private final String group = "DEFAULT_GROUP";

    private final Set<String> routeIds = new HashSet<>();

    @PostConstruct // 项目启动时就执行  项目启动时加载这个类的Bean, 这个注解是在Bean初始化之后就执行
    public void initRouteConfigListener() throws NacosException {
        log.info("初始化路由配置监听器");
        // 项目启动时，先拉取一次配置，并添加配置监听器
        String configInfo = nacosConfigManager.getConfigService()
                .getConfigAndSignListener(dataId, group, 5000, new Listener() {

                    @Override
                    public Executor getExecutor() {
                        return null;
                    }

                    @Override
                    public void receiveConfigInfo(String configInfo) {
                        //监听到配置变更，需要取更新路由表
                        updateConfigInfo(configInfo);
                    }
                });
        //第一次获取配置，需要更新到路由表
        updateConfigInfo(configInfo);
    }

    public void updateConfigInfo(String configInfo){
        log.debug("更新路由表，配置信息:{}", configInfo);
        //解析配置文件，转为RouteDefinition
        List<RouteDefinition> routeDefinitions = JSONUtil.toList(configInfo, RouteDefinition.class);
        //删除旧的路由表
        for (String routeId : routeIds) {
            writer.delete(Mono.just(routeId)).subscribe();
        }
        routeIds.clear();

        //更新路由表
        for (RouteDefinition routeDefinition : routeDefinitions) {
            //更新路由表
            writer.save(Mono.just(routeDefinition)).subscribe();
            //记录路由id便于下一次删除
            routeIds.add(routeDefinition.getId());
        }
    }

}
