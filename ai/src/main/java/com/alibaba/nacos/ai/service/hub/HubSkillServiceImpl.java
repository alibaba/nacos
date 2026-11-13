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
import org.springframework.stereotype.Service;

/**
 * Server-only implementation of the S1 Hub Skill mutation contract.
 *
 * <p>API handlers added in a later stage must invoke this service and must
 * never bypass source authorization or modify Hub rows directly.</p>
 */
@Service
public class HubSkillServiceImpl implements HubSkillService {
    
    private final HubSkillShareService shareService;
    
    private final HubSkillAdjustService adjustService;
    
    private final HubSkillUnshareService unshareService;
    
    public HubSkillServiceImpl(HubSkillShareService shareService,
        HubSkillAdjustService adjustService, HubSkillUnshareService unshareService) {
        this.shareService = shareService;
        this.adjustService = adjustService;
        this.unshareService = unshareService;
    }
    
    @Override
    public HubShareResult shareSkill(HubShareSkillRequest request) throws NacosException {
        return shareService.shareSkill(request);
    }
    
    @Override
    public HubShareResult adjustShare(HubAdjustShareRequest request) throws NacosException {
        return adjustService.adjustShare(request);
    }
    
    @Override
    public HubShareResult unshareSkill(HubUnshareRequest request) throws NacosException {
        return unshareService.unshareSkill(request);
    }
    
    @Override
    public HubShareResult forceUnshare(HubForceUnshareRequest request) throws NacosException {
        return unshareService.forceUnshare(request);
    }
}
