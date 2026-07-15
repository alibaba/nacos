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

package com.alibaba.nacos.ai.service.repository;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.common.utils.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generic query condition for repository list APIs.
 *
 * @author nacos
 */
public class QueryCondition {
    
    /**
     * Query filter for namespace.
     */
    private String namespaceId = Constants.DEFAULT_NAMESPACE_ID;
    
    /**
     * Query filter for type, e.g. skill/agentspec.
     */
    private String type;
    
    /**
     * Query filter for name like.
     */
    private String nameLike;
    
    /**
     * Query filter for bizTags like.
     */
    private String bizTagsLike;
    
    /**
     * Query filter for scope.
     */
    private String scope;
    
    /**
     * Query filter for owner.
     */
    private String owner;
    
    /**
     * Query order by field.
     */
    private String orderBy;
    
    /**
     * Query filter for authorized resource names.
     */
    private List<String> authorizedResourceNames = new ArrayList<>();
    
    /**
     * Optional OR group, field -> value. List value means IN.
     */
    private Map<String, Object> orGroup = new LinkedHashMap<>();
    
    /**
     * Whether this query is guaranteed to return empty result.
     */
    private boolean alwaysEmpty;
    
    public String getNamespaceId() {
        return namespaceId;
    }
    
    public void setNamespaceId(String namespaceId) {
        this.namespaceId =
            StringUtils.isBlank(namespaceId) ? Constants.DEFAULT_NAMESPACE_ID : namespaceId;
    }
    
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    public String getNameLike() {
        return nameLike;
    }
    
    public void setNameLike(String nameLike) {
        this.nameLike = nameLike;
    }
    
    public String getBizTagsLike() {
        return bizTagsLike;
    }
    
    public void setBizTagsLike(String bizTagsLike) {
        this.bizTagsLike = bizTagsLike;
    }
    
    public String getScope() {
        return scope;
    }
    
    public void setScope(String scope) {
        this.scope = scope;
    }
    
    public String getOwner() {
        return owner;
    }
    
    public void setOwner(String owner) {
        this.owner = owner;
    }
    
    public String getOrderBy() {
        return orderBy;
    }
    
    public void setOrderBy(String orderBy) {
        this.orderBy = orderBy;
    }
    
    public List<String> getAuthorizedResourceNames() {
        return authorizedResourceNames;
    }
    
    public void setAuthorizedResourceNames(List<String> authorizedResourceNames) {
        this.authorizedResourceNames = authorizedResourceNames;
    }
    
    public Map<String, Object> getOrGroup() {
        return orGroup;
    }
    
    public void setOrGroup(Map<String, Object> orGroup) {
        this.orGroup = orGroup;
    }
    
    /**
     * Put one item into OR group.
     */
    public void putOrGroup(String field, Object value) {
        if (field == null || value == null) {
            return;
        }
        orGroup.put(field, value);
    }
    
    public boolean isAlwaysEmpty() {
        return alwaysEmpty;
    }
    
    public void setAlwaysEmpty(boolean alwaysEmpty) {
        this.alwaysEmpty = alwaysEmpty;
    }
}
