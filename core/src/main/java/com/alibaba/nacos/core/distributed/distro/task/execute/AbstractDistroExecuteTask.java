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

import com.alibaba.nacos.common.task.AbstractExecuteTask;
import com.alibaba.nacos.consistency.DataOperation;
import com.alibaba.nacos.core.distributed.distro.component.DistroComponentHolder;
import com.alibaba.nacos.core.distributed.distro.component.DistroFailedTaskHandler;
import com.alibaba.nacos.core.distributed.distro.entity.DistroKey;
import com.alibaba.nacos.core.utils.Loggers;

/**
 * Abstract distro execute task.
 *
 * @author xiweng.yy
 */
public abstract class AbstractDistroExecuteTask extends AbstractExecuteTask {
    
    private final DistroKey distroKey;
    
    private final DistroComponentHolder distroComponentHolder;
    
    protected AbstractDistroExecuteTask(DistroKey distroKey, DistroComponentHolder distroComponentHolder) {
        this.distroKey = distroKey;
        this.distroComponentHolder = distroComponentHolder;
    }
    
    protected DistroKey getDistroKey() {
        return distroKey;
    }
    
    protected DistroComponentHolder getDistroComponentHolder() {
        return distroComponentHolder;
    }
    
    @Override
    public void run() {
        Loggers.DISTRO.info("[DISTRO-START] {}", toString());
        try {
            boolean result = doExecute();
            if (!result) {
                handleFailedTask();
            }
            Loggers.DISTRO.info("[DISTRO-END] {} result: {}", toString(), result);
        } catch (Exception e) {
            Loggers.DISTRO.warn("[DISTRO] Sync data change failed.", e);
            handleFailedTask();
        }
    }
    
    /**
     * Do execute for different sub class.
     *
     * @return result of execute
     */
    protected abstract boolean doExecute();
    
    /**
     * Handle failed task.
     */
    protected void handleFailedTask() {
        String type = getDistroKey().getResourceType();
        DistroFailedTaskHandler failedTaskHandler = distroComponentHolder.findFailedTaskHandler(type);
        if (null == failedTaskHandler) {
            Loggers.DISTRO.warn("[DISTRO] Can't find failed task for type {}, so discarded", type);
            return;
        }
        failedTaskHandler.retry(getDistroKey(), DataOperation.CHANGE);
    }
}
