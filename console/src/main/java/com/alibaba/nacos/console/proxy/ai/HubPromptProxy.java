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

package com.alibaba.nacos.console.proxy.ai;

import com.alibaba.nacos.api.ai.model.hub.HubPromptImportRequest;
import com.alibaba.nacos.api.ai.model.hub.HubResourceTransferResult;

import com.alibaba.nacos.api.ai.model.hub.HubAdjustShareRequest;
import com.alibaba.nacos.api.ai.model.hub.HubForceUnshareRequest;
import com.alibaba.nacos.api.ai.model.hub.HubResourceShareState;
import com.alibaba.nacos.api.ai.model.hub.HubSharePromptRequest;
import com.alibaba.nacos.api.ai.model.hub.HubShareResult;
import com.alibaba.nacos.api.ai.model.hub.HubUnshareRequest;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.console.handler.ai.HubPromptHandler;

@org.springframework.stereotype.Component
public class HubPromptProxy {
    
    private final HubPromptHandler handler;
    public HubPromptProxy(HubPromptHandler handler) {
        this.handler = handler;
    }
    
    /** Delegate Prompt Hub share. */
    public HubShareResult share(HubSharePromptRequest request) throws NacosException {
        return handler.share(request);
    }
    
    /** Delegate Prompt Hub adjust. */
    public HubShareResult adjust(HubAdjustShareRequest request) throws NacosException {
        return handler.adjust(request);
    }
    
    /** Delegate Prompt Hub unshare. */
    public HubShareResult unshare(HubUnshareRequest request) throws NacosException {
        return handler.unshare(request);
    }
    
    /** Delegate Prompt Hub forceUnshare. */
    public HubShareResult forceUnshare(HubForceUnshareRequest request) throws NacosException {
        return handler.forceUnshare(request);
    }
    
    /** Delegate Prompt Hub sourceState. */
    public HubResourceShareState sourceState(String namespaceId, String name)
        throws NacosException {
        return handler.sourceState(namespaceId, name);
    }
    
    /** Delegate Prompt Hub governanceState. */
    public HubResourceShareState governanceState(Long entryId) throws NacosException {
        return handler.governanceState(entryId);
    }
    
    /** Import the pinned Hub entry into an independent target draft. */
    public HubResourceTransferResult importFromHub(HubPromptImportRequest request)
        throws NacosException {
        return handler.importFromHub(request);
    }
}
