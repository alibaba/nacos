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

package com.alibaba.nacos.ai.remote.handler;

import com.alibaba.nacos.ai.index.McpServerIndex;
import com.alibaba.nacos.ai.model.mcp.McpServerIndexData;
import com.alibaba.nacos.ai.service.McpEndpointOperationService;
import com.alibaba.nacos.ai.service.McpServerOperationService;
import com.alibaba.nacos.ai.utils.McpRequestUtil;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.mcp.McpEndpointSpec;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpToolSpecification;
import com.alibaba.nacos.api.ai.remote.request.ReleaseMcpServerRequest;
import com.alibaba.nacos.api.ai.remote.response.ReleaseMcpServerResponse;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.naming.CommonParams;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.namespace.filter.NamespaceValidation;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.core.paramcheck.impl.McpServerRequestParamExtractor;
import com.alibaba.nacos.core.remote.RequestHandler;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Nacos AI module release new mcp server or new version of exist mcp server request handler.
 *
 * @author xiweng.yy
 */
@Component
public class ReleaseMcpServerRequestHandler extends RequestHandler<ReleaseMcpServerRequest, ReleaseMcpServerResponse> {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ReleaseMcpServerRequest.class);
    
    private final McpServerOperationService mcpServerOperationService;
    
    private final McpEndpointOperationService endpointOperationService;
    
    private final McpServerIndex mcpServerIndex;
    
    public ReleaseMcpServerRequestHandler(McpServerOperationService mcpServerOperationService,
            McpEndpointOperationService endpointOperationService, McpServerIndex mcpServerIndex) {
        this.mcpServerOperationService = mcpServerOperationService;
        this.endpointOperationService = endpointOperationService;
        this.mcpServerIndex = mcpServerIndex;
    }
    
    @Override
    @NamespaceValidation
    @ExtractorManager.Extractor(rpcExtractor = McpServerRequestParamExtractor.class)
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI)
    public ReleaseMcpServerResponse handle(ReleaseMcpServerRequest request, RequestMeta meta) throws NacosException {
        McpRequestUtil.fillNamespaceId(request);
        try {
            checkParameters(request);
            return doHandler(request, meta);
        } catch (NacosException e) {
            ReleaseMcpServerResponse response = new ReleaseMcpServerResponse();
            response.setErrorInfo(e.getErrCode(), e.getErrMsg());
            return response;
        }
    }
    
    private void checkParameters(ReleaseMcpServerRequest request) throws NacosException {
        McpServerBasicInfo serverSpecification = request.getServerSpecification();
        if (null == serverSpecification) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "Required parameter 'serverSpecification' type McpServerBasicInfo is not present");
        }
        String mcpName = serverSpecification.getName();
        if (StringUtils.isEmpty(mcpName)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "Required parameter 'serverSpecification.name' type String is not present");
        }
        if (null == serverSpecification.getVersionDetail() || StringUtils.isBlank(
                serverSpecification.getVersionDetail().getVersion())) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "Required parameter `serverSpecification.versionDetail.version` not present");
        }
    }
    
    private ReleaseMcpServerResponse doHandler(ReleaseMcpServerRequest request, RequestMeta meta)
            throws NacosException {
        String namespaceId = request.getNamespaceId();
        McpServerBasicInfo serverSpecification = request.getServerSpecification();
        LOGGER.info("Release new mcp server {}, version {} into namespaceId {} from connectionId {}.",
                serverSpecification.getName(), serverSpecification.getVersionDetail().getVersion(), namespaceId,
                meta.getConnectionId());
        ReleaseMcpServerResponse response = new ReleaseMcpServerResponse();
        try {
            // mcp server and version found, means this version of mcp server has been release, throw exception.
            McpServerBasicInfo existMcpServer = mcpServerOperationService.getMcpServerDetail(namespaceId,
                    serverSpecification.getId(), serverSpecification.getName(),
                    serverSpecification.getVersionDetail().getVersion());
            String version = existMcpServer.getVersionDetail().getVersion();
            LOGGER.info("Mcp Server {} and target version {} already exist.", existMcpServer.getName(), version);
            throw new NacosApiException(NacosException.CONFLICT, ErrorCode.MCP_SERVER_VERSION_EXIST,
                    String.format("Mcp Server %s and target version %s already exist, do not do release",
                            existMcpServer.getName(), version));
        } catch (NacosApiException e) {
            if (ErrorCode.MCP_SERVER_NOT_FOUND.getCode() == e.getDetailErrCode()) {
                // mcp server not found, create new mcp server.
                String mcpId = createNewMcpServer(namespaceId, request);
                response.setMcpId(mcpId);
                LOGGER.info("Mcp Server {} released, Mcp Server id: {}", serverSpecification.getName(), mcpId);
            } else if (ErrorCode.MCP_SEVER_VERSION_NOT_FOUND.getCode() == e.getDetailErrCode()) {
                // mcp server found but version not found, update mcp server.
                createNewVersionMcpServer(namespaceId, request);
                McpServerIndexData mcpServerIndexData = mcpServerIndex.getMcpServerByName(namespaceId,
                        serverSpecification.getName());
                response.setMcpId(mcpServerIndexData.getId());
                LOGGER.info("Mcp Server {} new version {} released, Mcp Server id: {}", serverSpecification.getName(),
                        serverSpecification.getVersionDetail().getVersion(), mcpServerIndexData.getId());
            } else {
                LOGGER.error("Mcp Server {} released failed.", serverSpecification.getName(), e);
                throw e;
            }
        }
        return response;
    }
    
    private String createNewMcpServer(String namespaceId, ReleaseMcpServerRequest request) throws NacosException {
        McpServerBasicInfo mcpServerBasicInfo = request.getServerSpecification();
        McpToolSpecification toolSpecification = request.getToolSpecification();
        McpEndpointSpec endpointSpecification =
                null == request.getEndpointSpecification() ? autoBuildMcpEndpointSpecification(namespaceId,
                        mcpServerBasicInfo) : request.getEndpointSpecification();
        return mcpServerOperationService.createMcpServer(namespaceId, mcpServerBasicInfo, toolSpecification,
                endpointSpecification);
    }
    
    private void createNewVersionMcpServer(String namespaceId, ReleaseMcpServerRequest request) throws NacosException {
        McpServerBasicInfo mcpServerBasicInfo = request.getServerSpecification();
        McpToolSpecification toolSpecification = request.getToolSpecification();
        McpEndpointSpec endpointSpecification =
                null == request.getEndpointSpecification() ? autoBuildMcpEndpointSpecification(namespaceId,
                        mcpServerBasicInfo) : request.getEndpointSpecification();
        Boolean isLatest = mcpServerBasicInfo.getVersionDetail().getIs_latest();
        boolean isPublish = isLatest != null && isLatest;
        mcpServerOperationService.updateMcpServer(namespaceId, isPublish, mcpServerBasicInfo, toolSpecification,
                endpointSpecification, Boolean.FALSE);
        
    }
    
    private McpEndpointSpec autoBuildMcpEndpointSpecification(String namespaceId,
            McpServerBasicInfo mcpServerBasicInfo) {
        if (AiConstants.Mcp.MCP_PROTOCOL_STDIO.equals(mcpServerBasicInfo.getProtocol())) {
            return null;
        }
        // Not stdio protocol need to create endpoint service.
        return autoBuildMcpEndpointSpecification(namespaceId, mcpServerBasicInfo.getName(),
                mcpServerBasicInfo.getVersionDetail().getVersion());
    }
    
    private McpEndpointSpec autoBuildMcpEndpointSpecification(String namespaceId, String mcpName, String version) {
        String versionMcpName = mcpName + "::" + version;
        Service service = endpointOperationService.generateService(namespaceId, versionMcpName);
        McpEndpointSpec endpointSpecification = new McpEndpointSpec();
        endpointSpecification.setType(AiConstants.Mcp.MCP_ENDPOINT_TYPE_REF);
        endpointSpecification.getData().put(CommonParams.NAMESPACE_ID, service.getNamespace());
        endpointSpecification.getData().put(CommonParams.GROUP_NAME, service.getGroup());
        endpointSpecification.getData().put(CommonParams.SERVICE_NAME, service.getName());
        return endpointSpecification;
    }
}
