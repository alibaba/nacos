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

package com.alibaba.nacos.ai.service;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.index.McpServerIndex;
import com.alibaba.nacos.ai.model.mcp.McpServerIndexData;
import com.alibaba.nacos.ai.model.mcp.McpServerStorageInfo;
import com.alibaba.nacos.ai.utils.McpConfigUtils;
import com.alibaba.nacos.ai.utils.McpRequestUtil;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.mcp.FrontEndpointConfig;
import com.alibaba.nacos.api.ai.model.mcp.McpCapability;
import com.alibaba.nacos.api.ai.model.mcp.McpEndpointInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpEndpointSpec;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerVersionInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServiceRef;
import com.alibaba.nacos.api.ai.model.mcp.McpToolSpecification;
import com.alibaba.nacos.api.ai.model.mcp.registry.KeyValueInput;
import com.alibaba.nacos.api.ai.model.mcp.registry.ServerVersionDetail;
import com.alibaba.nacos.api.config.ConfigType;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.common.utils.InternetAddressUtil;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.model.ConfigRequestInfo;
import com.alibaba.nacos.config.server.model.form.ConfigForm;
import com.alibaba.nacos.config.server.model.form.ConfigFormV3;
import com.alibaba.nacos.config.server.service.ConfigOperationService;
import com.alibaba.nacos.config.server.service.query.ConfigQueryChainService;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainRequest;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainResponse;
import com.alibaba.nacos.naming.core.v2.pojo.Service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.alibaba.nacos.ai.constant.Constants.MCP_SERVER_CONFIG_MARK;
import static com.alibaba.nacos.ai.utils.McpConfigUtils.buildMcpServerVersionConfigTags;

/**
 * Nacos AI MCP server operation service. Currently, mcp server is present by there configs: 1. mcp server version info
 * {@link McpServerVersionInfo} 2. mcp server description for specified version {@link McpServerDetailInfo} 3. mcp tools
 * info {@link McpToolSpecification} when create the mcp server, we will tag the {@link McpServerVersionInfo} with mcp
 * servername for name fuzzy search.
 *
 * @author xiweng.yy
 */
@org.springframework.stereotype.Service
public class McpServerOperationService {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(McpServerOperationService.class);
    
    private final ConfigQueryChainService configQueryChainService;
    
    private final ConfigOperationService configOperationService;
    
    private final McpToolOperationService toolOperationService;
    
    private final McpEndpointOperationService endpointOperationService;
    
    private final McpServerIndex mcpServerIndex;
    
    private final McpServerSyncEffectService syncEffectService;
    
    public McpServerOperationService(ConfigQueryChainService configQueryChainService,
            ConfigOperationService configOperationService, McpToolOperationService toolOperationService,
            McpEndpointOperationService endpointOperationService, McpServerIndex mcpServerIndex,
            McpServerSyncEffectService syncEffectService) {
        this.configQueryChainService = configQueryChainService;
        this.configOperationService = configOperationService;
        this.toolOperationService = toolOperationService;
        this.endpointOperationService = endpointOperationService;
        this.mcpServerIndex = mcpServerIndex;
        this.syncEffectService = syncEffectService;
    }
    
    /**
     * List mcp server.
     *
     * @param namespaceId namespace id of mcp servers
     * @param mcpName     mcp name pattern, if null or empty, filter all mcp servers.
     * @param search      search type `blur` or `accurate`, means whether to search by fuzzy or exact match by
     *                    `mcpName`.
     * @param pageNo      page number, start from 1
     * @param pageSize    page size each page
     * @return list of {@link McpServerBasicInfo} matched input parameters.
     */
    public Page<McpServerBasicInfo> listMcpServerWithPage(String namespaceId, String mcpName, String search, int pageNo,
            int pageSize) {
        int offset = pageSize * (pageNo - 1);
        Page<McpServerIndexData> indexData = mcpServerIndex.searchMcpServerByName(namespaceId, mcpName, search, offset,
                pageSize);
        return mapIndexToBasicServerInfo(indexData);
    }
    
