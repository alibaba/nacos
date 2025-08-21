/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.maintainer.client.core;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.response.ConnectionInfo;
import com.alibaba.nacos.api.model.response.IdGeneratorInfo;
import com.alibaba.nacos.api.model.response.NacosMember;
import com.alibaba.nacos.api.model.response.Namespace;
import com.alibaba.nacos.api.model.response.ServerLoaderMetrics;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.utils.HttpMethod;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.maintainer.client.constants.Constants;
import com.alibaba.nacos.maintainer.client.model.HttpRequest;
import com.alibaba.nacos.maintainer.client.remote.ClientHttpProxy;
import com.alibaba.nacos.maintainer.client.utils.ParamUtil;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Abstract core module maintainer service.
 *
 * @author Nacos
 */
public abstract class AbstractCoreMaintainerService implements CoreMaintainerService {
    
    private final ClientHttpProxy clientHttpProxy;
    
    protected AbstractCoreMaintainerService(Properties properties) throws NacosException {
        this.clientHttpProxy = new ClientHttpProxy(properties);
        ParamUtil.initSerialization();
    }
    
    @Override
    public Map<String, String> getServerState() throws NacosException {
        HttpRequest httpRequest = new HttpRequest.Builder().setHttpMethod(HttpMethod.GET)
                .setPath(Constants.AdminApiPath.CORE_STATE_ADMIN_PATH).build();
        HttpRestResult<String> httpRestResult = clientHttpProxy.executeSyncHttpRequest(httpRequest);
        Result<Map<String, String>> result = JacksonUtils.toObj(httpRestResult.getData(),
                new TypeReference<Result<Map<String, String>>>() {
                });
        return result.getData();
    }
    
    @Override
    public Boolean liveness() throws NacosException {
        HttpRequest httpRequest = new HttpRequest.Builder().setHttpMethod(HttpMethod.GET)
                .setPath(Constants.AdminApiPath.CORE_STATE_ADMIN_PATH + "/liveness").build();
        HttpRestResult<String> httpRestResult = clientHttpProxy.executeSyncHttpRequest(httpRequest);
        return httpRestResult.ok();
    }
    
    @Override
    public Boolean readiness() throws NacosException {
        HttpRequest httpRequest = new HttpRequest.Builder().setHttpMethod(HttpMethod.GET)
                .setPath(Constants.AdminApiPath.CORE_OPS_ADMIN_PATH + "/readiness").build();
        HttpRestResult<String> httpRestResult = clientHttpProxy.executeSyncHttpRequest(httpRequest);
        return httpRestResult.ok();
    }
    
    @Override
    public String raftOps(String command, String value, String groupId) throws NacosException {
        Map<String, String> params = new HashMap<>(8);
        params.put("command", command);
        params.put("value", value);
        params.put("groupId", groupId);
        
        HttpRequest httpRequest = new HttpRequest.Builder().setHttpMethod(HttpMethod.POST)
                .setPath(Constants.AdminApiPath.CORE_OPS_ADMIN_PATH + "/raft").setParamValue(params).build();
        HttpRestResult<String> httpRestResult = clientHttpProxy.executeSyncHttpRequest(httpRequest);
        Result<String> result = JacksonUtils.toObj(httpRestResult.getData(), new TypeReference<Result<String>>() {
        });
        return result.getData();
    }
    
    @Override
    public List<IdGeneratorInfo> getIdGenerators() throws NacosException {
        HttpRequest httpRequest = new HttpRequest.Builder().setHttpMethod(HttpMethod.GET)
                .setPath(Constants.AdminApiPath.CORE_OPS_ADMIN_PATH + "/ids").build();
        HttpRestResult<String> httpRestResult = clientHttpProxy.executeSyncHttpRequest(httpRequest);
        Result<List<IdGeneratorInfo>> result = JacksonUtils.toObj(httpRestResult.getData(),
                new TypeReference<Result<List<IdGeneratorInfo>>>() {
                });
        return result.getData();
    }
    
    @Override
    public void updateLogLevel(String logName, String logLevel) throws NacosException {
        Map<String, String> params = new HashMap<>(8);
        params.put("logName", logName);
        params.put("logLevel", logLevel);
        
        HttpRequest httpRequest = new HttpRequest.Builder().setHttpMethod(HttpMethod.PUT)
                .setPath(Constants.AdminApiPath.CORE_OPS_ADMIN_PATH + "/log").setParamValue(params).build();
        clientHttpProxy.executeSyncHttpRequest(httpRequest);
    }
    
