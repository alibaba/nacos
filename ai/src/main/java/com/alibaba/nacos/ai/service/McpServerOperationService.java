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
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.mcp.*;
import com.alibaba.nacos.api.ai.model.mcp.registry.*;
import com.alibaba.nacos.api.config.ConfigType;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.naming.pojo.Instance;
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
import org.springframework.beans.BeanUtils;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static com.alibaba.nacos.ai.constant.Constants.MCP_SERVER_CONFIG_MARK;

/**
 * Nacos AI MCP server operation service.
 * Currently, mcp server is present by there configs:
 * 1. mcp server version info {@link McpServerVersionInfo}
 * 2. mcp server description for specified version {@link McpServerDetailInfo}
 * 3. mcp tools info {@link McpToolSpecification}
 * 
 * when create the mcp server, we will tag the {@link McpServerVersionInfo}
 * with mcp servername for name fuzzy search.
 *
 * @author xiweng.yy
 */
@org.springframework.stereotype.Service
public class McpServerOperationService {
    
    private final ConfigQueryChainService configQueryChainService;
    
    private final ConfigOperationService configOperationService;
    
    private final McpToolOperationService toolOperationService;
    
    private final McpEndpointOperationService endpointOperationService;
    
    private final McpServerIndex mcpServerIndex;

    public McpServerOperationService(ConfigQueryChainService configQueryChainService,
                                     ConfigOperationService configOperationService, McpToolOperationService toolOperationService, 
                                     McpEndpointOperationService endpointOperationService, McpServerIndex mcpServerIndex) {
        this.configQueryChainService = configQueryChainService;
        this.configOperationService = configOperationService;
        this.toolOperationService = toolOperationService;
        this.endpointOperationService = endpointOperationService;
        this.mcpServerIndex = mcpServerIndex;
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
        Page<McpServerIndexData> indexData = mcpServerIndex.searchMcpServerByName(namespaceId, mcpName, search,
                pageSize * (pageNo - 1), pageSize);
        return getMcpServerBasicInfoPage(indexData);
    }
    
