/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.ai.service.mcp;

import com.alibaba.nacos.ai.service.McpEndpointOperationService;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.mcp.FrontEndpointConfig;
import com.alibaba.nacos.api.ai.model.mcp.McpEndpointSpec;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServiceRef;
import com.alibaba.nacos.api.ai.remote.AiRemoteConstants;
import com.alibaba.nacos.api.ai.remote.request.McpServerEndpointRequest;
import com.alibaba.nacos.api.ai.remote.request.ReleaseMcpServerRequest;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.naming.CommonParams;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.trace.DeregisterInstanceReason;
import com.alibaba.nacos.common.trace.event.naming.DeregisterInstanceTraceEvent;
import com.alibaba.nacos.common.trace.event.naming.RegisterInstanceTraceEvent;
import com.alibaba.nacos.common.utils.InternetAddressUtil;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.naming.core.v2.service.impl.EphemeralClientOperationServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Transport-neutral application service for MCP Client query, release, and Runtime Endpoint
 * operations.
 *
 * @author Nacos
 */
@Service
public class McpClientApplicationService {
    
    private static final Logger LOGGER =
        LoggerFactory.getLogger(McpClientApplicationService.class);
    
    private static final String VERSION_TAG = "_mcp_server_version";
    
    private final McpCompatibilityOperationService operationService;
    
    private final McpEndpointOperationService endpointOperationService;
    
    private final EphemeralClientOperationServiceImpl clientOperationService;
    
    public McpClientApplicationService(McpCompatibilityOperationService operationService,
        McpEndpointOperationService endpointOperationService,
        EphemeralClientOperationServiceImpl clientOperationService) {
        this.operationService = operationService;
        this.endpointOperationService = endpointOperationService;
        this.clientOperationService = clientOperationService;
    }
    
    /**
     * Query one exact or latest serving MCP Version.
     *
     * @param namespaceId namespace identifier
     * @param mcpName MCP name
     * @param version optional exact Version
     * @return MCP detail
     * @throws NacosException when the MCP Server is absent or unreadable
     */
    public McpServerDetailInfo query(String namespaceId, String mcpName, String version)
        throws NacosException {
        if (StringUtils.isBlank(mcpName)) {
            throw missing("parameters `mcpName` can't be empty or null");
        }
        McpServerDetailInfo result;
        try {
            result = operationService.getServingMcpServerDetail(namespaceId, mcpName, version);
        } catch (NacosException e) {
            if (NacosException.NOT_FOUND == e.getErrCode()) {
                throw notFound(mcpName, namespaceId);
            }
            throw e;
        }
        if (result == null) {
            throw notFound(mcpName, namespaceId);
        }
        return result;
    }
    
    /**
     * Perform historical direct-online release or create a standard lifecycle draft.
     *
     * @param request complete release request
     * @param publisher diagnostic publisher identity
     * @return stable internal MCP id
     * @throws NacosException when validation or persistence fails
     */
    public String release(ReleaseMcpServerRequest request, String publisher)
        throws NacosException {
        validateRelease(request);
        String namespaceId = request.getNamespaceId();
        McpServerBasicInfo server = request.getServerSpecification();
        LOGGER.info("Release MCP Server {}, Version {} into namespaceId {} from {}.",
            server.getName(), server.getVersionDetail().getVersion(), namespaceId, publisher);
        if (request.isCreateDraft()) {
            McpEndpointSpec endpoint = resolveEndpoint(namespaceId, request);
            operationService.createMcpServerDraft(namespaceId, server,
                request.getToolSpecification(), request.getResourceSpecification(), endpoint);
            return server.getId();
        }
        return releaseDirectOnline(namespaceId, request);
    }
    
