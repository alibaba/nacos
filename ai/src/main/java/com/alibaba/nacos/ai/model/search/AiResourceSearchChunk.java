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

package com.alibaba.nacos.ai.model.search;

import java.sql.Timestamp;

/**
 * Searchable chunk derived from an search document.
 *
 * @author nacos
 */
public class AiResourceSearchChunk {
    
    private Long id;
    
    private Timestamp gmtCreate;
    
    private Timestamp gmtModified;
    
    private Long documentId;
    
    private String namespaceId;
    
    private String resourceType;
    
    private String resourceName;
    
    private String resourceVersion;
    
    private String chunkType;
    
    private String chunkText;
    
    private String canonicalText;
    
    private String language;
    
    private String chunkHash;
    
    private String metadata;
    
    private String status;
    
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
    
    public Long getDocumentId() {
        return documentId;
    }
    
    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
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
    
    public String getChunkType() {
        return chunkType;
    }
    
    public void setChunkType(String chunkType) {
        this.chunkType = chunkType;
    }
    
    public String getChunkText() {
        return chunkText;
    }
    
    public void setChunkText(String chunkText) {
        this.chunkText = chunkText;
    }
    
    public String getCanonicalText() {
        return canonicalText;
    }
    
    public void setCanonicalText(String canonicalText) {
        this.canonicalText = canonicalText;
    }
    
    public String getLanguage() {
        return language;
    }
    
    public void setLanguage(String language) {
        this.language = language;
    }
    
    public String getChunkHash() {
        return chunkHash;
    }
    
    public void setChunkHash(String chunkHash) {
        this.chunkHash = chunkHash;
    }
    
    public String getMetadata() {
        return metadata;
    }
    
    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
}
