/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.client.security;

import com.alibaba.nacos.client.auth.ClientAuthPluginManager;
import com.alibaba.nacos.client.auth.ClientAuthService;
import com.alibaba.nacos.client.auth.LoginIdentityContext;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;

import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * Security proxy to update security information.
 *
 * @author nkorange
 * @since 1.2.0
 */
public class SecurityProxy {
    
    /**
     * ClientAuthPlugin instance set.
     */
    private final Set<ClientAuthService> clientAuthServiceHashSet;
    
    /**
     * Construct from serverList, nacosRestTemplate, init client auth plugin.
     *
     * @param serverList a server list that client request to.
     * @Param nacosRestTemplate http request template.
     */
    public SecurityProxy(List<String> serverList, NacosRestTemplate nacosRestTemplate) {
        ClientAuthPluginManager clientAuthPluginManager = new ClientAuthPluginManager();
        clientAuthPluginManager.init(serverList, nacosRestTemplate);
        clientAuthServiceHashSet = clientAuthPluginManager.getAuthServiceSpiImplSet();
    }
    
    /**
     * Login all available ClientAuthService instance.
     *
     * @param properties login identity information.
     * @return if there are any available clientAuthService instances.
     */
    public boolean loginClientAuthService(Properties properties) {
        if (clientAuthServiceHashSet.isEmpty()) {
            return false;
        }
        for (ClientAuthService clientAuthService : clientAuthServiceHashSet) {
            clientAuthService.login(properties);
        }
        return true;
    }
    
    /**
     * get the context of all nacosRestTemplate instance.
     *
     * @return a combination of all context.
     */
    public LoginIdentityContext getLoginIdentityContext() {
        LoginIdentityContext allContext = new LoginIdentityContext();
        for (ClientAuthService clientAuthService : clientAuthServiceHashSet) {
            LoginIdentityContext loginIdentityContext = clientAuthService.getLoginIdentityContext();
            if (loginIdentityContext == null) {
                continue;
            }
            for (String key : loginIdentityContext.getAllKey()) {
                allContext.setParameter(key, loginIdentityContext.getParameter(key));
            }
        }
        return allContext;
    }
    
}
