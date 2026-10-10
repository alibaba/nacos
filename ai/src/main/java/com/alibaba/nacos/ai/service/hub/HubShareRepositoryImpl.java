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
import com.alibaba.nacos.ai.model.hub.HubResourceReference;
import com.alibaba.nacos.ai.service.repository.AiResourcePersistService;
import com.alibaba.nacos.ai.service.repository.AiResourceVersionPersistService;
import com.alibaba.nacos.ai.service.repository.HubEntryStorage;
import com.alibaba.nacos.ai.service.resource.ResourceVersionInfo;
import com.alibaba.nacos.ai.utils.HubResourceNameUtils;
import com.alibaba.nacos.ai.utils.HubResourceReferenceJsonUtil;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.plugin.visibility.constant.VisibilityConstants;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Hub reference storage adapter. Its only visibility commit is an ID-pinned Meta CAS.
 *
 * <p>The entry is inserted disabled. A version reference is written before the
 * publish CAS, which changes status, ext and latest label together. Partial failures
 * leave disabled metadata or unreachable version rows; read APIs must require a
 * valid enabled current pointer and revalidate the source.</p>
 */
@Service
public class HubShareRepositoryImpl implements HubShareRepository {
    
    private static final String HUB_NAMESPACE = AiConstants.Hub.NAMESPACE_ID;
    
    private static final String SKILL = AiResourceConstants.RESOURCE_TYPE_SKILL;
    
    private static final String HUB_SOURCE = "hub";
    
    private static final String SYSTEM_OWNER = "nacos-ai-hub";
    
    private final HubEntryStorage hubStorage;
    
    private final AiResourcePersistService resourcePersistService;
    
    private final AiResourceVersionPersistService versionPersistService;
    
    public HubShareRepositoryImpl(HubEntryStorage hubStorage,
        AiResourcePersistService resourcePersistService,
        AiResourceVersionPersistService versionPersistService) {
        this.hubStorage = hubStorage;
        this.resourcePersistService = resourcePersistService;
        this.versionPersistService = versionPersistService;
    }
    
    @Override
    public AiResource reserveEntry(HubResourceReference reference, String description)
        throws NacosException {
        verifySource(reference);
        String name = HubResourceNameUtils.buildInternalName(reference.getSourceName(),
            reference.getSourceResourceId());
        AiResource existing = hubStorage.findByInternalName(name);
        if (existing != null) {
            verifyEntrySource(existing, reference);
            return existing;
        }
        AiResource pending = new AiResource();
        pending.setName(name);
        pending.setNamespaceId(HUB_NAMESPACE);
        pending.setType(SKILL);
        pending.setFrom(HUB_SOURCE);
        pending.setOwner(SYSTEM_OWNER);
        pending.setScope(VisibilityConstants.SCOPE_PUBLIC);
        pending.setStatus(AiResourceConstants.META_STATUS_DISABLE);
        pending.setDesc(description);
        pending.setExt(HubResourceReferenceJsonUtil.toJson(reference));
        pending.setMetaVersion(1L);
        ResourceVersionInfo info = new ResourceVersionInfo();
        info.setOnlineCnt(0);
        info.setLabels(Collections.emptyMap());
        pending.setVersionInfo(JacksonUtils.toJson(info));
        try {
            resourcePersistService.insert(pending);
        } catch (RuntimeException failure) {
            // The embedded store can wrap duplicate writes in IllegalStateException.
            // Recover only if the exact same entry now exists. Otherwise preserve
            // the original failure; never steal a colliding entry.
            AiResource concurrent = hubStorage.findByInternalName(name);
            if (concurrent == null) {
                throw failure;
            }
            verifyEntrySource(concurrent, reference);
            return concurrent;
        }
        AiResource result = hubStorage.findByInternalName(name);
        if (result == null) {
            throw new NacosApiException(NacosException.SERVER_ERROR, ErrorCode.SERVER_ERROR,
                "Hub entry not readable after reservation");
        }
        verifyEntrySource(result, reference);
        return result;
    }
    
    @Override
    public AiResource findByEntryId(long hubEntryId) {
        return hubEntryId > 0 ? hubStorage.findByEntryId(hubEntryId) : null;
    }
    
