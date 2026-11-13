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

package com.alibaba.nacos.ai.service.hub;

import com.alibaba.nacos.ai.config.HubConfig;
import com.alibaba.nacos.ai.constant.AiResourceConstants;
import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.model.hub.HubResourceReference;
import com.alibaba.nacos.ai.model.hub.HubResourceType;
import com.alibaba.nacos.ai.service.mcp.McpCompatibilityMode;
import com.alibaba.nacos.ai.service.mcp.McpLifecycleManagementStateService;
import com.alibaba.nacos.ai.service.resource.AiResourceManager;
import com.alibaba.nacos.ai.service.resource.ResourceVersionInfo;
import com.alibaba.nacos.ai.utils.HubResourceReferenceJsonUtil;
import com.alibaba.nacos.api.ai.model.hub.HubShareOutcome;
import com.alibaba.nacos.api.ai.model.hub.HubShareResult;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Typed publication primitives for resource adapters. No HTTP endpoint, content format, runtime
 * registration or source mutation is introduced here. Existing Skill entry points remain unchanged.
 */
@Service
public class HubResourcePublicationService {
    
    private static final Logger LOGGER =
        LoggerFactory.getLogger(HubResourcePublicationService.class);
    
    private final HubShareRepositoryImpl repositories;
    
    private final HubResourceEligibilityValidator eligibility;
    
    private final HubResourcePermissionGuard permissions;
    
    private final McpLifecycleManagementStateService mcpState;
    
    private final HubTakedownAuditService audit;
    
    public HubResourcePublicationService(HubShareRepositoryImpl repositories,
        HubResourceEligibilityValidator eligibility, HubResourcePermissionGuard permissions,
        McpLifecycleManagementStateService mcpState, HubTakedownAuditService audit) {
        this.repositories = repositories;
        this.eligibility = eligibility;
        this.permissions = permissions;
        this.mcpState = mcpState;
        this.audit = audit;
    }
    
    /** Publish one explicit online source version without copying source content. */
    public HubShareResult share(HubResourceType type, String namespaceId, String name,
        String version, Long expectedSourceId) throws NacosException {
        requireReady(type);
        AiResource source = eligibility.requireSource(type, namespaceId, name, expectedSourceId);
        permissions.requireSourceRead(type, source);
        permissions.requireSourceWrite(type, source);
        HubResourceReference reference = eligibility.requireShareableVersion(type, source, version);
        HubShareRepository repository = repositories.forResourceType(type);
        AiResource entry = repository.reserveEntry(reference, source.getDesc());
        requireRevision(entry);
        if (active(entry)) {
            if (!sameCurrentReference(entry, reference)) {
                throw conflict("Resource is already shared at another version; adjust explicitly");
            }
            repository.requireReferenceVersion(entry.getId(), reference);
            revalidateWrite(type, reference);
            permissions.requireSourceRead(type, eligibility.requirePinnedSource(type, reference));
            requireUnchanged(repository, entry);
            return result(entry, reference, HubShareOutcome.ALREADY_SHARED);
        }
        if (!AiResourceConstants.META_STATUS_DISABLE.equals(entry.getStatus())) {
            throw conflict("Unsupported Hub publication status");
        }
        ResourceVersionInfo oldInfo = AiResourceManager.requireVersionInfo(entry);
        HubShareOutcome outcome = oldInfo.getOnlineCnt() != null && oldInfo.getOnlineCnt() > 0
            ? HubShareOutcome.REACTIVATED : HubShareOutcome.CREATED;
        repository.ensureReferenceVersion(entry.getId(), reference);
        revalidateWrite(type, reference);
        permissions.requireSourceRead(type, eligibility.requirePinnedSource(type, reference));
        return publish(type, repository, entry, reference, outcome);
    }
    
    /** Adjust the sole effective shared version, conditioned on the observed metadata revision. */
    public HubShareResult adjust(HubResourceType type, long entryId, long expectedRevision,
        String targetVersion) throws NacosException {
        requireReady(type);
        HubShareRepository repository = repositories.forResourceType(type);
        AiResource entry = requireEntry(repository, entryId, expectedRevision);
        if (!active(entry)) {
            throw conflict("Inactive share cannot be adjusted");
        }
        HubResourceReference previous = HubResourceReferenceJsonUtil.parse(entry.getExt());
        AiResource source = eligibility.requireSource(type, previous.getSourceNamespaceId(),
            previous.getSourceName(), previous.getSourceResourceId());
        permissions.requireSourceWrite(type, source);
        HubResourceReference reference = eligibility.requireShareableVersion(type, source,
            targetVersion);
        repository.ensureReferenceVersion(entryId, reference);
        revalidateWrite(type, reference);
        if (sameCurrentReference(entry, reference)) {
            requireUnchanged(repository, entry);
            return result(entry, reference, HubShareOutcome.UNCHANGED);
        }
        return publish(type, repository, entry, reference, HubShareOutcome.ADJUSTED);
    }
    
