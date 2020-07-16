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
import com.alibaba.nacos.api.naming.remote.response.ServiceQueryResponse;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.core.remote.RequestHandler;
import com.alibaba.nacos.naming.core.Instance;
import com.alibaba.nacos.naming.core.Service;
import com.alibaba.nacos.naming.core.ServiceManager;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Nacos query instances request handler.
 *
 * @author xiweng.yy
 */
@Component
public class ServiceQueryRequestHandler extends RequestHandler<ServiceQueryRequest> {
    
    @Autowired
    private ServiceManager serviceManager;
    
    @Autowired
    private SwitchDomain switchDomain;
    
    @Override
    public ServiceQueryRequest parseBodyString(String bodyString) {
        return JacksonUtils.toObj(bodyString, ServiceQueryRequest.class);
    }
    
    @Override
    public Response handle(Request request, RequestMeta meta) throws NacosException {
        ServiceQueryRequest queryRequest = (ServiceQueryRequest) request;
        String namespaceId = queryRequest.getNamespace();
        String serviceName = queryRequest.getServiceName();
        if (!serviceManager.containService(namespaceId, serviceName)) {
            return new ServiceQueryResponse(new ServiceInfo(serviceName, queryRequest.getCluster()));
        }
        Service service = serviceManager.getService(namespaceId, serviceName);
        if (!service.getEnabled()) {
            throw new NacosException(NacosException.SERVER_ERROR,
                    String.format("Service %s : %s is disable now", namespaceId, serviceName));
        }
        // TODO the origin logic in {@link InstanceController#doSrvIpxt will try to add push.
        ServiceInfo result = new ServiceInfo(serviceName, queryRequest.getCluster());
        List<Instance> instances = getInstanceFromService(service, queryRequest, meta);
        result.addAllHosts(instances);
        result.setName(serviceName);
        result.setCacheMillis(switchDomain.getDefaultCacheMillis());
        result.setLastRefTime(System.currentTimeMillis());
        result.setChecksum(service.getChecksum());
        result.setClusters(queryRequest.getCluster());
        // TODO there are some parameters do not include in service info, but added to return in origin logic
        return new ServiceQueryResponse(result);
    }
    
    private List<Instance> getInstanceFromService(Service service, ServiceQueryRequest queryRequest, RequestMeta meta) {
        List<Instance> result = service.srvIPs(Arrays.asList(StringUtils.split(queryRequest.getCluster(), ",")));
        if (service.getSelector() != null && StringUtils.isNotBlank(meta.getClientIp())) {
            result = service.getSelector().select(meta.getClientIp(), result);
        }
        return result.isEmpty() ? result
                : queryRequest.isHealthyOnly() ? doProtectThreshold(service, queryRequest, result) : result;
    }
    
    private List<Instance> doProtectThreshold(Service service, ServiceQueryRequest queryRequest,
            List<Instance> instances) {
        Map<Boolean, List<Instance>> healthyInstancesMap = new HashMap<>();
        healthyInstancesMap.put(Boolean.TRUE, new LinkedList<>());
        healthyInstancesMap.put(Boolean.FALSE, new LinkedList<>());
        for (Instance each : instances) {
            healthyInstancesMap.get(each.isHealthy()).add(each);
        }
        if ((float) healthyInstancesMap.get(Boolean.TRUE).size() / instances.size() <= service.getProtectThreshold()) {
            Loggers.SRV_LOG.warn("protect threshold reached, return all ips, service: {}", service.getName());
            healthyInstancesMap.get(Boolean.TRUE).addAll(healthyInstancesMap.get(Boolean.FALSE));
            healthyInstancesMap.get(Boolean.FALSE).clear();
        }
        return healthyInstancesMap.get(Boolean.TRUE);
    }
    
    @Override
    public List<String> getRequestTypes() {
        return Lists.newArrayList(NamingRemoteConstants.QUERY_SERVICE);
    }
}
