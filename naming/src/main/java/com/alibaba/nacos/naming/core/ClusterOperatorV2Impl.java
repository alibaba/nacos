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
import com.alibaba.nacos.naming.core.v2.metadata.ClusterMetadata;
import com.alibaba.nacos.naming.core.v2.metadata.NamingMetadataOperateService;
import com.alibaba.nacos.naming.core.v2.pojo.Service;

import java.util.Optional;

/**
 * Implementation of cluster operator for v2.x.
 *
 * @author xiweng.yy
 */
@org.springframework.stereotype.Service
public class ClusterOperatorV2Impl implements ClusterOperator {
    
    private final NamingMetadataOperateService metadataOperateService;
    
    public ClusterOperatorV2Impl(NamingMetadataOperateService metadataOperateService) {
        this.metadataOperateService = metadataOperateService;
    }
    
    @Override
    public void updateClusterMetadata(String namespaceId, String serviceName, String clusterName,
            ClusterMetadata clusterMetadata) throws NacosException {
        String groupName = NamingUtils.getGroupName(serviceName);
        String serviceNameWithoutGroup = NamingUtils.getServiceName(serviceName);
        Optional<Service> service = ServiceManager.getInstance()
                .getSingletonIfExist(namespaceId, groupName, serviceNameWithoutGroup);
        if (!service.isPresent()) {
            throw new NacosException(NacosException.INVALID_PARAM, "service not found:" + serviceName);
        }
        metadataOperateService.addClusterMetadata(service.get(), clusterName, clusterMetadata);
    }
    
}
