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

package com.alibaba.nacos.maintainer.client.ai;

import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.a2a.AgentCard;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardDetailInfo;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardVersionInfo;
import com.alibaba.nacos.api.ai.model.a2a.AgentVersionDetail;
import com.alibaba.nacos.api.ai.model.mcp.McpEndpointSpec;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpToolSpecification;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
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
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Nacos AI module maintainer service implementation.
 *
 * @author xiweng.yy
 */
public class NacosAiMaintainerServiceImpl implements AiMaintainerService {
    
    public static final String SEARCH_BLUR = "blur";
    
    public static final String SEARCH_ACCURATE = "accurate";
    
    private final ClientHttpProxy clientHttpProxy;
    
    public NacosAiMaintainerServiceImpl(Properties properties) throws NacosException {
        this.clientHttpProxy = new ClientHttpProxy(properties);
        ParamUtil.initSerialization();
    }
    
    @Override
    public Page<McpServerBasicInfo> listMcpServer(String namespaceId, String mcpName, int pageNo, int pageSize)
            throws NacosException {
        if (StringUtils.isBlank(namespaceId)) {
            namespaceId = AiConstants.Mcp.MCP_DEFAULT_NAMESPACE;
        }
        Map<String, String> params = new HashMap<>(8);
        params.put("pageNo", String.valueOf(pageNo));
        params.put("pageSize", String.valueOf(pageSize));
        params.put("search", "accurate");
        params.put("mcpName", mcpName);
        params.put("namespaceId", namespaceId);
        RequestResource resource = buildRequestResource(namespaceId, mcpName);
        return getMcpServerBasicInfoPage(params, resource);
    }
    
    @Override
    public Page<McpServerBasicInfo> searchMcpServer(String namespaceId, String mcpName, int pageNo, int pageSize)
            throws NacosException {
        if (StringUtils.isBlank(namespaceId)) {
            namespaceId = AiConstants.Mcp.MCP_DEFAULT_NAMESPACE;
        }
        Map<String, String> params = new HashMap<>(8);
        params.put("pageNo", String.valueOf(pageNo));
        params.put("pageSize", String.valueOf(pageSize));
        params.put("search", "blur");
        params.put("mcpName", mcpName);
        params.put("namespaceId", namespaceId);
        RequestResource resource = buildRequestResource(namespaceId, mcpName);
        return getMcpServerBasicInfoPage(params, resource);
    }
    
    private Page<McpServerBasicInfo> getMcpServerBasicInfoPage(Map<String, String> params, RequestResource resource)
            throws NacosException {
        HttpRequest httpRequest = buildHttpRequestBuilder(resource).setHttpMethod(HttpMethod.GET)
                .setPath(Constants.AdminApiPath.AI_MCP_ADMIN_PATH + "/list").setParamValue(params).build();
        HttpRestResult<String> restResult = clientHttpProxy.executeSyncHttpRequest(httpRequest);
        Result<Page<McpServerBasicInfo>> result = JacksonUtils.toObj(restResult.getData(),
                new TypeReference<Result<Page<McpServerBasicInfo>>>() {
                });
        return result.getData();
    }
    
    @Override
    public McpServerDetailInfo getMcpServerDetail(String namespaceId, String mcpName, String mcpId, String version)
            throws NacosException {
        if (StringUtils.isBlank(namespaceId)) {
            namespaceId = AiConstants.Mcp.MCP_DEFAULT_NAMESPACE;
        }
        Map<String, String> params = new HashMap<>(4);
        params.put("mcpName", mcpName);
        params.put("mcpId", mcpId);
        params.put("version", version);
        params.put("namespaceId", namespaceId);
        RequestResource resource = buildRequestResource(namespaceId, mcpName);
        HttpRequest httpRequest = buildHttpRequestBuilder(resource).setHttpMethod(HttpMethod.GET)
                .setPath(Constants.AdminApiPath.AI_MCP_ADMIN_PATH).setParamValue(params).build();
        HttpRestResult<String> restResult = clientHttpProxy.executeSyncHttpRequest(httpRequest);
        Result<McpServerDetailInfo> result = JacksonUtils.toObj(restResult.getData(),
                new TypeReference<Result<McpServerDetailInfo>>() {
                });
        return result.getData();
    }
    
