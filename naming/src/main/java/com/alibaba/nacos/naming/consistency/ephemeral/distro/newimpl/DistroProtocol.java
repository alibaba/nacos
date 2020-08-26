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
import com.alibaba.nacos.naming.consistency.ephemeral.distro.newimpl.entity.DistroKey;
import com.alibaba.nacos.naming.consistency.ephemeral.distro.newimpl.task.delay.DistroDelayTask;
import com.alibaba.nacos.naming.consistency.ephemeral.distro.newimpl.task.delay.DistroDelayTaskExecuteEngine;
import com.alibaba.nacos.naming.consistency.ephemeral.distro.newimpl.task.delay.DistroDelayTaskProcessor;
import com.alibaba.nacos.naming.consistency.ephemeral.distro.newimpl.task.execute.DistroExecuteWorkersManager;
import com.alibaba.nacos.naming.consistency.ephemeral.distro.newimpl.task.verify.DistroVerifyTask;
import com.alibaba.nacos.naming.misc.GlobalExecutor;
import com.alibaba.nacos.naming.misc.Loggers;

/**
 * Distro protocol.
 *
 * @author xiweng.yy
 */
public class DistroProtocol {
    
    private final ServerMemberManager memberManager;
    
    private final DistroComponentHolder distroComponentHolder;
    
    public DistroProtocol(ServerMemberManager memberManager, DistroDataStorage distroDataStorage,
            DistroTransportAgent distroTransportAgent) {
        this.memberManager = memberManager;
        this.distroComponentHolder = new DistroComponentHolder();
        this.distroComponentHolder.setDataStorage(distroDataStorage);
        this.distroComponentHolder.setTransportAgent(distroTransportAgent);
        this.distroComponentHolder.setDelayTaskExecuteEngine(new DistroDelayTaskExecuteEngine());
        DistroDelayTaskProcessor delayTaskProcessor = new DistroDelayTaskProcessor(distroComponentHolder);
        this.distroComponentHolder.getDelayTaskExecuteEngine().setDefaultTaskProcessor(delayTaskProcessor);
        this.distroComponentHolder.setExecuteWorkersManager(new DistroExecuteWorkersManager());
        startVerifyTask();
    }
    
    private void startVerifyTask() {
        GlobalExecutor.schedulePartitionDataTimedSync(new DistroVerifyTask(memberManager, distroComponentHolder));
    }
    
    /**
     * Start to sync.
     *
     * @param distroKey distro key of sync data
     * @param action    the action of data operation
     */
    public void sync(DistroKey distroKey, ApplyAction action) {
        for (Member each : memberManager.allMembersWithoutSelf()) {
            DistroKey distroKeyWithTarget = new DistroKey(distroKey.getResourceKey(), distroKey.getResourceType(), each.getAddress());
            DistroDelayTask distroDelayTask = new DistroDelayTask(distroKeyWithTarget, action, 1000L);
            distroComponentHolder.getDelayTaskExecuteEngine().addTask(distroKeyWithTarget, distroDelayTask);
            if (Loggers.DISTRO.isDebugEnabled()) {
                Loggers.DISTRO.debug("[DISTRO-SCHEDULE] {} to {}", distroKey, each.getAddress());
            }
        }
    }
}
