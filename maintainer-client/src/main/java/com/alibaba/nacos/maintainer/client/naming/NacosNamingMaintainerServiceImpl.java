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
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.api.naming.pojo.healthcheck.AbstractHealthChecker;
import com.alibaba.nacos.api.naming.pojo.maintainer.InstanceMetadataBatchResult;
import com.alibaba.nacos.api.naming.pojo.maintainer.MetricsInfo;
import com.alibaba.nacos.api.naming.pojo.maintainer.ServiceDetailInfo;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.utils.HttpMethod;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.maintainer.client.constants.Constants;
import com.alibaba.nacos.maintainer.client.core.AbstractCoreMaintainerService;
import com.alibaba.nacos.maintainer.client.model.HttpRequest;
import com.alibaba.nacos.maintainer.client.utils.ParamUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * NacosNamingMaintainerServiceImpl.
 *
 * @author Nacos
 */
public class NacosNamingMaintainerServiceImpl extends AbstractCoreMaintainerService implements NamingMaintainerService {
    
    public NacosNamingMaintainerServiceImpl(Properties properties) throws NacosException {
        super(properties);
    }
    
    @Override
    public String createService(String serviceName) throws NacosException {
        return createService(ParamUtil.getDefaultNamespaceId(), ParamUtil.getDefaultGroupName(), serviceName, "", false,
                0.0f, "");
    }
    
    @Override
    public String createService(String namespaceId, String groupName, String serviceName, String metadata,
            boolean ephemeral, float protectThreshold, String selector) throws NacosException {
        Map<String, String> params = new HashMap<>(8);
        params.put("namespaceId", namespaceId);
        params.put("groupName", groupName);
        params.put("serviceName", serviceName);
        params.put("metadata", metadata);
        params.put("ephemeral", String.valueOf(ephemeral));
        params.put("protectThreshold", String.valueOf(protectThreshold));
        params.put("selector", selector);
        
        HttpRequest httpRequest = new HttpRequest.Builder().setHttpMethod(HttpMethod.POST)
                .setPath(Constants.AdminApiPath.NAMING_SERVICE_ADMIN_PATH).setParamValue(params).build();
        HttpRestResult<String> httpRestResult = getClientHttpProxy().executeSyncHttpRequest(httpRequest);
        Result<String> result = JacksonUtils.toObj(httpRestResult.getData(), new TypeReference<Result<String>>() {
        });
        return result.getData();
    }
    
    @Override
    public String updateService(String namespaceId, String groupName, String serviceName, String metadata,
            boolean ephemeral, float protectThreshold, String selector) throws NacosException {
        Map<String, String> params = new HashMap<>(8);
        params.put("namespaceId", namespaceId);
        params.put("groupName", groupName);
        params.put("serviceName", serviceName);
        params.put("metadata", metadata);
        params.put("ephemeral", String.valueOf(ephemeral));
        params.put("protectThreshold", String.valueOf(protectThreshold));
        params.put("selector", selector);
        
        HttpRequest httpRequest = new HttpRequest.Builder().setHttpMethod(HttpMethod.PUT)
                .setPath(Constants.AdminApiPath.NAMING_SERVICE_ADMIN_PATH).setParamValue(params).build();
        HttpRestResult<String> httpRestResult = getClientHttpProxy().executeSyncHttpRequest(httpRequest);
        Result<String> result = JacksonUtils.toObj(httpRestResult.getData(), new TypeReference<Result<String>>() {
            
        });
        return result.getData();
    }
    
    @Override
    public String removeService(String namespaceId, String groupName, String serviceName) throws NacosException {
        Map<String, String> params = new HashMap<>(8);
        params.put("namespaceId", namespaceId);
        params.put("groupName", groupName);
        params.put("serviceName", serviceName);
        
        HttpRequest httpRequest = new HttpRequest.Builder().setHttpMethod(HttpMethod.DELETE)
                .setPath(Constants.AdminApiPath.NAMING_SERVICE_ADMIN_PATH).setParamValue(params).build();
        HttpRestResult<String> httpRestResult = getClientHttpProxy().executeSyncHttpRequest(httpRequest);
        Result<String> result = JacksonUtils.toObj(httpRestResult.getData(), new TypeReference<Result<String>>() {
        });
        return result.getData();
    }
    