    private Page<McpServerBasicInfo> mapIndexToBasicServerInfo(Page<McpServerIndexData> indexData) {
        Page<McpServerBasicInfo> result = new Page<>();
        result.setTotalCount(indexData.getTotalCount());
        result.setPageNumber(indexData.getPageNumber());
        result.setPagesAvailable(indexData.getPagesAvailable());
        
        List<McpServerBasicInfo> finalResult = Collections.emptyList();
        
        if (CollectionUtils.isNotEmpty(indexData.getPageItems())) {
            finalResult = indexData.getPageItems().stream()
                    .map((index) -> buildQueryMcpServerVersionInfoRequest(index.getNamespaceId(), index.getId()))
                    .map(configQueryChainService::handle).map(ConfigQueryChainResponse::getContent)
                    .map(this::transferToMcpServerVersionInfo).collect(Collectors.toList());
        }
        result.setPageItems(finalResult);
        return result;
    }
    
    /**
     * List mcp server.
     *
     * @param namespaceId namespace id of mcp servers
     * @param mcpName     mcp name pattern, if null or empty, filter all mcp servers.
     * @param search      search type `blur` or `accurate`, means whether to search by fuzzy or exact match by
     *                    `mcpName`.
     * @param offset      offset
     * @param limit       limit
     * @return list of {@link McpServerBasicInfo} matched input parameters.
     */
    public Page<McpServerBasicInfo> listMcpServerWithOffset(String namespaceId, String mcpName, String search,
            int offset, int limit) {
        Page<McpServerIndexData> indexData = mcpServerIndex.searchMcpServerByName(namespaceId, mcpName, search, offset,
                limit);
        return mapIndexToBasicServerInfo(indexData);
    }
    
    /**
     * Get specified mcp server detail info. mcpServerId or namespaceId + mcpServerName is needed.
     *
     * @param namespaceId namespace id of mcp server
     * @param mcpServerId id of mcp server
     * @return detail info with {@link McpServerDetailInfo}
     * @throws NacosException any exception during handling
     */
    public McpServerDetailInfo getMcpServerDetail(String namespaceId, String mcpServerId, String mcpServerName,
            String version) throws NacosException {
        mcpServerId = resolveMcpServerId(namespaceId, mcpServerName, mcpServerId);
        
        McpServerVersionInfo mcpServerVersionInfo = getMcpServerVersionInfo(namespaceId, mcpServerId);
        if (StringUtils.isEmpty(version)) {
            int size = mcpServerVersionInfo.getVersionDetails().size();
            ServerVersionDetail last = mcpServerVersionInfo.getVersionDetails().get(size - 1);
            version = last.getVersion();
        }
        
        ConfigQueryChainRequest request = buildQueryMcpServerRequest(namespaceId, mcpServerId, version);
        ConfigQueryChainResponse response = configQueryChainService.handle(request);
        if (McpConfigUtils.isConfigNotFound(response.getStatus())) {
            throw new NacosApiException(NacosApiException.NOT_FOUND, ErrorCode.MCP_SEVER_VERSION_NOT_FOUND,
                    String.format("mcp server `%s` for version `%s` not found", mcpServerId, version));
        }
        
        McpServerStorageInfo serverSpecification = JacksonUtils.toObj(response.getContent(),
                McpServerStorageInfo.class);
        
        McpServerDetailInfo result = new McpServerDetailInfo();
        result.setId(mcpServerId);
        result.setNamespaceId(namespaceId);
        BeanUtils.copyProperties(serverSpecification, result);
        
        List<ServerVersionDetail> versionDetails = mcpServerVersionInfo.getVersionDetails();
        String latestVersion = mcpServerVersionInfo.getLatestPublishedVersion();
        for (ServerVersionDetail versionDetail : versionDetails) {
            versionDetail.setIs_latest(versionDetail.getVersion().equals(latestVersion));
        }
        result.setAllVersions(mcpServerVersionInfo.getVersionDetails());
        
        ServerVersionDetail versionDetail = result.getVersionDetail();
        versionDetail.setIs_latest(versionDetail.getVersion().equals(latestVersion));
        result.setVersion(versionDetail.getVersion());
        
        if (Objects.nonNull(serverSpecification.getToolsDescriptionRef())) {
            McpToolSpecification toolSpec = toolOperationService.getMcpTool(namespaceId,
                    serverSpecification.getToolsDescriptionRef());
            result.setToolSpec(toolSpec);
        }
        
        if (!AiConstants.Mcp.MCP_PROTOCOL_STDIO.equalsIgnoreCase(serverSpecification.getProtocol())) {
            injectEndpoint(result);
        }
        return result;
    }
    
