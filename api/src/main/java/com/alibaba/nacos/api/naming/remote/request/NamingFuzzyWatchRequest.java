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

package com.alibaba.nacos.api.naming.remote.request;

import com.alibaba.nacos.api.remote.request.Request;

import java.util.Set;

import static com.alibaba.nacos.api.common.Constants.Naming.NAMING_MODULE;

/**
 * Nacos naming fuzzy watch service request.
 *
 * @author tanyongquan
 */
public class NamingFuzzyWatchRequest extends Request {
    
    private boolean isInitializing;
    
    private String namespace;
    
    /**
     * The namespace or tenant associated with the configurations.
     */
    private String groupKeyPattern;
    
    private Set<String> receivedGroupKeys;
    
    private String watchType;
    
    public NamingFuzzyWatchRequest() {
    }
    
    public NamingFuzzyWatchRequest(String groupKeyPattern, String watchType) {
        this.watchType = watchType;
        this.groupKeyPattern = groupKeyPattern;
    }
    
    public String getGroupKeyPattern() {
        return groupKeyPattern;
    }
    
    public void setGroupKeyPattern(String groupKeyPattern) {
        this.groupKeyPattern = groupKeyPattern;
    }
    
    public String getWatchType() {
        return watchType;
    }
    
    public void setWatchType(String watchType) {
        this.watchType = watchType;
    }
    
    public Set<String> getReceivedGroupKeys() {
        return receivedGroupKeys;
    }
    
    public String getNamespace() {
        return namespace;
    }
    
    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }
    
    public boolean isInitializing() {
        return isInitializing;
    }
    
    public void setInitializing(boolean initializing) {
        isInitializing = initializing;
    }
    
    public void setReceivedGroupKeys(Set<String> receivedGroupKeys) {
        this.receivedGroupKeys = receivedGroupKeys;
    }
    
    @Override
    public String getModule() {
        return NAMING_MODULE;
    }
    
}
