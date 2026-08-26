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
import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.model.AiResourceVersion;
import com.alibaba.nacos.ai.model.mcp.McpResourceExt;
import com.alibaba.nacos.ai.model.mcp.McpServerStorageInfo;
import com.alibaba.nacos.ai.model.mcp.McpVersionStorageDescriptor;
import com.alibaba.nacos.ai.service.mcp.storage.McpResourceExtSerializer;
import com.alibaba.nacos.ai.service.mcp.storage.McpVersionStorageContents;
import com.alibaba.nacos.ai.service.mcp.storage.McpVersionStorageDescriptorSerializer;
import com.alibaba.nacos.ai.service.mcp.storage.McpVersionStorageKeyComposer;
import com.alibaba.nacos.ai.service.mcp.storage.McpVersionStorageService;
import com.alibaba.nacos.ai.service.repository.AiResourcePersistService;
import com.alibaba.nacos.ai.service.repository.AiResourceVersionPersistService;
import com.alibaba.nacos.ai.service.repository.QueryCondition;
import com.alibaba.nacos.ai.service.resource.ResourceVersionInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpResourceSpecification;
import com.alibaba.nacos.api.ai.model.mcp.McpServerVersionInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpToolSpecification;
import com.alibaba.nacos.api.ai.model.mcp.registry.ServerVersionDetail;
import com.alibaba.nacos.api.ai.utils.AgentValidationUtils;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.runtime.NacosDeserializationException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.visibility.constant.VisibilityConstants;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Reconciles one historical MCP serving Manifest into AI Resource lifecycle rows.
 *
 * <p>Historical content is loaded and validated through MCP Version Storage, then referenced by
 * descriptor. This service never saves payload bytes or mutates Naming. Version rows are made
 * equivalent first and the Resource row is inserted or updated last.</p>
 *
 * @author Nacos
 */
@Service
public class McpHistoricalResourceReconciler {
    
    public static final String LEGACY_SOURCE = "legacy-mcp";
    
    private static final String OWNER_NACOS = "nacos";
    
    private static final String RESOURCE_NAME_FIELD = "name";
    
    private static final int EXACT_RESOURCE_PAGE_SIZE = 2;
    
    private static final int VERSION_PAGE_SIZE = 100;
    
    private static final int MAX_RESOURCE_NAME_LENGTH = 256;
    
    private static final int MAX_RESOURCE_DESCRIPTION_LENGTH = 2048;
    
    private static final int MAX_VERSION_LENGTH = 64;
    
    private final McpVersionStorageService versionStorageService;
    
    private final AiResourcePersistService resourcePersistService;
    
    private final AiResourceVersionPersistService versionPersistService;
    
    public McpHistoricalResourceReconciler(McpVersionStorageService versionStorageService,
        AiResourcePersistService resourcePersistService,
        AiResourceVersionPersistService versionPersistService) {
        this.versionStorageService = versionStorageService;
        this.resourcePersistService = resourcePersistService;
        this.versionPersistService = versionPersistService;
    }
    
    /**
     * Reconcile one historical MCP resource.
     *
     * @param namespaceId namespace identifier
     * @param manifest historical serving Manifest
     * @return number of Resource or Version rows changed; zero means already equivalent
     * @throws NacosException when historical content is invalid, missing, or conflicts with rows
     */
    public int reconcile(String namespaceId, McpServerVersionInfo manifest)
        throws NacosException {
        ManifestIdentity identity = validateManifest(namespaceId, manifest);
        List<AiResourceVersion> expectedVersions = prepareVersions(identity, manifest);
        AiResource expectedResource = buildResource(identity, manifest, expectedVersions.size());
        AiResource existingResource = preflightResource(expectedResource);
        VersionPreflight versionPreflight = preflightVersions(identity, expectedVersions);
        if (!versionPreflight.extraVersions.isEmpty()) {
            throw conflict("Historical MCP Version rows exist outside the Manifest for "
                + identity.name + ": " + versionPreflight.extraVersions, null);
        }
        
        int changes = 0;
        for (AiResourceVersion missing : versionPreflight.missingVersions) {
            changes += insertVersionOrRecover(missing);
        }
        changes += upsertResourceLast(expectedResource, existingResource);
        return changes;
    }
    
