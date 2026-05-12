/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.auth.impl.ldap;

import com.alibaba.nacos.plugin.auth.impl.authenticate.IAuthenticationManager;
import com.alibaba.nacos.plugin.auth.impl.authenticate.MissingLdapAuthenticationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * LDAP missing dependency configuration.
 *
 * @author xiweng.yy
 */
@Configuration(proxyBeanMethods = false)
public class LdapDependencyMissingConfiguration {
    
    private static final Logger LOGGER =
        LoggerFactory.getLogger(LdapDependencyMissingConfiguration.class);
    
    @Bean(name = LdapPluginDependencyChecker.LDAP_AUTHENTICATION_MANAGER_BEAN_NAME)
    public IAuthenticationManager ldapAuthenticatoinManager() {
        String message = LdapPluginDependencyChecker.buildMissingDependencyMessage();
        LOGGER.warn(message);
        return new MissingLdapAuthenticationManager(message);
    }
}
