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

package com.alibaba.nacos.api.ai.model.rad;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;
import java.util.List;

/**
 * Opaque invalidation result for one HTTP Agent Watch generation.
 *
 * @author Nacos
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentWatchBatchResponse implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private long generation;
    
    private boolean changed;
    
    private List<String> changedClientWatchIds;
    
    public long getGeneration() {
        return generation;
    }
    
    public void setGeneration(long generation) {
        this.generation = generation;
    }
    
    public boolean isChanged() {
        return changed;
    }
    
    public void setChanged(boolean changed) {
        this.changed = changed;
    }
    
    public List<String> getChangedClientWatchIds() {
        return changedClientWatchIds;
    }
    
    public void setChangedClientWatchIds(List<String> changedClientWatchIds) {
        this.changedClientWatchIds = changedClientWatchIds;
    }
}