    /**
     * Reconcile lifecycle rows after the authoritative historical path deletes Versions.
     *
     * <p>The caller must finish the legacy endpoint, Version Storage and serving Manifest
     * mutations first. This method then removes only rows that are no longer present in the
     * remaining Manifest and reuses normal reconciliation for the retained Versions. A full
     * delete only removes a resource created by historical reconciliation with the same
     * compatibility identity. If a previous Version-first reconciliation failed before inserting
     * its Resource, the full delete also removes those orphan Version rows only after their
     * canonical metadata and Server storage key prove the same legacy identity. Independently
     * created MCP resources and versions are never claimed.</p>
     *
     * @param namespaceId namespace identifier
     * @param name canonical MCP name before deletion
     * @param mcpId historical compatibility identity
     * @param remainingManifest remaining authoritative Manifest, or {@code null} for full delete
     * @return number of Resource or Version rows changed
     * @throws NacosException when retained rows conflict with the historical identity
     */
    public int reconcileAfterLegacyDelete(String namespaceId, String name, String mcpId,
        McpServerVersionInfo remainingManifest) throws NacosException {
        ManifestIdentity identity = validateDeletionIdentity(namespaceId, name, mcpId);
        AiResource existing = uniqueResourceOrNull(namespaceId, name);
        if (existing != null) {
            validateLegacyDeletionResource(existing, identity);
        }
        if (remainingManifest == null) {
            Map<String, AiResourceVersion> versions = loadExistingVersions(identity);
            validateLegacyDeletionVersions(versions, identity);
            if (existing == null && versions.isEmpty()) {
                return 0;
            }
            int changes = existing == null ? 0 : resourcePersistService.delete(namespaceId, name,
                AiResourceConstants.RESOURCE_TYPE_MCP);
            changes += versionPersistService.deleteByNameAndType(namespaceId, name,
                AiResourceConstants.RESOURCE_TYPE_MCP);
            return changes;
        }
        ManifestIdentity remainingIdentity = validateManifest(namespaceId, remainingManifest);
        if (!identity.name.equals(remainingIdentity.name)
            || !identity.mcpId.equals(remainingIdentity.mcpId)) {
            throw conflict("Remaining historical MCP Manifest changed identity for " + name,
                null);
        }
        Set<String> retainedVersions = new HashSet<>();
        for (ServerVersionDetail detail : remainingManifest.getVersionDetails()) {
            retainedVersions.add(detail.getVersion());
        }
        int changes = 0;
        for (AiResourceVersion row : loadExistingVersions(identity).values()) {
            if (!retainedVersions.contains(row.getVersion())) {
                changes += versionPersistService.delete(namespaceId, name,
                    AiResourceConstants.RESOURCE_TYPE_MCP, row.getVersion());
            }
        }
        return changes + reconcile(namespaceId, remainingManifest);
    }
    
    private ManifestIdentity validateDeletionIdentity(String namespaceId, String name,
        String mcpId) throws NacosException {
        try {
            AgentValidationUtils.validateNamespaceId(namespaceId);
            McpResourceExtSerializer.validateMcpId(mcpId);
            if (StringUtils.isBlank(name) || name.length() > MAX_RESOURCE_NAME_LENGTH) {
                throw new IllegalArgumentException("Invalid historical MCP Resource name");
            }
            return new ManifestIdentity(namespaceId, name, mcpId);
        } catch (IllegalArgumentException e) {
            throw conflict("Invalid deleted historical MCP identity", e);
        }
    }
    
    private void validateLegacyDeletionResource(AiResource resource,
        ManifestIdentity identity) throws NacosException {
        boolean matches = identity.namespaceId.equals(resource.getNamespaceId())
            && identity.name.equals(resource.getName())
            && AiResourceConstants.RESOURCE_TYPE_MCP.equals(resource.getType())
            && LEGACY_SOURCE.equals(resource.getFrom());
        if (matches) {
            try {
                matches = identity.mcpId.equals(
                    McpResourceExtSerializer.deserialize(resource.getExt()).getMcpId());
            } catch (IllegalArgumentException e) {
                throw conflict("Historical MCP Resource has invalid compatibility identity for "
                    + identity.name, e);
            }
        }
        if (!matches) {
            throw conflict("Historical MCP delete conflicts with an existing Resource for "
                + identity.name, null);
        }
    }
    
