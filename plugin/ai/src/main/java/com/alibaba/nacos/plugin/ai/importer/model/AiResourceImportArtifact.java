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

package com.alibaba.nacos.plugin.ai.importer.model;

import java.util.Map;

/**
 * Import boundary object fetched by an AI resource import plugin.
 *
 * <p>The artifact is not a persistent Nacos resource model. Resource operators are responsible for
 * converting it into current Nacos domain services.</p>
 *
 * @author xiweng.yy
 * @since 3.2.1
 */
public class AiResourceImportArtifact {
    
    private String resourceType;
    
    private String externalId;
    
    private String name;
    
    private String version;
    
    private String description;
    
    private AiResourceImportPayloadKind payloadKind;
    
    private byte[] payload;
    
    private String payloadJson;
    
    private String checksum;
    
    private Map<String, String> sourceMetadata;
    
    public String getResourceType() {
        return resourceType;
    }
    
    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }
    
    public String getExternalId() {
        return externalId;
    }
    
    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getVersion() {
        return version;
    }
    
    public void setVersion(String version) {
        this.version = version;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public AiResourceImportPayloadKind getPayloadKind() {
        return payloadKind;
    }
    
    public void setPayloadKind(AiResourceImportPayloadKind payloadKind) {
        this.payloadKind = payloadKind;
    }
    
    public byte[] getPayload() {
        return payload;
    }
    
    public void setPayload(byte[] payload) {
        this.payload = payload;
    }
    
    public String getPayloadJson() {
        return payloadJson;
    }
    
    public void setPayloadJson(String payloadJson) {
        this.payloadJson = payloadJson;
    }
    
    public String getChecksum() {
        return checksum;
    }
    
    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }
    
    public Map<String, String> getSourceMetadata() {
        return sourceMetadata;
    }
    
    public void setSourceMetadata(Map<String, String> sourceMetadata) {
        this.sourceMetadata = sourceMetadata;
    }
}
