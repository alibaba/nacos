/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.api.remote.request.ServerRequest;

/**
 * Notify subscriber response.
 *
 * @author xiweng.yy
 */
public class NotifySubscriberRequest extends ServerRequest {
    
    private static final String MODULE = "naming";
    
    private String namespace;
    
    private String serviceName;
    
    private String groupName;
    
    private ServiceInfo serviceInfo;
    
    public NotifySubscriberRequest() {
    }
    
    @Override
    public String getModule() {
        return MODULE;
    }
    
    private NotifySubscriberRequest(ServiceInfo serviceInfo, String message) {
        this.serviceInfo = serviceInfo;
    }
    
    public static NotifySubscriberRequest buildSuccessResponse(ServiceInfo serviceInfo) {
        return new NotifySubscriberRequest(serviceInfo, "success");
    }
    
    /**
     * Build fail response.
     *
     * @param message error message
     * @return fail response
     */
    public static NotifySubscriberRequest buildFailResponse(String message) {
        return new NotifySubscriberRequest();
    }
    
    public ServiceInfo getServiceInfo() {
        return serviceInfo;
    }
    
    public void setServiceInfo(ServiceInfo serviceInfo) {
        this.serviceInfo = serviceInfo;
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
