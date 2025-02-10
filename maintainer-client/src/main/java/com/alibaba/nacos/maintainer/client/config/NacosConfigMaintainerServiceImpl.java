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

package com.alibaba.nacos.maintainer.client.config;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.utils.HttpMethod;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.maintainer.client.constants.Constants;
import com.alibaba.nacos.maintainer.client.core.AbstractCoreMaintainerService;
import com.alibaba.nacos.maintainer.client.model.HttpRequest;
import com.alibaba.nacos.maintainer.client.model.config.Capacity;
import com.alibaba.nacos.maintainer.client.model.config.ConfigAdvanceInfo;
import com.alibaba.nacos.maintainer.client.model.config.ConfigAllInfo;
import com.alibaba.nacos.maintainer.client.model.config.ConfigHistoryInfo;
import com.alibaba.nacos.maintainer.client.model.config.ConfigInfo;
import com.alibaba.nacos.maintainer.client.model.config.ConfigInfo4Beta;
import com.alibaba.nacos.maintainer.client.model.config.ConfigInfoWrapper;
import com.alibaba.nacos.maintainer.client.model.config.GroupkeyListenserStatus;
import com.alibaba.nacos.maintainer.client.model.config.Page;
import com.alibaba.nacos.maintainer.client.model.config.SameConfigPolicy;
import com.alibaba.nacos.maintainer.client.model.config.SameNamespaceCloneConfigBean;
import com.alibaba.nacos.maintainer.client.utils.ParamUtil;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Configuration management.
 *
 * @author Nacos
 */
public class NacosConfigMaintainerServiceImpl extends AbstractCoreMaintainerService implements ConfigMaintainerService {
    
    public NacosConfigMaintainerServiceImpl(Properties properties) throws NacosException {
        super(properties);
    }
    
    @Override
    public ConfigAllInfo getConfig(String dataId, String groupName) throws NacosException {
        return getConfig(dataId, groupName, ParamUtil.getDefaultNamespaceId());
    }
    
    @Override
    public ConfigAllInfo getConfig(String dataId, String groupName, String namespaceId) throws NacosException {
        Map<String, String> params = new HashMap<>(8);
        params.put("dataId", dataId);
        params.put("groupName", groupName);
        params.put("namespaceId", namespaceId);
        
        HttpRequest httpRequest = new HttpRequest.Builder().setHttpMethod(HttpMethod.GET)
                .setPath(Constants.AdminApiPath.CONFIG_ADMIN_PATH).setParamValue(params).build();
        HttpRestResult<String> httpRestResult = getClientHttpProxy().executeSyncHttpRequest(httpRequest);
        Result<ConfigAllInfo> result = JacksonUtils.toObj(httpRestResult.getData(),
                new TypeReference<Result<ConfigAllInfo>>() {
                });
        return result.getData();
    }
    
    @Override
    public boolean publishConfig(String dataId, String groupName, String content) throws NacosException {
        return publishConfig(dataId, groupName, ParamUtil.getDefaultNamespaceId(), content);
    }
    
    @Override
    public boolean publishConfig(String dataId, String groupName, String namespaceId, String content)
            throws NacosException {
        return publishConfig(dataId, groupName, namespaceId, content, null, null, null, null, null, null, null, null,
                null);
    }
    
    @Override
    public boolean publishConfig(String dataId, String groupName, String namespaceId, String content, String tag,
            String appName, String srcUser, String configTags, String desc, String use, String effect, String type,
            String schema) throws NacosException {
        Map<String, String> params = new HashMap<>(8);
        params.put("dataId", dataId);
        params.put("groupName", groupName);
        params.put("namespaceId", namespaceId);
        params.put("content", content);
        params.put("tag", tag);
        params.put("appName", appName);
        params.put("srcUser", srcUser);
        params.put("configTags", configTags);
        params.put("desc", desc);
        params.put("use", use);
        params.put("effect", effect);
        params.put("type", type);
        params.put("schema", schema);
        
        HttpRequest httpRequest = new HttpRequest.Builder().setHttpMethod(HttpMethod.POST)
                .setPath(Constants.AdminApiPath.CONFIG_ADMIN_PATH).setParamValue(params).build();
        HttpRestResult<String> httpRestResult = getClientHttpProxy().executeSyncHttpRequest(httpRequest);
        Result<Boolean> result = JacksonUtils.toObj(httpRestResult.getData(), new TypeReference<Result<Boolean>>() {
        });
        return result.getData();
    }
    
    @Override
    public boolean deleteConfig(String dataId, String groupName) throws NacosException {
        return deleteConfig(dataId, groupName, ParamUtil.getDefaultNamespaceId(), null);
    }
    
