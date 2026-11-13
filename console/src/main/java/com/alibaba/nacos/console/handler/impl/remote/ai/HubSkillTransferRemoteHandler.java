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

import com.alibaba.nacos.api.ai.model.hub.HubSkillTransferRequest;
import com.alibaba.nacos.api.ai.model.hub.HubSkillTransferResult;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.console.handler.ai.EnabledAiHandler;
import com.alibaba.nacos.console.handler.ai.HubSkillTransferHandler;
import com.alibaba.nacos.console.handler.impl.remote.EnabledRemoteHandler;
import com.alibaba.nacos.console.handler.impl.remote.NacosMaintainerClientHolder;
import org.springframework.stereotype.Service;

/** The original Console user credentials must be forwardable to Admin. */
@Service
@EnabledRemoteHandler
@EnabledAiHandler
public class HubSkillTransferRemoteHandler implements HubSkillTransferHandler {
    
    private final NacosMaintainerClientHolder holder;
    private final HubRemoteCallerGuard caller;
    
    public HubSkillTransferRemoteHandler(NacosMaintainerClientHolder holder,
        HubRemoteCallerGuard caller) {
        this.holder = holder;
        this.caller = caller;
    }
    
    @Override
    public HubSkillTransferResult importFromHub(HubSkillTransferRequest request)
        throws NacosException {
        caller.requireForwardableUser();
        return holder.getAiMaintainerService().skill().importHubSkill(request);
    }
    
    @Override
    public HubSkillTransferResult copyDirect(HubSkillTransferRequest request)
        throws NacosException {
        caller.requireForwardableUser();
        return holder.getAiMaintainerService().skill().copyHubSkill(request);
    }
}