    private void validateLegacyDeletionVersions(Map<String, AiResourceVersion> versions,
        ManifestIdentity identity) throws NacosException {
        for (AiResourceVersion version : versions.values()) {
            boolean matches = AiResourceConstants.VERSION_STATUS_ONLINE.equals(
                version.getStatus()) && OWNER_NACOS.equals(version.getAuthor())
                && StringUtils.isBlank(version.getDesc())
                && StringUtils.isBlank(version.getPublishPipelineInfo());
            try {
                McpVersionStorageDescriptor actual =
                    McpVersionStorageDescriptorSerializer.deserialize(version.getStorage());
                McpVersionStorageDescriptor expected = McpVersionStorageKeyComposer.compose(
                    identity.namespaceId, identity.mcpId, version.getVersion(), false, false);
                matches = matches && expected.getServerKey().equals(actual.getServerKey());
            } catch (IllegalArgumentException e) {
                throw conflict("Historical MCP Version has invalid storage identity for "
                    + identity.name + ':' + version.getVersion(), e);
            }
            if (!matches) {
                throw conflict("Historical MCP delete conflicts with an existing Version for "
                    + identity.name + ':' + version.getVersion(), null);
            }
        }
    }
    
    private ManifestIdentity validateManifest(String namespaceId, McpServerVersionInfo manifest)
        throws NacosException {
        try {
            AgentValidationUtils.validateNamespaceId(namespaceId);
            if (manifest == null) {
                throw new IllegalArgumentException("MCP serving Manifest must not be null");
            }
            McpResourceExtSerializer.validateMcpId(manifest.getId());
            if (StringUtils.isBlank(manifest.getName())
                || manifest.getName().length() > MAX_RESOURCE_NAME_LENGTH) {
                throw new IllegalArgumentException("Invalid historical MCP Resource name");
            }
            if (manifest.getDescription() != null
                && manifest.getDescription().length() > MAX_RESOURCE_DESCRIPTION_LENGTH) {
                throw new IllegalArgumentException("Historical MCP description is too long");
            }
            if (manifest.getVersionDetails() == null
                || manifest.getVersionDetails().isEmpty()) {
                throw new IllegalArgumentException("Historical MCP Manifest has no Versions");
            }
            Set<String> versions = new HashSet<>();
            for (ServerVersionDetail detail : manifest.getVersionDetails()) {
                String version = detail == null ? null : detail.getVersion();
                validateVersion(version);
                if (!versions.add(version)) {
                    throw new IllegalArgumentException(
                        "Historical MCP Manifest has duplicate Version " + version);
                }
            }
            if (StringUtils.isBlank(manifest.getLatestPublishedVersion())
                || !versions.contains(manifest.getLatestPublishedVersion())) {
                throw new IllegalArgumentException(
                    "Historical MCP latest Version is absent from the Manifest");
            }
            return new ManifestIdentity(namespaceId, manifest.getName(), manifest.getId());
        } catch (IllegalArgumentException e) {
            throw conflict("Invalid historical MCP serving Manifest", e);
        }
    }
    
    private List<AiResourceVersion> prepareVersions(ManifestIdentity identity,
        McpServerVersionInfo manifest) throws NacosException {
        List<AiResourceVersion> result = new ArrayList<>(manifest.getVersionDetails().size());
        for (ServerVersionDetail detail : manifest.getVersionDetails()) {
            result.add(prepareVersion(identity, detail.getVersion()));
        }
        return result;
    }
    
