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
package com.alibaba.nacos.core.auth;

import com.alibaba.nacos.core.env.ReloadableConfigs;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Component;

/**
 * Auth related configurations
 *
 * @author nkorange
 * @since 1.2.0
 */
@Component
@Configuration
public class AuthConfigs {

    @Autowired
    private ReloadableConfigs reloadableConfigs;

    /**
     * secret key
     */
    @Value("${nacos.core.auth.default.token.secret.key:}")
    private String secretKey;

    /**
     * Token validity time(seconds)
     */
    @Value("${nacos.core.auth.default.token.expire.seconds:1800}")
    private long tokenValidityInSeconds;

    /**
     * Which auth system is in use
     */
    @Value("${nacos.core.auth.system.type:}")
    private String nacosAuthSystemType;

    public String getSecretKey() {
        return secretKey;
    }

    public long getTokenValidityInSeconds() {
        return tokenValidityInSeconds;
    }

    public String getNacosAuthSystemType() {
        return nacosAuthSystemType;
    }

    public boolean isAuthEnabled() {
        // Runtime -D parameter has higher priority:
        String enabled = System.getProperty("nacos.core.auth.enabled");
        if (StringUtils.isNotBlank(enabled)) {
            return BooleanUtils.toBoolean(enabled);
        }
        return BooleanUtils.toBoolean(reloadableConfigs.getProperties()
            .getProperty("nacos.core.auth.enabled", "false"));
    }

    public boolean isCachingEnabled() {
        return BooleanUtils.toBoolean(reloadableConfigs.getProperties()
            .getProperty("nacos.core.auth.caching.enabled", "true"));
    }

    @Bean
    public FilterRegistrationBean authFilterRegistration() {
        FilterRegistrationBean<AuthFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(authFilter());
        registration.addUrlPatterns("/*");
        registration.setName("authFilter");
        registration.setOrder(6);

        return registration;
    }

    @Bean
    public AuthFilter authFilter() {
        return new AuthFilter();
    }

}