    @Override
    public AiResource findBySource(HubResourceReference source) throws NacosException {
        verifySource(source);
        String name = HubResourceNameUtils.buildInternalName(source.getSourceName(),
            source.getSourceResourceId());
        AiResource result = hubStorage.findByInternalName(name);
        if (result != null) {
            verifyEntrySource(result, source);
        }
        return result;
    }
    
    @Override
    public AiResourceVersion ensureReferenceVersion(long hubEntryId,
        HubResourceReference reference) throws NacosException {
        AiResource entry = requireMatchingEntry(hubEntryId, reference);
        String name = entry.getName();
        String version = reference.getSourceVersion();
        AiResourceVersion existing =
            versionPersistService.find(HUB_NAMESPACE, name, SKILL, version);
        if (existing != null) {
            verifyVersion(existing, reference);
            return existing;
        }
        AiResourceVersion pending = new AiResourceVersion();
        pending.setNamespaceId(HUB_NAMESPACE);
        pending.setName(name);
        pending.setType(SKILL);
        pending.setVersion(version);
        pending.setAuthor(SYSTEM_OWNER);
        pending.setStatus(AiResourceConstants.VERSION_STATUS_ONLINE);
        pending.setStorage(HubResourceReferenceJsonUtil.toJson(reference));
        try {
            versionPersistService.insert(pending);
        } catch (RuntimeException failure) {
            AiResourceVersion concurrent = versionPersistService.find(HUB_NAMESPACE, name, SKILL,
                version);
            if (concurrent == null) {
                throw failure;
            }
            verifyVersion(concurrent, reference);
            return concurrent;
        }
        AiResourceVersion persisted =
            versionPersistService.find(HUB_NAMESPACE, name, SKILL, version);
        if (persisted == null) {
            throw new NacosApiException(NacosException.SERVER_ERROR, ErrorCode.SERVER_ERROR,
                "Hub reference version not readable after insertion");
        }
        verifyVersion(persisted, reference);
        return persisted;
    }
    
    @Override
    public AiResourceVersion requireReferenceVersion(long hubEntryId,
        HubResourceReference reference) throws NacosException {
        return requirePreparedVersion(requireMatchingEntry(hubEntryId, reference), reference);
    }
    
    @Override
    public boolean publish(long hubEntryId, long expectedMetaVersion,
        HubResourceReference reference) throws NacosException {
        AiResource entry = requireMatchingEntry(hubEntryId, reference);
        if (!expectedVersion(entry, expectedMetaVersion)) {
            return false;
        }
        // The version must have been prepared; a caller cannot publish metadata
        // referring to a missing or unrelated version row.
        requirePreparedVersion(entry, reference);
        ResourceVersionInfo info = new ResourceVersionInfo();
        info.setOnlineCnt(1);
        Map<String, String> labels = new HashMap<>(1);
        labels.put(AiResourceConstants.LABEL_LATEST, reference.getSourceVersion());
        info.setLabels(labels);
        AiResource next = copyMutableMeta(entry);
        next.setStatus(AiResourceConstants.META_STATUS_ENABLE);
        next.setExt(HubResourceReferenceJsonUtil.toJson(reference));
        next.setVersionInfo(JacksonUtils.toJson(info));
        return hubStorage.updateMetaCas(entry, expectedMetaVersion, next);
    }
    
    @Override
    public boolean disable(long hubEntryId, long expectedMetaVersion) throws NacosException {
        AiResource entry = requireEntry(hubEntryId);
        if (!expectedVersion(entry, expectedMetaVersion)) {
            return false;
        }
        // Preserve version_info and pinned reference for repair/audit. The only
        // publication gate is meta.status plus a valid pinned version pointer.
        AiResource next = copyMutableMeta(entry);
        next.setStatus(AiResourceConstants.META_STATUS_DISABLE);
        return hubStorage.updateMetaCas(entry, expectedMetaVersion, next);
    }
    
    private AiResourceVersion requirePreparedVersion(AiResource entry,
        HubResourceReference reference) throws NacosException {
        AiResourceVersion version = versionPersistService.find(HUB_NAMESPACE, entry.getName(),
            SKILL, reference.getSourceVersion());
        if (version == null) {
            throw new NacosApiException(NacosException.CONFLICT, ErrorCode.RESOURCE_CONFLICT,
                "Hub reference version has not been prepared");
        }
        verifyVersion(version, reference);
        return version;
    }
    
