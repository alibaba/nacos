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

final class PromptMaintainerServiceImpl extends AbstractAiDelegateMaintainerService implements PromptMaintainerService {

    PromptMaintainerServiceImpl(AiMaintainerHttpContext context) {
        super(context);
    }

    @Override
    public Page<PromptMetaSummary> listPrompts(String namespaceId, String promptKey, String search, String bizTags,
            int pageNo, int pageSize) throws NacosException {
        namespaceId = resolveNamespace(namespaceId);
        Map<String, String> params = new HashMap<>(8);
        params.put("namespaceId", namespaceId);
        params.put("promptKey", promptKey);
        params.put("search", search);
        putIfNotBlank(params, "bizTags", bizTags);
        params.put("pageNo", String.valueOf(pageNo));
        params.put("pageSize", String.valueOf(pageSize));
        HttpRequest httpRequest = buildHttpRequestBuilder(buildRequestResource(namespaceId, promptKey))
                .setHttpMethod(HttpMethod.GET).setPath(Constants.AdminApiPath.AI_PROMPT_LIST_ADMIN_PATH)
                .setParamValue(params).build();
        HttpRestResult<String> restResult = executeSyncHttpRequest(httpRequest);
        Result<Page<PromptMetaSummary>> result = JacksonUtils.toObj(restResult.getData(),
                new TypeReference<Result<Page<PromptMetaSummary>>>() {
                });
        return result.getData();
    }

    @Override
    public PromptMetaInfo getPromptMeta(String namespaceId, String promptKey) throws NacosException {
        namespaceId = resolveNamespace(namespaceId);
        Map<String, String> params = new HashMap<>(4);
        params.put("namespaceId", namespaceId);
        params.put("promptKey", promptKey);
        HttpRequest httpRequest = buildHttpRequestBuilder(buildRequestResource(namespaceId, promptKey))
                .setHttpMethod(HttpMethod.GET).setPath(Constants.AdminApiPath.AI_PROMPT_METADATA_ADMIN_PATH)
                .setParamValue(params).build();
        HttpRestResult<String> restResult = executeSyncHttpRequest(httpRequest);
        Result<PromptMetaInfo> result = JacksonUtils.toObj(restResult.getData(),
                new TypeReference<Result<PromptMetaInfo>>() {
                });
        return result.getData();
    }

    @Override
    public PromptVersionInfo queryPromptDetail(String namespaceId, String promptKey, String version, String label)
            throws NacosException {
        namespaceId = resolveNamespace(namespaceId);
        Map<String, String> params = new HashMap<>(8);
        params.put("namespaceId", namespaceId);
        params.put("promptKey", promptKey);
        putIfNotBlank(params, "version", version);
        putIfNotBlank(params, "label", label);
        HttpRequest httpRequest = buildHttpRequestBuilder(buildRequestResource(namespaceId, promptKey))
                .setHttpMethod(HttpMethod.GET).setPath(Constants.AdminApiPath.AI_PROMPT_DETAIL_ADMIN_PATH)
                .setParamValue(params).build();
        HttpRestResult<String> restResult = executeSyncHttpRequest(httpRequest);
        Result<PromptVersionInfo> result = JacksonUtils.toObj(restResult.getData(),
                new TypeReference<Result<PromptVersionInfo>>() {
                });
        return result.getData();
    }

    @Override
    public boolean bindLabel(String namespaceId, String promptKey, String label, String version) throws NacosException {
        return updatePromptLabel(namespaceId, promptKey, label, version, HttpMethod.PUT);
    }

    @Override
    public boolean unbindLabel(String namespaceId, String promptKey, String label) throws NacosException {
        return updatePromptLabel(namespaceId, promptKey, label, null, HttpMethod.DELETE);
    }

    @Override
    public boolean publishPrompt(String namespaceId, String promptKey, String version, String template,
            String commitMsg, String description, String bizTags) throws NacosException {
        return publishPrompt(namespaceId, promptKey, version, template, commitMsg, description, bizTags, null);
    }

