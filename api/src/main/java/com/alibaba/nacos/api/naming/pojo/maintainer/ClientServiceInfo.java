/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.api.naming.pojo.maintainer;

import java.io.Serializable;

/**
 * Nacos naming client service information.
 *
 * @author xiweng.yy
 */
public class ClientServiceInfo implements Serializable {
    
    private static final long serialVersionUID = 7400821120040393395L;
    
    private String namespaceId;
    
    private String groupName;
    
    private String serviceName;
    
    private ClientPublisherInfo publisherInfo;
    
    private ClientSubscriberInfo subscriberInfo;
    
    public String getNamespaceId() {
        return namespaceId;
    }
    
    public void setNamespaceId(String namespaceId) {
        this.namespaceId = namespaceId;
    }
    
    public String getGroupName() {
        return groupName;
    }
    
    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }
    
    public String getServiceName() {
        return serviceName;
    }
    
    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }
    
    public ClientPublisherInfo getPublisherInfo() {
        return publisherInfo;
    }
    
    public void setPublisherInfo(ClientPublisherInfo publisherInfo) {
        this.publisherInfo = publisherInfo;
    }
    
    public ClientSubscriberInfo getSubscriberInfo() {
        return subscriberInfo;
    }
    
    public void setSubscriberInfo(ClientSubscriberInfo subscriberInfo) {
        this.subscriberInfo = subscriberInfo;
    }
}