    @Override
    public boolean deleteConfig(String dataId, String groupName, String namespaceId) throws NacosException {
        return deleteConfig(dataId, groupName, namespaceId, null);
    }
    
    @Override
    public boolean deleteConfig(String dataId, String groupName, String namespaceId, String tag) throws NacosException {
        Map<String, String> params = new HashMap<>(8);
        params.put("dataId", dataId);
        params.put("groupName", groupName);
        params.put("namespaceId", namespaceId);
        params.put("tag", tag);
        
        HttpRequest httpRequest = new HttpRequest.Builder().setHttpMethod(HttpMethod.DELETE)
                .setPath(Constants.AdminApiPath.CONFIG_ADMIN_PATH).setParamValue(params).build();
        HttpRestResult<String> httpRestResult = getClientHttpProxy().executeSyncHttpRequest(httpRequest);
        Result<Boolean> result = JacksonUtils.toObj(httpRestResult.getData(), new TypeReference<Result<Boolean>>() {
        });
        return result.getData();
    }
    
    @Override
    public boolean deleteConfigs(List<Long> ids) throws NacosException {
        Map<String, String> params = new HashMap<>(8);
        StringBuilder idStr = new StringBuilder();
        for (Long id : ids) {
            if (idStr.length() > 0) {
                idStr.append(",");
            }
            idStr.append(id);
        }
        params.put("ids", idStr.toString());
        
        HttpRequest httpRequest = new HttpRequest.Builder().setHttpMethod(HttpMethod.DELETE)
                .setPath(Constants.AdminApiPath.CONFIG_ADMIN_PATH + "/batch").setParamValue(params).build();
        HttpRestResult<String> httpRestResult = getClientHttpProxy().executeSyncHttpRequest(httpRequest);
        Result<Boolean> result = JacksonUtils.toObj(httpRestResult.getData(), new TypeReference<Result<Boolean>>() {
        });
        return result.getData();
    }
    
    @Override
    public ConfigAdvanceInfo getConfigAdvanceInfo(String dataId, String groupName) throws NacosException {
        return getConfigAdvanceInfo(dataId, groupName, ParamUtil.getDefaultNamespaceId());
    }
    
    @Override
    public ConfigAdvanceInfo getConfigAdvanceInfo(String dataId, String groupName, String namespaceId)
            throws NacosException {
        Map<String, String> params = new HashMap<>(8);
        params.put("dataId", dataId);
        params.put("groupName", groupName);
        params.put("namespaceId", namespaceId);
        
        HttpRequest httpRequest = new HttpRequest.Builder().setHttpMethod(HttpMethod.GET)
                .setPath(Constants.AdminApiPath.CONFIG_ADMIN_PATH + "/extInfo").setParamValue(params).build();
        HttpRestResult<String> httpRestResult = getClientHttpProxy().executeSyncHttpRequest(httpRequest);
        Result<ConfigAdvanceInfo> result = JacksonUtils.toObj(httpRestResult.getData(),
                new TypeReference<Result<ConfigAdvanceInfo>>() {
                });
        return result.getData();
    }
    
    @Override
    public Page<ConfigInfo> searchConfigByDetails(String dataId, String groupName, String namespaceId,
            String configDetail, String search, int pageNo, int pageSize) throws NacosException {
        Map<String, String> params = new HashMap<>(8);
        params.put("dataId", dataId);
        params.put("groupName", groupName);
        params.put("namespaceId", namespaceId);
        params.put("configDetail", configDetail);
        params.put("search", search);
        params.put("pageNo", String.valueOf(pageNo));
        params.put("pageSize", String.valueOf(pageSize));
        
        HttpRequest httpRequest = new HttpRequest.Builder().setHttpMethod(HttpMethod.GET)
                .setPath(Constants.AdminApiPath.CONFIG_ADMIN_PATH + "/searchDetail").setParamValue(params).build();
        HttpRestResult<String> httpRestResult = getClientHttpProxy().executeSyncHttpRequest(httpRequest);
        Result<Page<ConfigInfo>> result = JacksonUtils.toObj(httpRestResult.getData(),
                new TypeReference<Result<Page<ConfigInfo>>>() {
                });
        return result.getData();
    }
    
    @Override
    public GroupkeyListenserStatus getListeners(String dataId, String groupName) throws NacosException {
        return getListeners(dataId, groupName, ParamUtil.getDefaultNamespaceId(), 1);
    }
    