    @Override
    public ServiceDetailInfo getServiceDetail(String namespaceId, String groupName, String serviceName)
            throws NacosException {
        Map<String, String> params = new HashMap<>(8);
        params.put("namespaceId", namespaceId);
        params.put("groupName", groupName);
        params.put("serviceName", serviceName);
        
        HttpRequest httpRequest = new HttpRequest.Builder().setHttpMethod(HttpMethod.GET)
                .setPath(Constants.AdminApiPath.NAMING_SERVICE_ADMIN_PATH).setParamValue(params).build();
        HttpRestResult<String> httpRestResult = getClientHttpProxy().executeSyncHttpRequest(httpRequest);
        Result<ServiceDetailInfo> result = JacksonUtils.toObj(httpRestResult.getData(),
                new TypeReference<Result<ServiceDetailInfo>>() {
                });
        return result.getData();
    }
    
    @Override
    public Object listServices(String namespaceId, String groupNameParam, String serviceNameParam,
            boolean withInstances, boolean ignoreEmptyService, int pageNo, int pageSize) throws NacosException {
        Map<String, String> params = new HashMap<>(8);
        params.put("namespaceId", namespaceId);
        params.put("groupNameParam", groupNameParam);
        params.put("serviceNameParam", serviceNameParam);
        params.put("withInstances", String.valueOf(withInstances));
        params.put("ignoreEmptyService", String.valueOf(ignoreEmptyService));
        params.put("pageNo", String.valueOf(pageNo));
        params.put("pageSize", String.valueOf(pageSize));
        
        HttpRequest httpRequest = new HttpRequest.Builder().setHttpMethod(HttpMethod.GET)
                .setPath(Constants.AdminApiPath.NAMING_SERVICE_ADMIN_PATH + "/list").setParamValue(params).build();
        HttpRestResult<String> httpRestResult = getClientHttpProxy().executeSyncHttpRequest(httpRequest);
        Result<Object> result = JacksonUtils.toObj(httpRestResult.getData(), new TypeReference<Result<Object>>() {
        });
        // TODO uniform return type.
        return result.getData();
    }
    
    @Override
    public ObjectNode searchService(String namespaceId, String expr) throws NacosException {
        Map<String, String> params = new HashMap<>(8);
        params.put("namespaceId", namespaceId);
        params.put("expr", expr);
        
        HttpRequest httpRequest = new HttpRequest.Builder().setHttpMethod(HttpMethod.GET)
                .setPath(Constants.AdminApiPath.NAMING_SERVICE_ADMIN_PATH + "/names").setParamValue(params).build();
        HttpRestResult<String> httpRestResult = getClientHttpProxy().executeSyncHttpRequest(httpRequest);
        return JacksonUtils.toObj(httpRestResult.getData(), ObjectNode.class);
    }
    
    @Override
    public ObjectNode getSubscribers(String namespaceId, String groupName, String serviceName, int pageNo, int pageSize,
            boolean aggregation) throws NacosException {
        Map<String, String> params = new HashMap<>(8);
        params.put("namespaceId", namespaceId);
        params.put("groupName", groupName);
        params.put("serviceName", serviceName);
        params.put("pageNo", String.valueOf(pageNo));
        params.put("pageSize", String.valueOf(pageSize));
        params.put("aggregation", String.valueOf(aggregation));
        
        HttpRequest httpRequest = new HttpRequest.Builder().setHttpMethod(HttpMethod.GET)
                .setPath(Constants.AdminApiPath.NAMING_SERVICE_ADMIN_PATH + "/subscribers").setParamValue(params)
                .build();
        HttpRestResult<String> httpRestResult = getClientHttpProxy().executeSyncHttpRequest(httpRequest);
        return JacksonUtils.toObj(httpRestResult.getData(), new TypeReference<Result<ObjectNode>>() {
        }).getData();
    }
    
    @Override
    public List<String> listSelectorTypes() throws NacosException {
        HttpRequest httpRequest = new HttpRequest.Builder().setHttpMethod(HttpMethod.GET)
                .setPath(Constants.AdminApiPath.NAMING_SERVICE_ADMIN_PATH + "/selector/types").build();
        HttpRestResult<String> httpRestResult = getClientHttpProxy().executeSyncHttpRequest(httpRequest);
        return JacksonUtils.toObj(httpRestResult.getData(), new TypeReference<List<String>>() {
        });
    }
    
