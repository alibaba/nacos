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

import com.alibaba.nacos.api.ai.model.mcp.McpEndpointSpec;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpToolSpecification;
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
import com.alibaba.nacos.maintainer.client.remote.ClientHttpProxy;
import com.alibaba.nacos.maintainer.client.utils.ParamUtil;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Nacos AI module maintainer service implementation.
 *
 * @author xiweng.yy
 */
public class NacosAiMaintainerServiceImpl implements AiMaintainerService {
    
    private final ClientHttpProxy clientHttpProxy;
    
    private static final String DEFAULT_NAMESPACE = "public";
    
    public NacosAiMaintainerServiceImpl(Properties properties) throws NacosException {
        this.clientHttpProxy = new ClientHttpProxy(properties);
        ParamUtil.initSerialization();
    }
    
    @Override
    public Page<McpServerBasicInfo> listMcpServer(String namespaceId, String mcpName, int pageNo, int pageSize) throws NacosException {
        Map<String, String> params = new HashMap<>(3);
        params.put("pageNo", String.valueOf(pageNo));
        params.put("pageSize", String.valueOf(pageSize));
        params.put("search", "accurate");
        params.put("mcpName", mcpName);
        params.put("namespaceId", namespaceId);
        return getMcpServerBasicInfoPage(params);
    }

    @Override
    public Page<McpServerBasicInfo> searchMcpServer(String namespaceId, String mcpName, int pageNo, int pageSize) throws NacosException {
        Map<String, String> params = new HashMap<>(4);
        params.put("pageNo", String.valueOf(pageNo));
        params.put("pageSize", String.valueOf(pageSize));
        params.put("search", "blur");
        params.put("mcpName", mcpName);
        params.put("namespaceId", namespaceId);
        return getMcpServerBasicInfoPage(params);
    }

    private Page<McpServerBasicInfo> getMcpServerBasicInfoPage(Map<String, String> params) throws NacosException {
        HttpRequest httpRequest = new HttpRequest.Builder().setHttpMethod(HttpMethod.GET)
                .setPath(Constants.AdminApiPath.AI_MCP_ADMIN_PATH + "/list").setParamValue(params).build();
        HttpRestResult<String> restResult = clientHttpProxy.executeSyncHttpRequest(httpRequest);
        Result<Page<McpServerBasicInfo>> result = JacksonUtils.toObj(restResult.getData(),
                new TypeReference<Result<Page<McpServerBasicInfo>>>() {
                });
        return result.getData();
    }
    
    @Override
    public McpServerDetailInfo getMcpServerDetail(String namespaceId, String mcpName, String version) throws NacosException {
        Map<String, String> params = new HashMap<>(1);
        params.put("mcpName", mcpName);
        params.put("version", version);
        params.put("namespaceId", namespaceId);
        HttpRequest httpRequest = new HttpRequest.Builder().setHttpMethod(HttpMethod.GET)
                .setPath(Constants.AdminApiPath.AI_MCP_ADMIN_PATH).setParamValue(params).build();
        HttpRestResult<String> restResult = clientHttpProxy.executeSyncHttpRequest(httpRequest);
        Result<McpServerDetailInfo> result = JacksonUtils.toObj(restResult.getData(),
                new TypeReference<Result<McpServerDetailInfo>>() {
                });
        return result.getData();
    }

    @Override
    public boolean createMcpServer(String namespaceId, String mcpName, McpServerBasicInfo serverSpec, McpToolSpecification toolSpec,
                                   McpEndpointSpec endpointSpec) throws NacosException {
        Map<String, String> params = buildFullParameters(serverSpec, toolSpec, endpointSpec);
        params.put("namespaceId", namespaceId);
        HttpRequest httpRequest = new HttpRequest.Builder().setHttpMethod(HttpMethod.POST)
                .setPath(Constants.AdminApiPath.AI_MCP_ADMIN_PATH).setParamValue(params).build();
        HttpRestResult<String> restResult = clientHttpProxy.executeSyncHttpRequest(httpRequest);
        Result<String> result = JacksonUtils.toObj(restResult.getData(), new TypeReference<Result<String>>() {
        });
        return ErrorCode.SUCCESS.getCode().equals(result.getCode());
    }


    @Override
    public boolean updateMcpServer(String namespaceId, String mcpName, boolean isLatest, McpServerBasicInfo serverSpec, McpToolSpecification toolSpec,
            McpEndpointSpec endpointSpec) throws NacosException {
        Map<String, String> params = buildFullParameters(serverSpec, toolSpec, endpointSpec);
        params.put("latest", String.valueOf(isLatest));
        params.put("namespaceId", namespaceId);
        HttpRequest httpRequest = new HttpRequest.Builder().setHttpMethod(HttpMethod.PUT)
                .setPath(Constants.AdminApiPath.AI_MCP_ADMIN_PATH).setParamValue(params).build();
        HttpRestResult<String> restResult = clientHttpProxy.executeSyncHttpRequest(httpRequest);
        Result<String> result = JacksonUtils.toObj(restResult.getData(), new TypeReference<Result<String>>() {
        });
        return ErrorCode.SUCCESS.getCode().equals(result.getCode());
    }

    private Map<String, String> buildFullParameters(McpServerBasicInfo serverSpec,
            McpToolSpecification toolSpec, McpEndpointSpec endpointSpec) {
        Map<String, String> params = new HashMap<>(4);
        params.put("mcpName", serverSpec.getName());
        params.put("serverSpecification", JacksonUtils.toJson(serverSpec));
        if (null != toolSpec) {
            params.put("toolSpecification", JacksonUtils.toJson(toolSpec));
        }
        if (null != endpointSpec) {
            params.put("endpointSpecification", JacksonUtils.toJson(endpointSpec));
        }
        return params;
    }
    
    @Override
    public boolean deleteMcpServer(String namespaceId, String mcpName) throws NacosException {
        Map<String, String> params = new HashMap<>(1);
        params.put("mcpName", mcpName);
        params.put("namespaceId", namespaceId);
        HttpRequest httpRequest = new HttpRequest.Builder().setHttpMethod(HttpMethod.DELETE)
                .setPath(Constants.AdminApiPath.AI_MCP_ADMIN_PATH).setParamValue(params).build();
        HttpRestResult<String> restResult = clientHttpProxy.executeSyncHttpRequest(httpRequest);
        Result<String> result = JacksonUtils.toObj(restResult.getData(), new TypeReference<Result<String>>() {
        });
        return ErrorCode.SUCCESS.getCode().equals(result.getCode());
    }
}
