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

final class PipelineMaintainerServiceImpl extends AbstractAiDelegateMaintainerService
    implements PipelineMaintainerService {
    
    PipelineMaintainerServiceImpl(AiMaintainerHttpContext context) {
        super(context);
    }
    
    @Override
    public Result<JsonNode> getPipelineDetail(String pipelineId) throws NacosException {
        Map<String, String> params = new HashMap<>(2);
        params.put("pipelineId", pipelineId);
        HttpRequest httpRequest =
            buildHttpRequestBuilder(buildRequestResource(StringUtils.EMPTY, pipelineId))
                .setHttpMethod(HttpMethod.GET)
                .setPath(Constants.AdminApiPath.AI_PIPELINE_DETAIL_ADMIN_PATH)
                .setParamValue(params).build();
        try {
            return parseResultFromHttp(executeSyncHttpRequest(httpRequest));
        } catch (NacosException e) {
            if (e.getErrCode() == NacosException.NOT_FOUND) {
                return getPipelineDetailLegacy(pipelineId);
            }
            throw e;
        }
    }
    
    /**
     * Pre-3.2.1 style: GET {@code /v3/admin/ai/pipelines/{pipelineId}} when {@code /detail} is not mapped.
     */
    private Result<JsonNode> getPipelineDetailLegacy(String pipelineId) throws NacosException {
        HttpRequest httpRequest =
            buildHttpRequestBuilder(buildRequestResource(StringUtils.EMPTY, pipelineId))
                .setHttpMethod(HttpMethod.GET)
                .setPath(Constants.AdminApiPath.AI_PIPELINE_ADMIN_PATH + "/" + pipelineId)
                .setParamValue(new HashMap<>(2)).build();
        return parseResultFromHttp(executeSyncHttpRequest(httpRequest));
    }
    
    @Override
    public Result<JsonNode> listPipelineExecutions(String resourceType, String resourceName,
        String namespaceId,
        String version, int pageNo, int pageSize) throws NacosException {
        Map<String, String> params =
            buildListQueryParams(resourceType, resourceName, namespaceId, version, pageNo,
                pageSize);
        String resolvedNamespace =
            StringUtils.isNotBlank(namespaceId) ? namespaceId : StringUtils.EMPTY;
        HttpRequest httpRequest =
            buildHttpRequestBuilder(buildRequestResource(resolvedNamespace, resourceName))
                .setHttpMethod(HttpMethod.GET)
                .setPath(Constants.AdminApiPath.AI_PIPELINE_LIST_ADMIN_PATH)
                .setParamValue(params).build();
        try {
            return parseResultFromHttp(executeSyncHttpRequest(httpRequest));
        } catch (NacosException e) {
            if (e.getErrCode() == NacosException.NOT_FOUND) {
                return listPipelineExecutionsLegacy(resolvedNamespace, resourceName, params);
            }
            throw e;
        }
    }
    
    /**
     * Pre-3.2.1 style: GET {@code /v3/admin/ai/pipelines} when {@code /list} is not mapped.
     */
    private Result<JsonNode> listPipelineExecutionsLegacy(String resolvedNamespace,
        String resourceName,
        Map<String, String> params) throws NacosException {
        HttpRequest httpRequest =
            buildHttpRequestBuilder(buildRequestResource(resolvedNamespace, resourceName))
                .setHttpMethod(HttpMethod.GET)
                .setPath(Constants.AdminApiPath.AI_PIPELINE_ADMIN_PATH)
                .setParamValue(params).build();
        return parseResultFromHttp(executeSyncHttpRequest(httpRequest));
    }
    
    @Override
    @Deprecated
    public JsonNode getPipeline(String pipelineId) throws NacosException {
        return unwrapSuccessData(getPipelineDetail(pipelineId));
    }
    
    @Override
    @Deprecated
    public JsonNode listPipelines(String resourceType, String resourceName, String namespaceId,
        String version,
        int pageNo, int pageSize) throws NacosException {
        return unwrapSuccessData(
            listPipelineExecutions(resourceType, resourceName, namespaceId, version, pageNo,
                pageSize));
    }
    
    private Map<String, String> buildListQueryParams(String resourceType, String resourceName,
        String namespaceId,
        String version, int pageNo, int pageSize) {
        Map<String, String> params = new HashMap<>(8);
        params.put("resourceType", resourceType);
        putIfNotBlank(params, "resourceName", resourceName);
        putIfNotBlank(params, "namespaceId", namespaceId);
        putIfNotBlank(params, "version", version);
        params.put("pageNo", String.valueOf(pageNo));
        params.put("pageSize", String.valueOf(pageSize));
        return params;
    }
    
    private static Result<JsonNode> parseResultFromHttp(HttpRestResult<String> restResult)
        throws NacosException {
        String body = restResult.getData();
        if (StringUtils.isBlank(body)) {
            throw new NacosException(NacosException.SERVER_ERROR, "empty response body");
        }
        try {
            Result<JsonNode> result =
                JacksonUtils.toObj(body, new TypeReference<Result<JsonNode>>() {
                });
            if (result == null) {
                throw new NacosException(NacosException.SERVER_ERROR,
                    "failed to parse Result wrapper");
            }
            return result;
        } catch (Exception e) {
            throw new NacosException(NacosException.SERVER_ERROR,
                "failed to parse Result: " + e.getMessage(), e);
        }
    }
    
    private static JsonNode unwrapSuccessData(Result<JsonNode> result) throws NacosException {
        if (result == null) {
            throw new NacosException(NacosException.SERVER_ERROR, "empty Result");
        }
        Integer code = result.getCode();
        if (isSuccessCode(code)) {
            return result.getData();
        }
        String msg =
            StringUtils.isNotBlank(result.getMessage()) ? result.getMessage() : "request failed";
        throw new NacosException(NacosException.SERVER_ERROR, msg);
    }
    
    private static boolean isSuccessCode(Integer code) {
        if (code == null) {
            return false;
        }
        return ErrorCode.SUCCESS.getCode().equals(code) || Integer.valueOf(200).equals(code);
    }
}
