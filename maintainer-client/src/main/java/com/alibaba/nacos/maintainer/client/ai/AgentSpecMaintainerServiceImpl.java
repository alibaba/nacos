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

import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpec;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpecBasicInfo;
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
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * AgentSpec maintainer service implementation via HTTP.
 *
 * <p>Mirrors the Skill implementation pattern in {@link NacosAiMaintainerServiceImpl},
 * calling {@code AgentSpecAdminController} endpoints through {@link ClientHttpProxy}.
 *
 * @author nacos
 */
public class AgentSpecMaintainerServiceImpl extends AbstractAiDelegateMaintainerService implements AgentSpecMaintainerService {
    
    public AgentSpecMaintainerServiceImpl(Properties properties) throws NacosException {
        this(new AiMaintainerHttpContext(properties));
    }

    AgentSpecMaintainerServiceImpl(AiMaintainerHttpContext context) {
        super(context);
    }
    
    @Override
    public AgentSpec getAgentSpecDetail(String namespaceId, String agentSpecName) throws NacosException {
        namespaceId = resolveNamespace(namespaceId);
        Map<String, String> params = new HashMap<>(4);
        params.put("namespaceId", namespaceId);
        params.put("agentSpecName", agentSpecName);
        HttpRequest httpRequest = buildHttpRequestBuilder(buildRequestResource(namespaceId, agentSpecName)).setHttpMethod(HttpMethod.GET)
                .setPath(Constants.AdminApiPath.AI_AGENTSPEC_ADMIN_PATH).setParamValue(params).build();
        HttpRestResult<String> restResult = executeSyncHttpRequest(httpRequest);
        Result<JsonNode> result = JacksonUtils.toObj(restResult.getData(),
            new TypeReference<Result<JsonNode>>() {
                });
        return extractAgentSpec(namespaceId, agentSpecName, result.getData());
    }

    @Override
    public AgentSpec getAgentSpecVersionDetail(String namespaceId, String agentSpecName, String version)
            throws NacosException {
        namespaceId = resolveNamespace(namespaceId);
        Map<String, String> params = new HashMap<>(8);
        params.put("namespaceId", namespaceId);
        params.put("agentSpecName", agentSpecName);
        params.put("version", version);
        HttpRequest httpRequest = buildHttpRequestBuilder(buildRequestResource(namespaceId, agentSpecName)).setHttpMethod(HttpMethod.GET)
                .setPath(Constants.AdminApiPath.AI_AGENTSPEC_VERSION_ADMIN_PATH).setParamValue(params).build();
        HttpRestResult<String> restResult = executeSyncHttpRequest(httpRequest);
        Result<AgentSpec> result = JacksonUtils.toObj(restResult.getData(),
                new TypeReference<Result<AgentSpec>>() {
                });
        return result.getData();
    }
    
    @Override
    public boolean deleteAgentSpec(String namespaceId, String agentSpecName) throws NacosException {
        namespaceId = resolveNamespace(namespaceId);
        Map<String, String> params = new HashMap<>(4);
        params.put("namespaceId", namespaceId);
        params.put("agentSpecName", agentSpecName);
        HttpRequest httpRequest = buildHttpRequestBuilder(buildRequestResource(namespaceId, agentSpecName)).setHttpMethod(HttpMethod.DELETE)
                .setPath(Constants.AdminApiPath.AI_AGENTSPEC_ADMIN_PATH).setParamValue(params).build();
        HttpRestResult<String> restResult = executeSyncHttpRequest(httpRequest);
        Result<String> result = JacksonUtils.toObj(restResult.getData(),
                new TypeReference<Result<String>>() {
                });
        return ErrorCode.SUCCESS.getCode().equals(result.getCode());
    }
    