    @Override
    public String createMcpServer(String namespaceId, String mcpName, McpServerBasicInfo serverSpec,
            McpToolSpecification toolSpec, McpEndpointSpec endpointSpec) throws NacosException {
        if (StringUtils.isBlank(namespaceId)) {
            namespaceId = AiConstants.Mcp.MCP_DEFAULT_NAMESPACE;
        }
        Map<String, String> params = buildFullParameters(serverSpec, toolSpec, endpointSpec);
        params.put("namespaceId", namespaceId);
        RequestResource resource = buildRequestResource(namespaceId, mcpName);
        HttpRequest httpRequest = buildHttpRequestBuilder(resource).setHttpMethod(HttpMethod.POST)
                .setPath(Constants.AdminApiPath.AI_MCP_ADMIN_PATH).setParamValue(params).build();
        HttpRestResult<String> restResult = clientHttpProxy.executeSyncHttpRequest(httpRequest);
        Result<String> result = JacksonUtils.toObj(restResult.getData(), new TypeReference<Result<String>>() {
        });
        return result.getData();
    }
    
    @Override
    public boolean updateMcpServer(String namespaceId, String mcpName, boolean isLatest, McpServerBasicInfo serverSpec,
            McpToolSpecification toolSpec, McpEndpointSpec endpointSpec, boolean overrideExisting) throws NacosException {
        if (StringUtils.isBlank(namespaceId)) {
            namespaceId = AiConstants.Mcp.MCP_DEFAULT_NAMESPACE;
        }
        Map<String, String> params = buildFullParameters(serverSpec, toolSpec, endpointSpec);
        params.put("latest", String.valueOf(isLatest));
        params.put("namespaceId", namespaceId);
        params.put("overrideExisting", String.valueOf(overrideExisting));
        RequestResource resource = buildRequestResource(namespaceId, mcpName);
        HttpRequest httpRequest = buildHttpRequestBuilder(resource).setHttpMethod(HttpMethod.PUT)
                .setPath(Constants.AdminApiPath.AI_MCP_ADMIN_PATH).setParamValue(params).build();
        HttpRestResult<String> restResult = clientHttpProxy.executeSyncHttpRequest(httpRequest);
        Result<String> result = JacksonUtils.toObj(restResult.getData(), new TypeReference<Result<String>>() {
        });
        return ErrorCode.SUCCESS.getCode().equals(result.getCode());
    }
    
    private Map<String, String> buildFullParameters(McpServerBasicInfo serverSpec, McpToolSpecification toolSpec,
            McpEndpointSpec endpointSpec) {
        Map<String, String> params = new HashMap<>(4);
        params.put("mcpName", serverSpec.getName());
        params.put("serverSpecification", JacksonUtils.toJson(serverSpec));
        if (null != toolSpec) {
            params.put("toolSpecification", JacksonUtils.toJson(toolSpec));
        }
        if (null != endpointSpec) {
            params.put("endpointSpecification", JacksonUtils.toJson(endpointSpec));
        }
        return params;
    }
    
