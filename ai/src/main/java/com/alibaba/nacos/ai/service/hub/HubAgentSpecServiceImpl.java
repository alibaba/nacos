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

import com.alibaba.nacos.api.ai.model.hub.HubAgentSpecImportRequest;
import com.alibaba.nacos.api.ai.model.hub.HubResourceTransferResult;

import com.alibaba.nacos.api.ai.model.hub.HubAdjustShareRequest;
import com.alibaba.nacos.api.ai.model.hub.HubForceUnshareRequest;
import com.alibaba.nacos.api.ai.model.hub.HubResourceShareState;
import com.alibaba.nacos.api.ai.model.hub.HubShareAgentSpecRequest;
import com.alibaba.nacos.api.ai.model.hub.HubShareResult;
import com.alibaba.nacos.api.ai.model.hub.HubUnshareRequest;
import com.alibaba.nacos.api.exception.NacosException;

@org.springframework.stereotype.Service
public class HubAgentSpecServiceImpl implements HubAgentSpecService {
    
    private HubResourceTransferFacade transfers;
    @org.springframework.beans.factory.annotation.Autowired
    public void setTransfers(HubResourceTransferFacade transfers) {
        this.transfers = transfers;
    }
    
    private final HubResourceManagementService publications;
    public HubAgentSpecServiceImpl(HubResourceManagementService publications) {
        this.publications = publications;
    }
    
    @Override
    public HubShareResult share(HubShareAgentSpecRequest request) throws NacosException {
        return publications.share("agentspec", request);
    }
    
    @Override
    public HubShareResult adjust(HubAdjustShareRequest request) throws NacosException {
        return publications.adjust("agentspec", request);
    }
    
    @Override
    public HubShareResult unshare(HubUnshareRequest request) throws NacosException {
        return publications.unshare("agentspec", request);
    }
    
    @Override
    public HubShareResult forceUnshare(HubForceUnshareRequest request) throws NacosException {
        return publications.forceUnshare("agentspec", request);
    }
    
    @Override
    public HubResourceShareState sourceState(String namespaceId, String name)
        throws NacosException {
        return publications.sourceState("agentspec", namespaceId, name);
    }
    
    @Override
    public HubResourceShareState governanceState(Long entryId) throws NacosException {
        return publications.governanceState("agentspec", entryId);
    }
    
    /** Import the pinned Hub entry into an independent target draft. */
    @Override
    public HubResourceTransferResult importFromHub(HubAgentSpecImportRequest request)
        throws NacosException {
        return transfers.importFromHub("agentspec", request);
    }
}
