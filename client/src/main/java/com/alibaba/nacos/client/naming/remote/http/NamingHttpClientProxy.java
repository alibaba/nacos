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

package com.alibaba.nacos.client.naming.remote.http;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.SystemPropertyKeyConst;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.CommonParams;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ListView;
import com.alibaba.nacos.api.naming.pojo.Service;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.api.naming.utils.NamingUtils;
import com.alibaba.nacos.api.selector.AbstractSelector;
import com.alibaba.nacos.api.selector.ExpressionSelector;
import com.alibaba.nacos.api.selector.SelectorType;
import com.alibaba.nacos.client.address.ServerListChangeEvent;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.client.monitor.MetricsMonitor;
import com.alibaba.nacos.client.naming.core.NamingServerListManager;
import com.alibaba.nacos.client.naming.remote.AbstractNamingClientProxy;
import com.alibaba.nacos.client.naming.utils.CollectionUtils;
import com.alibaba.nacos.client.naming.utils.UtilAndComs;
import com.alibaba.nacos.client.security.SecurityProxy;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.http.HttpUtils;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.utils.ConvertUtils;
import com.alibaba.nacos.common.utils.HttpMethod;
import com.alibaba.nacos.common.utils.InternetAddressUtil;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.http.HttpStatus;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static com.alibaba.nacos.client.utils.LogUtils.NAMING_LOGGER;
import static com.alibaba.nacos.common.constant.RequestUrlConstants.HTTPS_PREFIX;
import static com.alibaba.nacos.common.constant.RequestUrlConstants.HTTP_PREFIX;

/**
 * Naming proxy.
 *
 * @author nkorange
 */
public class NamingHttpClientProxy extends AbstractNamingClientProxy {
    
    private final NacosRestTemplate nacosRestTemplate = NamingHttpClientManager.getInstance().getNacosRestTemplate();
    
    private static final int DEFAULT_SERVER_PORT = 8848;
    
    private static final String MODULE_NAME = "Naming";
    
    private static final String IP_PARAM = "ip";
    
    private static final String PORT_PARAM = "port";
    
    private static final String WEIGHT_PARAM = "weight";
    
    private static final String ENABLE_PARAM = "enabled";
    
    private static final String EPHEMERAL_PARAM = "ephemeral";
    
    private static final String META_PARAM = "metadata";
    
    private static final String SELECTOR_PARAM = "selector";
    
    private static final String HEALTHY_PARAM = "healthy";
    
    private static final String PROTECT_THRESHOLD_PARAM = "protectThreshold";
    
    private static final String CLUSTERS_PARAM = "clusters";
    
    private static final String CLIENT_IP_PARAM = "clientIP";
    
    private static final String HEALTHY_ONLY_PARAM = "healthyOnly";
    
    private static final String REGISTER_ENABLE_PARAM = "enable";
    
    private final String namespaceId;
    
    private final NamingServerListManager serverListManager;
    
    private final int maxRetry;
    
    private int serverPort = DEFAULT_SERVER_PORT;
    
    public NamingHttpClientProxy(String namespaceId, SecurityProxy securityProxy, NamingServerListManager serverListManager,
            NacosClientProperties properties) {
        super(securityProxy);
        this.serverListManager = serverListManager;
        this.setServerPort(DEFAULT_SERVER_PORT);
        this.namespaceId = namespaceId;
        this.maxRetry = ConvertUtils.toInt(properties.getProperty(PropertyKeyConst.NAMING_REQUEST_DOMAIN_RETRY_COUNT,
                String.valueOf(UtilAndComs.REQUEST_DOMAIN_RETRY_COUNT)));
    }
    
    @Override
    public void onEvent(ServerListChangeEvent event) {
        // do nothing in http client
    }
    
    @Override
    public Class<? extends Event> subscribeType() {
        return ServerListChangeEvent.class;
    }
    
    @Override
    public void registerService(String serviceName, String groupName, Instance instance) throws NacosException {
        NAMING_LOGGER.info("[REGISTER-SERVICE] {} registering service {} with instance: {}", namespaceId, serviceName,
                instance);
        String groupedServiceName = NamingUtils.getGroupedName(serviceName, groupName);
        if (instance.isEphemeral()) {
            throw new UnsupportedOperationException(
                    "Do not support register ephemeral instances by HTTP, please use gRPC replaced.");
        }
        final Map<String, String> params = new HashMap<>(32);
        params.put(CommonParams.NAMESPACE_ID, namespaceId);
        params.put(CommonParams.SERVICE_NAME, groupedServiceName);
        params.put(CommonParams.GROUP_NAME, groupName);
        params.put(CommonParams.CLUSTER_NAME, instance.getClusterName());
        params.put(IP_PARAM, instance.getIp());
        params.put(PORT_PARAM, String.valueOf(instance.getPort()));
        params.put(WEIGHT_PARAM, String.valueOf(instance.getWeight()));
        params.put(REGISTER_ENABLE_PARAM, String.valueOf(instance.isEnabled()));
        params.put(HEALTHY_PARAM, String.valueOf(instance.isHealthy()));
        params.put(EPHEMERAL_PARAM, String.valueOf(instance.isEphemeral()));
        params.put(META_PARAM, JacksonUtils.toJson(instance.getMetadata()));
        reqApi(UtilAndComs.nacosUrlInstance, params, HttpMethod.POST);
    }
    
