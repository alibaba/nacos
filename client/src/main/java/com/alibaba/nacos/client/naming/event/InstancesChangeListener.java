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

package com.alibaba.nacos.client.naming.event;

import com.alibaba.nacos.api.naming.listener.EventListener;
import com.alibaba.nacos.api.naming.listener.NamingEvent;
import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.listener.Subscriber;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

/**
 * A subscriber to notify eventListener callback.
 *
 * @author horizonzy
 * @since 1.4.0
 */
public class InstancesChangeListener extends Subscriber<InstancesChangeEvent> {
    
    private final String serviceName;
    
    private final String clusters;
    
    private final EventListener eventListener;
    
    private final ExecutorService executorService;
    
    public InstancesChangeListener(String serviceName, String clusters, EventListener eventListener,
            ExecutorService executorService) {
        this.serviceName = serviceName;
        this.clusters = clusters;
        this.eventListener = eventListener;
        this.executorService = executorService;
    }
    
    @Override
    public void onEvent(InstancesChangeEvent event) {
        eventListener.onEvent(transferToNamingEvent(event));
    }
    
    private com.alibaba.nacos.api.naming.listener.Event transferToNamingEvent(
            InstancesChangeEvent instancesChangeEvent) {
        return new NamingEvent(instancesChangeEvent.getServiceName(), instancesChangeEvent.getGroupName(),
                instancesChangeEvent.getClusters(), instancesChangeEvent.getHosts());
    }
    
    @Override
    public Class<? extends Event> subscribeType() {
        return InstancesChangeEvent.class;
    }
    
    @Override
    public Executor executor() {
        return executorService;
    }
    
    public String getServiceName() {
        return serviceName;
    }
    
    public String getClusters() {
        return clusters;
    }
    
    public EventListener getEventListener() {
        return eventListener;
    }
    
    public ExecutorService getExecutorService() {
        return executorService;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof InstancesChangeListener)) {
            return false;
        }
        if (!serviceName.equals(((InstancesChangeListener) obj).getServiceName())) {
            return false;
        }
        if (!clusters.equals(((InstancesChangeListener) obj).getClusters())) {
            return false;
        }
        if (eventListener != ((InstancesChangeListener) obj).getEventListener()) {
            return false;
        }
        if (executorService != ((InstancesChangeListener) obj).getExecutorService()) {
            return false;
        }
        return true;
    }
    
    @Override
    public int hashCode() {
        int prime = 31;
        int result = 1;
        result = prime * result + ((serviceName == null) ? 0 : serviceName.hashCode());
        result = prime * result + ((clusters == null) ? 0 : clusters.hashCode());
        result = prime * result + ((eventListener == null) ? 0 : eventListener.hashCode());
        result = prime * result + ((executorService == null) ? 0 : executorService.hashCode());
        return result;
    }
}
