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
import com.alibaba.nacos.api.ai.model.hub.HubAdjustShareRequest;
import com.alibaba.nacos.api.ai.model.hub.HubForceUnshareRequest;
import com.alibaba.nacos.api.ai.model.hub.HubShareResult;
import com.alibaba.nacos.api.ai.model.hub.HubSkillShareState;
import com.alibaba.nacos.api.ai.model.hub.HubSkillTransferRequest;
import com.alibaba.nacos.api.ai.model.hub.HubSkillTransferResult;
import com.alibaba.nacos.api.ai.model.hub.HubSkillSummary;
import com.alibaba.nacos.api.ai.model.hub.HubSkillListRequest;
import com.alibaba.nacos.api.ai.model.hub.HubSkillDetail;
import com.alibaba.nacos.api.ai.model.hub.HubSkillContent;
import com.alibaba.nacos.api.ai.model.hub.HubSkillDownload;
import com.alibaba.nacos.api.ai.model.hub.HubShareSkillRequest;
import com.alibaba.nacos.api.ai.model.hub.HubUnshareRequest;
import com.alibaba.nacos.api.ai.model.skills.BatchUploadResult;
import com.alibaba.nacos.api.ai.model.skills.Skill;
import com.alibaba.nacos.api.ai.model.skills.SkillMeta;
import com.alibaba.nacos.api.ai.model.skills.SkillSummary;
import com.alibaba.nacos.api.ai.model.skills.SkillUploadPrecheckResult;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.api.utils.json.JsonUtils;
import com.alibaba.nacos.api.utils.json.NacosTypeReference;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.utils.HttpMethod;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.common.utils.VersionUtils;
import com.alibaba.nacos.maintainer.client.constants.Constants;
import com.alibaba.nacos.maintainer.client.model.HttpRequest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Skill maintainer service implementation via HTTP.
 */
