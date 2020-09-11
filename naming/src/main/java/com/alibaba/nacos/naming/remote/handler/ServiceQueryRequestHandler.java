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
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.api.naming.remote.request.ServiceQueryRequest;
import com.alibaba.nacos.api.naming.remote.response.QueryServiceResponse;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.remote.RequestHandler;
import com.alibaba.nacos.naming.core.v2.index.ServiceStorage;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * Nacos query instances request handler.
 *
 * @author xiweng.yy
 */
@Component
public class ServiceQueryRequestHandler extends RequestHandler<ServiceQueryRequest, QueryServiceResponse> {
    
    private final ServiceStorage serviceStorage;
    
    public ServiceQueryRequestHandler(ServiceStorage serviceStorage) {
        this.serviceStorage = serviceStorage;
    }
    
    @Override
    public QueryServiceResponse handle(ServiceQueryRequest request, RequestMeta meta) throws NacosException {
        String namespaceId = request.getNamespace();
        String groupName = request.getGroupName();
        String serviceName = request.getServiceName();
        Service service = Service.newService(namespaceId, groupName, serviceName);
        String cluster = null == request.getCluster() ? "" : request.getCluster();
        boolean healthyOnly = request.isHealthyOnly();
        ServiceInfo result = serviceStorage.getData(service);
        result = filterInstance(result, cluster, healthyOnly);
        return QueryServiceResponse.buildSuccessResponse(result);
    }
    
    private ServiceInfo filterInstance(ServiceInfo serviceInfo, String cluster, boolean healthyOnly) {
        ServiceInfo result = new ServiceInfo();
        result.setName(serviceInfo.getName());
        result.setGroupName(serviceInfo.getGroupName());
        result.setCacheMillis(serviceInfo.getCacheMillis());
        result.setLastRefTime(System.currentTimeMillis());
        result.setClusters(cluster);
        Set<String> clusterSets =
                StringUtils.isNotBlank(cluster) ? new HashSet<>(Arrays.asList(cluster.split(","))) : new HashSet<>();
        List<Instance> filteredInstance = new LinkedList<>();
        for (Instance each : serviceInfo.getHosts()) {
            if (checkCluster(clusterSets, each) && checkHealthy(healthyOnly, each)) {
                filteredInstance.add(each);
            }
        }
        result.setHosts(filteredInstance);
        return result;
    }
    
    private boolean checkCluster(Set<String> clusterSets, Instance instance) {
        if (clusterSets.isEmpty()) {
            return true;
        }
        return clusterSets.contains(instance.getClusterName());
    }
    
    private boolean checkHealthy(boolean healthyOnly, Instance instance) {
        return !healthyOnly || instance.isHealthy();
    }
    
}
