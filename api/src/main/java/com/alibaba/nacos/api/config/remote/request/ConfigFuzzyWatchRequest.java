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

package com.alibaba.nacos.api.config.remote.request;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.remote.request.Request;

import java.util.Set;

/**
 * Represents a request for batch fuzzy listening configurations.
 *
 * <p>This request is used to request batch fuzzy listening configurations from the server. It contains a set of
 * contexts, each representing a fuzzy listening context.
 *
 * @author stone-98
 * @date 2024/3/4
 */
public class ConfigFuzzyWatchRequest extends Request {
    
    /**
     * The namespace or tenant associated with the configurations.
     */
    private String groupKeyPattern;
    
    private Set<String> receivedGroupKeys;
    
    /**
     * Flag indicating whether to listen for changes.
     */
    private String watchType;
    
    /**
     * Flag indicating whether the client is initializing.
     */
    private boolean isInitializing;
    
    /**
     * Constructs an empty ConfigBatchFuzzyListenRequest.
     */
    public ConfigFuzzyWatchRequest() {
    }
    
    public String getGroupKeyPattern() {
        return groupKeyPattern;
    }
    
    public void setGroupKeyPattern(String groupKeyPattern) {
        this.groupKeyPattern = groupKeyPattern;
    }
    
    public Set<String> getReceivedGroupKeys() {
        return receivedGroupKeys;
    }
    
    public void setReceivedGroupKeys(Set<String> receivedGroupKeys) {
        this.receivedGroupKeys = receivedGroupKeys;
    }
    
    public String getWatchType() {
        return watchType;
    }
    
    public void setWatchType(String watchType) {
        this.watchType = watchType;
    }
    
    public boolean isInitializing() {
        return isInitializing;
    }
    
    public void setInitializing(boolean initializing) {
        isInitializing = initializing;
    }
    
    /**
     * Get the module name for this request.
     *
     * @return The module name
     */
    @Override
    public String getModule() {
        return Constants.Config.CONFIG_MODULE;
    }
    
}
