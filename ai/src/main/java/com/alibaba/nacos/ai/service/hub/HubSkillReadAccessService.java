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
import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import org.springframework.stereotype.Service;

/**
 * S2-3 read gate for Hub entry snapshots. This is a server-internal service.
 *
 * <p>A Hub reference is an explicit, version-scoped public grant only while the
 * source resource and the immutable pinned version are still eligible. This
 * gate enforces authenticated caller admission separately from source READ,
 * then verifies the real source and the unchanged Hub publication.</p>
 *
 * <p>Before sending content, S2-7 must call {@link #requireStillReadable}
 * after loading the source bytes. The checks reduce but cannot completely
 * eliminate read/revoke races without a cross-namespace transaction.</p>
 */
@Service
public class HubSkillReadAccessService {
    
    private final HubSkillReadPermissionGuard permissionGuard;
    
    private final HubSkillSourceReadValidator sourceValidator;
    
    private final HubSkillReferenceResolver resolver;
    
    public HubSkillReadAccessService(HubSkillReadPermissionGuard permissionGuard,
        HubSkillSourceReadValidator sourceValidator, HubSkillReferenceResolver resolver) {
        this.permissionGuard = permissionGuard;
        this.sourceValidator = sourceValidator;
        this.resolver = resolver;
    }
    
    /**
     * Safe entry point for S2-6/S2-7 HTTP detail/content handlers: authorize
     * the caller BEFORE looking up an entry ID, avoiding existence probes by
     * unauthenticated or untrusted callers.
     *
     * @param hubEntryId server-generated Hub entry ID, not a source resource ID
     * @return verified internal publication snapshot (never serialize directly)
     * @throws NacosException for missing permission or an unavailable share
     */
    public ResolvedHubSkillReference resolveReadable(Long hubEntryId) throws NacosException {
        HubConfig.requireEnabled();
        permissionGuard.requireHubRead();
        ResolvedHubSkillReference publication = resolver.resolve(hubEntryId);
        sourceValidator.requireLiveSource(publication);
        resolver.requireUnchanged(publication);
        return publication;
    }
    
    /**
     * Check admission, current source and current Hub pointer before listing,
     * showing details, or starting the fixed-version content load.
     *
     * @param publication resolved S2-2 snapshot (not caller-controlled fields)
     * @throws NacosException if the caller or share is no longer allowed
     */
    public void requireReadable(ResolvedHubSkillReference publication) throws NacosException {
        requireReadableSource(publication);
    }
    
    /**
     * Validate the publication and return source metadata for internal projection.
     *
     * @param publication immutable Hub publication snapshot
     * @return currently eligible source metadata, never a public response
     * @throws NacosException when the caller or publication is unavailable
     */
    public AiResource requireReadableSource(ResolvedHubSkillReference publication)
        throws NacosException {
        HubConfig.requireEnabled();
        if (publication == null) {
            throw unavailable();
        }
        permissionGuard.requireHubRead();
        AiResource source = sourceValidator.requireLiveSource(publication);
        resolver.requireUnchanged(publication);
        return source;
    }
    
    /**
     * Final revocation fence AFTER retrieving content, immediately before return.
     * Rechecks the Hub pointer on both sides of fresh source validation.
     *
     * @param publication the original publication used for the content lookup
     * @throws NacosException when permissions, source, or Hub state were revoked
     */
    public void requireStillReadable(ResolvedHubSkillReference publication) throws NacosException {
        HubConfig.requireEnabled();
        if (publication == null) {
            throw unavailable();
        }
        permissionGuard.requireHubRead();
        resolver.requireUnchanged(publication);
        sourceValidator.requireLiveSource(publication);
        resolver.requireUnchanged(publication);
    }
    
    /**
     * Check whether the caller also has independent source Namespace READ for
     * an optional source-detail deep link. A false return does not revoke Hub
     * content access. Never return source identifiers when this is false.
     *
     * @param publication pinned Hub reference already resolved by S2-2
     * @return whether normal source navigation is permitted
     * @throws NacosException when the Hub read itself is no longer permitted
     */
    public boolean canReadSourceDetail(ResolvedHubSkillReference publication)
        throws NacosException {
        HubConfig.requireEnabled();
        if (publication == null) {
            throw unavailable();
        }
        permissionGuard.requireHubRead();
        AiResource source = sourceValidator.requireLiveSource(publication);
        resolver.requireUnchanged(publication);
        boolean permitted = permissionGuard.canReadSource(source);
        requireStillReadable(publication);
        return permitted;
    }
    
    /**
     * Authorize and load the exact shared source version for detail projection.
     *
     * @param publication resolved immutable publication
     * @return validated metadata and version snapshot; never serialize directly
     * @throws NacosException if authorization or eligibility is revoked
     */
    public HubEligibleSkillVersion requireReadableVersion(ResolvedHubSkillReference publication)
        throws NacosException {
        HubConfig.requireEnabled();
        permissionGuard.requireHubRead();
        HubEligibleSkillVersion source = sourceValidator.requireLiveVersion(publication);
        resolver.requireUnchanged(publication);
        return source;
    }
    
    private NacosApiException unavailable() {
        return new NacosApiException(NacosException.NOT_FOUND,
            ErrorCode.RESOURCE_NOT_FOUND, "Hub Skill not found or unavailable");
    }
}
