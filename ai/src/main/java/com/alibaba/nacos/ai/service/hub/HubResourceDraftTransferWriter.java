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

import com.alibaba.nacos.ai.constant.AiResourceConstants;
import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.model.AiResourceVersion;
import com.alibaba.nacos.ai.service.VisibilityHelper;
import com.alibaba.nacos.ai.service.repository.AiResourcePersistService;
import com.alibaba.nacos.ai.service.repository.AiResourceVersionPersistService;
import com.alibaba.nacos.ai.service.resource.ResourceVersionInfo;
import com.alibaba.nacos.ai.model.hub.HubResourceType;
import com.alibaba.nacos.ai.utils.AiResourceOriginUtils;
import com.alibaba.nacos.ai.utils.HubNamespaceUtils;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.plugin.visibility.constant.VisibilityConstants;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Typed create-only draft commit: reserve unique target name while disabled,
 * write independent content, insert one new draft version, then ID-pinned publish.
 * The target is never an alias of the source storage keys.
 */
@Service
public class HubResourceDraftTransferWriter {
    
    private final AiResourcePersistService resources;
    private final AiResourceVersionPersistService versions;
    private final HubResourceDraftCommitStorage commit;
    
    public HubResourceDraftTransferWriter(AiResourcePersistService resources,
        AiResourceVersionPersistService versions, HubResourceDraftCommitStorage commit) {
        this.resources = resources;
        this.versions = versions;
        this.commit = commit;
    }
    
