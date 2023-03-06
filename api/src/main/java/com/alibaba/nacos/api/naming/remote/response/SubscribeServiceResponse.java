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

package com.alibaba.nacos.api.naming.remote.response;

import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.api.remote.response.Response;

/**
 * Nacos naming subscribe service response.
 *
 * @author xiweng.yy
 */
public class SubscribeServiceResponse extends Response {
    
    private ServiceInfo serviceInfo;
    
    public SubscribeServiceResponse() {
    }
    
    public SubscribeServiceResponse(int resultCode, String message, ServiceInfo serviceInfo) {
        super();
        setResultCode(resultCode);
        setMessage(message);
        this.serviceInfo = serviceInfo;
    }
    
    public ServiceInfo getServiceInfo() {
        return serviceInfo;
    }
    
    public void setServiceInfo(ServiceInfo serviceInfo) {
        this.serviceInfo = serviceInfo;
    }
}
