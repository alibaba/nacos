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
import com.alibaba.nacos.api.SystemPropertyKeyConst;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.constant.Constants.Address;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.client.naming.utils.CollectionUtils;
import com.alibaba.nacos.client.utils.ContextPathUtil;
import com.alibaba.nacos.client.utils.LogUtils;
import com.alibaba.nacos.client.utils.ParamUtil;
import com.alibaba.nacos.client.utils.TemplateUtils;
import com.alibaba.nacos.common.executor.NameThreadFactory;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.http.HttpUtils;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.utils.InternetAddressUtil;
import com.alibaba.nacos.common.utils.IoUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import org.slf4j.Logger;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Endpoint server list provider.
 * 
 * @author totalo 
 */
public class EndpointServerListProvider extends AbstractServerListProvider {
    
    private static final Logger LOGGER = LogUtils.logger(EndpointServerListProvider.class);
    
    private NacosRestTemplate nacosRestTemplate;
    
    private static final String CUSTOM_NAME = "custom";
    
    private final long refreshServerListInternal = TimeUnit.SECONDS.toMillis(30);
    
    private final int initServerListRetryTimes = 5;
    
    private long lastServerListRefreshTime = 0L;
    
    private ScheduledExecutorService refreshServerListExecutor;
    
    private String endpoint;
    
    private int endpointPort = 8080;
    
    private String endpointContextPath;
    
    private String serverListName = ParamUtil.getDefaultNodesPath();
    
    private volatile List<String> serversFromEndpoint = new ArrayList<>();
    
    private String addressServerUrl;
    
    private String moduleName = "default";
    
    @Override
    public void init(final NacosClientProperties properties, final NacosRestTemplate nacosRestTemplate) throws NacosException {
        super.init(properties, nacosRestTemplate);
        this.nacosRestTemplate = nacosRestTemplate;
        initEndpoint(properties);
        initEndpointPort(properties);
        initEndpointContextPath(properties);
        initServerListName(properties);
        initAddressServerUrl(properties);
        initModuleName(properties);
        startRefreshServerListTask();
    }
    
    @Override
    public List<String> getServerList() {
        return serversFromEndpoint;
    }
    
    @Override
    public String getServerName() {
        String contextPathTmp =
                StringUtils.isNotBlank(this.endpointContextPath) ? this.endpointContextPath : this.contextPath;
        return CUSTOM_NAME + "-" + String.join("_", endpoint, String.valueOf(endpointPort), contextPathTmp,
                serverListName) + (StringUtils.isNotBlank(namespace) ? ("_" + StringUtils.trim(
                namespace)) : "");
    }
    
    @Override
    public int getOrder() {
        return Address.ENDPOINT_SERVER_LIST_PROVIDER_ORDER;
    }
    
    @Override
    public boolean match(final NacosClientProperties properties) {
        String endpointTmp = properties.getProperty(PropertyKeyConst.ENDPOINT);
        String isUseEndpointRuleParsing = properties.getProperty(PropertyKeyConst.IS_USE_ENDPOINT_PARSING_RULE,
                properties.getProperty(SystemPropertyKeyConst.IS_USE_ENDPOINT_PARSING_RULE,
                        String.valueOf(ParamUtil.USE_ENDPOINT_PARSING_RULE_DEFAULT_VALUE)));
        if (Boolean.parseBoolean(isUseEndpointRuleParsing)) {
            endpointTmp = ParamUtil.parsingEndpointRule(endpointTmp);
        }
        return StringUtils.isNotBlank(endpointTmp);
    }
    
    @Override
    public String getAddressSource() {
        return this.addressServerUrl;
    }
    
    /**
     * Start refresh server list task.
     *
     * @throws NacosException nacos exception
     */
    public void startRefreshServerListTask() throws NacosException {
        for (int i = 0; i < initServerListRetryTimes && getServerList().isEmpty(); ++i) {
            refreshServerListIfNeed();
            if (!serversFromEndpoint.isEmpty()) {
                break;
            }
            try {
                this.wait((i + 1) * 100L);
            } catch (Exception e) {
                LOGGER.warn("get serverlist fail,url: {}", addressServerUrl);
            }
        }
        
        if (serversFromEndpoint.isEmpty()) {
            LOGGER.error("[init-serverlist] fail to get NACOS-server serverlist! url: {}", addressServerUrl);
            throw new NacosException(NacosException.SERVER_ERROR,
                    "fail to get NACOS-server serverlist! not connnect url:" + addressServerUrl);
        }
        
        refreshServerListExecutor = new ScheduledThreadPoolExecutor(1,
                new NameThreadFactory("com.alibaba.nacos.client.address.EndpointServerListProvider.refreshServerList"));
        // executor schedules the timer task
        refreshServerListExecutor.scheduleWithFixedDelay(this::refreshServerListIfNeed, 0L, 30L, TimeUnit.SECONDS);
    }
    
