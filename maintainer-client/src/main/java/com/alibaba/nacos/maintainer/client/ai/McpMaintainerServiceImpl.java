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
import com.alibaba.nacos.maintainer.client.constants.Constants;
import com.alibaba.nacos.maintainer.client.model.HttpRequest;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.HashMap;
import java.util.Map;

final class McpMaintainerServiceImpl extends AbstractAiDelegateMaintainerService
    implements McpMaintainerService {
    
    private static final String SEARCH_BLUR = "blur";
    
    private static final String SEARCH_ACCURATE = "accurate";
    
    McpMaintainerServiceImpl(AiMaintainerHttpContext context) {
        super(context);
    }
    
    @Override
    public Page<McpServerBasicInfo> listMcpServer(String namespaceId, String mcpName, int pageNo,
        int pageSize)
        throws NacosException {
        return queryServerPage(namespaceId, mcpName, pageNo, pageSize, SEARCH_ACCURATE);
    }
    
    @Override
    public Page<McpServerBasicInfo> searchMcpServer(String namespaceId, String mcpName, int pageNo,
        int pageSize)
        throws NacosException {
        return queryServerPage(namespaceId, mcpName, pageNo, pageSize, SEARCH_BLUR);
    }
    
    @Override
    public McpServerDetailInfo getMcpServerDetail(String namespaceId, String mcpName, String mcpId,
        String version)
        throws NacosException {
        namespaceId = resolveMcpNamespace(namespaceId);
        Map<String, String> params = new HashMap<>(4);
        params.put("mcpName", mcpName);
        params.put("mcpId", mcpId);
        params.put("version", version);
        params.put("namespaceId", namespaceId);
        HttpRequest httpRequest =
            buildHttpRequestBuilder(buildRequestResource(namespaceId, mcpName))
                .setHttpMethod(HttpMethod.GET).setPath(Constants.AdminApiPath.AI_MCP_ADMIN_PATH)
                .setParamValue(params).build();
        HttpRestResult<String> restResult = executeSyncHttpRequest(httpRequest);
        Result<McpServerDetailInfo> result = JacksonUtils.toObj(restResult.getData(),
            new TypeReference<Result<McpServerDetailInfo>>() {
            });
        return result.getData();
    }
    
    @Override
    public String createMcpServer(String namespaceId, String mcpName, McpServerBasicInfo serverSpec,
        McpToolSpecification toolSpec, McpEndpointSpec endpointSpec) throws NacosException {
        namespaceId = resolveMcpNamespace(namespaceId);
        Map<String, String> params = buildFullParameters(serverSpec, toolSpec, endpointSpec);
        params.put("namespaceId", namespaceId);
        HttpRequest httpRequest =
            buildHttpRequestBuilder(buildRequestResource(namespaceId, mcpName))
                .setHttpMethod(HttpMethod.POST).setPath(Constants.AdminApiPath.AI_MCP_ADMIN_PATH)
                .setParamValue(params).build();
        HttpRestResult<String> restResult = executeSyncHttpRequest(httpRequest);
        Result<String> result =
            JacksonUtils.toObj(restResult.getData(), new TypeReference<Result<String>>() {
            });
        return result.getData();
    }
    
    @Override
    public boolean updateMcpServer(String namespaceId, String mcpName, boolean isLatest,
        McpServerBasicInfo serverSpec,
        McpToolSpecification toolSpec, McpEndpointSpec endpointSpec, boolean overrideExisting)
        throws NacosException {
        namespaceId = resolveMcpNamespace(namespaceId);
        Map<String, String> params = buildFullParameters(serverSpec, toolSpec, endpointSpec);
        params.put("latest", String.valueOf(isLatest));
        params.put("namespaceId", namespaceId);
        params.put("overrideExisting", String.valueOf(overrideExisting));
        HttpRequest httpRequest =
            buildHttpRequestBuilder(buildRequestResource(namespaceId, mcpName))
                .setHttpMethod(HttpMethod.PUT).setPath(Constants.AdminApiPath.AI_MCP_ADMIN_PATH)
                .setParamValue(params).build();
        HttpRestResult<String> restResult = executeSyncHttpRequest(httpRequest);
        Result<String> result =
            JacksonUtils.toObj(restResult.getData(), new TypeReference<Result<String>>() {
            });
        return ErrorCode.SUCCESS.getCode().equals(result.getCode());
    }
    
    @Override
    public boolean deleteMcpServer(String namespaceId, String mcpName, String mcpId, String version)
        throws NacosException {
        namespaceId = resolveMcpNamespace(namespaceId);
        Map<String, String> params = new HashMap<>(4);
        params.put("mcpName", mcpName);
        params.put("mcpId", mcpId);
        params.put("version", version);
        params.put("namespaceId", namespaceId);
        HttpRequest httpRequest =
            buildHttpRequestBuilder(buildRequestResource(namespaceId, mcpName))
                .setHttpMethod(HttpMethod.DELETE).setPath(Constants.AdminApiPath.AI_MCP_ADMIN_PATH)
                .setParamValue(params).build();
        HttpRestResult<String> restResult = executeSyncHttpRequest(httpRequest);
        Result<String> result =
            JacksonUtils.toObj(restResult.getData(), new TypeReference<Result<String>>() {
            });
        return ErrorCode.SUCCESS.getCode().equals(result.getCode());
    }
    
    private Page<McpServerBasicInfo> queryServerPage(String namespaceId, String mcpName, int pageNo,
        int pageSize,
        String search) throws NacosException {
        namespaceId = resolveMcpNamespace(namespaceId);
        Map<String, String> params = new HashMap<>(8);
        params.put("pageNo", String.valueOf(pageNo));
        params.put("pageSize", String.valueOf(pageSize));
        params.put("search", search);
        params.put("mcpName", mcpName);
        params.put("namespaceId", namespaceId);
        HttpRequest httpRequest =
            buildHttpRequestBuilder(buildRequestResource(namespaceId, mcpName))
                .setHttpMethod(HttpMethod.GET)
                .setPath(Constants.AdminApiPath.AI_MCP_ADMIN_PATH + "/list")
                .setParamValue(params).build();
        HttpRestResult<String> restResult = executeSyncHttpRequest(httpRequest);
        Result<Page<McpServerBasicInfo>> result = JacksonUtils.toObj(restResult.getData(),
            new TypeReference<Result<Page<McpServerBasicInfo>>>() {
            });
        return result.getData();
    }
    
    private Map<String, String> buildFullParameters(McpServerBasicInfo serverSpec,
        McpToolSpecification toolSpec,
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
    
    private String resolveMcpNamespace(String namespaceId) {
        if (com.alibaba.nacos.common.utils.StringUtils.isBlank(namespaceId)) {
            return AiConstants.Mcp.MCP_DEFAULT_NAMESPACE;
        }
        return namespaceId;
    }
}