    private AiResourceVersion prepareVersion(ManifestIdentity identity, String version)
        throws NacosException {
        McpVersionStorageDescriptor serverDescriptor = McpVersionStorageKeyComposer.compose(
            identity.namespaceId, identity.mcpId, version, false, false);
        McpVersionStorageContents serverOnly = versionStorageService.load(serverDescriptor);
        McpServerStorageInfo server = decode(serverOnly.getServerContent(),
            McpServerStorageInfo.class, "Server", identity, version);
        validateServerIdentity(server, identity, version);
        
        McpVersionStorageDescriptor descriptor = McpVersionStorageKeyComposer.fromLegacy(
            identity.namespaceId, identity.mcpId, version, server);
        McpVersionStorageContents contents = versionStorageService.load(descriptor);
        if (!Arrays.equals(serverOnly.getServerContent(), contents.getServerContent())) {
            throw conflict("Historical MCP Server content changed while reconciling "
                + identity.name + ':' + version, null);
        }
        if (contents.getToolContent() != null) {
            decode(contents.getToolContent(), McpToolSpecification.class, "Tools", identity,
                version);
        }
        if (contents.getResourceContent() != null) {
            decode(contents.getResourceContent(), McpResourceSpecification.class, "Resources",
                identity, version);
        }
        
        AiResourceVersion result = new AiResourceVersion();
        result.setNamespaceId(identity.namespaceId);
        result.setName(identity.name);
        result.setType(AiResourceConstants.RESOURCE_TYPE_MCP);
        result.setVersion(version);
        result.setStatus(AiResourceConstants.VERSION_STATUS_ONLINE);
        result.setAuthor(OWNER_NACOS);
        result.setDesc("");
        result.setStorage(McpVersionStorageDescriptorSerializer.serialize(descriptor));
        return result;
    }
    
    private <T> T decode(byte[] content, Class<T> type, String contentName,
        ManifestIdentity identity, String version) throws NacosException {
        try {
            T result = JacksonUtils.toObj(new String(content, StandardCharsets.UTF_8), type);
            if (result == null) {
                throw new IllegalArgumentException(contentName + " content is null");
            }
            return result;
        } catch (NacosDeserializationException | IllegalArgumentException e) {
            throw conflict("Historical MCP " + contentName + " content is invalid for "
                + identity.name + ':' + version, e);
        }
    }
    
    private void validateServerIdentity(McpServerStorageInfo server, ManifestIdentity identity,
        String version) throws NacosException {
        String versionDetail = server.getVersionDetail() == null ? null
            : server.getVersionDetail().getVersion();
        String legacyVersion = server.getVersion();
        if (StringUtils.isNotBlank(versionDetail) && StringUtils.isNotBlank(legacyVersion)
            && !versionDetail.equals(legacyVersion)) {
            throw conflict("Historical MCP Server contains conflicting Version fields for "
                + identity.name + ':' + version, null);
        }
        String actualVersion = StringUtils.isNotBlank(versionDetail) ? versionDetail
            : legacyVersion;
        boolean identityMatches = identity.mcpId.equals(server.getId())
            && identity.name.equals(server.getName()) && version.equals(actualVersion)
            && (StringUtils.isBlank(server.getNamespaceId())
                || identity.namespaceId.equals(server.getNamespaceId()));
        if (!identityMatches) {
            throw conflict("Historical MCP Server identity does not match its Manifest for "
                + identity.name + ':' + version, null);
        }
    }
    
    private AiResource buildResource(ManifestIdentity identity, McpServerVersionInfo manifest,
        int onlineCount) {
        McpResourceExt ext = new McpResourceExt();
        ext.setSchemaVersion(McpResourceExt.SCHEMA_VERSION);
        ext.setMcpId(identity.mcpId);
        
        ResourceVersionInfo versionInfo = new ResourceVersionInfo();
        versionInfo.setEditingVersion(null);
        versionInfo.setReviewingVersion(null);
        versionInfo.setOnlineCnt(onlineCount);
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put(AiResourceConstants.LABEL_LATEST, manifest.getLatestPublishedVersion());
        versionInfo.setLabels(labels);
        
        AiResource result = new AiResource();
        result.setNamespaceId(identity.namespaceId);
        result.setName(identity.name);
        result.setType(AiResourceConstants.RESOURCE_TYPE_MCP);
        result.setDesc(manifest.getDescription());
        result.setStatus(manifest.isEnabled() ? AiResourceConstants.META_STATUS_ENABLE
            : AiResourceConstants.META_STATUS_DISABLE);
        result.setBizTags("[]");
        result.setExt(McpResourceExtSerializer.serialize(ext));
        result.setFrom(LEGACY_SOURCE);
        result.setVersionInfo(JacksonUtils.toJson(versionInfo));
        result.setMetaVersion(1L);
        result.setScope(VisibilityConstants.SCOPE_PUBLIC);
        result.setOwner(OWNER_NACOS);
        return result;
    }
    
