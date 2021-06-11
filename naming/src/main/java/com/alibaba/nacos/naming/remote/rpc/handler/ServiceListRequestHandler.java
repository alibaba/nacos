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

package com.alibaba.nacos.naming.remote.rpc.handler;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.remote.request.ServiceListRequest;
import com.alibaba.nacos.api.naming.remote.response.ServiceListResponse;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.auth.common.ActionTypes;
import com.alibaba.nacos.core.remote.RequestHandler;
import com.alibaba.nacos.naming.core.v2.ServiceManager;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.utils.ServiceUtil;
import com.alibaba.nacos.naming.web.NamingResourceParser;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

/**
 * Service list request handler.
 *
 * @author xiweng.yy
 */
@Component
public class ServiceListRequestHandler extends RequestHandler<ServiceListRequest, ServiceListResponse> {
    
    @Override
    @Secured(action = ActionTypes.READ, parser = NamingResourceParser.class)
    public ServiceListResponse handle(ServiceListRequest request, RequestMeta meta) throws NacosException {
        Collection<Service> serviceSet = ServiceManager.getInstance().getSingletons(request.getNamespace());
        ServiceListResponse result = ServiceListResponse.buildSuccessResponse(0, new LinkedList<>());
        if (!serviceSet.isEmpty()) {
            Collection<String> serviceNameSet = selectServiceWithGroupName(serviceSet, request.getGroupName());
            // TODO select service by selector
            List<String> serviceNameList = ServiceUtil
                    .pageServiceName(request.getPageNo(), request.getPageSize(), serviceNameSet);
            result.setCount(serviceNameList.size());
            result.setServiceNames(serviceNameList);
        }
        return result;
    }
    
    private Collection<String> selectServiceWithGroupName(Collection<Service> serviceSet, String groupName) {
        Collection<String> result = new HashSet<>(serviceSet.size());
        for (Service each : serviceSet) {
            if (Objects.equals(groupName, each.getGroup())) {
                result.add(each.getGroupedServiceName());
            }
        }
        return result;
    }
    
}
