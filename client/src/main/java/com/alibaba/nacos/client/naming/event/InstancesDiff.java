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

package com.alibaba.nacos.client.naming.event;

import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.common.utils.CollectionUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * The differences in instances compared to the last callback.
 *
 * @author lideyou
 */
public class InstancesDiff {
    
    private final List<Instance> addedInstances = new ArrayList<>();
    
    private final List<Instance> removedInstances = new ArrayList<>();
    
    private final List<Instance> modifiedInstances = new ArrayList<>();
    
    public InstancesDiff() {
    }
    
    public InstancesDiff(List<Instance> addedInstances, List<Instance> removedInstances,
            List<Instance> modifiedInstances) {
        setAddedInstances(addedInstances);
        setRemovedInstances(removedInstances);
        setModifiedInstances(modifiedInstances);
    }
    
    public List<Instance> getAddedInstances() {
        return addedInstances;
    }
    
    public void setAddedInstances(Collection<Instance> addedInstances) {
        this.addedInstances.clear();
        if (CollectionUtils.isNotEmpty(addedInstances)) {
            this.addedInstances.addAll(addedInstances);
        }
    }
    
    public List<Instance> getRemovedInstances() {
        return removedInstances;
    }
    
    public void setRemovedInstances(Collection<Instance> removedInstances) {
        this.removedInstances.clear();
        if (CollectionUtils.isNotEmpty(removedInstances)) {
            this.removedInstances.addAll(removedInstances);
        }
    }
    
    public List<Instance> getModifiedInstances() {
        return modifiedInstances;
    }
    
    public void setModifiedInstances(Collection<Instance> modifiedInstances) {
        this.modifiedInstances.clear();
        if (CollectionUtils.isNotEmpty(modifiedInstances)) {
            this.modifiedInstances.addAll(modifiedInstances);
        }
    }
    
    /**
     * Check if any instances have changed.
     *
     * @return true if there are instances that have changed
     */
    public boolean hasDifferent() {
        return isAdded() || isRemoved() || isModified();
    }
    
    /**
     * Check if any instances have been added.
     *
     * @return true if there are instances that have been added.
     */
    public boolean isAdded() {
        return CollectionUtils.isNotEmpty(this.addedInstances);
    }
    
    /**
     * Check if any instances have been added.
     *
     * @return true if there are instances that have been added.
     */
    public boolean isRemoved() {
        return CollectionUtils.isNotEmpty(this.removedInstances);
    }
    
    /**
     * Check if any instances have been added.
     *
     * @return true if there are instances that have been added.
     */
    public boolean isModified() {
        return CollectionUtils.isNotEmpty(this.modifiedInstances);
    }
}
