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
import com.alibaba.nacos.naming.core.v2.event.publisher.NamingEventPublisherFactory;
import com.alibaba.nacos.naming.core.v2.event.service.ServiceEvent;
import com.alibaba.nacos.naming.core.v2.upgrade.UpgradeJudgement;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

import static com.alibaba.nacos.naming.core.v2.upgrade.doublewrite.delay.DoubleWriteAction.REMOVE;
import static com.alibaba.nacos.naming.core.v2.upgrade.doublewrite.delay.DoubleWriteAction.UPDATE;
import static com.alibaba.nacos.naming.core.v2.upgrade.doublewrite.delay.DoubleWriteContent.METADATA;

/**
 * Event listener for double write.
 *
 * @author xiweng.yy
 */
@Component
public class DoubleWriteEventListener extends Subscriber<ServiceEvent.ServiceChangedEvent> {
    
    private final UpgradeJudgement upgradeJudgement;
    
    private final DoubleWriteDelayTaskEngine doubleWriteDelayTaskEngine;
    
    private volatile boolean stopDoubleWrite;
    
    public DoubleWriteEventListener(UpgradeJudgement upgradeJudgement,
            DoubleWriteDelayTaskEngine doubleWriteDelayTaskEngine) {
        this.upgradeJudgement = upgradeJudgement;
        this.doubleWriteDelayTaskEngine = doubleWriteDelayTaskEngine;
        NotifyCenter.registerSubscriber(this, NamingEventPublisherFactory.getInstance());
        stopDoubleWrite = EnvUtil.getStandaloneMode();
        if (!stopDoubleWrite) {
            Thread doubleWriteEnabledChecker = new DoubleWriteEnabledChecker();
            doubleWriteEnabledChecker.start();
        }
    }
    
    @Override
    public void onEvent(ServiceEvent.ServiceChangedEvent event) {
        if (stopDoubleWrite) {
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
     * @param remove  is removing service for v2
     */
    public void doubleWriteMetadataToV1(com.alibaba.nacos.naming.core.v2.pojo.Service service, boolean remove) {
        if (stopDoubleWrite) {
            return;
        }
        if (!upgradeJudgement.isUseGrpcFeatures()) {
            return;
        }
        doubleWriteDelayTaskEngine.addTask(ServiceChangeV2Task.getKey(service),
                new ServiceChangeV2Task(service, METADATA, remove ? REMOVE : UPDATE));
    }
    
    /**
     * Double write service from v1 to v2.
     *
     * @param service   service for v1
     * @param ephemeral ephemeral of service
     */
    public void doubleWriteToV2(Service service, boolean ephemeral) {
        if (stopDoubleWrite) {
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
     * @param remove    is removing service for v1
     */
    public void doubleWriteMetadataToV2(Service service, boolean ephemeral, boolean remove) {
        if (stopDoubleWrite) {
            return;
        }
        if (upgradeJudgement.isUseGrpcFeatures() || upgradeJudgement.isAll20XVersion()) {
            return;
        }
        String namespace = service.getNamespaceId();
        String serviceName = service.getName();
        doubleWriteDelayTaskEngine.addTask(ServiceChangeV1Task.getKey(namespace, serviceName, ephemeral),
                new ServiceChangeV1Task(namespace, serviceName, ephemeral, METADATA, remove ? REMOVE : UPDATE));
    }
    
    private class DoubleWriteEnabledChecker extends Thread {
        
        private volatile boolean stillCheck = true;
        
        @Override
        public void run() {
            Loggers.SRV_LOG.info("Check whether close double write");
            while (stillCheck) {
                try {
                    TimeUnit.SECONDS.sleep(5);
                    stopDoubleWrite = !ApplicationUtils.getBean(SwitchDomain.class).isDoubleWriteEnabled();
                    if (stopDoubleWrite) {
                        upgradeJudgement.stopAll();
                        stillCheck = false;
                    }
                } catch (Exception e) {
                    Loggers.SRV_LOG.error("Close double write failed ", e);
                }
            }
            Loggers.SRV_LOG.info("Check double write closed");
        }
    }
}
