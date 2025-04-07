/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.client.naming.remote;

import com.alibaba.nacos.plugin.auth.api.RequestResource;
import com.alibaba.nacos.client.address.ServerListChangeEvent;
import com.alibaba.nacos.client.security.SecurityProxy;
import com.alibaba.nacos.client.utils.AppNameUtils;
import com.alibaba.nacos.common.notify.listener.Subscriber;

import java.util.HashMap;
import java.util.Map;

/**
 * Abstract Naming client proxy.
 *
 * @author xiweng.yy
 */
public abstract class AbstractNamingClientProxy extends Subscriber<ServerListChangeEvent>
        implements NamingClientProxy {
    
    private static final String APP_FILED = "app";
    
    private final SecurityProxy securityProxy;
    
    protected AbstractNamingClientProxy(SecurityProxy securityProxy) {
        this.securityProxy = securityProxy;
    }
    
    protected Map<String, String> getSecurityHeaders(String namespace, String group, String serviceName) {
        RequestResource resource = RequestResource.namingBuilder().setNamespace(namespace).setGroup(group)
                .setResource(serviceName).build();
        Map<String, String> result = this.securityProxy.getIdentityContext(resource);
        result.putAll(getAppHeaders());
        return result;
    }
    
    protected Map<String, String> getAppHeaders() {
        Map<String, String> result = new HashMap<>(1);
        result.put(APP_FILED, AppNameUtils.getAppName());
        return result;
    }
    
    protected void reLogin() {
        securityProxy.reLogin();
    }
}
