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

package com.alibaba.nacos.plugin.visibility.model;

import com.alibaba.nacos.plugin.visibility.constant.VisibilityConstants;

/**
 * Base class for resources that support visibility validation.
 *
 * @author xiweng.yy
 */
public abstract class VisibilityResource {
    
    private String scope = VisibilityConstants.SCOPE_PRIVATE;
    
    private String owner = "";
    
    /**
     * Get the namespace id of this resource.
     *
     * @return namespace id
     */
    public abstract String getNamespaceId();
    
    /**
     * Get the unique name of this resource within its namespace and type.
     *
     * @return resource name
     */
    public abstract String getResourceName();
    
    /**
     * Get the subtype of this resource, e.g. "skill", "mcp", "prompt", "a2a".
     *
     * @return resource type
     */
    public abstract String getResourceType();
    
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
}
