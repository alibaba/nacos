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
import com.alibaba.nacos.ai.model.hub.HubResourceReference;
import com.alibaba.nacos.ai.utils.HubResourceReferenceJsonUtil;
import com.alibaba.nacos.api.exception.NacosException;

import java.util.Objects;

/**
 * Immutable server-internal snapshot of the currently pinned Hub Skill reference.
 *
 * <p>This is NOT a response DTO and must never be returned directly by an HTTP API.
 * It carries source identifiers for subsequent source-state/authorization checks,
 * but grants no right to read source content. No raw ext or storage field is kept.</p>
 *
 * @author nacos
 */
public final class ResolvedHubSkillReference {
    
    private final long hubEntryId;
    
    private final long hubMetaVersion;
    
    private final long hubVersionId;
    
    private final String internalName;
    
    private final String description;
    
    private final String bizTags;
    
    private final Long createdAt;
    
    private final Long updatedAt;
    
    private final String sourceNamespaceId;
    
    private final long sourceResourceId;
    
    private final String sourceName;
    
    private final long sourceVersionId;
    
    private final String sourceVersion;
    
    ResolvedHubSkillReference(AiResource meta, AiResourceVersion hubVersion,
        HubResourceReference reference) {
        this.hubEntryId = meta.getId();
        this.hubMetaVersion = meta.getMetaVersion();
        this.hubVersionId = hubVersion.getId();
        this.internalName = meta.getName();
        this.description = meta.getDesc();
        this.bizTags = meta.getBizTags();
        this.createdAt = meta.getGmtCreate() == null ? null : meta.getGmtCreate().getTime();
        this.updatedAt = meta.getGmtModified() == null ? null : meta.getGmtModified().getTime();
        this.sourceNamespaceId = reference.getSourceNamespaceId();
        this.sourceResourceId = reference.getSourceResourceId();
        this.sourceName = reference.getSourceName();
        this.sourceVersionId = reference.getSourceVersionId();
        this.sourceVersion = reference.getSourceVersion();
    }
    
    public long getHubEntryId() {
        return hubEntryId;
    }
    
    public long getHubMetaVersion() {
        return hubMetaVersion;
    }
    
    public long getHubVersionId() {
        return hubVersionId;
    }
    
    public String getInternalName() {
        return internalName;
    }
    
    public String getDescription() {
        return description;
    }
    
    public String getBizTags() {
        return bizTags;
    }
    
    public Long getCreatedAt() {
        return createdAt;
    }
    
    public Long getUpdatedAt() {
        return updatedAt;
    }
    
    public String getSourceNamespaceId() {
        return sourceNamespaceId;
    }
    
    public long getSourceResourceId() {
        return sourceResourceId;
    }
    
    public String getSourceName() {
        return sourceName;
    }
    
    public long getSourceVersionId() {
        return sourceVersionId;
    }
    
    public String getSourceVersion() {
        return sourceVersion;
    }
    
    /** Construct a fresh descriptor; do not expose the mutable persisted descriptor. */
    HubResourceReference toReference() throws NacosException {
        return HubResourceReferenceJsonUtil.build(sourceNamespaceId, sourceResourceId,
            sourceName, sourceVersionId, sourceVersion);
    }
    
    /** Compare publication identity rather than mutable object references. */
    boolean samePublication(ResolvedHubSkillReference other) {
        return other != null && hubEntryId == other.hubEntryId
            && hubMetaVersion == other.hubMetaVersion
            && hubVersionId == other.hubVersionId
            && sourceResourceId == other.sourceResourceId
            && sourceVersionId == other.sourceVersionId
            && Objects.equals(internalName, other.internalName)
            && Objects.equals(sourceNamespaceId, other.sourceNamespaceId)
            && Objects.equals(sourceName, other.sourceName)
            && Objects.equals(sourceVersion, other.sourceVersion);
    }
}
