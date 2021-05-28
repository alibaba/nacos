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

package com.alibaba.nacos.naming.consistency.ephemeral.distro.combined;

import com.alibaba.nacos.common.task.AbstractExecuteTask;
import com.alibaba.nacos.consistency.DataOperation;
import com.alibaba.nacos.core.distributed.distro.DistroConfig;
import com.alibaba.nacos.core.distributed.distro.entity.DistroKey;
import com.alibaba.nacos.core.distributed.distro.task.delay.DistroDelayTaskExecuteEngine;
import com.alibaba.nacos.naming.misc.GlobalConfig;
import com.alibaba.nacos.naming.misc.Loggers;

/**
 * Distro http combined key execute task.
 *
 * <p>
 * In this task, it will generate combined key delay task and add back to delay engine.
 * </p>
 *
 * @author xiweng.yy
 */
public class DistroHttpCombinedKeyExecuteTask extends AbstractExecuteTask {
    
    private final GlobalConfig globalConfig;
    
    private final DistroDelayTaskExecuteEngine distroDelayTaskExecuteEngine;
    
    private final DistroKey singleDistroKey;
    
    private final DataOperation taskAction;
    
    public DistroHttpCombinedKeyExecuteTask(GlobalConfig globalConfig,
            DistroDelayTaskExecuteEngine distroDelayTaskExecuteEngine, DistroKey singleDistroKey,
            DataOperation taskAction) {
        this.globalConfig = globalConfig;
        this.distroDelayTaskExecuteEngine = distroDelayTaskExecuteEngine;
        this.singleDistroKey = singleDistroKey;
        this.taskAction = taskAction;
    }
    
    @Override
    public void run() {
        try {
            DistroKey newKey = new DistroKey(DistroHttpCombinedKey.getSequenceKey(),
                    DistroHttpCombinedKeyDelayTask.class.getSimpleName(), singleDistroKey.getTargetServer());
            DistroHttpCombinedKeyDelayTask combinedTask = new DistroHttpCombinedKeyDelayTask(newKey, taskAction,
                    DistroConfig.getInstance().getSyncDelayMillis(), globalConfig.getBatchSyncKeyCount());
            combinedTask.getActualResourceKeys().add(singleDistroKey.getResourceKey());
            distroDelayTaskExecuteEngine.addTask(newKey, combinedTask);
        } catch (Exception e) {
            Loggers.DISTRO.error("[DISTRO-FAILED] Combined key for http failed. ", e);
        }
    }
}
