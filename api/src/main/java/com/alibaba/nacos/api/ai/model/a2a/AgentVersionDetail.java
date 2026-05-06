/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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
 *
 */

package com.alibaba.nacos.api.ai.model.a2a;

import java.util.Objects;

/**
 * AgentVersionDetail.
 *
 * @author KiteSoar
 */
public class AgentVersionDetail {
    
    private String version;
    
    private String createdAt;
    
    private String updatedAt;
    
    private boolean isLatest;
    
    public String getVersion() {
        return version;
    }
    
    public void setVersion(String version) {
        this.version = version;
    }
    
    public String getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
    
    public String getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    public boolean isLatest() {
        return isLatest;
    }
    
    public void setLatest(boolean latest) {
        isLatest = latest;
    }
    
    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AgentVersionDetail that = (AgentVersionDetail) o;
        return Objects.equals(version, that.version) && Objects.equals(createdAt, that.createdAt) && Objects.equals(
                updatedAt, that.updatedAt) && Objects.equals(isLatest, that.isLatest);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(version, createdAt, updatedAt, isLatest);
    }
}
