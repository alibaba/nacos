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
import com.alibaba.nacos.ai.model.hub.HubResourceReference;
import com.alibaba.nacos.ai.service.repository.HubEntryStorage;
import com.alibaba.nacos.ai.utils.HubResourceNameUtils;
import com.alibaba.nacos.ai.utils.HubResourceReferenceJsonUtil;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Called only AFTER ordinary Skill mutation authorization, and BEFORE the
 * destructive source operation. Called again after completion/failure so a
 * concurrent share that raced the first fence cannot remain enabled.
 *
 * <p>Failure of the first fence aborts the source mutation. If the subsequent
 * source operation fails, leaving an inactive Hub share is acceptable; never
 * revive a publication automatically. Final read-time validation is mandatory.</p>
 */
@Service
public class HubSkillSourceLifecycleRevoker {
    
    private final HubEntryStorage storage;
    
    private final HubSkillPublicationInvalidator invalidator;
    
    /**
     * Create the source lifecycle revoker.
     *
     * @param storage Hub metadata storage
     * @param invalidator publication invalidator
     */
    public HubSkillSourceLifecycleRevoker(HubEntryStorage storage,
        HubSkillPublicationInvalidator invalidator) {
        this.storage = storage;
        this.invalidator = invalidator;
    }
    
    /** Disable all shares for this precise source row before/after deletion, disable or PRIVATE. */
    public boolean revokeSource(AiResource source) throws NacosException {
        return revoke(source, null);
    }
    
    /** Disable only the currently shared pinned version, not an unrelated version. */
    public boolean revokeVersion(AiResource source, String offlineVersion) throws NacosException {
        if (offlineVersion == null || offlineVersion.isBlank()) {
            throw new NacosException(NacosException.INVALID_PARAM,
                "Explicit source version required for version takedown");
        }
        return revoke(source, offlineVersion);
    }
    
    private boolean revoke(AiResource source, String offlineVersion) throws NacosException {
        if (source == null || source.getId() == null || source.getId() <= 0
            || !AiResourceConstants.RESOURCE_TYPE_SKILL.equals(source.getType())
            || source.getNamespaceId() == null || source.getName() == null
            || AiConstants.Hub.NAMESPACE_ID.equals(source.getNamespaceId())) {
            throw new NacosException(NacosException.INVALID_PARAM,
                "Source lifecycle revocation requires a real Skill row");
        }
        String sourceNamespaceId = source.getNamespaceId();
        long sourceId = source.getId();
        String sourceName = source.getName();
        String internalName = HubResourceNameUtils.buildInternalName(sourceName, sourceId);
        AiResource observed;
        try {
            observed = storage.findByInternalName(internalName);
        } catch (RuntimeException storageFailure) {
            NacosException error = new NacosApiException(NacosException.SERVER_ERROR,
                ErrorCode.SERVER_ERROR, "Hub publication storage unavailable");
            error.initCause(storageFailure);
            throw error;
        }
        if (observed == null) {
            return false;
        }
        if (observed.getId() == null) {
            throw new NacosApiException(NacosException.SERVER_ERROR, ErrorCode.SERVER_ERROR,
                "Hub publication identity is inconsistent");
        }
        return invalidator.disableIf(observed.getId(),
            current -> internalName.equals(current.getName()) && referencesAffectedSource(
                current, sourceNamespaceId, sourceId, sourceName, offlineVersion));
    }
    
    private boolean referencesAffectedSource(AiResource entry, String sourceNamespaceId,
        long sourceId, String sourceName, String offlineVersion) {
        try {
            HubResourceReference ref = HubResourceReferenceJsonUtil.parse(entry.getExt());
            return Objects.equals(ref.getSourceNamespaceId(), sourceNamespaceId)
                && Objects.equals(ref.getSourceResourceId(), sourceId)
                && Objects.equals(ref.getSourceName(), sourceName)
                && (offlineVersion == null
                    || Objects.equals(ref.getSourceVersion(), offlineVersion));
        } catch (NacosException | RuntimeException malformed) {
            // The canonical internal key is derived from the globally unique
            // source resource ID. A broken descriptor must never leave an
            // enabled orphan visible merely because we cannot parse it.
            return true;
        }
    }
}
