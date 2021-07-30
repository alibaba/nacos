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

import com.alibaba.nacos.api.naming.pojo.healthcheck.HealthCheckType;
import com.alibaba.nacos.api.naming.utils.NamingUtils;
import com.alibaba.nacos.common.task.AbstractExecuteTask;
import com.alibaba.nacos.naming.core.v2.metadata.ClusterMetadata;
import com.alibaba.nacos.naming.core.v2.metadata.NamingMetadataOperateService;
import com.alibaba.nacos.naming.core.v2.metadata.ServiceMetadata;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.core.v2.upgrade.doublewrite.delay.DoubleWriteContent;
import com.alibaba.nacos.naming.core.v2.upgrade.doublewrite.delay.DoubleWriteDelayTaskEngine;
import com.alibaba.nacos.naming.core.v2.upgrade.doublewrite.delay.ServiceChangeV1Task;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.selector.NoneSelector;
import com.alibaba.nacos.sys.utils.ApplicationUtils;

import java.util.Map;

/**
 * Double write from 1.x to 2 task.
 *
 * @author xiweng.yy
 */
public class DoubleWriteMetadataChangeToV2Task extends AbstractExecuteTask {
    
    private final Service service;
    
    private final ServiceMetadata serviceMetadata;
    
    private static final int PORT = 80;
    
    public DoubleWriteMetadataChangeToV2Task(String namespace, String serviceName, boolean ephemeral,
            ServiceMetadata serviceMetadata) {
        this.serviceMetadata = serviceMetadata;
        String groupName = NamingUtils.getGroupName(serviceName);
        String serviceNameWithoutGroup = NamingUtils.getServiceName(serviceName);
        this.service = Service.newService(namespace, groupName, serviceNameWithoutGroup, ephemeral);
    }
    
    @Override
    public void run() {
        try {
            NamingMetadataOperateService metadataOperate = ApplicationUtils.getBean(NamingMetadataOperateService.class);
            if (!isDefaultServiceMetadata()) {
                metadataOperate.updateServiceMetadata(service, serviceMetadata);
            }
            for (Map.Entry<String, ClusterMetadata> entry : serviceMetadata.getClusters().entrySet()) {
                if (!isDefaultClusterMetadata(entry.getValue())) {
                    metadataOperate.addClusterMetadata(service, entry.getKey(), entry.getValue());
                }
            }
        } catch (Exception e) {
            if (Loggers.SRV_LOG.isDebugEnabled()) {
                Loggers.SRV_LOG.debug("Double write task for {} metadata from 1 to 2 failed", service, e);
            }
            ServiceChangeV1Task retryTask = new ServiceChangeV1Task(service.getNamespace(),
                    service.getGroupedServiceName(), service.isEphemeral(), DoubleWriteContent.METADATA);
            retryTask.setTaskInterval(INTERVAL);
            String taskKey = ServiceChangeV1Task
                    .getKey(service.getNamespace(), service.getGroupedServiceName(), service.isEphemeral());
            ApplicationUtils.getBean(DoubleWriteDelayTaskEngine.class).addTask(taskKey, retryTask);
        }
    }
    
    private boolean isDefaultServiceMetadata() {
        return serviceMetadata.getExtendData().isEmpty() && serviceMetadata.getProtectThreshold() == 0.0F
                && serviceMetadata.getSelector() instanceof NoneSelector && serviceMetadata.isEphemeral();
    }
    
    private boolean isDefaultClusterMetadata(ClusterMetadata metadata) {
        return HealthCheckType.TCP.name().equals(metadata.getHealthyCheckType()) && metadata.getExtendData().isEmpty()
                && metadata.getHealthyCheckPort() == PORT && metadata.isUseInstancePortForCheck();
    }
}