    @Override
    public Page<AgentSpecBasicInfo> listAgentSpecs(String namespaceId, String agentSpecName, String search, int pageNo,
            int pageSize) throws NacosException {
        namespaceId = resolveNamespace(namespaceId);
        Map<String, String> params = new HashMap<>(8);
        params.put("namespaceId", namespaceId);
        params.put("agentSpecName", agentSpecName);
        params.put("search", search);
        params.put("pageNo", String.valueOf(pageNo));
        params.put("pageSize", String.valueOf(pageSize));
        HttpRequest httpRequest = buildHttpRequestBuilder(buildRequestResource(namespaceId, agentSpecName)).setHttpMethod(HttpMethod.GET)
                .setPath(Constants.AdminApiPath.AI_AGENTSPEC_LIST_ADMIN_PATH).setParamValue(params).build();
        HttpRestResult<String> restResult = executeSyncHttpRequest(httpRequest);
        Result<Page<AgentSpecBasicInfo>> result = JacksonUtils.toObj(restResult.getData(),
                new TypeReference<Result<Page<AgentSpecBasicInfo>>>() {
                });
        return result.getData();
    }
    
    @Override
    public String uploadAgentSpecFromZip(String namespaceId, byte[] zipBytes, boolean overwrite)
            throws NacosException {
        namespaceId = resolveNamespace(namespaceId);
        Map<String, String> params = new HashMap<>(4);
        params.put("namespaceId", namespaceId);
        params.put("overwrite", String.valueOf(overwrite));
        HttpRequest httpRequest = buildHttpRequestBuilder(buildRequestResource(namespaceId, null)).setHttpMethod(HttpMethod.POST)
                .setPath(Constants.AdminApiPath.AI_AGENTSPEC_UPLOAD_ADMIN_PATH).setParamValue(params)
                .setFileUpload(zipBytes, "agentspec.zip", "file").build();
        HttpRestResult<String> restResult = executeSyncHttpRequest(httpRequest);
        Result<String> result = JacksonUtils.toObj(restResult.getData(),
                new TypeReference<Result<String>>() {
                });
        return result.getData();
    }
    
    @Override
    public String createDraft(String namespaceId, String agentSpecName, String basedOnVersion) throws NacosException {
        namespaceId = resolveNamespace(namespaceId);
        Map<String, String> params = new HashMap<>(8);
        params.put("namespaceId", namespaceId);
        params.put("agentSpecName", agentSpecName);
        putIfNotBlank(params, "basedOnVersion", basedOnVersion);
        HttpRequest httpRequest = buildHttpRequestBuilder(buildRequestResource(namespaceId, agentSpecName)).setHttpMethod(HttpMethod.POST)
                .setPath(Constants.AdminApiPath.AI_AGENTSPEC_ADMIN_PATH + "/draft").setParamValue(params).build();
        HttpRestResult<String> restResult = executeSyncHttpRequest(httpRequest);
        Result<String> result = JacksonUtils.toObj(restResult.getData(), new TypeReference<Result<String>>() {
        });
        return result.getData();
    }
    
    @Override
    public boolean updateDraft(String namespaceId, String agentSpecCard, Boolean setAsLatest) throws NacosException {
        namespaceId = resolveNamespace(namespaceId);
        Map<String, String> params = new HashMap<>(8);
        params.put("namespaceId", namespaceId);
        params.put("agentSpecCard", agentSpecCard);
        if (null != setAsLatest) {
            params.put("setAsLatest", String.valueOf(setAsLatest));
        }
        HttpRequest httpRequest = buildHttpRequestBuilder(buildRequestResource(namespaceId, null)).setHttpMethod(HttpMethod.PUT)
                .setPath(Constants.AdminApiPath.AI_AGENTSPEC_ADMIN_PATH + "/draft").setParamValue(params).build();
        HttpRestResult<String> restResult = executeSyncHttpRequest(httpRequest);
        Result<String> result = JacksonUtils.toObj(restResult.getData(), new TypeReference<Result<String>>() {
        });
        return ErrorCode.SUCCESS.getCode().equals(result.getCode());
    }
    
