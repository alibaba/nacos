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
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.plugin.visibility.constant.VisibilityConstants;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * S2-3 source-side eligibility check for one *already resolved* Hub publication.
 *
 * <p>Reuses the S1 validator to reload the real source metadata and its precise
 * version row. The source must still be PUBLIC and enabled and the pinned version
 * must still be online, with unchanged database IDs. A name or version string
 * alone can never rebind a share to a deleted/recreated source.</p>
 *
 * <p>This does not authorize the requesting user. In particular, PUBLIC on a
 * source is not itself a cross-namespace read grant. Only an active Hub share,
 * authenticated Hub read admission and this check together allow access.</p>
 */
@Service
public class HubSkillSourceReadValidator {
    
    private final HubSkillEligibilityValidator eligibilityValidator;
    
    public HubSkillSourceReadValidator(HubSkillEligibilityValidator eligibilityValidator) {
        this.eligibilityValidator = eligibilityValidator;
    }
    
    /**
     * Recheck the exact currently published source Skill and version.
     *
     * @param publication S2-2 pinned Hub snapshot, never user-supplied source fields
     * @return fresh source metadata for *internal* use only; never serialize its ext
     * @throws NacosException if the source is no longer eligible or identities differ
     */
    public AiResource requireLiveSource(ResolvedHubSkillReference publication)
        throws NacosException {
        return qualifySource(publication, false).getSourceMeta();
    }
    
    /**
     * Check a derived index without mistaking a storage outage for revocation.
     *
     * @param publication pinned Hub publication
     * @return live source metadata
     * @throws NacosException on invalid source or unavailable storage
     */
    public AiResource requireLiveSourceForIndex(ResolvedHubSkillReference publication)
        throws NacosException {
        return qualifySource(publication, true).getSourceMeta();
    }
    
    /**
     * Return both fresh metadata and the validated immutable shared version.
     *
     * @param publication resolved Hub publication
     * @return eligible source snapshot, for internal use only
     * @throws NacosException if the publication is no longer eligible
     */
    public HubEligibleSkillVersion requireLiveVersion(ResolvedHubSkillReference publication)
        throws NacosException {
        return qualifySource(publication, false);
    }
    
    private HubEligibleSkillVersion qualifySource(ResolvedHubSkillReference publication,
        boolean preserveStorageFailure)
        throws NacosException {
        if (publication == null) {
            throw unavailable();
        }
        try {
            HubResourceReference expected = publication.toReference();
            AiResource initial = eligibilityValidator.requireReferencedSource(expected);
            HubEligibleSkillVersion eligible = eligibilityValidator.requireShareableVersion(
                initial, publication.getSourceVersion());
            if (eligible == null || eligible.getSourceMeta() == null
                || eligible.getSourceVersion() == null
                || !sameReference(expected, eligible.getReference())) {
                throw unavailable();
            }
            // Source scope/status may change while the pinned version is loaded.
            AiResource current = eligibilityValidator.requireReferencedSource(expected);
            if (!VisibilityConstants.SCOPE_PUBLIC.equals(current.getScope())
                || !AiResourceConstants.META_STATUS_ENABLE.equals(current.getStatus())
                || !sameVersion(expected, eligible.getSourceVersion())) {
                throw unavailable();
            }
            return new HubEligibleSkillVersion(current, eligible.getSourceVersion(), expected);
        } catch (NacosException | RuntimeException invalidSource) {
            if (preserveStorageFailure && (invalidSource instanceof RuntimeException
                || ((NacosException) invalidSource).getErrCode() >= NacosException.SERVER_ERROR)) {
                throw new NacosApiException(NacosException.SERVER_ERROR, ErrorCode.SERVER_ERROR,
                    "Hub Skill source validation unavailable");
            }
            // An absent, withdrawn, private, offline or replaced source must be
            // indistinguishable. Do not leak internal IDs or source storage paths.
            throw unavailable();
        }
    }
    
    private boolean sameReference(HubResourceReference expected, HubResourceReference actual) {
        return actual != null
            && Objects.equals(expected.getSourceNamespaceId(), actual.getSourceNamespaceId())
            && Objects.equals(expected.getSourceResourceId(), actual.getSourceResourceId())
            && Objects.equals(expected.getSourceName(), actual.getSourceName())
            && Objects.equals(expected.getSourceVersionId(), actual.getSourceVersionId())
            && Objects.equals(expected.getSourceVersion(), actual.getSourceVersion());
    }
    
    private boolean sameVersion(HubResourceReference expected, AiResourceVersion actual) {
        return AiResourceConstants.RESOURCE_TYPE_SKILL.equals(actual.getType())
            && AiResourceConstants.VERSION_STATUS_ONLINE.equals(actual.getStatus())
            && Objects.equals(expected.getSourceNamespaceId(), actual.getNamespaceId())
            && Objects.equals(expected.getSourceName(), actual.getName())
            && Objects.equals(expected.getSourceVersionId(), actual.getId())
            && Objects.equals(expected.getSourceVersion(), actual.getVersion());
    }
    
    private NacosApiException unavailable() {
        return new NacosApiException(NacosException.NOT_FOUND,
            ErrorCode.RESOURCE_NOT_FOUND, "Hub Skill not found or unavailable");
    }
}
