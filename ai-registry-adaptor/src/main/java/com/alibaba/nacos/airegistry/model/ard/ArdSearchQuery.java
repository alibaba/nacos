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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ARD search query.
 *
 * @author nacos
 */
public class ArdSearchQuery {
    
    private String text;
    
    private Object context;
    
    private boolean contextPresent;
    
    @JsonProperty("@context")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Object getContext() {
        return context;
    }
    
    @JsonProperty("@context")
    public void setContext(Object context) {
        this.context = context;
        this.contextPresent = true;
    }
    
    @JsonIgnore
    public boolean isContextPresent() {
        return contextPresent;
    }
    
    private Map<String, Object> filter = new LinkedHashMap<>();
    
    private List<ArdSearchFilter> filters = new ArrayList<>();
    
    public String getText() {
        return text;
    }
    
    public void setText(String text) {
        this.text = text;
    }
    
    public Map<String, Object> getFilter() {
        return filter;
    }
    
    public void setFilter(Map<String, Object> filter) {
        this.filter = filter;
    }
    
    public List<ArdSearchFilter> getFilters() {
        return filters;
    }
    
    public void setFilters(List<ArdSearchFilter> filters) {
        this.filters = filters;
    }
}
