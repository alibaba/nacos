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

import com.alibaba.nacos.ai.model.hub.HubResourceType;
import com.alibaba.nacos.api.ai.model.hub.HubResourceTransferRequest;
import com.alibaba.nacos.api.ai.model.hub.HubResourceTransferResult;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import org.springframework.stereotype.Service;

/** Typed HTTP/Maintainer boundary, separate from resource-specific draft creation. */
@Service
public class HubResourceTransferFacade {
    
    private final HubResourceTransferService transfers;
    
    public HubResourceTransferFacade(HubResourceTransferService transfers) {
        this.transfers = transfers;
    }
    
    /** Import just the pinned shared version into an entirely new target resource. */
    public HubResourceTransferResult importFromHub(String resourceType,
        HubResourceTransferRequest request)
        throws NacosException {
        HubResourceType type = type(resourceType);
        String version = HubResourceTransferRequestValidator.validate(resourceType, request, true);
        HubResourceTransferService.Target target = target(request, version);
        HubResourceDraftTransferWriter.CreatedDraft created =
            transfers.importFromHub(type, request.getHubEntryId(), target);
        return result(resourceType, target, created, request.getHubEntryId());
    }
    
    private HubResourceTransferService.Target target(HubResourceTransferRequest request,
        String version) {
        return new HubResourceTransferService.Target(request.getTargetNamespaceId(),
            request.getTargetName(),
            version, request.getMcpRemoteServerConfig());
    }
    
    private HubResourceType type(String type) throws NacosApiException {
        for (HubResourceType supported : HubResourceType.values()) {
            if (supported != HubResourceType.SKILL && supported.resourceType().equals(type)) {
                return supported;
            }
        }
        throw new NacosApiException(400, ErrorCode.PARAMETER_VALIDATE_ERROR,
            "Unsupported transfer resource type");
    }
    
    private HubResourceTransferResult result(String type, HubResourceTransferService.Target target,
        HubResourceDraftTransferWriter.CreatedDraft created, Long entryId) {
        HubResourceTransferResult result = new HubResourceTransferResult();
        result.setResourceType(type);
        result.setTargetNamespaceId(target.namespaceId());
        result.setTargetName(target.name());
        result.setDraftVersion(created.version());
        result.setTargetResourceId(created.resourceId());
        result.setStatus("draft");
        result.setHubEntryId(entryId);
        result.setOrigin(entryId == null ? "DIRECT_COPY" : "HUB");
        return result;
    }
}
