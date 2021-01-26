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

package com.alibaba.nacos.naming.core.v2.upgrade.doublewrite.execute;

import com.alibaba.nacos.common.task.AbstractExecuteTask;
import com.alibaba.nacos.naming.core.Cluster;
import com.alibaba.nacos.naming.core.ServiceManager;
import com.alibaba.nacos.naming.core.v2.metadata.ClusterMetadata;
import com.alibaba.nacos.naming.core.v2.metadata.NamingMetadataManager;
import com.alibaba.nacos.naming.core.v2.metadata.ServiceMetadata;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.core.v2.upgrade.doublewrite.delay.DoubleWriteDelayTaskEngine;
import com.alibaba.nacos.naming.core.v2.upgrade.doublewrite.delay.ServiceChangeV2Task;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.sys.utils.ApplicationUtils;

import java.util.Map;
import java.util.Optional;

/**
 * Double write from 2.x to 1 task.
 *
 * @author xiweng.yy
 */
public class DoubleWriteMetadataChangeToV1Task extends AbstractExecuteTask {
    
    private final Service service;
    
    public DoubleWriteMetadataChangeToV1Task(Service service) {
        this.service = service;
    }
    
    @Override
    public void run() {
        try {
            NamingMetadataManager metadataManager = ApplicationUtils.getBean(NamingMetadataManager.class);
            Optional<ServiceMetadata> serviceMetadata = metadataManager.getServiceMetadata(service);
            if (!serviceMetadata.isPresent()) {
                return;
            }
            ServiceManager serviceManager = ApplicationUtils.getBean(ServiceManager.class);
            com.alibaba.nacos.naming.core.Service serviceV1 = newServiceForV1(serviceManager, serviceMetadata.get());
            serviceManager.addOrReplaceService(serviceV1);
        } catch (Exception e) {
            if (Loggers.SRV_LOG.isDebugEnabled()) {
                Loggers.SRV_LOG.debug("Double write task for {} metadata from 2 to 1 failed", service, e);
            }
            ServiceChangeV2Task retryTask = new ServiceChangeV2Task(service);
            retryTask.setTaskInterval(3000L);
            String taskKey = ServiceChangeV2Task.getKey(service);
            ApplicationUtils.getBean(DoubleWriteDelayTaskEngine.class).addTask(taskKey, retryTask);
        }
    }
    
    private com.alibaba.nacos.naming.core.Service newServiceForV1(ServiceManager serviceManager,
            ServiceMetadata serviceMetadata) {
        com.alibaba.nacos.naming.core.Service result = serviceManager
                .getService(service.getNamespace(), service.getGroupedServiceName());
        if (null == result) {
            result = new com.alibaba.nacos.naming.core.Service(service.getGroupedServiceName());
            result.setGroupName(service.getGroup());
            result.setNamespaceId(service.getNamespace());
        }
        result.setSelector(serviceMetadata.getSelector());
        result.setProtectThreshold(serviceMetadata.getProtectThreshold());
        result.setMetadata(serviceMetadata.getExtendData());
        for (Map.Entry<String, ClusterMetadata> entry : serviceMetadata.getClusters().entrySet()) {
            if (!result.getClusterMap().containsKey(entry.getKey())) {
                result.addCluster(newClusterV1(entry.getValue()));
            } else {
                updateCluster(result.getClusterMap().get(entry.getKey()), entry.getValue());
            }
        }
        result.init();
        return result;
    }
    
    private Cluster newClusterV1(ClusterMetadata metadata) {
        Cluster result = new Cluster();
        result.setDefCkport(metadata.getHealthyCheckPort());
        result.setUseIPPort4Check(metadata.isUseInstancePortForCheck());
        result.setHealthChecker(metadata.getHealthChecker());
        result.setMetadata(metadata.getExtendData());
        return result;
    }
    
    private void updateCluster(Cluster cluster, ClusterMetadata metadata) {
        cluster.setDefCkport(metadata.getHealthyCheckPort());
        cluster.setUseIPPort4Check(metadata.isUseInstancePortForCheck());
        cluster.setHealthChecker(metadata.getHealthChecker());
        cluster.setMetadata(metadata.getExtendData());
    }
}
