/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.distributed.distro.entity;

import java.util.Objects;

/**
 * Distro key.
 *
 * @author xiweng.yy
 */
public class DistroKey {
    
    private String resourceKey;
    
    private String resourceType;
    
    private String targetServer;
    
    public DistroKey() {
    }
    
    public DistroKey(String resourceKey, String resourceType) {
        this.resourceKey = resourceKey;
        this.resourceType = resourceType;
    }
    
    public DistroKey(String resourceKey, String resourceType, String targetServer) {
        this.resourceKey = resourceKey;
        this.resourceType = resourceType;
        this.targetServer = targetServer;
    }
    
    public String getResourceKey() {
        return resourceKey;
    }
    
    public void setResourceKey(String resourceKey) {
        this.resourceKey = resourceKey;
    }
    
    public String getResourceType() {
        return resourceType;
    }
    
    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }
    
    public String getTargetServer() {
        return targetServer;
    }
    
    public void setTargetServer(String targetServer) {
        this.targetServer = targetServer;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DistroKey distroKey = (DistroKey) o;
        return Objects.equals(resourceKey, distroKey.resourceKey) && Objects
                .equals(resourceType, distroKey.resourceType) && Objects.equals(targetServer, distroKey.targetServer);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(resourceKey, resourceType, targetServer);
    }
    
    @Override
    public String toString() {
        return "DistroKey{" + "resourceKey='" + resourceKey + '\'' + ", resourceType='" + resourceType + '\''
                + ", targetServer='" + targetServer + '\'' + '}';
    }
}
