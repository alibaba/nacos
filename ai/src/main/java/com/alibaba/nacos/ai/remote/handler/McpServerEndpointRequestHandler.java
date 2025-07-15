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
import com.alibaba.nacos.ai.service.McpServerOperationService;
import com.alibaba.nacos.ai.utils.McpRequestUtils;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServiceRef;
import com.alibaba.nacos.api.ai.remote.AiRemoteConstants;
import com.alibaba.nacos.api.ai.remote.request.McpServerEndpointRequest;
import com.alibaba.nacos.api.ai.remote.response.McpServerEndpointResponse;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.core.paramcheck.impl.McpServerRequestParamExtractor;
import com.alibaba.nacos.core.remote.RequestHandler;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.core.v2.service.impl.EphemeralClientOperationServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Register or Deregister endpoint for mcp server to nacos AI module request handler.
 *
 * @author xiweng.yy
 */
@Component
public class McpServerEndpointRequestHandler
        extends RequestHandler<McpServerEndpointRequest, McpServerEndpointResponse> {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(McpServerEndpointRequestHandler.class);
    
    private static final String VERSION_TAG = "_mcp_server_version";
    
    private final EphemeralClientOperationServiceImpl clientOperationService;
    
    private final McpServerOperationService mcpServerOperationService;
    
    private final McpServerIndex mcpServerIndex;
    
    public McpServerEndpointRequestHandler(EphemeralClientOperationServiceImpl clientOperationService,
            McpServerOperationService mcpServerOperationService, McpServerIndex mcpServerIndex) {
        this.clientOperationService = clientOperationService;
        this.mcpServerOperationService = mcpServerOperationService;
        this.mcpServerIndex = mcpServerIndex;
    }
    
    @Override
    @ExtractorManager.Extractor(rpcExtractor = McpServerRequestParamExtractor.class)
    public McpServerEndpointResponse handle(McpServerEndpointRequest request, RequestMeta meta) throws NacosException {
        McpRequestUtils.fillNamespaceId(request);
        checkParameters(request);
        Instance instance = buildInstance(request);
        return doHandler(request, instance, meta);
    }
    
    private void checkParameters(McpServerEndpointRequest request) throws NacosApiException {
        if (StringUtils.isBlank(request.getMcpName())) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "parameters `mcpName` can't be empty or null");
        }
    }
    
    private McpServerEndpointResponse doHandler(McpServerEndpointRequest request, Instance instance, RequestMeta meta)
            throws NacosException {
        McpServerIndexData indexData = mcpServerIndex.getMcpServerByName(request.getNamespaceId(), request.getMcpName());
        if (null == indexData) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.MCP_SERVER_NOT_FOUND,
                    String.format("MCP server `%s` not found in namespaceId: `%s`", request.getMcpName(),
                            request.getNamespaceId()));
        }
        McpServerDetailInfo mcpServer = mcpServerOperationService.getMcpServerDetail(request.getNamespaceId(),
                indexData.getId(), null, request.getVersion());
        McpServiceRef serviceRef = mcpServer.getRemoteServerConfig().getServiceRef();
        Service service = Service.newService(request.getNamespaceId(), serviceRef.getGroupName(),
                serviceRef.getServiceName(), true);
        switch (request.getType()) {
            case AiRemoteConstants.REGISTER_ENDPOINT:
                LOGGER.info("[{}] register endpoint {}:{} version {} for mcp server: {}", meta.getConnectionId(),
                        request.getAddress(), request.getPort(), request.getVersion(), request.getMcpName());
                doRegister(service, instance, meta.getConnectionId());
                break;
            case AiRemoteConstants.DE_REGISTER_ENDPOINT:
                LOGGER.info("[{}] de-register endpoint {}:{} version {} for mcp server: {}", meta.getConnectionId(),
                        request.getAddress(), request.getPort(), request.getVersion(), request.getMcpName());
                doDeregister(service, instance, meta.getConnectionId());
                break;
            default:
                throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_VALIDATE_ERROR,
                        String.format("parameter `type` should be %s or %s, but was %s",
                                AiRemoteConstants.REGISTER_ENDPOINT, AiRemoteConstants.DE_REGISTER_ENDPOINT,
                                request.getType()));
        }
        McpServerEndpointResponse response = new McpServerEndpointResponse();
        response.setType(response.getType());
        return response;
    }
    
    private Instance buildInstance(McpServerEndpointRequest request) throws NacosApiException {
        Instance instance = new Instance();
        instance.setIp(request.getAddress());
        instance.setPort(request.getPort());
        instance.validate();
        if (StringUtils.isNotBlank(request.getVersion())) {
            instance.getMetadata().put(VERSION_TAG, request.getVersion());
        }
        return instance;
    }
    
    private void doRegister(Service service, Instance instance, String connectionId) throws NacosException {
        clientOperationService.registerInstance(service, instance, connectionId);
    }
    
    private void doDeregister(Service service, Instance instance, String connectionId) {
        clientOperationService.deregisterInstance(service, instance, connectionId);
    }
}
