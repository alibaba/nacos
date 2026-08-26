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

import com.alibaba.nacos.ai.constant.AiResourceConstants;
import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.index.McpServerIndex;
import com.alibaba.nacos.ai.model.mcp.McpServerIndexData;
import com.alibaba.nacos.ai.model.mcp.McpServerStorageInfo;
import com.alibaba.nacos.ai.model.mcp.McpVersionStorageDescriptor;
import com.alibaba.nacos.ai.service.McpEndpointOperationService;
import com.alibaba.nacos.ai.service.VisibilityHelper;
import com.alibaba.nacos.ai.service.mcp.storage.McpServingManifestStorage;
import com.alibaba.nacos.ai.service.mcp.storage.McpVersionStorageContents;
import com.alibaba.nacos.ai.service.mcp.storage.McpVersionStorageKeyComposer;
import com.alibaba.nacos.ai.service.mcp.storage.McpVersionStorageService;
import com.alibaba.nacos.ai.service.search.AiResourceIndexMaintenanceService;
import com.alibaba.nacos.ai.service.trace.AiResourceTraceService;
import com.alibaba.nacos.ai.utils.McpConfigUtils;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.mcp.McpCapability;
import com.alibaba.nacos.api.ai.model.mcp.McpEndpointSpec;
import com.alibaba.nacos.api.ai.model.mcp.McpResourceSpecification;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerRemoteServiceConfig;
import com.alibaba.nacos.api.ai.model.mcp.McpServerVersionInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServiceRef;
import com.alibaba.nacos.api.ai.model.mcp.McpToolSpecification;
import com.alibaba.nacos.api.ai.model.mcp.registry.ServerVersionDetail;
import com.alibaba.nacos.api.ai.utils.AgentValidationUtils;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.exception.runtime.NacosDeserializationException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Complete historical MCP strategy used while lifecycle reconciliation is still running.
 *
 * <p>The historical Manifest remains authoritative in {@link McpCompatibilityMode#SYNCING}, but
 * every Server, Tools, Resources and Manifest Config access is owned by MCP Storage. Successful
 * writes immediately reconcile the affected resource into lifecycle rows and schedule the
 * canonical name-keyed search task. The process-local MCP index is retained only for historical
 * list and identity behavior until the atomic managed cutover.</p>
 *
 * @author Nacos
 */
@Component
public class LegacyMcpOperationService implements McpOperationService {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(LegacyMcpOperationService.class);
    
    private static final DateTimeFormatter RELEASE_TIME_FORMATTER =
        DateTimeFormatter.ofPattern(Constants.RELEASE_DATE_FORMAT);
    
    private final McpServingManifestStorage manifestStorage;
    
    private final McpVersionStorageService versionStorageService;
    
    private final McpEndpointOperationService endpointOperationService;
    
    private final McpServerIndex mcpServerIndex;
    
    private final McpHistoricalResourceReconciler reconciler;
    
    private AiResourceIndexMaintenanceService indexMaintenanceService =
        AiResourceIndexMaintenanceService.NOOP;
    
    public LegacyMcpOperationService(McpServingManifestStorage manifestStorage,
        McpVersionStorageService versionStorageService,
        McpEndpointOperationService endpointOperationService, McpServerIndex mcpServerIndex,
        McpHistoricalResourceReconciler reconciler) {
        this.manifestStorage = manifestStorage;
        this.versionStorageService = versionStorageService;
        this.endpointOperationService = endpointOperationService;
        this.mcpServerIndex = mcpServerIndex;
        this.reconciler = reconciler;
    }
    
    @Autowired(required = false)
    public void setIndexMaintenanceService(
        AiResourceIndexMaintenanceService indexMaintenanceService) {
        if (indexMaintenanceService != null) {
            this.indexMaintenanceService = indexMaintenanceService;
        }
    }
    
    @Override
    public Page<McpServerBasicInfo> listMcpServerWithPage(String namespaceId, String mcpName,
        String search, int pageNo, int pageSize) throws NacosException {
        String normalizedNamespace = normalizeNamespace(namespaceId);
        Page<McpServerIndexData> indexPage = mcpServerIndex.searchMcpServerByNameWithPage(
            normalizedNamespace, mcpName, search, pageNo, pageSize);
        List<McpServerBasicInfo> items = new ArrayList<>();
        if (indexPage != null && indexPage.getPageItems() != null) {
            for (McpServerIndexData index : indexPage.getPageItems()) {
                if (index == null || StringUtils.isBlank(index.getId())) {
                    throw serverError("Historical MCP index returned an invalid row", null);
                }
                McpServerVersionInfo manifest = requireManifest(normalizedNamespace,
                    index.getId());
                manifest.setNamespaceId(normalizedNamespace);
                normalizeManifestLatest(manifest);
                items.add(manifest);
            }
        }
        return copyPage(items, indexPage, pageNo);
    }
    
    @Override
    public McpServerDetailInfo getMcpServerDetail(String namespaceId, String mcpServerId,
        String mcpServerName, String version) throws NacosException {
        String normalizedNamespace = normalizeNamespace(namespaceId);
        LegacyIdentity identity = resolveIdentity(normalizedNamespace, mcpServerName,
            mcpServerId);
        String selectedVersion = StringUtils.isBlank(version)
            ? identity.manifest.getLatestPublishedVersion() : version;
        LoadedVersion loaded = loadVersion(normalizedNamespace, identity.mcpId, selectedVersion);
        if (!identity.name.equals(loaded.server.getName())) {
            throw integrityFailure("Historical MCP Server name does not match its Manifest: "
                + identity.name + '@' + selectedVersion, null);
        }
        McpServerDetailInfo result = new McpServerDetailInfo();
        BeanUtils.copyProperties(loaded.server, result);
        result.setId(identity.mcpId);
        result.setNamespaceId(normalizedNamespace);
        normalizeManifestLatest(identity.manifest);
        result.setAllVersions(identity.manifest.getVersionDetails());
        result.setVersion(selectedVersion);
        ServerVersionDetail selectedDetail = copyVersionDetail(loaded.server.getVersionDetail());
        selectedDetail.setVersion(selectedVersion);
        selectedDetail.setIs_latest(
            selectedVersion.equals(identity.manifest.getLatestPublishedVersion()));
        result.setVersionDetail(selectedDetail);
        injectOptionalContent(loaded, result, identity.name, selectedVersion);
        if (!AiConstants.Mcp.MCP_PROTOCOL_STDIO.equalsIgnoreCase(loaded.server.getProtocol())) {
            endpointOperationService.injectEndpoint(result);
        }
        return result;
    }
    
    @Override
    public String createMcpServer(String namespaceId, McpServerBasicInfo serverSpecification,
        McpToolSpecification toolSpecification,
        McpResourceSpecification resourceSpecification,
        McpEndpointSpec endpointSpecification) throws NacosException {
        String normalizedNamespace = normalizeNamespace(namespaceId);
        VersionIdentity identity = validateSpecification(serverSpecification);
        if (mcpServerIndex.getMcpServerByName(normalizedNamespace, identity.name) != null) {
            throw conflict("mcp server `" + identity.name
                + "` has existed, please update it rather than create.", null);
        }
        String mcpId = resolveCreateMcpId(serverSpecification.getId());
        serverSpecification.setId(mcpId);
        ServerVersionDetail detail = requireVersionDetail(serverSpecification);
        detail.setRelease_date(now());
        PreparedVersion prepared = prepareVersion(normalizedNamespace, mcpId,
            serverSpecification, toolSpecification, resourceSpecification, endpointSpecification,
            false);
        versionStorageService.save(prepared.descriptor, prepared.contents);
        McpServerVersionInfo manifest = buildInitialManifest(prepared.server, detail);
        manifestStorage.publish(normalizedNamespace, manifest);
        afterLegacyWrite(normalizedNamespace, manifest, null);
        trace(identity.name, identity.version, AiResourceTraceService.OP_CREATE_DRAFT);
        return mcpId;
    }
    
    @Override
    public void updateMcpServer(String namespaceId, boolean isPublish,
        McpServerBasicInfo serverSpecification, McpToolSpecification toolSpecification,
        McpResourceSpecification resourceSpecification, McpEndpointSpec endpointSpecification,
        boolean overrideExisting) throws NacosException {
        String normalizedNamespace = normalizeNamespace(namespaceId);
        VersionIdentity requested = validateSpecification(serverSpecification);
        LegacyIdentity identity = resolveIdentity(normalizedNamespace, requested.name,
            serverSpecification.getId());
        serverSpecification.setId(identity.mcpId);
        McpVersionStorageDescriptor oldDescriptor = existingDescriptor(normalizedNamespace,
            identity.mcpId, requested.version);
        PreparedVersion prepared = prepareVersion(normalizedNamespace, identity.mcpId,
            serverSpecification, toolSpecification, resourceSpecification, endpointSpecification,
            overrideExisting);
        versionStorageService.save(prepared.descriptor, prepared.contents);
        if (oldDescriptor != null) {
            versionStorageService.deleteObsolete(oldDescriptor, prepared.descriptor);
        }
        McpServerVersionInfo manifest = identity.manifest;
        List<ServerVersionDetail> details = mutableVersionDetails(manifest);
        ServerVersionDetail target = findVersionDetail(manifest, requested.version);
        if (target == null) {
            target = new ServerVersionDetail();
            target.setVersion(requested.version);
            details.add(target);
        }
        if (isPublish) {
            target.setRelease_date(now());
            applyPublishedPresentation(manifest, prepared.server, target, details);
        }
        manifest.setVersions(details);
        manifestStorage.publish(normalizedNamespace, manifest);
        afterLegacyWrite(normalizedNamespace, manifest, identity.name);
        trace(requested.name, requested.version,
            isPublish ? AiResourceTraceService.OP_PUBLISH
                : AiResourceTraceService.OP_UPDATE_DRAFT);
    }
    
    @Override
    public void deleteMcpServer(String namespaceId, String mcpName, String mcpServerId,
        String version) throws NacosException {
        String normalizedNamespace = normalizeNamespace(namespaceId);
        LegacyIdentity identity = resolveIdentity(normalizedNamespace, mcpName, mcpServerId);
        List<String> deletingVersions = deletingVersions(identity.manifest, version);
        for (String deletingVersion : deletingVersions) {
            endpointOperationService.deleteMcpServerEndpointService(normalizedNamespace,
                identity.name + "::" + deletingVersion);
            versionStorageService.delete(McpVersionStorageKeyComposer.compose(normalizedNamespace,
                identity.mcpId, deletingVersion, true, true));
        }
        Set<String> deletingSet = new HashSet<>(deletingVersions);
        List<ServerVersionDetail> remaining = mutableVersionDetails(identity.manifest);
        remaining.removeIf(each -> each != null && deletingSet.contains(each.getVersion()));
        McpServerVersionInfo remainingManifest = null;
        if (remaining.isEmpty()) {
            manifestStorage.delete(normalizedNamespace, identity.mcpId);
        } else {
            identity.manifest.setVersions(remaining);
            electLatest(identity.manifest);
            manifestStorage.publish(normalizedNamespace, identity.manifest);
            remainingManifest = identity.manifest;
        }
        invalidateIndex(normalizedNamespace, identity.name, identity.mcpId);
        reconcileAfterLegacyDelete(normalizedNamespace, identity, remainingManifest);
        scheduleIndex(normalizedNamespace, identity.name);
        trace(identity.name, version, StringUtils.isBlank(version)
            ? AiResourceTraceService.OP_DELETE_RESOURCE
            : AiResourceTraceService.OP_DELETE_VERSION);
    }
    
    private PreparedVersion prepareVersion(String namespaceId, String mcpId,
        McpServerBasicInfo serverSpecification, McpToolSpecification toolSpecification,
        McpResourceSpecification resourceSpecification, McpEndpointSpec endpointSpecification,
        boolean overrideExisting) throws NacosException {
        McpServerStorageInfo server = new McpServerStorageInfo();
        BeanUtils.copyProperties(serverSpecification, server);
        server.setId(mcpId);
        ServerVersionDetail versionDetail = copyVersionDetail(
            requireVersionDetail(serverSpecification));
        server.setVersionDetail(versionDetail);
        server.setVersion(versionDetail.getVersion());
        boolean hasTools = hasToolSpecification(toolSpecification);
        boolean hasResources = hasResourceSpecification(resourceSpecification);
        List<McpCapability> capabilities = new LinkedList<>();
        if (hasTools) {
            capabilities.add(McpCapability.TOOL);
            server.setToolsDescriptionRef(
                McpConfigUtils.formatServerToolSpecDataId(mcpId, versionDetail.getVersion()));
        }
        if (hasResources) {
            capabilities.add(McpCapability.RESOURCE);
            server.setResourceDescriptionRef(McpConfigUtils.formatServerResourceSpecDataId(mcpId,
                versionDetail.getVersion()));
        }
        server.setCapabilities(capabilities);
        if (endpointSpecification != null) {
            com.alibaba.nacos.naming.core.v2.pojo.Service endpointService =
                endpointOperationService.createMcpServerEndpointServiceIfNecessary(namespaceId,
                    server.getName(), versionDetail.getVersion(), endpointSpecification,
                    overrideExisting);
            McpServiceRef serviceRef = new McpServiceRef();
            serviceRef.setNamespaceId(endpointService.getNamespace());
            serviceRef.setGroupName(endpointService.getGroup());
            serviceRef.setServiceName(endpointService.getName());
            serviceRef.setTransportProtocol(endpointSpecification.getData()
                .get(Constants.MCP_BACKEND_INSTANCE_PROTOCOL_KEY));
            McpServerRemoteServiceConfig remote = server.getRemoteServerConfig();
            if (remote == null) {
                remote = new McpServerRemoteServiceConfig();
                server.setRemoteServerConfig(remote);
            }
            remote.setServiceRef(serviceRef);
        }
        McpVersionStorageDescriptor descriptor = McpVersionStorageKeyComposer.compose(namespaceId,
            mcpId, versionDetail.getVersion(), hasTools, hasResources);
        return new PreparedVersion(server, descriptor,
            new McpVersionStorageContents(jsonBytes(server),
                hasTools ? jsonBytes(toolSpecification) : null,
                hasResources ? jsonBytes(resourceSpecification) : null));
    }
    
    private McpVersionStorageDescriptor existingDescriptor(String namespaceId, String mcpId,
        String version) throws NacosException {
        McpVersionStorageDescriptor serverDescriptor = McpVersionStorageKeyComposer.compose(
            namespaceId, mcpId, version, false, false);
        McpVersionStorageContents serverOnly = versionStorageService.loadIfPresent(
            serverDescriptor);
        if (serverOnly == null) {
            return null;
        }
        McpServerStorageInfo server = decode(serverOnly.getServerContent(),
            McpServerStorageInfo.class, "Server", mcpId, version);
        return McpVersionStorageKeyComposer.fromLegacy(namespaceId, mcpId, version, server);
    }
    
    private LoadedVersion loadVersion(String namespaceId, String mcpId, String version)
        throws NacosException {
        if (StringUtils.isBlank(version)) {
            throw versionNotFound(mcpId, version);
        }
        McpVersionStorageDescriptor serverDescriptor = McpVersionStorageKeyComposer.compose(
            namespaceId, mcpId, version, false, false);
        McpVersionStorageContents serverOnly = versionStorageService.loadIfPresent(
            serverDescriptor);
        if (serverOnly == null) {
            throw versionNotFound(mcpId, version);
        }
        McpServerStorageInfo server = decode(serverOnly.getServerContent(),
            McpServerStorageInfo.class, "Server", mcpId, version);
        String contentVersion = server.getVersionDetail() == null ? server.getVersion()
            : server.getVersionDetail().getVersion();
        if (!mcpId.equals(server.getId()) || !version.equals(contentVersion)) {
            throw integrityFailure("Historical MCP Server identity is invalid: " + mcpId + '@'
                + version, null);
        }
        McpVersionStorageDescriptor descriptor = McpVersionStorageKeyComposer.fromLegacy(
            namespaceId, mcpId, version, server);
        McpVersionStorageContents contents = versionStorageService.load(descriptor);
        return new LoadedVersion(server, contents);
    }
    
    private void injectOptionalContent(LoadedVersion loaded, McpServerDetailInfo result,
        String name, String version) throws NacosException {
        if (loaded.contents.getToolContent() != null) {
            result.setToolSpec(decode(loaded.contents.getToolContent(),
                McpToolSpecification.class, "Tools", name, version));
        }
        if (loaded.contents.getResourceContent() != null) {
            result.setResourceSpec(decode(loaded.contents.getResourceContent(),
                McpResourceSpecification.class, "Resources", name, version));
        }
    }
    
    private LegacyIdentity resolveIdentity(String namespaceId, String name, String mcpId)
        throws NacosException {
        String resolvedId = mcpId;
        if (StringUtils.isBlank(resolvedId)) {
            McpServerIndexData index = mcpServerIndex.getMcpServerByName(namespaceId, name);
            resolvedId = index == null ? null : index.getId();
        }
        if (StringUtils.isBlank(resolvedId)) {
            throw notFound("MCP server not found: " + name);
        }
        McpServerVersionInfo manifest = requireManifest(namespaceId, resolvedId);
        if (StringUtils.isNotBlank(name) && !name.equals(manifest.getName())) {
            throw conflict("MCP name and compatibility id identify different servers", null);
        }
        return new LegacyIdentity(manifest.getName(), resolvedId, manifest);
    }
    
    private McpServerVersionInfo requireManifest(String namespaceId, String mcpId)
        throws NacosException {
        McpServerVersionInfo result = manifestStorage.get(namespaceId, mcpId);
        if (result == null) {
            throw notFound("MCP server not found: " + mcpId);
        }
        return result;
    }
    
    private void afterLegacyWrite(String namespaceId, McpServerVersionInfo manifest,
        String previousName) {
        if (StringUtils.isNotBlank(previousName)
            && !Objects.equals(previousName, manifest.getName())) {
            mcpServerIndex.removeMcpServerByName(namespaceId, previousName);
        }
        mcpServerIndex.removeMcpServerByName(namespaceId, manifest.getName());
        mcpServerIndex.removeMcpServerById(manifest.getId());
        try {
            reconciler.reconcile(namespaceId, manifest);
        } catch (Exception e) {
            LOGGER.warn("Failed to reconcile historical MCP write into lifecycle rows: {}:{}",
                namespaceId, manifest.getName(), e);
        }
        scheduleIndex(namespaceId, manifest.getName());
    }
    
    private void reconcileAfterLegacyDelete(String namespaceId, LegacyIdentity identity,
        McpServerVersionInfo remainingManifest) {
        try {
            reconciler.reconcileAfterLegacyDelete(namespaceId, identity.name, identity.mcpId,
                remainingManifest);
        } catch (Exception e) {
            LOGGER.warn("Failed to reconcile historical MCP delete into lifecycle rows: {}:{}",
                namespaceId, identity.name, e);
        }
    }
    
    private void invalidateIndex(String namespaceId, String name, String mcpId) {
        if (StringUtils.isNotBlank(name)) {
            mcpServerIndex.removeMcpServerByName(namespaceId, name);
        }
        if (StringUtils.isNotBlank(mcpId)) {
            mcpServerIndex.removeMcpServerById(mcpId);
        }
    }
    
    private McpServerVersionInfo buildInitialManifest(McpServerStorageInfo server,
        ServerVersionDetail detail) {
        McpServerVersionInfo result = new McpServerVersionInfo();
        result.setName(server.getName());
        result.setId(server.getId());
        result.setDescription(server.getDescription());
        result.setRepository(server.getRepository());
        result.setFrontProtocol(server.getFrontProtocol());
        result.setProtocol(server.getProtocol());
        result.setCapabilities(server.getCapabilities());
        result.setEnabled(server.isEnabled());
        result.setStatus(server.getStatus());
        result.setLatestPublishedVersion(detail.getVersion());
        result.setVersions(Collections.singletonList(copyVersionDetail(detail)));
        return result;
    }
    
    private void applyPublishedPresentation(McpServerVersionInfo manifest,
        McpServerStorageInfo server, ServerVersionDetail target,
        List<ServerVersionDetail> details) {
        manifest.setName(server.getName());
        manifest.setDescription(server.getDescription());
        manifest.setRepository(server.getRepository());
        manifest.setProtocol(server.getProtocol());
        manifest.setFrontProtocol(server.getFrontProtocol());
        manifest.setCapabilities(server.getCapabilities());
        manifest.setEnabled(server.isEnabled());
        manifest.setStatus(server.getStatus());
        manifest.setLatestPublishedVersion(target.getVersion());
        for (ServerVersionDetail detail : details) {
            if (detail != null) {
                detail.setIs_latest(target.getVersion().equals(detail.getVersion()));
            }
        }
    }
    
    private void electLatest(McpServerVersionInfo manifest) {
        List<ServerVersionDetail> details = mutableVersionDetails(manifest);
        ServerVersionDetail latest = findVersionDetail(manifest,
            manifest.getLatestPublishedVersion());
        if (latest == null && !details.isEmpty()) {
            latest = details.get(details.size() - 1);
            manifest.setLatestPublishedVersion(latest.getVersion());
        }
        for (ServerVersionDetail detail : details) {
            detail.setIs_latest(latest != null && detail.getVersion().equals(latest.getVersion()));
        }
        manifest.setVersion(latest == null ? null : latest.getVersion());
        manifest.setVersionDetail(latest);
        manifest.setVersions(details);
    }
    
    private void normalizeManifestLatest(McpServerVersionInfo manifest) {
        electLatest(manifest);
    }
    
    private List<String> deletingVersions(McpServerVersionInfo manifest, String version) {
        if (StringUtils.isNotBlank(version)) {
            if (findVersionDetail(manifest, version) == null) {
                return Collections.emptyList();
            }
            return Collections.singletonList(version);
        }
        List<String> result = new ArrayList<>();
        for (ServerVersionDetail detail : mutableVersionDetails(manifest)) {
            if (detail != null && StringUtils.isNotBlank(detail.getVersion())) {
                result.add(detail.getVersion());
            }
        }
        return result;
    }
    
    private List<ServerVersionDetail> mutableVersionDetails(McpServerVersionInfo manifest) {
        return manifest.getVersionDetails() == null ? new ArrayList<>()
            : new ArrayList<>(manifest.getVersionDetails());
    }
    
    private ServerVersionDetail findVersionDetail(McpServerVersionInfo manifest, String version) {
        if (manifest.getVersionDetails() == null) {
            return null;
        }
        for (ServerVersionDetail detail : manifest.getVersionDetails()) {
            if (detail != null && Objects.equals(version, detail.getVersion())) {
                return detail;
            }
        }
        return null;
    }
    
    private VersionIdentity validateSpecification(McpServerBasicInfo specification)
        throws NacosException {
        if (specification == null || StringUtils.isBlank(specification.getName())) {
            throw invalidParameter("MCP server specification and name must not be blank", null);
        }
        ServerVersionDetail detail = requireVersionDetail(specification);
        return new VersionIdentity(specification.getName(), detail.getVersion());
    }
    
    private ServerVersionDetail requireVersionDetail(McpServerBasicInfo specification)
        throws NacosException {
        ServerVersionDetail result = specification.getVersionDetail();
        if (result == null && StringUtils.isNotBlank(specification.getVersion())) {
            result = new ServerVersionDetail();
            result.setVersion(specification.getVersion());
            specification.setVersionDetail(result);
        }
        if (result == null || StringUtils.isBlank(result.getVersion())) {
            throw invalidParameter("Version must be specified in serverSpecification", null);
        }
        return result;
    }
    
    private String resolveCreateMcpId(String requestedId) throws NacosException {
        String result = StringUtils.isBlank(requestedId) ? UUID.randomUUID().toString()
            : requestedId;
        if (!StringUtils.isUuidString(result)) {
            throw invalidParameter("serverSpecification.id must be a UUID", null);
        }
        if (mcpServerIndex.getMcpServerById(result) != null) {
            throw conflict("MCP compatibility id already exists: " + result, null);
        }
        return result;
    }
    
    private String normalizeNamespace(String namespaceId) throws NacosException {
        String result = StringUtils.isBlank(namespaceId) ? AiConstants.Mcp.MCP_DEFAULT_NAMESPACE
            : namespaceId;
        try {
            AgentValidationUtils.validateNamespaceId(result);
            return result;
        } catch (IllegalArgumentException e) {
            throw invalidParameter("Invalid MCP namespaceId: " + namespaceId, e);
        }
    }
    
    private boolean hasToolSpecification(McpToolSpecification specification) {
        return specification != null && (specification.getTools() != null
            || specification.getSecuritySchemes() != null
            || specification.getEncryptData() != null);
    }
    
    private boolean hasResourceSpecification(McpResourceSpecification specification) {
        return specification != null && (CollectionUtils.isNotEmpty(specification.getResources())
            || CollectionUtils.isNotEmpty(specification.getResourceTemplates())
            || specification.getEncryptData() != null);
    }
    
    private byte[] jsonBytes(Object value) {
        return JacksonUtils.toJson(value).getBytes(StandardCharsets.UTF_8);
    }
    
    private <T> T decode(byte[] content, Class<T> type, String contentName, String identity,
        String version) throws NacosException {
        try {
            T result = JacksonUtils.toObj(new String(content, StandardCharsets.UTF_8), type);
            if (result == null) {
                throw new IllegalArgumentException(contentName + " content is null");
            }
            return result;
        } catch (NacosDeserializationException | IllegalArgumentException e) {
            throw integrityFailure("Historical MCP " + contentName + " content is invalid for "
                + identity + ':' + version, e);
        }
    }
    
    private ServerVersionDetail copyVersionDetail(ServerVersionDetail source) {
        ServerVersionDetail result = new ServerVersionDetail();
        if (source != null) {
            result.setVersion(source.getVersion());
            result.setRelease_date(source.getRelease_date());
            result.setIs_latest(source.getIs_latest());
        }
        return result;
    }
    
    private <T> Page<T> copyPage(List<T> items, Page<?> source, int pageNo) {
        Page<T> result = new Page<>();
        result.setPageItems(items);
        result.setTotalCount(source == null ? 0 : source.getTotalCount());
        result.setPagesAvailable(source == null ? 0 : source.getPagesAvailable());
        result.setPageNumber(pageNo);
        return result;
    }
    
    private void scheduleIndex(String namespaceId, String name) {
        indexMaintenanceService.schedule(namespaceId, AiResourceConstants.RESOURCE_TYPE_MCP,
            name);
    }
    
    private void trace(String name, String version, String operation) {
        AiResourceTraceService.logSuccess(AiResourceConstants.RESOURCE_TYPE_MCP, name, version,
            operation, VisibilityHelper.resolveCurrentIdentity(),
            VisibilityHelper.resolveClientIp());
    }
    
    private String now() {
        return ZonedDateTime.now(ZoneOffset.UTC).format(RELEASE_TIME_FORMATTER);
    }
    
    private NacosApiException versionNotFound(String mcpId, String version) {
        return new NacosApiException(NacosException.NOT_FOUND,
            ErrorCode.MCP_SEVER_VERSION_NOT_FOUND,
            String.format("mcp server `%s` for version `%s` not found", mcpId, version));
    }
    
    private NacosApiException notFound(String message) {
        return new NacosApiException(NacosException.NOT_FOUND, ErrorCode.MCP_SERVER_NOT_FOUND,
            message);
    }
    
    private NacosApiException invalidParameter(String message, Throwable cause) {
        return cause == null
            ? new NacosApiException(NacosException.INVALID_PARAM,
                ErrorCode.PARAMETER_VALIDATE_ERROR, message)
            : new NacosApiException(NacosException.INVALID_PARAM,
                ErrorCode.PARAMETER_VALIDATE_ERROR, cause, message);
    }
    
    private NacosApiException conflict(String message, Throwable cause) {
        return cause == null
            ? new NacosApiException(NacosException.CONFLICT, ErrorCode.RESOURCE_CONFLICT, message)
            : new NacosApiException(NacosException.CONFLICT, ErrorCode.RESOURCE_CONFLICT, cause,
                message);
    }
    
    private NacosApiException integrityFailure(String message, Throwable cause) {
        return conflict(message, cause);
    }
    
    private NacosApiException serverError(String message, Throwable cause) {
        return cause == null
            ? new NacosApiException(NacosException.SERVER_ERROR, ErrorCode.SERVER_ERROR, message)
            : new NacosApiException(NacosException.SERVER_ERROR, ErrorCode.SERVER_ERROR, cause,
                message);
    }
    
    private static final class VersionIdentity {
        
        private final String name;
        
        private final String version;
        
        private VersionIdentity(String name, String version) {
            this.name = name;
            this.version = version;
        }
    }
    
    private static final class LegacyIdentity {
        
        private final String name;
        
        private final String mcpId;
        
        private final McpServerVersionInfo manifest;
        
        private LegacyIdentity(String name, String mcpId, McpServerVersionInfo manifest) {
            this.name = name;
            this.mcpId = mcpId;
            this.manifest = manifest;
        }
    }
    
    private static final class PreparedVersion {
        
        private final McpServerStorageInfo server;
        
        private final McpVersionStorageDescriptor descriptor;
        
        private final McpVersionStorageContents contents;
        
        private PreparedVersion(McpServerStorageInfo server,
            McpVersionStorageDescriptor descriptor, McpVersionStorageContents contents) {
            this.server = server;
            this.descriptor = descriptor;
            this.contents = contents;
        }
    }
    
    private static final class LoadedVersion {
        
        private final McpServerStorageInfo server;
        
        private final McpVersionStorageContents contents;
        
        private LoadedVersion(McpServerStorageInfo server, McpVersionStorageContents contents) {
            this.server = server;
            this.contents = contents;
        }
    }
}
