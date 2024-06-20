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

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.alibaba.nacos.common.lifecycle.Closeable;
import com.alibaba.nacos.common.remote.client.ServerListFactory;
import com.alibaba.nacos.plugin.auth.api.LoginIdentityContext;
import com.alibaba.nacos.plugin.auth.api.RequestResource;
import com.alibaba.nacos.plugin.auth.spi.client.ClientAuthPluginManager;
import com.alibaba.nacos.plugin.auth.spi.client.ClientAuthService;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Security proxy to update security information.
 *
 * @author nkorange
 * @since 1.2.0
 */
public class SecurityProxy implements Closeable {
    
    private ClientAuthPluginManager clientAuthPluginManager;

    private ServerListFactory serverListFactory;
    
    /**
     * Construct from serverList, nacosRestTemplate, init client auth plugin.
     * // TODO change server list to serverListManager after serverListManager refactor and unite.
     *
     * @param serverListFactory a server list factory.
     * @Param nacosRestTemplate http request template.
     */
    public SecurityProxy(ServerListFactory serverListFactory, NacosRestTemplate nacosRestTemplate) {
        clientAuthPluginManager = new ClientAuthPluginManager();
        this.serverListFactory = serverListFactory;
        clientAuthPluginManager.init(serverListFactory.getServerList(), nacosRestTemplate);
    }
    
    /**
     * Login all available ClientAuthService instance.
     *
     * @param properties login identity information.
     */
    public void login(Properties properties) {
        if (clientAuthPluginManager.getAuthServiceSpiImplSet().isEmpty()) {
            return;
        }
        for (ClientAuthService clientAuthService : clientAuthPluginManager.getAuthServiceSpiImplSet()) {
            clientAuthService.login(properties);
        }
    }

    /**
     * refresh auth plugin server list.
     */
    public void refreshServerList() {
        clientAuthPluginManager.refreshServerList(serverListFactory.getServerList());
    }
    
    /**
     * get the context of all nacosRestTemplate instance.
     *
     * @return a combination of all context.
     */
    public Map<String, String> getIdentityContext(RequestResource resource) {
        Map<String, String> header = new HashMap<>(1);
        for (ClientAuthService clientAuthService : clientAuthPluginManager.getAuthServiceSpiImplSet()) {
            LoginIdentityContext loginIdentityContext = clientAuthService.getLoginIdentityContext(resource);
            for (String key : loginIdentityContext.getAllKey()) {
                header.put(key, loginIdentityContext.getParameter(key));
            }
        }
        return header;
    }
    
    @Override
    public void shutdown() throws NacosException {
        clientAuthPluginManager.shutdown();
    }
}
