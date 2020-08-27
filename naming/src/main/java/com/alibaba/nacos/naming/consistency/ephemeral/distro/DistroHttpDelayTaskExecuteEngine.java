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

import com.alibaba.nacos.common.task.AbstractDelayTask;
import com.alibaba.nacos.common.task.NacosTaskProcessor;
import com.alibaba.nacos.naming.consistency.ApplyAction;
import com.alibaba.nacos.naming.consistency.ephemeral.distro.newimpl.entity.DistroKey;
import com.alibaba.nacos.naming.consistency.ephemeral.distro.newimpl.task.delay.DistroDelayTask;
import com.alibaba.nacos.naming.consistency.ephemeral.distro.newimpl.task.delay.DistroDelayTaskExecuteEngine;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Distro http delay task execute engine.
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
public class DistroHttpDelayTaskExecuteEngine extends DistroDelayTaskExecuteEngine {
    
    @Override
    protected void processTasks() {
        Collection<Object> keys = getAllTaskKeys();
        Map<String, DistroHttpCombinedKey> combinedKeyMap = new HashMap<>(4);
        for (Object each : keys) {
            AbstractDelayTask task = removeTask(each);
            if (null == task) {
                continue;
            }
            DistroKey distroKey = (DistroKey) each;
            if (!combinedKeyMap.containsKey(distroKey.getTargetServer())) {
                DistroHttpCombinedKey combinedKey = new DistroHttpCombinedKey(distroKey.getResourceType(),
                        distroKey.getTargetServer());
                combinedKeyMap.put(distroKey.getTargetServer(), combinedKey);
            }
            combinedKeyMap.get(distroKey.getTargetServer()).getActualResourceTypes().add(distroKey.getResourceKey());
        }
        for (DistroHttpCombinedKey each : combinedKeyMap.values()) {
            NacosTaskProcessor processor = getProcessor(each);
            if (null == processor) {
                getEngineLog().error("processor not found for task, so discarded. " + each);
                continue;
            }
            DistroDelayTask task = new DistroDelayTask(each, ApplyAction.CHANGE, 0);
            try {
                if (!processor.process(task)) {
                    retryFailedCombinedTask(each, task);
                }
            } catch (Throwable e) {
                getEngineLog().error("Nacos task execute error : " + e.toString(), e);
                retryFailedCombinedTask(each, task);
            }
        }
    }
    
    private void retryFailedCombinedTask(DistroHttpCombinedKey combinedKey, DistroDelayTask task) {
        for (String each : combinedKey.getActualResourceTypes()) {
            addTask(new DistroKey(each, combinedKey.getResourceType(), combinedKey.getTargetServer()), task);
        }
    }
}