    private McpServerVersionInfo getMcpServerVersionInfo(String namespaceId, String mcpServerId)
            throws NacosApiException {
        ConfigQueryChainRequest request = buildQueryMcpServerVersionInfoRequest(namespaceId, mcpServerId);
        ConfigQueryChainResponse response = configQueryChainService.handle(request);
        if (McpConfigUtils.isConfigNotFound(response.getStatus())) {
            throw new NacosApiException(NacosApiException.NOT_FOUND, ErrorCode.MCP_SERVER_NOT_FOUND,
                    String.format("Mcp server [ID: %s] not found in namespace [%s]. Response: %s", mcpServerId,
                            namespaceId, response.getMessage()));
        }
        
        return JacksonUtils.toObj(response.getContent(), McpServerVersionInfo.class);
    }
    
    private void injectEndpoint(McpServerDetailInfo detailInfo) throws NacosException {
        injectBackendEndpointRef(detailInfo);
        injectFrontendEndpointRef(detailInfo);
    }
    
    private void injectBackendEndpointRef(McpServerDetailInfo detailInfo) throws NacosException {
        List<Instance> instances;
        
        instances = endpointOperationService.getMcpServerEndpointInstances(
                detailInfo.getRemoteServerConfig().getServiceRef());
        
        String protocol = null;
        McpServiceRef serviceRef = detailInfo.getRemoteServerConfig().getServiceRef();
        if (Objects.nonNull(serviceRef)) {
            protocol = serviceRef.getTransportProtocol();
        }
        List<McpEndpointInfo> backendEndpoints = transferToMcpEndpointInfo(instances,
                detailInfo.getRemoteServerConfig().getExportPath(), 
                protocol);
        detailInfo.setBackendEndpoints(backendEndpoints);
    }

    private List<McpEndpointInfo> transferToMcpEndpointInfoWithHeaders(List<Instance> instances, String exportPath,
            String protocol, List<KeyValueInput> headers) {
        List<McpEndpointInfo> endpointInfos = new LinkedList<>();
        for (Instance each : instances) {
            McpEndpointInfo mcpEndpointInfo = new McpEndpointInfo();
            mcpEndpointInfo.setAddress(each.getIp());
            mcpEndpointInfo.setPort(each.getPort());
            mcpEndpointInfo.setProtocol(protocol);
            mcpEndpointInfo.setHeaders(headers);
            mcpEndpointInfo.setPath(exportPath);
            endpointInfos.add(mcpEndpointInfo);
        }
        return endpointInfos;
    }
    
    private List<McpEndpointInfo> transferToMcpEndpointInfo(List<Instance> instances, String exportPath,
            String protocol) {
        return transferToMcpEndpointInfoWithHeaders(instances, exportPath, protocol, null);
    }
    
