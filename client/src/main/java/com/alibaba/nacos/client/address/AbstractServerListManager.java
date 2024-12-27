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
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.client.utils.LogUtils;
import com.alibaba.nacos.common.JustForTest;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.alibaba.nacos.common.lifecycle.Closeable;
import com.alibaba.nacos.common.remote.client.ServerListFactory;
import com.alibaba.nacos.common.spi.NacosServiceLoader;
import com.alibaba.nacos.common.utils.StringUtils;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Server list Manager.
 *
 * @author totalo
 */
public abstract class AbstractServerListManager implements ServerListFactory, Closeable {
    
    private static final Logger LOGGER = LogUtils.logger(AbstractServerListManager.class);
    
    protected ServerListProvider serverListProvider;
    
    protected NacosClientProperties properties;
    
    public AbstractServerListManager(NacosClientProperties properties) {
        this(properties, null);
    }
    
    public AbstractServerListManager(NacosClientProperties properties, String namespace) {
        // To avoid set operation affect the original properties.
        NacosClientProperties tmpProperties = properties.derive();
        if (StringUtils.isNotBlank(namespace)) {
            tmpProperties.setProperty(PropertyKeyConst.NAMESPACE, namespace);
        }
        tmpProperties.setProperty(Constants.CLIENT_MODULE_TYPE, getModuleName());
        this.properties = tmpProperties;
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
    
    /**
     * Start server list manager.
     *
     * @throws NacosException during start and initialize.
     */
    public void start() throws NacosException {
        Collection<ServerListProvider> serverListProviders = NacosServiceLoader.load(ServerListProvider.class);
        Collection<ServerListProvider> sorted = serverListProviders.stream()
                .sorted((a, b) -> b.getOrder() - a.getOrder()).collect(Collectors.toList());
        for (ServerListProvider each : sorted) {
            boolean matchResult = each.match(properties);
            LOGGER.info("Load and match ServerListProvider {}, match result: {}", each.getClass().getCanonicalName(),
                    matchResult);
            if (matchResult) {
                this.serverListProvider = each;
                LOGGER.info("Will use {} as ServerListProvider", this.serverListProvider.getClass().getCanonicalName());
                break;
            }
        }
        if (null == serverListProvider) {
            LOGGER.error("No server list provider found, SPI load size: {}", sorted.size());
            throw new NacosException(NacosException.CLIENT_INVALID_PARAM, "No server list provider found.");
        }
        this.serverListProvider.init(properties, getNacosRestTemplate());
    }
    
    public String getServerName() {
        return getModuleName() + "-" + serverListProvider.getServerName();
    }
    
    public String getContextPath() {
        return serverListProvider.getContextPath();
    }
    
    public String getNamespace() {
        return serverListProvider.getNamespace();
    }
    
    public String getAddressSource() {
        return serverListProvider.getAddressSource();
    }
    
    public boolean isFixed() {
        return serverListProvider.isFixed();
    }
    
    /**
     * get module name.
     *
     * @return module name
     */
    protected abstract String getModuleName();
    
    /**
     * get nacos rest template.
     *
     * @return nacos rest template
     */
    protected abstract NacosRestTemplate getNacosRestTemplate();
    
    @JustForTest
    NacosClientProperties getProperties() {
        return properties;
    }
}
