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

import com.alibaba.nacos.auth.config.AuthConfigs;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;

import static com.alibaba.nacos.prometheus.api.ApiConstants.PROMETHEUS_CONTROLLER_PATH;


/**
 * prometheus auth configuration, avoid spring security configuration override.
 *
 * @author vividfish
 */
@Configuration
@ConditionalOnProperty(name = "nacos.prometheus.metrics.enabled", havingValue = "true")
@Order(4)
public class PrometheusSecurityConfiguration extends WebSecurityConfigurerAdapter {
    
    private final AuthConfigs authConfigs;
    
    public PrometheusSecurityConfiguration(AuthConfigs authConfigs) {
        this.authConfigs = authConfigs;
    }
    
    @Override
    protected void configure(HttpSecurity http) throws Exception {
        if (!authConfigs.isAuthEnabled()) {
            http.authorizeRequests().antMatchers(PROMETHEUS_CONTROLLER_PATH + "/**").permitAll();
        } else {
            http.authorizeRequests().antMatchers(PROMETHEUS_CONTROLLER_PATH + "/**").authenticated().and().httpBasic();
        }
    }
}