    private void injectFrontendEndpointRef(McpServerDetailInfo detailInfo) throws NacosException {
        List<FrontEndpointConfig> frontEndpointConfigs = detailInfo.getRemoteServerConfig()
                .getFrontEndpointConfigList();
        if (CollectionUtils.isEmpty(frontEndpointConfigs)) {
            detailInfo.setFrontendEndpoints(Collections.emptyList());
            return;
        }
        List<McpEndpointInfo> frontendEndpoints = new LinkedList<>();
        for (FrontEndpointConfig each : frontEndpointConfigs) {
            if (AiConstants.Mcp.MCP_ENDPOINT_TYPE_REF.equals(each.getEndpointType())) {
                McpServiceRef mcpServiceRef = McpRequestUtil.transferToMcpServiceRef(each.getEndpointData());
                List<Instance> instances = endpointOperationService.getMcpServerEndpointInstances(mcpServiceRef);
                List<McpEndpointInfo> endpointInfos = transferToMcpEndpointInfoWithHeaders(instances, each.getPath(),
                        each.getProtocol(), each.getHeaders());
                frontendEndpoints.addAll(endpointInfos);
            } else if (AiConstants.Mcp.MCP_ENDPOINT_TYPE_DIRECT.equals(each.getEndpointType())) {
                McpEndpointInfo endpointInfo = new McpEndpointInfo();
                endpointInfo.setPath(each.getPath());
                endpointInfo.setProtocol(each.getProtocol());
                endpointInfo.setHeaders(each.getHeaders());
                String address = each.getEndpointData().toString();
                if (InternetAddressUtil.containsPort(address)) {
                    String[] info = InternetAddressUtil.splitIpPortStr(address);
                    endpointInfo.setAddress(info[0]);
                    endpointInfo.setPort(Integer.parseInt(info[1]));
                } else {
                    endpointInfo.setAddress(address);
                    endpointInfo.setPort(Constants.PROTOCOL_TYPE_HTTP.equals(each.getProtocol()) ? 80 : 443);
                }
                frontendEndpoints.add(endpointInfo);
            } else {
                frontendEndpoints.addAll(detailInfo.getBackendEndpoints());
            }
            detailInfo.setFrontendEndpoints(frontendEndpoints);
        }
    }
    
    /**
     * Create new mcp server.
     *
     * @param namespaceId           namespace id of mcp server
     * @param serverSpecification   mcp server specification, see {@link McpServerBasicInfo}
     * @param toolSpecification     mcp server included tools, see {@link McpToolSpecification}, optional
     * @param endpointSpecification mcp server endpoint specification, see {@link McpEndpointSpec}, optional
     * @throws NacosException any exception during handling
     */
    public String createMcpServer(String namespaceId, McpServerBasicInfo serverSpecification,
            McpToolSpecification toolSpecification, McpEndpointSpec endpointSpecification) throws NacosException {
        
        String existId = resolveMcpServerId(namespaceId, serverSpecification.getName(), StringUtils.EMPTY);
        if (StringUtils.isNotEmpty(existId)) {
            throw new NacosApiException(NacosApiException.CONFLICT, ErrorCode.RESOURCE_CONFLICT,
                    String.format("mcp server `%s` has existed, please update it rather than create.",
                            serverSpecification.getName()));
        }
        
        ServerVersionDetail versionDetail = serverSpecification.getVersionDetail();
        if (null == versionDetail && StringUtils.isNotBlank(serverSpecification.getVersion())) {
            versionDetail = new ServerVersionDetail();
            versionDetail.setVersion(serverSpecification.getVersion());
            serverSpecification.setVersionDetail(versionDetail);
        }
        if (Objects.isNull(versionDetail) || StringUtils.isEmpty(versionDetail.getVersion())) {
            throw new NacosApiException(NacosApiException.INVALID_PARAM, ErrorCode.PARAMETER_VALIDATE_ERROR,
                    "Version must be specified in parameter `serverSpecification`");
        }
        String id;
        String customMcpId = serverSpecification.getId();
        
        if (StringUtils.isEmpty(customMcpId)) {
            id = UUID.randomUUID().toString();
        } else {
            if (!StringUtils.isUuidString(customMcpId)) {
                throw new NacosApiException(NacosApiException.INVALID_PARAM, ErrorCode.PARAMETER_VALIDATE_ERROR,
                        "parameter `serverSpecification.id` is not match uuid pattern,  must obey uuid pattern");
            }
            if (mcpServerIndex.getMcpServerById(serverSpecification.getId()) != null) {
                throw new NacosApiException(NacosApiException.INVALID_PARAM, ErrorCode.PARAMETER_VALIDATE_ERROR,
                        "parameter `serverSpecification.id` conflict with exist mcp server id");
            }
            
            id = customMcpId;
        }
        
        serverSpecification.setId(id);
        ZonedDateTime currentTime = ZonedDateTime.now(ZoneOffset.UTC);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(Constants.RELEASE_DATE_FORMAT);
        String formattedCurrentTime = currentTime.format(formatter);
        versionDetail.setRelease_date(formattedCurrentTime);
        
        McpServerStorageInfo newSpecification = new McpServerStorageInfo();
        BeanUtils.copyProperties(serverSpecification, newSpecification);
        injectToolAndEndpoint(namespaceId, serverSpecification.getId(), newSpecification, toolSpecification,
                endpointSpecification);
        
        McpServerVersionInfo versionInfo = buildServerVersionInfo(newSpecification, id, versionDetail);
        
        ConfigRequestInfo configRequestInfo = new ConfigRequestInfo();
        configRequestInfo.setUpdateForExist(Boolean.FALSE);
        
        ConfigFormV3 mcpServerVersionForm = buildMcpServerVersionForm(namespaceId, versionInfo);
        configOperationService.publishConfig(mcpServerVersionForm, configRequestInfo, null);
        
        ConfigForm configForm = buildMcpConfigForm(namespaceId, id, versionDetail.getVersion(), newSpecification);
        long startOperationTime = System.currentTimeMillis();
        configOperationService.publishConfig(configForm, configRequestInfo, null);
        syncEffectService.toSync(configForm, startOperationTime);
        
        // Delete the relevant cache after a successful database operation
        invalidateCacheAfterDbOperation(namespaceId, serverSpecification.getName(), id);
        
        return id;
    }
    