    @Override
    public GroupkeyListenserStatus getListeners(String dataId, String groupName, String namespaceId, int sampleTime)
            throws NacosException {
        Map<String, String> params = new HashMap<>(8);
        params.put("dataId", dataId);
        params.put("groupName", groupName);
        params.put("namespaceId", namespaceId);
        params.put("sampleTime", String.valueOf(sampleTime));
        
        HttpRequest httpRequest = new HttpRequest.Builder().setHttpMethod(HttpMethod.GET)
                .setPath(Constants.AdminApiPath.CONFIG_LISTENER_ADMIN_PATH + "/listener").setParamValue(params).build();
        HttpRestResult<String> httpRestResult = getClientHttpProxy().executeSyncHttpRequest(httpRequest);
        Result<GroupkeyListenserStatus> result = JacksonUtils.toObj(httpRestResult.getData(),
                new TypeReference<Result<GroupkeyListenserStatus>>() {
                });
        return result.getData();
    }
    
    @Override
    public boolean stopBeta(String dataId, String groupName) throws NacosException {
        return stopBeta(dataId, groupName, ParamUtil.getDefaultNamespaceId());
    }
    
    @Override
    public boolean stopBeta(String dataId, String groupName, String namespaceId) throws NacosException {
        Map<String, String> params = new HashMap<>(8);
        params.put("dataId", dataId);
        params.put("groupName", groupName);
        params.put("namespaceId", namespaceId);
        
        HttpRequest httpRequest = new HttpRequest.Builder().setHttpMethod(HttpMethod.DELETE)
                .setPath(Constants.AdminApiPath.CONFIG_ADMIN_PATH + "/beta").setParamValue(params).build();
        HttpRestResult<String> httpRestResult = getClientHttpProxy().executeSyncHttpRequest(httpRequest);
        Result<Boolean> result = JacksonUtils.toObj(httpRestResult.getData(), new TypeReference<Result<Boolean>>() {
        });
        return result.getData();
    }
    
    @Override
    public ConfigInfo4Beta queryBeta(String dataId, String groupName) throws NacosException {
        return queryBeta(dataId, groupName, ParamUtil.getDefaultNamespaceId());
    }
    
    @Override
    public ConfigInfo4Beta queryBeta(String dataId, String groupName, String namespaceId) throws NacosException {
        Map<String, String> params = new HashMap<>(8);
        params.put("dataId", dataId);
        params.put("groupName", groupName);
        params.put("namespaceId", namespaceId);
        
        HttpRequest httpRequest = new HttpRequest.Builder().setHttpMethod(HttpMethod.GET)
                .setPath(Constants.AdminApiPath.CONFIG_ADMIN_PATH + "/beta").setParamValue(params).build();
        HttpRestResult<String> httpRestResult = getClientHttpProxy().executeSyncHttpRequest(httpRequest);
        Result<ConfigInfo4Beta> result = JacksonUtils.toObj(httpRestResult.getData(),
                new TypeReference<Result<ConfigInfo4Beta>>() {
                });
        return result.getData();
    }
    
    @Override
    public Map<String, Object> cloneConfig(String namespaceId, List<SameNamespaceCloneConfigBean> configBeansList,
            String srcUser, SameConfigPolicy policy) throws NacosException {
        Map<String, String> params = new HashMap<>(8);
        params.put("namespaceId", namespaceId);
        params.put("srcUser", srcUser);
        params.put("policy", policy.toString());
        params.put("configBeansList", JacksonUtils.toJson(configBeansList));
        
        HttpRequest httpRequest = new HttpRequest.Builder().setHttpMethod(HttpMethod.POST)
                .setPath(Constants.AdminApiPath.CONFIG_ADMIN_PATH + "/clone").setParamValue(params).build();
        HttpRestResult<String> httpRestResult = getClientHttpProxy().executeSyncHttpRequest(httpRequest);
        Result<Map<String, Object>> result = JacksonUtils.toObj(httpRestResult.getData(),
                new TypeReference<Result<Map<String, Object>>>() {
                });
        return result.getData();
    }
    
    @Override
    public Page<ConfigHistoryInfo> listConfigHistory(String dataId, String groupName, String namespaceId, int pageNo,
            int pageSize) throws NacosException {
        Map<String, String> params = new HashMap<>(8);
        params.put("dataId", dataId);
        params.put("groupName", groupName);
        params.put("namespaceId", namespaceId);
        params.put("pageNo", String.valueOf(pageNo));
        params.put("pageSize", String.valueOf(pageSize));
        
        HttpRequest httpRequest = new HttpRequest.Builder().setHttpMethod(HttpMethod.GET)
                .setPath(Constants.AdminApiPath.CONFIG_HISTORY_ADMIN_PATH + "/list").setParamValue(params).build();
        HttpRestResult<String> httpRestResult = getClientHttpProxy().executeSyncHttpRequest(httpRequest);
        Result<Page<ConfigHistoryInfo>> result = JacksonUtils.toObj(httpRestResult.getData(),
                new TypeReference<Result<Page<ConfigHistoryInfo>>>() {
                });
        return result.getData();
    }
    
