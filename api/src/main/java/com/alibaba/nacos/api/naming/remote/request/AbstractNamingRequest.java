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

package com.alibaba.nacos.api.naming.remote.request;

import com.alibaba.nacos.api.remote.request.Request;

/**
 * Uniform remote request of naming module.
 *
 * @author liuzunfei
 */
public abstract class AbstractNamingRequest extends Request {
    
    private static final String MODULE = "naming";
    
    private String namespace;
    
    private String serviceName;
    
    private String groupName;
    
    public AbstractNamingRequest() {
    }
    
    public AbstractNamingRequest(String namespace, String serviceName, String groupName) {
        this.namespace = namespace;
        this.serviceName = serviceName;
        this.groupName = groupName;
    }
    
    @Override
    public String getModule() {
        return MODULE;
    }
    
    public String getNamespace() {
        return namespace;
    }
    
    public void setNamespace(String namespace) {
        this.namespace = namespace;
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
