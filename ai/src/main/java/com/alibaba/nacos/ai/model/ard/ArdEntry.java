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

package com.alibaba.nacos.ai.model.ard;

import java.sql.Timestamp;

/**
 * Persistent ARD catalog entry generated from a Nacos AI resource.
 *
 * @author nacos
 */
public class ArdEntry {
    
    private Long id;
    
    private Timestamp gmtCreate;
    
    private Timestamp gmtModified;
    
    private String namespaceId;
    
    private String resourceType;
    
    private String resourceName;
    
    private String resourceVersion;
    
    private String identifier;
    
    private String displayName;
    
    private String type;
    
    private String url;
    
    private String description;
    
    private String tags;
    
    private String capabilities;
    
    private String representativeQueries;
    
    private String metadata;
    
    private String trustManifest;
    
    private String sourceDigest;
    
    private String status;
    
    private String generateMode;
    
    private String source;
    
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public Timestamp getGmtCreate() {
        return gmtCreate;
    }
    
    public void setGmtCreate(Timestamp gmtCreate) {
        this.gmtCreate = gmtCreate;
    }
    
    public Timestamp getGmtModified() {
        return gmtModified;
    }
    
    public void setGmtModified(Timestamp gmtModified) {
        this.gmtModified = gmtModified;
    }
    
    public String getNamespaceId() {
        return namespaceId;
    }
    
    public void setNamespaceId(String namespaceId) {
        this.namespaceId = namespaceId;
    }
    
    public String getResourceType() {
        return resourceType;
    }
    
    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }
    
    public String getResourceName() {
        return resourceName;
    }
    
    public void setResourceName(String resourceName) {
        this.resourceName = resourceName;
    }
    
    public String getResourceVersion() {
        return resourceVersion;
    }
    
    public void setResourceVersion(String resourceVersion) {
        this.resourceVersion = resourceVersion;
    }
    
    public String getIdentifier() {
        return identifier;
    }
    
    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
    
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    public String getUrl() {
        return url;
    }
    
    public void setUrl(String url) {
        this.url = url;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public String getTags() {
        return tags;
    }
    
    public void setTags(String tags) {
        this.tags = tags;
    }
    
    public String getCapabilities() {
        return capabilities;
    }
    
    public void setCapabilities(String capabilities) {
        this.capabilities = capabilities;
    }
    
    public String getRepresentativeQueries() {
        return representativeQueries;
    }
    
    public void setRepresentativeQueries(String representativeQueries) {
        this.representativeQueries = representativeQueries;
    }
    
    public String getMetadata() {
        return metadata;
    }
    
    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }
    
    public String getTrustManifest() {
        return trustManifest;
    }
    
    public void setTrustManifest(String trustManifest) {
        this.trustManifest = trustManifest;
    }
    
    public String getSourceDigest() {
        return sourceDigest;
    }
    
    public void setSourceDigest(String sourceDigest) {
        this.sourceDigest = sourceDigest;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public String getGenerateMode() {
        return generateMode;
    }
    
    public void setGenerateMode(String generateMode) {
        this.generateMode = generateMode;
    }
    
    public String getSource() {
        return source;
    }
    
    public void setSource(String source) {
        this.source = source;
    }
}
