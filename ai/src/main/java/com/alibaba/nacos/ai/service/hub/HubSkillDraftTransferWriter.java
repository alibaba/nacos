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
import com.alibaba.nacos.ai.storage.AiResourceStorageUtils;
import com.alibaba.nacos.api.ai.model.hub.HubSkillTransferResult;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.plugin.visibility.constant.VisibilityConstants;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * S3 create-only draft commit: reserve unique target name while disabled,
 * write independent files, insert one new draft version, then ID-pinned publish.
 * The target is never an alias of the source storage keys.
 */
@Service
public class HubSkillDraftTransferWriter {
    
    private static final String TYPE = AiResourceConstants.RESOURCE_TYPE_SKILL;
    
    private final AiResourcePersistService resources;
    private final AiResourceVersionPersistService versions;
    private final HubSkillDraftCommitStorage commit;
    private final HubSkillTransferFileService files;
    
    public HubSkillDraftTransferWriter(AiResourcePersistService resources,
        AiResourceVersionPersistService versions, HubSkillDraftCommitStorage commit,
        HubSkillTransferFileService files) {
        this.resources = resources;
        this.versions = versions;
        this.commit = commit;
        this.files = files;
    }
    
    /** A callback rechecks source, publication and user permissions immediately before activation. */
    public HubSkillTransferResult create(HubSkillTransferSourceSnapshot source,
        String namespaceId, String name, String targetVersion,
        HubSkillTransferFileService.Files contents, Map<String, Object> origin,
        RunnableWithNacosException prePublishCheck) throws NacosException {
        requireAvailable(namespaceId, name, targetVersion);
        String provider;
        try {
            provider = AiResourceStorageUtils.resolveProvider(
                com.alibaba.nacos.ai.constant.Constants.Skills.SKILL_STORAGE_PROVIDER_CONFIG_KEY,
                "nacos_config");
        } catch (RuntimeException failure) {
            throw controlledFailure(failure);
        }
        if (provider == null || !provider.matches("[a-zA-Z0-9_-]{1,64}")) {
            throw new NacosApiException(NacosException.SERVER_ERROR, ErrorCode.SERVER_ERROR,
                "Target Skill storage provider is unavailable");
        }
        Map<String, Object> ext = new LinkedHashMap<>();
        ext.put("origin", origin); // persisted with the FIRST meta insert, never tacked on later
        final String extJson = JacksonUtils.toJson(ext);
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
        pending.setType(TYPE);
        pending.setFrom("local");
        pending.setStatus(AiResourceConstants.META_STATUS_DISABLE);
        pending.setScope(VisibilityConstants.SCOPE_PRIVATE);
        pending.setOwner(owner);
        pending.setDesc(contents.getDescription());
        pending.setExt(extJson);
        pending.setVersionInfo(JacksonUtils.toJson(info));
        pending.setMetaVersion(1L);
        
        // The local origin participates in the database unique key for the reservation.
        long id;
        try {
            id = resources.insert(pending);
        } catch (RuntimeException insertionFailure) {
            try {
                if (resources.find(namespaceId, name, TYPE) != null) {
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
        List<String> touchedFiles = new ArrayList<>();
        long expectedMetaVersion = 1L;
        try {
            AiResource owned = resources.find(namespaceId, name, TYPE);
            if (!ownedPending(owned, id, extJson, expectedMetaVersion)) {
                throw conflict(name);
            }
            if (versions.find(namespaceId, name, TYPE, targetVersion) != null) {
                throw conflict(name);
            }
            files.save(namespaceId, name, targetVersion,
                withProvider(contents, provider), touchedFiles);
            
            AiResourceVersion draft = new AiResourceVersion();
            draft.setNamespaceId(namespaceId);
            draft.setName(name);
            draft.setType(TYPE);
            draft.setVersion(targetVersion);
            draft.setStatus(AiResourceConstants.VERSION_STATUS_DRAFT);
            draft.setAuthor(owner);
            draft.setDesc("Imported independent draft");
            Map<String, Object> storage = new LinkedHashMap<>();
            storage.put("provider", provider);
            storage.put("scope", namespaceId + ":" + name + ":" + targetVersion);
            storage.put("files", touchedFiles);
            draft.setStorage(JacksonUtils.toJson(storage));
            insertedVersionStorage = draft.getStorage();
            insertedVersionId = versions.insert(draft);
            versionInserted = true;
            AiResourceVersion savedVersion = versions.find(namespaceId, name, TYPE, targetVersion);
            if (!ownedVersion(savedVersion, insertedVersionId, insertedVersionStorage, owner)) {
                throw conflict(name);
            }
            prePublishCheck.run();
            if (!commit.publish(id, namespaceId, name, expectedMetaVersion)) {
                throw conflict(name);
            }
            published = true;
            expectedMetaVersion = 2L;
            AiResource visible = resources.find(namespaceId, name, TYPE);
            if (visible == null || !Objects.equals(visible.getId(), id)
                || !Objects.equals(visible.getExt(), extJson)
                || !AiResourceConstants.META_STATUS_ENABLE.equals(visible.getStatus())
                || !Objects.equals(visible.getMetaVersion(), expectedMetaVersion)) {
                throw conflict(name);
            }
            // Revalidation after publish catches the common revoke-during-CAS race.
            prePublishCheck.run();
            HubSkillTransferResult result = new HubSkillTransferResult();
            result.setTargetNamespaceId(namespaceId);
            result.setTargetName(name);
            result.setTargetResourceId(id);
            result.setDraftVersion(targetVersion);
            result.setStatus(AiResourceConstants.VERSION_STATUS_DRAFT);
            result.setOrigin(source.getHubEntryId() == null ? "DIRECT_COPY" : "HUB");
            result.setHubEntryId(source.getHubEntryId());
            return result;
        } catch (NacosException | RuntimeException operationFailure) {
            // If publish succeeded, turn off only our own exact metaVersion. A
            // concurrent edit wins, and must never be deleted by this rollback.
            if (published) {
                try {
                    AiResourceVersion current =
                        versions.find(namespaceId, name, TYPE, targetVersion);
                    if (!ownedVersion(current, insertedVersionId, insertedVersionStorage, owner)) {
                        throw new IllegalStateException(
                            "Target draft changed; automatic cleanup skipped");
                    }
                    if (commit.disable(id, namespaceId, name, 2L)) {
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
                AiResource owned = resources.find(namespaceId, name, TYPE);
                if (!ownedPending(owned, id, extJson, expectedMetaVersion)) {
                    operationFailure.addSuppressed(new IllegalStateException(
                        "Target reservation changed; refuse destructive cleanup"));
                    throw operationFailure;
                }
                // Version must remain draft and belong to this reservation.
                AiResourceVersion currentVersion =
                    versions.find(namespaceId, name, TYPE, targetVersion);
                if (currentVersion != null) {
                    if (!versionInserted || !ownedVersion(currentVersion, insertedVersionId,
                        insertedVersionStorage, owner)) {
                        operationFailure.addSuppressed(new IllegalStateException(
                            "Unexpected version row; refuse destructive cleanup"));
                        throw operationFailure;
                    }
                    if (!commit.deleteDraftVersion(id, namespaceId, name, expectedMetaVersion,
                        insertedVersionId, targetVersion)) {
                        throw new IllegalStateException(
                            "Draft cleanup lost reservation or version identity");
                    }
                }
                // Keep the unique name reserved until every touched key is removed. A cleanup
                // failure retains the disabled reservation for recovery instead of allowing reuse.
                files.delete(namespaceId, name, targetVersion, provider, touchedFiles);
                if (!commit.deleteDisabled(id, namespaceId, name, expectedMetaVersion)) {
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
    public void requireAvailable(String namespaceId, String name, String version)
        throws NacosException {
        try {
            if (resources.find(namespaceId, name, TYPE) != null
                || versions.find(namespaceId, name, TYPE, version) != null) {
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
                "Skill draft transfer unavailable");
        result.initCause(failure);
        return result;
    }
    
    // Provider resolved for the TARGET, independently from the source provider.
    private HubSkillTransferFileService.Files withProvider(
        HubSkillTransferFileService.Files original, String provider) {
        return original.forProvider(provider);
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
            "Target Skill already exists or was changed concurrently: " + name);
    }
    
    @FunctionalInterface
    public interface RunnableWithNacosException {
        
        void run() throws NacosException;
    }
}