    private AiResource preflightResource(AiResource expected) throws NacosException {
        List<AiResource> rows = findResourceRows(expected.getNamespaceId(), expected.getName());
        if (rows.size() > 1) {
            throw conflict("Multiple MCP Resource source rows exist for " + expected.getName(),
                null);
        }
        if (rows.isEmpty()) {
            return null;
        }
        AiResource existing = rows.get(0);
        if (!immutableResourceIdentityMatches(existing, expected)) {
            throw conflict("Historical MCP Resource conflicts with an existing source row for "
                + expected.getName(), null);
        }
        return existing;
    }
    
    private List<AiResource> findResourceRows(String namespaceId, String name)
        throws NacosException {
        QueryCondition condition = new QueryCondition();
        condition.setNamespaceId(namespaceId);
        condition.setType(AiResourceConstants.RESOURCE_TYPE_MCP);
        condition.putOrGroup(RESOURCE_NAME_FIELD, name);
        Page<AiResource> page = resourcePersistService.list(condition, 1,
            EXACT_RESOURCE_PAGE_SIZE);
        if (page == null || page.getPageItems() == null) {
            throw storageFailure("Unable to query MCP Resource rows for " + name, null);
        }
        List<AiResource> result = page.getPageItems();
        if (page.getTotalCount() > result.size() || result.size() > EXACT_RESOURCE_PAGE_SIZE) {
            throw conflict("Multiple MCP Resource source rows exist for " + name, null);
        }
        for (AiResource resource : result) {
            if (resource == null || !namespaceId.equals(resource.getNamespaceId())
                || !name.equals(resource.getName())
                || !AiResourceConstants.RESOURCE_TYPE_MCP.equals(resource.getType())) {
                throw conflict("MCP Resource query returned an inconsistent row for " + name,
                    null);
            }
        }
        return result;
    }
    
    private VersionPreflight preflightVersions(ManifestIdentity identity,
        List<AiResourceVersion> expectedVersions) throws NacosException {
        Map<String, AiResourceVersion> existing = loadExistingVersions(identity);
        List<AiResourceVersion> missing = new ArrayList<>();
        for (AiResourceVersion expected : expectedVersions) {
            AiResourceVersion current = existing.remove(expected.getVersion());
            if (current == null) {
                missing.add(expected);
            } else if (!versionEquivalent(current, expected)) {
                throw conflict("Historical MCP Version conflicts with an existing row for "
                    + identity.name + ':' + expected.getVersion(), null);
            }
        }
        return new VersionPreflight(missing, new ArrayList<>(existing.keySet()));
    }
    
    private Map<String, AiResourceVersion> loadExistingVersions(ManifestIdentity identity)
        throws NacosException {
        Map<String, AiResourceVersion> result = new LinkedHashMap<>();
        int pageNo = 1;
        int pagesAvailable = 1;
        while (pageNo <= pagesAvailable) {
            Page<AiResourceVersion> page = versionPersistService.list(identity.namespaceId,
                identity.name, AiResourceConstants.RESOURCE_TYPE_MCP, null, pageNo,
                VERSION_PAGE_SIZE);
            if (page == null || page.getPageItems() == null) {
                throw storageFailure("Unable to page historical MCP Version rows for "
                    + identity.name, null);
            }
            pagesAvailable = resolvePages(page, pageNo, VERSION_PAGE_SIZE);
            for (AiResourceVersion version : page.getPageItems()) {
                if (!versionIdentityMatches(version, identity)
                    || result.put(version.getVersion(), version) != null) {
                    throw conflict("Historical MCP Version query returned inconsistent rows for "
                        + identity.name, null);
                }
            }
            pageNo++;
        }
        return result;
    }
    