public class SkillMaintainerServiceImpl extends AbstractAiDelegateMaintainerService
    implements SkillMaintainerService {
    
    public SkillMaintainerServiceImpl(Properties properties) throws NacosException {
        this(new AiMaintainerHttpContext(properties));
    }
    
    SkillMaintainerServiceImpl(AiMaintainerHttpContext context) {
        super(context);
    }
    
    @Override
    public SkillMeta getSkillMeta(String namespaceId, String skillName) throws NacosException {
        namespaceId = resolveNamespace(namespaceId);
        Map<String, String> params = new HashMap<>(4);
        params.put("namespaceId", namespaceId);
        params.put("skillName", skillName);
        HttpRequest httpRequest =
            buildHttpRequestBuilder(buildRequestResource(namespaceId, skillName))
                .setHttpMethod(HttpMethod.GET).setPath(Constants.AdminApiPath.AI_SKILL_ADMIN_PATH)
                .setParamValue(params).build();
        HttpRestResult<String> restResult = executeSyncHttpRequest(httpRequest);
        Result<SkillMeta> result =
            JsonUtils.toObj(restResult.getData(), new NacosTypeReference<Result<SkillMeta>>() {
            });
        return result.getData();
    }
    
    @Override
    public Skill getSkillVersionDetail(String namespaceId, String skillName, String version)
        throws NacosException {
        namespaceId = resolveNamespace(namespaceId);
        Map<String, String> params = new HashMap<>(8);
        params.put("namespaceId", namespaceId);
        params.put("skillName", skillName);
        params.put("version", version);
        HttpRequest httpRequest =
            buildHttpRequestBuilder(buildRequestResource(namespaceId, skillName))
                .setHttpMethod(HttpMethod.GET)
                .setPath(Constants.AdminApiPath.AI_SKILL_VERSION_ADMIN_PATH)
                .setParamValue(params).build();
        HttpRestResult<String> restResult = executeSyncHttpRequest(httpRequest);
        Result<Skill> result =
            JsonUtils.toObj(restResult.getData(), new NacosTypeReference<Result<Skill>>() {
            });
        return result.getData();
    }
    
    @Override
    public boolean deleteSkill(String namespaceId, String skillName) throws NacosException {
        namespaceId = resolveNamespace(namespaceId);
        Map<String, String> params = new HashMap<>(4);
        params.put("namespaceId", namespaceId);
        params.put("skillName", skillName);
        HttpRequest httpRequest =
            buildHttpRequestBuilder(buildRequestResource(namespaceId, skillName))
                .setHttpMethod(HttpMethod.DELETE)
                .setPath(Constants.AdminApiPath.AI_SKILL_ADMIN_PATH)
                .setParamValue(params).build();
        HttpRestResult<String> restResult = executeSyncHttpRequest(httpRequest);
        Result<String> result =
            JsonUtils.toObj(restResult.getData(), new NacosTypeReference<Result<String>>() {
            });
        return ErrorCode.SUCCESS.getCode().equals(result.getCode());
    }
    
    @Override
    public Page<SkillSummary> listSkills(String namespaceId, String skillName, String search,
        int pageNo,
        int pageSize) throws NacosException {
        return listSkills(namespaceId, skillName, search, null, null, null, null, pageNo, pageSize);
    }
    
    @Override
    public Page<SkillSummary> listSkills(String namespaceId, String skillName, String search,
        String orderBy,
        String owner, String scope, String bizTag, int pageNo, int pageSize) throws NacosException {
        namespaceId = resolveNamespace(namespaceId);
        Map<String, String> params = new HashMap<>(12);
        params.put("namespaceId", namespaceId);
        params.put("skillName", skillName);
        params.put("search", search);
        params.put("pageNo", String.valueOf(pageNo));
        params.put("pageSize", String.valueOf(pageSize));
        if (StringUtils.isNotBlank(orderBy)) {
            params.put("orderBy", orderBy);
        }
        if (StringUtils.isNotBlank(owner)) {
            params.put("owner", owner);
        }
        if (StringUtils.isNotBlank(scope)) {
            params.put("scope", scope);
        }
        if (StringUtils.isNotBlank(bizTag)) {
            params.put("bizTag", bizTag);
        }
        HttpRequest httpRequest =
            buildHttpRequestBuilder(buildRequestResource(namespaceId, skillName))
                .setHttpMethod(HttpMethod.GET)
                .setPath(Constants.AdminApiPath.AI_SKILL_LIST_ADMIN_PATH)
                .setParamValue(params).build();
        HttpRestResult<String> restResult = executeSyncHttpRequest(httpRequest);
        Result<Page<SkillSummary>> result = JsonUtils.toObj(restResult.getData(),
            new NacosTypeReference<Result<Page<SkillSummary>>>() {
            });
        return result.getData();
    }
    
    @Override
    public String uploadSkillFromZip(String namespaceId, byte[] zipBytes, boolean overwrite)
        throws NacosException {
        return uploadSkillFromZip(namespaceId, zipBytes, overwrite, null, null);
    }
    
    @Override
    public String uploadSkillFromZip(String namespaceId, byte[] zipBytes, boolean overwrite,
        String targetVersion, String commitMsg)
        throws NacosException {
        return uploadSkillFromZip(namespaceId, zipBytes, overwrite, targetVersion, commitMsg,
            null);
    }
    
    @Override
    public String uploadSkillFromZip(String namespaceId, byte[] zipBytes, boolean overwrite,
        String targetVersion, String commitMsg, String uploadAction)
        throws NacosException {
        namespaceId = resolveNamespace(namespaceId);
        Map<String, String> params = new HashMap<>(8);
        params.put("namespaceId", namespaceId);
        params.put("overwrite", String.valueOf(overwrite));
        putIfNotBlank(params, "targetVersion", targetVersion);
        putIfNotBlank(params, "commitMsg", commitMsg);
        putIfNotBlank(params, "uploadAction", uploadAction);
        HttpRequest httpRequest = buildHttpRequestBuilder(buildRequestResource(namespaceId, null))
            .setHttpMethod(HttpMethod.POST)
            .setPath(Constants.AdminApiPath.AI_SKILL_UPLOAD_ADMIN_PATH)
            .setParamValue(params).setFileUpload(zipBytes, "skill.zip", "file").build();
        HttpRestResult<String> restResult = executeSyncHttpRequest(httpRequest);
        Result<String> result =
            JsonUtils.toObj(restResult.getData(), new NacosTypeReference<Result<String>>() {
            });
        return result.getData();
    }
    
    @Override
    public List<SkillUploadPrecheckResult> precheckUploadSkillFromZip(String namespaceId,
        byte[] zipBytes) throws NacosException {
        namespaceId = resolveNamespace(namespaceId);
        Map<String, String> params = new HashMap<>(2);
        params.put("namespaceId", namespaceId);
        HttpRequest httpRequest = buildHttpRequestBuilder(buildRequestResource(namespaceId, null))
            .setHttpMethod(HttpMethod.POST)
            .setPath(Constants.AdminApiPath.AI_SKILL_UPLOAD_PRECHECK_ADMIN_PATH)
            .setParamValue(params).setFileUpload(zipBytes, "skills.zip", "file").build();
        HttpRestResult<String> restResult = executeSyncHttpRequest(httpRequest);
        Result<List<SkillUploadPrecheckResult>> result = JsonUtils.toObj(restResult.getData(),
            new NacosTypeReference<Result<List<SkillUploadPrecheckResult>>>() {
            });
        return result.getData();
    }
    
    @Override
    public BatchUploadResult batchUploadSkillsFromZip(String namespaceId, byte[] zipBytes,
        boolean overwrite)
        throws NacosException {
        namespaceId = resolveNamespace(namespaceId);
        Map<String, String> params = new HashMap<>(4);
        params.put("namespaceId", namespaceId);
        params.put("overwrite", String.valueOf(overwrite));
        HttpRequest httpRequest = buildHttpRequestBuilder(buildRequestResource(namespaceId, null))
            .setHttpMethod(HttpMethod.POST)
            .setPath(Constants.AdminApiPath.AI_SKILL_BATCH_UPLOAD_ADMIN_PATH)
            .setParamValue(params).setFileUpload(zipBytes, "skills.zip", "file").build();
        HttpRestResult<String> restResult = executeSyncHttpRequest(httpRequest);
        Result<BatchUploadResult> result = JsonUtils.toObj(restResult.getData(),
            new NacosTypeReference<Result<BatchUploadResult>>() {
            });
        return result.getData();
    }
    
    @Override
    public String createDraft(String namespaceId, String skillName, String basedOnVersion,
        String targetVersion,
        String skillCard, String commitMsg)
        throws NacosException {
        namespaceId = resolveNamespace(namespaceId);
        Map<String, String> params = new HashMap<>(8);
        params.put("namespaceId", namespaceId);
        params.put("skillName", skillName);
        putIfNotBlank(params, "basedOnVersion", basedOnVersion);
        putIfNotBlank(params, "targetVersion", targetVersion);
        putIfNotBlank(params, "skillCard", skillCard);
        putIfNotBlank(params, "commitMsg", commitMsg);
        HttpRequest httpRequest =
            buildHttpRequestBuilder(buildRequestResource(namespaceId, skillName))
                .setHttpMethod(HttpMethod.POST)
                .setPath(Constants.AdminApiPath.AI_SKILL_ADMIN_PATH + "/draft")
                .setParamValue(params).build();
        HttpRestResult<String> restResult = executeSyncHttpRequest(httpRequest);
        Result<String> result =
            JsonUtils.toObj(restResult.getData(), new NacosTypeReference<Result<String>>() {
            });
        return result.getData();
    }
    
    @Override
    public boolean updateDraft(String namespaceId, String skillCard, Boolean setAsLatest,
        String commitMsg)
        throws NacosException {
        namespaceId = resolveNamespace(namespaceId);
        Map<String, String> params = new HashMap<>(8);
        params.put("namespaceId", namespaceId);
        params.put("skillCard", skillCard);
        if (null != setAsLatest) {
            params.put("setAsLatest", String.valueOf(setAsLatest));
        }
        putIfNotBlank(params, "commitMsg", commitMsg);
        HttpRequest httpRequest = buildHttpRequestBuilder(buildRequestResource(namespaceId, null))
            .setHttpMethod(HttpMethod.PUT)
            .setPath(Constants.AdminApiPath.AI_SKILL_ADMIN_PATH + "/draft")
            .setParamValue(params).build();
        HttpRestResult<String> restResult = executeSyncHttpRequest(httpRequest);
        Result<String> result =
            JsonUtils.toObj(restResult.getData(), new NacosTypeReference<Result<String>>() {
            });
        return ErrorCode.SUCCESS.getCode().equals(result.getCode());
    }
    
    @Override
    public boolean deleteDraft(String namespaceId, String skillName) throws NacosException {
        namespaceId = resolveNamespace(namespaceId);
        Map<String, String> params = new HashMap<>(4);
        params.put("namespaceId", namespaceId);
        params.put("skillName", skillName);
        HttpRequest httpRequest =
            buildHttpRequestBuilder(buildRequestResource(namespaceId, skillName))
                .setHttpMethod(HttpMethod.DELETE)
                .setPath(Constants.AdminApiPath.AI_SKILL_ADMIN_PATH + "/draft")
                .setParamValue(params).build();
        HttpRestResult<String> restResult = executeSyncHttpRequest(httpRequest);
        Result<String> result =
            JsonUtils.toObj(restResult.getData(), new NacosTypeReference<Result<String>>() {
            });
        return ErrorCode.SUCCESS.getCode().equals(result.getCode());
    }
    
    @Override
    public String submit(String namespaceId, String skillName, String version)
        throws NacosException {
        namespaceId = resolveNamespace(namespaceId);
        Map<String, String> params = new HashMap<>(8);
        params.put("namespaceId", namespaceId);
        params.put("skillName", skillName);
        putIfNotBlank(params, "version", version);
        HttpRequest httpRequest =
            buildHttpRequestBuilder(buildRequestResource(namespaceId, skillName))
                .setHttpMethod(HttpMethod.POST)
                .setPath(Constants.AdminApiPath.AI_SKILL_ADMIN_PATH + "/submit")
                .setParamValue(params).build();
        HttpRestResult<String> restResult = executeSyncHttpRequest(httpRequest);
        Result<String> result =
            JsonUtils.toObj(restResult.getData(), new NacosTypeReference<Result<String>>() {
            });
        return result.getData();
    }
    
    @Override
    public boolean publish(String namespaceId, String skillName, String version,
        Boolean updateLatestLabel)
        throws NacosException {
        namespaceId = resolveNamespace(namespaceId);
        Map<String, String> params = new HashMap<>(8);
        params.put("namespaceId", namespaceId);
        params.put("skillName", skillName);
        params.put("version", version);
        if (null != updateLatestLabel) {
            params.put("updateLatestLabel", String.valueOf(updateLatestLabel));
        }
        HttpRequest httpRequest =
            buildHttpRequestBuilder(buildRequestResource(namespaceId, skillName))
                .setHttpMethod(HttpMethod.POST)
                .setPath(Constants.AdminApiPath.AI_SKILL_ADMIN_PATH + "/publish")
                .setParamValue(params).build();
        HttpRestResult<String> restResult = executeSyncHttpRequest(httpRequest);
        Result<String> result =
            JsonUtils.toObj(restResult.getData(), new NacosTypeReference<Result<String>>() {
            });
        return ErrorCode.SUCCESS.getCode().equals(result.getCode());
    }
    
    @Override
    public boolean forcePublish(String namespaceId, String skillName, String version,
        Boolean updateLatestLabel)
        throws NacosException {
        namespaceId = resolveNamespace(namespaceId);
        Map<String, String> params = new HashMap<>(8);
        params.put("namespaceId", namespaceId);
        params.put("skillName", skillName);
        params.put("version", version);
        if (null != updateLatestLabel) {
            params.put("updateLatestLabel", String.valueOf(updateLatestLabel));
        }
        HttpRequest httpRequest =
            buildHttpRequestBuilder(buildRequestResource(namespaceId, skillName))
                .setHttpMethod(HttpMethod.POST)
                .setPath(Constants.AdminApiPath.AI_SKILL_ADMIN_PATH + "/force-publish")
                .setParamValue(params).build();
        HttpRestResult<String> restResult = executeSyncHttpRequest(httpRequest);
        Result<String> result =
            JsonUtils.toObj(restResult.getData(), new NacosTypeReference<Result<String>>() {
            });
        return ErrorCode.SUCCESS.getCode().equals(result.getCode());
    }
    
    @Override
    public boolean redraft(String namespaceId, String skillName, String version)
        throws NacosException {
        namespaceId = resolveNamespace(namespaceId);
        Map<String, String> params = new HashMap<>(8);
        params.put("namespaceId", namespaceId);
        params.put("skillName", skillName);
        params.put("version", version);
        HttpRequest httpRequest =
            buildHttpRequestBuilder(buildRequestResource(namespaceId, skillName))
                .setHttpMethod(HttpMethod.POST)
                .setPath(Constants.AdminApiPath.AI_SKILL_ADMIN_PATH + "/redraft")
                .setParamValue(params).build();
        HttpRestResult<String> restResult = executeSyncHttpRequest(httpRequest);
        Result<String> result =
            JsonUtils.toObj(restResult.getData(), new NacosTypeReference<Result<String>>() {
            });
        return ErrorCode.SUCCESS.getCode().equals(result.getCode());
    }
    
    @Override
    public boolean updateLabels(String namespaceId, String skillName, String labels)
        throws NacosException {
        namespaceId = resolveNamespace(namespaceId);
        Map<String, String> params = new HashMap<>(8);
        params.put("namespaceId", namespaceId);
        params.put("skillName", skillName);
        params.put("labels", labels);
        HttpRequest httpRequest =
            buildHttpRequestBuilder(buildRequestResource(namespaceId, skillName))
                .setHttpMethod(HttpMethod.PUT)
                .setPath(Constants.AdminApiPath.AI_SKILL_ADMIN_PATH + "/labels")
                .setParamValue(params).build();
        HttpRestResult<String> restResult = executeSyncHttpRequest(httpRequest);
        Result<String> result =
            JsonUtils.toObj(restResult.getData(), new NacosTypeReference<Result<String>>() {
            });
        return ErrorCode.SUCCESS.getCode().equals(result.getCode());
    }
    
    @Override
    public boolean updateBizTags(String namespaceId, String skillName, String bizTags)
        throws NacosException {
        namespaceId = resolveNamespace(namespaceId);
        Map<String, String> params = new HashMap<>(8);
        params.put("namespaceId", namespaceId);
        params.put("skillName", skillName);
        params.put("bizTags", bizTags);
        HttpRequest httpRequest =
            buildHttpRequestBuilder(buildRequestResource(namespaceId, skillName))
                .setHttpMethod(HttpMethod.PUT)
                .setPath(Constants.AdminApiPath.AI_SKILL_BIZ_TAGS_ADMIN_PATH)
                .setParamValue(params).build();
        HttpRestResult<String> restResult = executeSyncHttpRequest(httpRequest);
        Result<String> result =
            JsonUtils.toObj(restResult.getData(), new NacosTypeReference<Result<String>>() {
            });
        return ErrorCode.SUCCESS.getCode().equals(result.getCode());
    }
    
    @Override
    public boolean changeOnlineStatus(String namespaceId, String skillName, String scope,
        String version,
        boolean online) throws NacosException {
        namespaceId = resolveNamespace(namespaceId);
        Map<String, String> params = new HashMap<>(8);
        params.put("namespaceId", namespaceId);
        params.put("skillName", skillName);
        putIfNotBlank(params, "scope", scope);
        putIfNotBlank(params, "version", version);
        String op = online ? "/online" : "/offline";
        HttpRequest httpRequest =
            buildHttpRequestBuilder(buildRequestResource(namespaceId, skillName))
                .setHttpMethod(HttpMethod.POST)
                .setPath(Constants.AdminApiPath.AI_SKILL_ADMIN_PATH + op)
                .setParamValue(params).build();
        HttpRestResult<String> restResult = executeSyncHttpRequest(httpRequest);
        Result<String> result =
            JsonUtils.toObj(restResult.getData(), new NacosTypeReference<Result<String>>() {
            });
        return ErrorCode.SUCCESS.getCode().equals(result.getCode());
    }
    
    @Override
    public boolean updateScope(String namespaceId, String skillName, String scope)
        throws NacosException {
        namespaceId = resolveNamespace(namespaceId);
        Map<String, String> params = new HashMap<>(4);
        params.put("namespaceId", namespaceId);
        params.put("skillName", skillName);
        params.put("scope", scope);
        HttpRequest httpRequest =
            buildHttpRequestBuilder(buildRequestResource(namespaceId, skillName))
                .setHttpMethod(HttpMethod.PUT)
                .setPath(Constants.AdminApiPath.AI_SKILL_SCOPE_ADMIN_PATH)
                .setParamValue(params).build();
        HttpRestResult<String> restResult = executeSyncHttpRequest(httpRequest);
        Result<String> result =
            JsonUtils.toObj(restResult.getData(), new NacosTypeReference<Result<String>>() {
            });
        return ErrorCode.SUCCESS.getCode().equals(result.getCode());
    }
    
    @Override
    public HubShareResult shareHubSkill(HubShareSkillRequest request) throws NacosException {
        requireHubRequest(request);
        Map<String, String> params = new HashMap<>(4);
        params.put("sourceNamespaceId", request.getSourceNamespaceId());
        params.put("sourceName", request.getSourceName());
        params.put("sourceVersion", request.getSourceVersion());
        if (request.getExpectedSourceResourceId() != null) {
            params.put("expectedSourceResourceId",
                request.getExpectedSourceResourceId().toString());
        }
        return executeHubRequest("/share", params, request.getSourceNamespaceId(),
            request.getSourceName());
    }
    
    @Override
    public HubShareResult adjustHubShare(HubAdjustShareRequest request) throws NacosException {
        requireHubRequest(request);
        Map<String, String> params = new HashMap<>(3);
        params.put("hubEntryId", String.valueOf(request.getHubEntryId()));
        params.put("targetVersion", request.getTargetVersion());
        params.put("expectedMetaVersion", String.valueOf(request.getExpectedMetaVersion()));
        return executeHubRequest("/adjust", params, "", "ai-hub/entry");
    }
    
    @Override
    public HubShareResult unshareHubSkill(HubUnshareRequest request) throws NacosException {
        requireHubRequest(request);
        Map<String, String> params = new HashMap<>(2);
        params.put("hubEntryId", String.valueOf(request.getHubEntryId()));
        params.put("expectedMetaVersion", String.valueOf(request.getExpectedMetaVersion()));
        return executeHubRequest("/unshare", params, "", "ai-hub/entry");
    }
    
    @Override
    public HubShareResult forceUnshareHubSkill(HubForceUnshareRequest request)
        throws NacosException {
        requireHubRequest(request);
        Map<String, String> params = new HashMap<>(3);
        params.put("hubEntryId", String.valueOf(request.getHubEntryId()));
        params.put("expectedMetaVersion", String.valueOf(request.getExpectedMetaVersion()));
        params.put("reason", request.getReason());
        return executeHubRequest("/force-unshare", params, "", AiConstants.Hub.ADMIN_RESOURCE);
    }
    
    @Override
    public HubSkillTransferResult importHubSkill(HubSkillTransferRequest request)
        throws NacosException {
        validateHubTransfer(request, true);
        Map<String, String> params = hubTransferTargetParams(request);
        params.put("hubEntryId", request.getHubEntryId().toString());
        return executeHubTransfer("/import", params);
    }
    
    @Override
    public HubSkillTransferResult copyHubSkill(HubSkillTransferRequest request)
        throws NacosException {
        validateHubTransfer(request, false);
        Map<String, String> params = hubTransferTargetParams(request);
        params.put("sourceNamespaceId", request.getSourceNamespaceId());
        params.put("sourceName", request.getSourceName());
        params.put("sourceVersion", request.getSourceVersion());
        return executeHubTransfer("/copy", params);
    }
    
    private Map<String, String> hubTransferTargetParams(HubSkillTransferRequest request) {
        Map<String, String> params = new HashMap<>(7);
        params.put("targetNamespaceId", request.getTargetNamespaceId());
        params.put("targetName", request.getTargetName());
        if (StringUtils.isNotBlank(request.getTargetVersion())) {
            params.put("targetVersion", request.getTargetVersion());
        }
        return params;
    }
    
    private void validateHubTransfer(HubSkillTransferRequest request, boolean fromHub)
        throws NacosException {
        requireHubRequest(request);
        requireTransferText(request.getTargetNamespaceId(), 128);
        requireTransferText(request.getTargetName(), 256);
        if ("__nacos_ai_hub__".equals(request.getTargetNamespaceId())) {
            throw invalidTransfer();
        }
        String version = request.getTargetVersion();
        if (StringUtils.isNotBlank(version) && (version.length() > 64
            || !version.equals(version.trim())
            || !VersionUtils.isSupportedVersionFormat(version))) {
            throw invalidTransfer();
        }
        if (fromHub) {
            if (request.getHubEntryId() == null || request.getHubEntryId() <= 0
                || request.getSourceNamespaceId() != null || request.getSourceName() != null
                || request.getSourceVersion() != null) {
                throw invalidTransfer();
            }
        } else {
            requireTransferText(request.getSourceNamespaceId(), 128);
            requireTransferText(request.getSourceName(), 256);
            requireTransferText(request.getSourceVersion(), 64);
            if (request.getHubEntryId() != null
                || "__nacos_ai_hub__".equals(request.getSourceNamespaceId())
                || "latest".equalsIgnoreCase(request.getSourceVersion())) {
                throw invalidTransfer();
            }
        }
    }
    
    private void requireTransferText(String text, int limit) throws NacosException {
        if (StringUtils.isBlank(text) || text.length() > limit || !text.equals(text.trim())) {
            throw invalidTransfer();
        }
    }
    
    private NacosException invalidTransfer() {
        return new NacosException(NacosException.INVALID_PARAM,
            "Invalid Hub Skill transfer request");
    }
    
    private HubSkillTransferResult executeHubTransfer(String suffix, Map<String, String> params)
        throws NacosException {
        HttpRequest request = buildHttpRequestBuilder(buildRequestResource("", "ai-hub/entry"))
            .setHttpMethod(HttpMethod.POST)
            .setPath(Constants.AdminApiPath.AI_HUB_SKILL_ADMIN_PATH + suffix)
            .setParamValue(params).build();
        HttpRestResult<String> response = executeSyncHttpRequest(request);
        try {
            Result<HubSkillTransferResult> result =
                response == null ? null : JsonUtils.toObj(response.getData(),
                    new NacosTypeReference<Result<HubSkillTransferResult>>() {
                    });
            if (result != null && ErrorCode.SUCCESS.getCode().equals(result.getCode())
                && result.getData() != null) {
                return result.getData();
            }
        } catch (RuntimeException failure) {
            throw new NacosException(NacosException.SERVER_ERROR, "Invalid Hub transfer response");
        }
        throw new NacosException(NacosException.SERVER_ERROR, "Invalid Hub transfer response");
    }
    
    @Override
    public HubSkillShareState getHubSkillSourceShare(String namespaceId, String name)
        throws NacosException {
        if (StringUtils.isBlank(namespaceId) || StringUtils.isBlank(name)) {
            throw new NacosException(NacosException.INVALID_PARAM,
                "Source Skill identity required");
        }
        Map<String, String> params = new HashMap<>(2);
        params.put("sourceNamespaceId", namespaceId);
        params.put("sourceName", name);
        return executeHubState("/source-share", params, namespaceId, name);
    }
    
    @Override
    public HubSkillShareState getHubSkillGovernanceState(Long entryId) throws NacosException {
        if (entryId == null || entryId <= 0) {
            throw new NacosException(NacosException.INVALID_PARAM, "Invalid Hub entry ID");
        }
        Map<String, String> params = new HashMap<>(1);
        params.put("hubEntryId", String.valueOf(entryId));
        return executeHubState("/governance-state", params, "", AiConstants.Hub.ADMIN_RESOURCE);
    }
    
    private HubSkillShareState executeHubState(String suffix, Map<String, String> params,
        String namespaceId, String resourceName) throws NacosException {
        HttpRequest request =
            buildHttpRequestBuilder(buildRequestResource(namespaceId, resourceName))
                .setHttpMethod(HttpMethod.GET)
                .setPath(Constants.AdminApiPath.AI_HUB_SKILL_ADMIN_PATH + suffix)
                .setParamValue(params)
                .build();
        HttpRestResult<String> restResult = executeSyncHttpRequest(request);
        Result<HubSkillShareState> result;
        try {
            result = JsonUtils.toObj(restResult.getData(),
                new NacosTypeReference<Result<HubSkillShareState>>() {
                });
        } catch (RuntimeException invalid) {
            throw new NacosException(NacosException.SERVER_ERROR,
                "Invalid Hub share state response", invalid);
        }
        if (result == null || !ErrorCode.SUCCESS.getCode().equals(result.getCode())) {
            throw new NacosException(NacosException.SERVER_ERROR,
                "Invalid or unsuccessful Hub share state response");
        }
        HubSkillShareState state = result.getData();
        if (state == null && "/source-share".equals(suffix)) {
            return null;
        }
        if (state == null || state.getHubEntryId() == null || state.getHubEntryId() <= 0
            || state.getMetaVersion() == null || state.getMetaVersion() <= 0
            || StringUtils.isBlank(state.getSharedVersion())
            || ("/governance-state".equals(suffix)
                && !params.get("hubEntryId").equals(String.valueOf(state.getHubEntryId())))) {
            throw new NacosException(NacosException.SERVER_ERROR,
                "Invalid Hub share state response");
        }
        return state;
    }
    
    private HubShareResult executeHubRequest(String suffix, Map<String, String> params,
        String namespaceId, String resourceName) throws NacosException {
        HttpRequest httpRequest =
            buildHttpRequestBuilder(buildRequestResource(namespaceId, resourceName))
                .setHttpMethod(HttpMethod.POST)
                .setPath(Constants.AdminApiPath.AI_HUB_SKILL_ADMIN_PATH + suffix)
                .setParamValue(params)
                .build();
        HttpRestResult<String> restResult = executeSyncHttpRequest(httpRequest);
        Result<HubShareResult> result;
        try {
            result = JsonUtils.toObj(restResult.getData(),
                new NacosTypeReference<Result<HubShareResult>>() {
                });
        } catch (RuntimeException e) {
            throw new NacosException(NacosException.SERVER_ERROR,
                "Invalid Hub mutation response", e);
        }
        if (result == null || !ErrorCode.SUCCESS.getCode().equals(result.getCode())
            || result.getData() == null) {
            throw new NacosException(NacosException.SERVER_ERROR,
                "Invalid or unsuccessful Hub mutation response");
        }
        return result.getData();
    }
    
    @Override
    public Page<HubSkillSummary> listHubSkills(
        HubSkillListRequest request) throws NacosException {
        requireHubRequest(request);
        if (request.getPageNo() < 1 || request.getPageSize() < 1 || request.getPageSize() > 100) {
            throw new NacosException(NacosException.INVALID_PARAM, "Invalid Hub page parameters");
        }
        Map<String, String> params = new HashMap<>();
        params.put("pageNo", String.valueOf(request.getPageNo()));
        params.put("pageSize", String.valueOf(request.getPageSize()));
        if (StringUtils.isNotBlank(request.getQuery())) {
            params.put("query", request.getQuery());
        }
        if (request.getQuery() != null && request.getQuery().length() > 1024) {
            throw new NacosException(NacosException.INVALID_PARAM, "Hub query is too long");
        }
        if (request.getTagsAll() != null) {
            if (request.getTagsAll().size() > 32) {
                throw new NacosException(NacosException.INVALID_PARAM, "Too many Hub tags");
            }
            for (int i = 0; i < request.getTagsAll().size(); i++) {
                String tag = request.getTagsAll().get(i);
                if (StringUtils.isBlank(tag)) {
                    continue;
                }
                if (tag.trim().length() > 128) {
                    throw new NacosException(NacosException.INVALID_PARAM, "Hub tag is too long");
                }
                params.put("tagsAll[" + i + "]", tag.trim());
            }
        }
        HttpRestResult<String> response = executeHubRead("/list", params);
        return parseHubReadResponse(response,
            new NacosTypeReference<Result<Page<HubSkillSummary>>>() {
            });
    }
    
    @Override
    public HubSkillDetail getHubSkillDetail(Long id)
        throws NacosException {
        HttpRestResult<String> response = executeHubRead("/detail", hubIdParams(id));
        return parseHubReadResponse(response, new NacosTypeReference<Result<HubSkillDetail>>() {
        });
    }
    
    @Override
    public HubSkillContent getHubSkillContent(Long id)
        throws NacosException {
        HttpRestResult<String> response = executeHubRead("/content", hubIdParams(id));
        return parseHubReadResponse(response, new NacosTypeReference<Result<HubSkillContent>>() {
        });
    }
    
    private <T> T parseHubReadResponse(HttpRestResult<String> response,
        NacosTypeReference<Result<T>> type) throws NacosException {
        try {
            Result<T> parsed = response == null ? null : JsonUtils.toObj(response.getData(), type);
            if (parsed != null && ErrorCode.SUCCESS.getCode().equals(parsed.getCode())
                && parsed.getData() != null) {
                return parsed.getData();
            }
        } catch (RuntimeException failure) {
            throw new NacosException(NacosException.SERVER_ERROR, "Invalid Hub read response");
        }
        throw new NacosException(NacosException.SERVER_ERROR, "Invalid Hub read response");
    }
    
    private Map<String, String> hubIdParams(Long id) throws NacosException {
        if (id == null || id <= 0) {
            throw new NacosException(NacosException.INVALID_PARAM, "hubEntryId must be positive");
        }
        Map<String, String> params = new HashMap<>(1);
        params.put("hubEntryId", id.toString());
        return params;
    }
    
    private HttpRestResult<String> executeHubRead(String suffix, Map<String, String> params)
        throws NacosException {
        HttpRequest request = buildHttpRequestBuilder(buildRequestResource("", "ai-hub/entry"))
            .setHttpMethod(HttpMethod.GET)
            .setPath(Constants.AdminApiPath.AI_HUB_SKILL_ADMIN_PATH + suffix)
            .setParamValue(params)
            .build();
        return executeSyncHttpRequest(request);
    }
    
    private void requireHubRequest(Object request) throws NacosException {
        if (request == null) {
            throw new NacosException(NacosException.INVALID_PARAM, "Hub request is required");
        }
    }
    
    @Override
    public HubSkillDownload getHubSkillDownload(Long hubEntryId) throws NacosException {
        HubSkillDownload download = parseHubReadResponse(
            executeHubRead("/download", hubIdParams(hubEntryId)),
            new NacosTypeReference<Result<HubSkillDownload>>() {
            });
        if (!hubEntryId.equals(download.getHubEntryId())
            || StringUtils.isBlank(download.getDisplayName())
            || StringUtils.isBlank(download.getSharedVersion())
            || download.getZipBytes() == null || download.getZipBytes().length == 0) {
            throw new NacosException(NacosException.SERVER_ERROR, "Invalid Hub download response");
        }
        return download;
    }
}
