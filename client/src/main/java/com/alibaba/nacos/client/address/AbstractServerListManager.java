/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.client.utils.ContextPathUtil;
import com.alibaba.nacos.client.utils.LogUtils;
import com.alibaba.nacos.client.utils.ParamUtil;
import com.alibaba.nacos.client.utils.TemplateUtils;
import com.alibaba.nacos.common.lifecycle.Closeable;
import com.alibaba.nacos.common.remote.client.ServerListFactory;
import com.alibaba.nacos.common.spi.NacosServiceLoader;
import com.alibaba.nacos.common.utils.InternetAddressUtil;
import com.alibaba.nacos.common.utils.StringUtils;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.StringTokenizer;

import static com.alibaba.nacos.common.constant.RequestUrlConstants.HTTPS_PREFIX;
import static com.alibaba.nacos.common.constant.RequestUrlConstants.HTTP_PREFIX;

/**
 * Serverlist Manager.
 *
 * @author totalo
 */
public abstract class AbstractServerListManager implements ServerListFactory, Closeable {
    
    private static final Logger LOGGER = LogUtils.logger(AbstractServerListManager.class);
    
    private ServerListProvider serverListProvider;
    
    protected String namespace = "";
    
    protected String endpoint;
    
    protected int endpointPort = 8080;
    
    protected String endpointContextPath;
    
    protected String contextPath = ParamUtil.getDefaultContextPath();
    
    protected String serverListName = ParamUtil.getDefaultNodesPath();
    
    protected final List<String> serverList = new ArrayList<>();
    
    protected volatile List<String> serversFromEndpoint = new ArrayList<>();
    
    protected String addressServerUrl;
    
    protected String serverAddrsStr;
    
    protected boolean isFixed;
    
    {
        Collection<ServerListProvider> serviceLoader = NacosServiceLoader.load(ServerListProvider.class);
        for (ServerListProvider each : serviceLoader) {
            this.serverListProvider = each;
            break;
        }
    }
    
    public AbstractServerListManager() {
        this.isFixed = false;
    }
    
    public AbstractServerListManager(List<String> fixed) {
        this(fixed, null);
    }
    
    public AbstractServerListManager(List<String> fixed, String namespace) {
        this.isFixed = true;
        for (String serverAddr : fixed) {
            String[] serverAddrArr = InternetAddressUtil.splitIPPortStr(serverAddr);
            if (serverAddrArr.length == 1) {
                this.serverList
                        .add(serverAddrArr[0] + InternetAddressUtil.IP_PORT_SPLITER + ParamUtil.getDefaultServerPort());
            } else {
                this.serverList.add(serverAddr);
            }
        }
        if (StringUtils.isNotBlank(namespace)) {
            this.namespace = namespace;
        }
    }
    
    public AbstractServerListManager(String host, int port) {
        this.isFixed = false;
        this.endpoint = host;
        this.endpointPort = port;
        initAddressServerUrl(null);
    }
    
    public AbstractServerListManager(String endpoint) throws NacosException {
        this(endpoint, null);
    }
    
    public AbstractServerListManager(String endpoint, String namespace) throws NacosException {
        this.isFixed = false;
        if (StringUtils.isBlank(endpoint)) {
            throw new NacosException(NacosException.CLIENT_INVALID_PARAM, "endpoint is blank");
        }
        Properties properties = new Properties();
        properties.setProperty(PropertyKeyConst.ENDPOINT, endpoint);
        final NacosClientProperties clientProperties = NacosClientProperties.PROTOTYPE.derive(properties);
        initParam(clientProperties);
        if (StringUtils.isNotBlank(namespace)) {
            this.namespace = namespace;
        }
    }
    
    public AbstractServerListManager(NacosClientProperties properties) throws NacosException {
        initParam(properties);
        // 若这个不为空，说明是固定的地址
        if (StringUtils.isNotEmpty(serverAddrsStr)) {
            this.isFixed = true;
            StringTokenizer serverAddrsTokens = new StringTokenizer(this.serverAddrsStr, ",;");
            while (serverAddrsTokens.hasMoreTokens()) {
                String serverAddr = serverAddrsTokens.nextToken().trim();
                if (serverAddr.startsWith(HTTP_PREFIX) || serverAddr.startsWith(HTTPS_PREFIX)) {
                    this.serverList.add(serverAddr);
                } else {
                    String[] serverAddrArr = InternetAddressUtil.splitIPPortStr(serverAddr);
                    if (serverAddrArr.length == 1) {
                        this.serverList.add(serverAddrArr[0] + InternetAddressUtil.IP_PORT_SPLITER + ParamUtil
                                .getDefaultServerPort());
                    } else {
                        this.serverList.add(serverAddr);
                    }
                }
            }
        } else {
            if (StringUtils.isBlank(endpoint)) {
                throw new NacosException(NacosException.CLIENT_INVALID_PARAM, "endpoint is blank");
            }
        }
    }
    
    public AbstractServerListManager(NacosClientProperties properties, String namespace) {
        initParam(properties);
        if (StringUtils.isNotBlank(namespace)) {
            this.namespace = namespace;
        }
    }
    
    private void initNameSpace(NacosClientProperties properties) {
        String namespace = properties.getProperty(PropertyKeyConst.NAMESPACE);
        if (StringUtils.isNotBlank(namespace)) {
            this.namespace = namespace;
        }
    }
    