    @Override
    public void batchRegisterService(String serviceName, String groupName, List<Instance> instances) {
        throw new UnsupportedOperationException(
                "Do not support persistent instances to perform batch registration methods.");
    }
    
    @Override
    public void batchDeregisterService(String serviceName, String groupName, List<Instance> instances) {
        throw new UnsupportedOperationException(
                "Do not support persistent instances to perform batch de registration methods.");
    }
    
    @Override
    public void deregisterService(String serviceName, String groupName, Instance instance) throws NacosException {
        NAMING_LOGGER.info("[DEREGISTER-SERVICE] {} deregistering service {} with instance: {}", namespaceId,
                serviceName, instance);
        if (instance.isEphemeral()) {
            return;
        }
        final Map<String, String> params = new HashMap<>(16);
        params.put(CommonParams.NAMESPACE_ID, namespaceId);
        params.put(CommonParams.SERVICE_NAME, NamingUtils.getGroupedName(serviceName, groupName));
        params.put(CommonParams.CLUSTER_NAME, instance.getClusterName());
        params.put(IP_PARAM, instance.getIp());
        params.put(PORT_PARAM, String.valueOf(instance.getPort()));
        params.put(EPHEMERAL_PARAM, String.valueOf(instance.isEphemeral()));
        
        reqApi(UtilAndComs.nacosUrlInstance, params, HttpMethod.DELETE);
    }
    
    @Override
    public void updateInstance(String serviceName, String groupName, Instance instance) throws NacosException {
        NAMING_LOGGER.info("[UPDATE-SERVICE] {} update service {} with instance: {}", namespaceId, serviceName,
                instance);
        
        final Map<String, String> params = new HashMap<>(32);
        params.put(CommonParams.NAMESPACE_ID, namespaceId);
        params.put(CommonParams.SERVICE_NAME, serviceName);
        params.put(CommonParams.GROUP_NAME, groupName);
        params.put(CommonParams.CLUSTER_NAME, instance.getClusterName());
        params.put(IP_PARAM, instance.getIp());
        params.put(PORT_PARAM, String.valueOf(instance.getPort()));
        params.put(WEIGHT_PARAM, String.valueOf(instance.getWeight()));
        params.put(ENABLE_PARAM, String.valueOf(instance.isEnabled()));
        params.put(EPHEMERAL_PARAM, String.valueOf(instance.isEphemeral()));
        params.put(META_PARAM, JacksonUtils.toJson(instance.getMetadata()));
        
        reqApi(UtilAndComs.nacosUrlInstance, params, HttpMethod.PUT);
    }
    
    @Override
    public ServiceInfo queryInstancesOfService(String serviceName, String groupName, String clusters,
            boolean healthyOnly) {
        throw new UnsupportedOperationException(
                "Do not support query instance by http client,please use gRPC replaced.");
    }
    
    @Override
    public Service queryService(String serviceName, String groupName) throws NacosException {
        NAMING_LOGGER.info("[QUERY-SERVICE] {} query service : {}, {}", namespaceId, serviceName, groupName);
        
        final Map<String, String> params = new HashMap<>(16);
        params.put(CommonParams.NAMESPACE_ID, namespaceId);
        params.put(CommonParams.SERVICE_NAME, serviceName);
        params.put(CommonParams.GROUP_NAME, groupName);
        
        String result = reqApi(UtilAndComs.nacosUrlService, params, HttpMethod.GET);
        return JacksonUtils.toObj(result, Service.class);
    }
    
    @Override
    public void createService(Service service, AbstractSelector selector) throws NacosException {
        
        NAMING_LOGGER.info("[CREATE-SERVICE] {} creating service : {}", namespaceId, service);
        
        final Map<String, String> params = new HashMap<>(16);
        params.put(CommonParams.NAMESPACE_ID, namespaceId);
        params.put(CommonParams.SERVICE_NAME, service.getName());
        params.put(CommonParams.GROUP_NAME, service.getGroupName());
        params.put(PROTECT_THRESHOLD_PARAM, String.valueOf(service.getProtectThreshold()));
        params.put(META_PARAM, JacksonUtils.toJson(service.getMetadata()));
        params.put(SELECTOR_PARAM, JacksonUtils.toJson(selector));
        
        reqApi(UtilAndComs.nacosUrlService, params, HttpMethod.POST);
        
    }
    
