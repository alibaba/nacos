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
public class ConfigBatchFuzzyListenEvent extends Event {
    
    private static final long serialVersionUID = 1953965691384930209L;
    
    /**
     * ID of the client making the request.
     */
    private String clientId;
    
    /**
     * Pattern for matching group keys.
     */
    private String keyGroupPattern;
    
    /**
     * Set of existing group keys associated with the client.
     */
    private Set<String> clientExistingGroupKeys;
    
    /**
     * Flag indicating whether the client is initializing.
     */
    private boolean isInitializing;
    
    /**
     * Constructs a new ConfigBatchFuzzyListenEvent with the specified parameters.
     *
     * @param clientId                ID of the client making the request
     * @param clientExistingGroupKeys Set of existing group keys associated with the client
     * @param keyGroupPattern         Pattern for matching group keys
     * @param isInitializing          Flag indicating whether the client is initializing
     */
    public ConfigBatchFuzzyListenEvent(String clientId, Set<String> clientExistingGroupKeys, String keyGroupPattern,
            boolean isInitializing) {
        this.clientId = clientId;
        this.clientExistingGroupKeys = clientExistingGroupKeys;
        this.keyGroupPattern = keyGroupPattern;
        this.isInitializing = isInitializing;
    }
    
    /**
     * Get the ID of the client making the request.
     *
     * @return The client ID
     */
    public String getClientId() {
        return clientId;
    }
    
    /**
     * Set the ID of the client making the request.
     *
     * @param clientId The client ID to be set
     */
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }
    
    /**
     * Get the pattern for matching group keys.
     *
     * @return The key group pattern
     */
    public String getKeyGroupPattern() {
        return keyGroupPattern;
    }
    
    /**
     * Set the pattern for matching group keys.
     *
     * @param keyGroupPattern The key group pattern to be set
     */
    public void setKeyGroupPattern(String keyGroupPattern) {
        this.keyGroupPattern = keyGroupPattern;
    }
    
    /**
     * Get the set of existing group keys associated with the client.
     *
     * @return The set of existing group keys
     */
    public Set<String> getClientExistingGroupKeys() {
        return clientExistingGroupKeys;
    }
    
    /**
     * Set the set of existing group keys associated with the client.
     *
     * @param clientExistingGroupKeys The set of existing group keys to be set
     */
    public void setClientExistingGroupKeys(Set<String> clientExistingGroupKeys) {
        this.clientExistingGroupKeys = clientExistingGroupKeys;
    }
    
    /**
     * Check whether the client is initializing.
     *
     * @return True if the client is initializing, otherwise false
     */
    public boolean isInitializing() {
        return isInitializing;
    }
    
    /**
     * Set the flag indicating whether the client is initializing.
     *
     * @param initializing True if the client is initializing, otherwise false
     */
    public void setInitializing(boolean initializing) {
        isInitializing = initializing;
    }
}
