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

import com.alibaba.nacos.ai.service.hub.HubSkillReadRequestValidator;
import com.alibaba.nacos.api.ai.model.hub.HubSkillContent;
import com.alibaba.nacos.api.ai.model.hub.HubSkillDownload;
import com.alibaba.nacos.api.ai.model.hub.HubSkillDetail;
import com.alibaba.nacos.api.ai.model.hub.HubSkillListRequest;
import com.alibaba.nacos.api.ai.model.hub.HubSkillSummary;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.console.handler.ai.EnabledAiHandler;
import com.alibaba.nacos.console.handler.ai.HubSkillReadHandler;
import com.alibaba.nacos.console.handler.impl.remote.EnabledRemoteHandler;
import com.alibaba.nacos.console.handler.impl.remote.NacosMaintainerClientHolder;
import org.springframework.stereotype.Service;

/** Remote mode must forward the verified original caller; no server-identity fallback. */
@Service
@EnabledRemoteHandler
@EnabledAiHandler
public class HubSkillReadRemoteHandler implements HubSkillReadHandler {
    
    private final NacosMaintainerClientHolder clientHolder;
    private final HubRemoteCallerGuard guard;
    
    public HubSkillReadRemoteHandler(NacosMaintainerClientHolder clientHolder,
        HubRemoteCallerGuard guard) {
        this.clientHolder = clientHolder;
        this.guard = guard;
    }
    
    @Override
    public Page<HubSkillSummary> list(HubSkillListRequest request) throws NacosException {
        guard.requireForwardableUser();
        HubSkillReadRequestValidator.validate(request);
        return clientHolder.getAiMaintainerService().skill().listHubSkills(request);
    }
    
    @Override
    public HubSkillDetail detail(Long id) throws NacosException {
        guard.requireForwardableUser();
        HubSkillReadRequestValidator.validateEntryId(id);
        return clientHolder.getAiMaintainerService().skill().getHubSkillDetail(id);
    }
    
    @Override
    public HubSkillContent content(Long id) throws NacosException {
        guard.requireForwardableUser();
        HubSkillReadRequestValidator.validateEntryId(id);
        return clientHolder.getAiMaintainerService().skill().getHubSkillContent(id);
    }
    
    @Override
    public HubSkillDownload download(Long hubEntryId) throws NacosException {
        guard.requireForwardableUser();
        HubSkillReadRequestValidator.validateEntryId(hubEntryId);
        return clientHolder.getAiMaintainerService().skill().getHubSkillDownload(hubEntryId);
    }
}
