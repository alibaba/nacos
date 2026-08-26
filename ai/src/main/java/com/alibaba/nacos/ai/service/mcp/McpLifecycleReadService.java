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
import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.model.AiResourceVersion;
import com.alibaba.nacos.ai.model.mcp.McpResourceExt;
import com.alibaba.nacos.ai.model.mcp.McpServerStorageInfo;
import com.alibaba.nacos.ai.model.mcp.McpVersionStorageDescriptor;
import com.alibaba.nacos.ai.service.McpEndpointReadService;
import com.alibaba.nacos.ai.service.mcp.storage.McpResourceExtSerializer;
import com.alibaba.nacos.ai.service.mcp.storage.McpServingManifestStorage;
import com.alibaba.nacos.ai.service.mcp.storage.McpVersionStorageContents;
import com.alibaba.nacos.ai.service.mcp.storage.McpVersionStorageDescriptorSerializer;
import com.alibaba.nacos.ai.service.mcp.storage.McpVersionStorageService;
import com.alibaba.nacos.ai.service.repository.QueryCondition;
import com.alibaba.nacos.ai.service.resource.AiResourceManager;
import com.alibaba.nacos.ai.service.resource.ResourceVersionInfo;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.mcp.McpResourceSpecification;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerVersionInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpToolSpecification;
import com.alibaba.nacos.api.ai.model.mcp.registry.ServerVersionDetail;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.exception.runtime.NacosDeserializationException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.visibility.constant.VisibilityConstants;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Reads legacy MCP management DTOs from canonical lifecycle rows and Version descriptors.
 *
 * <p>The AI Resource name is the canonical identity. The serving Manifest is read only as the
 * compatibility projection for fields such as historical release dates; it is never used to
 * resolve a name or deprecated ID. Server, Tools and Resources content is loaded exclusively
 * through the descriptor stored in {@code ai_resource_version.storage}.</p>
 *
 * @author Nacos
 */
@Service
public class McpLifecycleReadService {
    
    private static final int VERSION_PAGE_SIZE = 100;
    
    private final McpResourceLocator resourceLocator;
    
    private final AiResourceManager resourceManager;
    
    private final McpVersionStorageService versionStorageService;
    
    private final McpServingManifestStorage manifestStorage;
    
    private final McpEndpointReadService endpointReadService;
    
    private final McpCanonicalAuthorizationService authorizationService;
    
    public McpLifecycleReadService(McpResourceLocator resourceLocator,
        AiResourceManager resourceManager, McpVersionStorageService versionStorageService,
        McpServingManifestStorage manifestStorage, McpEndpointReadService endpointReadService,
        McpCanonicalAuthorizationService authorizationService) {
        this.resourceLocator = resourceLocator;
        this.resourceManager = resourceManager;
        this.versionStorageService = versionStorageService;
        this.manifestStorage = manifestStorage;
        this.endpointReadService = endpointReadService;
        this.authorizationService = authorizationService;
    }
    
    /**
     * List visible MCP resources using the canonical Resource page and legacy response shape.
     *
     * @param namespaceId namespace identifier
     * @param mcpName optional exact or fuzzy name text
     * @param search {@code accurate} or {@code blur}
     * @param pageNo one-based page number
     * @param pageSize page size
     * @return compatible MCP basic-info page
     * @throws NacosException when lifecycle data or its compatibility projection is inconsistent
     */
    public Page<McpServerBasicInfo> listMcpServers(String namespaceId, String mcpName,
        String search, int pageNo, int pageSize) throws NacosException {
        String nameLike = buildNameLike(mcpName, search);
        QueryCondition condition = resourceManager.buildQueryCondition(namespaceId,
            AiResourceConstants.RESOURCE_TYPE_MCP, nameLike, null,
            VisibilityConstants.ACTION_READ);
        if (condition.isAlwaysEmpty()) {
            return AiResourceManager.buildEmptyPage(pageNo);
        }
        Page<AiResource> resourcePage = resourceManager.listMeta(condition, pageNo, pageSize);
        List<McpServerBasicInfo> items = new ArrayList<>();
        if (resourcePage != null && resourcePage.getPageItems() != null) {
            for (AiResource resource : resourcePage.getPageItems()) {
                items.add(toBasicInfo(requireLifecycleResource(resource)));
            }
        }
        return AiResourceManager.buildPageResult(items, resourcePage, pageNo);
    }
    
