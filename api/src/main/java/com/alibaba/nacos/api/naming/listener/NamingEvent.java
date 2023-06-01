/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.api.naming.listener;

import com.alibaba.nacos.api.naming.pojo.Instance;

import java.util.List;
import java.util.Set;

/**
 * Naming Event.
 *
 * @author nkorange
 */
public class NamingEvent implements Event {
    
    private String serviceName;
    
    private String groupName;
    
    private String clusters;
    
    private List<Instance> instances;

    private Set<Instance> modInstances;

    private Set<Instance> newInstances;

    private Set<Instance> removeInstances;
    
    public NamingEvent(String serviceName, List<Instance> instances) {
        this.serviceName = serviceName;
        this.instances = instances;
    }
    
    public NamingEvent(String serviceName, String groupName, String clusters, List<Instance> instances) {
        this.serviceName = serviceName;
        this.groupName = groupName;
        this.clusters = clusters;
        this.instances = instances;
    }

    public NamingEvent(String serviceName, String groupName, String clusters, List<Instance> instances,
                       Set<Instance> modInstances, Set<Instance> newInstances, Set<Instance> removeInstances) {
        this.serviceName = serviceName;
        this.groupName = groupName;
        this.clusters = clusters;
        this.instances = instances;
        this.modInstances = modInstances;
        this.newInstances = newInstances;
        this.removeInstances = removeInstances;
    }
    
    public String getServiceName() {
        return serviceName;
    }
    
    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }
    
    public List<Instance> getInstances() {
        return instances;
    }
    
    public void setInstances(List<Instance> instances) {
        this.instances = instances;
    }
    
    public String getGroupName() {
        return groupName;
    }
    
    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }
    
    public String getClusters() {
        return clusters;
    }
    
    public void setClusters(String clusters) {
        this.clusters = clusters;
    }

    public Set<Instance> getModInstances() {
        return modInstances;
    }

    public void setModInstances(Set<Instance> modInstances) {
        this.modInstances = modInstances;
    }

    public Set<Instance> getNewInstances() {
        return newInstances;
    }

    public void setNewInstances(Set<Instance> newInstances) {
        this.newInstances = newInstances;
    }

    public Set<Instance> getRemoveInstances() {
        return removeInstances;
    }

    public void setRemoveInstances(Set<Instance> removeInstances) {
        this.removeInstances = removeInstances;
    }
}
