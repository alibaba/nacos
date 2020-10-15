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

import com.alibaba.nacos.common.task.NacosTask;
import com.alibaba.nacos.common.task.NacosTaskProcessor;
import com.alibaba.nacos.core.distributed.distro.entity.DistroKey;
import com.alibaba.nacos.core.distributed.distro.task.DistroTaskEngineHolder;
import com.alibaba.nacos.core.distributed.distro.task.delay.DistroDelayTask;
import com.alibaba.nacos.naming.misc.GlobalConfig;

/**
 * Distro http task processor.
 *
 * <p>
 * The reason of create this delay task execute engine is that HTTP will establish and close tcp connection frequently,
 * so that cost more memory and cpu. What's more, there may be much 'TIME_WAIT' status tcp connection when service
 * change frequently.
 * </p>
 *
 * <p>
 * For naming usage, the only task is the ephemeral instances change.
 * </p>
 *
 * @author xiweng.yy
 */
public class DistroHttpDelayTaskProcessor implements NacosTaskProcessor {
    
    private final GlobalConfig globalConfig;
    
    private final DistroTaskEngineHolder distroTaskEngineHolder;
    
    public DistroHttpDelayTaskProcessor(GlobalConfig globalConfig, DistroTaskEngineHolder distroTaskEngineHolder) {
        this.globalConfig = globalConfig;
        this.distroTaskEngineHolder = distroTaskEngineHolder;
    }
    
    @Override
    public boolean process(NacosTask task) {
        DistroDelayTask distroDelayTask = (DistroDelayTask) task;
        DistroKey distroKey = distroDelayTask.getDistroKey();
        DistroHttpCombinedKeyExecuteTask executeTask = new DistroHttpCombinedKeyExecuteTask(globalConfig,
                distroTaskEngineHolder.getDelayTaskExecuteEngine(), distroKey, distroDelayTask.getAction());
        distroTaskEngineHolder.getExecuteWorkersManager().addTask(distroKey, executeTask);
        return true;
    }
}
