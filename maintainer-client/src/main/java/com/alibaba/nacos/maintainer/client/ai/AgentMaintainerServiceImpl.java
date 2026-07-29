/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

import com.alibaba.nacos.api.ai.model.agent.Agent;
import com.alibaba.nacos.api.ai.model.agent.AgentDraftCreateRequest;
import com.alibaba.nacos.api.ai.model.agent.AgentDraftUpdateRequest;
import com.alibaba.nacos.api.ai.model.agent.AgentLabelsUpdateRequest;
import com.alibaba.nacos.api.ai.model.agent.AgentOverview;
import com.alibaba.nacos.api.ai.model.agent.AgentSummary;
import com.alibaba.nacos.api.ai.model.agent.AgentUpdateRequest;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionCommand;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionDetail;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionSummary;
import com.alibaba.nacos.api.ai.model.agent.RuntimeEndpointSnapshot;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.api.utils.json.JsonUtils;
import com.alibaba.nacos.api.utils.json.NacosTypeReference;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.utils.HttpMethod;
import com.alibaba.nacos.maintainer.client.constants.Constants;
import com.alibaba.nacos.maintainer.client.model.HttpRequest;

import java.util.HashMap;
import java.util.Map;

final class AgentMaintainerServiceImpl extends AbstractAiDelegateMaintainerService
    implements AgentMaintainerService {
    
    private static final String ROOT_PATH = Constants.AdminApiPath.AI_AGENTS_ADMIN_PATH;
    
    AgentMaintainerServiceImpl(AiMaintainerHttpContext context) {
        super(context);
    }
    
    @Override
    public AgentOverview getAgent(String namespaceId, String agentName) throws NacosException {
        namespaceId = resolveNamespace(namespaceId);
        Map<String, String> params = identityParams(namespaceId, agentName);
        HttpRestResult<String> restResult =
            executeQuery(HttpMethod.GET, ROOT_PATH, namespaceId, agentName, params);
        Result<AgentOverview> result = JsonUtils.toObj(restResult.getData(),
            new NacosTypeReference<Result<AgentOverview>>() {
            });
        return result.getData();
    }
    
    @Override
    public Agent updateAgent(String namespaceId, AgentUpdateRequest request)
        throws NacosException {
        request = requireRequest(request);
        namespaceId = resolveNamespace(namespaceId);
        Map<String, String> params = agentParams(namespaceId, request.getAgentName(),
            request.getDisplayName(), request.getDescription(), request.getIconUrl(),
            request.getProvider(), request.getTags(), request.getExtensions(), request.getStatus());
        HttpRestResult<String> restResult = executeFormRequest(HttpMethod.PUT, ROOT_PATH,
            namespaceId, request.getAgentName(), params);
        Result<Agent> result =
            JsonUtils.toObj(restResult.getData(), new NacosTypeReference<Result<Agent>>() {
            });
        return result.getData();
    }
    
    @Override
    public void deleteAgent(String namespaceId, String agentName) throws NacosException {
        namespaceId = resolveNamespace(namespaceId);
        executeQuery(HttpMethod.DELETE, ROOT_PATH, namespaceId, agentName,
            identityParams(namespaceId, agentName));
    }
    
    @Override
    public Page<AgentSummary> listAgents(String namespaceId, String agentName, String bizTag,
        String scope, String owner, String orderBy, int pageNo, int pageSize)
        throws NacosException {
        namespaceId = resolveNamespace(namespaceId);
        Map<String, String> params = new HashMap<>(11);
        params.put("namespaceId", namespaceId);
        putIfNotBlank(params, "agentName", agentName);
        putIfNotBlank(params, "bizTag", bizTag);
        putIfNotBlank(params, "scope", scope);
        putIfNotBlank(params, "owner", owner);
        putIfNotBlank(params, "orderBy", orderBy);
        params.put("pageNo", String.valueOf(pageNo));
        params.put("pageSize", String.valueOf(pageSize));
        HttpRequest request =
            buildHttpRequestBuilder(buildRequestResource(namespaceId, agentName))
                .setHttpMethod(HttpMethod.GET).setPath(ROOT_PATH + "/list").setParamValue(params)
                .build();
        HttpRestResult<String> restResult = executeSyncHttpRequest(request);
        Result<Page<AgentSummary>> result = JsonUtils.toObj(restResult.getData(),
            new NacosTypeReference<Result<Page<AgentSummary>>>() {
            });
        return result.getData();
    }
    
    @Override
    public Page<AgentVersionSummary> listAgentVersions(String namespaceId, String agentName,
        String status, int pageNo, int pageSize) throws NacosException {
        namespaceId = resolveNamespace(namespaceId);
        Map<String, String> params = identityParams(namespaceId, agentName);
        putIfNotBlank(params, "status", status);
        params.put("pageNo", String.valueOf(pageNo));
        params.put("pageSize", String.valueOf(pageSize));
        HttpRestResult<String> restResult =
            executeQuery(HttpMethod.GET, ROOT_PATH + "/versions", namespaceId, agentName, params);
        Result<Page<AgentVersionSummary>> result = JsonUtils.toObj(restResult.getData(),
            new NacosTypeReference<Result<Page<AgentVersionSummary>>>() {
            });
        return result.getData();
    }
    
    @Override
    public AgentVersionDetail getAgentVersion(String namespaceId, String agentName, String version)
        throws NacosException {
        namespaceId = resolveNamespace(namespaceId);
        Map<String, String> params = versionParams(namespaceId, agentName, version);
        HttpRestResult<String> restResult =
            executeQuery(HttpMethod.GET, ROOT_PATH + "/version", namespaceId, agentName, params);
        Result<AgentVersionDetail> result = JsonUtils.toObj(restResult.getData(),
            new NacosTypeReference<Result<AgentVersionDetail>>() {
            });
        return result.getData();
    }
    
    @Override
    public RuntimeEndpointSnapshot getRuntimeEndpoints(String namespaceId, String agentName,
        String protocol, String version) throws NacosException {
        namespaceId = resolveNamespace(namespaceId);
        Map<String, String> params = identityParams(namespaceId, agentName);
        params.put("protocol", protocol);
        putIfNotBlank(params, "version", version);
        HttpRestResult<String> restResult =
            executeQuery(HttpMethod.GET, ROOT_PATH + "/runtime-endpoints", namespaceId, agentName,
                params);
        Result<RuntimeEndpointSnapshot> result = JsonUtils.toObj(restResult.getData(),
            new NacosTypeReference<Result<RuntimeEndpointSnapshot>>() {
            });
        return result.getData();
    }
    
    @Override
    public AgentVersionDetail createDraft(String namespaceId, AgentDraftCreateRequest request)
        throws NacosException {
        request = requireRequest(request);
        namespaceId = resolveNamespace(namespaceId);
        Map<String, String> params = versionParams(namespaceId, request.getAgentName(),
            request.getVersion());
        putIfNotNull(params, "displayName", request.getDisplayName());
        putIfNotNull(params, "description", request.getDescription());
        putIfNotNull(params, "iconUrl", request.getIconUrl());
        putJsonIfNotNull(params, "provider", request.getProvider());
        putJsonIfNotNull(params, "tags", request.getTags());
        putJsonIfNotNull(params, "extensions", request.getExtensions());
        putJsonIfNotNull(params, "callInterfaces", request.getCallInterfaces());
        putIfNotNull(params, "author", request.getAuthor());
        putIfNotNull(params, "changeDescription", request.getChangeDescription());
        putIfNotNull(params, "basedOnVersion", request.getBasedOnVersion());
        HttpRestResult<String> restResult = executeFormRequest(HttpMethod.POST,
            ROOT_PATH + "/draft", namespaceId, request.getAgentName(), params);
        Result<AgentVersionDetail> result = JsonUtils.toObj(restResult.getData(),
            new NacosTypeReference<Result<AgentVersionDetail>>() {
            });
        return result.getData();
    }
    
    @Override
    public AgentVersionDetail updateDraft(String namespaceId, AgentDraftUpdateRequest request)
        throws NacosException {
        request = requireRequest(request);
        namespaceId = resolveNamespace(namespaceId);
        Map<String, String> params = versionParams(namespaceId, request.getAgentName(),
            request.getVersion());
        putJsonIfNotNull(params, "callInterfaces", request.getCallInterfaces());
        putIfNotNull(params, "changeDescription", request.getChangeDescription());
        HttpRestResult<String> restResult = executeFormRequest(HttpMethod.PUT,
            ROOT_PATH + "/draft", namespaceId, request.getAgentName(), params);
        Result<AgentVersionDetail> result = JsonUtils.toObj(restResult.getData(),
            new NacosTypeReference<Result<AgentVersionDetail>>() {
            });
        return result.getData();
    }
    
    @Override
    public void deleteDraft(String namespaceId, String agentName, String version)
        throws NacosException {
        namespaceId = resolveNamespace(namespaceId);
        executeQuery(HttpMethod.DELETE, ROOT_PATH + "/draft", namespaceId, agentName,
            versionParams(namespaceId, agentName, version));
    }
    
    @Override
    public AgentVersionSummary submit(String namespaceId, AgentVersionCommand command)
        throws NacosException {
        return executeVersionCommand(namespaceId, command, "/submit");
    }
    
    @Override
    public AgentVersionSummary publish(String namespaceId, AgentVersionCommand command)
        throws NacosException {
        return executeVersionCommand(namespaceId, command, "/publish");
    }
    
    @Override
    public AgentVersionSummary forcePublish(String namespaceId, AgentVersionCommand command)
        throws NacosException {
        return executeVersionCommand(namespaceId, command, "/force-publish");
    }
    
    @Override
    public AgentVersionSummary redraft(String namespaceId, AgentVersionCommand command)
        throws NacosException {
        return executeVersionCommand(namespaceId, command, "/redraft");
    }
    
    @Override
    public AgentVersionSummary online(String namespaceId, AgentVersionCommand command)
        throws NacosException {
        return executeVersionCommand(namespaceId, command, "/online");
    }
    
    @Override
    public AgentVersionSummary offline(String namespaceId, AgentVersionCommand command)
        throws NacosException {
        return executeVersionCommand(namespaceId, command, "/offline");
    }
    
    @Override
    public Agent updateLabels(String namespaceId, AgentLabelsUpdateRequest request)
        throws NacosException {
        request = requireRequest(request);
        namespaceId = resolveNamespace(namespaceId);
        Map<String, String> params = identityParams(namespaceId, request.getAgentName());
        putJsonIfNotNull(params, "labels", request.getLabels());
        HttpRestResult<String> restResult = executeFormRequest(HttpMethod.PUT,
            ROOT_PATH + "/labels", namespaceId, request.getAgentName(), params);
        Result<Agent> result =
            JsonUtils.toObj(restResult.getData(), new NacosTypeReference<Result<Agent>>() {
            });
        return result.getData();
    }
    
    private AgentVersionSummary executeVersionCommand(String namespaceId,
        AgentVersionCommand command, String path) throws NacosException {
        command = requireRequest(command);
        namespaceId = resolveNamespace(namespaceId);
        Map<String, String> params =
            versionParams(namespaceId, command.getAgentName(), command.getVersion());
        HttpRestResult<String> restResult = executeFormRequest(HttpMethod.POST, ROOT_PATH + path,
            namespaceId, command.getAgentName(), params);
        Result<AgentVersionSummary> result = JsonUtils.toObj(restResult.getData(),
            new NacosTypeReference<Result<AgentVersionSummary>>() {
            });
        return result.getData();
    }
    
    private HttpRestResult<String> executeFormRequest(String method, String path,
        String namespaceId, String agentName, Map<String, String> params) throws NacosException {
        HttpRequest httpRequest =
            buildHttpRequestBuilder(buildRequestResource(namespaceId, agentName))
                .setHttpMethod(method).setPath(path).setParamValue(params).build();
        return executeSyncHttpRequest(httpRequest);
    }
    
    private HttpRestResult<String> executeQuery(String method, String path, String namespaceId,
        String agentName, Map<String, String> params) throws NacosException {
        HttpRequest httpRequest =
            buildHttpRequestBuilder(buildRequestResource(namespaceId, agentName))
                .setHttpMethod(method).setPath(path).setParamValue(params).build();
        return executeSyncHttpRequest(httpRequest);
    }
    
    private Map<String, String> identityParams(String namespaceId, String agentName) {
        Map<String, String> result = new HashMap<>(4);
        result.put("namespaceId", namespaceId);
        result.put("agentName", agentName);
        return result;
    }
    
    private Map<String, String> versionParams(String namespaceId, String agentName,
        String version) {
        Map<String, String> result = identityParams(namespaceId, agentName);
        result.put("version", version);
        return result;
    }
    
    private Map<String, String> agentParams(String namespaceId, String agentName,
        String displayName, String description, String iconUrl, Object provider, Object tags,
        Object extensions, String status) {
        Map<String, String> result = identityParams(namespaceId, agentName);
        putIfNotNull(result, "displayName", displayName);
        putIfNotNull(result, "description", description);
        putIfNotNull(result, "iconUrl", iconUrl);
        putJsonIfNotNull(result, "provider", provider);
        putJsonIfNotNull(result, "tags", tags);
        putJsonIfNotNull(result, "extensions", extensions);
        putIfNotNull(result, "status", status);
        return result;
    }
    
    private void putJsonIfNotNull(Map<String, String> params, String key, Object value) {
        if (value != null) {
            params.put(key, JsonUtils.toJson(value));
        }
    }
    
    private void putIfNotNull(Map<String, String> params, String key, String value) {
        if (value != null) {
            params.put(key, value);
        }
    }
    
    private <T> T requireRequest(T request) {
        if (request == null) {
            throw new IllegalArgumentException("Agent request must not be null");
        }
        return request;
    }
}
