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
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.api.naming.PreservedMetadataKeys;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.Service;
import com.alibaba.nacos.api.naming.pojo.healthcheck.AbstractHealthChecker;
import com.alibaba.nacos.api.naming.pojo.maintainer.ClusterInfo;
import com.alibaba.nacos.api.naming.pojo.maintainer.InstanceMetadataBatchResult;
import com.alibaba.nacos.api.naming.pojo.maintainer.MetricsInfo;
import com.alibaba.nacos.api.naming.pojo.maintainer.ServiceDetailInfo;
import com.alibaba.nacos.api.naming.pojo.maintainer.ServiceView;
import com.alibaba.nacos.api.naming.pojo.maintainer.SubscriberInfo;
import com.alibaba.nacos.api.selector.Selector;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.utils.HttpMethod;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.maintainer.client.constants.Constants;
import com.alibaba.nacos.maintainer.client.core.AbstractCoreMaintainerService;
import com.alibaba.nacos.maintainer.client.model.HttpRequest;
import com.alibaba.nacos.maintainer.client.utils.ParamUtil;
import com.alibaba.nacos.maintainer.client.utils.RequestUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/**
 * NacosNamingMaintainerServiceImpl.
 *
 * @author Nacos
 */
public class NacosNamingMaintainerServiceImpl extends AbstractCoreMaintainerService implements NamingMaintainerService {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(NacosNamingMaintainerServiceImpl.class);
    
    public NacosNamingMaintainerServiceImpl(Properties properties) throws NacosException {
        super(properties);
    }
    
    @Override
    public String createService(Service service) throws NacosException {
        service.validate();
        Map<String, String> params = RequestUtil.toParameters(service);
        HttpRequest httpRequest = new HttpRequest.Builder().setHttpMethod(HttpMethod.POST)
                .setPath(Constants.AdminApiPath.NAMING_SERVICE_ADMIN_PATH).setParamValue(params).build();
        HttpRestResult<String> httpRestResult = getClientHttpProxy().executeSyncHttpRequest(httpRequest);
        Result<String> result = JacksonUtils.toObj(httpRestResult.getData(), new TypeReference<Result<String>>() {
        });
        return result.getData();
    }
    
    @Override
    public String updateService(String serviceName, Map<String, String> newMetadata, float newProtectThreshold,
            Selector newSelector) throws NacosException {
        return updateService(ParamUtil.getDefaultGroupName(), serviceName, newMetadata, newProtectThreshold,
                newSelector);
    }
    
    @Override
    public String updateService(String groupName, String serviceName, Map<String, String> newMetadata,
            float newProtectThreshold, Selector newSelector) throws NacosException {
        return updateService(ParamUtil.getDefaultNamespaceId(), groupName, serviceName, newMetadata,
                newProtectThreshold, newSelector);
    }
    
    @Override
    public String updateService(String namespaceId, String groupName, String serviceName,
            Map<String, String> newMetadata, float newProtectThreshold, Selector newSelector) throws NacosException {
        return updateService(namespaceId, groupName, serviceName, false, newMetadata, newProtectThreshold, newSelector);
    }
    
    @Override
    public String updateService(String namespaceId, String groupName, String serviceName, boolean ephemeral,
            Map<String, String> newMetadata, float newProtectThreshold, Selector newSelector) throws NacosException {
        Service service = new Service();
        service.setNamespaceId(namespaceId);
        service.setGroupName(groupName);
        service.setName(serviceName);
        service.setEphemeral(ephemeral);
        service.setProtectThreshold(newProtectThreshold);
        service.setMetadata(newMetadata);
        service.setSelector(newSelector);
        return updateService(service);
    }
    
    @Override
    public String updateService(Service service) throws NacosException {
        service.validate();
        Map<String, String> params = RequestUtil.toParameters(service);
        HttpRequest httpRequest = new HttpRequest.Builder().setHttpMethod(HttpMethod.PUT)
                .setPath(Constants.AdminApiPath.NAMING_SERVICE_ADMIN_PATH).setParamValue(params).build();
        HttpRestResult<String> httpRestResult = getClientHttpProxy().executeSyncHttpRequest(httpRequest);
        Result<String> result = JacksonUtils.toObj(httpRestResult.getData(), new TypeReference<Result<String>>() {
        });
        return result.getData();
    }
    
