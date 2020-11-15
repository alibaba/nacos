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

package com.alibaba.nacos.core.distributed.distro.task.execute;

import com.alibaba.nacos.consistency.DataOperation;
import com.alibaba.nacos.core.distributed.distro.component.DistroComponentHolder;
import com.alibaba.nacos.core.distributed.distro.entity.DistroData;
import com.alibaba.nacos.core.distributed.distro.entity.DistroKey;
import com.alibaba.nacos.core.utils.Loggers;

/**
 * Distro sync change task.
 *
 * @author xiweng.yy
 */
public class DistroSyncChangeTask extends AbstractDistroExecuteTask {
    
    public DistroSyncChangeTask(DistroKey distroKey, DistroComponentHolder distroComponentHolder) {
        super(distroKey, distroComponentHolder);
    }
    
    @Override
    protected boolean doExecute() {
        String type = getDistroKey().getResourceType();
        DistroData distroData = getDistroComponentHolder().findDataStorage(type).getDistroData(getDistroKey());
        if (null != distroData) {
            distroData.setType(DataOperation.CHANGE);
            return getDistroComponentHolder().findTransportAgent(type)
                    .syncData(distroData, getDistroKey().getTargetServer());
        }
        Loggers.DISTRO.warn("[DISTRO-END] {} with null data to sync, skip", toString());
        return false;
    }
    
    @Override
    public String toString() {
        return "DistroSyncChangeTask for " + getDistroKey().toString();
    }
}
