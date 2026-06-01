/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.auth.impl.oidc.config;

import com.alibaba.nacos.plugin.auth.impl.oidc.controller.OidcLoginController;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Spring auto-configuration for OIDC authentication plugin.
 * Registers the OidcLoginController and security configuration when OIDC auth is enabled.
 *
 * @author WangzJi
 */
@Configuration
@ConditionalOnProperty(name = "nacos.core.auth.system.type", havingValue = "oidc")
@Import(OidcWebSecurityConfig.class)
@SuppressWarnings("PMD")
public class OidcPluginAutoConfiguration {
    
    /**
     * Register OidcLoginController bean.
     *
     * @return OidcLoginController
     */
    @Bean
    public OidcLoginController oidcLoginController() {
        return new OidcLoginController();
    }
}