    @Override
    public String removeService(String serviceName) throws NacosException {
        return removeService(ParamUtil.getDefaultGroupName(), serviceName);
    }
    
    @Override
    public String removeService(String groupName, String serviceName) throws NacosException {
        return removeService(ParamUtil.getDefaultNamespaceId(), groupName, serviceName);
    }
    
    @Override
    public String removeService(String namespaceId, String groupName, String serviceName) throws NacosException {
        Service service = new Service();
        service.setNamespaceId(namespaceId);
        service.setGroupName(groupName);
        service.setName(serviceName);
        return removeService(service);
    }
    
    @Override
    public String removeService(Service service) throws NacosException {
        service.validate();
        Map<String, String> params = RequestUtil.toParameters(service);
        HttpRequest httpRequest = new HttpRequest.Builder().setHttpMethod(HttpMethod.DELETE)
                .setPath(Constants.AdminApiPath.NAMING_SERVICE_ADMIN_PATH).setParamValue(params).build();
        HttpRestResult<String> httpRestResult = getClientHttpProxy().executeSyncHttpRequest(httpRequest);
        Result<String> result = JacksonUtils.toObj(httpRestResult.getData(), new TypeReference<Result<String>>() {
        });
        return result.getData();
    }
    
    @Override
    public ServiceDetailInfo getServiceDetail(Service service) throws NacosException {
        service.validate();
        Map<String, String> params = RequestUtil.toParameters(service);
        HttpRequest httpRequest = new HttpRequest.Builder().setHttpMethod(HttpMethod.GET)
                .setPath(Constants.AdminApiPath.NAMING_SERVICE_ADMIN_PATH).setParamValue(params).build();
        HttpRestResult<String> httpRestResult = getClientHttpProxy().executeSyncHttpRequest(httpRequest);
        Result<ServiceDetailInfo> result = JacksonUtils.toObj(httpRestResult.getData(),
                new TypeReference<Result<ServiceDetailInfo>>() {
                });
        return result.getData();
    }
    
    @Override
    public List<ServiceView> listServices(String namespaceId, String groupNameParam, String serviceNameParam,
            boolean ignoreEmptyService, int pageNo, int pageSize) throws NacosException {
        HttpRestResult<String> httpRestResult = doListServices(namespaceId, groupNameParam, serviceNameParam, false,
                ignoreEmptyService, pageNo, pageSize);
        Result<List<ServiceView>> result = JacksonUtils.toObj(httpRestResult.getData(),
                new TypeReference<Result<List<ServiceView>>>() {
                });
        return result.getData();
    }
    
    @Override
    public List<ServiceDetailInfo> listServicesWithDetail(String namespaceId, String groupNameParam,
            String serviceNameParam, int pageNo, int pageSize) throws NacosException {
        HttpRestResult<String> httpRestResult = doListServices(namespaceId, groupNameParam, serviceNameParam, true,
                false, pageNo, pageSize);
        Result<List<ServiceDetailInfo>> result = JacksonUtils.toObj(httpRestResult.getData(),
                new TypeReference<Result<List<ServiceDetailInfo>>>() {
                });
        return result.getData();
    }
    
    private HttpRestResult<String> doListServices(String namespaceId, String groupNameParam, String serviceNameParam,
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
        return getClientHttpProxy().executeSyncHttpRequest(httpRequest);
    }
    
