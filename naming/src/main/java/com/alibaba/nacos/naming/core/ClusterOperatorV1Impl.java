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
import com.alibaba.nacos.naming.core.v2.metadata.ClusterMetadata;
import com.alibaba.nacos.naming.misc.Loggers;

/**
 * Implementation of cluster operator for v1.x.
 *
 * @author xiweng.yy
 */
@org.springframework.stereotype.Service
public class ClusterOperatorV1Impl implements ClusterOperator {
    
    private final ServiceManager serviceManager;
    
    public ClusterOperatorV1Impl(ServiceManager serviceManager) {
        this.serviceManager = serviceManager;
    }
    
    @Override
    public void updateClusterMetadata(String namespaceId, String serviceName, String clusterName,
            ClusterMetadata clusterMetadata) throws NacosException {
        Service service = serviceManager.getService(namespaceId, serviceName);
        
        serviceManager.checkServiceIsNull(service, namespaceId, serviceName);
        
        Cluster cluster = service.getClusterMap().get(clusterName);
        if (cluster == null) {
            Loggers.SRV_LOG.warn("[UPDATE-CLUSTER] cluster not exist, will create it: {}, service: {}", clusterName,
                    serviceName);
            cluster = new Cluster(clusterName, service);
        }
        cluster.setDefCkport(clusterMetadata.getHealthyCheckPort());
        cluster.setUseIPPort4Check(clusterMetadata.isUseInstancePortForCheck());
        cluster.setHealthChecker(clusterMetadata.getHealthChecker());
        cluster.setMetadata(clusterMetadata.getExtendData());
        cluster.init();
        service.getClusterMap().put(clusterName, cluster);
        service.setLastModifiedMillis(System.currentTimeMillis());
        service.recalculateChecksum();
        service.validate();
        serviceManager.addOrReplaceService(service);
    }
    
}
