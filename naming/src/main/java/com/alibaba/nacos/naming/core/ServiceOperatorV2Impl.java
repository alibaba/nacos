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

package com.alibaba.nacos.naming.core;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.utils.NamingUtils;
import com.alibaba.nacos.naming.core.v2.ServiceManager;
import com.alibaba.nacos.naming.core.v2.metadata.NamingMetadataOperateService;
import com.alibaba.nacos.naming.core.v2.metadata.ServiceMetadata;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.utils.ServiceUtil;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * Implementation of service operator for v2.x.
 *
 * @author xiweng.yy
 */
@Component
public class ServiceOperatorV2Impl implements ServiceOperator {
    
    private final NamingMetadataOperateService metadataOperateService;
    
    public ServiceOperatorV2Impl(NamingMetadataOperateService metadataOperateService) {
        this.metadataOperateService = metadataOperateService;
    }
    
    @Override
    public void create(String namespaceId, String serviceName, ServiceMetadata metadata) throws NacosException {
        Service service = getServiceFromGroupedServiceName(namespaceId, serviceName, metadata.isEphemeral());
        if (ServiceManager.getInstance().containSingleton(service)) {
            throw new NacosException(NacosException.INVALID_PARAM,
                    String.format("specified service %s already exists!", service.getGroupedServiceName()));
        }
        metadataOperateService.updateServiceMetadata(service, metadata);
    }
    
    @Override
    public void update(Service service, ServiceMetadata metadata) throws NacosException {
        if (!ServiceManager.getInstance().containSingleton(service)) {
            throw new NacosException(NacosException.INVALID_PARAM,
                    String.format("service %s not found!", service.getGroupedServiceName()));
        }
        metadataOperateService.updateServiceMetadata(service, metadata);
    }
    
    @Override
    public void delete(String namespaceId, String serviceName) throws NacosException {
        metadataOperateService.deleteServiceMetadata(getServiceFromGroupedServiceName(namespaceId, serviceName, true));
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public List<String> listService(String namespaceId, String groupName, String selector, int pageSize, int pageNo)
            throws NacosException {
        Collection<Service> services = ServiceManager.getInstance().getSingletons(namespaceId);
        if (services.isEmpty()) {
            return Collections.EMPTY_LIST;
        }
        Collection<String> serviceNameSet = selectServiceWithGroupName(services, groupName);
        // TODO select service by selector
        return ServiceUtil.pageServiceName(pageNo, pageSize, serviceNameSet);
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
    
    private Service getServiceFromGroupedServiceName(String namespaceId, String groupedServiceName, boolean ephemeral) {
        String groupName = NamingUtils.getGroupName(groupedServiceName);
        String serviceName = NamingUtils.getServiceName(groupedServiceName);
        return Service.newService(namespaceId, groupName, serviceName, ephemeral);
    }
}
