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

package com.alibaba.nacos.prometheus.conf;

import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.web.NacosWebBean;
import com.alibaba.nacos.plugin.auth.constant.Constants;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

import static com.alibaba.nacos.prometheus.api.ApiConstants.PROMETHEUS_CONTROLLER_NAMESPACE_PATH;
import static com.alibaba.nacos.prometheus.api.ApiConstants.PROMETHEUS_CONTROLLER_PATH;
import static com.alibaba.nacos.prometheus.api.ApiConstants.PROMETHEUS_CONTROLLER_SERVICE_PATH;

/**
 * prometheus auth configuration, avoid spring security configuration override.
 *
 * @author vividfish
 */
@Configuration
@NacosWebBean
@ConditionalOnProperty(name = "nacos.prometheus.metrics.enabled", havingValue = "true")
public class PrometheusSecurityConfiguration {
    
    @Bean
    @Conditional(ConditionOnNoAuthPluginType.class)
    public SecurityFilterChain prometheusSecurityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(
                (authorizeHttpRequests) -> authorizeHttpRequests.requestMatchers(PROMETHEUS_CONTROLLER_PATH,
                        PROMETHEUS_CONTROLLER_NAMESPACE_PATH, PROMETHEUS_CONTROLLER_SERVICE_PATH).permitAll());
        return http.getOrBuild();
    }
    
    private static class ConditionOnNoAuthPluginType implements Condition {
        
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            String nacosAuthSystemType = context.getEnvironment()
                    .getProperty(Constants.Auth.NACOS_CORE_AUTH_SYSTEM_TYPE, "");
            return StringUtils.isBlank(nacosAuthSystemType);
        }
    }
}
