/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.console.config;

import com.alibaba.nacos.console.filter.XssFilter;
import com.alibaba.nacos.core.code.ControllerMethodsCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.PropertySource;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import javax.annotation.PostConstruct;
import java.time.ZoneId;
import java.util.Set;

/**
 * Console config.
 *
 * @author yshen
 * @author nkorange
 * @since 1.2.0
 */
@Component
@EnableScheduling
@PropertySource("/application.properties")
public class ConsoleConfig {
    
    @Autowired
    private ControllerMethodsCache methodsCache;
    
    @Value("${nacos.console.ui.enabled:true}")
    private boolean consoleUiEnabled;
    
    /**
     * Init.
     */
    @PostConstruct
    public void init() {
        final String graalEnv = "org.graalvm.nativeimage.imagecode";
        final boolean isGraalEnv = System.getProperty(graalEnv) != null;
        if (isGraalEnv) {
            initAotPlatform();
        } else {
            initJavaPlatform();
        }
    }
    
    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        config.addAllowedHeader("*");
        config.setMaxAge(18000L);
        config.addAllowedMethod("*");
        config.addAllowedOriginPattern("*");
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
    
    @Bean
    public XssFilter xssFilter() {
        return new XssFilter();
    }
    
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonObjectMapperCustomization() {
        return jacksonObjectMapperBuilder -> jacksonObjectMapperBuilder.timeZone(ZoneId.systemDefault().toString());
    }
    
    public boolean isConsoleUiEnabled() {
        return consoleUiEnabled;
    }
    
    private void initJavaPlatform() {
        methodsCache.initClassMethod("com.alibaba.nacos.core.controller");
        methodsCache.initClassMethod("com.alibaba.nacos.naming.controllers");
        methodsCache.initClassMethod("com.alibaba.nacos.config.server.controller");
        methodsCache.initClassMethod("com.alibaba.nacos.console.controller");
    }
    
    private void initAotPlatform() {
        final Set<Class<?>> classList = Set.of(
                com.alibaba.nacos.core.controller.v2.NacosClusterControllerV2.class,
                com.alibaba.nacos.core.controller.ServerLoaderController.class,
                com.alibaba.nacos.core.controller.CoreOpsController.class,
                com.alibaba.nacos.core.controller.NacosClusterController.class,
                com.alibaba.nacos.core.controller.v2.CoreOpsV2Controller.class,
                com.alibaba.nacos.naming.controllers.CatalogController.class,
                com.alibaba.nacos.naming.controllers.OperatorController.class,
                com.alibaba.nacos.naming.controllers.v2.ServiceControllerV2.class,
                com.alibaba.nacos.naming.controllers.v2.CatalogControllerV2.class,
                com.alibaba.nacos.naming.controllers.ClusterController.class,
                com.alibaba.nacos.naming.controllers.HealthController.class,
                com.alibaba.nacos.naming.controllers.v2.HealthControllerV2.class,
                com.alibaba.nacos.naming.controllers.InstanceController.class,
                com.alibaba.nacos.naming.controllers.v2.InstanceControllerV2.class,
                com.alibaba.nacos.naming.controllers.v2.OperatorControllerV2.class,
                com.alibaba.nacos.naming.controllers.v2.ClientInfoControllerV2.class,
                com.alibaba.nacos.naming.controllers.ServiceController.class,
                com.alibaba.nacos.config.server.controller.HistoryController.class,
                com.alibaba.nacos.config.server.controller.v2.HistoryControllerV2.class,
                com.alibaba.nacos.config.server.controller.CommunicationController.class,
                com.alibaba.nacos.config.server.controller.ListenerController.class,
                com.alibaba.nacos.config.server.controller.HealthController.class,
                com.alibaba.nacos.config.server.controller.ConfigController.class,
                com.alibaba.nacos.config.server.controller.CapacityController.class,
                com.alibaba.nacos.config.server.controller.ClientMetricsController.class,
                com.alibaba.nacos.config.server.controller.ConfigOpsController.class,
                com.alibaba.nacos.config.server.controller.v2.ConfigControllerV2.class,
                com.alibaba.nacos.console.controller.HealthController.class,
                com.alibaba.nacos.console.controller.ServerStateController.class,
                com.alibaba.nacos.console.controller.v2.HealthControllerV2.class,
                com.alibaba.nacos.console.controller.NamespaceController.class,
                com.alibaba.nacos.console.controller.v2.NamespaceControllerV2.class,
                com.alibaba.nacos.plugin.auth.impl.controller.UserController.class,
                com.alibaba.nacos.plugin.auth.impl.controller.PermissionController.class,
                com.alibaba.nacos.plugin.auth.impl.controller.RoleController.class
        );
        methodsCache.initClassMethod(classList);
    }
}