    @Override
    public boolean deleteService(String serviceName, String groupName) throws NacosException {
        NAMING_LOGGER.info("[DELETE-SERVICE] {} deleting service : {} with groupName : {}", namespaceId, serviceName,
                groupName);
        
        final Map<String, String> params = new HashMap<>(16);
        params.put(CommonParams.NAMESPACE_ID, namespaceId);
        params.put(CommonParams.SERVICE_NAME, serviceName);
        params.put(CommonParams.GROUP_NAME, groupName);
        
        String result = reqApi(UtilAndComs.nacosUrlService, params, HttpMethod.DELETE);
        return "ok".equals(result);
    }
    
    @Override
    public void updateService(Service service, AbstractSelector selector) throws NacosException {
        NAMING_LOGGER.info("[UPDATE-SERVICE] {} updating service : {}", namespaceId, service);
        
        final Map<String, String> params = new HashMap<>(16);
        params.put(CommonParams.NAMESPACE_ID, namespaceId);
        params.put(CommonParams.SERVICE_NAME, service.getName());
        params.put(CommonParams.GROUP_NAME, service.getGroupName());
        params.put(PROTECT_THRESHOLD_PARAM, String.valueOf(service.getProtectThreshold()));
        params.put(META_PARAM, JacksonUtils.toJson(service.getMetadata()));
        params.put(SELECTOR_PARAM, JacksonUtils.toJson(selector));
        
        reqApi(UtilAndComs.nacosUrlService, params, HttpMethod.PUT);
    }
    
    @Override
    public boolean serverHealthy() {
        try {
            String result = reqApi(UtilAndComs.nacosUrlBase + "/operator/metrics", new HashMap<>(8), HttpMethod.GET);
            JsonNode json = JacksonUtils.toObj(result);
            String serverStatus = json.get("status").asText();
            return "UP".equals(serverStatus);
        } catch (Exception e) {
            return false;
        }
    }
    
    @Override
    public ListView<String> getServiceList(int pageNo, int pageSize, String groupName, AbstractSelector selector)
            throws NacosException {
        
        Map<String, String> params = new HashMap<>(16);
        params.put("pageNo", String.valueOf(pageNo));
        params.put("pageSize", String.valueOf(pageSize));
        params.put(CommonParams.NAMESPACE_ID, namespaceId);
        params.put(CommonParams.GROUP_NAME, groupName);
        
        if (selector != null) {
            switch (SelectorType.valueOf(selector.getType())) {
                case none:
                    break;
                case label:
                    ExpressionSelector expressionSelector = (ExpressionSelector) selector;
                    params.put(SELECTOR_PARAM, JacksonUtils.toJson(expressionSelector));
                    break;
                default:
                    break;
            }
        }
        
        String result = reqApi(UtilAndComs.nacosUrlBase + "/service/list", params, HttpMethod.GET);
        
        JsonNode json = JacksonUtils.toObj(result);
        ListView<String> listView = new ListView<>();
        listView.setCount(json.get("count").asInt());
        listView.setData(JacksonUtils.toObj(json.get("doms").toString(), new TypeReference<List<String>>() {
        }));
        
        return listView;
    }
    
    @Override
    public ServiceInfo subscribe(String serviceName, String groupName, String clusters) throws NacosException {
        throw new UnsupportedOperationException("Do not support subscribe service by UDP, please use gRPC replaced.");
    }
    
    @Override
    public void unsubscribe(String serviceName, String groupName, String clusters) throws NacosException {
    }
    
    @Override
    public boolean isSubscribed(String serviceName, String groupName, String clusters) throws NacosException {
        return true;
    }
    
    public String reqApi(String api, Map<String, String> params, String method) throws NacosException {
        return reqApi(api, params, Collections.EMPTY_MAP, method);
    }
    
    public String reqApi(String api, Map<String, String> params, Map<String, String> body, String method)
            throws NacosException {
        return reqApi(api, params, body, serverListManager.getServerList(), method);
    }
    