    @Override
    public MetricsInfo getMetrics(boolean onlyStatus) throws NacosException {
        Map<String, String> params = new HashMap<>(8);
        params.put("onlyStatus", String.valueOf(onlyStatus));
        
        HttpRequest httpRequest = new HttpRequest.Builder().setHttpMethod(HttpMethod.GET)
                .setPath(Constants.AdminApiPath.NAMING_OPS_ADMIN_PATH + "/metrics").setParamValue(params).build();
        HttpRestResult<String> httpRestResult = getClientHttpProxy().executeSyncHttpRequest(httpRequest);
        Result<MetricsInfo> result = JacksonUtils.toObj(httpRestResult.getData(),
                new TypeReference<Result<MetricsInfo>>() {
                });
        return result.getData();
    }
    
    @Override
    public String setLogLevel(String logName, String logLevel) throws NacosException {
        Map<String, String> params = new HashMap<>(8);
        params.put("logName", logName);
        params.put("logLevel", logLevel);
        
        HttpRequest httpRequest = new HttpRequest.Builder().setHttpMethod(HttpMethod.PUT)
                .setPath(Constants.AdminApiPath.NAMING_OPS_ADMIN_PATH + "/log").setParamValue(params).build();
        HttpRestResult<String> httpRestResult = getClientHttpProxy().executeSyncHttpRequest(httpRequest);
        Result<String> result = JacksonUtils.toObj(httpRestResult.getData(), new TypeReference<Result<String>>() {
        });
        return result.getData();
    }
    
    @Override
    public String registerInstance(String namespaceId, String groupName, String serviceName, String clusterName,
            String ip, int port, String weight, boolean healthy, boolean enabled, String ephemeral, String metadata)
            throws NacosException {
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
        
        HttpRequest httpRequest = new HttpRequest.Builder().setHttpMethod(HttpMethod.POST)
                .setPath(Constants.AdminApiPath.NAMING_INSTANCE_ADMIN_PATH).setParamValue(params).build();
        HttpRestResult<String> httpRestResult = getClientHttpProxy().executeSyncHttpRequest(httpRequest);
        Result<String> result = JacksonUtils.toObj(httpRestResult.getData(), new TypeReference<Result<String>>() {
        });
        return result.getData();
    }
    
    @Override
    public String deregisterInstance(String namespaceId, String groupName, String serviceName, String clusterName,
            String ip, int port, String weight, boolean healthy, boolean enabled, String ephemeral, String metadata)
            throws NacosException {
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
        
        HttpRequest httpRequest = new HttpRequest.Builder().setHttpMethod(HttpMethod.DELETE)
                .setPath(Constants.AdminApiPath.NAMING_INSTANCE_ADMIN_PATH).setParamValue(params).build();
        HttpRestResult<String> httpRestResult = getClientHttpProxy().executeSyncHttpRequest(httpRequest);
        Result<String> result = JacksonUtils.toObj(httpRestResult.getData(), new TypeReference<Result<String>>() {
        });
        return result.getData();
    }
    
    @Override
    public String updateInstance(String namespaceId, String groupName, String serviceName, String clusterName,
            String ip, int port, String weight, boolean healthy, boolean enabled, String ephemeral, String metadata)
            throws NacosException {
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
        
        HttpRequest httpRequest = new HttpRequest.Builder().setHttpMethod(HttpMethod.PUT)
                .setPath(Constants.AdminApiPath.NAMING_INSTANCE_ADMIN_PATH).setParamValue(params).build();
        HttpRestResult<String> httpRestResult = getClientHttpProxy().executeSyncHttpRequest(httpRequest);
        Result<String> result = JacksonUtils.toObj(httpRestResult.getData(), new TypeReference<Result<String>>() {
        });
        return result.getData();
    }
    
    @Override
    public InstanceMetadataBatchResult batchUpdateInstanceMetadata(String namespaceId, String groupName,
            String serviceName, String instance, Map<String, String> metadata, String consistencyType)
            throws NacosException {
        Map<String, String> params = new HashMap<>(8);
        params.put("namespaceId", namespaceId);
        params.put("groupName", groupName);
        params.put("serviceName", serviceName);
        params.put("instance", instance);
        params.put("consistencyType", consistencyType);
        params.put("metadata", JacksonUtils.toJson(metadata));
        
        HttpRequest httpRequest = new HttpRequest.Builder().setHttpMethod(HttpMethod.PUT)
                .setPath(Constants.AdminApiPath.NAMING_INSTANCE_ADMIN_PATH + "/metadata/batch").setParamValue(params)
                .build();
        HttpRestResult<String> httpRestResult = getClientHttpProxy().executeSyncHttpRequest(httpRequest);
        Result<InstanceMetadataBatchResult> result = JacksonUtils.toObj(httpRestResult.getData(),
                new TypeReference<Result<InstanceMetadataBatchResult>>() {
                });
        return result.getData();
    }
    
