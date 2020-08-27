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

package com.alibaba.nacos.naming.consistency.ephemeral.distro.newimpl.task.execute;

import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.naming.consistency.ApplyAction;
import com.alibaba.nacos.naming.consistency.ephemeral.distro.newimpl.component.DistroComponentHolder;
import com.alibaba.nacos.naming.consistency.ephemeral.distro.newimpl.entity.DistroData;
import com.alibaba.nacos.naming.consistency.ephemeral.distro.newimpl.entity.DistroKey;
import com.alibaba.nacos.naming.consistency.ephemeral.distro.newimpl.event.DistroTaskRetryEvent;
import com.alibaba.nacos.naming.misc.Loggers;

/**
 * Distro sync change task.
 *
 * @author xiweng.yy
 */
public class DistroSyncChangeTask extends AbstractDistroExecuteTask {
    
    private final DistroComponentHolder distroComponentHolder;
    
    public DistroSyncChangeTask(DistroKey distroKey, DistroComponentHolder distroComponentHolder) {
        super(distroKey);
        this.distroComponentHolder = distroComponentHolder;
    }
    
    @Override
    public void run() {
        Loggers.DISTRO.info("[DISTRO-START] {}", toString());
        try {
            DistroData distroData = distroComponentHolder.getDataStorage().getDistroData(getDistroKey());
            distroData.setType(ApplyAction.CHANGE);
            boolean result = distroComponentHolder.getTransportAgent().syncData(distroData, getDistroKey().getTargetServer());
            if (!result) {
                retrySyncChange();
            }
            Loggers.DISTRO.info("[DISTRO-END] {} result: {}", toString(), result);
        } catch (Exception e) {
            Loggers.DISTRO.warn("[DISTRO] Sync data change failed.", e);
            retrySyncChange();
        }
    }
    
    private void retrySyncChange() {
        NotifyCenter.publishEvent(new DistroTaskRetryEvent(getDistroKey(), ApplyAction.CHANGE));
    }
    
    @Override
    public String toString() {
        return "DistroSyncChangeTask for " + getDistroKey().toString() + " to " + getDistroKey().getTargetServer();
    }
}
