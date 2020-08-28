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

package com.alibaba.nacos.naming.consistency.ephemeral.distro;

import com.alibaba.nacos.naming.consistency.ApplyAction;
import com.alibaba.nacos.naming.consistency.KeyBuilder;
import com.alibaba.nacos.naming.consistency.ephemeral.distro.newimpl.component.DistroComponentHolder;
import com.alibaba.nacos.naming.consistency.ephemeral.distro.newimpl.component.DistroFailedTaskHandler;
import com.alibaba.nacos.naming.consistency.ephemeral.distro.newimpl.entity.DistroKey;
import com.alibaba.nacos.naming.consistency.ephemeral.distro.newimpl.task.delay.DistroDelayTask;
import com.alibaba.nacos.naming.misc.GlobalConfig;

/**
 * Distro combined key task failed handler.
 *
 * @author xiweng.yy
 */
public class DistroHttpCombinedKeyTaskFailedHandler implements DistroFailedTaskHandler {
    
    private final GlobalConfig globalConfig;
    
    private final DistroComponentHolder distroComponentHolder;
    
    public DistroHttpCombinedKeyTaskFailedHandler(GlobalConfig globalConfig,
            DistroComponentHolder distroComponentHolder) {
        this.globalConfig = globalConfig;
        this.distroComponentHolder = distroComponentHolder;
    }
    
    @Override
    public void retry(DistroKey distroKey, ApplyAction action) {
        DistroHttpCombinedKey combinedKey = (DistroHttpCombinedKey) distroKey;
        for (String each : combinedKey.getActualResourceTypes()) {
            DistroKey newKey = new DistroKey(each, KeyBuilder.INSTANCE_LIST_KEY_PREFIX, distroKey.getTargetServer());
            DistroDelayTask newTask = new DistroDelayTask(newKey, action, globalConfig.getSyncRetryDelay());
            distroComponentHolder.getDelayTaskExecuteEngine().addTask(newKey, newTask);
        }
    }
}
