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

package com.alibaba.nacos.naming.remote.handler;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.remote.NamingRemoteConstants;
import com.alibaba.nacos.api.naming.remote.request.ServiceListRequest;
import com.alibaba.nacos.api.naming.remote.response.ServiceListResponse;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.core.remote.RequestHandler;
import com.alibaba.nacos.naming.core.Service;
import com.alibaba.nacos.naming.core.ServiceManager;
import com.alibaba.nacos.naming.utils.ServiceUtil;
import com.google.common.collect.Lists;
import org.springframework.stereotype.Component;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Service list request handler.
 *
 * @author xiweng.yy
 */
@Component
public class ServiceListRequestHandler extends RequestHandler<ServiceListRequest> {
    
    private final ServiceManager serviceManager;
    
    public ServiceListRequestHandler(ServiceManager serviceManager) {
        this.serviceManager = serviceManager;
    }
    
    @Override
    public ServiceListRequest parseBodyString(String bodyString) {
        return JacksonUtils.toObj(bodyString, ServiceListRequest.class);
    }
    
    @Override
    public Response handle(Request request, RequestMeta meta) throws NacosException {
        ServiceListRequest serviceListRequest = (ServiceListRequest) request;
        Map<String, Service> serviceMap = serviceManager.chooseServiceMap(serviceListRequest.getNamespace());
        ServiceListResponse result = ServiceListResponse.buildSuccessResponse(0, new LinkedList<>());
        if (null != serviceMap && !serviceMap.isEmpty()) {
            serviceMap = ServiceUtil.selectServiceWithGroupName(serviceMap, serviceListRequest.getGroupName());
            serviceMap = ServiceUtil.selectServiceBySelector(serviceMap, serviceListRequest.getSelector());
            List<String> serviceNameList = ServiceUtil
                    .pageServiceName(serviceListRequest.getPageNo(), serviceListRequest.getPageSize(), serviceMap);
            result.setCount(serviceNameList.size());
            result.setServiceNames(serviceNameList);
        }
        return result;
    }
    
    @Override
    public List<String> getRequestTypes() {
        return Lists.newArrayList(NamingRemoteConstants.LIST_SERVICE);
    }
}