    private static McpServerVersionInfo buildServerVersionInfo(McpServerBasicInfo serverSpecification, String id,
            ServerVersionDetail versionDetail) {
        McpServerVersionInfo versionInfo = new McpServerVersionInfo();
        versionInfo.setName(serverSpecification.getName());
        versionInfo.setId(id);
        versionInfo.setDescription(serverSpecification.getDescription());
        versionInfo.setRepository(serverSpecification.getRepository());
        versionInfo.setFrontProtocol(serverSpecification.getFrontProtocol());
        versionInfo.setProtocol(serverSpecification.getProtocol());
        versionInfo.setCapabilities(serverSpecification.getCapabilities());
        versionInfo.setLatestPublishedVersion(serverSpecification.getVersionDetail().getVersion());
        versionInfo.setVersions(Collections.singletonList(versionDetail));
        return versionInfo;
    }
    
    /**
     * Update existed mcp server.
     *
     * <p>
     * `namespaceId` and `mcpServerId` can't be changed.
     * </p>
     *
     * @param namespaceId           namespace id of mcp server, used to mark which mcp server to update
     * @param serverSpecification   mcp server specification, see {@link McpServerBasicInfo}
     * @param toolSpecification     mcp server included tools, see {@link McpToolSpecification}, optional
     * @param endpointSpecification mcp server endpoint specification, see {@link McpEndpointSpec}, optional
     * @throws NacosException any exception during handling
     */
    public void updateMcpServer(String namespaceId, boolean isPublish, McpServerBasicInfo serverSpecification,
            McpToolSpecification toolSpecification, McpEndpointSpec endpointSpecification) throws NacosException {
        
        String mcpServerId = serverSpecification.getId();
        mcpServerId = resolveMcpServerId(namespaceId, serverSpecification.getName(), mcpServerId);
        if (StringUtils.isEmpty(serverSpecification.getId())) {
            serverSpecification.setId(mcpServerId);
        }
        
        ServerVersionDetail versionDetail = serverSpecification.getVersionDetail();
        if (null == versionDetail && StringUtils.isNotBlank(serverSpecification.getVersion())) {
            versionDetail = new ServerVersionDetail();
            versionDetail.setVersion(serverSpecification.getVersion());
            serverSpecification.setVersionDetail(versionDetail);
        }
        if (Objects.isNull(versionDetail) || StringUtils.isEmpty(versionDetail.getVersion())) {
            throw new NacosApiException(NacosApiException.INVALID_PARAM, ErrorCode.PARAMETER_VALIDATE_ERROR,
                    "Version must be specified in parameter `serverSpecification`");
        }
        final McpServerVersionInfo mcpServerVersionInfo = getMcpServerVersionInfo(namespaceId, mcpServerId);
        
        String updateVersion = versionDetail.getVersion();
        McpServerStorageInfo newSpecification = new McpServerStorageInfo();
        BeanUtils.copyProperties(serverSpecification, newSpecification);
        injectToolAndEndpoint(namespaceId, mcpServerId, newSpecification, toolSpecification, endpointSpecification);
        
        ConfigForm configForm = buildMcpConfigForm(namespaceId, mcpServerId, updateVersion, newSpecification);
        configOperationService.publishConfig(configForm, new ConfigRequestInfo(), null);
        
        List<ServerVersionDetail> versionDetails = mcpServerVersionInfo.getVersionDetails();
        Set<String> versionSet = versionDetails.stream().map(ServerVersionDetail::getVersion)
                .collect(Collectors.toSet());
        if (!versionSet.contains(updateVersion)) {
            ServerVersionDetail version = new ServerVersionDetail();
            version.setVersion(updateVersion);
            versionDetails.add(version);
            mcpServerVersionInfo.setVersions(versionDetails);
        }
        
        if (isPublish) {
            mcpServerVersionInfo.setName(newSpecification.getName());
            mcpServerVersionInfo.setDescription(newSpecification.getDescription());
            mcpServerVersionInfo.setRepository(newSpecification.getRepository());
            mcpServerVersionInfo.setProtocol(newSpecification.getProtocol());
            mcpServerVersionInfo.setFrontProtocol(newSpecification.getFrontProtocol());
            mcpServerVersionInfo.setCapabilities(newSpecification.getCapabilities());
            mcpServerVersionInfo.setLatestPublishedVersion(updateVersion);
            
            for (ServerVersionDetail detail : versionDetails) {
                if (detail.getVersion().equals(updateVersion)) {
                    ZonedDateTime currentTime = ZonedDateTime.now(ZoneOffset.UTC);
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");
                    String formattedCurrentTime = currentTime.format(formatter);
                    detail.setRelease_date(formattedCurrentTime);
                    detail.setIs_latest(true);
                    break;
                } else {
                    detail.setIs_latest(false);
                }
            }
            mcpServerVersionInfo.setVersions(versionDetails);
            mcpServerVersionInfo.setEnabled(newSpecification.isEnabled());
        }
        
        ConfigFormV3 mcpServerVersionForm = buildMcpServerVersionForm(namespaceId, mcpServerVersionInfo);
        long startOperationTime = System.currentTimeMillis();
        configOperationService.publishConfig(mcpServerVersionForm, new ConfigRequestInfo(), null);
        syncEffectService.toSync(mcpServerVersionForm, startOperationTime);
        
        // Delete the relevant cache after a successful database operation
        invalidateCacheAfterDbUpdateOperation(namespaceId, mcpServerVersionInfo.getName(),
                serverSpecification.getName(), mcpServerId);
    }
    