    @Override
    public Collection<NacosMember> listClusterNodes(String address, String state) throws NacosException {
        Map<String, String> params = new HashMap<>(8);
        params.put("address", address);
        params.put("state", state);
        
        HttpRequest httpRequest = new HttpRequest.Builder().setHttpMethod(HttpMethod.GET)
                .setPath(Constants.AdminApiPath.CORE_CLUSTER_ADMIN_PATH + "/node/list").setParamValue(params).build();
        HttpRestResult<String> httpRestResult = clientHttpProxy.executeSyncHttpRequest(httpRequest);
        Result<Collection<NacosMember>> result = JacksonUtils.toObj(httpRestResult.getData(),
                new TypeReference<Result<Collection<NacosMember>>>() {
                });
        return result.getData();
    }
    
    @Override
    public Boolean updateLookupMode(String type) throws NacosException {
        Map<String, String> params = new HashMap<>(8);
        params.put("type", type);
        
        HttpRequest httpRequest = new HttpRequest.Builder().setHttpMethod(HttpMethod.PUT)
                .setPath(Constants.AdminApiPath.CORE_CLUSTER_ADMIN_PATH + "/lookup").setParamValue(params).build();
        HttpRestResult<String> httpRestResult = clientHttpProxy.executeSyncHttpRequest(httpRequest);
        Result<Boolean> result = JacksonUtils.toObj(httpRestResult.getData(), new TypeReference<Result<Boolean>>() {
        });
        return result.getData();
    }
    
    @Override
    public Map<String, ConnectionInfo> getCurrentClients() throws NacosException {
        HttpRequest httpRequest = new HttpRequest.Builder().setHttpMethod(HttpMethod.GET)
                .setPath(Constants.AdminApiPath.CORE_LOADER_ADMIN_PATH + "/current").build();
        HttpRestResult<String> httpRestResult = clientHttpProxy.executeSyncHttpRequest(httpRequest);
        Result<Map<String, ConnectionInfo>> result = JacksonUtils.toObj(httpRestResult.getData(),
                new TypeReference<Result<Map<String, ConnectionInfo>>>() {
                });
        return result.getData();
    }
    
    @Override
    public String reloadConnectionCount(Integer count, String redirectAddress) throws NacosException {
        Map<String, String> params = new HashMap<>(8);
        params.put("count", String.valueOf(count));
        params.put("redirectAddress", redirectAddress);
        
        HttpRequest httpRequest = new HttpRequest.Builder().setHttpMethod(HttpMethod.POST)
                .setPath(Constants.AdminApiPath.CORE_LOADER_ADMIN_PATH + "/reloadCurrent").setParamValue(params)
                .build();
        HttpRestResult<String> httpRestResult = clientHttpProxy.executeSyncHttpRequest(httpRequest);
        Result<String> result = JacksonUtils.toObj(httpRestResult.getData(), new TypeReference<Result<String>>() {
        });
        return result.getData();
    }
    
    @Override
    public String smartReloadCluster(String loaderFactorStr) throws NacosException {
        Map<String, String> params = new HashMap<>(8);
        params.put("loaderFactorStr", loaderFactorStr);
        
        HttpRequest httpRequest = new HttpRequest.Builder().setHttpMethod(HttpMethod.POST)
                .setPath(Constants.AdminApiPath.CORE_LOADER_ADMIN_PATH + "/smartReloadCluster").setParamValue(params)
                .build();
        HttpRestResult<String> httpRestResult = clientHttpProxy.executeSyncHttpRequest(httpRequest);
        Result<String> result = JacksonUtils.toObj(httpRestResult.getData(), new TypeReference<Result<String>>() {
        });
        return result.getData();
    }
    
    @Override
    public String reloadSingleClient(String connectionId, String redirectAddress) throws NacosException {
        Map<String, String> params = new HashMap<>(8);
        params.put("connectionId", connectionId);
        params.put("redirectAddress", redirectAddress);
        
        HttpRequest httpRequest = new HttpRequest.Builder().setHttpMethod(HttpMethod.POST)
                .setPath(Constants.AdminApiPath.CORE_LOADER_ADMIN_PATH + "/reloadClient").setParamValue(params).build();
        HttpRestResult<String> httpRestResult = clientHttpProxy.executeSyncHttpRequest(httpRequest);
        Result<String> result = JacksonUtils.toObj(httpRestResult.getData(), new TypeReference<Result<String>>() {
        });
        return result.getData();
    }
    
    @Override
    public ServerLoaderMetrics getClusterLoaderMetrics() throws NacosException {
        HttpRequest httpRequest = new HttpRequest.Builder().setHttpMethod(HttpMethod.GET)
                .setPath(Constants.AdminApiPath.CORE_LOADER_ADMIN_PATH + "/cluster").build();
        HttpRestResult<String> httpRestResult = clientHttpProxy.executeSyncHttpRequest(httpRequest);
        Result<ServerLoaderMetrics> result = JacksonUtils.toObj(httpRestResult.getData(),
                new TypeReference<Result<ServerLoaderMetrics>>() {
                });
        return result.getData();
    }
    
