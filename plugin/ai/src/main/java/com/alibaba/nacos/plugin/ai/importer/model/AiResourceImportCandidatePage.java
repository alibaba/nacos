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

import java.util.List;
import java.util.Map;

/**
 * Candidate page returned by an AI resource import plugin.
 *
 * @author xiweng.yy
 * @since 3.2.1
 */
public class AiResourceImportCandidatePage {
    
    private List<AiResourceImportCandidate> items;
    
    private String nextCursor;
    
    private boolean hasMore;
    
    private Map<String, String> sourceMetadata;
    
    public List<AiResourceImportCandidate> getItems() {
        return items;
    }
    
    public void setItems(List<AiResourceImportCandidate> items) {
        this.items = items;
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
    
    public Map<String, String> getSourceMetadata() {
        return sourceMetadata;
    }
    
    public void setSourceMetadata(Map<String, String> sourceMetadata) {
        this.sourceMetadata = sourceMetadata;
    }
}