    @Override
    public boolean deleteDraft(String namespaceId, String agentSpecName) throws NacosException {
        namespaceId = resolveNamespace(namespaceId);
        Map<String, String> params = new HashMap<>(4);
        params.put("namespaceId", namespaceId);
        params.put("agentSpecName", agentSpecName);
        HttpRequest httpRequest = buildHttpRequestBuilder(buildRequestResource(namespaceId, agentSpecName)).setHttpMethod(HttpMethod.DELETE)
                .setPath(Constants.AdminApiPath.AI_AGENTSPEC_ADMIN_PATH + "/draft").setParamValue(params).build();
        HttpRestResult<String> restResult = executeSyncHttpRequest(httpRequest);
        Result<String> result = JacksonUtils.toObj(restResult.getData(), new TypeReference<Result<String>>() {
        });
        return ErrorCode.SUCCESS.getCode().equals(result.getCode());
    }
    
    @Override
    public String submit(String namespaceId, String agentSpecName, String version) throws NacosException {
        namespaceId = resolveNamespace(namespaceId);
        Map<String, String> params = new HashMap<>(8);
        params.put("namespaceId", namespaceId);
        params.put("agentSpecName", agentSpecName);
        putIfNotBlank(params, "version", version);
        HttpRequest httpRequest = buildHttpRequestBuilder(buildRequestResource(namespaceId, agentSpecName)).setHttpMethod(HttpMethod.POST)
                .setPath(Constants.AdminApiPath.AI_AGENTSPEC_ADMIN_PATH + "/submit").setParamValue(params).build();
        HttpRestResult<String> restResult = executeSyncHttpRequest(httpRequest);
        Result<String> result = JacksonUtils.toObj(restResult.getData(), new TypeReference<Result<String>>() {
        });
        return result.getData();
    }
    
    @Override
    public boolean publish(String namespaceId, String agentSpecName, String version, Boolean updateLatestLabel)
            throws NacosException {
        namespaceId = resolveNamespace(namespaceId);
        Map<String, String> params = new HashMap<>(8);
        params.put("namespaceId", namespaceId);
        params.put("agentSpecName", agentSpecName);
        params.put("version", version);
        if (null != updateLatestLabel) {
            params.put("updateLatestLabel", String.valueOf(updateLatestLabel));
        }
        HttpRequest httpRequest = buildHttpRequestBuilder(buildRequestResource(namespaceId, agentSpecName)).setHttpMethod(HttpMethod.POST)
                .setPath(Constants.AdminApiPath.AI_AGENTSPEC_ADMIN_PATH + "/publish").setParamValue(params).build();
        HttpRestResult<String> restResult = executeSyncHttpRequest(httpRequest);
        Result<String> result = JacksonUtils.toObj(restResult.getData(), new TypeReference<Result<String>>() {
        });
        return ErrorCode.SUCCESS.getCode().equals(result.getCode());
    }
    
    @Override
    public boolean updateLabels(String namespaceId, String agentSpecName, String labels) throws NacosException {
        namespaceId = resolveNamespace(namespaceId);
        Map<String, String> params = new HashMap<>(8);
        params.put("namespaceId", namespaceId);
        params.put("agentSpecName", agentSpecName);
        params.put("labels", labels);
        HttpRequest httpRequest = buildHttpRequestBuilder(buildRequestResource(namespaceId, agentSpecName)).setHttpMethod(HttpMethod.PUT)
                .setPath(Constants.AdminApiPath.AI_AGENTSPEC_ADMIN_PATH + "/labels").setParamValue(params).build();
        HttpRestResult<String> restResult = executeSyncHttpRequest(httpRequest);
        Result<String> result = JacksonUtils.toObj(restResult.getData(), new TypeReference<Result<String>>() {
        });
        return ErrorCode.SUCCESS.getCode().equals(result.getCode());
    }
    
