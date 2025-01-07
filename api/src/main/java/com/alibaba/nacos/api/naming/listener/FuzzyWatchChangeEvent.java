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

/**
 * Fuzzy Watch Notify Event.
 *
 * @author tanyongquan
 */
public class FuzzyWatchChangeEvent implements Event {
    
    private String serviceName;
    
    private String groupName;
    
    private String namespace;
    
    private String changeType;
    
    private String syncType;
    
    public FuzzyWatchChangeEvent() {
    }
    
    public FuzzyWatchChangeEvent(String serviceName,String groupName,String namespace, String changeType,String syncType) {
        this.changeType = changeType;
        this.serviceName=serviceName;
        this.groupName=groupName;
        this.namespace=namespace;
        this.syncType=syncType;
    }
    
    public String getServiceName() {
        return serviceName;
    }
    
    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }
    
    public String getGroupName() {
        return groupName;
    }
    
    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }
    
    public String getNamespace() {
        return namespace;
    }
    
    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }
    
    public String getChangeType() {
        return changeType;
    }
    
    public void setChangeType(String changeType) {
        this.changeType = changeType;
    }
}