    /**
     * Delete existed mcp server.
     *
     * @param namespaceId namespace id of mcp server
     * @param mcpServerId name of mcp server
     * @throws NacosException any exception during handling
     */
    public void deleteMcpServer(String namespaceId, String mcpName, String mcpServerId, String version)
            throws NacosException {
        mcpServerId = resolveMcpServerId(namespaceId, mcpName, mcpServerId);
        McpServerVersionInfo mcpServerVersionInfo = getMcpServerVersionInfo(namespaceId, mcpServerId);
        List<String> versionsNeedDelete = new ArrayList<>();
        if (StringUtils.isNotEmpty(version)) {
            versionsNeedDelete.add(version);
        } else {
            versionsNeedDelete = mcpServerVersionInfo.getVersionDetails().stream().map(ServerVersionDetail::getVersion)
                    .collect(Collectors.toList());
        }
        
        for (String versionNeedDelete : versionsNeedDelete) {
            toolOperationService.deleteMcpTool(namespaceId, mcpServerId, versionNeedDelete);
            endpointOperationService.deleteMcpServerEndpointService(namespaceId, mcpServerVersionInfo.getName());
            String serverSpecDataId = McpConfigUtils.formatServerSpecInfoDataId(mcpServerId, versionNeedDelete);
            configOperationService.deleteConfig(serverSpecDataId, Constants.MCP_SERVER_GROUP, namespaceId, null, null,
                    "nacos", null);
            String serverVersionDataId = McpConfigUtils.formatServerVersionInfoDataId(mcpServerId);
            configOperationService.deleteConfig(serverVersionDataId, Constants.MCP_SERVER_VERSIONS_GROUP, namespaceId,
                    null, null, "nacos", null);
        }
        
        // Delete the relevant cache after a successful database operation
        invalidateCacheAfterDbOperation(namespaceId, mcpName, mcpServerId);
    }
    
