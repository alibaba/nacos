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

package com.alibaba.nacos.console.handler.impl.remote.ai;

import com.alibaba.nacos.api.ai.model.hub.HubAdjustShareRequest;
import com.alibaba.nacos.api.ai.model.hub.HubForceUnshareRequest;
import com.alibaba.nacos.api.ai.model.hub.HubShareResult;
import com.alibaba.nacos.api.ai.model.hub.HubShareSkillRequest;
import com.alibaba.nacos.api.ai.model.hub.HubUnshareRequest;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.console.handler.ai.EnabledAiHandler;
import com.alibaba.nacos.console.handler.ai.HubSkillHandler;
import com.alibaba.nacos.console.handler.impl.remote.EnabledRemoteHandler;
import com.alibaba.nacos.console.handler.impl.remote.NacosMaintainerClientHolder;
import org.springframework.stereotype.Service;

/** Remote Console delegates to Admin API with original caller credentials only. */
@Service
@EnabledRemoteHandler
@EnabledAiHandler
public class HubSkillRemoteHandler implements HubSkillHandler {
    
    private final NacosMaintainerClientHolder clientHolder;
    
    private final HubRemoteCallerGuard callerGuard;
    
    public HubSkillRemoteHandler(NacosMaintainerClientHolder clientHolder,
        HubRemoteCallerGuard callerGuard) {
        this.clientHolder = clientHolder;
        this.callerGuard = callerGuard;
    }
    
    @Override
    public HubShareResult share(HubShareSkillRequest request) throws NacosException {
        callerGuard.requireForwardableUser();
        return clientHolder.getAiMaintainerService().skill().shareHubSkill(request);
    }
    
    @Override
    public HubShareResult adjust(HubAdjustShareRequest request) throws NacosException {
        callerGuard.requireForwardableUser();
        return clientHolder.getAiMaintainerService().skill().adjustHubShare(request);
    }
    
    @Override
    public HubShareResult unshare(HubUnshareRequest request) throws NacosException {
        callerGuard.requireForwardableUser();
        return clientHolder.getAiMaintainerService().skill().unshareHubSkill(request);
    }
    
    @Override
    public HubShareResult forceUnshare(HubForceUnshareRequest request) throws NacosException {
        callerGuard.requireForwardableUser();
        return clientHolder.getAiMaintainerService().skill().forceUnshareHubSkill(request);
    }
}