    /** Source-authorized cancellation remains available after disable/offline/private transitions. */
    public HubShareResult unshare(HubResourceType type, long entryId, long expectedRevision)
        throws NacosException {
        HubConfig.requireEnabled();
        HubShareRepository repository = repositories.forResourceType(type);
        AiResource entry = requireEntry(repository, entryId, expectedRevision);
        HubResourceReference reference = HubResourceReferenceJsonUtil.parse(entry.getExt());
        AiResource source = eligibility.requireSource(type, reference.getSourceNamespaceId(),
            reference.getSourceName(), reference.getSourceResourceId());
        permissions.requireSourceWrite(type, source);
        return deactivate(type, repository, entry, reference, false, null);
    }
    
    /** Administrator governance uses its own grant, never inferred from source WRITE. */
    public HubShareResult forceUnshare(HubResourceType type, long entryId, long expectedRevision,
        String reason) throws NacosException {
        HubConfig.requireEnabled();
        if (reason == null || reason.isBlank() || reason.length() > 512) {
            throw new NacosApiException(NacosException.INVALID_PARAM,
                ErrorCode.PARAMETER_VALIDATE_ERROR,
                "A takedown reason of 1..512 characters is required");
        }
        permissions.requireAdministrator();
        HubShareRepository repository = repositories.forResourceType(type);
        AiResource entry = requireEntry(repository, entryId, expectedRevision);
        return deactivate(type, repository, entry,
            HubResourceReferenceJsonUtil.parse(entry.getExt()), true, reason);
    }
    
    /**
     * Execute a resource-specific content read against one immutable publication. The adapter must
     * load the exact source version's persisted descriptor; no latest labels or runtime state are
     * resolved here. Source and publication are checked again after content IO.
     */
    public <T> T readPinned(HubResourceType type, long entryId, String requestedVersion,
        PinnedContentReader<T> reader) throws NacosException {
        return inspectPinned(type, entryId, requestedVersion,
            (entry, source, reference) -> reader.read(source, reference));
    }
    
    /** Inspect sanitized projections within the same publication and source validity fences. */
    public <T> T inspectPinned(HubResourceType type, long entryId, String requestedVersion,
        PinnedPublicationReader<T> reader) throws NacosException {
        requireReady(type);
        permissions.requireHubRead();
        HubShareRepository repository = repositories.forResourceType(type);
        AiResource entry = repository.findByEntryId(entryId);
        requireRevision(entry);
        if (!active(entry)) {
            throw unavailable();
        }
        HubResourceReference reference = HubResourceReferenceJsonUtil.parse(entry.getExt());
        if (requestedVersion != null && !requestedVersion.isBlank()
            && !reference.getSourceVersion().equals(requestedVersion)) {
            throw unavailable();
        }
        if (!sameCurrentReference(entry, reference)) {
            throw unavailable();
        }
        repository.requireReferenceVersion(entryId, reference);
        AiResource source = eligibility.requirePinnedSource(type, reference);
        requireUnchanged(repository, entry);
        T contents = reader.read(entry, source, reference);
        requireReady(type);
        permissions.requireHubRead();
        eligibility.requirePinnedSource(type, reference);
        requireUnchanged(repository, entry);
        return contents;
    }
    
    private HubShareResult publish(HubResourceType type, HubShareRepository repository,
        AiResource entry, HubResourceReference reference, HubShareOutcome outcome)
        throws NacosException {
        long expected = entry.getMetaVersion();
        if (!repository.publish(entry.getId(), expected, reference)) {
            throw conflict("Hub publication changed concurrently; refresh and retry");
        }
        try {
            requireReady(type);
            eligibility.requirePinnedSource(type, reference);
        } catch (NacosException | RuntimeException invalidated) {
            try {
                repository.disable(entry.getId(), expected + 1);
            } catch (NacosException | RuntimeException compensationFailure) {
                LOGGER.warn("Hub publication compensation failed, entryId={}", entry.getId(),
                    compensationFailure);
            }
            throw invalidated;
        }
        AiResource published = repository.findByEntryId(entry.getId());
        if (published == null || !active(published)
            || !Objects.equals(published.getMetaVersion(), expected + 1)
            || !sameCurrentReference(published, reference)) {
            throw conflict("Hub publication changed during commit");
        }
        return result(published, reference, outcome);
    }
    
    private HubShareResult deactivate(HubResourceType type, HubShareRepository repository,
        AiResource entry, HubResourceReference reference, boolean administrative, String reason)
        throws NacosException {
        if (AiResourceConstants.META_STATUS_DISABLE.equals(entry.getStatus())) {
            return result(entry, reference, HubShareOutcome.ALREADY_UNSHARED);
        }
        long expected = entry.getMetaVersion();
        if (!active(entry) || !repository.disable(entry.getId(), expected)) {
            throw conflict("Hub publication changed concurrently; refresh and retry");
        }
        audit.recordUnshare(type, entry.getId(), reference, expected + 1,
            administrative, reason);
        AiResource disabled = repository.findByEntryId(entry.getId());
        if (disabled == null || !Objects.equals(disabled.getMetaVersion(), expected + 1)
            || !AiResourceConstants.META_STATUS_DISABLE.equals(disabled.getStatus())) {
            throw conflict("Hub publication changed during cancellation");
        }
        return result(disabled, reference,
            administrative ? HubShareOutcome.FORCE_UNSHARED : HubShareOutcome.UNSHARED);
    }
    
