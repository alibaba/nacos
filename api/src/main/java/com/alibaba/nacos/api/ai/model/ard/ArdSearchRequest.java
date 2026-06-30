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

package com.alibaba.nacos.api.ai.model.ard;

/**
 * Request body for Nacos Local ARD Search.
 *
 * @author nacos
 */
public class ArdSearchRequest {
    
    private ArdSearchQuery query;
    
    private String federation;
    
    private Integer pageSize;
    
    private String pageToken;
    
    private String namespaceId;
    
    public ArdSearchQuery getQuery() {
        return query;
    }
    
    public void setQuery(ArdSearchQuery query) {
        this.query = query;
    }
    
    public String getFederation() {
        return federation;
    }
    
    public void setFederation(String federation) {
        this.federation = federation;
    }
    
    public Integer getPageSize() {
        return pageSize;
    }
    
    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }
    
    public String getPageToken() {
        return pageToken;
    }
    
    public void setPageToken(String pageToken) {
        this.pageToken = pageToken;
    }
    
    public String getNamespaceId() {
        return namespaceId;
    }
    
    public void setNamespaceId(String namespaceId) {
        this.namespaceId = namespaceId;
    }
}
