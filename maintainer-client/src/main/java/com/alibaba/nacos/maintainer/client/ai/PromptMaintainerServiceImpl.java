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

import com.alibaba.nacos.api.ai.model.prompt.PromptMetaInfo;
import com.alibaba.nacos.api.ai.model.prompt.PromptMetaSummary;
import com.alibaba.nacos.api.ai.model.prompt.PromptVersionInfo;
import com.alibaba.nacos.api.ai.model.prompt.PromptVersionSummary;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.utils.HttpMethod;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.maintainer.client.constants.Constants;
import com.alibaba.nacos.maintainer.client.model.HttpRequest;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.HashMap;
import java.util.Map;

final class PromptMaintainerServiceImpl extends AbstractAiDelegateMaintainerService
    implements PromptMaintainerService {
    
    PromptMaintainerServiceImpl(AiMaintainerHttpContext context) {
        super(context);
    }
    
    @Override
    public Page<PromptMetaSummary> listPrompts(String namespaceId, String promptKey, String search,
        String bizTags,
        int pageNo, int pageSize) throws NacosException {
        namespaceId = resolveNamespace(namespaceId);
        Map<String, String> params = new HashMap<>(8);
        params.put("namespaceId", namespaceId);
        params.put("promptKey", promptKey);
        params.put("search", search);
        putIfNotBlank(params, "bizTags", bizTags);
        params.put("pageNo", String.valueOf(pageNo));
        params.put("pageSize", String.valueOf(pageSize));
        HttpRequest httpRequest =
            buildHttpRequestBuilder(buildRequestResource(namespaceId, promptKey))
                .setHttpMethod(HttpMethod.GET)
                .setPath(Constants.AdminApiPath.AI_PROMPT_LIST_ADMIN_PATH)
                .setParamValue(params).build();
        HttpRestResult<String> restResult = executeSyncHttpRequest(httpRequest);
        Result<Page<PromptMetaSummary>> result = JacksonUtils.toObj(restResult.getData(),
            new TypeReference<Result<Page<PromptMetaSummary>>>() {
            });
        return result.getData();
    }
    
    @Override
    public boolean deletePrompt(String namespaceId, String promptKey) throws NacosException {
        namespaceId = resolveNamespace(namespaceId);
        Map<String, String> params = new HashMap<>(4);
        params.put("namespaceId", namespaceId);
        params.put("promptKey", promptKey);
        HttpRequest httpRequest =
            buildHttpRequestBuilder(buildRequestResource(namespaceId, promptKey))
                .setHttpMethod(HttpMethod.DELETE)
                .setPath(Constants.AdminApiPath.AI_PROMPT_ADMIN_PATH)
                .setParamValue(params).build();
        HttpRestResult<String> restResult = executeSyncHttpRequest(httpRequest);
        Result<Boolean> result =
            JacksonUtils.toObj(restResult.getData(), new TypeReference<Result<Boolean>>() {
            });
        return Boolean.TRUE.equals(result.getData());
    }
    
    @Override
    public Page<PromptVersionSummary> listPromptVersions(String namespaceId, String promptKey,
        int pageNo, int pageSize)
        throws NacosException {
        namespaceId = resolveNamespace(namespaceId);
        Map<String, String> params = new HashMap<>(8);
        params.put("namespaceId", namespaceId);
        params.put("promptKey", promptKey);
        params.put("pageNo", String.valueOf(pageNo));
        params.put("pageSize", String.valueOf(pageSize));
        HttpRequest httpRequest =
            buildHttpRequestBuilder(buildRequestResource(namespaceId, promptKey))
                .setHttpMethod(HttpMethod.GET)
                .setPath(Constants.AdminApiPath.AI_PROMPT_VERSIONS_ADMIN_PATH)
                .setParamValue(params).build();
        HttpRestResult<String> restResult = executeSyncHttpRequest(httpRequest);
        Result<Page<PromptVersionSummary>> result = JacksonUtils.toObj(restResult.getData(),
            new TypeReference<Result<Page<PromptVersionSummary>>>() {
            });
        return result.getData();
    }
    
    // ========== Lifecycle APIs ==========
    
    @Override
    public PromptMetaInfo getPromptGovernanceDetail(String namespaceId, String promptKey)
        throws NacosException {
        namespaceId = resolveNamespace(namespaceId);
        Map<String, String> params = new HashMap<>(4);
        params.put("namespaceId", namespaceId);
        params.put("promptKey", promptKey);
        HttpRequest httpRequest =
            buildHttpRequestBuilder(buildRequestResource(namespaceId, promptKey))
                .setHttpMethod(HttpMethod.GET)
                .setPath(Constants.AdminApiPath.AI_PROMPT_GOVERNANCE_ADMIN_PATH)
                .setParamValue(params).build();
        HttpRestResult<String> restResult = executeSyncHttpRequest(httpRequest);
        Result<PromptMetaInfo> result = JacksonUtils.toObj(restResult.getData(),
            new TypeReference<Result<PromptMetaInfo>>() {
            });
        return result.getData();
    }
    
    @Override
    public PromptVersionInfo getVersionDetail(String namespaceId, String promptKey, String version)
        throws NacosException {
        namespaceId = resolveNamespace(namespaceId);
        Map<String, String> params = new HashMap<>(8);
        params.put("namespaceId", namespaceId);
        params.put("promptKey", promptKey);
        params.put("version", version);
        HttpRequest httpRequest =
            buildHttpRequestBuilder(buildRequestResource(namespaceId, promptKey))
                .setHttpMethod(HttpMethod.GET)
                .setPath(Constants.AdminApiPath.AI_PROMPT_VERSION_DETAIL_ADMIN_PATH)
                .setParamValue(params).build();
        HttpRestResult<String> restResult = executeSyncHttpRequest(httpRequest);
        Result<PromptVersionInfo> result = JacksonUtils.toObj(restResult.getData(),
            new TypeReference<Result<PromptVersionInfo>>() {
            });
        return result.getData();
    }
    
    @Override
    public String createDraft(String namespaceId, String promptKey, String basedOnVersion,
        String targetVersion,
        String template, String variables, String commitMsg, String description, String bizTags)
        throws NacosException {
        namespaceId = resolveNamespace(namespaceId);
        Map<String, String> params = new HashMap<>(16);
        params.put("namespaceId", namespaceId);
        params.put("promptKey", promptKey);
        putIfNotBlank(params, "basedOnVersion", basedOnVersion);
        putIfNotBlank(params, "targetVersion", targetVersion);
        putIfNotBlank(params, "template", template);
        putIfNotBlank(params, "variables", variables);
        putIfNotBlank(params, "commitMsg", commitMsg);
        putIfNotBlank(params, "description", description);
        putIfNotBlank(params, "bizTags", bizTags);
        HttpRequest httpRequest =
            buildHttpRequestBuilder(buildRequestResource(namespaceId, promptKey))
                .setHttpMethod(HttpMethod.POST)
                .setPath(Constants.AdminApiPath.AI_PROMPT_ADMIN_PATH + "/draft")
                .setParamValue(params).build();
        HttpRestResult<String> restResult = executeSyncHttpRequest(httpRequest);
        Result<String> result =
            JacksonUtils.toObj(restResult.getData(), new TypeReference<Result<String>>() {
            });
        return result.getData();
    }
    
    @Override
    public void updateDraft(String namespaceId, String promptKey, String template, String variables,
        String commitMsg)
        throws NacosException {
        namespaceId = resolveNamespace(namespaceId);
        Map<String, String> params = new HashMap<>(8);
        params.put("namespaceId", namespaceId);
        params.put("promptKey", promptKey);
        putIfNotBlank(params, "template", template);
        putIfNotBlank(params, "variables", variables);
        putIfNotBlank(params, "commitMsg", commitMsg);
        HttpRequest httpRequest =
            buildHttpRequestBuilder(buildRequestResource(namespaceId, promptKey))
                .setHttpMethod(HttpMethod.PUT)
                .setPath(Constants.AdminApiPath.AI_PROMPT_ADMIN_PATH + "/draft")
                .setParamValue(params).build();
        executeSyncHttpRequest(httpRequest);
    }
    
    @Override
    public void deleteDraft(String namespaceId, String promptKey) throws NacosException {
        namespaceId = resolveNamespace(namespaceId);
        Map<String, String> params = new HashMap<>(4);
        params.put("namespaceId", namespaceId);
        params.put("promptKey", promptKey);
        HttpRequest httpRequest =
            buildHttpRequestBuilder(buildRequestResource(namespaceId, promptKey))
                .setHttpMethod(HttpMethod.DELETE)
                .setPath(Constants.AdminApiPath.AI_PROMPT_ADMIN_PATH + "/draft")
                .setParamValue(params).build();
        executeSyncHttpRequest(httpRequest);
    }
    
    @Override
    public String submit(String namespaceId, String promptKey, String version)
        throws NacosException {
        namespaceId = resolveNamespace(namespaceId);
        Map<String, String> params = new HashMap<>(8);
        params.put("namespaceId", namespaceId);
        params.put("promptKey", promptKey);
        putIfNotBlank(params, "version", version);
        HttpRequest httpRequest =
            buildHttpRequestBuilder(buildRequestResource(namespaceId, promptKey))
                .setHttpMethod(HttpMethod.POST)
                .setPath(Constants.AdminApiPath.AI_PROMPT_ADMIN_PATH + "/submit")
                .setParamValue(params).build();
        HttpRestResult<String> restResult = executeSyncHttpRequest(httpRequest);
        Result<String> result =
            JacksonUtils.toObj(restResult.getData(), new TypeReference<Result<String>>() {
            });
        return result.getData();
    }
    
    @Override
    public void publish(String namespaceId, String promptKey, String version,
        Boolean updateLatestLabel)
        throws NacosException {
        namespaceId = resolveNamespace(namespaceId);
        Map<String, String> params = new HashMap<>(8);
        params.put("namespaceId", namespaceId);
        params.put("promptKey", promptKey);
        params.put("version", version);
        if (null != updateLatestLabel) {
            params.put("updateLatestLabel", String.valueOf(updateLatestLabel));
        }
        HttpRequest httpRequest =
            buildHttpRequestBuilder(buildRequestResource(namespaceId, promptKey))
                .setHttpMethod(HttpMethod.POST)
                .setPath(Constants.AdminApiPath.AI_PROMPT_ADMIN_PATH + "/publish")
                .setParamValue(params).build();
        executeSyncHttpRequest(httpRequest);
    }
    
    @Override
    public void forcePublish(String namespaceId, String promptKey, String version,
        Boolean updateLatestLabel)
        throws NacosException {
        namespaceId = resolveNamespace(namespaceId);
        Map<String, String> params = new HashMap<>(8);
        params.put("namespaceId", namespaceId);
        params.put("promptKey", promptKey);
        params.put("version", version);
        if (null != updateLatestLabel) {
            params.put("updateLatestLabel", String.valueOf(updateLatestLabel));
        }
        HttpRequest httpRequest =
            buildHttpRequestBuilder(buildRequestResource(namespaceId, promptKey))
                .setHttpMethod(HttpMethod.POST)
                .setPath(Constants.AdminApiPath.AI_PROMPT_ADMIN_PATH + "/force-publish")
                .setParamValue(params).build();
        executeSyncHttpRequest(httpRequest);
    }
    
    @Override
    public boolean redraft(String namespaceId, String promptKey, String version)
        throws NacosException {
        namespaceId = resolveNamespace(namespaceId);
        Map<String, String> params = new HashMap<>(8);
        params.put("namespaceId", namespaceId);
        params.put("promptKey", promptKey);
        params.put("version", version);
        HttpRequest httpRequest =
            buildHttpRequestBuilder(buildRequestResource(namespaceId, promptKey))
                .setHttpMethod(HttpMethod.POST)
                .setPath(Constants.AdminApiPath.AI_PROMPT_ADMIN_PATH + "/redraft")
                .setParamValue(params).build();
        HttpRestResult<String> restResult = executeSyncHttpRequest(httpRequest);
        Result<Boolean> result =
            JacksonUtils.toObj(restResult.getData(), new TypeReference<Result<Boolean>>() {
            });
        return Boolean.TRUE.equals(result.getData());
    }
    
    @Override
    public void changeOnlineStatus(String namespaceId, String promptKey, String version,
        boolean online)
        throws NacosException {
        namespaceId = resolveNamespace(namespaceId);
        Map<String, String> params = new HashMap<>(8);
        params.put("namespaceId", namespaceId);
        params.put("promptKey", promptKey);
        params.put("version", version);
        String op = online ? "/online" : "/offline";
        HttpRequest httpRequest =
            buildHttpRequestBuilder(buildRequestResource(namespaceId, promptKey))
                .setHttpMethod(HttpMethod.POST)
                .setPath(Constants.AdminApiPath.AI_PROMPT_ADMIN_PATH + op)
                .setParamValue(params).build();
        executeSyncHttpRequest(httpRequest);
    }
    
    @Override
    public void updateLabels(String namespaceId, String promptKey, String labels)
        throws NacosException {
        namespaceId = resolveNamespace(namespaceId);
        Map<String, String> params = new HashMap<>(8);
        params.put("namespaceId", namespaceId);
        params.put("promptKey", promptKey);
        params.put("labels", labels);
        HttpRequest httpRequest =
            buildHttpRequestBuilder(buildRequestResource(namespaceId, promptKey))
                .setHttpMethod(HttpMethod.PUT)
                .setPath(Constants.AdminApiPath.AI_PROMPT_ADMIN_PATH + "/labels")
                .setParamValue(params).build();
        executeSyncHttpRequest(httpRequest);
    }
    
    @Override
    public void updateDescription(String namespaceId, String promptKey, String description)
        throws NacosException {
        namespaceId = resolveNamespace(namespaceId);
        Map<String, String> params = new HashMap<>(8);
        params.put("namespaceId", namespaceId);
        params.put("promptKey", promptKey);
        params.put("description", description);
        HttpRequest httpRequest =
            buildHttpRequestBuilder(buildRequestResource(namespaceId, promptKey))
                .setHttpMethod(HttpMethod.PUT)
                .setPath(Constants.AdminApiPath.AI_PROMPT_DESCRIPTION_ADMIN_PATH)
                .setParamValue(params).build();
        executeSyncHttpRequest(httpRequest);
    }
    
    @Override
    public void updateBizTags(String namespaceId, String promptKey, String bizTags)
        throws NacosException {
        namespaceId = resolveNamespace(namespaceId);
        Map<String, String> params = new HashMap<>(8);
        params.put("namespaceId", namespaceId);
        params.put("promptKey", promptKey);
        params.put("bizTags", bizTags);
        HttpRequest httpRequest =
            buildHttpRequestBuilder(buildRequestResource(namespaceId, promptKey))
                .setHttpMethod(HttpMethod.PUT)
                .setPath(Constants.AdminApiPath.AI_PROMPT_BIZ_TAGS_ADMIN_PATH)
                .setParamValue(params).build();
        executeSyncHttpRequest(httpRequest);
    }
    
    // ========== Legacy compatibility implementations (deprecated) ==========
    
    @Deprecated
    @Override
    public PromptMetaInfo getPromptMeta(String namespaceId, String promptKey)
        throws NacosException {
        namespaceId = resolveNamespace(namespaceId);
        Map<String, String> params = new HashMap<>(4);
        params.put("namespaceId", namespaceId);
        params.put("promptKey", promptKey);
        HttpRequest httpRequest =
            buildHttpRequestBuilder(buildRequestResource(namespaceId, promptKey))
                .setHttpMethod(HttpMethod.GET)
                .setPath(Constants.AdminApiPath.AI_PROMPT_METADATA_ADMIN_PATH)
                .setParamValue(params).build();
        HttpRestResult<String> restResult = executeSyncHttpRequest(httpRequest);
        Result<PromptMetaInfo> result = JacksonUtils.toObj(restResult.getData(),
            new TypeReference<Result<PromptMetaInfo>>() {
            });
        return result.getData();
    }
    
    @Deprecated
    @Override
    public PromptVersionInfo queryPromptDetail(String namespaceId, String promptKey, String version,
        String label)
        throws NacosException {
        namespaceId = resolveNamespace(namespaceId);
        Map<String, String> params = new HashMap<>(8);
        params.put("namespaceId", namespaceId);
        params.put("promptKey", promptKey);
        putIfNotBlank(params, "version", version);
        putIfNotBlank(params, "label", label);
        HttpRequest httpRequest =
            buildHttpRequestBuilder(buildRequestResource(namespaceId, promptKey))
                .setHttpMethod(HttpMethod.GET)
                .setPath(Constants.AdminApiPath.AI_PROMPT_DETAIL_ADMIN_PATH)
                .setParamValue(params).build();
        HttpRestResult<String> restResult = executeSyncHttpRequest(httpRequest);
        Result<PromptVersionInfo> result = JacksonUtils.toObj(restResult.getData(),
            new TypeReference<Result<PromptVersionInfo>>() {
            });
        return result.getData();
    }
    
    @Deprecated
    @Override
    public boolean bindLabel(String namespaceId, String promptKey, String label, String version)
        throws NacosException {
        namespaceId = resolveNamespace(namespaceId);
        Map<String, String> params = new HashMap<>(8);
        params.put("namespaceId", namespaceId);
        params.put("promptKey", promptKey);
        params.put("label", label);
        params.put("version", version);
        HttpRequest httpRequest =
            buildHttpRequestBuilder(buildRequestResource(namespaceId, promptKey))
                .setHttpMethod(HttpMethod.PUT)
                .setPath(Constants.AdminApiPath.AI_PROMPT_LABEL_ADMIN_PATH)
                .setParamValue(params).build();
        HttpRestResult<String> restResult = executeSyncHttpRequest(httpRequest);
        Result<Boolean> result =
            JacksonUtils.toObj(restResult.getData(), new TypeReference<Result<Boolean>>() {
            });
        return Boolean.TRUE.equals(result.getData());
    }
    
    @Deprecated
    @Override
    public boolean unbindLabel(String namespaceId, String promptKey, String label)
        throws NacosException {
        namespaceId = resolveNamespace(namespaceId);
        Map<String, String> params = new HashMap<>(8);
        params.put("namespaceId", namespaceId);
        params.put("promptKey", promptKey);
        params.put("label", label);
        HttpRequest httpRequest =
            buildHttpRequestBuilder(buildRequestResource(namespaceId, promptKey))
                .setHttpMethod(HttpMethod.DELETE)
                .setPath(Constants.AdminApiPath.AI_PROMPT_LABEL_ADMIN_PATH)
                .setParamValue(params).build();
        HttpRestResult<String> restResult = executeSyncHttpRequest(httpRequest);
        Result<Boolean> result =
            JacksonUtils.toObj(restResult.getData(), new TypeReference<Result<Boolean>>() {
            });
        return Boolean.TRUE.equals(result.getData());
    }
    
    @Deprecated
    @Override
    public boolean publishPrompt(String namespaceId, String promptKey, String version,
        String template,
        String commitMsg, String description, String bizTags) throws NacosException {
        namespaceId = resolveNamespace(namespaceId);
        Map<String, String> params = new HashMap<>(16);
        params.put("namespaceId", namespaceId);
        params.put("promptKey", promptKey);
        params.put("version", version);
        params.put("template", template);
        putIfNotBlank(params, "commitMsg", commitMsg);
        putIfNotBlank(params, "description", description);
        putIfNotBlank(params, "bizTags", bizTags);
        HttpRequest httpRequest =
            buildHttpRequestBuilder(buildRequestResource(namespaceId, promptKey))
                .setHttpMethod(HttpMethod.POST).setPath(Constants.AdminApiPath.AI_PROMPT_ADMIN_PATH)
                .setParamValue(params).build();
        HttpRestResult<String> restResult = executeSyncHttpRequest(httpRequest);
        Result<Boolean> result =
            JacksonUtils.toObj(restResult.getData(), new TypeReference<Result<Boolean>>() {
            });
        return Boolean.TRUE.equals(result.getData());
    }
    
    @Deprecated
    @Override
    public boolean updatePromptMetadata(String namespaceId, String promptKey, String description,
        String bizTags)
        throws NacosException {
        namespaceId = resolveNamespace(namespaceId);
        Map<String, String> params = new HashMap<>(8);
        params.put("namespaceId", namespaceId);
        params.put("promptKey", promptKey);
        putIfNotBlank(params, "description", description);
        putIfNotBlank(params, "bizTags", bizTags);
        HttpRequest httpRequest =
            buildHttpRequestBuilder(buildRequestResource(namespaceId, promptKey))
                .setHttpMethod(HttpMethod.PUT)
                .setPath(Constants.AdminApiPath.AI_PROMPT_METADATA_ADMIN_PATH)
                .setParamValue(params).build();
        HttpRestResult<String> restResult = executeSyncHttpRequest(httpRequest);
        Result<Boolean> result =
            JacksonUtils.toObj(restResult.getData(), new TypeReference<Result<Boolean>>() {
            });
        return Boolean.TRUE.equals(result.getData());
    }
}
