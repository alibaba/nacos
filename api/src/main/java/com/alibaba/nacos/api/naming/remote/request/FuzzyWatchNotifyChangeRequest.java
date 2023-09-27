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

package com.alibaba.nacos.api.naming.remote.request;

/**
 * Nacos fuzzy watch notify service change request, use it when one of the services changes.
 *
 * @author tanyongquan
 */
public class FuzzyWatchNotifyChangeRequest extends AbstractFuzzyWatchNotifyRequest {
    
    String serviceName;
    
    String groupName;
    
    public FuzzyWatchNotifyChangeRequest() {
    }
    
    public FuzzyWatchNotifyChangeRequest(String namespace, String serviceName,
            String groupName, String serviceChangedType) {
        super(namespace, "", serviceChangedType);
        this.serviceName = serviceName;
        this.groupName = groupName;
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
}