    /**
     * Get one MCP detail by canonical name, deprecated ID, or a matching pair.
     *
     * @param namespaceId namespace identifier
     * @param mcpName canonical MCP name, optional for deprecated ID-only requests
     * @param mcpId deprecated compatibility alias, optional when the name is present
     * @param version explicit Version; blank selects the latest label
     * @return compatible MCP detail
     * @throws NacosException when absent, unreadable, or lifecycle data is inconsistent
     */
    public McpServerDetailInfo getMcpServer(String namespaceId, String mcpName, String mcpId,
        String version) throws NacosException {
        AiResource resource = resourceLocator.locate(namespaceId, mcpName, mcpId);
        authorizationService.authorizeRead(resource.getNamespaceId(), resource.getName());
        resourceManager.ensureReadableOrNotFound(resource,
            "MCP server not found: " + resource.getName());
        LifecycleResource lifecycle = requireLifecycleResource(resource);
        String selectedVersion = StringUtils.isBlank(version) ? lifecycle.latestVersion : version;
        AiResourceVersion versionRow = resourceManager.findVersion(resource.getNamespaceId(),
            resource.getName(), AiResourceConstants.RESOURCE_TYPE_MCP, selectedVersion);
        if (versionRow == null) {
            throw versionNotFound(lifecycle.mcpId, selectedVersion);
        }
        McpVersionStorageContents contents = loadContents(versionRow);
        McpServerStorageInfo server = decodeServer(lifecycle, versionRow, contents);
        McpServerDetailInfo result = new McpServerDetailInfo();
        result.setId(lifecycle.mcpId);
        BeanUtils.copyProperties(server, result);
        result.setNamespaceId(resource.getNamespaceId());
        result.setAllVersions(decorateVersionDetails(lifecycle.manifest,
            lifecycle.latestVersion));
        ServerVersionDetail selectedDetail = result.getVersionDetail();
        selectedDetail.setIs_latest(selectedVersion.equals(lifecycle.latestVersion));
        result.setVersion(selectedVersion);
        injectOptionalContent(server, contents, result, lifecycle, selectedVersion);
        if (!AiConstants.Mcp.MCP_PROTOCOL_STDIO.equalsIgnoreCase(server.getProtocol())) {
            endpointReadService.injectEndpoint(result);
        }
        return result;
    }
    
    private String buildNameLike(String mcpName, String search) {
        if (StringUtils.isBlank(mcpName)) {
            return null;
        }
        if (Constants.MCP_LIST_SEARCH_ACCURATE.equalsIgnoreCase(search)) {
            return mcpName;
        }
        return resourceManager.generateLikeArgument(
            Constants.ALL_PATTERN + mcpName + Constants.ALL_PATTERN);
    }
    
    private LifecycleResource requireLifecycleResource(AiResource resource)
        throws NacosException {
        if (resource == null || StringUtils.isBlank(resource.getNamespaceId())
            || StringUtils.isBlank(resource.getName())
            || !AiResourceConstants.RESOURCE_TYPE_MCP.equals(resource.getType())) {
            throw integrityFailure("MCP Resource query returned an inconsistent row", null);
        }
        McpResourceExt ext;
        try {
            ext = McpResourceExtSerializer.deserialize(resource.getExt());
        } catch (IllegalArgumentException e) {
            throw integrityFailure("MCP Resource " + resource.getName()
                + " has invalid compatibility identity", e);
        }
        McpServerVersionInfo manifest = manifestStorage.get(resource.getNamespaceId(),
            ext.getMcpId());
        if (manifest == null) {
            throw integrityFailure("MCP Resource " + resource.getName()
                + " has no serving Manifest", null);
        }
        if (!resource.getName().equals(manifest.getName())
            || !ext.getMcpId().equals(manifest.getId())) {
            throw integrityFailure("MCP Resource and serving Manifest identity do not match for "
                + resource.getName(), null);
        }
        ResourceVersionInfo versionInfo = AiResourceManager.parseVersionInfo(
            resource.getVersionInfo());
        Map<String, String> labels = versionInfo == null ? null : versionInfo.getLabels();
        String latestVersion = labels == null ? null : labels.get(AiResourceConstants.LABEL_LATEST);
        if (StringUtils.isBlank(latestVersion)
            || !latestVersion.equals(manifest.getLatestPublishedVersion())) {
            throw integrityFailure(
                "MCP Resource latest label does not match its serving Manifest for "
                    + resource.getName(),
                null);
        }
        validateEnabled(resource, manifest);
        int onlineCount = validateOnlineVersions(resource, manifest);
        if (versionInfo.getOnlineCnt() == null || versionInfo.getOnlineCnt() != onlineCount) {
            throw integrityFailure("MCP Resource online count does not match Version rows for "
                + resource.getName(), null);
        }
        return new LifecycleResource(resource, ext.getMcpId(), latestVersion, manifest);
    }
    
    private void validateEnabled(AiResource resource, McpServerVersionInfo manifest)
        throws NacosApiException {
        boolean enabled = AiResourceConstants.META_STATUS_ENABLE.equalsIgnoreCase(
            resource.getStatus());
        boolean disabled = AiResourceConstants.META_STATUS_DISABLE.equalsIgnoreCase(
            resource.getStatus());
        if ((!enabled && !disabled) || enabled != manifest.isEnabled()) {
            throw integrityFailure("MCP Resource status does not match its serving Manifest for "
                + resource.getName(), null);
        }
    }
    
