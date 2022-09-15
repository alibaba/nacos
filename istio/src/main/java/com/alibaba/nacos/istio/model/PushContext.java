/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.istio.model;

import com.alibaba.nacos.istio.common.ResourceSnapshot;
import com.google.protobuf.ProtocolStringList;

/**
 * @author RocketEngine26
 * @date 2022/8/21 下午1:09
 */
public class PushContext {
    private ResourceSnapshot resourceSnapshot;
    
    private boolean full;
    
    private String version;
    
    private ProtocolStringList resourceNamesSubscribe;
    
    private ProtocolStringList resourceNamesUnSubscribe;
    
    public PushContext(ResourceSnapshot resourceSnapshot, boolean full, ProtocolStringList resourceNamesSubscribe,
            ProtocolStringList resourceNamesUnSubscribe) {
        this.resourceSnapshot = resourceSnapshot;
        this.full = full;
        this.version = resourceSnapshot.getVersion();
        this.resourceNamesSubscribe = resourceNamesSubscribe;
        this.resourceNamesUnSubscribe = resourceNamesUnSubscribe;
    }
    
    public ResourceSnapshot getResourceSnapshot() {
        return resourceSnapshot;
    }
    
    public String getVersion() {
        return version;
    }
    
    public boolean isFull() {
        return full;
    }
    
    public void setFull(boolean full) {
        this.full = full;
    }
    
    public ProtocolStringList getResourceNamesSubscribe() {
        return resourceNamesSubscribe;
    }
    
    public ProtocolStringList getResourceNamesUnSubscribe() {
        return resourceNamesUnSubscribe;
    }
}