    @Override
    public boolean deleteMcpServer(String namespaceId, String mcpName, String mcpId, String version)
            throws NacosException {
        if (StringUtils.isBlank(namespaceId)) {
            namespaceId = AiConstants.Mcp.MCP_DEFAULT_NAMESPACE;
        }
        Map<String, String> params = new HashMap<>(4);
        params.put("mcpName", mcpName);
        params.put("mcpId", mcpId);
        params.put("version", version);
        params.put("namespaceId", namespaceId);
        RequestResource resource = buildRequestResource(namespaceId, mcpName);
        HttpRequest httpRequest = buildHttpRequestBuilder(resource).setHttpMethod(HttpMethod.DELETE)
                .setPath(Constants.AdminApiPath.AI_MCP_ADMIN_PATH).setParamValue(params).build();
        HttpRestResult<String> restResult = clientHttpProxy.executeSyncHttpRequest(httpRequest);
        Result<String> result = JacksonUtils.toObj(restResult.getData(), new TypeReference<Result<String>>() {
        });
        return ErrorCode.SUCCESS.getCode().equals(result.getCode());
    }
    
    private RequestResource buildRequestResource(String namespaceId, String resourceName) {
        RequestResource.Builder builder = RequestResource.aiBuilder();
        builder.setNamespace(namespaceId);
        builder.setGroup(com.alibaba.nacos.api.common.Constants.DEFAULT_GROUP);
        builder.setResource(null == resourceName ? StringUtils.EMPTY : resourceName);
        return builder.build();
    }
    
    private HttpRequest.Builder buildHttpRequestBuilder(RequestResource resource) {
        return new HttpRequest.Builder().setResource(resource);
    }
    
    @Override
    public boolean registerAgent(AgentCard agentCard, String namespaceId, String registrationType)
            throws NacosException {
        RequestResource resource = buildRequestResource(namespaceId, agentCard.getName());
        Map<String, String> params = new HashMap<>(4);
        params.put("agentCard", JacksonUtils.toJson(agentCard));
        params.put("namespaceId", namespaceId);
        params.put("agentName", agentCard.getName());
        params.put("registrationType", registrationType);
        HttpRequest request = buildHttpRequestBuilder(resource).setHttpMethod(HttpMethod.POST).setParamValue(params)
                .setPath(Constants.AdminApiPath.AI_AGENT_ADMIN_PATH).build();
        HttpRestResult<String> restResult = clientHttpProxy.executeSyncHttpRequest(request);
        Result<String> result = JacksonUtils.toObj(restResult.getData(), new TypeReference<Result<String>>() {
        });
        
        return ErrorCode.SUCCESS.getCode().equals(result.getCode());
    }
    
    @Override
    public AgentCardDetailInfo getAgentCard(String agentName, String namespaceId, String registrationType)
            throws NacosException {
        RequestResource resource = buildRequestResource(namespaceId, agentName);
        
        Map<String, String> params = new HashMap<>(1);
        params.put("agentName", agentName);
        params.put("namespaceId", namespaceId);
        params.put("registrationType", registrationType);
        
        HttpRequest request = buildHttpRequestBuilder(resource).setHttpMethod(HttpMethod.GET).setParamValue(params)
                .setPath(Constants.AdminApiPath.AI_AGENT_ADMIN_PATH).build();
        HttpRestResult<String> restResult = clientHttpProxy.executeSyncHttpRequest(request);
        Result<AgentCardDetailInfo> result = JacksonUtils.toObj(restResult.getData(),
                new TypeReference<Result<AgentCardDetailInfo>>() {
                });
        
        return result.getData();
    }
    
    @Override
    public boolean updateAgentCard(AgentCard agentCard, String namespaceId, boolean setAsLatest, String registrationType) throws NacosException {
        RequestResource resource = buildRequestResource(namespaceId, agentCard.getName());
        Map<String, String> params = new HashMap<>(5);
        params.put("agentCard", JacksonUtils.toJson(agentCard));
        params.put("namespaceId", namespaceId);
        params.put("agentName", agentCard.getName());
        params.put("setAsLatest", String.valueOf(setAsLatest));
        params.put("registrationType", registrationType);
        HttpRequest request = buildHttpRequestBuilder(resource).setHttpMethod(HttpMethod.PUT).setParamValue(params)
                .setPath(Constants.AdminApiPath.AI_AGENT_ADMIN_PATH).build();
        HttpRestResult<String> restResult = clientHttpProxy.executeSyncHttpRequest(request);
        Result<String> result = JacksonUtils.toObj(restResult.getData(), new TypeReference<Result<String>>() {
        });
        
        return ErrorCode.SUCCESS.getCode().equals(result.getCode());
    }
    