    private void injectToolAndEndpoint(String namespaceId, String mcpServerId, McpServerStorageInfo serverSpecification,
            McpToolSpecification toolSpecification, McpEndpointSpec endpointSpecification) throws NacosException {
        serverSpecification.setCapabilities(new LinkedList<>());
        boolean hasToolSpec = toolSpecification != null;
        boolean hasTools = hasToolSpec && toolSpecification.getTools() != null;
        boolean hasSecuritySchemes = hasToolSpec && toolSpecification.getSecuritySchemes() != null;
        boolean shouldCreateToolConfig = hasToolSpec && (hasTools || hasSecuritySchemes);
        if (shouldCreateToolConfig) {
            toolOperationService.refreshMcpTool(namespaceId, serverSpecification, toolSpecification);
            serverSpecification.getCapabilities().add(McpCapability.TOOL);
            String version = serverSpecification.getVersionDetail().getVersion();
            String toolSpecDataId = McpConfigUtils.formatServerToolSpecDataId(mcpServerId, version);
            serverSpecification.setToolsDescriptionRef(toolSpecDataId);
        }
        if (null != endpointSpecification) {
            Service service = endpointOperationService.createMcpServerEndpointServiceIfNecessary(namespaceId,
                    serverSpecification.getName(), serverSpecification.getVersionDetail().getVersion(), endpointSpecification);
            String transportProtocol = endpointSpecification.getData().get(Constants.MCP_BACKEND_ISTANCE_PROTOCOL_KEY);
            McpServiceRef serviceRef = new McpServiceRef();
            serviceRef.setNamespaceId(service.getNamespace());
            serviceRef.setGroupName(service.getGroup());
            serviceRef.setServiceName(service.getName());
            serviceRef.setTransportProtocol(transportProtocol);
            serverSpecification.getRemoteServerConfig().setServiceRef(serviceRef);
        }
    }
    
    private ConfigFormV3 buildMcpServerVersionForm(String namespaceId, McpServerVersionInfo mcpServerVersionInfo) {
        ConfigFormV3 configFormV3 = new ConfigFormV3();
        configFormV3.setGroupName(Constants.MCP_SERVER_VERSIONS_GROUP);
        configFormV3.setGroup(Constants.MCP_SERVER_VERSIONS_GROUP);
        configFormV3.setNamespaceId(namespaceId);
        configFormV3.setDataId(McpConfigUtils.formatServerVersionInfoDataId(mcpServerVersionInfo.getId()));
        configFormV3.setContent(JacksonUtils.toJson(mcpServerVersionInfo));
        configFormV3.setType(ConfigType.JSON.getType());
        configFormV3.setAppName(mcpServerVersionInfo.getName());
        configFormV3.setSrcUser("nacos");
        String configTags = buildMcpServerVersionConfigTags(mcpServerVersionInfo.getName());
        configFormV3.setConfigTags(configTags);
        return configFormV3;
    }
    
    private ConfigFormV3 buildMcpConfigForm(String namespaceId, String mcpServerId, String version,
            McpServerBasicInfo serverSpecification) {
        ConfigFormV3 configFormV3 = new ConfigFormV3();
        configFormV3.setGroupName(Constants.MCP_SERVER_GROUP);
        configFormV3.setGroup(Constants.MCP_SERVER_GROUP);
        configFormV3.setNamespaceId(namespaceId);
        configFormV3.setDataId(McpConfigUtils.formatServerSpecInfoDataId(mcpServerId, version));
        configFormV3.setContent(JacksonUtils.toJson(serverSpecification));
        configFormV3.setType(ConfigType.JSON.getType());
        configFormV3.setAppName(serverSpecification.getName());
        configFormV3.setSrcUser("nacos");
        configFormV3.setConfigTags(MCP_SERVER_CONFIG_MARK);
        return configFormV3;
    }
    
