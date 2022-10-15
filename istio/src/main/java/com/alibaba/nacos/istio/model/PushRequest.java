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

import java.util.HashSet;
import java.util.Set;

/**
 * @author RocketEngine26
 * @date 2022/8/21 下午1:09
 */
public class PushRequest {
    private ResourceSnapshot resourceSnapshot;
    
    private final Set<String> reason = new HashSet<>();
    
    private Set<String> subscribe;
    
    private final Set<String> removed = new HashSet<>();
    
    private boolean full;
    
    public PushRequest(ResourceSnapshot snapshot, boolean full) {
        this.resourceSnapshot = snapshot;
        this.full = full;
    }
    
    public PushRequest(String reason, boolean full) {
        this.full = full;
        this.reason.add(reason);
    }
    
    public ResourceSnapshot getResourceSnapshot() {
        return resourceSnapshot;
    }
    
    public boolean isFull() {
        return full;
    }
    
    public void setFull(boolean full) {
        this.full = full;
    }
    
    public void setResourceSnapshot(ResourceSnapshot resourceSnapshot) {
        this.resourceSnapshot = resourceSnapshot;
    }
    
    public Set<String> getReason() {
        return reason;
    }
    
    public void addReason(String reason) {
        this.reason.add(reason);
    }
    
    public Set<String> getRemoved() {
        return removed;
    }
    
    public void addRemoved(String remove) {
        this.removed.add(remove);
    }
    
    public Set<String> getSubscribe() {
        return subscribe;
    }
    
    public void setSubscribe(Set<String> subscribe) {
        this.subscribe = subscribe;
    }
}