    @Override
    public boolean changeOnlineStatus(String namespaceId, String agentSpecName, String scope, String version,
            boolean online) throws NacosException {
        namespaceId = resolveNamespace(namespaceId);
        Map<String, String> params = new HashMap<>(8);
        params.put("namespaceId", namespaceId);
        params.put("agentSpecName", agentSpecName);
        putIfNotBlank(params, "scope", scope);
        putIfNotBlank(params, "version", version);
        String op = online ? "/online" : "/offline";
        HttpRequest httpRequest = buildHttpRequestBuilder(buildRequestResource(namespaceId, agentSpecName)).setHttpMethod(HttpMethod.POST)
                .setPath(Constants.AdminApiPath.AI_AGENTSPEC_ADMIN_PATH + op).setParamValue(params).build();
        HttpRestResult<String> restResult = executeSyncHttpRequest(httpRequest);
        Result<String> result = JacksonUtils.toObj(restResult.getData(), new TypeReference<Result<String>>() {
        });
        return ErrorCode.SUCCESS.getCode().equals(result.getCode());
    }

    @Override
    public boolean updateScope(String namespaceId, String agentSpecName, String scope)
            throws NacosException {
        namespaceId = resolveNamespace(namespaceId);
        Map<String, String> params = new HashMap<>(4);
        params.put("namespaceId", namespaceId);
        params.put("agentSpecName", agentSpecName);
        params.put("scope", scope);
        HttpRequest httpRequest = buildHttpRequestBuilder(buildRequestResource(namespaceId, agentSpecName)).setHttpMethod(HttpMethod.PUT)
                .setPath(Constants.AdminApiPath.AI_AGENTSPEC_SCOPE_ADMIN_PATH).setParamValue(params).build();
        HttpRestResult<String> restResult = executeSyncHttpRequest(httpRequest);
        Result<String> result = JacksonUtils.toObj(restResult.getData(), new TypeReference<Result<String>>() {
        });
        return ErrorCode.SUCCESS.getCode().equals(result.getCode());
    }

    private AgentSpec extractAgentSpec(String namespaceId, String agentSpecName, JsonNode dataNode)
            throws NacosException {
        if (dataNode == null || dataNode.isNull()) {
            return null;
        }
        JsonNode resolvedNode = dataNode.has("agentSpec") ? dataNode.get("agentSpec") : dataNode;
        if (resolvedNode != null && !resolvedNode.isNull()) {
            return JacksonUtils.toObj(resolvedNode.toString(), AgentSpec.class);
        }
        String resolvedVersion = resolveAgentSpecVersion(dataNode);
        if (StringUtils.isBlank(resolvedVersion)) {
            return null;
        }
        return getAgentSpecVersionDetail(namespaceId, agentSpecName, resolvedVersion);
    }

    private String resolveAgentSpecVersion(JsonNode dataNode) {
        String currentVersion = readTextField(dataNode, "version");
        if (StringUtils.isNotBlank(currentVersion)) {
            return currentVersion;
        }
        String editingVersion = readTextField(dataNode, "editingVersion");
        if (StringUtils.isNotBlank(editingVersion)) {
            return editingVersion;
        }
        String reviewingVersion = readTextField(dataNode, "reviewingVersion");
        if (StringUtils.isNotBlank(reviewingVersion)) {
            return reviewingVersion;
        }
        JsonNode labelsNode = dataNode.get("labels");
        if (labelsNode != null && !labelsNode.isNull()) {
            String latestVersion = readTextField(labelsNode, "latest");
            if (StringUtils.isNotBlank(latestVersion)) {
                return latestVersion;
            }
        }
        return null;
    }

    private String readTextField(JsonNode dataNode, String fieldName) {
        JsonNode fieldNode = dataNode.get(fieldName);
        return fieldNode == null || fieldNode.isNull() ? null : fieldNode.asText();
    }
}
