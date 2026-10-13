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

import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.model.AiResourceVersion;
import com.alibaba.nacos.ai.constant.AiResourceConstants;
import java.util.Objects;

/** Immutable exact source identity. A version label is never stored here. */
public final class HubSkillTransferSourceSnapshot {
    
    private final String namespaceId;
    private final String name;
    private final long resourceId;
    private final String version;
    private final long versionId;
    private final String storageDescriptor;
    private final Long hubEntryId;
    private final long metaVersion;
    
    public HubSkillTransferSourceSnapshot(AiResource meta, AiResourceVersion version,
        Long hubEntryId) {
        this.namespaceId = meta.getNamespaceId();
        this.name = meta.getName();
        this.resourceId = meta.getId();
        this.version = version.getVersion();
        this.versionId = version.getId();
        this.storageDescriptor = version.getStorage();
        this.metaVersion = meta.getMetaVersion() == null ? 0 : meta.getMetaVersion();
        this.hubEntryId = hubEntryId;
    }
    
    public String getNamespaceId() {
        return namespaceId;
    }
    
    public String getName() {
        return name;
    }
    
    public long getResourceId() {
        return resourceId;
    }
    
    public String getVersion() {
        return version;
    }
    
    public long getVersionId() {
        return versionId;
    }
    
    public Long getHubEntryId() {
        return hubEntryId;
    }
    
    public long getMetaVersion() {
        return metaVersion;
    }
    
    public String getStorageDescriptor() {
        return storageDescriptor;
    }
    
    /** Compare every pinned source identity and storage descriptor. */
    public boolean matches(AiResource meta, AiResourceVersion row) {
        return meta != null && row != null
            && AiResourceConstants.RESOURCE_TYPE_SKILL.equals(meta.getType())
            && AiResourceConstants.RESOURCE_TYPE_SKILL.equals(row.getType())
            && Objects.equals(meta.getId(), resourceId)
            && Objects.equals(meta.getNamespaceId(), namespaceId)
            && Objects.equals(meta.getName(), name)
            && Objects.equals(row.getId(), versionId)
            && Objects.equals(row.getNamespaceId(), namespaceId)
            && Objects.equals(row.getName(), name)
            && Objects.equals(row.getVersion(), version)
            && Objects.equals(row.getStorage(), storageDescriptor);
    }
}
