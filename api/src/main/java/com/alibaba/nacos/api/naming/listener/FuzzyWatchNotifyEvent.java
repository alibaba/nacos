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

package com.alibaba.nacos.api.naming.listener;

import com.alibaba.nacos.api.naming.pojo.Service;

/**
 * Fuzzy Watch Notify Event.
 *
 * @author tanyongquan
 */
public class FuzzyWatchNotifyEvent implements Event {
    
    private Service service;
    
    private String changeType;
    
    public FuzzyWatchNotifyEvent() {
    }
    
    public FuzzyWatchNotifyEvent(Service service, String changeType) {
        this.service = service;
        this.changeType = changeType;
    }
    
    public Service getService() {
        return service;
    }
    
    public void setService(Service service) {
        this.service = service;
    }
    
    public String getChangeType() {
        return changeType;
    }
    
    public void setChangeType(String changeType) {
        this.changeType = changeType;
    }
}