    @Override
    public InstanceMetadataBatchResult batchDeleteInstanceMetadata(String namespaceId, String groupName,
            String serviceName, String instance, Map<String, String> metadata, String consistencyType)
            throws NacosException {
        Map<String, String> params = new HashMap<>(8);
        params.put("namespaceId", namespaceId);
        params.put("groupName", groupName);
        params.put("serviceName", serviceName);
        params.put("instance", instance);
        params.put("consistencyType", consistencyType);
        params.put("metadata", JacksonUtils.toJson(metadata));
        
        HttpRequest httpRequest = new HttpRequest.Builder().setHttpMethod(HttpMethod.DELETE)
                .setPath(Constants.AdminApiPath.NAMING_INSTANCE_ADMIN_PATH + "/metadata/batch").setParamValue(params)
                .build();
        HttpRestResult<String> httpRestResult = getClientHttpProxy().executeSyncHttpRequest(httpRequest);
        Result<InstanceMetadataBatchResult> result = JacksonUtils.toObj(httpRestResult.getData(),
                new TypeReference<Result<InstanceMetadataBatchResult>>() {
                });
        return result.getData();
    }
    
    @Override
    public String partialUpdateInstance(String namespaceId, String serviceName, String clusterName, int ip, int port,
            double weight, boolean enabled, String metadata) throws NacosException {
        Map<String, String> params = new HashMap<>(8);
        params.put("namespaceId", namespaceId);
        params.put("serviceName", serviceName);
        params.put("clusterName", clusterName);
        params.put("ip", String.valueOf(ip));
        params.put("port", String.valueOf(port));
        params.put("weight", String.valueOf(weight));
        params.put("enabled", String.valueOf(enabled));
        params.put("metadata", metadata);
        
        HttpRequest httpRequest = new HttpRequest.Builder().setHttpMethod(HttpMethod.PUT)
                .setPath(Constants.AdminApiPath.NAMING_INSTANCE_ADMIN_PATH + "/partial").setParamValue(params).build();
        HttpRestResult<String> httpRestResult = getClientHttpProxy().executeSyncHttpRequest(httpRequest);
        Result<String> result = JacksonUtils.toObj(httpRestResult.getData(), new TypeReference<Result<String>>() {
        });
        return result.getData();
    }
    
    @Override
    public ServiceInfo listInstances(String namespaceId, String groupName, String serviceName, String clusterName,
            String ip, int port, boolean healthyOnly) throws NacosException {
        Map<String, String> params = new HashMap<>(8);
        params.put("namespaceId", namespaceId);
        params.put("groupName", groupName);
        params.put("serviceName", serviceName);
        params.put("clusterName", clusterName);
        params.put("ip", ip);
        params.put("port", String.valueOf(port));
        params.put("healthyOnly", String.valueOf(healthyOnly));
        
        HttpRequest httpRequest = new HttpRequest.Builder().setHttpMethod(HttpMethod.GET)
                .setPath(Constants.AdminApiPath.NAMING_INSTANCE_ADMIN_PATH + "/list").setParamValue(params).build();
        HttpRestResult<String> httpRestResult = getClientHttpProxy().executeSyncHttpRequest(httpRequest);
        return JacksonUtils.toObj(httpRestResult.getData(), ServiceInfo.class);
    }
    
    @Override
    public Instance getInstanceDetail(String namespaceId, String groupName, String serviceName, String clusterName,
            String ip, int port) throws NacosException {
        Map<String, String> params = new HashMap<>(8);
        params.put("namespaceId", namespaceId);
        params.put("groupName", groupName);
        params.put("serviceName", serviceName);
        params.put("clusterName", clusterName);
        params.put("ip", ip);
        params.put("port", String.valueOf(port));
        
        HttpRequest httpRequest = new HttpRequest.Builder().setHttpMethod(HttpMethod.GET)
                .setPath(Constants.AdminApiPath.NAMING_INSTANCE_ADMIN_PATH).setParamValue(params).build();
        HttpRestResult<String> httpRestResult = getClientHttpProxy().executeSyncHttpRequest(httpRequest);
        Result<Instance> result = JacksonUtils.toObj(httpRestResult.getData(), new TypeReference<Result<Instance>>() {
        });
        return result.getData();
    }
    
