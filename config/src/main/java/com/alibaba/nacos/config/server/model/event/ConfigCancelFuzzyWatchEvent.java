/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.model.event;

import com.alibaba.nacos.common.notify.Event;

import java.util.Set;

/**
 * This event represents a batch fuzzy listening event for configurations. It is used to notify the server about a batch
 * of fuzzy listening requests from clients. Each request contains a client ID, a set of existing group keys associated
 * with the client, a key group pattern, and a flag indicating whether the client is initializing.
 *
 * @author stone-98
 * @date 2024/3/5
 */
public class ConfigCancelFuzzyWatchEvent extends Event {
    
    private static final long serialVersionUID = 1953965691384930209L;
    
    /**
     * ID of the client making the request.
     */
    private String connectionId;
    
    /**
     * Pattern for matching group keys.
     */
    private String groupKeyPattern;
    
    /**
     * Constructs a new ConfigBatchFuzzyListenEvent with the specified parameters.
     *
     * @param connectionId                ID of the client making the request
     * @param groupKeyPattern         Pattern for matching group keys
     */
    public ConfigCancelFuzzyWatchEvent(String connectionId,  String groupKeyPattern) {
        this.connectionId = connectionId;
        this.groupKeyPattern = groupKeyPattern;
    }
    
    /**
     * Get the ID of the client making the request.
     *
     * @return The client ID
     */
    public String getConnectionId() {
        return connectionId;
    }
    
    public String getGroupKeyPattern() {
        return groupKeyPattern;
    }
    
    /**
     * Set the ID of the client making the request.
     *
     * @param connectionId The client ID to be set
     */
    public void setConnectionId(String connectionId) {
        this.connectionId = connectionId;
    }
    
}
