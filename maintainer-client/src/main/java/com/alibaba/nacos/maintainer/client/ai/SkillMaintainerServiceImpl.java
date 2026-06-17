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

import com.alibaba.nacos.api.ai.model.skills.BatchUploadResult;
import com.alibaba.nacos.api.ai.model.skills.Skill;
import com.alibaba.nacos.api.ai.model.skills.SkillMeta;
import com.alibaba.nacos.api.ai.model.skills.SkillSummary;
import com.alibaba.nacos.api.ai.model.skills.SkillUploadPrecheckRequest;
import com.alibaba.nacos.api.ai.model.skills.SkillUploadPrecheckResult;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.api.utils.json.JsonUtils;
import com.alibaba.nacos.api.utils.json.NacosTypeReference;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.utils.HttpMethod;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.maintainer.client.constants.Constants;
import com.alibaba.nacos.maintainer.client.model.HttpRequest;

import java.util.Collections;
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
    public List<SkillUploadPrecheckResult> batchPrecheckUploadSkill(
        List<SkillUploadPrecheckRequest> requests) throws NacosException {
        if (requests == null || requests.isEmpty()) {
            return Collections.emptyList();
        }
        for (SkillUploadPrecheckRequest req : requests) {
            if (req != null) {
                req.setNamespaceId(resolveNamespace(req.getNamespaceId()));
            }
        }
        String firstNs = requests.get(0) != null ? requests.get(0).getNamespaceId() : null;
        HttpRequest httpRequest = buildHttpRequestBuilder(
            buildRequestResource(firstNs, null))
            .setHttpMethod(HttpMethod.POST)
            .setPath(Constants.AdminApiPath.AI_SKILL_BATCH_UPLOAD_PRECHECK_ADMIN_PATH)
            .setBody(JacksonUtils.toJson(requests)).build();
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
}
