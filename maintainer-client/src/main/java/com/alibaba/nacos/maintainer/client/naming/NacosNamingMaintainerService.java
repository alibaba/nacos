/*
 * Copyright 1999-$toady.year Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.maintainer.client.naming;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.api.naming.pojo.healthcheck.AbstractHealthChecker;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.utils.HttpMethod;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.maintainer.client.constants.Constants;
import com.alibaba.nacos.maintainer.client.model.HttpRequest;
import com.alibaba.nacos.maintainer.client.model.core.Connection;
import com.alibaba.nacos.maintainer.client.model.core.IdGeneratorVO;
import com.alibaba.nacos.maintainer.client.model.core.Member;
import com.alibaba.nacos.maintainer.client.model.core.ServerLoaderMetrics;
import com.alibaba.nacos.maintainer.client.model.naming.InstanceDetailInfoVo;
import com.alibaba.nacos.maintainer.client.model.naming.InstanceMetadataBatchOperationVo;
import com.alibaba.nacos.maintainer.client.model.naming.MetricsInfoVo;
import com.alibaba.nacos.maintainer.client.model.naming.ServiceDetailInfo;
import com.alibaba.nacos.maintainer.client.model.naming.SwitchDomain;
import com.alibaba.nacos.maintainer.client.remote.ClientHttpProxy;
import com.alibaba.nacos.maintainer.client.utils.ParamUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class NacosNamingMaintainerService implements NamingMaintainerService {
    
    private final ClientHttpProxy clientHttpProxy;
    
    public NacosNamingMaintainerService(Properties properties) throws NacosException {
        this.clientHttpProxy = new ClientHttpProxy(properties);
    }
    
    @Override
    public String createService(String serviceName) throws Exception {
        return createService(ParamUtil.getDefaultNamespaceId(), ParamUtil.getDefaultGroupName(), serviceName, "", false, 0.0f, "");
    }
    
    @Override
    public String createService(String namespaceId, String groupName, String serviceName, String metadata,
            boolean ephemeral, float protectThreshold, String selector) throws Exception {
        Map<String, String> params = new HashMap<>(8);
        params.put("namespaceId", namespaceId);
        params.put("groupName", groupName);
        params.put("serviceName", serviceName);
        params.put("metadata", metadata);
        params.put("ephemeral", String.valueOf(ephemeral));
        params.put("protectThreshold", String.valueOf(protectThreshold));
        params.put("selector", selector);
        
        HttpRequest httpRequest = new HttpRequest.Builder()
                .setHttpMethod(HttpMethod.POST)
                .setPath(Constants.AdminApiPath.NAMING_SERVICE_ADMIN_PATH)
                .setParamValue(params)
                .build();
        HttpRestResult<String> httpRestResult = clientHttpProxy.executeSyncHttpRequest(httpRequest);
        Result<String> result = JacksonUtils.toObj(httpRestResult.getData(), new TypeReference<Result<String>>() {
        });
        return result.getData();
    }
    
    @Override
    public String updateService(String namespaceId, String groupName, String serviceName, String metadata,
            boolean ephemeral, float protectThreshold, String selector) throws Exception {
        Map<String, String> params = new HashMap<>(8);
        params.put("namespaceId", namespaceId);
        params.put("groupName", groupName);
        params.put("serviceName", serviceName);
        params.put("metadata", metadata);
        params.put("ephemeral", String.valueOf(ephemeral));
        params.put("protectThreshold", String.valueOf(protectThreshold));
        params.put("selector", selector);
        
        HttpRequest httpRequest = new HttpRequest.Builder()
                .setHttpMethod(HttpMethod.PUT)
                .setPath(Constants.AdminApiPath.NAMING_SERVICE_ADMIN_PATH)
                .setParamValue(params)
                .build();
        HttpRestResult<String> httpRestResult = clientHttpProxy.executeSyncHttpRequest(httpRequest);
        Result<String> result = JacksonUtils.toObj(httpRestResult.getData(), new TypeReference<Result<String>>() {
            
        });
        return result.getData();
    }
    
    @Override
    public String removeService(String namespaceId, String groupName, String serviceName) throws Exception {
        Map<String, String> params = new HashMap<>(8);
        params.put("namespaceId", namespaceId);
        params.put("groupName", groupName);
        params.put("serviceName", serviceName);
        
        HttpRequest httpRequest = new HttpRequest.Builder()
                .setHttpMethod(HttpMethod.DELETE)
                .setPath(Constants.AdminApiPath.NAMING_SERVICE_ADMIN_PATH)
                .setParamValue(params)
                .build();
        HttpRestResult<String> httpRestResult = clientHttpProxy.executeSyncHttpRequest(httpRequest);
        Result<String> result = JacksonUtils.toObj(httpRestResult.getData(), new TypeReference<Result<String>>() {
        });
        return result.getData();
    }
    
    @Override
    public ServiceDetailInfo getServiceDetail(String namespaceId, String groupName, String serviceName)
            throws Exception {
        Map<String, String> params = new HashMap<>(8);
        params.put("namespaceId", namespaceId);
        params.put("groupName", groupName);
        params.put("serviceName", serviceName);
        
        HttpRequest httpRequest = new HttpRequest.Builder()
                .setHttpMethod(HttpMethod.GET)
                .setPath(Constants.AdminApiPath.NAMING_SERVICE_ADMIN_PATH)
                .setParamValue(params)
                .build();
        HttpRestResult<String> httpRestResult = clientHttpProxy.executeSyncHttpRequest(httpRequest);
        Result<ServiceDetailInfo> result = JacksonUtils.toObj(httpRestResult.getData(),
                new TypeReference<Result<ServiceDetailInfo>>() {
                });
        return result.getData();
    }
    
    @Override
    public Object listServices(String namespaceId, String groupName, String selector, int pageNo, int pageSize)
            throws Exception {
        Map<String, String> params = new HashMap<>(8);
        params.put("namespaceId", namespaceId);
        params.put("groupName", groupName);
        params.put("selector", selector);
        params.put("pageNo", String.valueOf(pageNo));
        params.put("pageSize", String.valueOf(pageSize));
        
        HttpRequest httpRequest = new HttpRequest.Builder()
                .setHttpMethod(HttpMethod.GET)
                .setPath(Constants.AdminApiPath.NAMING_SERVICE_ADMIN_PATH + "/list")
                .setParamValue(params)
                .build();
        HttpRestResult<String> httpRestResult = clientHttpProxy.executeSyncHttpRequest(httpRequest);
        return JacksonUtils.toObj(httpRestResult.getData());
    }
    
    @Override
    public ObjectNode searchService(String namespaceId, String expr) throws Exception {
        Map<String, String> params = new HashMap<>(8);
        params.put("namespaceId", namespaceId);
        params.put("expr", expr);
        
        HttpRequest httpRequest = new HttpRequest.Builder()
                .setHttpMethod(HttpMethod.GET)
                .setPath(Constants.AdminApiPath.NAMING_SERVICE_ADMIN_PATH + "/names")
                .setParamValue(params)
                .build();
        HttpRestResult<String> httpRestResult = clientHttpProxy.executeSyncHttpRequest(httpRequest);
        return JacksonUtils.toObj(httpRestResult.getData(), ObjectNode.class);
    }
    
    @Override
    public Result<ObjectNode> getSubscribers(String namespaceId, String groupName, String serviceName, int pageNo,
            int pageSize, boolean aggregation) throws Exception {
        Map<String, String> params = new HashMap<>(8);
        params.put("namespaceId", namespaceId);
        params.put("groupName", groupName);
        params.put("serviceName", serviceName);
        params.put("pageNo", String.valueOf(pageNo));
        params.put("pageSize", String.valueOf(pageSize));
        params.put("aggregation", String.valueOf(aggregation));
        
        HttpRequest httpRequest = new HttpRequest.Builder()
                .setHttpMethod(HttpMethod.GET)
                .setPath(Constants.AdminApiPath.NAMING_SERVICE_ADMIN_PATH + "/subscribers")
                .setParamValue(params)
                .build();
        HttpRestResult<String> httpRestResult = clientHttpProxy.executeSyncHttpRequest(httpRequest);
        return JacksonUtils.toObj(httpRestResult.getData(), new TypeReference<Result<ObjectNode>>() {
        });
    }
    
    @Override
    public List<String> listSelectorTypes() throws Exception {
        HttpRequest httpRequest = new HttpRequest.Builder()
                .setHttpMethod(HttpMethod.GET)
                .setPath(Constants.AdminApiPath.NAMING_SERVICE_ADMIN_PATH + "/selector/types")
                .build();
        HttpRestResult<String> httpRestResult = clientHttpProxy.executeSyncHttpRequest(httpRequest);
        return JacksonUtils.toObj(httpRestResult.getData(), new TypeReference<List<String>>() { });
    }
    
    @Override
    public SwitchDomain getSwitches() throws Exception {
        HttpRequest httpRequest = new HttpRequest.Builder()
                .setHttpMethod(HttpMethod.GET)
                .setPath(Constants.AdminApiPath.NAMING_OPS_ADMIN_PATH + "/switches")
                .build();
        HttpRestResult<String> httpRestResult = clientHttpProxy.executeSyncHttpRequest(httpRequest);
        return JacksonUtils.toObj(httpRestResult.getData(), SwitchDomain.class);
    }
    
    @Override
    public String updateSwitch(String entry, String value, boolean debug) throws Exception {
        Map<String, String> params = new HashMap<>(8);
        params.put("entry", entry);
        params.put("value", value);
        params.put("debug", String.valueOf(debug));
        
        HttpRequest httpRequest = new HttpRequest.Builder()
                .setHttpMethod(HttpMethod.PUT)
                .setPath(Constants.AdminApiPath.NAMING_OPS_ADMIN_PATH + "/switches")
                .setParamValue(params)
                .build();
        HttpRestResult<String> httpRestResult = clientHttpProxy.executeSyncHttpRequest(httpRequest);
        Result<String> result = JacksonUtils.toObj(httpRestResult.getData(), new TypeReference<Result<String>>() { });
        return result.getData();
    }
    
    @Override
    public MetricsInfoVo getMetrics(boolean onlyStatus) throws Exception {
        Map<String, String> params = new HashMap<>(8);
        params.put("onlyStatus", String.valueOf(onlyStatus));
        
        HttpRequest httpRequest = new HttpRequest.Builder()
                .setHttpMethod(HttpMethod.GET)
                .setPath(Constants.AdminApiPath.NAMING_OPS_ADMIN_PATH + "/metrics")
                .setParamValue(params)
                .build();
        HttpRestResult<String> httpRestResult = clientHttpProxy.executeSyncHttpRequest(httpRequest);
        return JacksonUtils.toObj(httpRestResult.getData(), MetricsInfoVo.class);
    }
    
    @Override
    public String setLogLevel(String logName, String logLevel) throws Exception {
        Map<String, String> params = new HashMap<>(8);
        params.put("logName", logName);
        params.put("logLevel", logLevel);
        
        HttpRequest httpRequest = new HttpRequest.Builder()
                .setHttpMethod(HttpMethod.PUT)
                .setPath(Constants.AdminApiPath.NAMING_OPS_ADMIN_PATH + "/log")
                .setParamValue(params)
                .build();
        HttpRestResult<String> httpRestResult = clientHttpProxy.executeSyncHttpRequest(httpRequest);
        Result<String> result = JacksonUtils.toObj(httpRestResult.getData(), new TypeReference<Result<String>>() { });
        return result.getData();
    }
    
    @Override
    public String registerInstance(String namespaceId, String groupName, String serviceName, String clusterName,
            String ip, int port, String weight, boolean healthy, boolean enabled, String ephemeral, String metadata)
            throws Exception {
        Map<String, String> params = new HashMap<>(8);
        params.put("namespaceId", namespaceId);
        params.put("groupName", groupName);
        params.put("serviceName", serviceName);
        params.put("clusterName", clusterName);
        params.put("ip", ip);
        params.put("port", String.valueOf(port));
        params.put("weight", weight);
        params.put("healthy", String.valueOf(healthy));
        params.put("enabled", String.valueOf(enabled));
        params.put("ephemeral", ephemeral);
        params.put("metadata", metadata);
        
        HttpRequest httpRequest = new HttpRequest.Builder()
                .setHttpMethod(HttpMethod.POST)
                .setPath(Constants.AdminApiPath.NAMING_INSTANCE_ADMIN_PATH)
                .setParamValue(params)
                .build();
        HttpRestResult<String> httpRestResult = clientHttpProxy.executeSyncHttpRequest(httpRequest);
        Result<String> result = JacksonUtils.toObj(httpRestResult.getData(), new TypeReference<Result<String>>() { });
        return result.getData();
    }
    
    @Override
    public String deregisterInstance(String namespaceId, String groupName, String serviceName, String clusterName,
            String ip, int port, String weight, boolean healthy, boolean enabled, String ephemeral, String metadata)
            throws Exception {
        Map<String, String> params = new HashMap<>(8);
        params.put("namespaceId", namespaceId);
        params.put("groupName", groupName);
        params.put("serviceName", serviceName);
        params.put("clusterName", clusterName);
        params.put("ip", ip);
        params.put("port", String.valueOf(port));
        params.put("weight", weight);
        params.put("healthy", String.valueOf(healthy));
        params.put("enabled", String.valueOf(enabled));
        params.put("ephemeral", ephemeral);
        params.put("metadata", metadata);
        
        HttpRequest httpRequest = new HttpRequest.Builder()
                .setHttpMethod(HttpMethod.DELETE)
                .setPath(Constants.AdminApiPath.NAMING_INSTANCE_ADMIN_PATH)
                .setParamValue(params)
                .build();
        HttpRestResult<String> httpRestResult = clientHttpProxy.executeSyncHttpRequest(httpRequest);
        Result<String> result = JacksonUtils.toObj(httpRestResult.getData(), new TypeReference<Result<String>>() { });
        return result.getData();
    }
    
    @Override
    public String updateInstance(String namespaceId, String groupName, String serviceName, String clusterName,
            String ip, int port, String weight, boolean healthy, boolean enabled, String ephemeral, String metadata)
            throws Exception {
        Map<String, String> params = new HashMap<>(8);
        params.put("namespaceId", namespaceId);
        params.put("groupName", groupName);
        params.put("serviceName", serviceName);
        params.put("clusterName", clusterName);
        params.put("ip", ip);
        params.put("port", String.valueOf(port));
        params.put("weight", weight);
        params.put("healthy", String.valueOf(healthy));
        params.put("enabled", String.valueOf(enabled));
        params.put("ephemeral", ephemeral);
        params.put("metadata", metadata);
        
        HttpRequest httpRequest = new HttpRequest.Builder()
                .setHttpMethod(HttpMethod.PUT)
                .setPath(Constants.AdminApiPath.NAMING_INSTANCE_ADMIN_PATH)
                .setParamValue(params)
                .build();
        HttpRestResult<String> httpRestResult = clientHttpProxy.executeSyncHttpRequest(httpRequest);
        Result<String> result = JacksonUtils.toObj(httpRestResult.getData(), new TypeReference<Result<String>>() { });
        return result.getData();
    }
    
    @Override
    public InstanceMetadataBatchOperationVo batchUpdateInstanceMetadata(String namespaceId, String groupName,
            String serviceName, String instance, Map<String, String> metadata, String consistencyType)
            throws Exception {
        Map<String, String> params = new HashMap<>(8);
        params.put("namespaceId", namespaceId);
        params.put("groupName", groupName);
        params.put("serviceName", serviceName);
        params.put("instance", instance);
        params.put("consistencyType", consistencyType);
        params.put("metadata", JacksonUtils.toJson(metadata));
        
        HttpRequest httpRequest = new HttpRequest.Builder()
                .setHttpMethod(HttpMethod.PUT)
                .setPath(Constants.AdminApiPath.NAMING_INSTANCE_ADMIN_PATH + "/metadata/batch")
                .setParamValue(params)
                .build();
        HttpRestResult<String> httpRestResult = clientHttpProxy.executeSyncHttpRequest(httpRequest);
        return JacksonUtils.toObj(httpRestResult.getData(), InstanceMetadataBatchOperationVo.class);
    }
    
    @Override
    public InstanceMetadataBatchOperationVo batchDeleteInstanceMetadata(String namespaceId, String groupName,
            String serviceName, String instance, Map<String, String> metadata, String consistencyType)
            throws Exception {
        Map<String, String> params = new HashMap<>(8);
        params.put("namespaceId", namespaceId);
        params.put("groupName", groupName);
        params.put("serviceName", serviceName);
        params.put("instance", instance);
        params.put("consistencyType", consistencyType);
        params.put("metadata", JacksonUtils.toJson(metadata));
        
        HttpRequest httpRequest = new HttpRequest.Builder()
                .setHttpMethod(HttpMethod.DELETE)
                .setPath(Constants.AdminApiPath.NAMING_INSTANCE_ADMIN_PATH + "/metadata/batch")
                .setParamValue(params)
                .build();
        HttpRestResult<String> httpRestResult = clientHttpProxy.executeSyncHttpRequest(httpRequest);
        return JacksonUtils.toObj(httpRestResult.getData(), InstanceMetadataBatchOperationVo.class);
    }
    
    @Override
    public String partialUpdateInstance(String namespaceId, String serviceName, String clusterName, int ip, int port,
            double weight, boolean enabled, String metadata) throws Exception {
        Map<String, String> params = new HashMap<>(8);
        params.put("namespaceId", namespaceId);
        params.put("serviceName", serviceName);
        params.put("clusterName", clusterName);
        params.put("ip", String.valueOf(ip));
        params.put("port", String.valueOf(port));
        params.put("weight", String.valueOf(weight));
        params.put("enabled", String.valueOf(enabled));
        params.put("metadata", metadata);
        
        HttpRequest httpRequest = new HttpRequest.Builder()
                .setHttpMethod(HttpMethod.PUT)
                .setPath(Constants.AdminApiPath.NAMING_INSTANCE_ADMIN_PATH + "/partial")
                .setParamValue(params)
                .build();
        HttpRestResult<String> httpRestResult = clientHttpProxy.executeSyncHttpRequest(httpRequest);
        Result<String> result = JacksonUtils.toObj(httpRestResult.getData(), new TypeReference<Result<String>>() { });
        return result.getData();
    }
    
    @Override
    public ServiceInfo listInstances(String namespaceId, String groupName, String serviceName, String clusterName,
            String ip, int port, boolean healthyOnly) throws Exception {
        Map<String, String> params = new HashMap<>(8);
        params.put("namespaceId", namespaceId);
        params.put("groupName", groupName);
        params.put("serviceName", serviceName);
        params.put("clusterName", clusterName);
        params.put("ip", ip);
        params.put("port", String.valueOf(port));
        params.put("healthyOnly", String.valueOf(healthyOnly));
        
        HttpRequest httpRequest = new HttpRequest.Builder()
                .setHttpMethod(HttpMethod.GET)
                .setPath(Constants.AdminApiPath.NAMING_INSTANCE_ADMIN_PATH + "/list")
                .setParamValue(params)
                .build();
        HttpRestResult<String> httpRestResult = clientHttpProxy.executeSyncHttpRequest(httpRequest);
        return JacksonUtils.toObj(httpRestResult.getData(), ServiceInfo.class);
    }
    
    @Override
    public InstanceDetailInfoVo getInstanceDetail(String namespaceId, String groupName, String serviceName,
            String clusterName, String ip, int port) throws Exception {
        Map<String, String> params = new HashMap<>(8);
        params.put("namespaceId", namespaceId);
        params.put("groupName", groupName);
        params.put("serviceName", serviceName);
        params.put("clusterName", clusterName);
        params.put("ip", ip);
        params.put("port", String.valueOf(port));
        
        HttpRequest httpRequest = new HttpRequest.Builder()
                .setHttpMethod(HttpMethod.GET)
                .setPath(Constants.AdminApiPath.NAMING_INSTANCE_ADMIN_PATH)
                .setParamValue(params)
                .build();
        HttpRestResult<String> httpRestResult = clientHttpProxy.executeSyncHttpRequest(httpRequest);
        return JacksonUtils.toObj(httpRestResult.getData(), InstanceDetailInfoVo.class);
    }
    
    @Override
    public String updateInstanceHealthStatus(String namespaceId, String groupName, String serviceName,
            String clusterName, String metadata, boolean ephemeral, float protectThreshold, String selector)
            throws Exception {
        Map<String, String> params = new HashMap<>(8);
        params.put("namespaceId", namespaceId);
        params.put("groupName", groupName);
        params.put("serviceName", serviceName);
        params.put("clusterName", clusterName);
        params.put("metadata", metadata);
        params.put("ephemeral", String.valueOf(ephemeral));
        params.put("protectThreshold", String.valueOf(protectThreshold));
        params.put("selector", selector);
        
        HttpRequest httpRequest = new HttpRequest.Builder()
                .setHttpMethod(HttpMethod.PUT)
                .setPath(Constants.AdminApiPath.NAMING_HEALTH_ADMIN_PATH + "/instance")
                .setParamValue(params)
                .build();
        
        HttpRestResult<String> httpRestResult = clientHttpProxy.executeSyncHttpRequest(httpRequest);
        Result<String> result = JacksonUtils.toObj(httpRestResult.getData(), new TypeReference<Result<String>>() { });
        return result.getData();
    }
    
    @Override
    public Map<String, AbstractHealthChecker> getHealthCheckers() throws Exception {
        HttpRequest httpRequest = new HttpRequest.Builder()
                .setHttpMethod(HttpMethod.GET)
                .setPath(Constants.AdminApiPath.NAMING_HEALTH_ADMIN_PATH + "/checkers")
                .build();
        HttpRestResult<String> httpRestResult = clientHttpProxy.executeSyncHttpRequest(httpRequest);
        return JacksonUtils.toObj(httpRestResult.getData(), new TypeReference<Map<String, AbstractHealthChecker>>() { });
    }
    
    @Override
    public String updateCluster(String namespaceId, String groupName, String clusterName, Integer checkPort,
            Boolean useInstancePort4Check, String healthChecker, Map<String, String> metadata) throws Exception {
        Map<String, String> params = new HashMap<>(8);
        params.put("namespaceId", namespaceId);
        params.put("groupName", groupName);
        params.put("clusterName", clusterName);
        params.put("checkPort", String.valueOf(checkPort));
        params.put("useInstancePort4Check", String.valueOf(useInstancePort4Check));
        params.put("healthChecker", healthChecker);
        params.put("metadata", JacksonUtils.toJson(metadata));
        
        HttpRequest httpRequest = new HttpRequest.Builder()
                .setHttpMethod(HttpMethod.PUT)
                .setPath(Constants.AdminApiPath.NAMING_CLUSTER_ADMIN_PATH)
                .setParamValue(params)
                .build();
        HttpRestResult<String> httpRestResult = clientHttpProxy.executeSyncHttpRequest(httpRequest);
        Result<String> result = JacksonUtils.toObj(httpRestResult.getData(), new TypeReference<Result<String>>() { });
        return result.getData();
    }
    
    @Override
    public List<String> getClientList() throws Exception {
        HttpRequest httpRequest = new HttpRequest.Builder()
                .setHttpMethod(HttpMethod.GET)
                .setPath(Constants.AdminApiPath.NAMING_CLIENT_ADMIN_PATH + "/list")
                .build();
        HttpRestResult<String> httpRestResult = clientHttpProxy.executeSyncHttpRequest(httpRequest);
        return JacksonUtils.toObj(httpRestResult.getData(), new TypeReference<List<String>>() { });
    }
    
    @Override
    public ObjectNode getClientDetail(String clientId) throws Exception {
        Map<String, String> params = new HashMap<>(8);
        params.put("clientId", clientId);
        
        HttpRequest httpRequest = new HttpRequest.Builder()
                .setHttpMethod(HttpMethod.GET)
                .setPath(Constants.AdminApiPath.NAMING_CLIENT_ADMIN_PATH)
                .setParamValue(params)
                .build();
        HttpRestResult<String> httpRestResult = clientHttpProxy.executeSyncHttpRequest(httpRequest);
        return JacksonUtils.toObj(httpRestResult.getData(), ObjectNode.class);
    }
    
    @Override
    public List<ObjectNode> getPublishedServiceList(String clientId) throws Exception {
        Map<String, String> params = new HashMap<>(8);
        params.put("clientId", clientId);
        
        HttpRequest httpRequest = new HttpRequest.Builder()
                .setHttpMethod(HttpMethod.GET)
                .setPath(Constants.AdminApiPath.NAMING_CLIENT_ADMIN_PATH + "/publish/list")
                .setParamValue(params)
                .build();
        HttpRestResult<String> httpRestResult = clientHttpProxy.executeSyncHttpRequest(httpRequest);
        return JacksonUtils.toObj(httpRestResult.getData(), new TypeReference<List<ObjectNode>>() { });
    }
    
    @Override
    public List<ObjectNode> getSubscribeServiceList(String clientId) throws Exception {
        Map<String, String> params = new HashMap<>(8);
        params.put("clientId", clientId);
        
        HttpRequest httpRequest = new HttpRequest.Builder()
                .setHttpMethod(HttpMethod.GET)
                .setPath(Constants.AdminApiPath.NAMING_CLIENT_ADMIN_PATH + "/subscribe/list")
                .setParamValue(params)
                .build();
        HttpRestResult<String> httpRestResult = clientHttpProxy.executeSyncHttpRequest(httpRequest);
        return JacksonUtils.toObj(httpRestResult.getData(), new TypeReference<List<ObjectNode>>() { });
    }
    
    @Override
    public List<ObjectNode> getPublishedClientList(String namespaceId, String groupName, String serviceName,
            boolean ephemeral, String ip, Integer port) throws Exception {
        Map<String, String> params = new HashMap<>(8);
        params.put("namespaceId", namespaceId);
        params.put("groupName", groupName);
        params.put("serviceName", serviceName);
        params.put("ephemeral", String.valueOf(ephemeral));
        params.put("ip", ip);
        params.put("port", String.valueOf(port));
        
        HttpRequest httpRequest = new HttpRequest.Builder()
                .setHttpMethod(HttpMethod.GET)
                .setPath(Constants.AdminApiPath.NAMING_CLIENT_ADMIN_PATH + "/service/publisher/list")
                .setParamValue(params)
                .build();
        HttpRestResult<String> httpRestResult = clientHttpProxy.executeSyncHttpRequest(httpRequest);
        return JacksonUtils.toObj(httpRestResult.getData(), new TypeReference<List<ObjectNode>>() { });
    }
    
    @Override
    public List<ObjectNode> getSubscribeClientList(String namespaceId, String groupName, String serviceName,
            boolean ephemeral, String ip, Integer port) throws Exception {
        Map<String, String> params = new HashMap<>(8);
        params.put("namespaceId", namespaceId);
        params.put("groupName", groupName);
        params.put("serviceName", serviceName);
        params.put("ephemeral", String.valueOf(ephemeral));
        params.put("ip", ip);
        params.put("port", String.valueOf(port));
        
        HttpRequest httpRequest = new HttpRequest.Builder()
                .setHttpMethod(HttpMethod.GET)
                .setPath(Constants.AdminApiPath.NAMING_CLIENT_ADMIN_PATH + "/service/subscriber/list")
                .setParamValue(params)
                .build();
        HttpRestResult<String> httpRestResult = clientHttpProxy.executeSyncHttpRequest(httpRequest);
        return JacksonUtils.toObj(httpRestResult.getData(), new TypeReference<List<ObjectNode>>() { });
    }
    
    @Override
    public ObjectNode getResponsibleServerForClient(String ip, String port) throws Exception {
        Map<String, String> params = new HashMap<>(8);
        params.put("ip", ip);
        params.put("port", port);
        
        HttpRequest httpRequest = new HttpRequest.Builder()
                .setHttpMethod(HttpMethod.GET)
                .setPath(Constants.AdminApiPath.NAMING_CLIENT_ADMIN_PATH + "/distro")
                .setParamValue(params)
                .build();
        HttpRestResult<String> httpRestResult = clientHttpProxy.executeSyncHttpRequest(httpRequest);
        return JacksonUtils.toObj(httpRestResult.getData(), ObjectNode.class);
    }
    
    @Override
    public String raftOps(String command, String value, String groupId) throws Exception {
        Map<String, String> params = new HashMap<>(8);
        params.put("command", command);
        params.put("value", value);
        params.put("groupId", groupId);
        
        HttpRequest httpRequest = new HttpRequest.Builder()
                .setHttpMethod(HttpMethod.POST)
                .setPath(Constants.AdminApiPath.CORE_OPS_ADMIN_PATH + "/raft")
                .setParamValue(params)
                .build();
        HttpRestResult<String> httpRestResult = clientHttpProxy.executeSyncHttpRequest(httpRequest);
        Result<String> result = JacksonUtils.toObj(httpRestResult.getData(),
                new TypeReference<Result<String>>() {
                });
        return result.getData();
    }
    
    @Override
    public List<IdGeneratorVO> getIdsHealth() throws Exception {
        HttpRequest httpRequest = new HttpRequest.Builder()
                .setHttpMethod(HttpMethod.GET)
                .setPath(Constants.AdminApiPath.CORE_OPS_ADMIN_PATH + "/ids")
                .build();
        HttpRestResult<String> httpRestResult = clientHttpProxy.executeSyncHttpRequest(httpRequest);
        Result<List<IdGeneratorVO>> result = JacksonUtils.toObj(httpRestResult.getData(),
                new TypeReference<Result<List<IdGeneratorVO>>>() {
                });
        return result.getData();
    }
    
    @Override
    public void updateLogLevel(String logName, String logLevel) throws Exception {
        Map<String, String> params = new HashMap<>(8);
        params.put("logName", logName);
        params.put("logLevel", logLevel);
        
        HttpRequest httpRequest = new HttpRequest.Builder()
                .setHttpMethod(HttpMethod.PUT)
                .setPath(Constants.AdminApiPath.CORE_OPS_ADMIN_PATH + "/log")
                .setParamValue(params)
                .build();
        clientHttpProxy.executeSyncHttpRequest(httpRequest);
    }
    
    @Override
    public Member getSelfNode() throws Exception {
        HttpRequest httpRequest = new HttpRequest.Builder()
                .setHttpMethod(HttpMethod.GET)
                .setPath(Constants.AdminApiPath.CORE_CLUSTER_ADMIN_PATH + "/node/self")
                .build();
        HttpRestResult<String> httpRestResult = clientHttpProxy.executeSyncHttpRequest(httpRequest);
        Result<Member> result = JacksonUtils.toObj(httpRestResult.getData(),
                new TypeReference<Result<Member>>() {
                });
        return result.getData();
    }
    
    @Override
    public Collection<Member> listClusterNodes(String address, String state) throws Exception {
        Map<String, String> params = new HashMap<>(8);
        params.put("address", address);
        params.put("state", state);
        
        HttpRequest httpRequest = new HttpRequest.Builder()
                .setHttpMethod(HttpMethod.GET)
                .setPath(Constants.AdminApiPath.CORE_CLUSTER_ADMIN_PATH + "/node/list")
                .setParamValue(params)
                .build();
        HttpRestResult<String> httpRestResult = clientHttpProxy.executeSyncHttpRequest(httpRequest);
        Result<Collection<Member>> result = JacksonUtils.toObj(httpRestResult.getData(),
                new TypeReference<Result<Collection<Member>>>() {
                });
        return result.getData();
    }
    
    @Override
    public String getSelfNodeHealth() throws Exception {
        HttpRequest httpRequest = new HttpRequest.Builder()
                .setHttpMethod(HttpMethod.GET)
                .setPath(Constants.AdminApiPath.CORE_CLUSTER_ADMIN_PATH + "/node/self/health")
                .build();
        HttpRestResult<String> httpRestResult = clientHttpProxy.executeSyncHttpRequest(httpRequest);
        Result<String> result = JacksonUtils.toObj(httpRestResult.getData(),
                new TypeReference<Result<String>>() {
                });
        return result.getData();
    }
    
    @Override
    public Boolean updateClusterNodes(List<Member> nodes) throws Exception {
        Map<String, String> params = new HashMap<>(8);
        params.put("nodes", JacksonUtils.toJson(nodes));
        
        HttpRequest httpRequest = new HttpRequest.Builder()
                .setHttpMethod(HttpMethod.PUT)
                .setPath(Constants.AdminApiPath.CORE_CLUSTER_ADMIN_PATH + "/node/list")
                .setParamValue(params)
                .build();
        HttpRestResult<String> httpRestResult = clientHttpProxy.executeSyncHttpRequest(httpRequest);
        Result<Boolean> result = JacksonUtils.toObj(httpRestResult.getData(),
                new TypeReference<Result<Boolean>>() {
                });
        return result.getData();
    }
    
    @Override
    public Boolean updateLookupMode(String type) throws Exception {
        Map<String, String> params = new HashMap<>(8);
        params.put("type", type);
        
        HttpRequest httpRequest = new HttpRequest.Builder()
                .setHttpMethod(HttpMethod.PUT)
                .setPath(Constants.AdminApiPath.CORE_CLUSTER_ADMIN_PATH + "/lookup")
                .setParamValue(params)
                .build();
        HttpRestResult<String> httpRestResult = clientHttpProxy.executeSyncHttpRequest(httpRequest);
        Result<Boolean> result = JacksonUtils.toObj(httpRestResult.getData(),
                new TypeReference<Result<Boolean>>() {
                });
        return result.getData();
    }
    
    @Override
    public Map<String, Connection> getCurrentClients() throws Exception {
        HttpRequest httpRequest = new HttpRequest.Builder()
                .setHttpMethod(HttpMethod.GET)
                .setPath(Constants.AdminApiPath.CORE_LOADER_ADMIN_PATH + "/current")
                .build();
        HttpRestResult<String> httpRestResult = clientHttpProxy.executeSyncHttpRequest(httpRequest);
        Result<Map<String, Connection>> result = JacksonUtils.toObj(httpRestResult.getData(),
                new TypeReference<Result<Map<String, Connection>>>() {
                });
        return result.getData();
    }
    
    @Override
    public String reloadConnectionCount(Integer count, String redirectAddress) throws Exception {
        Map<String, String> params = new HashMap<>(8);
        params.put("count", String.valueOf(count));
        params.put("redirectAddress", redirectAddress);
        
        HttpRequest httpRequest = new HttpRequest.Builder()
                .setHttpMethod(HttpMethod.GET)
                .setPath(Constants.AdminApiPath.CORE_LOADER_ADMIN_PATH + "/reloadCurrent")
                .setParamValue(params)
                .build();
        HttpRestResult<String> httpRestResult = clientHttpProxy.executeSyncHttpRequest(httpRequest);
        Result<String> result = JacksonUtils.toObj(httpRestResult.getData(),
                new TypeReference<Result<String>>() {
                });
        return result.getData();
    }
    
    @Override
    public String smartReloadCluster(String loaderFactorStr) throws Exception {
        Map<String, String> params = new HashMap<>(8);
        params.put("loaderFactorStr", loaderFactorStr);
        
        HttpRequest httpRequest = new HttpRequest.Builder()
                .setHttpMethod(HttpMethod.GET)
                .setPath(Constants.AdminApiPath.CORE_LOADER_ADMIN_PATH + "/smartReloadCluster")
                .setParamValue(params)
                .build();
        HttpRestResult<String> httpRestResult = clientHttpProxy.executeSyncHttpRequest(httpRequest);
        Result<String> result = JacksonUtils.toObj(httpRestResult.getData(),
                new TypeReference<Result<String>>() {
                });
        return result.getData();
    }
    
    @Override
    public String reloadSingleClient(String connectionId, String redirectAddress) throws Exception {
        Map<String, String> params = new HashMap<>(8);
        params.put("connectionId", connectionId);
        params.put("redirectAddress", redirectAddress);
        
        HttpRequest httpRequest = new HttpRequest.Builder()
                .setHttpMethod(HttpMethod.GET)
                .setPath(Constants.AdminApiPath.CORE_LOADER_ADMIN_PATH + "/reloadClient")
                .setParamValue(params)
                .build();
        HttpRestResult<String> httpRestResult = clientHttpProxy.executeSyncHttpRequest(httpRequest);
        Result<String> result = JacksonUtils.toObj(httpRestResult.getData(),
                new TypeReference<Result<String>>() {
                });
        return result.getData();
    }
    
    @Override
    public ServerLoaderMetrics getClusterLoaderMetrics() throws Exception {
        HttpRequest httpRequest = new HttpRequest.Builder()
                .setHttpMethod(HttpMethod.GET)
                .setPath(Constants.AdminApiPath.CORE_LOADER_ADMIN_PATH + "/cluster")
                .build();
        HttpRestResult<String> httpRestResult = clientHttpProxy.executeSyncHttpRequest(httpRequest);
        Result<ServerLoaderMetrics> result = JacksonUtils.toObj(httpRestResult.getData(),
                new TypeReference<Result<ServerLoaderMetrics>>() {
                });
        return result.getData();
    }
}