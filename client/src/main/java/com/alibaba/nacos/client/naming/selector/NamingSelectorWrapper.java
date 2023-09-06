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

package com.alibaba.nacos.client.naming.selector;

import com.alibaba.nacos.api.naming.listener.EventListener;
import com.alibaba.nacos.api.naming.listener.NamingEvent;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.selector.NamingContext;
import com.alibaba.nacos.api.naming.selector.NamingSelector;
import com.alibaba.nacos.client.naming.event.InstancesChangeEvent;
import com.alibaba.nacos.client.naming.event.InstancesDiff;
import com.alibaba.nacos.client.naming.listener.NamingChangeEvent;
import com.alibaba.nacos.client.selector.AbstractSelectorWrapper;
import com.alibaba.nacos.common.utils.CollectionUtils;

import java.util.Collections;
import java.util.List;

/**
 * Naming selector wrapper.
 *
 * @author lideyou
 */
public class NamingSelectorWrapper extends AbstractSelectorWrapper<NamingSelector, NamingEvent, InstancesChangeEvent> {
    
    private String serviceName;
    
    private String groupName;
    
    private String clusters;
    
    private final InnerNamingContext namingContext = new InnerNamingContext();
    
    private class InnerNamingContext implements NamingContext {
        
        private List<Instance> instances;
        
        @Override
        public String getServiceName() {
            return serviceName;
        }
        
        @Override
        public String getGroupName() {
            return groupName;
        }
        
        @Override
        public String getClusters() {
            return clusters;
        }
        
        @Override
        public List<Instance> getInstances() {
            return instances;
        }
        
        private void setInstances(List<Instance> instances) {
            this.instances = instances;
        }
    }
    
    public NamingSelectorWrapper(NamingSelector selector, EventListener listener) {
        super(selector, new NamingListenerInvoker(listener));
    }
    
    public NamingSelectorWrapper(String serviceName, String groupName, String clusters, NamingSelector selector,
            EventListener listener) {
        this(selector, listener);
        this.serviceName = serviceName;
        this.groupName = groupName;
        this.clusters = clusters;
    }
    
    @Override
    protected boolean isSelectable(InstancesChangeEvent event) {
        return event != null && event.getHosts() != null && event.getInstancesDiff() != null;
    }
    
    @Override
    public boolean isCallable(NamingEvent event) {
        if (event == null) {
            return false;
        }
        NamingChangeEvent changeEvent = (NamingChangeEvent) event;
        return changeEvent.isAdded() || changeEvent.isRemoved() || changeEvent.isModified();
    }
    
    @Override
    protected NamingEvent buildListenerEvent(InstancesChangeEvent event) {
        List<Instance> currentIns = Collections.emptyList();
        if (CollectionUtils.isNotEmpty(event.getHosts())) {
            currentIns = doSelect(event.getHosts());
        }
        
        InstancesDiff diff = event.getInstancesDiff();
        InstancesDiff newDiff = new InstancesDiff();
        if (diff.isAdded()) {
            newDiff.setAddedInstances(doSelect(diff.getAddedInstances()));
        }
        if (diff.isRemoved()) {
            newDiff.setRemovedInstances(doSelect(diff.getRemovedInstances()));
        }
        if (diff.isModified()) {
            newDiff.setModifiedInstances(doSelect(diff.getModifiedInstances()));
        }
        
        return new NamingChangeEvent(serviceName, groupName, clusters, currentIns, newDiff);
    }
    
    private List<Instance> doSelect(List<Instance> instances) {
        NamingContext context = getNamingContext(instances);
        return this.getSelector().select(context).getResult();
    }
    
    private NamingContext getNamingContext(final List<Instance> instances) {
        namingContext.setInstances(instances);
        return namingContext;
    }
}
