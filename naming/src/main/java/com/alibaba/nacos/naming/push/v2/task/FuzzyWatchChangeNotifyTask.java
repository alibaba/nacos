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

/**
 * Nacos naming fuzzy watch notify service change push delay task.
 *
 * @author tanyongquan
 */
public class FuzzyWatchChangeNotifyTask extends AbstractDelayTask {
    
    private final String serviceKey;
    
    private final String changedType;
    
    private final String clientId;
    
    private final long delay;
    
    public FuzzyWatchChangeNotifyTask(String serviceKey, String changedType, String clientId, long delay) {
        this.serviceKey = serviceKey;
        this.changedType = changedType;
        this.delay = delay;
        this.clientId = clientId;
        
    }
    
    public String getChangedType() {
        return changedType;
    }
    
    public String getClientId() {
        return clientId;
    }
    
    public long getDelay() {
        return delay;
    }
    
    public String getServiceKey() {
        return serviceKey;
    }
    
    @Override
    public void merge(AbstractDelayTask task) {
    
    }
}