    @Override
    public boolean deleteAgent(String agentName, String namespaceId, String version) throws NacosException {
        RequestResource resource = buildRequestResource(namespaceId, agentName);
        
        Map<String, String> params = new HashMap<>(1);
        params.put("agentName", agentName);
        params.put("namespaceId", namespaceId);
        params.put("version", version);
        
        HttpRequest request = buildHttpRequestBuilder(resource).setHttpMethod(HttpMethod.DELETE).setParamValue(params)
                .setPath(Constants.AdminApiPath.AI_AGENT_ADMIN_PATH).build();
        HttpRestResult<String> restResult = clientHttpProxy.executeSyncHttpRequest(request);
        Result<String> result = JacksonUtils.toObj(restResult.getData(), new TypeReference<Result<String>>() {
        });
        
        return ErrorCode.SUCCESS.getCode().equals(result.getCode());
    }
    
    @Override
    public List<AgentVersionDetail> listAllVersionOfAgent(String agentName, String namespaceId) throws NacosException {
        RequestResource resource = buildRequestResource(namespaceId, agentName);
        
        Map<String, String> params = new HashMap<>(1);
        params.put("agentName", agentName);
        params.put("namespaceId", namespaceId);
        
        HttpRequest request = buildHttpRequestBuilder(resource).setHttpMethod(HttpMethod.GET).setParamValue(params)
                .setPath(Constants.AdminApiPath.AI_AGENT_LIST_VERSION_ADMIN_PATH).build();
        HttpRestResult<String> restResult = clientHttpProxy.executeSyncHttpRequest(request);
        Result<List<AgentVersionDetail>> result = JacksonUtils.toObj(restResult.getData(),
                new TypeReference<Result<List<AgentVersionDetail>>>() {
                });
        return result.getData();
    }
    
    @Override
    public Page<AgentCardVersionInfo> searchAgentCardsByName(String namespaceId, String agentNamePattern, int pageNo,
            int pageSize) throws NacosException {
        return listOrSearchAgentCardsByName(namespaceId, agentNamePattern, pageNo, pageSize, true);
    }
    
    @Override
    public Page<AgentCardVersionInfo> listAgentCards(String namespaceId, String agentName, int pageNo, int pageSize)
            throws NacosException {
        return listOrSearchAgentCardsByName(namespaceId, agentName, pageNo, pageSize, false);
    }
    
    private Page<AgentCardVersionInfo> listOrSearchAgentCardsByName(String namespaceId, String agentName, int pageNo,
            int pageSize, boolean isBlur) throws NacosException {
        RequestResource resource = buildRequestResource(namespaceId, null);
        Map<String, String> params = new HashMap<>(1);
        params.put("agentName", agentName);
        params.put("namespaceId", namespaceId);
        params.put("search", isBlur ? SEARCH_BLUR : SEARCH_ACCURATE);
        params.put("pageNo", String.valueOf(pageNo));
        params.put("pageSize", String.valueOf(pageSize));
        
        HttpRequest request = buildHttpRequestBuilder(resource).setHttpMethod(HttpMethod.GET).setParamValue(params)
                .setPath(Constants.AdminApiPath.AI_AGENT_LIST_ADMIN_PATH).build();
        HttpRestResult<String> restResult = clientHttpProxy.executeSyncHttpRequest(request);
        Result<Page<AgentCardVersionInfo>> result = JacksonUtils.toObj(restResult.getData(),
                new TypeReference<Result<Page<AgentCardVersionInfo>>>() {
                });
        return result.getData();
    }
}