    /** A callback rechecks source, publication and user permissions immediately before activation. */
    public CreatedDraft create(HubResourceType type, String namespaceId, String name,
        String targetVersion, String extJson, String description, String bizTags,
        PreparedContent contents, RunnableWithNacosException prePublishCheck)
        throws NacosException {
        if (type == null || type == HubResourceType.SKILL || contents == null
            || prePublishCheck == null) {
            throw new NacosApiException(400, ErrorCode.PARAMETER_VALIDATE_ERROR,
                "Invalid typed draft transfer plan");
        }
        requireAvailable(type, namespaceId, name, targetVersion);
        Map<?, ?> ext = JacksonUtils.toObj(extJson, Map.class);
        if (ext == null || !(ext.get("origin") instanceof Map)) {
            throw new NacosApiException(400, ErrorCode.PARAMETER_VALIDATE_ERROR,
                "Transferred draft must contain origin in its first metadata insert");
        }
        AiResourceOriginUtils.copy((Map<?, ?>) ext.get("origin"));
        String owner = VisibilityHelper.resolveCurrentIdentity();
        if (owner == null || owner.isBlank() || "anonymous".equalsIgnoreCase(owner)) {
            throw new NacosApiException(NacosException.NO_RIGHT, ErrorCode.ACCESS_DENIED,
                "Authenticated target owner is required");
        }
        ResourceVersionInfo info = new ResourceVersionInfo();
        info.setEditingVersion(targetVersion);
        info.setOnlineCnt(0);
        info.setLabels(Collections.emptyMap());
        AiResource pending = new AiResource();
        pending.setNamespaceId(namespaceId);
        pending.setName(name);
        pending.setType(type.resourceType());
        pending.setFrom("local");
        pending.setStatus(AiResourceConstants.META_STATUS_DISABLE);
        pending.setScope(VisibilityConstants.SCOPE_PRIVATE);
        pending.setOwner(owner);
        pending.setDesc(description);
        pending.setBizTags(bizTags);
        pending.setExt(extJson);
        pending.setVersionInfo(JacksonUtils.toJson(info));
        pending.setMetaVersion(1L);
        
        // The local origin participates in the database unique key for the reservation.
        long id;
        try {
            id = resources.insert(pending);
        } catch (RuntimeException insertionFailure) {
            try {
                if (resources.find(namespaceId, name, type.resourceType()) != null) {
                    throw conflict(name);
                }
            } catch (RuntimeException lookupFailure) {
                insertionFailure.addSuppressed(lookupFailure);
            }
            throw controlledFailure(insertionFailure);
        }
        if (id <= 0) {
            throw controlledFailure(
                new IllegalStateException("Invalid target reservation identity"));
        }
        // Nothing may write target files until the insertion succeeded.
        boolean versionInserted = false;
        boolean published = false;
        long insertedVersionId = 0L;
        String insertedVersionStorage = null;
        long expectedMetaVersion = 1L;
        try {
            AiResource owned = resources.find(namespaceId, name, type.resourceType());
            if (!ownedPending(owned, id, extJson, expectedMetaVersion)) {
                throw conflict(name);
            }
            if (versions.find(namespaceId, name, type.resourceType(), targetVersion) != null) {
                throw conflict(name);
            }
            String storageJson = contents.write();
            if (storageJson == null || storageJson.isBlank()) {
                throw new NacosApiException(500, ErrorCode.SERVER_ERROR,
                    "Target content writer returned no storage descriptor");
            }
            
            AiResourceVersion draft = new AiResourceVersion();
            draft.setNamespaceId(namespaceId);
            draft.setName(name);
            draft.setType(type.resourceType());
            draft.setVersion(targetVersion);
            draft.setStatus(AiResourceConstants.VERSION_STATUS_DRAFT);
            draft.setAuthor(owner);
            draft.setDesc("Imported independent draft");
            draft.setStorage(storageJson);
            insertedVersionStorage = draft.getStorage();
            insertedVersionId = versions.insert(draft);
            versionInserted = true;
            AiResourceVersion savedVersion =
                versions.find(namespaceId, name, type.resourceType(), targetVersion);
            if (!ownedVersion(savedVersion, insertedVersionId, insertedVersionStorage, owner)) {
                throw conflict(name);
            }
            prePublishCheck.run();
            if (!commit.publish(type, id, namespaceId, name, expectedMetaVersion)) {
                throw conflict(name);
            }
            published = true;
            expectedMetaVersion = 2L;
            AiResource visible = resources.find(namespaceId, name, type.resourceType());
            if (visible == null || !Objects.equals(visible.getId(), id)
                || !Objects.equals(visible.getExt(), extJson)
                || !AiResourceConstants.META_STATUS_ENABLE.equals(visible.getStatus())
                || !Objects.equals(visible.getMetaVersion(), expectedMetaVersion)) {
                throw conflict(name);
            }
            // Revalidation after publish catches the common revoke-during-CAS race.
            prePublishCheck.run();
            return new CreatedDraft(id, targetVersion);
        } catch (NacosException | RuntimeException operationFailure) {
            // If publish succeeded, turn off only our own exact metaVersion. A
            // concurrent edit wins, and must never be deleted by this rollback.
            if (published) {
                try {
                    AiResourceVersion current =
                        versions.find(namespaceId, name, type.resourceType(), targetVersion);
                    if (!ownedVersion(current, insertedVersionId, insertedVersionStorage, owner)) {
                        throw new IllegalStateException(
                            "Target draft changed; automatic cleanup skipped");
                    }
                    if (commit.disable(type, id, namespaceId, name, 2L)) {
                        expectedMetaVersion = 3L;
                    } else {
                        operationFailure.addSuppressed(new IllegalStateException(
                            "Target changed after publish; automatic cleanup skipped"));
                        throw controlledFailure(operationFailure);
                    }
                } catch (RuntimeException rollbackFailure) {
                    if (rollbackFailure != operationFailure) {
                        operationFailure.addSuppressed(rollbackFailure);
                    }
                    throw controlledFailure(operationFailure);
                }
            }
            try {
                AiResource owned = resources.find(namespaceId, name, type.resourceType());
                if (!ownedPending(owned, id, extJson, expectedMetaVersion)) {
                    operationFailure.addSuppressed(new IllegalStateException(
                        "Target reservation changed; refuse destructive cleanup"));
                    throw operationFailure;
                }
                // Version must remain draft and belong to this reservation.
                AiResourceVersion currentVersion =
                    versions.find(namespaceId, name, type.resourceType(), targetVersion);
                if (currentVersion != null) {
                    if (!versionInserted || !ownedVersion(currentVersion, insertedVersionId,
                        insertedVersionStorage, owner)) {
                        operationFailure.addSuppressed(new IllegalStateException(
                            "Unexpected version row; refuse destructive cleanup"));
                        throw operationFailure;
                    }
                }
                // Keep the unique name reserved until every touched key is removed. A cleanup
                // failure retains the version descriptor and disabled reservation for recovery.
                contents.cleanup();
                if (currentVersion != null && !commit.deleteDraftVersion(type, id, namespaceId,
                    name, expectedMetaVersion, insertedVersionId, targetVersion)) {
                    throw new IllegalStateException(
                        "Draft cleanup lost reservation or version identity");
                }
                if (!commit.deleteDisabled(type, id, namespaceId, name, expectedMetaVersion)) {
                    operationFailure.addSuppressed(new IllegalStateException(
                        "Reservation cleanup lost metaVersion CAS"));
                    throw operationFailure;
                }
            } catch (NacosException | RuntimeException rollbackFailure) {
                if (rollbackFailure != operationFailure) {
                    operationFailure.addSuppressed(rollbackFailure);
                }
            }
            throw controlledFailure(operationFailure);
        }
    }
    