    private void refreshServerListIfNeed() {
        try {
            if (System.currentTimeMillis() - lastServerListRefreshTime < refreshServerListInternal) {
                return;
            }
            List<String> list = getServerListFromEndpoint();
            if (CollectionUtils.isEmpty(list)) {
                throw new Exception("Can not acquire Nacos list");
            }
            list.sort(String::compareTo);
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
            HttpRestResult<String> httpResult = nacosRestTemplate.get(addressServerUrl, HttpUtils.builderHeader(moduleName),
                    Query.EMPTY, String.class);
            
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
    
    private void initEndpoint(NacosClientProperties properties) {
        String endpointTmp = properties.getProperty(PropertyKeyConst.ENDPOINT);
        // Whether to enable domain name resolution rules
        String isUseEndpointRuleParsing = properties.getProperty(PropertyKeyConst.IS_USE_ENDPOINT_PARSING_RULE,
                properties.getProperty(SystemPropertyKeyConst.IS_USE_ENDPOINT_PARSING_RULE,
                        String.valueOf(ParamUtil.USE_ENDPOINT_PARSING_RULE_DEFAULT_VALUE)));
        if (Boolean.parseBoolean(isUseEndpointRuleParsing)) {
            endpointTmp = ParamUtil.parsingEndpointRule(endpointTmp);
        }
        this.endpoint = StringUtils.isNotBlank(endpointTmp) ? endpointTmp : "";
    }
    
    private void initEndpointPort(NacosClientProperties properties) {
        String endpointPortTmp = TemplateUtils.stringEmptyAndThenExecute(
                properties.getProperty(PropertyKeyConst.SystemEnv.ALIBABA_ALIWARE_ENDPOINT_PORT),
                () -> properties.getProperty(PropertyKeyConst.ENDPOINT_PORT));
        if (StringUtils.isNotBlank(endpointPortTmp)) {
            this.endpointPort = Integer.parseInt(endpointPortTmp);
        }
    }
    
    private void initEndpointContextPath(NacosClientProperties properties) {
        String endpointContextPathTmp = TemplateUtils.stringEmptyAndThenExecute(
                properties.getProperty(PropertyKeyConst.SystemEnv.ALIBABA_ALIWARE_ENDPOINT_CONTEXT_PATH),
                () -> properties.getProperty(PropertyKeyConst.ENDPOINT_CONTEXT_PATH));
        if (StringUtils.isNotBlank(endpointContextPathTmp)) {
            this.endpointContextPath = endpointContextPathTmp;
        }
    }
    
    private void initServerListName(NacosClientProperties properties) {
        String serverListNameTmp = properties.getProperty(PropertyKeyConst.ENDPOINT_CLUSTER_NAME,
                properties.getProperty(PropertyKeyConst.CLUSTER_NAME));
        if (!StringUtils.isBlank(serverListNameTmp)) {
            this.serverListName = serverListNameTmp;
        }
    }
    
    private void initAddressServerUrl(NacosClientProperties properties) {
        String contextPathTmp = StringUtils.isNotBlank(this.endpointContextPath) ? ContextPathUtil.normalizeContextPath(
                this.endpointContextPath) : ContextPathUtil.normalizeContextPath(this.contextPath);
        StringBuilder addressServerUrlTem = new StringBuilder(
                String.format("http://%s:%d%s/%s", this.endpoint, this.endpointPort, contextPathTmp,
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
    
    private void initModuleName(NacosClientProperties properties) {
        String moduleNameTmp = properties.getProperty(PropertyKeyConst.CLIENT_MODULE_TYPE);
        if (StringUtils.isNotBlank(moduleNameTmp)) {
            this.moduleName = moduleNameTmp;
        }
    }
    
    @Override
    public void shutdown() throws NacosException {
        if (null != refreshServerListExecutor) {
            refreshServerListExecutor.shutdown();
        }
    }
}