    @Override
    public String updateInstanceHealthStatus(String namespaceId, String groupName, String serviceName,
            String clusterName, String metadata, boolean ephemeral, float protectThreshold, String selector)
            throws NacosException {
        Map<String, String> params = new HashMap<>(8);
        params.put("namespaceId", namespaceId);
        params.put("groupName", groupName);
        params.put("serviceName", serviceName);
        params.put("clusterName", clusterName);
        params.put("metadata", metadata);
        params.put("ephemeral", String.valueOf(ephemeral));
        params.put("protectThreshold", String.valueOf(protectThreshold));
        params.put("selector", selector);
        
        HttpRequest httpRequest = new HttpRequest.Builder().setHttpMethod(HttpMethod.PUT)
                .setPath(Constants.AdminApiPath.NAMING_HEALTH_ADMIN_PATH + "/instance").setParamValue(params).build();
        
        HttpRestResult<String> httpRestResult = getClientHttpProxy().executeSyncHttpRequest(httpRequest);
        Result<String> result = JacksonUtils.toObj(httpRestResult.getData(), new TypeReference<Result<String>>() {
        });
        return result.getData();
    }
    
    @Override
    public Map<String, AbstractHealthChecker> getHealthCheckers() throws NacosException {
        HttpRequest httpRequest = new HttpRequest.Builder().setHttpMethod(HttpMethod.GET)
                .setPath(Constants.AdminApiPath.NAMING_HEALTH_ADMIN_PATH + "/checkers").build();
        HttpRestResult<String> httpRestResult = getClientHttpProxy().executeSyncHttpRequest(httpRequest);
        return JacksonUtils.toObj(httpRestResult.getData(), new TypeReference<Map<String, AbstractHealthChecker>>() {
        });
    }
    
    @Override
    public String updateCluster(String namespaceId, String groupName, String serviceName, String clusterName, Integer checkPort,
            Boolean useInstancePort4Check, String healthChecker, Map<String, String> metadata) throws NacosException {
        Map<String, String> params = new HashMap<>(8);
        params.put("namespaceId", namespaceId);
        params.put("groupName", groupName);
        params.put("serviceName", serviceName);
        params.put("clusterName", clusterName);
        params.put("checkPort", String.valueOf(checkPort));
        params.put("useInstancePort4Check", String.valueOf(useInstancePort4Check));
        params.put("healthChecker", healthChecker);
        params.put("metadata", JacksonUtils.toJson(metadata));
        
        HttpRequest httpRequest = new HttpRequest.Builder().setHttpMethod(HttpMethod.PUT)
                .setPath(Constants.AdminApiPath.NAMING_CLUSTER_ADMIN_PATH).setParamValue(params).build();
        HttpRestResult<String> httpRestResult = getClientHttpProxy().executeSyncHttpRequest(httpRequest);
        Result<String> result = JacksonUtils.toObj(httpRestResult.getData(), new TypeReference<Result<String>>() {
        });
        return result.getData();
    }
    
    @Override
    public List<String> getClientList() throws NacosException {
        HttpRequest httpRequest = new HttpRequest.Builder().setHttpMethod(HttpMethod.GET)
                .setPath(Constants.AdminApiPath.NAMING_CLIENT_ADMIN_PATH + "/list").build();
        HttpRestResult<String> httpRestResult = getClientHttpProxy().executeSyncHttpRequest(httpRequest);
        return JacksonUtils.toObj(httpRestResult.getData(), new TypeReference<List<String>>() {
        });
    }
    
    @Override
    public ObjectNode getClientDetail(String clientId) throws NacosException {
        Map<String, String> params = new HashMap<>(8);
        params.put("clientId", clientId);
        
        HttpRequest httpRequest = new HttpRequest.Builder().setHttpMethod(HttpMethod.GET)
                .setPath(Constants.AdminApiPath.NAMING_CLIENT_ADMIN_PATH).setParamValue(params).build();
        HttpRestResult<String> httpRestResult = getClientHttpProxy().executeSyncHttpRequest(httpRequest);
        return JacksonUtils.toObj(httpRestResult.getData(), ObjectNode.class);
    }
    
