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

package com.alibaba.nacos.naming.consistency.ephemeral.distro.newimpl;

import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.naming.consistency.ApplyAction;
import com.alibaba.nacos.naming.consistency.ephemeral.distro.newimpl.component.DistroComponentHolder;
import com.alibaba.nacos.naming.consistency.ephemeral.distro.newimpl.component.DistroDataProcessor;
import com.alibaba.nacos.naming.consistency.ephemeral.distro.newimpl.entity.DistroData;
import com.alibaba.nacos.naming.consistency.ephemeral.distro.newimpl.entity.DistroKey;
import com.alibaba.nacos.naming.consistency.ephemeral.distro.newimpl.task.DistroTaskEngineHolder;
import com.alibaba.nacos.naming.consistency.ephemeral.distro.newimpl.task.delay.DistroDelayTask;
import com.alibaba.nacos.naming.consistency.ephemeral.distro.newimpl.task.verify.DistroVerifyTask;
import com.alibaba.nacos.naming.misc.GlobalExecutor;
import com.alibaba.nacos.naming.misc.Loggers;
import org.springframework.stereotype.Component;

/**
 * Distro protocol.
 *
 * @author xiweng.yy
 */
@Component
public class DistroProtocol {
    
    private final ServerMemberManager memberManager;
    
    private final DistroComponentHolder distroComponentHolder;
    
    private final DistroTaskEngineHolder distroTaskEngineHolder;
    
    public DistroProtocol(ServerMemberManager memberManager, DistroComponentHolder distroComponentHolder,
            DistroTaskEngineHolder distroTaskEngineHolder) {
        this.memberManager = memberManager;
        this.distroComponentHolder = distroComponentHolder;
        this.distroTaskEngineHolder = distroTaskEngineHolder;
        startVerifyTask();
    }
    
    private void startVerifyTask() {
        GlobalExecutor.schedulePartitionDataTimedSync(new DistroVerifyTask(memberManager, distroComponentHolder));
    }
    
    /**
     * Start to sync immediately.
     *
     * @param distroKey distro key of sync data
     * @param action    the action of data operation
     */
    public void sync(DistroKey distroKey, ApplyAction action) {
        sync(distroKey, action, 0L);
    }
    
    /**
     * Start to sync.
     *
     * @param distroKey distro key of sync data
     * @param action    the action of data operation
     */
    public void sync(DistroKey distroKey, ApplyAction action, long delay) {
        for (Member each : memberManager.allMembersWithoutSelf()) {
            DistroKey distroKeyWithTarget = new DistroKey(distroKey.getResourceKey(), distroKey.getResourceType(), each.getAddress());
            DistroDelayTask distroDelayTask = new DistroDelayTask(distroKeyWithTarget, action, delay);
            distroTaskEngineHolder.getDelayTaskExecuteEngine().addTask(distroKeyWithTarget, distroDelayTask);
            if (Loggers.DISTRO.isDebugEnabled()) {
                Loggers.DISTRO.debug("[DISTRO-SCHEDULE] {} to {}", distroKey, each.getAddress());
            }
        }
    }
    
    /**
     * Receive synced distro data, find processor to process.
     *
     * @param distroData Received data
     */
    public void onReceive(DistroData distroData) {
        String resourceType = distroData.getDistroKey().getResourceType();
        DistroDataProcessor dataProcessor = distroComponentHolder.findDataProcessor(resourceType);
        if (null == dataProcessor) {
            Loggers.DISTRO.warn("[DISTRO] Can't find data process for receive data {}", resourceType);
            return;
        }
        dataProcessor.processData(distroData);
    }
    
    /**
     * Receive verify data, find processor to process.
     *
     * @param distroData verify data
     */
    public void onVerify(DistroData distroData) {
        String resourceType = distroData.getDistroKey().getResourceType();
        DistroDataProcessor dataProcessor = distroComponentHolder.findDataProcessor(resourceType);
        if (null == dataProcessor) {
            Loggers.DISTRO.warn("[DISTRO] Can't find verify data process for receive data {}", resourceType);
            return;
        }
        dataProcessor.processVerifyData(distroData);
    }
}
