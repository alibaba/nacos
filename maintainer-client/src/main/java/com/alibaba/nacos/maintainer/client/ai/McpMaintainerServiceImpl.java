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
import com.alibaba.nacos.api.ai.model.mcp.McpLifecycleDraftRequest;
import com.alibaba.nacos.api.ai.model.mcp.McpLifecycleLabelsUpdateRequest;
import com.alibaba.nacos.api.ai.model.mcp.McpLifecycleVersionCommand;
import com.alibaba.nacos.api.ai.model.mcp.McpLifecycleVersionDetail;
import com.alibaba.nacos.api.ai.model.mcp.McpLifecycleVersionSummary;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpToolSpecification;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.api.utils.json.JsonUtils;
import com.alibaba.nacos.api.utils.json.NacosTypeReference;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.utils.HttpMethod;
import com.alibaba.nacos.maintainer.client.constants.Constants;
import com.alibaba.nacos.maintainer.client.model.HttpRequest;

import java.util.HashMap;
import java.util.Map;

final class McpMaintainerServiceImpl extends AbstractAiDelegateMaintainerService
    implements McpMaintainerService {
    
    private static final String SEARCH_BLUR = "blur";
    
    private static final String SEARCH_ACCURATE = "accurate";
    
    private static final String ROOT_PATH = Constants.AdminApiPath.AI_MCP_ADMIN_PATH;
    
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
        Result<McpServerDetailInfo> result = JsonUtils.toObj(restResult.getData(),
            new NacosTypeReference<Result<McpServerDetailInfo>>() {
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
            JsonUtils.toObj(restResult.getData(), new NacosTypeReference<Result<String>>() {
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
            JsonUtils.toObj(restResult.getData(), new NacosTypeReference<Result<String>>() {
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
            JsonUtils.toObj(restResult.getData(), new NacosTypeReference<Result<String>>() {
            });
        return ErrorCode.SUCCESS.getCode().equals(result.getCode());
    }
    
    @Override
    public Page<McpLifecycleVersionSummary> listLifecycleVersions(String namespaceId,
        String mcpName, String status, int pageNo, int pageSize) throws NacosException {
        namespaceId = resolveMcpNamespace(namespaceId);
        Map<String, String> params = lifecycleIdentityParams(namespaceId, mcpName);
        putIfNotBlank(params, "status", status);
        params.put("pageNo", String.valueOf(pageNo));
        params.put("pageSize", String.valueOf(pageSize));
        HttpRestResult<String> restResult = executeLifecycleRequest(HttpMethod.GET,
            ROOT_PATH + "/versions", namespaceId, mcpName, params);
        Result<Page<McpLifecycleVersionSummary>> result = JsonUtils.toObj(restResult.getData(),
            new NacosTypeReference<Result<Page<McpLifecycleVersionSummary>>>() {
            });
        return result.getData();
    }
    
    @Override
    public McpLifecycleVersionDetail getLifecycleVersion(String namespaceId, String mcpName,
        String version) throws NacosException {
        namespaceId = resolveMcpNamespace(namespaceId);
        Map<String, String> params = lifecycleVersionParams(namespaceId, mcpName, version);
        HttpRestResult<String> restResult = executeLifecycleRequest(HttpMethod.GET,
            ROOT_PATH + "/version", namespaceId, mcpName, params);
        Result<McpLifecycleVersionDetail> result = JsonUtils.toObj(restResult.getData(),
            new NacosTypeReference<Result<McpLifecycleVersionDetail>>() {
            });
        return result.getData();
    }
    
    @Override
    public McpLifecycleVersionDetail createLifecycleDraft(String namespaceId,
        McpLifecycleDraftRequest request) throws NacosException {
        return executeDraftRequest(HttpMethod.POST, namespaceId, request);
    }
    
    @Override
    public McpLifecycleVersionDetail updateLifecycleDraft(String namespaceId,
        McpLifecycleDraftRequest request) throws NacosException {
        return executeDraftRequest(HttpMethod.PUT, namespaceId, request);
    }
    
    @Override
    public void deleteLifecycleDraft(String namespaceId, McpLifecycleVersionCommand command)
        throws NacosException {
        command = requireRequest(command);
        namespaceId = resolveMcpNamespace(namespaceId);
        executeLifecycleRequest(HttpMethod.DELETE, ROOT_PATH + "/draft", namespaceId,
            command.getMcpName(), lifecycleVersionParams(namespaceId, command.getMcpName(),
                command.getVersion()));
    }
    
    @Override
    public McpLifecycleVersionSummary submitLifecycleVersion(String namespaceId,
        McpLifecycleVersionCommand command) throws NacosException {
        return executeVersionCommand(namespaceId, command, "/submit");
    }
    
    @Override
    public McpLifecycleVersionSummary publishLifecycleVersion(String namespaceId,
        McpLifecycleVersionCommand command) throws NacosException {
        return executeVersionCommand(namespaceId, command, "/publish");
    }
    
    @Override
    public McpLifecycleVersionSummary forcePublishLifecycleVersion(String namespaceId,
        McpLifecycleVersionCommand command) throws NacosException {
        return executeVersionCommand(namespaceId, command, "/force-publish");
    }
    
    @Override
    public McpLifecycleVersionSummary redraftLifecycleVersion(String namespaceId,
        McpLifecycleVersionCommand command) throws NacosException {
        return executeVersionCommand(namespaceId, command, "/redraft");
    }
    
    @Override
    public McpLifecycleVersionSummary onlineLifecycleVersion(String namespaceId,
        McpLifecycleVersionCommand command) throws NacosException {
        return executeVersionCommand(namespaceId, command, "/online");
    }
    
    @Override
    public McpLifecycleVersionSummary offlineLifecycleVersion(String namespaceId,
        McpLifecycleVersionCommand command) throws NacosException {
        return executeVersionCommand(namespaceId, command, "/offline");
    }
    
    @Override
    public Map<String, String> updateLifecycleLabels(String namespaceId,
        McpLifecycleLabelsUpdateRequest request) throws NacosException {
        request = requireRequest(request);
        namespaceId = resolveMcpNamespace(namespaceId);
        Map<String, String> params = lifecycleIdentityParams(namespaceId, request.getMcpName());
        if (request.getLabels() != null) {
            params.put("labels", JsonUtils.toJson(request.getLabels()));
        }
        HttpRestResult<String> restResult = executeLifecycleRequest(HttpMethod.PUT,
            ROOT_PATH + "/labels", namespaceId, request.getMcpName(), params);
        Result<Map<String, String>> result = JsonUtils.toObj(restResult.getData(),
            new NacosTypeReference<Result<Map<String, String>>>() {
            });
        return result.getData();
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
        Result<Page<McpServerBasicInfo>> result = JsonUtils.toObj(restResult.getData(),
            new NacosTypeReference<Result<Page<McpServerBasicInfo>>>() {
            });
        return result.getData();
    }
    
    private McpLifecycleVersionDetail executeDraftRequest(String method, String namespaceId,
        McpLifecycleDraftRequest request) throws NacosException {
        request = requireRequest(request);
        McpServerBasicInfo serverSpecification = request.getServerSpecification();
        if (serverSpecification == null) {
            throw new IllegalArgumentException("MCP server specification must not be null");
        }
        namespaceId = resolveMcpNamespace(namespaceId);
        String mcpName = serverSpecification.getName();
        Map<String, String> params = lifecycleIdentityParams(namespaceId, mcpName);
        params.put("serverSpecification", JsonUtils.toJson(serverSpecification));
        putJsonIfNotNull(params, "toolSpecification", request.getToolSpecification());
        putJsonIfNotNull(params, "resourceSpecification", request.getResourceSpecification());
        putJsonIfNotNull(params, "endpointSpecification", request.getEndpointSpecification());
        HttpRestResult<String> restResult = executeLifecycleRequest(method, ROOT_PATH + "/draft",
            namespaceId, mcpName, params);
        Result<McpLifecycleVersionDetail> result = JsonUtils.toObj(restResult.getData(),
            new NacosTypeReference<Result<McpLifecycleVersionDetail>>() {
            });
        return result.getData();
    }
    
    private McpLifecycleVersionSummary executeVersionCommand(String namespaceId,
        McpLifecycleVersionCommand command, String path) throws NacosException {
        command = requireRequest(command);
        namespaceId = resolveMcpNamespace(namespaceId);
        Map<String, String> params = lifecycleVersionParams(namespaceId, command.getMcpName(),
            command.getVersion());
        HttpRestResult<String> restResult = executeLifecycleRequest(HttpMethod.POST,
            ROOT_PATH + path, namespaceId, command.getMcpName(), params);
        Result<McpLifecycleVersionSummary> result = JsonUtils.toObj(restResult.getData(),
            new NacosTypeReference<Result<McpLifecycleVersionSummary>>() {
            });
        return result.getData();
    }
    
    private HttpRestResult<String> executeLifecycleRequest(String method, String path,
        String namespaceId, String mcpName, Map<String, String> params) throws NacosException {
        HttpRequest request = buildHttpRequestBuilder(buildRequestResource(namespaceId, mcpName))
            .setHttpMethod(method).setPath(path).setParamValue(params).build();
        return executeSyncHttpRequest(request);
    }
    
    private Map<String, String> lifecycleIdentityParams(String namespaceId, String mcpName) {
        Map<String, String> result = new HashMap<>(6);
        result.put("namespaceId", namespaceId);
        result.put("mcpName", mcpName);
        return result;
    }
    
    private Map<String, String> lifecycleVersionParams(String namespaceId, String mcpName,
        String version) {
        Map<String, String> result = lifecycleIdentityParams(namespaceId, mcpName);
        result.put("version", version);
        return result;
    }
    
    private void putJsonIfNotNull(Map<String, String> params, String key, Object value) {
        if (value != null) {
            params.put(key, JsonUtils.toJson(value));
        }
    }
    
    private <T> T requireRequest(T request) {
        if (request == null) {
            throw new IllegalArgumentException("MCP lifecycle request must not be null");
        }
        return request;
    }
    
    private Map<String, String> buildFullParameters(McpServerBasicInfo serverSpec,
        McpToolSpecification toolSpec,
        McpEndpointSpec endpointSpec) {
        Map<String, String> params = new HashMap<>(4);
        params.put("mcpName", serverSpec.getName());
        params.put("serverSpecification", JsonUtils.toJson(serverSpec));
        if (null != toolSpec) {
            params.put("toolSpecification", JsonUtils.toJson(toolSpec));
        }
        if (null != endpointSpec) {
            params.put("endpointSpecification", JsonUtils.toJson(endpointSpec));
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