    private ConfigQueryChainRequest buildQueryMcpServerRequest(String namespaceId, String mcpServerId, String version) {
        ConfigQueryChainRequest request = new ConfigQueryChainRequest();
        request.setDataId(McpConfigUtils.formatServerSpecInfoDataId(mcpServerId, version));
        request.setGroup(Constants.MCP_SERVER_GROUP);
        request.setTenant(namespaceId);
        return request;
    }
    
    private ConfigQueryChainRequest buildQueryMcpServerVersionInfoRequest(String namespaceId, String mcpServerId) {
        ConfigQueryChainRequest request = new ConfigQueryChainRequest();
        request.setDataId(McpConfigUtils.formatServerVersionInfoDataId(mcpServerId));
        request.setGroup(Constants.MCP_SERVER_VERSIONS_GROUP);
        request.setTenant(namespaceId);
        return request;
    }
    
    private McpServerVersionInfo transferToMcpServerVersionInfo(String content) {
        McpServerVersionInfo versionInfo = JacksonUtils.toObj(content, McpServerVersionInfo.class);
        String latestPublishedVersion = versionInfo.getLatestPublishedVersion();
        for (ServerVersionDetail versionDetail : versionInfo.getVersionDetails()) {
            if (versionDetail.getVersion().equals(latestPublishedVersion)) {
                versionDetail.setIs_latest(true);
                versionInfo.setVersionDetail(versionDetail);
                break;
            } else {
                versionDetail.setIs_latest(false);
            }
        }
        versionInfo.setVersion(latestPublishedVersion);
        return versionInfo;
    }
    
    private String resolveMcpServerId(String namespaceId, String serverName, String serverId) {
        if (StringUtils.isNotEmpty(serverId)) {
            return serverId;
        }
        
        McpServerIndexData indexData = mcpServerIndex.getMcpServerByName(namespaceId, serverName);
        if (Objects.nonNull(indexData)) {
            return indexData.getId();
        }
        
        return null;
    }
    
    /**
     * Invalidate cache after update mcp server operation.
     *
     * @param namespaceId namespace id of mcp server
     * @param oldMcpName  old mcp server name
     * @param newMcpName  new mcp server name
     * @param mcpServerId mcp server id
     */
    private void invalidateCacheAfterDbUpdateOperation(String namespaceId, String oldMcpName, String newMcpName,
            String mcpServerId) {
        try {
            if (StringUtils.isNotEmpty(oldMcpName) && !oldMcpName.equals(newMcpName)) {
                mcpServerIndex.removeMcpServerByName(namespaceId, oldMcpName);
            }
            if (StringUtils.isNotEmpty(newMcpName)) {
                mcpServerIndex.removeMcpServerByName(namespaceId, newMcpName);
            }
            if (StringUtils.isNotEmpty(mcpServerId)) {
                mcpServerIndex.removeMcpServerById(mcpServerId);
            }
            LOGGER.debug("Cache invalidated after updateMcpServer: namespaceId={}, oldName={}, newName={}, id={}",
                    namespaceId, oldMcpName, newMcpName, mcpServerId);
        } catch (Exception e) {
            LOGGER.warn(
                    "Failed to invalidate cache after updateMcpServer: namespaceId={}, oldName={}, newName={}, id={}, error={}",
                    namespaceId, oldMcpName, newMcpName, mcpServerId, e.getMessage());
        }
    }
    
    /**
     * Unified cache invalidation method.
     *
     * @param namespaceId namespace ID
     * @param mcpName     MCP server name
     * @param mcpServerId MCP server ID
     */
    private void invalidateCacheAfterDbOperation(String namespaceId, String mcpName, String mcpServerId) {
        try {
            if (StringUtils.isNotEmpty(mcpName)) {
                mcpServerIndex.removeMcpServerByName(namespaceId, mcpName);
            }
            if (StringUtils.isNotEmpty(mcpServerId)) {
                mcpServerIndex.removeMcpServerById(mcpServerId);
            }
            LOGGER.debug("Cache invalidated after DB operation: namespaceId={}, mcpName={}, mcpId={}", namespaceId,
                    mcpName, mcpServerId);
        } catch (Exception e) {
            LOGGER.warn("Failed to invalidate cache after DB operation: namespaceId={}, mcpName={}, mcpId={}, error={}",
                    namespaceId, mcpName, mcpServerId, e.getMessage());
        }
    }
}
