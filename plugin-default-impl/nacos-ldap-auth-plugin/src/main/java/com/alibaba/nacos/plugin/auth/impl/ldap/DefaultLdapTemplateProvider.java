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

import org.springframework.ldap.core.LdapTemplate;

/**
 * Lazily builds an LDAP template from the latest accepted plugin configuration.
 *
 * @author Nacos
 */
public class DefaultLdapTemplateProvider implements LdapTemplateProvider {
    
    private final LdapAuthPluginConfigProvider configProvider;
    
    private volatile TemplateSnapshot snapshot;
    
    public DefaultLdapTemplateProvider(LdapAuthPluginConfigProvider configProvider) {
        this.configProvider = configProvider;
    }
    
    @Override
    public LdapTemplate getLdapTemplate() {
        LdapAuthPluginConfig config = configProvider.getConfig();
        TemplateSnapshot current = snapshot;
        if (current == null || current.config != config) {
            synchronized (this) {
                current = snapshot;
                if (current == null || current.config != config) {
                    current = new TemplateSnapshot(config, createLdapTemplate(config));
                    snapshot = current;
                }
            }
        }
        return current.ldapTemplate;
    }
    
    private LdapTemplate createLdapTemplate(LdapAuthPluginConfig config) {
        NacosLdapContextSource contextSource = new NacosLdapContextSource(config.getUrl(),
            config.getBaseDn(), config.getUserDn(), config.getPassword(),
            Long.toString(config.getTimeout()));
        LdapTemplate result = new LdapTemplate(contextSource);
        result.setIgnorePartialResultException(config.isIgnorePartialResultException());
        return result;
    }
    
    private static final class TemplateSnapshot {
        
        private final LdapAuthPluginConfig config;
        
        private final LdapTemplate ldapTemplate;
        
        private TemplateSnapshot(LdapAuthPluginConfig config, LdapTemplate ldapTemplate) {
            this.config = config;
            this.ldapTemplate = ldapTemplate;
        }
    }
}
