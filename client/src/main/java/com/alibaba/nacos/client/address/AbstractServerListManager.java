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

package com.alibaba.nacos.client.address;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.client.utils.LogUtils;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.alibaba.nacos.common.lifecycle.Closeable;
import com.alibaba.nacos.common.remote.client.ServerListFactory;
import com.alibaba.nacos.common.spi.NacosServiceLoader;
import com.alibaba.nacos.common.utils.StringUtils;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.List;

/**
 * Server list Manager.
 *
 * @author totalo
 */
public abstract class AbstractServerListManager implements ServerListFactory, Closeable {
    
    private static final Logger LOGGER = LogUtils.logger(AbstractServerListManager.class);
    
    protected ServerListProvider serverListProvider;
    
    private final NacosClientProperties properties;
    
    public AbstractServerListManager(NacosClientProperties properties) throws NacosException {
        this(properties, null);
    }
    
    public AbstractServerListManager(NacosClientProperties properties, String namespace) throws NacosException {
        if (null == properties) {
            throw new NacosException(NacosException.INVALID_PARAM, "properties is null");
        }
        if (StringUtils.isNotBlank(namespace)) {
            properties.setProperty(PropertyKeyConst.NAMESPACE, namespace);
        }
        properties.setProperty(PropertyKeyConst.CLIENT_MODULE_TYPE, getModuleName());
        this.properties = properties;
        Collection<ServerListProvider> serverListProviders = NacosServiceLoader.load(ServerListProvider.class);
        for (ServerListProvider each : serverListProviders) {
            if (each.match(properties)) {
                this.serverListProvider = each;
                break;
            }
        }
        if (null == serverListProvider) {
            throw new NacosException(NacosException.SERVER_ERROR, "no server list provider found");
        }
        this.serverListProvider.init(properties, getNacosRestTemplate());
    }
    
    @Override
    public List<String> getServerList() {
        return serverListProvider.getServerList();
    }
    
    @Override
    public void shutdown() throws NacosException {
        String className = this.getClass().getName();
        LOGGER.info("{} do shutdown begin", className);
        if (null != serverListProvider) {
            serverListProvider.shutdown();
        }
        serverListProvider = null;
        LOGGER.info("{} do shutdown stop", className);
    }
    
    public ServerListProvider getServerListProvider() {
        return serverListProvider;
    }
    
    public NacosClientProperties getProperties() {
        return properties;
    }
    
    public String getServerName() {
        return getModuleName() + "-" + serverListProvider.getServerName();
    }
    
    /**
     * get module name.
     * @return module name
     */
    public abstract String getModuleName();
    
    /**
     * get nacos rest template.
     * @return nacos rest template
     */
    public abstract NacosRestTemplate getNacosRestTemplate();
}
