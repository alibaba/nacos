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
 *
 */

package com.alibaba.nacos.maintainer.client.ai;

import com.alibaba.nacos.api.ai.model.a2a.AgentCard;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardVersionInfo;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardWrapper;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.utils.HttpMethod;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.maintainer.client.constants.Constants;
import com.alibaba.nacos.maintainer.client.model.HttpRequest;
import com.alibaba.nacos.maintainer.client.remote.ClientHttpProxy;
import com.alibaba.nacos.maintainer.client.utils.ParamUtil;
import com.alibaba.nacos.plugin.auth.api.RequestResource;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * NacosA2aMaintainerServiceImpl.
 *
 * @author KiteSoar
 */
public class NacosA2aMaintainerServiceImpl implements A2aMaintainerService {
    
    private final ClientHttpProxy clientHttpProxy;
    
    public NacosA2aMaintainerServiceImpl(Properties properties) throws NacosException {
        this.clientHttpProxy = new ClientHttpProxy(properties);
        ParamUtil.initSerialization();
    }
    
    @Override
    public boolean registerAgent(AgentCard agentCard, String namespaceId) throws NacosException {
        AgentCardWrapper agentCardWrapper = new AgentCardWrapper(agentCard, namespaceId);
        String agentCardJson = JacksonUtils.toJson(agentCardWrapper);
        RequestResource resource = buildRequestResource(namespaceId, agentCard.getName());
        HttpRequest request = buildHttpRequestBuilder(resource).setHttpMethod(HttpMethod.POST).setBody(agentCardJson)
                .setPath(Constants.AdminApiPath.AI_AGENT_ADMIN_PATH).build();
        HttpRestResult<String> restResult = clientHttpProxy.executeSyncHttpRequest(request);
        Result<String> result = JacksonUtils.toObj(restResult.getData(), new TypeReference<Result<String>>() {
        });
        
        return ErrorCode.SUCCESS.getCode().equals(result.getCode());
    }
    
    @Override
    public AgentCardVersionInfo getAgentCardWithVersions(String agentName, String namespaceId) throws NacosException {
        RequestResource resource = buildRequestResource(namespaceId, agentName);
        
        Map<String, String> params = new HashMap<>(1);
        params.put("name", agentName);
        
        HttpRequest request = buildHttpRequestBuilder(resource).setHttpMethod(HttpMethod.GET)
                .setParamValue(params)
                .setPath(Constants.AdminApiPath.AI_AGENT_ADMIN_PATH).build();
        HttpRestResult<String> restResult = clientHttpProxy.executeSyncHttpRequest(request);
        Result<AgentCardVersionInfo> result = JacksonUtils.toObj(restResult.getData(), new TypeReference<Result<AgentCardVersionInfo>>() {
        });
        
        return  result.getData();
    }
    
    @Override
    public boolean updateAgentCard(AgentCard agentCard, String namespaceId) throws NacosException {
        AgentCardWrapper agentCardWrapper = new AgentCardWrapper(agentCard, namespaceId);
        String agentCardJson = JacksonUtils.toJson(agentCardWrapper);
        RequestResource resource = buildRequestResource(namespaceId, agentCard.getName());
        HttpRequest request = buildHttpRequestBuilder(resource).setHttpMethod(HttpMethod.PUT).setBody(agentCardJson)
                .setPath(Constants.AdminApiPath.AI_AGENT_ADMIN_PATH).build();
        HttpRestResult<String> restResult = clientHttpProxy.executeSyncHttpRequest(request);
        Result<String> result = JacksonUtils.toObj(restResult.getData(), new TypeReference<Result<String>>() {
        });
        
        return ErrorCode.SUCCESS.getCode().equals(result.getCode());
    }
    
    @Override
    public boolean deleteAgent(String agentName, String namespaceId) throws NacosException {
        RequestResource resource = buildRequestResource(namespaceId, agentName);
        
        Map<String, String> params = new HashMap<>(1);
        params.put("name", agentName);
        
        HttpRequest request = buildHttpRequestBuilder(resource).setHttpMethod(HttpMethod.DELETE)
                .setParamValue(params)
                .setPath(Constants.AdminApiPath.AI_AGENT_ADMIN_PATH).build();
        HttpRestResult<String> restResult = clientHttpProxy.executeSyncHttpRequest(request);
        Result<AgentCard> result = JacksonUtils.toObj(restResult.getData(), new TypeReference<Result<AgentCard>>() {
        });
        
        return ErrorCode.SUCCESS.getCode().equals(result.getCode());
    }
    
    private RequestResource buildRequestResource(String namespaceId, String agentName) {
        RequestResource.Builder builder = RequestResource.aiBuilder();
        builder.setNamespace(namespaceId);
        builder.setGroup(com.alibaba.nacos.api.common.Constants.DEFAULT_GROUP);
        builder.setResource(null == agentName ? StringUtils.EMPTY : agentName);
        return builder.build();
    }
    
    private HttpRequest.Builder buildHttpRequestBuilder(RequestResource resource) {
        return new HttpRequest.Builder().setResource(resource);
    }
}