    private void initServerAddr(NacosClientProperties properties) {
        this.serverAddrsStr = properties.getProperty(PropertyKeyConst.SERVER_ADDR);
    }
    
    private void initAddressServerUrl(NacosClientProperties properties) {
        if (isFixed) {
            return;
        }
        String contextPathTem = StringUtils.isNotBlank(this.endpointContextPath) ? ContextPathUtil.normalizeContextPath(
                this.endpointContextPath) : ContextPathUtil.normalizeContextPath(this.contextPath);
        StringBuilder addressServerUrlTem = new StringBuilder(
                String.format("http://%s:%d%s/%s", this.endpoint, this.endpointPort, contextPathTem,
                        this.serverListName));
        boolean hasQueryString = false;
        if (StringUtils.isNotBlank(namespace)) {
            addressServerUrlTem.append("?namespace=").append(namespace);
            hasQueryString = true;
        }
        if (properties != null && properties.containsKey(PropertyKeyConst.ENDPOINT_QUERY_PARAMS)) {
            addressServerUrlTem.append(
                    hasQueryString ? "&" : "?" + properties.getProperty(PropertyKeyConst.ENDPOINT_QUERY_PARAMS));
            
        }
        this.addressServerUrl = addressServerUrlTem.toString();
        LOGGER.info("address server url = {}", this.addressServerUrl);
    }
    
    private void initParam(NacosClientProperties properties) {
        initServerAddr(properties);
        initNameSpace(properties);
        initEndpoint(properties);
        initEndpointPort(properties);
        initEndpointContextPath(properties);
        initContextPath(properties);
        initServerListName(properties);
        initAddressServerUrl(properties);
    }
    
    private void initEndpointContextPath(NacosClientProperties properties) {
        String endpointContextPathTmp = TemplateUtils.stringEmptyAndThenExecute(
                properties.getProperty(PropertyKeyConst.SystemEnv.ALIBABA_ALIWARE_ENDPOINT_CONTEXT_PATH),
                () -> properties.getProperty(PropertyKeyConst.ENDPOINT_CONTEXT_PATH));
        if (StringUtils.isNotBlank(endpointContextPathTmp)) {
            this.endpointContextPath = endpointContextPathTmp;
        }
    }
    
    private void initEndpointPort(NacosClientProperties properties) {
        String endpointPortTmp = TemplateUtils.stringEmptyAndThenExecute(
                properties.getProperty(PropertyKeyConst.SystemEnv.ALIBABA_ALIWARE_ENDPOINT_PORT),
                () -> properties.getProperty(PropertyKeyConst.ENDPOINT_PORT));
        if (StringUtils.isNotBlank(endpointPortTmp)) {
            this.endpointPort = Integer.parseInt(endpointPortTmp);
        }
    }
    
    private void initServerListName(NacosClientProperties properties) {
        String serverListNameTmp = properties.getProperty(PropertyKeyConst.CLUSTER_NAME);
        if (!StringUtils.isBlank(serverListNameTmp)) {
            this.serverListName = serverListNameTmp;
        }
    }
    
    private void initContextPath(NacosClientProperties properties) {
        String contentPathTmp = properties.getProperty(PropertyKeyConst.CONTEXT_PATH);
        if (!StringUtils.isBlank(contentPathTmp)) {
            this.contextPath = contentPathTmp;
        }
    }
    
    private void initEndpoint(final NacosClientProperties properties) {
        String endpointTmp = properties.getProperty(PropertyKeyConst.ENDPOINT);
        // Whether to enable domain name resolution rules
        String isUseEndpointRuleParsing = properties.getProperty(PropertyKeyConst.IS_USE_ENDPOINT_PARSING_RULE,
                properties.getProperty(SystemPropertyKeyConst.IS_USE_ENDPOINT_PARSING_RULE,
                        String.valueOf(ParamUtil.USE_ENDPOINT_PARSING_RULE_DEFAULT_VALUE)));
        if (Boolean.parseBoolean(isUseEndpointRuleParsing)) {
            String endpointUrl = ParamUtil.parsingEndpointRule(endpointTmp);
            if (StringUtils.isNotBlank(endpointUrl)) {
                this.serverAddrsStr = "";
            }
            this.endpoint = endpointUrl;
        }
        this.endpoint = StringUtils.isNotBlank(endpointTmp) ? endpointTmp : "";
    }
    
    public String getContentPath() {
        return contextPath;
    }
    
    public String getNamespace() {
        return namespace;
    }
    
    public boolean isFixed() {
        return isFixed;
    }
    
    public String getEndpoint() {
        return endpoint;
    }
    
    public int getEndpointPort() {
        return endpointPort;
    }
    
    public String getEndpointContextPath() {
        return endpointContextPath;
    }
    
    public String getServerListName() {
        return serverListName;
    }
    
    public List<String> getServersFromEndpoint() {
        return serversFromEndpoint;
    }
    
    public String getAddressServerUrl() {
        return addressServerUrl;
    }
    
    public String getServerAddrsStr() {
        return serverAddrsStr;
    }
    
    @Override
    public List<String> getServerList() {
        if (null != serverListProvider) {
            return serverListProvider.getServerList();
        }
        return serverList.isEmpty() ? serversFromEndpoint : serverList;
    }
}
