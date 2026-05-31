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

package com.alibaba.nacos.api.ai.model.importer;

import java.io.Serializable;
import java.util.List;

/**
 * Response for AI resource import search.
 *
 * @author xiweng.yy
 * @since 3.2.1
 */
public class AiResourceImportSearchResponse implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private String sourceId;
    
    private String resourceType;
    
    private String nextCursor;
    
    private boolean hasMore;
    
    private List<AiResourceImportCandidateItem> items;
    
    public String getSourceId() {
        return sourceId;
    }
    
    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }
    
    public String getResourceType() {
        return resourceType;
    }
    
    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }
    
    public String getNextCursor() {
        return nextCursor;
    }
    
    public void setNextCursor(String nextCursor) {
        this.nextCursor = nextCursor;
    }
    
    public boolean isHasMore() {
        return hasMore;
    }
    
    public void setHasMore(boolean hasMore) {
        this.hasMore = hasMore;
    }
    
    public List<AiResourceImportCandidateItem> getItems() {
        return items;
    }
    
    public void setItems(List<AiResourceImportCandidateItem> items) {
        this.items = items;
    }
}
