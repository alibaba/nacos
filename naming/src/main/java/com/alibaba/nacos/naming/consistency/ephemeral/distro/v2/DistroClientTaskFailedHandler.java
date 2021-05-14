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

package com.alibaba.nacos.naming.consistency.ephemeral.distro.v2;

import com.alibaba.nacos.consistency.DataOperation;
import com.alibaba.nacos.core.distributed.distro.DistroConfig;
import com.alibaba.nacos.core.distributed.distro.component.DistroFailedTaskHandler;
import com.alibaba.nacos.core.distributed.distro.entity.DistroKey;
import com.alibaba.nacos.core.distributed.distro.task.DistroTaskEngineHolder;
import com.alibaba.nacos.core.distributed.distro.task.delay.DistroDelayTask;

/**
 * Distro client task failed handler.
 *
 * @author xiweng.yy
 */
public class DistroClientTaskFailedHandler implements DistroFailedTaskHandler {
    
    private final DistroTaskEngineHolder distroTaskEngineHolder;
    
    public DistroClientTaskFailedHandler(DistroTaskEngineHolder distroTaskEngineHolder) {
        this.distroTaskEngineHolder = distroTaskEngineHolder;
    }
    
    @Override
    public void retry(DistroKey distroKey, DataOperation action) {
        DistroDelayTask retryTask = new DistroDelayTask(distroKey, action,
                DistroConfig.getInstance().getSyncRetryDelayMillis());
        distroTaskEngineHolder.getDelayTaskExecuteEngine().addTask(distroKey, retryTask);
    }
}