    /**
     * Register or deregister one MCP Runtime Endpoint under a publisher Client.
     *
     * @param request Endpoint request
     * @param publisherId Naming publisher Client id
     * @param sourceIp request source IP used by tracing
     * @throws NacosException when validation or Naming mutation fails
     */
    public void operateEndpoint(McpServerEndpointRequest request, String publisherId,
        String sourceIp) throws NacosException {
        validateEndpoint(request);
        Instance instance = buildInstance(request);
        McpServerDetailInfo mcpServer = query(request.getNamespaceId(), request.getMcpName(),
            request.getVersion());
        McpServiceRef serviceRef = buildServiceRef(mcpServer);
        if (serviceRef == null) {
            throw new NacosApiException(NacosException.NOT_FOUND,
                ErrorCode.MCP_SERVER_REF_ENDPOINT_SERVICE_NOT_FOUND,
                "The Mcp Server Ref endpoint service not found.");
        }
        com.alibaba.nacos.naming.core.v2.pojo.Service service =
            com.alibaba.nacos.naming.core.v2.pojo.Service.newService(request.getNamespaceId(),
                serviceRef.getGroupName(), serviceRef.getServiceName(), true);
        if (AiRemoteConstants.REGISTER_ENDPOINT.equals(request.getType())) {
            clientOperationService.registerInstance(service, instance, publisherId);
            NotifyCenter.publishEvent(new RegisterInstanceTraceEvent(System.currentTimeMillis(),
                sourceIp, true, service.getNamespace(), service.getGroup(), service.getName(),
                instance.getIp(), instance.getPort()));
            return;
        }
        if (AiRemoteConstants.DE_REGISTER_ENDPOINT.equals(request.getType())) {
            clientOperationService.deregisterInstance(service, instance, publisherId);
            NotifyCenter.publishEvent(new DeregisterInstanceTraceEvent(System.currentTimeMillis(),
                sourceIp, true, DeregisterInstanceReason.REQUEST, service.getNamespace(),
                service.getGroup(), service.getName(), instance.getIp(), instance.getPort()));
            return;
        }
        throw new NacosApiException(NacosException.INVALID_PARAM,
            ErrorCode.PARAMETER_VALIDATE_ERROR,
            String.format("parameter `type` should be %s or %s, but was %s",
                AiRemoteConstants.REGISTER_ENDPOINT, AiRemoteConstants.DE_REGISTER_ENDPOINT,
                request.getType()));
    }
    
    private String releaseDirectOnline(String namespaceId, ReleaseMcpServerRequest request)
        throws NacosException {
        McpServerBasicInfo server = request.getServerSpecification();
        try {
            McpServerBasicInfo existing = operationService.getMcpServerDetail(namespaceId,
                server.getId(), server.getName(), server.getVersionDetail().getVersion());
            throw new NacosApiException(NacosException.CONFLICT,
                ErrorCode.MCP_SERVER_VERSION_EXIST,
                String.format(
                    "Mcp Server %s and target version %s already exist, do not do release",
                    existing.getName(), existing.getVersionDetail().getVersion()));
        } catch (NacosApiException e) {
            if (ErrorCode.MCP_SERVER_NOT_FOUND.getCode() == e.getDetailErrCode()) {
                McpEndpointSpec endpoint = resolveEndpoint(namespaceId, request);
                return operationService.createMcpServer(namespaceId, server,
                    request.getToolSpecification(), request.getResourceSpecification(), endpoint);
            }
            if (ErrorCode.MCP_SEVER_VERSION_NOT_FOUND.getCode() == e.getDetailErrCode()) {
                Boolean latest = server.getVersionDetail().getIs_latest();
                McpEndpointSpec endpoint = resolveEndpoint(namespaceId, request);
                operationService.updateMcpServer(namespaceId, latest != null && latest, server,
                    request.getToolSpecification(), request.getResourceSpecification(), endpoint,
                    false);
                return server.getId();
            }
            throw e;
        }
    }
    
