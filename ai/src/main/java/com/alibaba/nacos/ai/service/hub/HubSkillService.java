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

import com.alibaba.nacos.api.ai.model.hub.HubAdjustShareRequest;
import com.alibaba.nacos.api.ai.model.hub.HubForceUnshareRequest;
import com.alibaba.nacos.api.ai.model.hub.HubShareResult;
import com.alibaba.nacos.api.ai.model.hub.HubShareSkillRequest;
import com.alibaba.nacos.api.ai.model.hub.HubUnshareRequest;
import com.alibaba.nacos.api.exception.NacosException;

/**
 * Service contract for sharing one fixed source Skill version into the AI Resource Hub.
 *
 * <p>All operations are authorized against the source Skill, except the separately
 * authorized administrative takedown. Ordinary Skill APIs must not be used for Hub rows.</p>
 *
 * @author nacos
 */
public interface HubSkillService {
    
    /**
     * Share an explicit online source Skill version, idempotently for the same source ID
     * and source version row ID. A different version requires {@link #adjustShare}.
     *
     * @param request source resource and version
     * @return stable Hub entry ID and outcome
     * @throws NacosException for invalid state, permission or persistence conflicts
     */
    HubShareResult shareSkill(HubShareSkillRequest request) throws NacosException;
    
    /**
     * Change the effective pinned source version using the expected Hub meta version.
     *
     * @param request Hub entry ID, exact new version and expected meta version
     * @return current Hub share state
     * @throws NacosException for failed eligibility checks or CAS conflicts
     */
    HubShareResult adjustShare(HubAdjustShareRequest request) throws NacosException;
    
    /**
     * Disable a Hub reference. The source Skill remains unchanged.
     *
     * @param request Hub entry ID and expected meta version
     * @return inactive Hub share state
     * @throws NacosException for missing authorization or CAS conflicts
     */
    HubShareResult unshareSkill(HubUnshareRequest request) throws NacosException;
    
    /**
     * Administratively disable a Hub reference with an auditable reason.
     *
     * @param request Hub entry ID, expected meta version and reason
     * @return inactive Hub share state
     * @throws NacosException for insufficient admin authority or CAS conflicts
     */
    HubShareResult forceUnshare(HubForceUnshareRequest request) throws NacosException;
}
