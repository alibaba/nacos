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
import com.alibaba.nacos.client.naming.utils.CollectionUtils;
import com.alibaba.nacos.client.utils.ContextPathUtil;
import com.alibaba.nacos.client.utils.LogUtils;
import com.alibaba.nacos.client.utils.ParamUtil;
import com.alibaba.nacos.client.utils.TemplateUtils;
import com.alibaba.nacos.common.executor.NameThreadFactory;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.lifecycle.Closeable;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.remote.client.ServerListFactory;
import com.alibaba.nacos.common.spi.NacosServiceLoader;
import com.alibaba.nacos.common.utils.InternetAddressUtil;
import com.alibaba.nacos.common.utils.IoUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.common.utils.ThreadUtils;
import org.slf4j.Logger;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.StringTokenizer;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static com.alibaba.nacos.common.constant.RequestUrlConstants.HTTPS_PREFIX;
import static com.alibaba.nacos.common.constant.RequestUrlConstants.HTTP_PREFIX;

/**
 * Server list Manager.
 *
 * @author totalo
 */
public abstract class AbstractServerListManager implements ServerListFactory, Closeable {
    
    private static final Logger LOGGER = LogUtils.logger(AbstractServerListManager.class);
    
    private final ScheduledExecutorService refreshServerListExecutor =
            new ScheduledThreadPoolExecutor(1, new NameThreadFactory("com.alibaba.nacos.client.ServerListManager"));
    
    private final NacosRestTemplate nacosRestTemplate = ServerListHttpClientManager.getInstance().getNacosRestTemplate();
    
    private ServerListProvider serverListProvider;
    
    private String serverListProviderType;
    
    private final long refreshServerListInternal = TimeUnit.SECONDS.toMillis(30);
    
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
    
    protected volatile boolean isStarted = false;
    
    private final int initServerListRetryTimes = 5;
    
    private long lastServerListRefreshTime = 0L;
    
    {
        Collection<ServerListProvider> serviceLoader = NacosServiceLoader.load(ServerListProvider.class);
        for (ServerListProvider each : serviceLoader) {
            if (Objects.equals(each.type(), serverListProviderType)) {
                serverListProvider = each;
                break;
            }
        }
    }
    
    public AbstractServerListManager(NacosClientProperties properties) throws NacosException {
        initParam(properties);
        if (StringUtils.isNotBlank(endpoint)) {
            return;
        }
        if (StringUtils.isBlank(serverAddrsStr)) {
            throw new NacosException(NacosException.CLIENT_INVALID_PARAM, "endpoint and serverAddr is blank");
        }
        this.isFixed = true;
        StringTokenizer serverAddrsTokens = new StringTokenizer(this.serverAddrsStr, ",;");
        while (serverAddrsTokens.hasMoreTokens()) {
            String serverAddr = serverAddrsTokens.nextToken().trim();
            if (serverAddr.startsWith(HTTP_PREFIX) || serverAddr.startsWith(HTTPS_PREFIX)) {
                this.serverList.add(serverAddr);
            } else {
                String[] serverAddrArr = InternetAddressUtil.splitIPPortStr(serverAddr);
                if (serverAddrArr.length == 1) {
                    this.serverList
                            .add(serverAddrArr[0] + InternetAddressUtil.IP_PORT_SPLITER + ParamUtil.getDefaultServerPort());
                } else {
                    this.serverList.add(serverAddr);
                }
            }
        }
    }
    
    public AbstractServerListManager(NacosClientProperties properties, String namespace) throws NacosException {
        this(properties);
        if (StringUtils.isNotBlank(namespace)) {
            this.namespace = namespace;
        }
    }
    