    private Page<McpServerBasicInfo> getMcpServerBasicInfoPage(Page<McpServerIndexData> indexData) {
        List<McpServerBasicInfo> finalResult = indexData.getPageItems().stream().map((index) -> {
            ConfigQueryChainRequest request = buildQueryMcpServerVersionInfoRequest(index.getNamespaceId(), index.getId());
            ConfigQueryChainResponse response = configQueryChainService.handle(request);
            return transferToMcpServerVersionInfo(response.getContent());
        }).collect(Collectors.toList());

        Page<McpServerBasicInfo> result = new Page<>();
        result.setTotalCount(indexData.getTotalCount());
        result.setPageNumber(indexData.getPageNumber());
        result.setPagesAvailable(indexData.getPagesAvailable());
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
    public Page<McpServerBasicInfo> listMcpServerWithOffset(String namespaceId, String mcpName, String search,int offset,
                                                  int limit) {
        Page<McpServerIndexData> indexData = mcpServerIndex.searchMcpServerByName(namespaceId, mcpName, search,offset, limit);

        return getMcpServerBasicInfoPage(indexData);
    }
    
    /**
     * Get specified mcp server detail info.
     *
     * @param namespaceId namespace id of mcp server
     * @param mcpServerId id of mcp server
     * @return detail info with {@link McpServerDetailInfo}
     * @throws NacosException any exception during handling
     */
    public McpServerDetailInfo getMcpServerDetail(String namespaceId, String mcpServerId, String mcpServerName, String version) throws NacosException {
        mcpServerId = resolveMcpServerId(namespaceId, mcpServerName, mcpServerId);

        if (StringUtils.isEmpty(namespaceId)) {
            McpServerIndexData indexData = mcpServerIndex.getMcpServerById(mcpServerId);
            if (Objects.isNull(indexData)) {
                return null;
            }
            namespaceId = indexData.getNamespaceId();
        }

        McpServerVersionInfo mcpServerVersionInfo = getMcpServerVersionInfo(namespaceId, mcpServerId);
        if (StringUtils.isEmpty(version)) {
            int size = mcpServerVersionInfo.getVersionDetails().size();
            ServerVersionDetail last = mcpServerVersionInfo.getVersionDetails().get(size - 1);
            version = last.getVersion();
        }
        
        ConfigQueryChainRequest request = buildQueryMcpServerRequest(namespaceId, mcpServerId, version);
        ConfigQueryChainResponse response = configQueryChainService.handle(request);
        if (ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_NOT_FOUND == response.getStatus()) {
            throw new NacosApiException(NacosApiException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    String.format("mcp server `%s` for version `%s` not found", mcpServerId, version));
        }
        McpServerStorageInfo serverSpecification = JacksonUtils.toObj(response.getContent(),
                McpServerStorageInfo.class);
        McpServerDetailInfo result = new McpServerDetailInfo();
        List<ServerVersionDetail> versionDetails = mcpServerVersionInfo.getVersionDetails();
        for (ServerVersionDetail versionDetail : versionDetails) {
            if (versionDetail.getVersion().equals(mcpServerVersionInfo.getLatestPublishedVersion())) {
                versionDetail.setIs_latest(true);
            } else {
                versionDetail.setIs_latest(false);
            }
        }
        
        result.setAllVersions(mcpServerVersionInfo.getVersionDetails());
        BeanUtils.copyProperties(serverSpecification, result);
        if (result.getVersionDetail().getVersion().equals(mcpServerVersionInfo.getLatestPublishedVersion())) {
            result.getVersionDetail().setIs_latest(true);
        } else {
            result.getVersionDetail().setIs_latest(false);
        }
        if (null != serverSpecification.getToolsDescriptionRef()) {
            McpToolSpecification toolSpec = toolOperationService.getMcpTool(namespaceId,
                    serverSpecification.getToolsDescriptionRef());
            result.setToolSpec(toolSpec);
        }
        if (!AiConstants.Mcp.MCP_PROTOCOL_STDIO.equalsIgnoreCase(serverSpecification.getProtocol())) {
            injectBackendEndpointRef(namespaceId, result);
        }
        
        result.setNamespaceId(namespaceId);
        return result;
    }
    
    public McpServerVersionInfo getMcpServerVersionInfo(String namespaceId, String mcpServerId) throws NacosApiException {
        if (StringUtils.isEmpty(namespaceId)) {
            McpServerIndexData indexData = mcpServerIndex.getMcpServerById(mcpServerId);
            namespaceId = indexData.getNamespaceId();
        }

        ConfigQueryChainRequest request = buildQueryMcpServerVersionInfoRequest(namespaceId, mcpServerId);
        ConfigQueryChainResponse response = configQueryChainService.handle(request);
        if (ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_NOT_FOUND == response.getStatus()) {
            throw new NacosApiException(NacosApiException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    String.format("mcp server `%s` not found", mcpServerId));
        }
        
       return JacksonUtils.toObj(response.getContent(), McpServerVersionInfo.class);
    }
    
    private void injectBackendEndpointRef(String namespaceId, McpServerDetailInfo detailInfo) throws NacosException {
        List<Instance> instances;
        if (Constants.PROTOCOL_TYPE_HTTP.equals(detailInfo.getProtocol())) {
            McpServiceRef mcpServiceRef = new McpServiceRef();
            mcpServiceRef.setServiceName(detailInfo.getName());
            mcpServiceRef.setGroupName(Constants.MCP_SERVER_PROTOCOL_TRANSLATOR_GROUP);
            mcpServiceRef.setNamespaceId(namespaceId);
            instances = endpointOperationService.getMcpServerEndpointInstances(mcpServiceRef);
        } else {
            instances = endpointOperationService.getMcpServerEndpointInstances(
                    detailInfo.getRemoteServerConfig().getServiceRef());
        }
        List<McpEndpointInfo> backendEndpoints = new LinkedList<>();
        for (Instance each : instances) {
            McpEndpointInfo mcpEndpointInfo = new McpEndpointInfo();
            mcpEndpointInfo.setAddress(each.getIp());
            mcpEndpointInfo.setPort(each.getPort());
            String exportPath = detailInfo.getRemoteServerConfig().getExportPath();
            if (Constants.PROTOCOL_TYPE_HTTP.equals(detailInfo.getProtocol())) {
                exportPath = each.getMetadata().get("path");
            }
            mcpEndpointInfo.setPath(exportPath);
            backendEndpoints.add(mcpEndpointInfo);
        }
        detailInfo.setBackendEndpoints(backendEndpoints);
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
    public void createMcpServer(String namespaceId, McpServerBasicInfo serverSpecification,
            McpToolSpecification toolSpecification, McpEndpointSpec endpointSpecification) throws NacosException {

        String existId = resolveMcpServerId(namespaceId, serverSpecification.getName(), StringUtils.EMPTY);
        if (StringUtils.isNotEmpty(existId)) {
            throw new NacosApiException(NacosApiException.CONFLICT, ErrorCode.RESOURCE_CONFLICT,
                    String.format("mcp server `%s` has existed, please update it rather than create.", serverSpecification.getName()));
        }

        String id = UUID.randomUUID().toString();
        McpServerIndexData indexData = new McpServerIndexData();
        indexData.setId(id);
        indexData.setNamespaceId(namespaceId);
        indexData.setName(serverSpecification.getName());
        
        ServerVersionDetail versionDetail = serverSpecification.getVersionDetail();
        ZonedDateTime currentTime = ZonedDateTime.now(ZoneOffset.UTC);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");
        String formattedCurrentTime = currentTime.format(formatter);
        versionDetail.setRelease_date(formattedCurrentTime);
        
        McpServerStorageInfo newSpecification = new McpServerStorageInfo();
        BeanUtils.copyProperties(serverSpecification, newSpecification);
        injectToolAndEndpoint(namespaceId, serverSpecification.getName(), newSpecification, toolSpecification, endpointSpecification);

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
        
        ConfigRequestInfo configRequestInfo = new ConfigRequestInfo();
        configRequestInfo.setUpdateForExist(Boolean.FALSE);
        ConfigFormV3 mcpServerVersionForm = buildMcpServerVersionForm(namespaceId, versionInfo);
        configOperationService.publishConfig(mcpServerVersionForm, configRequestInfo, null);
        
        ConfigForm configForm = buildMcpConfigForm(namespaceId, id, versionDetail.getVersion(), newSpecification);
        configOperationService.publishConfig(configForm, configRequestInfo, null);
    }
    
    /**
     * Update existed mcp server.
     *
     * <p>
     * `namespaceId` and `mcpServerId` can't be changed.
     * </p>
     *
     * @param namespaceId           namespace id of mcp server, used to mark which mcp server to update
     * @param mcpServerId               name of mcp server, used to mark which mcp server to update
     * @param serverSpecification   mcp server specification, see {@link McpServerBasicInfo}
     * @param toolSpecification     mcp server included tools, see {@link McpToolSpecification}, optional
     * @param endpointSpecification mcp server endpoint specification, see {@link McpEndpointSpec}, optional
     * @throws NacosException any exception during handling
     */
    public void updateMcpServer(String namespaceId, String mcpServerId, boolean isPublish, McpServerBasicInfo serverSpecification,
            McpToolSpecification toolSpecification, McpEndpointSpec endpointSpecification) throws NacosException {
        mcpServerId = resolveMcpServerId(namespaceId, serverSpecification.getName(), mcpServerId);
        
        McpServerIndexData indexData = mcpServerIndex.getMcpServerById(mcpServerId);
        if (Objects.isNull(indexData)) {
            throw new NacosApiException(NacosApiException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    String.format("mcp server `%s` not found", mcpServerId));
        }

        String updateVersion = serverSpecification.getVersionDetail().getVersion();
        McpServerVersionInfo mcpServerVersionInfo = getMcpServerVersionInfo(namespaceId, mcpServerId);
        List<ServerVersionDetail> versionDetails = mcpServerVersionInfo.getVersionDetails();
        
        McpServerStorageInfo newSpecification = new McpServerStorageInfo();
        BeanUtils.copyProperties(serverSpecification, newSpecification);
        injectToolAndEndpoint(namespaceId, mcpServerId, newSpecification, toolSpecification, endpointSpecification);
        ConfigForm configForm = buildMcpConfigForm(namespaceId, mcpServerId, updateVersion , newSpecification);
        configOperationService.publishConfig(configForm, new ConfigRequestInfo(), null);
        
        Set<String> versionSet = versionDetails.stream().map(ServerVersionDetail::getVersion).collect(Collectors.toSet());
        if (!versionSet.contains(updateVersion)) {
            ServerVersionDetail versionDetail = new ServerVersionDetail();
            versionDetail.setVersion(updateVersion);
            versionDetails.add(versionDetail);
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

            for (ServerVersionDetail versionDetail : versionDetails) {
                if (versionDetail.getVersion().equals(updateVersion)) {
                    ZonedDateTime currentTime = ZonedDateTime.now(ZoneOffset.UTC);
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");
                    String formattedCurrentTime = currentTime.format(formatter);
                    versionDetail.setRelease_date(formattedCurrentTime);
                    versionDetail.setIs_latest(true);
                    break;
                }
            }
            mcpServerVersionInfo.setVersions(versionDetails);
        }
        
        ConfigFormV3 mcpServerVersionForm = buildMcpServerVersionForm(namespaceId, mcpServerVersionInfo);
        configOperationService.publishConfig(mcpServerVersionForm, new ConfigRequestInfo(), null);
    }
    
    /**
     * Delete existed mcp server.
     *
     * @param namespaceId namespace id of mcp server
     * @param mcpServerId     name of mcp server
     * @throws NacosException any exception during handling
     */
    public void deleteMcpServer(String namespaceId, String mcpServerId, String version) throws NacosException {
        McpServerVersionInfo mcpServerVersionInfo = getMcpServerVersionInfo(namespaceId, mcpServerId);
        List<String> versionsNeedDelete = new ArrayList<>();
        if (StringUtils.isNotEmpty(version)) {
            versionsNeedDelete.add(version);
        } else {
            versionsNeedDelete = mcpServerVersionInfo.getVersionDetails()
                    .stream().map(ServerVersionDetail::getVersion).collect(Collectors.toList());
        }

        McpServerIndexData indexData = mcpServerIndex.getMcpServerById(mcpServerId);
        namespaceId = indexData.getNamespaceId();
        for (String versionNeedDelete : versionsNeedDelete) {
            toolOperationService.deleteMcpTool(namespaceId, mcpServerId, versionNeedDelete);
            endpointOperationService.deleteMcpServerEndpointService(namespaceId, mcpServerVersionInfo.getName());
            String serverSpecDataId = mcpServerId + "-" + versionNeedDelete + Constants.MCP_SERVER_SPEC_DATA_ID_SUFFIX;
            configOperationService.deleteConfig(serverSpecDataId,
                    Constants.MCP_SERVER_GROUP, namespaceId, null, null, "nacos", null);
            String serverVersionDataId = mcpServerId + Constants.MCP_SERVER_VERSION_DATA_ID_SUFFIX;
            configOperationService.deleteConfig(serverVersionDataId, Constants.MCP_SERVER_VERSIONS_GROUP, namespaceId, null, null, "nacos", null);
        }
    }
    
    private void injectToolAndEndpoint(String namespaceId, String mcpServerId, McpServerStorageInfo serverSpecification,
            McpToolSpecification toolSpecification, McpEndpointSpec endpointSpecification) throws NacosException {
        serverSpecification.setCapabilities(new LinkedList<>());
        String currentVersion = serverSpecification.getVersionDetail().getVersion();
        if (null != toolSpecification && null != toolSpecification.getTools() && !toolSpecification.getTools().isEmpty()) {
            toolOperationService.refreshMcpTool(namespaceId, mcpServerId, currentVersion, toolSpecification);
            serverSpecification.getCapabilities().add(McpCapability.TOOL);
            serverSpecification.setToolsDescriptionRef(mcpServerId + "-" + currentVersion + Constants.MCP_SERVER_TOOL_DATA_ID_SUFFIX);
        }
        if (null != endpointSpecification) {
            Service service = endpointOperationService.createMcpServerEndpointServiceIfNecessary(namespaceId, mcpServerId,
                    endpointSpecification);
            McpServiceRef serviceRef = new McpServiceRef();
            serviceRef.setNamespaceId(service.getNamespace());
            serviceRef.setGroupName(service.getGroup());
            serviceRef.setServiceName(service.getName());
            serverSpecification.getRemoteServerConfig().setServiceRef(serviceRef);
        }
    }
    
    private ConfigFormV3 buildMcpServerVersionForm(String namespaceId, McpServerVersionInfo mcpServerVersionInfo) {
        ConfigFormV3 configFormV3 = new ConfigFormV3();
        configFormV3.setGroupName(Constants.MCP_SERVER_VERSIONS_GROUP);
        configFormV3.setGroup(Constants.MCP_SERVER_VERSIONS_GROUP);
        configFormV3.setNamespaceId(namespaceId);
        configFormV3.setDataId(mcpServerVersionInfo.getId() + Constants.MCP_SERVER_VERSION_DATA_ID_SUFFIX);
        configFormV3.setContent(JacksonUtils.toJson(mcpServerVersionInfo));
        configFormV3.setType(ConfigType.JSON.getType());
        configFormV3.setAppName(mcpServerVersionInfo.getName());
        configFormV3.setSrcUser("nacos");
        configFormV3.setConfigTags(buildMcpServerVersionConfigTags(mcpServerVersionInfo.getName()));
        return configFormV3;
    }
    
    private String buildMcpServerVersionConfigTags(String serverName) {
        return StringUtils.join(Arrays.asList(MCP_SERVER_CONFIG_MARK, Constants.MCP_SERVER_NAME_TAG_KEY_PREFIX + serverName), ",");
    }
    
    private ConfigFormV3 buildMcpConfigForm(String namespaceId, String mcpServerId, String version,
            McpServerBasicInfo serverSpecification) {
        ConfigFormV3 configFormV3 = new ConfigFormV3();
        configFormV3.setGroupName(Constants.MCP_SERVER_GROUP);
        configFormV3.setGroup(Constants.MCP_SERVER_GROUP);
        configFormV3.setNamespaceId(namespaceId);
        configFormV3.setDataId(mcpServerId + "-" + version + Constants.MCP_SERVER_SPEC_DATA_ID_SUFFIX);
        configFormV3.setContent(JacksonUtils.toJson(serverSpecification));
        configFormV3.setType(ConfigType.JSON.getType());
        configFormV3.setAppName(serverSpecification.getName());
        configFormV3.setSrcUser("nacos");
        configFormV3.setConfigTags(MCP_SERVER_CONFIG_MARK);
        return configFormV3;
    }
    
    private ConfigQueryChainRequest buildQueryMcpServerRequest(String namespaceId, String mcpServerId, String version) {
        ConfigQueryChainRequest request = new ConfigQueryChainRequest();
        request.setDataId(mcpServerId + "-" + version + Constants.MCP_SERVER_SPEC_DATA_ID_SUFFIX);
        request.setGroup(Constants.MCP_SERVER_GROUP);
        request.setTenant(namespaceId);
        return request;
    }
    
    private ConfigQueryChainRequest buildQueryMcpServerVersionInfoRequest(String namespaceId, String mcpServerId) {
        ConfigQueryChainRequest request = new ConfigQueryChainRequest();
        request.setDataId(mcpServerId + Constants.MCP_SERVER_VERSION_DATA_ID_SUFFIX);
        request.setGroup(Constants.MCP_SERVER_VERSIONS_GROUP);
        request.setTenant(namespaceId);
        return request;
    }
    
    private McpServerVersionInfo transferToMcpServerVersionInfo(String content) {
        McpServerVersionInfo versionInfo = JacksonUtils.toObj(content, McpServerVersionInfo.class);
        String latestPublishedVersion = versionInfo.getLatestPublishedVersion();
        for (ServerVersionDetail versionDetail : versionInfo.getVersionDetails()) {
            if (versionDetail.getVersion().equals(latestPublishedVersion)) {
                versionInfo.setVersionDetail(versionDetail);
                break;
            }
        }
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
}