    @Override
    public boolean publishPrompt(String namespaceId, String promptKey, String version, String template,
            String commitMsg, String description, String bizTags, String variables) throws NacosException {
        namespaceId = resolveNamespace(namespaceId);
        Map<String, String> params = new HashMap<>(8);
        params.put("namespaceId", namespaceId);
        params.put("promptKey", promptKey);
        params.put("version", version);
        params.put("template", template);
        putIfNotBlank(params, "commitMsg", commitMsg);
        if (null != description) {
            params.put("description", description);
        }
        if (null != bizTags) {
            params.put("bizTags", bizTags);
        }
        putIfNotBlank(params, "variables", variables);
        HttpRequest httpRequest = buildHttpRequestBuilder(buildRequestResource(namespaceId, promptKey))
                .setHttpMethod(HttpMethod.POST).setPath(Constants.AdminApiPath.AI_PROMPT_ADMIN_PATH)
                .setParamValue(params).build();
        HttpRestResult<String> restResult = executeSyncHttpRequest(httpRequest);
        Result<Boolean> result = JacksonUtils.toObj(restResult.getData(), new TypeReference<Result<Boolean>>() {
        });
        return Boolean.TRUE.equals(result.getData());
    }

    @Override
    public boolean deletePrompt(String namespaceId, String promptKey) throws NacosException {
        namespaceId = resolveNamespace(namespaceId);
        Map<String, String> params = new HashMap<>(4);
        params.put("namespaceId", namespaceId);
        params.put("promptKey", promptKey);
        HttpRequest httpRequest = buildHttpRequestBuilder(buildRequestResource(namespaceId, promptKey))
                .setHttpMethod(HttpMethod.DELETE).setPath(Constants.AdminApiPath.AI_PROMPT_ADMIN_PATH)
                .setParamValue(params).build();
        HttpRestResult<String> restResult = executeSyncHttpRequest(httpRequest);
        Result<Boolean> result = JacksonUtils.toObj(restResult.getData(), new TypeReference<Result<Boolean>>() {
        });
        return Boolean.TRUE.equals(result.getData());
    }

    @Override
    public Page<PromptVersionSummary> listPromptVersions(String namespaceId, String promptKey, int pageNo, int pageSize)
            throws NacosException {
        namespaceId = resolveNamespace(namespaceId);
        Map<String, String> params = new HashMap<>(8);
        params.put("namespaceId", namespaceId);
        params.put("promptKey", promptKey);
        params.put("pageNo", String.valueOf(pageNo));
        params.put("pageSize", String.valueOf(pageSize));
        HttpRequest httpRequest = buildHttpRequestBuilder(buildRequestResource(namespaceId, promptKey))
                .setHttpMethod(HttpMethod.GET).setPath(Constants.AdminApiPath.AI_PROMPT_VERSIONS_ADMIN_PATH)
                .setParamValue(params).build();
        HttpRestResult<String> restResult = executeSyncHttpRequest(httpRequest);
        Result<Page<PromptVersionSummary>> result = JacksonUtils.toObj(restResult.getData(),
                new TypeReference<Result<Page<PromptVersionSummary>>>() {
                });
        return result.getData();
    }

    @Override
    public boolean updatePromptMetadata(String namespaceId, String promptKey, String description, String bizTags)
            throws NacosException {
        namespaceId = resolveNamespace(namespaceId);
        Map<String, String> params = new HashMap<>(4);
        params.put("namespaceId", namespaceId);
        params.put("promptKey", promptKey);
        if (null != description) {
            params.put("description", description);
        }
        if (null != bizTags) {
            params.put("bizTags", bizTags);
        }
        HttpRequest httpRequest = buildHttpRequestBuilder(buildRequestResource(namespaceId, promptKey))
                .setHttpMethod(HttpMethod.PUT).setPath(Constants.AdminApiPath.AI_PROMPT_METADATA_ADMIN_PATH)
                .setParamValue(params).build();
        HttpRestResult<String> restResult = executeSyncHttpRequest(httpRequest);
        Result<Boolean> result = JacksonUtils.toObj(restResult.getData(), new TypeReference<Result<Boolean>>() {
        });
        return Boolean.TRUE.equals(result.getData());
    }

    private boolean updatePromptLabel(String namespaceId, String promptKey, String label, String version,
            String method) throws NacosException {
        namespaceId = resolveNamespace(namespaceId);
        Map<String, String> params = new HashMap<>(8);
        params.put("namespaceId", namespaceId);
        params.put("promptKey", promptKey);
        params.put("label", label);
        putIfNotBlank(params, "version", version);
        HttpRequest httpRequest = buildHttpRequestBuilder(buildRequestResource(namespaceId, promptKey))
                .setHttpMethod(method).setPath(Constants.AdminApiPath.AI_PROMPT_LABEL_ADMIN_PATH)
                .setParamValue(params).build();
        HttpRestResult<String> restResult = executeSyncHttpRequest(httpRequest);
        Result<Boolean> result = JacksonUtils.toObj(restResult.getData(), new TypeReference<Result<Boolean>>() {
        });
        return Boolean.TRUE.equals(result.getData());
    }
}