    private void requireReady(HubResourceType type) throws NacosException {
        Objects.requireNonNull(type, "type");
        HubConfig.requireEnabled();
        if (type == HubResourceType.MCP
            && mcpState.resolveMode() != McpCompatibilityMode.LIFECYCLE_MANAGED) {
            throw conflict("MCP lifecycle migration must complete before sharing");
        }
    }
    
    private void revalidateWrite(HubResourceType type, HubResourceReference reference)
        throws NacosException {
        requireReady(type);
        permissions.requireSourceWrite(type, eligibility.requirePinnedSource(type, reference));
    }
    
    private AiResource requireEntry(HubShareRepository repository, long id, long revision)
        throws NacosException {
        if (id <= 0 || revision <= 0) {
            throw new NacosApiException(NacosException.INVALID_PARAM,
                ErrorCode.PARAMETER_VALIDATE_ERROR,
                "Entry ID and expected revision must be positive");
        }
        AiResource entry = repository.findByEntryId(id);
        requireRevision(entry);
        if (!Objects.equals(entry.getMetaVersion(), revision)) {
            throw conflict("Hub publication changed concurrently; refresh and retry");
        }
        return entry;
    }
    
    private void requireRevision(AiResource entry) throws NacosException {
        if (entry == null || entry.getId() == null || entry.getId() <= 0
            || entry.getMetaVersion() == null || entry.getMetaVersion() <= 0
            || !AiConstants.Hub.NAMESPACE_ID.equals(entry.getNamespaceId())
            || !"hub".equals(entry.getFrom()) || !"nacos-ai-hub".equals(entry.getOwner())
            || !"PUBLIC".equals(entry.getScope())) {
            throw unavailable();
        }
    }
    
    private void requireUnchanged(HubShareRepository repository, AiResource previous)
        throws NacosException {
        AiResource current = repository.findByEntryId(previous.getId());
        if (current == null || !Objects.equals(previous.getMetaVersion(), current.getMetaVersion())
            || !Objects.equals(previous.getStatus(), current.getStatus())
            || !Objects.equals(previous.getExt(), current.getExt())
            || !Objects.equals(previous.getVersionInfo(), current.getVersionInfo())) {
            throw unavailable();
        }
    }
    
    private boolean active(AiResource entry) {
        return AiResourceConstants.META_STATUS_ENABLE.equals(entry.getStatus());
    }
    
    private boolean sameCurrentReference(AiResource entry, HubResourceReference expected)
        throws NacosException {
        HubResourceReference current = HubResourceReferenceJsonUtil.parse(entry.getExt());
        ResourceVersionInfo info = AiResourceManager.requireVersionInfo(entry);
        return Objects.equals(current.getSourceNamespaceId(), expected.getSourceNamespaceId())
            && Objects.equals(current.getSourceResourceId(), expected.getSourceResourceId())
            && Objects.equals(current.getSourceName(), expected.getSourceName())
            && Objects.equals(current.getSourceVersionId(), expected.getSourceVersionId())
            && Objects.equals(current.getSourceVersion(), expected.getSourceVersion())
            && Objects.equals(info.getOnlineCnt(), 1) && info.getLabels() != null
            && Objects.equals(info.getLabels().get(AiResourceConstants.LABEL_LATEST),
                expected.getSourceVersion());
    }
    
    private HubShareResult result(AiResource entry, HubResourceReference reference,
        HubShareOutcome outcome) {
        HubShareResult result = new HubShareResult();
        result.setHubEntryId(entry.getId());
        result.setDisplayName(reference.getSourceName());
        result.setSourceNamespaceId(reference.getSourceNamespaceId());
        result.setSourceResourceId(reference.getSourceResourceId());
        result.setSourceName(reference.getSourceName());
        result.setSourceVersionId(reference.getSourceVersionId());
        result.setSourceVersion(reference.getSourceVersion());
        result.setMetaVersion(entry.getMetaVersion());
        result.setActive(active(entry));
        result.setOutcome(outcome);
        return result;
    }
    
    private NacosApiException conflict(String message) {
        return new NacosApiException(NacosException.CONFLICT, ErrorCode.RESOURCE_CONFLICT, message);
    }
    
    private NacosApiException unavailable() {
        return new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
            "Hub resource not found or unavailable");
    }
    
    /** Internal adapter callback; source identity is already pinned and validated. */
    @FunctionalInterface
    public interface PinnedContentReader<T> {
        
        /** Load exactly the referenced immutable source content. */
        T read(AiResource source, HubResourceReference reference) throws NacosException;
    }
    
    /** Internal projection callback; raw rows must never be returned as HTTP payloads. */
    @FunctionalInterface
    public interface PinnedPublicationReader<T> {
        
        /** Project one publication while preserving its exact source and metadata revision. */
        T read(AiResource entry, AiResource source, HubResourceReference reference)
            throws NacosException;
    }
}
