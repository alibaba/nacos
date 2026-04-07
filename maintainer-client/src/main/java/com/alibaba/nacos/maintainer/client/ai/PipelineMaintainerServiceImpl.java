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

import com.alibaba.nacos.api.exception.NacosException;
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

final class PipelineMaintainerServiceImpl extends AbstractAiDelegateMaintainerService implements PipelineMaintainerService {

    PipelineMaintainerServiceImpl(AiMaintainerHttpContext context) {
        super(context);
    }

    @Override
    public JsonNode getPipeline(String pipelineId) throws NacosException {
        HttpRequest httpRequest = buildHttpRequestBuilder(buildRequestResource(StringUtils.EMPTY, pipelineId))
                .setHttpMethod(HttpMethod.GET)
                .setPath(Constants.AdminApiPath.AI_PIPELINE_ADMIN_PATH + "/" + pipelineId)
                .setParamValue(new HashMap<>(2)).build();
        HttpRestResult<String> restResult = executeSyncHttpRequest(httpRequest);
        Result<JsonNode> result = JacksonUtils.toObj(restResult.getData(), new TypeReference<Result<JsonNode>>() {
        });
        return result.getData();
    }

    @Override
    public JsonNode listPipelines(String resourceType, String resourceName, String namespaceId, String version,
            int pageNo, int pageSize) throws NacosException {
        Map<String, String> params = new HashMap<>(8);
        params.put("resourceType", resourceType);
        putIfNotBlank(params, "resourceName", resourceName);
        putIfNotBlank(params, "namespaceId", namespaceId);
        putIfNotBlank(params, "version", version);
        params.put("pageNo", String.valueOf(pageNo));
        params.put("pageSize", String.valueOf(pageSize));
        String resolvedNamespace = StringUtils.isNotBlank(namespaceId) ? namespaceId : StringUtils.EMPTY;
        HttpRequest httpRequest = buildHttpRequestBuilder(buildRequestResource(resolvedNamespace, resourceName))
                .setHttpMethod(HttpMethod.GET).setPath(Constants.AdminApiPath.AI_PIPELINE_ADMIN_PATH)
                .setParamValue(params).build();
        HttpRestResult<String> restResult = executeSyncHttpRequest(httpRequest);
        Result<JsonNode> result = JacksonUtils.toObj(restResult.getData(), new TypeReference<Result<JsonNode>>() {
        });
        return result.getData();
    }
}