    private AiResource requireMatchingEntry(long id, HubResourceReference reference)
        throws NacosException {
        verifySource(reference);
        AiResource entry = requireEntry(id);
        verifyEntrySource(entry, reference);
        return entry;
    }
    
    private AiResource requireEntry(long id) throws NacosException {
        AiResource entry = findByEntryId(id);
        if (entry == null) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                "Hub Skill entry not found: " + id);
        }
        if (entry.getMetaVersion() == null) {
            throw new NacosApiException(NacosException.SERVER_ERROR, ErrorCode.SERVER_ERROR,
                "Hub Skill metaVersion is missing");
        }
        HubResourceReferenceJsonUtil.parse(entry.getExt());
        return entry;
    }
    
    private void verifyEntrySource(AiResource entry, HubResourceReference expected)
        throws NacosException {
        String expectedName = HubResourceNameUtils.buildInternalName(expected.getSourceName(),
            expected.getSourceResourceId());
        HubResourceReference current = HubResourceReferenceJsonUtil.parse(entry.getExt());
        if (!HUB_NAMESPACE.equals(entry.getNamespaceId()) || !SKILL.equals(entry.getType())
            || !HUB_SOURCE.equals(entry.getFrom()) || !expectedName.equals(entry.getName())
            || !SYSTEM_OWNER.equals(entry.getOwner())
            || !VisibilityConstants.SCOPE_PUBLIC.equals(entry.getScope())
            || !sameSource(current, expected)) {
            throw new NacosApiException(NacosException.CONFLICT, ErrorCode.RESOURCE_CONFLICT,
                "Hub entry is bound to a different source Skill");
        }
    }
    
    private void verifyVersion(AiResourceVersion version, HubResourceReference reference)
        throws NacosException {
        HubResourceReference stored = HubResourceReferenceJsonUtil.parse(version.getStorage());
        if (!AiResourceConstants.VERSION_STATUS_ONLINE.equals(version.getStatus())
            || !SYSTEM_OWNER.equals(version.getAuthor())
            || !HUB_NAMESPACE.equals(version.getNamespaceId())
            || !SKILL.equals(version.getType())
            || !Objects.equals(version.getName(), HubResourceNameUtils.buildInternalName(
                reference.getSourceName(), reference.getSourceResourceId()))
            || !Objects.equals(version.getVersion(), reference.getSourceVersion())
            || !sameReference(stored, reference)) {
            throw new NacosApiException(NacosException.CONFLICT, ErrorCode.RESOURCE_CONFLICT,
                "Immutable Hub version conflicts with pinned source version");
        }
    }
    
    private boolean expectedVersion(AiResource entry, long expectedMetaVersion) {
        return expectedMetaVersion > 0
            && Objects.equals(entry.getMetaVersion(), expectedMetaVersion);
    }
    
    private boolean sameSource(HubResourceReference left, HubResourceReference right) {
        return Objects.equals(left.getSourceNamespaceId(), right.getSourceNamespaceId())
            && Objects.equals(left.getSourceResourceId(), right.getSourceResourceId())
            && Objects.equals(left.getSourceName(), right.getSourceName());
    }
    
    private boolean sameReference(HubResourceReference left, HubResourceReference right) {
        return sameSource(left, right)
            && Objects.equals(left.getSourceVersionId(), right.getSourceVersionId())
            && Objects.equals(left.getSourceVersion(), right.getSourceVersion());
    }
    
    private void verifySource(HubResourceReference reference) throws NacosException {
        HubResourceReferenceJsonUtil.validate(reference);
        if (HUB_NAMESPACE.equals(reference.getSourceNamespaceId())) {
            throw new NacosApiException(NacosException.INVALID_PARAM,
                ErrorCode.ILLEGAL_NAMESPACE, "Hub namespace cannot be a share source");
        }
    }
    
    private AiResource copyMutableMeta(AiResource meta) {
        AiResource next = new AiResource();
        next.setStatus(meta.getStatus());
        next.setDesc(meta.getDesc());
        next.setBizTags(meta.getBizTags());
        next.setExt(meta.getExt());
        next.setVersionInfo(meta.getVersionInfo());
        return next;
    }
}
