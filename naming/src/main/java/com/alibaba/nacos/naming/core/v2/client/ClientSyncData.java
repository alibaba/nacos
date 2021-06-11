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

package com.alibaba.nacos.naming.core.v2.client;

import com.alibaba.nacos.naming.core.v2.pojo.InstancePublishInfo;

import java.io.Serializable;
import java.util.List;

/**
 * Client sync data.
 *
 * @author xiweng.yy
 */
public class ClientSyncData implements Serializable {
    
    private static final long serialVersionUID = -5141768777704539562L;
    
    private String clientId;
    
    private ClientSyncAttributes attributes;
    
    private List<String> namespaces;
    
    private List<String> groupNames;
    
    private List<String> serviceNames;
    
    private List<InstancePublishInfo> instancePublishInfos;
    
    public ClientSyncData() {
    }
    
    public ClientSyncData(String clientId, List<String> namespaces, List<String> groupNames, List<String> serviceNames,
            List<InstancePublishInfo> instancePublishInfos) {
        this.clientId = clientId;
        this.namespaces = namespaces;
        this.groupNames = groupNames;
        this.serviceNames = serviceNames;
        this.instancePublishInfos = instancePublishInfos;
        this.attributes = new ClientSyncAttributes();
    }
    
    public String getClientId() {
        return clientId;
    }
    
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }
    
    public List<String> getNamespaces() {
        return namespaces;
    }
    
    public void setNamespaces(List<String> namespaces) {
        this.namespaces = namespaces;
    }
    
    public List<String> getGroupNames() {
        return groupNames;
    }
    
    public void setGroupNames(List<String> groupNames) {
        this.groupNames = groupNames;
    }
    
    public List<String> getServiceNames() {
        return serviceNames;
    }
    
    public void setServiceNames(List<String> serviceNames) {
        this.serviceNames = serviceNames;
    }
    
    public List<InstancePublishInfo> getInstancePublishInfos() {
        return instancePublishInfos;
    }
    
    public void setInstancePublishInfos(List<InstancePublishInfo> instancePublishInfos) {
        this.instancePublishInfos = instancePublishInfos;
    }
    
    public ClientSyncAttributes getAttributes() {
        return attributes;
    }
    
    public void setAttributes(ClientSyncAttributes attributes) {
        this.attributes = attributes;
    }
}