    @Override
    public Page<SubscriberInfo> getSubscribers(Service service, int pageNo, int pageSize, boolean aggregation)
            throws NacosException {
        service.validate();
        Map<String, String> params = new HashMap<>(8);
        params.put("namespaceId", service.getNamespaceId());
        params.put("groupName", service.getGroupName());
        params.put("serviceName", service.getName());
        params.put("pageNo", String.valueOf(pageNo));
        params.put("pageSize", String.valueOf(pageSize));
        params.put("aggregation", String.valueOf(aggregation));
        
        HttpRequest httpRequest = new HttpRequest.Builder().setHttpMethod(HttpMethod.GET)
                .setPath(Constants.AdminApiPath.NAMING_SERVICE_ADMIN_PATH + "/subscribers").setParamValue(params)
                .build();
        HttpRestResult<String> httpRestResult = getClientHttpProxy().executeSyncHttpRequest(httpRequest);
        return JacksonUtils.toObj(httpRestResult.getData(), new TypeReference<Result<Page<SubscriberInfo>>>() {
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
    public String registerInstance(Service service, Instance instance) throws NacosException {
        service.validate();
        instance.validate();
        checkEphemeral(service, instance);
        if (instance.isEphemeral()) {
            LOGGER.warn(
                    "Using maintainer client to register an ephemeral instance, the instance will be auto-deregister after {} milliseconds.",
                    instance.getIpDeleteTimeout());
            LOGGER.warn(
                    "Strongly recommended to use the nacos-client for ephemeral instance registration to avoid auto-deregister.");
            LOGGER.warn("If wanted to register ephemeral instance with maintainer client, "
                            + "please set `{}` in instance metadata to delay auto-deregister time.",
                    PreservedMetadataKeys.IP_DELETE_TIMEOUT);
        }
        Map<String, String> params = RequestUtil.toParameters(service, instance);
        HttpRequest httpRequest = new HttpRequest.Builder().setHttpMethod(HttpMethod.POST)
                .setPath(Constants.AdminApiPath.NAMING_INSTANCE_ADMIN_PATH).setParamValue(params).build();
        HttpRestResult<String> httpRestResult = getClientHttpProxy().executeSyncHttpRequest(httpRequest);
        Result<String> result = JacksonUtils.toObj(httpRestResult.getData(), new TypeReference<Result<String>>() {
        });
        return result.getData();
    }
    
    @Override
    public String deregisterInstance(Service service, Instance instance) throws NacosException {
        service.validate();
        instance.validate();
        checkEphemeral(service, instance);
        if (instance.isEphemeral()) {
            LOGGER.warn("De-registering an ephemeral instance from service {}@@{}.", service.getGroupName(),
                    service.getName());
            LOGGER.warn(
                    "De-registering an ephemeral instance might fail due to the nacos-client registered this instance is keeping connection");
            LOGGER.warn(
                    "If you want to de-register this ephemeral instance, please use the nacos-client which registered this instance.");
        }
        Map<String, String> params = RequestUtil.toParameters(service, instance);
        HttpRequest httpRequest = new HttpRequest.Builder().setHttpMethod(HttpMethod.DELETE)
                .setPath(Constants.AdminApiPath.NAMING_INSTANCE_ADMIN_PATH).setParamValue(params).build();
        HttpRestResult<String> httpRestResult = getClientHttpProxy().executeSyncHttpRequest(httpRequest);
        Result<String> result = JacksonUtils.toObj(httpRestResult.getData(), new TypeReference<Result<String>>() {
        });
        return result.getData();
    }
    
    @Override
    public String updateInstance(Service service, Instance instance) throws NacosException {
        service.validate();
        instance.validate();
        checkEphemeral(service, instance);
        Map<String, String> params = RequestUtil.toParameters(service, instance);
        HttpRequest httpRequest = new HttpRequest.Builder().setHttpMethod(HttpMethod.PUT)
                .setPath(Constants.AdminApiPath.NAMING_INSTANCE_ADMIN_PATH).setParamValue(params).build();
        HttpRestResult<String> httpRestResult = getClientHttpProxy().executeSyncHttpRequest(httpRequest);
        Result<String> result = JacksonUtils.toObj(httpRestResult.getData(), new TypeReference<Result<String>>() {
        });
        return result.getData();
    }
    
    @Override
    public InstanceMetadataBatchResult batchUpdateInstanceMetadata(Service service, List<Instance> instances,
            Map<String, String> newMetadata) throws NacosException {
        service.validate();
        if (Objects.isNull(newMetadata)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "Parameter `newMetadata` can't be null");
        }
        if (instances.isEmpty()) {
            return new InstanceMetadataBatchResult(Collections.emptyList());
        }
        for (Instance each : instances) {
            each.validate();
        }
        checkEphemeral(service, instances.get(0));
        Map<String, String> params = RequestUtil.toParameters(service, instances, newMetadata);
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
    public InstanceMetadataBatchResult batchDeleteInstanceMetadata(Service service, List<Instance> instances,
            Map<String, String> newMetadata) throws NacosException {
        service.validate();
        if (Objects.isNull(newMetadata)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "Parameter `newMetadata` can't be null");
        }
        if (instances.isEmpty() || newMetadata.isEmpty()) {
            return new InstanceMetadataBatchResult(Collections.emptyList());
        }
        for (Instance each : instances) {
            each.validate();
        }
        checkEphemeral(service, instances.get(0));
        Map<String, String> params = RequestUtil.toParameters(service, instances, newMetadata);
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
    public String partialUpdateInstance(Service service, Instance instance) throws NacosException {
        service.validate();
        instance.validate();
        checkEphemeral(service, instance);
        Map<String, String> params = RequestUtil.toParameters(service, instance);
        HttpRequest httpRequest = new HttpRequest.Builder().setHttpMethod(HttpMethod.PUT)
                .setPath(Constants.AdminApiPath.NAMING_INSTANCE_ADMIN_PATH + "/partial").setParamValue(params).build();
        HttpRestResult<String> httpRestResult = getClientHttpProxy().executeSyncHttpRequest(httpRequest);
        Result<String> result = JacksonUtils.toObj(httpRestResult.getData(), new TypeReference<Result<String>>() {
        });
        return result.getData();
    }
    
    @Override
    public List<Instance> listInstances(Service service, String clusterName, boolean healthyOnly)
            throws NacosException {
        service.validate();
        if (StringUtils.isBlank(clusterName)) {
            clusterName = com.alibaba.nacos.api.common.Constants.DEFAULT_CLUSTER_NAME;
        }
        Map<String, String> params = new HashMap<>(5);
        params.put("namespaceId", service.getNamespaceId());
        params.put("groupName", service.getGroupName());
        params.put("serviceName", service.getName());
        params.put("clusterName", clusterName);
        params.put("healthyOnly", String.valueOf(healthyOnly));
        HttpRequest httpRequest = new HttpRequest.Builder().setHttpMethod(HttpMethod.GET)
                .setPath(Constants.AdminApiPath.NAMING_INSTANCE_ADMIN_PATH + "/list").setParamValue(params).build();
        HttpRestResult<String> httpRestResult = getClientHttpProxy().executeSyncHttpRequest(httpRequest);
        Result<List<Instance>> result = JacksonUtils.toObj(httpRestResult.getData(),
                new TypeReference<Result<List<Instance>>>() {
                });
        return result.getData();
    }
    
    @Override
    public Instance getInstanceDetail(Service service, Instance instance) throws NacosException {
        service.validate();
        instance.validate();
        Map<String, String> params = RequestUtil.toParameters(service, instance);
        HttpRequest httpRequest = new HttpRequest.Builder().setHttpMethod(HttpMethod.GET)
                .setPath(Constants.AdminApiPath.NAMING_INSTANCE_ADMIN_PATH).setParamValue(params).build();
        HttpRestResult<String> httpRestResult = getClientHttpProxy().executeSyncHttpRequest(httpRequest);
        Result<Instance> result = JacksonUtils.toObj(httpRestResult.getData(), new TypeReference<Result<Instance>>() {
        });
        return result.getData();
    }
    
    private void checkEphemeral(Service service, Instance instance) {
        if (service.isEphemeral() != instance.isEphemeral()) {
            LOGGER.warn(
                    "De-registering instance ephemeral parameters conflict, service: {}, instance: {}, will use instance value.",
                    service.isEphemeral(), instance.isEphemeral());
        }
    }
    
    @Override
    public String updateInstanceHealthStatus(Service service, Instance instance) throws NacosException {
        service.validate();
        instance.validate();
        Map<String, String> params = RequestUtil.toParameters(service, instance);
        if (!service.isEphemeral() || !instance.isEphemeral()) {
            LOGGER.warn("Only persistent instance with NONE health checker can be updated healthy statues.");
            params.put("ephemeral", String.valueOf(Boolean.FALSE));
        }
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
    public String updateCluster(Service service, ClusterInfo cluster) throws NacosException {
        service.validate();
        cluster.validate();
        Map<String, String> params = RequestUtil.toParameters(service, cluster);
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
}