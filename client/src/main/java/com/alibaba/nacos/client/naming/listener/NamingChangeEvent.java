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

package com.alibaba.nacos.client.naming.listener;

import com.alibaba.nacos.api.naming.listener.NamingEvent;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.client.naming.event.InstancesDiff;

import java.util.List;

/**
 * Naming Event with instance change information.
 *
 * @author lideyou
 */
public class NamingChangeEvent extends NamingEvent {
    
    private final InstancesDiff instancesDiff;
    
    public NamingChangeEvent(String serviceName, List<Instance> instances, InstancesDiff instancesDiff) {
        super(serviceName, instances);
        this.instancesDiff = instancesDiff;
    }
    
    public NamingChangeEvent(String serviceName, String groupName, String clusters, List<Instance> instances,
            InstancesDiff instancesDiff) {
        super(serviceName, groupName, clusters, instances);
        this.instancesDiff = instancesDiff;
    }
    
    public boolean isAdded() {
        return this.instancesDiff.isAdded();
    }
    
    public boolean isRemoved() {
        return this.instancesDiff.isRemoved();
    }
    
    public boolean isModified() {
        return this.instancesDiff.isModified();
    }
    
    public List<Instance> getAddedInstances() {
        return this.instancesDiff.getAddedInstances();
    }
    
    public List<Instance> getRemovedInstances() {
        return this.instancesDiff.getRemovedInstances();
    }
    
    public List<Instance> getModifiedInstances() {
        return this.instancesDiff.getModifiedInstances();
    }
}
