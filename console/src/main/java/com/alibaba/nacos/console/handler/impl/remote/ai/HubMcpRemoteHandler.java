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

import com.alibaba.nacos.api.ai.model.hub.HubMcpImportRequest;
import com.alibaba.nacos.api.ai.model.hub.HubResourceTransferResult;

import com.alibaba.nacos.api.ai.model.hub.HubAdjustShareRequest;
import com.alibaba.nacos.api.ai.model.hub.HubForceUnshareRequest;
import com.alibaba.nacos.api.ai.model.hub.HubResourceShareState;
import com.alibaba.nacos.api.ai.model.hub.HubShareMcpRequest;
import com.alibaba.nacos.api.ai.model.hub.HubShareResult;
import com.alibaba.nacos.api.ai.model.hub.HubUnshareRequest;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.console.handler.ai.EnabledAiHandler;
import com.alibaba.nacos.console.handler.ai.HubMcpHandler;
import com.alibaba.nacos.console.handler.impl.remote.EnabledRemoteHandler;
import com.alibaba.nacos.console.handler.impl.remote.NacosMaintainerClientHolder;
import org.springframework.stereotype.Component;

/** Remote Console forwards the original user, never a Console service identity. */
@Component
@EnabledRemoteHandler
@EnabledAiHandler
public class HubMcpRemoteHandler implements HubMcpHandler {
    
    private final NacosMaintainerClientHolder holder;
    private final HubRemoteCallerGuard callers;
    
    public HubMcpRemoteHandler(NacosMaintainerClientHolder holder,
        HubRemoteCallerGuard callers) {
        this.holder = holder;
        this.callers = callers;
    }
    
    @Override
    public HubShareResult share(HubShareMcpRequest request)
        throws NacosException {
        callers.requireForwardableUser();
        return holder.getAiMaintainerService().mcp().shareHubMcp(request);
    }
    
    @Override
    public HubShareResult adjust(HubAdjustShareRequest request)
        throws NacosException {
        callers.requireForwardableUser();
        return holder.getAiMaintainerService().mcp().adjustHubMcpShare(request);
    }
    
    @Override
    public HubShareResult unshare(HubUnshareRequest request)
        throws NacosException {
        callers.requireForwardableUser();
        return holder.getAiMaintainerService().mcp().unshareHubMcp(request);
    }
    
    @Override
    public HubShareResult forceUnshare(HubForceUnshareRequest request)
        throws NacosException {
        callers.requireForwardableUser();
        return holder.getAiMaintainerService().mcp().forceUnshareHubMcp(request);
    }
    
    @Override
    public HubResourceShareState sourceState(String namespaceId, String name)
        throws NacosException {
        callers.requireForwardableUser();
        return holder.getAiMaintainerService().mcp().getHubMcpSourceState(namespaceId, name);
    }
    
    @Override
    public HubResourceShareState governanceState(Long entryId)
        throws NacosException {
        callers.requireForwardableUser();
        return holder.getAiMaintainerService().mcp().getHubMcpGovernanceState(entryId);
    }
    
    /** Import the pinned Hub entry into an independent target draft. */
    @Override
    public HubResourceTransferResult importFromHub(HubMcpImportRequest request)
        throws NacosException {
        callers.requireForwardableUser();
        return holder.getAiMaintainerService().mcp().importHubMcp(request);
    }
}