    /**
     * Start.
     *
     * @throws NacosException nacos exception
     */
    public synchronized void start() throws NacosException {
        
        if (isStarted || isFixed()) {
            return;
        }
        
        for (int i = 0; i < initServerListRetryTimes && getServerList().isEmpty(); ++i) {
            refreshServerListIfNeed();
            if (!getServerList().isEmpty()) {
                break;
            }
            try {
                this.wait((i + 1) * 100L);
            } catch (Exception e) {
                LOGGER.warn("get serverlist fail,url: {}", addressServerUrl);
            }
        }
        
        if (getServerList().isEmpty()) {
            LOGGER.error("[init-serverlist] fail to get NACOS-server serverlist! url: {}", addressServerUrl);
            throw new NacosException(NacosException.SERVER_ERROR,
                    "fail to get NACOS-server serverlist! not connnect url:" + addressServerUrl);
        }
        
        // executor schedules the timer task
        this.refreshServerListExecutor.scheduleWithFixedDelay(this::refreshServerListIfNeed, 0L, 30L, TimeUnit.SECONDS);
        this.isStarted = true;
    }
    
    @Override
    public List<String> getServerList() {
        if (null != serverListProvider) {
            return serverListProvider.getServerList();
        }
        return serverList.isEmpty() ? serversFromEndpoint : serverList;
    }
    
    @Override
    public void shutdown() throws NacosException {
        String className = this.getClass().getName();
        LOGGER.info("{} do shutdown begin", className);
        ThreadUtils.shutdownThreadPool(refreshServerListExecutor, LOGGER);
        if (isStarted) {
            isStarted = false;
        }
        ServerListHttpClientManager.getInstance().shutdown();
        LOGGER.info("{} do shutdown stop", className);
    }
    
    private void refreshServerListIfNeed() {
        try {
            if (!CollectionUtils.isEmpty(serverList)) {
                LOGGER.debug("server list provided by user: " + serverList);
                return;
            }
            if (System.currentTimeMillis() - lastServerListRefreshTime < refreshServerListInternal) {
                return;
            }
            List<String> list = getServerListFromEndpoint();
            if (CollectionUtils.isEmpty(list)) {
                throw new Exception("Can not acquire Nacos list");
            }
            if (null == serversFromEndpoint || !CollectionUtils.isEqualCollection(list, serversFromEndpoint)) {
                LOGGER.info("[SERVER-LIST] server list is updated: {}", list);
                serversFromEndpoint = list;
                lastServerListRefreshTime = System.currentTimeMillis();
                NotifyCenter.publishEvent(new ServerListChangeEvent());
            }
        } catch (Throwable e) {
            LOGGER.warn("failed to update server list", e);
        }
    }
    
    private List<String> getServerListFromEndpoint() {
        try {
            HttpRestResult<String> httpResult = nacosRestTemplate.get(addressServerUrl, Header.EMPTY, Query.EMPTY, String.class);
            
            if (!httpResult.ok()) {
                LOGGER.error("[check-serverlist] error. addressServerUrl: {}, code: {}", addressServerUrl,
                        httpResult.getCode());
                return null;
            }
            List<String> lines = IoUtils.readLines(new StringReader(httpResult.getData()));
            List<String> result = new ArrayList<>(lines.size());
            for (String serverAddr : lines) {
                if (StringUtils.isBlank(serverAddr)) {
                    continue;
                }
                String[] ipPort = InternetAddressUtil.splitIPPortStr(serverAddr.trim());
                String ip = ipPort[0].trim();
                if (ipPort.length == 1) {
                    result.add(ip + InternetAddressUtil.IP_PORT_SPLITER + ParamUtil.getDefaultServerPort());
                } else {
                    result.add(serverAddr);
                }
            }
            return result;
        } catch (Exception e) {
            LOGGER.error("[check-serverlist] exception. url: {}", addressServerUrl, e);
            return null;
        }
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
        initServerListProviderType(properties);
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
    
    private void initServerListProviderType(NacosClientProperties properties) {
        this.serverListProviderType = properties.getProperty(PropertyKeyConst.SEVER_LIST_PROVIDER_TYPE);
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
        String serverListNameTmp = properties.getProperty(PropertyKeyConst.ENDPOINT_CLUSTER_NAME,
                properties.getProperty(PropertyKeyConst.CLUSTER_NAME));
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
            endpointTmp = endpointUrl;
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
}
