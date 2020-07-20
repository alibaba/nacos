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
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.api.naming.remote.NamingRemoteConstants;
import com.alibaba.nacos.api.naming.remote.request.ServiceQueryRequest;
import com.alibaba.nacos.api.naming.remote.response.QueryServiceResponse;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.core.remote.RequestHandler;
import com.alibaba.nacos.naming.core.ServiceInfoGenerator;
import com.google.common.collect.Lists;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Nacos query instances request handler.
 *
 * @author xiweng.yy
 */
@Component
public class ServiceQueryRequestHandler extends RequestHandler<ServiceQueryRequest> {
    
    private final ServiceInfoGenerator serviceInfoGenerator;
    
    public ServiceQueryRequestHandler(ServiceInfoGenerator serviceInfoGenerator) {
        this.serviceInfoGenerator = serviceInfoGenerator;
    }
    
    @Override
    public ServiceQueryRequest parseBodyString(String bodyString) {
        return JacksonUtils.toObj(bodyString, ServiceQueryRequest.class);
    }
    
    @Override
    public Response handle(Request request, RequestMeta meta) throws NacosException {
        ServiceQueryRequest queryRequest = (ServiceQueryRequest) request;
        String namespaceId = queryRequest.getNamespace();
        String serviceName = queryRequest.getServiceName();
        String cluster = null == queryRequest.getCluster() ? "" : queryRequest.getCluster();
        boolean healthyOnly = queryRequest.isHealthyOnly();
        ServiceInfo result = serviceInfoGenerator
                .generateServiceInfo(namespaceId, serviceName, cluster, healthyOnly, meta.getClientIp());
        return QueryServiceResponse.buildSuccessResponse(result);
    }
    
    @Override
    public List<String> getRequestTypes() {
        return Lists.newArrayList(NamingRemoteConstants.QUERY_SERVICE);
    }
}
