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

package com.alibaba.nacos.ai.service.a2a.migration;

import com.alibaba.nacos.api.ai.model.a2a.AgentCardDetailInfo;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardVersionInfo;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Temporary compatibility support for migrating Nacos 3.0-3.2 A2A data.
 *
 * <p>TODO(remove in 4.0): remove after the historical A2A migration window closes.</p>
 *
 * @author Nacos
 */
public final class A2aHistoricalDefinitionSnapshot {
    
    private final String namespaceId;
    
    private final String summaryDataId;
    
    private final String summaryContent;
    
    private final String summaryMd5;
    
    private final AgentCardVersionInfo summary;
    
    private final Map<String, VersionSnapshot> versions;
    
    private final String sourceFingerprint;
    
    A2aHistoricalDefinitionSnapshot(String namespaceId, String summaryDataId,
        String summaryContent, String summaryMd5, AgentCardVersionInfo summary,
        Map<String, VersionSnapshot> versions, String sourceFingerprint) {
        this.namespaceId = namespaceId;
        this.summaryDataId = summaryDataId;
        this.summaryContent = summaryContent;
        this.summaryMd5 = summaryMd5;
        this.summary = summary;
        this.versions = Collections.unmodifiableMap(
            new LinkedHashMap<String, VersionSnapshot>(versions));
        this.sourceFingerprint = sourceFingerprint;
    }
    
    public String getNamespaceId() {
        return namespaceId;
    }
    
    public String getSummaryDataId() {
        return summaryDataId;
    }
    
    public String getSummaryContent() {
        return summaryContent;
    }
    
    public String getSummaryMd5() {
        return summaryMd5;
    }
    
    public AgentCardVersionInfo getSummary() {
        return summary;
    }
    
    public Map<String, VersionSnapshot> getVersions() {
        return versions;
    }
    
    public String getSourceFingerprint() {
        return sourceFingerprint;
    }
    
    /**
     * One exact historical AgentCard Version and the Config identity used by source fencing.
     */
    public static final class VersionSnapshot {
        
        private final String dataId;
        
        private final String content;
        
        private final String md5;
        
        private final AgentCardDetailInfo agentCard;
        
        VersionSnapshot(String dataId, String content, String md5,
            AgentCardDetailInfo agentCard) {
            this.dataId = dataId;
            this.content = content;
            this.md5 = md5;
            this.agentCard = agentCard;
        }
        
        public String getDataId() {
            return dataId;
        }
        
        public String getContent() {
            return content;
        }
        
        public String getMd5() {
            return md5;
        }
        
        public AgentCardDetailInfo getAgentCard() {
            return agentCard;
        }
    }
}