    private int resolvePages(Page<?> page, int pageNo, int pageSize) {
        if (page.getPagesAvailable() > 0) {
            return Math.max(pageNo, page.getPagesAvailable());
        }
        int calculated = (page.getTotalCount() + pageSize - 1) / pageSize;
        return Math.max(pageNo, calculated);
    }
    
    private int insertVersionOrRecover(AiResourceVersion expected) throws NacosException {
        try {
            versionPersistService.insert(expected);
            return 1;
        } catch (RuntimeException e) {
            AiResourceVersion recovered = versionPersistService.find(expected.getNamespaceId(),
                expected.getName(), expected.getType(), expected.getVersion());
            if (recovered != null && versionEquivalent(recovered, expected)) {
                return 0;
            }
            if (recovered != null) {
                throw conflict("Concurrent MCP Version insert produced conflicting content for "
                    + expected.getName() + ':' + expected.getVersion(), e);
            }
            throw storageFailure("Unable to insert historical MCP Version row for "
                + expected.getName() + ':' + expected.getVersion(), e);
        }
    }
    
    private int upsertResourceLast(AiResource expected, AiResource existing)
        throws NacosException {
        if (existing == null) {
            try {
                resourcePersistService.insert(expected);
                return 1;
            } catch (RuntimeException e) {
                AiResource recovered = uniqueResourceOrNull(expected.getNamespaceId(),
                    expected.getName());
                if (recovered != null && resourceEquivalent(recovered, expected)) {
                    return 0;
                }
                if (recovered != null) {
                    throw conflict("Concurrent MCP Resource insert produced conflicting metadata "
                        + "for " + expected.getName(), e);
                }
                throw storageFailure("Unable to insert historical MCP Resource row for "
                    + expected.getName(), e);
            }
        }
        if (resourceEquivalent(existing, expected)) {
            return 0;
        }
        if (existing.getMetaVersion() == null) {
            throw conflict("Historical MCP Resource has no metadata version for "
                + expected.getName(), null);
        }
        if (resourcePersistService.updateMetaCas(expected.getNamespaceId(), expected.getName(),
            expected.getType(), existing.getMetaVersion(), expected)) {
            return 1;
        }
        AiResource recovered = uniqueResourceOrNull(expected.getNamespaceId(), expected.getName());
        if (recovered != null && resourceEquivalent(recovered, expected)) {
            return 0;
        }
        throw conflict("Historical MCP Resource changed concurrently for " + expected.getName(),
            null);
    }
    
    private AiResource uniqueResourceOrNull(String namespaceId, String name)
        throws NacosException {
        List<AiResource> rows = findResourceRows(namespaceId, name);
        if (rows.size() > 1) {
            throw conflict("Multiple MCP Resource source rows exist for " + name, null);
        }
        return rows.isEmpty() ? null : rows.get(0);
    }
    
