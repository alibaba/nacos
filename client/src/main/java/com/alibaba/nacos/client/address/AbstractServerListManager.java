/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
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
import com.alibaba.nacos.api.SystemPropertyKeyConst;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.naming.utils.InitUtils;
import com.alibaba.nacos.client.utils.ContextPathUtil;
import com.alibaba.nacos.client.utils.ParamUtil;
import com.alibaba.nacos.client.utils.TemplateUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.address.common.AddressProperties;
import com.alibaba.nacos.plugin.address.exception.AddressException;
import com.alibaba.nacos.plugin.address.spi.AddressPlugin;
import com.alibaba.nacos.plugin.address.spi.AddressPluginManager;

import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Abstract ServerListManager
 * Date 2022/8/30.
 *
 * @author GuoJiangFu
 */
public abstract class AbstractServerListManager implements ServerListManager {
    
    private final AtomicInteger currentIndex = new AtomicInteger();
    
    protected AddressPlugin addressPlugin;
    
    private static final String PROPERTY_ADDRESS_PLUGIN = "property-address-plugin";
    
    private static final String ENDPOINT_ADDRESS_PLUGIN = "address-server";
    
    private volatile boolean started = false;
    
    public AbstractServerListManager(Properties properties) throws NacosException {
        this.initAddressPluginProperties(properties);
        this.initAddressPlugin(properties);
        this.initAddressPluginListener();
    }
    
    @Override
    public void start() throws NacosException {
        if (addressPlugin == null) {
            throw new NacosException(NacosException.SERVER_ERROR,
                    "Address plugin can not be null");
        }
        if (!started) {
            addressPlugin.start();
            this.started = true;
        }
    }
    
    @Override
    public List<String> getServerList() {
        return addressPlugin.getServerList();
    }
    
    @Override
    public String getCurrentServer() {
        return getServerList().get(currentIndex.get() % getServerList().size());
    }
    
    @Override
    public String getNextServer() {
        int index = currentIndex.incrementAndGet() % getServerList().size();
        return getServerList().get(index);
    }
    
    @Override
    public void shutdown() {
        if (this.addressPlugin != null) {
            this.addressPlugin.shutdown();
        }
    }
    
    private String getAddressServerUrl(Properties properties) {
    
        String endpoint = properties.getProperty(PropertyKeyConst.ENDPOINT);
    
        // Whether to enable domain name resolution rules
        String isUseEndpointRuleParsing = properties.getProperty(PropertyKeyConst.IS_USE_ENDPOINT_PARSING_RULE,
                System.getProperty(SystemPropertyKeyConst.IS_USE_ENDPOINT_PARSING_RULE,
                        String.valueOf(ParamUtil.USE_ENDPOINT_PARSING_RULE_DEFAULT_VALUE)));
        if (Boolean.parseBoolean(isUseEndpointRuleParsing)) {
            endpoint = ParamUtil.parsingEndpointRule(endpoint);
        }
        if (StringUtils.isBlank(endpoint)) {
            return null;
        }
        
        String contentPath = properties.getProperty(PropertyKeyConst.CONTEXT_PATH);
        String serverListName = properties.getProperty(PropertyKeyConst.CLUSTER_NAME);
        
        contentPath = StringUtils.isBlank(contentPath) ? ParamUtil.getDefaultContextPath() : contentPath;
        serverListName = StringUtils.isBlank(serverListName) ? ParamUtil.getDefaultNodesPath() : serverListName;
    
        String endpointPort = TemplateUtils
                .stringEmptyAndThenExecute(System.getenv(PropertyKeyConst.SystemEnv.ALIBABA_ALIWARE_ENDPOINT_PORT),
                        () -> properties.getProperty(PropertyKeyConst.ENDPOINT_PORT));
        StringBuilder addressServerUrl = new StringBuilder(
                String.format("http://%s:%s%s/%s", endpoint, endpointPort,
                        ContextPathUtil.normalizeContextPath(contentPath), serverListName));
        
        String namespace = properties.getProperty(PropertyKeyConst.NAMESPACE);
        boolean hasQueryString = false;
        if (StringUtils.isNotBlank(namespace)) {
            addressServerUrl.append("?namespace=" + namespace);
            hasQueryString = true;
        }
        if (properties.containsKey(PropertyKeyConst.ENDPOINT_QUERY_PARAMS)) {
            addressServerUrl
                    .append(hasQueryString ? "&" : "?" + properties.get(PropertyKeyConst.ENDPOINT_QUERY_PARAMS));
            
        }
        return addressServerUrl.toString();
    }
    
    private void initAddressPluginProperties(Properties properties) {
        String addressServerUrl = getAddressServerUrl(properties);
        if (!StringUtils.isBlank(addressServerUrl)) {
            AddressProperties.setProperties("addressServerUrl", addressServerUrl);
        }
    
        String serverAddrsStr = properties.getProperty(PropertyKeyConst.SERVER_ADDR);
        if (!StringUtils.isBlank(serverAddrsStr)) {
            AddressProperties.setProperties("serverAddressStr", serverAddrsStr);
        }
        
        for (String key : properties.stringPropertyNames()) {
            if (key.startsWith("addressPlugin")) {
                AddressProperties.setProperties(key, properties.getProperty(key));
            }
        }
    }
    
    private void initAddressPlugin(Properties properties) throws NacosException {
        
        String addressPluginName = properties.getProperty("addressPluginName");
        String endpoint = InitUtils.initEndpoint(properties);
        String serverAddrsStr = properties.getProperty(PropertyKeyConst.SERVER_ADDR);
        Optional<AddressPlugin> addressPluginTemp = Optional.empty();
        if (StringUtils.isNotEmpty(addressPluginName)) {
            addressPluginTemp = AddressPluginManager.getInstance().findAuthServiceSpiImpl(addressPluginName);
        } else if (StringUtils.isNotEmpty(endpoint)) {
            addressPluginTemp = AddressPluginManager.getInstance()
                    .findAuthServiceSpiImpl(ENDPOINT_ADDRESS_PLUGIN);
        } else if (StringUtils.isNotEmpty(serverAddrsStr)) {
            addressPluginTemp = AddressPluginManager.getInstance()
                    .findAuthServiceSpiImpl(PROPERTY_ADDRESS_PLUGIN);
        }
        if (addressPluginTemp.isPresent()) {
            this.addressPlugin = addressPluginTemp.get();
            this.addressPlugin.start();
        } else {
            throw new NacosException(NacosException.SERVER_ERROR, "Can not get address plugin");
        }
        
    }
    
    protected abstract void initAddressPluginListener() throws AddressException;
    
}
