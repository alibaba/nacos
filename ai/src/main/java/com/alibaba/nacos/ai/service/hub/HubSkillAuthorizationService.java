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

import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.api.exception.NacosException;
import org.springframework.stereotype.Service;

/**
 * Authorization coordinator for Hub Skill mutations.
 *
 * <p>Normal operations are authorized against the original source Skill,
 * never the system-owned Hub meta row. This class does not validate source
 * identity, scope, lifecycle, or version eligibility; its callers must use
 * {@link HubSkillEligibilityValidator} and recheck before commit.</p>
 */
@Service
public class HubSkillAuthorizationService {
    
    private final HubSkillPermissionGuard guard;
    
    public HubSkillAuthorizationService(HubSkillPermissionGuard guard) {
        this.guard = guard;
    }
    
    /**
     * Require authenticated source READ and WRITE before sharing a Skill.
     *
     * @param sourceMeta current source Skill row, never the Hub row
     * @throws NacosException on permission denial
     */
    public void requireSharePermission(AiResource sourceMeta) throws NacosException {
        guard.requireSourceRead(sourceMeta);
        guard.requireSourceWrite(sourceMeta);
    }
    
    /**
     * Require authenticated source WRITE before switching shared versions.
     *
     * @param sourceMeta current source Skill row, never the Hub row
     * @throws NacosException on permission denial
     */
    public void requireAdjustPermission(AiResource sourceMeta) throws NacosException {
        guard.requireSourceWrite(sourceMeta);
    }
    
    /**
     * Require authenticated source WRITE for ordinary cancellation.
     *
     * <p>The source may be disabled, private, or have an offline version;
     * permissions are still evaluated on its original identity.</p>
     *
     * @param sourceMeta current source Skill row, never the Hub row
     * @throws NacosException on permission denial
     */
    public void requireUnsharePermission(AiResource sourceMeta) throws NacosException {
        guard.requireSourceWrite(sourceMeta);
    }
    
    /**
     * Require an independently granted Hub governance permission.
     *
     * <p>This path deliberately does not use the Hub row owner or source WRITE.
     * It is for administrator takedowns only, not source version adjustments.</p>
     *
     * @throws NacosException on permission denial
     */
    public void requireForceUnsharePermission() throws NacosException {
        guard.requireAdministrator();
    }
}