    private boolean immutableResourceIdentityMatches(AiResource actual, AiResource expected) {
        if (actual == null || !Objects.equals(actual.getNamespaceId(), expected.getNamespaceId())
            || !Objects.equals(actual.getName(), expected.getName())
            || !Objects.equals(actual.getType(), expected.getType())
            || !Objects.equals(actual.getFrom(), expected.getFrom())
            || !Objects.equals(actual.getOwner(), expected.getOwner())
            || !Objects.equals(actual.getScope(), expected.getScope())) {
            return false;
        }
        try {
            McpResourceExt actualExt = McpResourceExtSerializer.deserialize(actual.getExt());
            McpResourceExt expectedExt = McpResourceExtSerializer.deserialize(expected.getExt());
            return Objects.equals(actualExt.getMcpId(), expectedExt.getMcpId());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
    
    private boolean resourceEquivalent(AiResource actual, AiResource expected) {
        return immutableResourceIdentityMatches(actual, expected)
            && Objects.equals(actual.getDesc(), expected.getDesc())
            && Objects.equals(actual.getStatus(), expected.getStatus())
            && emptyBizTags(actual.getBizTags()) && versionInfoEquivalent(actual.getVersionInfo(),
                expected.getVersionInfo());
    }
    
    private boolean emptyBizTags(String json) {
        try {
            List<?> tags = JacksonUtils.toObj(json, List.class);
            return tags != null && tags.isEmpty();
        } catch (NacosDeserializationException e) {
            return false;
        }
    }
    
    private boolean versionInfoEquivalent(String actualJson, String expectedJson) {
        try {
            ResourceVersionInfo actual = JacksonUtils.toObj(actualJson,
                ResourceVersionInfo.class);
            ResourceVersionInfo expected = JacksonUtils.toObj(expectedJson,
                ResourceVersionInfo.class);
            return actual != null && expected != null
                && Objects.equals(actual.getEditingVersion(), expected.getEditingVersion())
                && Objects.equals(actual.getReviewingVersion(), expected.getReviewingVersion())
                && Objects.equals(actual.getOnlineCnt(), expected.getOnlineCnt())
                && Objects.equals(actual.getLabels(), expected.getLabels());
        } catch (NacosDeserializationException e) {
            return false;
        }
    }
    
    private boolean versionIdentityMatches(AiResourceVersion version,
        ManifestIdentity identity) {
        return version != null && identity.namespaceId.equals(version.getNamespaceId())
            && identity.name.equals(version.getName())
            && AiResourceConstants.RESOURCE_TYPE_MCP.equals(version.getType())
            && StringUtils.isNotBlank(version.getVersion());
    }
    
    private boolean versionEquivalent(AiResourceVersion actual, AiResourceVersion expected) {
        if (!Objects.equals(actual.getNamespaceId(), expected.getNamespaceId())
            || !Objects.equals(actual.getName(), expected.getName())
            || !Objects.equals(actual.getType(), expected.getType())
            || !Objects.equals(actual.getVersion(), expected.getVersion())
            || !Objects.equals(actual.getStatus(), expected.getStatus())
            || !Objects.equals(actual.getAuthor(), expected.getAuthor())
            || !blankEquivalent(actual.getDesc(), expected.getDesc())
            || StringUtils.isNotBlank(actual.getPublishPipelineInfo())) {
            return false;
        }
        try {
            String canonicalActual = McpVersionStorageDescriptorSerializer.serialize(
                McpVersionStorageDescriptorSerializer.deserialize(actual.getStorage()));
            return canonicalActual.equals(expected.getStorage());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
    
    private boolean blankEquivalent(String first, String second) {
        return StringUtils.isBlank(first) && StringUtils.isBlank(second)
            || Objects.equals(first, second);
    }
    
    private void validateVersion(String version) {
        if (StringUtils.isBlank(version) || version.length() > MAX_VERSION_LENGTH) {
            throw new IllegalArgumentException("Invalid historical MCP Version: " + version);
        }
    }
    
    private NacosException conflict(String message, Throwable cause) {
        return cause == null ? new NacosException(NacosException.CONFLICT, message)
            : new NacosException(NacosException.CONFLICT, message, cause);
    }
    
    private NacosException storageFailure(String message, Throwable cause) {
        return cause == null ? new NacosException(NacosException.SERVER_ERROR, message)
            : new NacosException(NacosException.SERVER_ERROR, message, cause);
    }
    
    private static final class ManifestIdentity {
        
        private final String namespaceId;
        
        private final String name;
        
        private final String mcpId;
        
        private ManifestIdentity(String namespaceId, String name, String mcpId) {
            this.namespaceId = namespaceId;
            this.name = name;
            this.mcpId = mcpId;
        }
    }
    
    private static final class VersionPreflight {
        
        private final List<AiResourceVersion> missingVersions;
        
        private final List<String> extraVersions;
        
        private VersionPreflight(List<AiResourceVersion> missingVersions,
            List<String> extraVersions) {
            this.missingVersions = missingVersions;
            this.extraVersions = extraVersions;
        }
    }
}
