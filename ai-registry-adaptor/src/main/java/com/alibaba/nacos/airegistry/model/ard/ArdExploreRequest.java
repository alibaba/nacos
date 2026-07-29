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

package com.alibaba.nacos.airegistry.model.ard;

/**
 * Request body for ARD Explore facets.
 *
 * @author nacos
 */
public class ArdExploreRequest {
    
    private ArdSearchQuery query;
    
    private ArdExploreResultType resultType;
    
    private String namespaceId;
    
    public ArdSearchQuery getQuery() {
        return query;
    }
    
    public void setQuery(ArdSearchQuery query) {
        this.query = query;
    }
    
    public ArdExploreResultType getResultType() {
        return resultType;
    }
    
    public void setResultType(ArdExploreResultType resultType) {
        this.resultType = resultType;
    }
    
    public String getNamespaceId() {
        return namespaceId;
    }
    
    public void setNamespaceId(String namespaceId) {
        this.namespaceId = namespaceId;
    }
}