    @Override
    public List<Namespace> getNamespaceList() throws NacosException {
        HttpRequest httpRequest = new HttpRequest.Builder().setHttpMethod(HttpMethod.GET)
                .setPath(Constants.AdminApiPath.CORE_NAMESPACE_ADMIN_PATH + "/list").build();
        HttpRestResult<String> httpRestResult = clientHttpProxy.executeSyncHttpRequest(httpRequest);
        Result<List<Namespace>> result = JacksonUtils.toObj(httpRestResult.getData(),
                new TypeReference<Result<List<Namespace>>>() {
                });
        return result.getData();
    }
    
    @Override
    public Namespace getNamespace(String namespaceId) throws NacosException {
        Map<String, String> params = new HashMap<>(8);
        params.put("namespaceId", namespaceId);
        
        HttpRequest httpRequest = new HttpRequest.Builder().setHttpMethod(HttpMethod.GET)
                .setPath(Constants.AdminApiPath.CORE_NAMESPACE_ADMIN_PATH).setParamValue(params).build();
        HttpRestResult<String> httpRestResult = clientHttpProxy.executeSyncHttpRequest(httpRequest);
        Result<Namespace> result = JacksonUtils.toObj(httpRestResult.getData(), new TypeReference<Result<Namespace>>() {
        });
        return result.getData();
    }
    
    @Override
    public Boolean createNamespace(String namespaceId, String namespaceName, String namespaceDesc)
            throws NacosException {
        Map<String, String> params = new HashMap<>(8);
        params.put("namespaceId", namespaceId);
        params.put("namespaceName", namespaceName);
        params.put("namespaceDesc", namespaceDesc);
        
        HttpRequest httpRequest = new HttpRequest.Builder().setHttpMethod(HttpMethod.POST)
                .setPath(Constants.AdminApiPath.CORE_NAMESPACE_ADMIN_PATH).setParamValue(params).build();
        HttpRestResult<String> httpRestResult = clientHttpProxy.executeSyncHttpRequest(httpRequest);
        Result<Boolean> result = JacksonUtils.toObj(httpRestResult.getData(), new TypeReference<Result<Boolean>>() {
        });
        return result.getData();
    }
    
    @Override
    public Boolean updateNamespace(String namespaceId, String namespaceName, String namespaceDesc)
            throws NacosException {
        Map<String, String> params = new HashMap<>(8);
        params.put("namespaceId", namespaceId);
        params.put("namespaceName", namespaceName);
        params.put("namespaceDesc", namespaceDesc);
        
        HttpRequest httpRequest = new HttpRequest.Builder().setHttpMethod(HttpMethod.PUT)
                .setPath(Constants.AdminApiPath.CORE_NAMESPACE_ADMIN_PATH).setParamValue(params).build();
        HttpRestResult<String> httpRestResult = clientHttpProxy.executeSyncHttpRequest(httpRequest);
        Result<Boolean> result = JacksonUtils.toObj(httpRestResult.getData(), new TypeReference<Result<Boolean>>() {
        });
        return result.getData();
    }
    
    @Override
    public Boolean deleteNamespace(String namespaceId) throws NacosException {
        Map<String, String> params = new HashMap<>(8);
        params.put("namespaceId", namespaceId);
        
        HttpRequest httpRequest = new HttpRequest.Builder().setHttpMethod(HttpMethod.DELETE)
                .setPath(Constants.AdminApiPath.CORE_NAMESPACE_ADMIN_PATH).setParamValue(params).build();
        HttpRestResult<String> httpRestResult = clientHttpProxy.executeSyncHttpRequest(httpRequest);
        Result<Boolean> result = JacksonUtils.toObj(httpRestResult.getData(), new TypeReference<Result<Boolean>>() {
        });
        return result.getData();
    }
    
    @Override
    public Boolean checkNamespaceIdExist(String namespaceId) throws NacosException {
        Map<String, String> params = new HashMap<>(8);
        params.put("namespaceId", namespaceId);
        
        HttpRequest httpRequest = new HttpRequest.Builder().setHttpMethod(HttpMethod.GET)
                .setPath(Constants.AdminApiPath.CORE_NAMESPACE_ADMIN_PATH + "/check").setParamValue(params).build();
        HttpRestResult<String> httpRestResult = clientHttpProxy.executeSyncHttpRequest(httpRequest);
        Result<Integer> result = JacksonUtils.toObj(httpRestResult.getData(), new TypeReference<Result<Integer>>() {
        });
        return result.getData() > 0;
    }
    
    protected ClientHttpProxy getClientHttpProxy() {
        return this.clientHttpProxy;
    }
    
    @Override
    public void shutdown() throws NacosException {
        clientHttpProxy.shutdown();
    }
}