    /** Preflight before source file IO; the unique insert still arbitrates concurrent creators. */
    public void requireAvailable(HubResourceType type, String namespaceId, String name,
        String version)
        throws NacosException {
        if (type == null || type == HubResourceType.SKILL || namespaceId == null
            || namespaceId.isBlank() || namespaceId.length() > 128 || name == null
            || name.isBlank() || name.length() > 256 || version == null || version.isBlank()
            || version.length() > 64 || "latest".equalsIgnoreCase(version)) {
            throw new NacosApiException(400, ErrorCode.PARAMETER_VALIDATE_ERROR,
                "Invalid target draft identity");
        }
        HubNamespaceUtils.rejectIfHubNamespace(namespaceId);
        try {
            if (resources.find(namespaceId, name, type.resourceType()) != null
                || versions.find(namespaceId, name, type.resourceType(), version) != null) {
                throw conflict(name);
            }
        } catch (RuntimeException failure) {
            throw controlledFailure(failure);
        }
    }
    
    private boolean ownedVersion(AiResourceVersion version, long id, String storage, String owner) {
        return id > 0 && version != null && Objects.equals(version.getId(), id)
            && AiResourceConstants.VERSION_STATUS_DRAFT.equals(version.getStatus())
            && Objects.equals(version.getStorage(), storage)
            && Objects.equals(version.getAuthor(), owner);
    }
    
    private NacosException controlledFailure(Throwable failure) {
        if (failure instanceof NacosException && ((NacosException) failure).getErrCode() < 500) {
            return (NacosException) failure;
        }
        NacosApiException result =
            new NacosApiException(NacosException.SERVER_ERROR, ErrorCode.SERVER_ERROR,
                "Resource draft transfer unavailable");
        result.initCause(failure);
        return result;
    }
    
    private boolean ownedPending(AiResource meta, long id, String ext, long version) {
        return meta != null && Objects.equals(meta.getId(), id)
            && "local".equals(meta.getFrom())
            && AiResourceConstants.META_STATUS_DISABLE.equals(meta.getStatus())
            && Objects.equals(meta.getMetaVersion(), version)
            && Objects.equals(meta.getExt(), ext);
    }
    
    private NacosApiException conflict(String name) {
        return new NacosApiException(NacosException.CONFLICT, ErrorCode.RESOURCE_CONFLICT,
            "Target resource already exists or was changed concurrently: " + name);
    }
    
    /** Resource-specific target content, prepared without IO before the unique reservation. */
    public interface PreparedContent {
        
        /** Write only this reserved target's independent content and return its storage descriptor. */
        String write() throws NacosException;
        
        /** Remove only keys written by this object; retain the reservation when cleanup fails. */
        void cleanup() throws NacosException;
    }
    
    /** Server-internal creation result, not an HTTP or SDK response contract. */
    public record CreatedDraft(long resourceId, String version) {
    }
    
    @FunctionalInterface
    public interface RunnableWithNacosException {
        
        void run() throws NacosException;
    }
}
