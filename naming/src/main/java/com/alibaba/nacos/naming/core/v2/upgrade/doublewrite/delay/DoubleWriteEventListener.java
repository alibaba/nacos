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

package com.alibaba.nacos.naming.core.v2.upgrade.doublewrite.delay;

import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.notify.listener.Subscriber;
import com.alibaba.nacos.naming.core.Service;
import com.alibaba.nacos.naming.core.v2.event.service.ServiceEvent;
import com.alibaba.nacos.naming.core.v2.upgrade.UpgradeJudgement;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.springframework.stereotype.Component;

/**
 * Event listener for double write.
 *
 * @author xiweng.yy
 */
@Component
public class DoubleWriteEventListener extends Subscriber<ServiceEvent.ServiceChangedEvent> {
    
    private final UpgradeJudgement upgradeJudgement;
    
    private final DoubleWriteDelayTaskEngine doubleWriteDelayTaskEngine;
    
    private volatile boolean startDoubleWrite = true;
    
    public DoubleWriteEventListener(UpgradeJudgement upgradeJudgement,
            DoubleWriteDelayTaskEngine doubleWriteDelayTaskEngine) {
        this.upgradeJudgement = upgradeJudgement;
        this.doubleWriteDelayTaskEngine = doubleWriteDelayTaskEngine;
        NotifyCenter.registerSubscriber(this);
        startDoubleWrite = EnvUtil.getStandaloneMode();
    }
    
    @Override
    public void onEvent(ServiceEvent.ServiceChangedEvent event) {
        if (!startDoubleWrite) {
            return;
        }
        if (!upgradeJudgement.isUseGrpcFeatures()) {
            return;
        }
        String taskKey = ServiceChangeV2Task.getKey(event.getService());
        ServiceChangeV2Task task = new ServiceChangeV2Task(event.getService(), DoubleWriteContent.INSTANCE);
        doubleWriteDelayTaskEngine.addTask(taskKey, task);
    }
    
    @Override
    public Class<? extends Event> subscribeType() {
        return ServiceEvent.ServiceChangedEvent.class;
    }
    
    /**
     * Double write service metadata from v2 to v1.
     *
     * @param service service for v2
     */
    public void doubleWriteMetadataToV1(com.alibaba.nacos.naming.core.v2.pojo.Service service) {
        if (!startDoubleWrite) {
            return;
        }
        if (!upgradeJudgement.isUseGrpcFeatures()) {
            return;
        }
        doubleWriteDelayTaskEngine.addTask(ServiceChangeV2Task.getKey(service),
                new ServiceChangeV2Task(service, DoubleWriteContent.METADATA));
    }
    
    /**
     * Double write service from v1 to v2.
     *
     * @param service   service for v1
     * @param ephemeral ephemeral of service
     */
    public void doubleWriteToV2(Service service, boolean ephemeral) {
        if (!startDoubleWrite) {
            return;
        }
        if (upgradeJudgement.isUseGrpcFeatures() || upgradeJudgement.isAll20XVersion()) {
            return;
        }
        String namespace = service.getNamespaceId();
        String serviceName = service.getName();
        doubleWriteDelayTaskEngine.addTask(ServiceChangeV1Task.getKey(namespace, serviceName, ephemeral),
                new ServiceChangeV1Task(namespace, serviceName, ephemeral, DoubleWriteContent.INSTANCE));
    }
    
    /**
     * Double write service metadata from v1 to v2.
     *
     * @param service   service for v1
     * @param ephemeral ephemeral of service
     */
    public void doubleWriteMetadataToV2(Service service, boolean ephemeral) {
        if (!startDoubleWrite) {
            return;
        }
        if (upgradeJudgement.isUseGrpcFeatures() || upgradeJudgement.isAll20XVersion()) {
            return;
        }
        String namespace = service.getNamespaceId();
        String serviceName = service.getName();
        doubleWriteDelayTaskEngine.addTask(ServiceChangeV1Task.getKey(namespace, serviceName, ephemeral),
                new ServiceChangeV1Task(namespace, serviceName, ephemeral, DoubleWriteContent.METADATA));
    }
}