    @Override
    public ConfigHistoryInfo getConfigHistoryInfo(String dataId, String groupName, String namespaceId, Long nid)
            throws NacosException {
        Map<String, String> params = new HashMap<>(8);
        params.put("dataId", dataId);
        params.put("groupName", groupName);
        params.put("namespaceId", namespaceId);
        params.put("nid", String.valueOf(nid));
        
        HttpRequest httpRequest = new HttpRequest.Builder().setHttpMethod(HttpMethod.GET)
                .setPath(Constants.AdminApiPath.CONFIG_HISTORY_ADMIN_PATH).setParamValue(params).build();
        HttpRestResult<String> httpRestResult = getClientHttpProxy().executeSyncHttpRequest(httpRequest);
        Result<ConfigHistoryInfo> result = JacksonUtils.toObj(httpRestResult.getData(),
                new TypeReference<Result<ConfigHistoryInfo>>() {
                });
        return result.getData();
    }
    
    @Override
    public ConfigHistoryInfo getPreviousConfigHistoryInfo(String dataId, String groupName, String namespaceId, Long id)
            throws NacosException {
        Map<String, String> params = new HashMap<>(8);
        params.put("dataId", dataId);
        params.put("groupName", groupName);
        params.put("namespaceId", namespaceId);
        params.put("id", String.valueOf(id));
        
        HttpRequest httpRequest = new HttpRequest.Builder().setHttpMethod(HttpMethod.GET)
                .setPath(Constants.AdminApiPath.CONFIG_HISTORY_ADMIN_PATH + "/previous").setParamValue(params).build();
        HttpRestResult<String> httpRestResult = getClientHttpProxy().executeSyncHttpRequest(httpRequest);
        Result<ConfigHistoryInfo> result = JacksonUtils.toObj(httpRestResult.getData(),
                new TypeReference<Result<ConfigHistoryInfo>>() {
                });
        return result.getData();
    }
    
    @Override
    public List<ConfigInfoWrapper> getConfigListByNamespace(String namespaceId) throws NacosException {
        Map<String, String> params = new HashMap<>(8);
        params.put("namespaceId", namespaceId);
        
        HttpRequest httpRequest = new HttpRequest.Builder().setHttpMethod(HttpMethod.GET)
                .setPath(Constants.AdminApiPath.CONFIG_HISTORY_ADMIN_PATH + "/configs").setParamValue(params).build();
        HttpRestResult<String> httpRestResult = getClientHttpProxy().executeSyncHttpRequest(httpRequest);
        Result<List<ConfigInfoWrapper>> result = JacksonUtils.toObj(httpRestResult.getData(),
                new TypeReference<Result<List<ConfigInfoWrapper>>>() {
                });
        return result.getData();
    }
    
    @Override
    public Capacity getCapacityWithDefault(String groupName, String namespaceId) throws NacosException {
        Map<String, String> params = new HashMap<>(8);
        params.put("groupName", groupName);
        params.put("namespaceId", namespaceId);
        
        HttpRequest httpRequest = new HttpRequest.Builder().setHttpMethod(HttpMethod.GET)
                .setPath(Constants.AdminApiPath.CONFIG_CAPACITY_ADMIN_PATH).setParamValue(params).build();
        HttpRestResult<String> httpRestResult = getClientHttpProxy().executeSyncHttpRequest(httpRequest);
        Result<Capacity> result = JacksonUtils.toObj(httpRestResult.getData(), new TypeReference<Result<Capacity>>() {
        });
        return result.getData();
    }
    
    @Override
    public boolean insertOrUpdateCapacity(String groupName, String namespaceId, Integer quota, Integer maxSize,
            Integer maxAggrCount, Integer maxAggrSize) throws NacosException {
        Map<String, String> params = new HashMap<>(8);
        params.put("groupName", groupName);
        params.put("namespaceId", namespaceId);
        params.put("quota", String.valueOf(quota));
        params.put("maxSize", String.valueOf(maxSize));
        params.put("maxAggrCount", String.valueOf(maxAggrCount));
        params.put("maxAggrSize", String.valueOf(maxAggrSize));
        
        HttpRequest httpRequest = new HttpRequest.Builder().setHttpMethod(HttpMethod.POST)
                .setPath(Constants.AdminApiPath.CONFIG_CAPACITY_ADMIN_PATH).setParamValue(params).build();
        HttpRestResult<String> httpRestResult = getClientHttpProxy().executeSyncHttpRequest(httpRequest);
        Result<Boolean> result = JacksonUtils.toObj(httpRestResult.getData(), new TypeReference<Result<Boolean>>() {
        });
        return result.getData();
    }
    
