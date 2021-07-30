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

import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.api.remote.response.ResponseCode;

import java.util.List;

/**
 * Service list response.
 *
 * @author xiweng.yy
 */
public class ServiceListResponse extends Response {
    
    private int count;
    
    private List<String> serviceNames;
    
    public ServiceListResponse(){
    }
    
    private ServiceListResponse(int count, List<String> serviceNames, String message) {
        this.count = count;
        this.serviceNames = serviceNames;
    }
    
    public static ServiceListResponse buildSuccessResponse(int count, List<String> serviceNames) {
        return new ServiceListResponse(count, serviceNames, "success");
    }
    
    /**
     * Build fail response.
     *
     * @param message error message
     * @return fail response
     */
    public static ServiceListResponse buildFailResponse(String message) {
        ServiceListResponse result = new ServiceListResponse();
        result.setErrorInfo(ResponseCode.FAIL.getCode(), message);
        return result;
    }
    
    public int getCount() {
        return count;
    }
    
    public void setCount(int count) {
        this.count = count;
    }
    
    public List<String> getServiceNames() {
        return serviceNames;
    }
    
    public void setServiceNames(List<String> serviceNames) {
        this.serviceNames = serviceNames;
    }
}