    private McpEndpointSpec resolveEndpoint(String namespaceId,
        ReleaseMcpServerRequest request) {
        return request.getEndpointSpecification() == null
            ? autoBuildMcpEndpointSpecification(namespaceId, request.getServerSpecification())
            : request.getEndpointSpecification();
    }
    
    private void validateRelease(ReleaseMcpServerRequest request) throws NacosApiException {
        McpServerBasicInfo server = request.getServerSpecification();
        if (server == null) {
            throw missing(
                "Required parameter 'serverSpecification' type McpServerBasicInfo is not present");
        }
        if (StringUtils.isEmpty(server.getName())) {
            throw missing(
                "Required parameter 'serverSpecification.name' type String is not present");
        }
        if (server.getVersionDetail() == null
            || StringUtils.isBlank(server.getVersionDetail().getVersion())) {
            throw missing(
                "Required parameter `serverSpecification.versionDetail.version` not present");
        }
    }
    
    private void validateEndpoint(McpServerEndpointRequest request) throws NacosApiException {
        if (StringUtils.isBlank(request.getMcpName())) {
            throw missing("parameters `mcpName` can't be empty or null");
        }
        if (!InternetAddressUtil.isIp(request.getAddress())) {
            throw new NacosApiException(NacosException.INVALID_PARAM,
                ErrorCode.PARAMETER_VALIDATE_ERROR,
                "parameter `address` should be a valid IPv4 or IPv6 address");
        }
        if (request.getPort() <= 0 || request.getPort() > 65535) {
            throw new NacosApiException(NacosException.INVALID_PARAM,
                ErrorCode.PARAMETER_VALIDATE_ERROR,
                "parameter `port` should be in the range 1 ~ 65535");
        }
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
    
    private McpServiceRef buildServiceRef(McpServerDetailInfo mcpServer) {
        if (mcpServer.getRemoteServerConfig() == null) {
            return null;
        }
        if (!AiConstants.Mcp.MCP_PROTOCOL_HTTP.equals(mcpServer.getProtocol())) {
            return mcpServer.getRemoteServerConfig().getServiceRef();
        }
        if (mcpServer.getRemoteServerConfig().getFrontEndpointConfigList() == null) {
            return null;
        }
        for (FrontEndpointConfig endpoint : mcpServer.getRemoteServerConfig()
            .getFrontEndpointConfigList()) {
            if (AiConstants.Mcp.MCP_ENDPOINT_TYPE_REF.equals(endpoint.getEndpointType())) {
                return com.alibaba.nacos.ai.utils.McpRequestUtil.transferToMcpServiceRef(
                    endpoint.getEndpointData());
            }
        }
        return null;
    }
    
    private McpEndpointSpec autoBuildMcpEndpointSpecification(String namespaceId,
        McpServerBasicInfo server) {
        if (AiConstants.Mcp.MCP_PROTOCOL_STDIO.equals(server.getProtocol())) {
            return null;
        }
        String versionMcpName = server.getName() + "::" + server.getVersionDetail().getVersion();
        com.alibaba.nacos.naming.core.v2.pojo.Service service =
            endpointOperationService.generateService(namespaceId, versionMcpName);
        McpEndpointSpec result = new McpEndpointSpec();
        result.setType(AiConstants.Mcp.MCP_ENDPOINT_TYPE_REF);
        result.getData().put(CommonParams.NAMESPACE_ID, service.getNamespace());
        result.getData().put(CommonParams.GROUP_NAME, service.getGroup());
        result.getData().put(CommonParams.SERVICE_NAME, service.getName());
        return result;
    }
    
    private NacosApiException missing(String message) {
        return new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
            message);
    }
    
    private NacosApiException notFound(String mcpName, String namespaceId) {
        return new NacosApiException(NacosException.NOT_FOUND, ErrorCode.MCP_SERVER_NOT_FOUND,
            String.format("MCP server `%s` not found in namespaceId: `%s`", mcpName,
                namespaceId));
    }
}
