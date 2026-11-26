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

package com.alibaba.nacos.console.handler.impl.inner.ai;

import com.alibaba.nacos.api.ai.model.hub.HubAgentSpecImportRequest;
import com.alibaba.nacos.api.ai.model.hub.HubResourceTransferResult;

import com.alibaba.nacos.api.ai.model.hub.HubAdjustShareRequest;
import com.alibaba.nacos.api.ai.model.hub.HubForceUnshareRequest;
import com.alibaba.nacos.api.ai.model.hub.HubResourceShareState;
import com.alibaba.nacos.api.ai.model.hub.HubShareAgentSpecRequest;
import com.alibaba.nacos.api.ai.model.hub.HubShareResult;
import com.alibaba.nacos.api.ai.model.hub.HubUnshareRequest;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.ai.service.hub.HubAgentSpecService;
import com.alibaba.nacos.console.handler.ai.EnabledAiHandler;
import com.alibaba.nacos.console.handler.ai.HubAgentSpecHandler;
import com.alibaba.nacos.console.handler.impl.inner.EnabledInnerHandler;
import org.springframework.stereotype.Component;

/** Local Console retains the authenticated end-user request context. */
@Component
@EnabledInnerHandler
@EnabledAiHandler
public class HubAgentSpecInnerHandler implements HubAgentSpecHandler {
    
    private final HubAgentSpecService service;
    
    public HubAgentSpecInnerHandler(HubAgentSpecService service) {
        this.service = service;
    }
    
    @Override
    public HubShareResult share(HubShareAgentSpecRequest request)
        throws NacosException {
        return service.share(request);
    }
    
    @Override
    public HubShareResult adjust(HubAdjustShareRequest request)
        throws NacosException {
        return service.adjust(request);
    }
    
    @Override
    public HubShareResult unshare(HubUnshareRequest request)
        throws NacosException {
        return service.unshare(request);
    }
    
    @Override
    public HubShareResult forceUnshare(HubForceUnshareRequest request)
        throws NacosException {
        return service.forceUnshare(request);
    }
    
    @Override
    public HubResourceShareState sourceState(String namespaceId, String name)
        throws NacosException {
        return service.sourceState(namespaceId, name);
    }
    
    @Override
    public HubResourceShareState governanceState(Long entryId)
        throws NacosException {
        return service.governanceState(entryId);
    }
    
    /** Import the pinned Hub entry into an independent target draft. */
    @Override
    public HubResourceTransferResult importFromHub(HubAgentSpecImportRequest request)
        throws NacosException {
        return service.importFromHub(request);
    }
}