    /**
     * Request api.
     *
     * @param api     api
     * @param params  parameters
     * @param body    body
     * @param servers servers
     * @param method  http method
     * @return result
     * @throws NacosException nacos exception
     */
    public String reqApi(String api, Map<String, String> params, Map<String, String> body, List<String> servers,
            String method) throws NacosException {
        
        params.put(CommonParams.NAMESPACE_ID, getNamespaceId());
        
        if (CollectionUtils.isEmpty(servers) && !serverListManager.isDomain()) {
            throw new NacosException(NacosException.INVALID_PARAM, "no server available");
        }
        
        NacosException exception = new NacosException();
        if (serverListManager.isDomain()) {
            String nacosDomain = serverListManager.getNacosDomain();
            for (int i = 0; i < maxRetry; i++) {
                try {
                    return callServer(api, params, body, nacosDomain, method);
                } catch (NacosException e) {
                    exception = e;
                    if (NAMING_LOGGER.isDebugEnabled()) {
                        NAMING_LOGGER.debug("request {} failed.", nacosDomain, e);
                    }
                }
            }
        } else {
            Random random = new Random();
            int index = random.nextInt(servers.size());
            
            for (int i = 0; i < servers.size(); i++) {
                String server = servers.get(index);
                try {
                    return callServer(api, params, body, server, method);
                } catch (NacosException e) {
                    exception = e;
                    if (NAMING_LOGGER.isDebugEnabled()) {
                        NAMING_LOGGER.debug("request {} failed.", server, e);
                    }
                }
                index = (index + 1) % servers.size();
            }
        }
        
        NAMING_LOGGER.error("request: {} failed, servers: {}, code: {}, msg: {}", api, servers, exception.getErrCode(),
                exception.getErrMsg());
        
        throw new NacosException(exception.getErrCode(),
                "failed to req API:" + api + " after all servers(" + servers + ") tried: " + exception.getMessage());
        
    }
    
    /**
     * Call server.
     *
     * @param api       api
     * @param params    parameters
     * @param body      body
     * @param curServer ?
     * @param method    http method
     * @return result
     * @throws NacosException nacos exception
     */
    public String callServer(String api, Map<String, String> params, Map<String, String> body, String curServer,
            String method) throws NacosException {
        long start = System.currentTimeMillis();
        long end = 0;
        String namespace = params.get(CommonParams.NAMESPACE_ID);
        String group = params.get(CommonParams.GROUP_NAME);
        String serviceName = params.get(CommonParams.SERVICE_NAME);
        params.putAll(getSecurityHeaders(namespace, group, serviceName));
        Header header = HttpUtils.builderHeader(MODULE_NAME);
        
        String url;
        if (curServer.startsWith(HTTPS_PREFIX) || curServer.startsWith(HTTP_PREFIX)) {
            url = curServer + api;
        } else {
            if (!InternetAddressUtil.containsPort(curServer)) {
                curServer = curServer + InternetAddressUtil.IP_PORT_SPLITER + serverPort;
            }
            url = NamingHttpClientManager.getInstance().getPrefix() + curServer + api;
        }
        try {
            HttpRestResult<String> restResult = nacosRestTemplate.exchangeForm(url, header,
                    Query.newInstance().initParams(params), body, method, String.class);
            end = System.currentTimeMillis();
            
            MetricsMonitor.getNamingRequestMonitor(method, url, String.valueOf(restResult.getCode()))
                    .observe(end - start);
            
            if (restResult.ok()) {
                return restResult.getData();
            }
            if (HttpStatus.SC_NOT_MODIFIED == restResult.getCode()) {
                return StringUtils.EMPTY;
            }
            
            // If the 403 login operation is triggered, refresh the accessToken of the client
            if (HttpStatus.SC_FORBIDDEN == restResult.getCode()) {
                reLogin();
            }

            throw new NacosException(restResult.getCode(), restResult.getMessage());
        } catch (NacosException e) {
            NAMING_LOGGER.error("[NA] failed to request", e);
            throw e;
        } catch (Exception e) {
            NAMING_LOGGER.error("[NA] failed to request", e);
            throw new NacosException(NacosException.SERVER_ERROR, e);
        }
    }
    
    public String getNamespaceId() {
        return namespaceId;
    }
    
    public void setServerPort(int serverPort) {
        this.serverPort = serverPort;
        
        String sp = NacosClientProperties.PROTOTYPE.getProperty(SystemPropertyKeyConst.NAMING_SERVER_PORT);
        if (StringUtils.isNotBlank(sp)) {
            this.serverPort = Integer.parseInt(sp);
        }
    }
    
    @Override
    public void shutdown() throws NacosException {
        String className = this.getClass().getName();
        NAMING_LOGGER.info("{} do shutdown begin", className);
        NamingHttpClientManager.getInstance().shutdown();
        NAMING_LOGGER.info("{} do shutdown stop", className);
    }
}

