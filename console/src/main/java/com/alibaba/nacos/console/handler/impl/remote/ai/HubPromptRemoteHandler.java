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

import com.alibaba.nacos.api.ai.model.hub.HubPromptImportRequest;
import com.alibaba.nacos.api.ai.model.hub.HubResourceTransferResult;

import com.alibaba.nacos.api.ai.model.hub.HubAdjustShareRequest;
import com.alibaba.nacos.api.ai.model.hub.HubForceUnshareRequest;
import com.alibaba.nacos.api.ai.model.hub.HubResourceShareState;
import com.alibaba.nacos.api.ai.model.hub.HubSharePromptRequest;
import com.alibaba.nacos.api.ai.model.hub.HubShareResult;
import com.alibaba.nacos.api.ai.model.hub.HubUnshareRequest;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.console.handler.ai.EnabledAiHandler;
import com.alibaba.nacos.console.handler.ai.HubPromptHandler;
import com.alibaba.nacos.console.handler.impl.remote.EnabledRemoteHandler;
import com.alibaba.nacos.console.handler.impl.remote.NacosMaintainerClientHolder;
import org.springframework.stereotype.Component;

/** Remote Console forwards the original user, never a Console service identity. */
@Component
@EnabledRemoteHandler
@EnabledAiHandler
public class HubPromptRemoteHandler implements HubPromptHandler {
    
    private final NacosMaintainerClientHolder holder;
    private final HubRemoteCallerGuard callers;
    
    public HubPromptRemoteHandler(NacosMaintainerClientHolder holder,
        HubRemoteCallerGuard callers) {
        this.holder = holder;
        this.callers = callers;
    }
    
    @Override
    public HubShareResult share(HubSharePromptRequest request)
        throws NacosException {
        callers.requireForwardableUser();
        return holder.getAiMaintainerService().prompt().shareHubPrompt(request);
    }
    
    @Override
    public HubShareResult adjust(HubAdjustShareRequest request)
        throws NacosException {
        callers.requireForwardableUser();
        return holder.getAiMaintainerService().prompt().adjustHubPromptShare(request);
    }
    
    @Override
    public HubShareResult unshare(HubUnshareRequest request)
        throws NacosException {
        callers.requireForwardableUser();
        return holder.getAiMaintainerService().prompt().unshareHubPrompt(request);
    }
    
    @Override
    public HubShareResult forceUnshare(HubForceUnshareRequest request)
        throws NacosException {
        callers.requireForwardableUser();
        return holder.getAiMaintainerService().prompt().forceUnshareHubPrompt(request);
    }
    
    @Override
    public HubResourceShareState sourceState(String namespaceId, String name)
        throws NacosException {
        callers.requireForwardableUser();
        return holder.getAiMaintainerService().prompt().getHubPromptSourceState(namespaceId, name);
    }
    
    @Override
    public HubResourceShareState governanceState(Long entryId)
        throws NacosException {
        callers.requireForwardableUser();
        return holder.getAiMaintainerService().prompt().getHubPromptGovernanceState(entryId);
    }
    
    /** Import the pinned Hub entry into an independent target draft. */
    @Override
    public HubResourceTransferResult importFromHub(HubPromptImportRequest request)
        throws NacosException {
        callers.requireForwardableUser();
        return holder.getAiMaintainerService().prompt().importHubPrompt(request);
    }
}
