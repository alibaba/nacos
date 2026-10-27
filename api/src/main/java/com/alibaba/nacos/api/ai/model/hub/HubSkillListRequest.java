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

package com.alibaba.nacos.api.ai.model.hub;

import java.util.List;

/**
 * Query parameters for the shared Skill directory.
 *
 * <p>Resource APIs select this directory with the reserved shared namespace.
 * This internal request always addresses that shared directory. Results use
 * entry update time descending by default, or Hub download count descending
 * when orderBy is download_count. Immutable entry ID ascending breaks ties
 * before pagination.</p>
 *
 * @author nacos
 */
public class HubSkillListRequest {
    
    private String query;
    
    private List<String> tagsAll;
    
    private int pageNo = 1;
    
    private int pageSize = 20;
    
    private String orderBy;
    
    public String getOrderBy() {
        return orderBy;
    }
    
    public void setOrderBy(String orderBy) {
        this.orderBy = orderBy;
    }
    
    public String getQuery() {
        return query;
    }
    
    public void setQuery(String query) {
        this.query = query;
    }
    
    public List<String> getTagsAll() {
        return tagsAll;
    }
    
    public void setTagsAll(List<String> tagsAll) {
        this.tagsAll = tagsAll;
    }
    
    public int getPageNo() {
        return pageNo;
    }
    
    public void setPageNo(int pageNo) {
        this.pageNo = pageNo;
    }
    
    public int getPageSize() {
        return pageSize;
    }
    
    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }
}
