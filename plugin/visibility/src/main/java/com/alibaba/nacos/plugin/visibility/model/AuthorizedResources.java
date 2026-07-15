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

import java.util.ArrayList;
import java.util.List;

/**
 * Storage-neutral authorized resources set.
 *
 * @author xiweng.yy
 */
public class AuthorizedResources {
    
    private String resourceType;
    
    private List<String> resources = new ArrayList<>();
    
    public String getResourceType() {
        return resourceType;
    }
    
    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }
    
    public List<String> getResources() {
        return resources;
    }
    
    public void setResources(List<String> resources) {
        this.resources = resources;
    }
}
