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

package com.alibaba.nacos.api.ai.model.hub;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Read-only detail of one currently shared Hub Skill version.
 *
 * <p>The optional source namespace and name are populated only if the caller
 * independently has source READ permission, for constructing the existing
 * source-detail deep link. Hub authorization alone never exposes these fields.
 * No internal Hub name, raw reference ext or storage descriptor is returned.</p>
 *
 * <p>Author, status, commit message and version downloads belong to the fixed
 * shared source version. The inherited download count is the source Skill total.
 * Version labels contain only names bound to that shared version.</p>
 *
 * @author nacos
 */
public class HubSkillDetail extends HubSkillSummary {
    
    /** Status of the pinned source version, never the Hub reference version. */
    private String versionStatus;
    
    private Long versionDownloadCount;
    
    private String commitMsg;
    
    /** Label names pointing only to the fixed shared version. */
    private List<String> versionLabels;
    
    private boolean sourceReadable;
    
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String sourceNamespaceId;
    
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String sourceName;
    
    public String getVersionStatus() {
        return versionStatus;
    }
    
    public void setVersionStatus(String versionStatus) {
        this.versionStatus = versionStatus;
    }
    
    public Long getVersionDownloadCount() {
        return versionDownloadCount;
    }
    
    public void setVersionDownloadCount(Long versionDownloadCount) {
        this.versionDownloadCount = versionDownloadCount;
    }
    
    public String getCommitMsg() {
        return commitMsg;
    }
    
    public void setCommitMsg(String commitMsg) {
        this.commitMsg = commitMsg;
    }
    
    public List<String> getVersionLabels() {
        return versionLabels;
    }
    
    public void setVersionLabels(List<String> versionLabels) {
        this.versionLabels = versionLabels;
    }
    
    public boolean isSourceReadable() {
        return sourceReadable;
    }
    
    public void setSourceReadable(boolean sourceReadable) {
        this.sourceReadable = sourceReadable;
    }
    
    public String getSourceNamespaceId() {
        return sourceReadable ? sourceNamespaceId : null;
    }
    
    public void setSourceNamespaceId(String sourceNamespaceId) {
        this.sourceNamespaceId = sourceNamespaceId;
    }
    
    public String getSourceName() {
        return sourceReadable ? sourceName : null;
    }
    
    public void setSourceName(String sourceName) {
        this.sourceName = sourceName;
    }
}