    private int validateOnlineVersions(AiResource resource, McpServerVersionInfo manifest)
        throws NacosException {
        Set<String> expected = manifestVersions(manifest, resource.getName());
        Set<String> actual = new HashSet<>();
        int pageNo = 1;
        int pagesAvailable = 1;
        while (pageNo <= pagesAvailable) {
            Page<AiResourceVersion> page = resourceManager.listVersions(resource.getNamespaceId(),
                resource.getName(), AiResourceConstants.RESOURCE_TYPE_MCP,
                AiResourceConstants.VERSION_STATUS_ONLINE, pageNo, VERSION_PAGE_SIZE);
            if (page == null || page.getPageItems() == null) {
                throw integrityFailure("Unable to page MCP Version rows for "
                    + resource.getName(), null);
            }
            pagesAvailable = resolvePages(page, pageNo);
            for (AiResourceVersion version : page.getPageItems()) {
                validateVersionRow(resource, version);
                if (!actual.add(version.getVersion())) {
                    throw integrityFailure("Duplicate MCP Version row for " + resource.getName()
                        + ':' + version.getVersion(), null);
                }
            }
            pageNo++;
        }
        if (!expected.equals(actual)) {
            throw integrityFailure("MCP Version rows do not match the serving Manifest for "
                + resource.getName(), null);
        }
        return actual.size();
    }
    
    private Set<String> manifestVersions(McpServerVersionInfo manifest, String resourceName)
        throws NacosApiException {
        if (manifest.getVersionDetails() == null || manifest.getVersionDetails().isEmpty()) {
            throw integrityFailure("MCP serving Manifest has no Versions for " + resourceName,
                null);
        }
        Set<String> result = new HashSet<>();
        for (ServerVersionDetail detail : manifest.getVersionDetails()) {
            if (detail == null || StringUtils.isBlank(detail.getVersion())
                || !result.add(detail.getVersion())) {
                throw integrityFailure("MCP serving Manifest has invalid Versions for "
                    + resourceName, null);
            }
        }
        return result;
    }
    
    private void validateVersionRow(AiResource resource, AiResourceVersion version)
        throws NacosApiException {
        if (version == null || !resource.getNamespaceId().equals(version.getNamespaceId())
            || !resource.getName().equals(version.getName())
            || !AiResourceConstants.RESOURCE_TYPE_MCP.equals(version.getType())
            || StringUtils.isBlank(version.getVersion())) {
            throw integrityFailure("MCP Version query returned an inconsistent row for "
                + resource.getName(), null);
        }
    }
    
    private int resolvePages(Page<?> page, int pageNo) {
        if (page.getPagesAvailable() > 0) {
            return Math.max(pageNo, page.getPagesAvailable());
        }
        int calculated = (page.getTotalCount() + VERSION_PAGE_SIZE - 1) / VERSION_PAGE_SIZE;
        return Math.max(pageNo, calculated);
    }
    
    private McpServerBasicInfo toBasicInfo(LifecycleResource lifecycle) throws NacosException {
        AiResourceVersion latest = resourceManager.findVersion(lifecycle.resource.getNamespaceId(),
            lifecycle.resource.getName(), AiResourceConstants.RESOURCE_TYPE_MCP,
            lifecycle.latestVersion);
        if (latest == null) {
            throw versionNotFound(lifecycle.mcpId, lifecycle.latestVersion);
        }
        loadServer(lifecycle, latest);
        McpServerVersionInfo result = lifecycle.manifest;
        result.setNamespaceId(lifecycle.resource.getNamespaceId());
        result.setEnabled(AiResourceConstants.META_STATUS_ENABLE.equalsIgnoreCase(
            lifecycle.resource.getStatus()));
        result.setDescription(lifecycle.resource.getDesc());
        List<ServerVersionDetail> versions = decorateVersionDetails(result,
            lifecycle.latestVersion);
        result.setVersions(versions);
        result.setVersion(lifecycle.latestVersion);
        for (ServerVersionDetail detail : versions) {
            if (lifecycle.latestVersion.equals(detail.getVersion())) {
                result.setVersionDetail(detail);
                break;
            }
        }
        return result;
    }
    
    private List<ServerVersionDetail> decorateVersionDetails(McpServerVersionInfo manifest,
        String latestVersion) throws NacosApiException {
        manifestVersions(manifest, manifest.getName());
        for (ServerVersionDetail detail : manifest.getVersionDetails()) {
            detail.setIs_latest(latestVersion.equals(detail.getVersion()));
        }
        return manifest.getVersionDetails();
    }
    
