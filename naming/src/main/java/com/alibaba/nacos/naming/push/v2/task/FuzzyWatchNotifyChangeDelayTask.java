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

package com.alibaba.nacos.naming.push.v2.task;

import com.alibaba.nacos.common.task.AbstractDelayTask;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.misc.Loggers;

import java.util.HashSet;
import java.util.Set;

public class FuzzyWatchNotifyChangeDelayTask extends AbstractDelayTask {
    private final Service service;
    
    private final String serviceChangedType;
    
    private boolean pushToAll;
    
    private Set<String> targetClients;
    
    public FuzzyWatchNotifyChangeDelayTask(Service service, String serviceChangedType, long delay) {
        this.service = service;
        this.serviceChangedType = serviceChangedType;
        pushToAll = true;
        targetClients = null;
        setTaskInterval(delay);
        setLastProcessTime(System.currentTimeMillis());
    }
    
    public FuzzyWatchNotifyChangeDelayTask(Service service, String serviceChangedType, long delay, String targetClient) {
        this.service = service;
        this.serviceChangedType = serviceChangedType;
        this.pushToAll = false;
        this.targetClients = new HashSet<>(1);
        this.targetClients.add(targetClient);
        setTaskInterval(delay);
        setLastProcessTime(System.currentTimeMillis());
    }
    
    @Override
    public void merge(AbstractDelayTask task) {
        if (!(task instanceof FuzzyWatchNotifyChangeDelayTask)) {
            return;
        }
        FuzzyWatchNotifyChangeDelayTask oldTask = (FuzzyWatchNotifyChangeDelayTask) task;
        if (isPushToAll() || oldTask.isPushToAll()) {
            pushToAll = true;
            targetClients = null;
        } else {
            targetClients.addAll(oldTask.getTargetClients());
        }
        setLastProcessTime(Math.min(getLastProcessTime(), task.getLastProcessTime()));
        Loggers.PUSH.info("[FUZZY-WATCH-PUSH] Task merge for {}", service);
    }
    
    public Service getService() {
        return service;
    }
    
    public boolean isPushToAll() {
        return pushToAll;
    }
    
    public String getServiceChangedType() {
        return serviceChangedType;
    }
    
    public Set<String> getTargetClients() {
        return targetClients;
    }
}
