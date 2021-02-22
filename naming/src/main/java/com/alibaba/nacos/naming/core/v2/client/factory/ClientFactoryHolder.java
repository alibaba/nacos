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

package com.alibaba.nacos.naming.core.v2.client.factory;

import com.alibaba.nacos.common.spi.NacosServiceLoader;
import com.alibaba.nacos.naming.constants.ClientConstants;
import com.alibaba.nacos.naming.misc.Loggers;

import java.util.Collection;
import java.util.HashMap;

/**
 * Client factory holder.
 *
 * @author xiweng.yy
 */
public class ClientFactoryHolder {
    
    private static final ClientFactoryHolder INSTANCE = new ClientFactoryHolder();
    
    private static final HashMap<String, ClientFactory> CLIENT_FACTORIES = new HashMap<>(4);
    
    private ClientFactoryHolder() {
        Collection<ClientFactory> clientFactories = NacosServiceLoader.load(ClientFactory.class);
        for (ClientFactory each : clientFactories) {
            if (CLIENT_FACTORIES.containsKey(each.getType())) {
                Loggers.SRV_LOG.warn("Client type {} found multiple factory, use {} default", each.getType(), each.getClass().getCanonicalName());
            }
            CLIENT_FACTORIES.put(each.getType(), each);
        }
    }
    
    public static ClientFactoryHolder getInstance() {
        return INSTANCE;
    }
    
    /**
     * Find target type {@link ClientFactory}.
     *
     * @param type target type
     * @return target type {@link ClientFactory}, if not fount, return 'default' client factory.
     */
    public ClientFactory findClientFactory(String type) {
        if (!CLIENT_FACTORIES.containsKey(type)) {
            return CLIENT_FACTORIES.get(ClientConstants.DEFAULT_FACTORY);
        }
        return CLIENT_FACTORIES.get(type);
    }
}
