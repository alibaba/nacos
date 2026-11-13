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

import com.alibaba.nacos.api.ai.model.hub.HubResourceTransferRequest;
import com.alibaba.nacos.api.ai.model.hub.HubResourceTransferResult;
import com.alibaba.nacos.api.ai.model.hub.HubAdjustShareRequest;
import com.alibaba.nacos.api.ai.model.hub.HubForceUnshareRequest;
import com.alibaba.nacos.api.ai.model.hub.HubResourceShareState;
import com.alibaba.nacos.api.ai.model.hub.HubShareResourceRequest;
import com.alibaba.nacos.api.ai.model.hub.HubShareResult;
import com.alibaba.nacos.api.ai.model.hub.HubUnshareRequest;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.api.utils.json.JsonUtils;
import com.alibaba.nacos.api.utils.json.NacosTypeReference;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.utils.HttpMethod;
import com.alibaba.nacos.maintainer.client.model.HttpRequest;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/** Management transport sharing the existing Maintainer connection and caller credentials. */
class HubResourceMaintainerServiceImpl extends AbstractAiDelegateMaintainerService
    implements HubResourceMaintainerService {
    
    public HubResourceMaintainerServiceImpl(Properties properties) throws NacosException {
        this(new AiMaintainerHttpContext(properties));
    }
    
    HubResourceMaintainerServiceImpl(AiMaintainerHttpContext context) {
        super(context);
    }
    
    @Override
    public HubShareResult share(String resourceType, HubShareResourceRequest request)
        throws NacosException {
        requireType(resourceType);
        if (request == null) {
            throw invalid();
        }
        requireSource(request.getSourceNamespaceId(), request.getSourceName(),
            request.getSourceVersion());
        if (request.getExpectedSourceResourceId() != null
            && request.getExpectedSourceResourceId() <= 0) {
            throw invalid();
        }
        Map<String, String> params = new HashMap<>(4);
        params.put("sourceNamespaceId", request.getSourceNamespaceId());
        params.put("sourceName", request.getSourceName());
        params.put("sourceVersion", request.getSourceVersion());
        if (request.getExpectedSourceResourceId() != null) {
            params.put("expectedSourceResourceId",
                request.getExpectedSourceResourceId().toString());
        }
        return mutation(resourceType, "/share", params);
    }
    
    @Override
    public HubShareResult adjust(String resourceType, HubAdjustShareRequest request)
        throws NacosException {
        requireType(resourceType);
        if (request == null) {
            throw invalid();
        }
        Map<String, String> params =
            entryParams(request.getHubEntryId(), request.getExpectedMetaVersion());
        requireVersion(request.getTargetVersion());
        params.put("targetVersion", request.getTargetVersion());
        return mutation(resourceType, "/adjust", params);
    }
    
    @Override
    public HubShareResult unshare(String resourceType, HubUnshareRequest request)
        throws NacosException {
        requireType(resourceType);
        if (request == null) {
            throw invalid();
        }
        return mutation(resourceType, "/unshare",
            entryParams(request.getHubEntryId(), request.getExpectedMetaVersion()));
    }
    
    @Override
    public HubShareResult forceUnshare(String resourceType, HubForceUnshareRequest request)
        throws NacosException {
        requireType(resourceType);
        if (request == null) {
            throw invalid();
        }
        Map<String, String> params =
            entryParams(request.getHubEntryId(), request.getExpectedMetaVersion());
        requireText(request.getReason(), 512);
        params.put("reason", request.getReason());
        return mutation(resourceType, "/force-unshare", params);
    }
    
    @Override
    public HubResourceShareState sourceState(String resourceType, String namespaceId, String name)
        throws NacosException {
        requireType(resourceType);
        requireText(namespaceId, 128);
        requireName(name);
        rejectHub(namespaceId);
        Map<String, String> params = new HashMap<>(2);
        params.put("sourceNamespaceId", namespaceId);
        params.put("sourceName", name);
        return state(resourceType, "/source-share", params, null);
    }
    
    @Override
    public HubResourceShareState governanceState(String resourceType, Long entryId)
        throws NacosException {
        requireType(resourceType);
        requireId(entryId);
        Map<String, String> params = new HashMap<>(1);
        params.put("hubEntryId", entryId.toString());
        return state(resourceType, "/governance-state", params, entryId);
    }
    
    private HubShareResult mutation(String resourceType, String operation,
        Map<String, String> params)
        throws NacosException {
        HttpRestResult<String> response = send(resourceType, operation, params, HttpMethod.POST);
        try {
            Result<HubShareResult> result =
                response == null ? null : JsonUtils.toObj(response.getData(),
                    new NacosTypeReference<Result<HubShareResult>>() {
                    });
            if (result == null || !ErrorCode.SUCCESS.getCode().equals(result.getCode())
                || result.getData() == null || result.getData().getHubEntryId() == null
                || result.getData().getHubEntryId() <= 0
                || result.getData().getMetaVersion() == null
                || result.getData().getMetaVersion() <= 0
                || (params.containsKey("hubEntryId")
                    && !params.get("hubEntryId")
                        .equals(result.getData().getHubEntryId().toString()))) {
                throw invalidResponse();
            }
            return result.getData();
        } catch (RuntimeException invalid) {
            throw invalidResponse();
        }
    }
    
    private HubResourceShareState state(String resourceType, String operation,
        Map<String, String> params,
        Long expectedId) throws NacosException {
        HttpRestResult<String> response = send(resourceType, operation, params, HttpMethod.GET);
        try {
            Result<HubResourceShareState> result =
                response == null ? null : JsonUtils.toObj(response.getData(),
                    new NacosTypeReference<Result<HubResourceShareState>>() {
                    });
            if (result == null || !ErrorCode.SUCCESS.getCode().equals(result.getCode())) {
                throw invalidResponse();
            }
            HubResourceShareState state = result.getData();
            if (state == null && expectedId == null) {
                return null;
            }
            if (state == null || state.getHubEntryId() == null || state.getHubEntryId() <= 0
                || state.getMetaVersion() == null || state.getMetaVersion() <= 0
                || state.getSharedVersion() == null || state.getSharedVersion().trim().isEmpty()
                || (expectedId != null && !expectedId.equals(state.getHubEntryId()))) {
                throw invalidResponse();
            }
            return state;
        } catch (RuntimeException invalid) {
            throw invalidResponse();
        }
    }
    
    private HttpRestResult<String> send(String resourceType, String operation,
        Map<String, String> params,
        String method) throws NacosException {
        String resource =
            operation.equals("/force-unshare") || operation.equals("/governance-state")
                ? AiConstants.Hub.ADMIN_RESOURCE : AiConstants.Hub.ENTRY_RESOURCE;
        HttpRequest request = buildHttpRequestBuilder(buildRequestResource("", resource))
            .setHttpMethod(method).setPath("/v3/admin/ai/hub/" + resourceType + operation)
            .setParamValue(params).build();
        return executeSyncHttpRequest(request);
    }
    
    private Map<String, String> entryParams(Long entryId, Long revision) throws NacosException {
        requireId(entryId);
        requireId(revision);
        Map<String, String> params = new HashMap<>(3);
        params.put("hubEntryId", entryId.toString());
        params.put("expectedMetaVersion", revision.toString());
        return params;
    }
    
    private void requireSource(String namespaceId, String name, String version)
        throws NacosException {
        requireText(namespaceId, 128);
        rejectHub(namespaceId);
        requireName(name);
        requireVersion(version);
    }
    
    private void rejectHub(String namespaceId) throws NacosException {
        if (AiConstants.Hub.NAMESPACE_ID.equals(namespaceId)) {
            throw invalid();
        }
    }
    
    private void requireVersion(String version) throws NacosException {
        requireText(version, 64);
        if ("latest".equalsIgnoreCase(version)) {
            throw invalid();
        }
    }
    
    private void requireType(String type) throws NacosException {
        if (!"mcp".equals(type) && !"agent".equals(type) && !"prompt".equals(type)
            && !"agentspec".equals(type)) {
            throw invalid();
        }
    }
    
    private void requireName(String name) throws NacosException {
        if (name == null || name.trim().isEmpty() || name.length() > 256) {
            throw invalid();
        }
    }
    
    private void requireText(String value, int maximum) throws NacosException {
        if (value == null || value.trim().isEmpty() || value.length() > maximum
            || !value.equals(value.trim())) {
            throw invalid();
        }
    }
    
    private void requireId(Long value) throws NacosException {
        if (value == null || value <= 0) {
            throw invalid();
        }
    }
    
    private NacosException invalid() {
        return new NacosException(NacosException.INVALID_PARAM,
            "Invalid typed Hub management request");
    }
    
    private NacosException invalidResponse() {
        return new NacosException(NacosException.SERVER_ERROR,
            "Invalid typed Hub management response");
    }
    
    /** Import one pinned Hub entry into an independent namespace draft. */
    public HubResourceTransferResult importFromHub(String resourceType,
        HubResourceTransferRequest request)
        throws NacosException {
        return transfer(resourceType, request);
    }
    
    private HubResourceTransferResult transfer(String resourceType,
        HubResourceTransferRequest request) throws NacosException {
        requireType(resourceType);
        if (request == null) {
            throw invalid();
        }
        String targetNamespace = request.getTargetNamespaceId();
        String targetName = request.getTargetName();
        String version =
            request.getTargetVersion() == null || request.getTargetVersion().trim().isEmpty()
                ? "0.0.1" : request.getTargetVersion();
        requireText(targetNamespace, 128);
        rejectHub(targetNamespace);
        requireName(targetName);
        requireVersion(version);
        try {
            if ("agent".equals(resourceType)) {
                com.alibaba.nacos.api.ai.utils.AgentValidationUtils
                    .validateNamespaceId(targetNamespace);
                com.alibaba.nacos.api.ai.utils.AgentValidationUtils.validateAgentName(targetName);
                com.alibaba.nacos.api.ai.utils.AgentValidationUtils.validateVersion(version);
            } else if (!"mcp".equals(resourceType)
                && !com.alibaba.nacos.common.utils.VersionUtils.isSupportedVersionFormat(version)) {
                throw invalid();
            }
        } catch (IllegalArgumentException invalid) {
            throw invalid();
        }
        Map<String, String> params = new HashMap<>();
        params.put("targetNamespaceId", targetNamespace);
        params.put("targetName", targetName);
        params.put("targetVersion", version);
        Long entryId = request.getHubEntryId();
        
        requireId(entryId);
        if (request.getSourceNamespaceId() != null || request.getSourceName() != null
            || request.getSourceVersion() != null) {
            throw invalid();
        }
        params.put("hubEntryId", entryId.toString());
        
        if (request.getMcpRemoteServerConfig() != null) {
            if (!"mcp".equals(resourceType)) {
                throw invalid();
            }
            String dependencies = JsonUtils.toJson(request.getMcpRemoteServerConfig());
            if (dependencies.length() > 65536) {
                throw invalid();
            }
            params.put("mcpRemoteServerConfig", dependencies);
        }
        HttpRestResult<String> response =
            send(resourceType, "/import", params, HttpMethod.POST);
        try {
            Result<HubResourceTransferResult> result =
                response == null ? null : JsonUtils.toObj(response.getData(),
                    new NacosTypeReference<Result<HubResourceTransferResult>>() {
                    });
            HubResourceTransferResult created = result == null ? null : result.getData();
            if (result == null || !ErrorCode.SUCCESS.getCode().equals(result.getCode())
                || created == null
                || !resourceType.equals(created.getResourceType())
                || !targetNamespace.equals(created.getTargetNamespaceId())
                || !targetName.equals(created.getTargetName())
                || !version.equals(created.getDraftVersion())
                || !"draft".equals(created.getStatus()) || created.getTargetResourceId() == null
                || created.getTargetResourceId() <= 0
                || !java.util.Objects.equals(entryId, created.getHubEntryId())
                || !"HUB".equals(created.getOrigin())) {
                throw invalidResponse();
            }
            return created;
        } catch (RuntimeException invalid) {
            throw invalidResponse();
        }
    }
    
}
