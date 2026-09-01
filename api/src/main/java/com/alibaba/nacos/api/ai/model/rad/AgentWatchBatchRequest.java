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
 * Complete current HTTP Agent Watch set for one client generation.
 *
 * @author Nacos
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentWatchBatchRequest implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private long generation;
    
    private long timeoutMillis;
    
    private List<AgentWatchBatchItem> watches;
    
    public long getGeneration() {
        return generation;
    }
    
    public void setGeneration(long generation) {
        this.generation = generation;
    }
    
    public long getTimeoutMillis() {
        return timeoutMillis;
    }
    
    public void setTimeoutMillis(long timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
    }
    
    public List<AgentWatchBatchItem> getWatches() {
        return watches;
    }
    
    public void setWatches(List<AgentWatchBatchItem> watches) {
        this.watches = watches;
    }
}
