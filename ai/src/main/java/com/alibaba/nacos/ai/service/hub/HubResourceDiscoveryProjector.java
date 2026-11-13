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
import com.alibaba.nacos.api.ai.model.hub.HubResourceDiscoveryInfo;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.response.Namespace;
import com.alibaba.nacos.common.utils.NamespaceUtil;
import com.alibaba.nacos.core.service.NamespaceOperationService;
import org.springframework.stereotype.Service;

/** Public Hub discovery metadata, with independently authorized source navigation and no storage coordinates. */
@Service
public class HubResourceDiscoveryProjector {
    
    private final NamespaceOperationService namespaces;
    
    private final HubSkillReadPermissionGuard permissions;
    
    public HubResourceDiscoveryProjector(NamespaceOperationService namespaces,
        HubSkillReadPermissionGuard permissions) {
        this.namespaces = namespaces;
        this.permissions = permissions;
    }
    
    /** Project the original display identity alongside the opaque typed entry coordinate. */
    public HubResourceDiscoveryInfo project(HubResourceType type,
        HubResourceDirectoryService.Entry entry)
        throws NacosException {
        HubResourceDiscoveryInfo result = new HubResourceDiscoveryInfo();
        result.setHubEntryId(entry.hub().getId());
        result.setResourceType(type.resourceType());
        result.setResourceName(entry.reference().getSourceName());
        result.setVersion(entry.reference().getSourceVersion());
        result.setAuthor(entry.source().getOwner());
        result.setDownloadCount(entry.hub().getDownloadCount());
        result.setUpdateTime(entry.hub().getGmtModified() == null ? null
            : entry.hub().getGmtModified().getTime());
        try {
            if (permissions.canReadSource(type, entry.source())) {
                result.setSourceReadable(true);
                result.setSourceNamespaceId(entry.reference().getSourceNamespaceId());
                result.setSourceName(entry.reference().getSourceName());
            }
        } catch (NacosException deniedSource) {
            if (deniedSource.getErrCode() != NacosException.NO_RIGHT) {
                throw deniedSource;
            }
        }
        String sourceNamespace = entry.reference().getSourceNamespaceId();
        if (NamespaceUtil.isDefaultNamespaceId(sourceNamespace)) {
            result.setSourceDisplayName("public");
        } else {
            try {
                Namespace namespace = namespaces.getNamespace(sourceNamespace);
                result.setSourceDisplayName(
                    namespace == null ? null : namespace.getNamespaceShowName());
            } catch (NacosException missing) {
                if (missing.getErrCode() != NacosException.NOT_FOUND) {
                    throw missing;
                }
            }
        }
        return result;
    }
}
