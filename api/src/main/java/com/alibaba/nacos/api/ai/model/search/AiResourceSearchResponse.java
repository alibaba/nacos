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

package com.alibaba.nacos.api.ai.model.search;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * Cursor page returned by generic AI Resource Search.
 *
 * @author Nacos
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AiResourceSearchResponse implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private List<AiResourceSearchItem> items = Collections.emptyList();
    
    private String nextCursor;
    
    public List<AiResourceSearchItem> getItems() {
        return items;
    }
    
    public void setItems(List<AiResourceSearchItem> items) {
        this.items = items == null ? Collections.emptyList() : items;
    }
    
    public String getNextCursor() {
        return nextCursor;
    }
    
    public void setNextCursor(String nextCursor) {
        this.nextCursor = nextCursor;
    }
}