    @Override
    public List<ObjectNode> getPublishedServiceList(String clientId) throws NacosException {
        Map<String, String> params = new HashMap<>(8);
        params.put("clientId", clientId);
        
        HttpRequest httpRequest = new HttpRequest.Builder().setHttpMethod(HttpMethod.GET)
                .setPath(Constants.AdminApiPath.NAMING_CLIENT_ADMIN_PATH + "/publish/list").setParamValue(params)
                .build();
        HttpRestResult<String> httpRestResult = getClientHttpProxy().executeSyncHttpRequest(httpRequest);
        return JacksonUtils.toObj(httpRestResult.getData(), new TypeReference<List<ObjectNode>>() {
        });
    }
    
    @Override
    public List<ObjectNode> getSubscribeServiceList(String clientId) throws NacosException {
        Map<String, String> params = new HashMap<>(8);
        params.put("clientId", clientId);
        
        HttpRequest httpRequest = new HttpRequest.Builder().setHttpMethod(HttpMethod.GET)
                .setPath(Constants.AdminApiPath.NAMING_CLIENT_ADMIN_PATH + "/subscribe/list").setParamValue(params)
                .build();
        HttpRestResult<String> httpRestResult = getClientHttpProxy().executeSyncHttpRequest(httpRequest);
        return JacksonUtils.toObj(httpRestResult.getData(), new TypeReference<List<ObjectNode>>() {
        });
    }
    
    @Override
    public List<ObjectNode> getPublishedClientList(String namespaceId, String groupName, String serviceName,
            boolean ephemeral, String ip, Integer port) throws NacosException {
        Map<String, String> params = new HashMap<>(8);
        params.put("namespaceId", namespaceId);
        params.put("groupName", groupName);
        params.put("serviceName", serviceName);
        params.put("ephemeral", String.valueOf(ephemeral));
        params.put("ip", ip);
        params.put("port", String.valueOf(port));
        
        HttpRequest httpRequest = new HttpRequest.Builder().setHttpMethod(HttpMethod.GET)
                .setPath(Constants.AdminApiPath.NAMING_CLIENT_ADMIN_PATH + "/service/publisher/list")
                .setParamValue(params).build();
        HttpRestResult<String> httpRestResult = getClientHttpProxy().executeSyncHttpRequest(httpRequest);
        return JacksonUtils.toObj(httpRestResult.getData(), new TypeReference<List<ObjectNode>>() {
        });
    }
    
    @Override
    public List<ObjectNode> getSubscribeClientList(String namespaceId, String groupName, String serviceName,
            boolean ephemeral, String ip, Integer port) throws NacosException {
        Map<String, String> params = new HashMap<>(8);
        params.put("namespaceId", namespaceId);
        params.put("groupName", groupName);
        params.put("serviceName", serviceName);
        params.put("ephemeral", String.valueOf(ephemeral));
        params.put("ip", ip);
        params.put("port", String.valueOf(port));
        
        HttpRequest httpRequest = new HttpRequest.Builder().setHttpMethod(HttpMethod.GET)
                .setPath(Constants.AdminApiPath.NAMING_CLIENT_ADMIN_PATH + "/service/subscriber/list")
                .setParamValue(params).build();
        HttpRestResult<String> httpRestResult = getClientHttpProxy().executeSyncHttpRequest(httpRequest);
        return JacksonUtils.toObj(httpRestResult.getData(), new TypeReference<List<ObjectNode>>() {
        });
    }
    
    @Override
    public ObjectNode getResponsibleServerForClient(String ip, String port) throws NacosException {
        Map<String, String> params = new HashMap<>(8);
        params.put("ip", ip);
        params.put("port", port);
        
        HttpRequest httpRequest = new HttpRequest.Builder().setHttpMethod(HttpMethod.GET)
                .setPath(Constants.AdminApiPath.NAMING_CLIENT_ADMIN_PATH + "/distro").setParamValue(params).build();
        HttpRestResult<String> httpRestResult = getClientHttpProxy().executeSyncHttpRequest(httpRequest);
        return JacksonUtils.toObj(httpRestResult.getData(), ObjectNode.class);
    }
}