    @Override
    public String updateLocalCacheFromStore() throws NacosException {
        HttpRequest httpRequest = new HttpRequest.Builder().setHttpMethod(HttpMethod.POST)
                .setPath(Constants.AdminApiPath.CONFIG_OPS_ADMIN_PATH + "/localCache").build();
        HttpRestResult<String> httpRestResult = getClientHttpProxy().executeSyncHttpRequest(httpRequest);
        Result<String> result = JacksonUtils.toObj(httpRestResult.getData(), new TypeReference<Result<String>>() {
        });
        return result.getData();
    }
    
    @Override
    public String setLogLevel(String logName, String logLevel) throws NacosException {
        Map<String, String> params = new HashMap<>(8);
        params.put("logName", logName);
        params.put("logLevel", logLevel);
        
        HttpRequest httpRequest = new HttpRequest.Builder().setHttpMethod(HttpMethod.PUT)
                .setPath(Constants.AdminApiPath.CONFIG_OPS_ADMIN_PATH + "/log").setParamValue(params).build();
        HttpRestResult<String> httpRestResult = getClientHttpProxy().executeSyncHttpRequest(httpRequest);
        Result<String> result = JacksonUtils.toObj(httpRestResult.getData(), new TypeReference<Result<String>>() {
        });
        return result.getData();
    }
    
    @Override
    public GroupkeyListenserStatus getAllSubClientConfigByIp(String ip, boolean all, String namespaceId, int sampleTime)
            throws NacosException {
        Map<String, String> params = new HashMap<>(8);
        params.put("ip", ip);
        params.put("all", String.valueOf(all));
        params.put("namespaceId", namespaceId);
        params.put("sampleTime", String.valueOf(sampleTime));
        
        HttpRequest httpRequest = new HttpRequest.Builder().setHttpMethod(HttpMethod.GET)
                .setPath(Constants.AdminApiPath.CONFIG_LISTENER_ADMIN_PATH).setParamValue(params).build();
        HttpRestResult<String> httpRestResult = getClientHttpProxy().executeSyncHttpRequest(httpRequest);
        Result<GroupkeyListenserStatus> result = JacksonUtils.toObj(httpRestResult.getData(),
                new TypeReference<Result<GroupkeyListenserStatus>>() {
                });
        return result.getData();
    }
    
    @Override
    public Map<String, Object> getClientMetrics(String ip, String dataId, String groupName, String namespaceId)
            throws NacosException {
        Map<String, String> params = new HashMap<>(8);
        params.put("ip", ip);
        params.put("dataId", dataId);
        params.put("groupName", groupName);
        params.put("namespaceId", namespaceId);
        
        HttpRequest httpRequest = new HttpRequest.Builder().setHttpMethod(HttpMethod.GET)
                .setPath(Constants.AdminApiPath.CONFIG_METRICS_ADMIN_PATH + "/ip").setParamValue(params).build();
        HttpRestResult<String> httpRestResult = getClientHttpProxy().executeSyncHttpRequest(httpRequest);
        Result<Map<String, Object>> result = JacksonUtils.toObj(httpRestResult.getData(),
                new TypeReference<Result<Map<String, Object>>>() {
                });
        return result.getData();
    }
    
    @Override
    public Map<String, Object> getClusterMetrics(String ip, String dataId, String groupName, String namespaceId)
            throws NacosException {
        Map<String, String> params = new HashMap<>(8);
        params.put("ip", ip);
        params.put("dataId", dataId);
        params.put("groupName", groupName);
        params.put("namespaceId", namespaceId);
        
        HttpRequest httpRequest = new HttpRequest.Builder().setHttpMethod(HttpMethod.GET)
                .setPath(Constants.AdminApiPath.CONFIG_METRICS_ADMIN_PATH + "/cluster").setParamValue(params).build();
        HttpRestResult<String> httpRestResult = getClientHttpProxy().executeSyncHttpRequest(httpRequest);
        Result<Map<String, Object>> result = JacksonUtils.toObj(httpRestResult.getData(),
                new TypeReference<Result<Map<String, Object>>>() {
                });
        return result.getData();
    }
}