    private McpServerStorageInfo loadServer(LifecycleResource lifecycle,
        AiResourceVersion versionRow) throws NacosException {
        validateVersionRow(lifecycle.resource, versionRow);
        McpVersionStorageContents contents = loadContents(versionRow);
        return decodeServer(lifecycle, versionRow, contents);
    }
    
    private McpServerStorageInfo decodeServer(LifecycleResource lifecycle,
        AiResourceVersion versionRow, McpVersionStorageContents contents) throws NacosException {
        validateVersionRow(lifecycle.resource, versionRow);
        McpServerStorageInfo server = decode(contents.getServerContent(),
            McpServerStorageInfo.class, "Server", lifecycle.resource.getName(),
            versionRow.getVersion());
        String detailVersion = server.getVersionDetail() == null ? null
            : server.getVersionDetail().getVersion();
        String actualVersion = StringUtils.isNotBlank(detailVersion) ? detailVersion
            : server.getVersion();
        boolean valid = lifecycle.mcpId.equals(server.getId())
            && lifecycle.resource.getName().equals(server.getName())
            && versionRow.getVersion().equals(actualVersion)
            && (StringUtils.isBlank(server.getNamespaceId())
                || lifecycle.resource.getNamespaceId().equals(server.getNamespaceId()));
        if (!valid || server.getVersionDetail() == null) {
            throw integrityFailure("MCP Server content identity does not match lifecycle Version "
                + lifecycle.resource.getName() + ':' + versionRow.getVersion(), null);
        }
        return server;
    }
    
    private McpVersionStorageContents loadContents(AiResourceVersion versionRow)
        throws NacosException {
        McpVersionStorageDescriptor descriptor;
        try {
            descriptor = McpVersionStorageDescriptorSerializer.deserialize(versionRow.getStorage());
        } catch (IllegalArgumentException e) {
            throw integrityFailure("MCP Version has an invalid storage descriptor for "
                + versionRow.getName() + ':' + versionRow.getVersion(), e);
        }
        return versionStorageService.load(descriptor);
    }
    
    private void injectOptionalContent(McpServerStorageInfo server,
        McpVersionStorageContents contents, McpServerDetailInfo result,
        LifecycleResource lifecycle, String version) throws NacosException {
        boolean expectsTools = server.getToolsDescriptionRef() != null;
        boolean hasTools = contents.getToolContent() != null;
        boolean expectsResources = server.getResourceDescriptionRef() != null;
        boolean hasResources = contents.getResourceContent() != null;
        if (expectsTools != hasTools || expectsResources != hasResources) {
            throw integrityFailure("MCP Version descriptor does not match Server references for "
                + lifecycle.resource.getName() + ':' + version, null);
        }
        if (hasTools) {
            result.setToolSpec(decode(contents.getToolContent(), McpToolSpecification.class,
                "Tools", lifecycle.resource.getName(), version));
        }
        if (hasResources) {
            result.setResourceSpec(decode(contents.getResourceContent(),
                McpResourceSpecification.class, "Resources", lifecycle.resource.getName(),
                version));
        }
    }
    
    private <T> T decode(byte[] content, Class<T> type, String contentName, String resourceName,
        String version) throws NacosApiException {
        try {
            T result = JacksonUtils.toObj(new String(content, StandardCharsets.UTF_8), type);
            if (result == null) {
                throw new IllegalArgumentException(contentName + " content is null");
            }
            return result;
        } catch (NacosDeserializationException | IllegalArgumentException e) {
            throw integrityFailure("MCP " + contentName + " content is invalid for "
                + resourceName + ':' + version, e);
        }
    }
    
    private NacosApiException versionNotFound(String mcpId, String version) {
        return new NacosApiException(NacosException.NOT_FOUND,
            ErrorCode.MCP_SEVER_VERSION_NOT_FOUND,
            String.format("mcp server `%s` for version `%s` not found", mcpId, version));
    }
    
    private NacosApiException integrityFailure(String message, Throwable cause) {
        return cause == null
            ? new NacosApiException(NacosException.CONFLICT, ErrorCode.RESOURCE_CONFLICT, message)
            : new NacosApiException(NacosException.CONFLICT, ErrorCode.RESOURCE_CONFLICT, cause,
                message);
    }
    
    private static final class LifecycleResource {
        
        private final AiResource resource;
        
        private final String mcpId;
        
        private final String latestVersion;
        
        private final McpServerVersionInfo manifest;
        
        private LifecycleResource(AiResource resource, String mcpId, String latestVersion,
            McpServerVersionInfo manifest) {
            this.resource = Objects.requireNonNull(resource);
            this.mcpId = mcpId;
            this.latestVersion = latestVersion;
            this.manifest = manifest;
        }
    }
}
