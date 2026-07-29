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
