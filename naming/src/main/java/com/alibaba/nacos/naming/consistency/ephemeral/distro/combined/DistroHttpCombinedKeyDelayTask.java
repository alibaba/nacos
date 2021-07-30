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

import com.alibaba.nacos.common.task.AbstractDelayTask;
import com.alibaba.nacos.consistency.DataOperation;
import com.alibaba.nacos.core.distributed.distro.entity.DistroKey;
import com.alibaba.nacos.core.distributed.distro.task.delay.DistroDelayTask;
import com.alibaba.nacos.naming.consistency.KeyBuilder;

import java.util.HashSet;
import java.util.Set;

/**
 * Distro combined multi keys delay task for http.
 *
 * @author xiweng.yy
 */
public class DistroHttpCombinedKeyDelayTask extends DistroDelayTask {
    
    private final int batchSize;
    
    private final Set<String> actualResourceKeys = new HashSet<>();
    
    public DistroHttpCombinedKeyDelayTask(DistroKey distroKey, DataOperation action, long delayTime, int batchSize) {
        super(distroKey, action, delayTime);
        this.batchSize = batchSize;
    }
    
    public Set<String> getActualResourceKeys() {
        return actualResourceKeys;
    }
    
    @Override
    public void merge(AbstractDelayTask task) {
        actualResourceKeys.addAll(((DistroHttpCombinedKeyDelayTask) task).getActualResourceKeys());
        if (actualResourceKeys.size() >= batchSize) {
            DistroHttpCombinedKey.incrementSequence();
            setLastProcessTime(0);
        } else {
            setLastProcessTime(task.getLastProcessTime());
        }
    }
    
    @Override
    public DistroKey getDistroKey() {
        DistroKey taskKey = super.getDistroKey();
        DistroHttpCombinedKey result = new DistroHttpCombinedKey(KeyBuilder.INSTANCE_LIST_KEY_PREFIX,
                taskKey.getTargetServer());
        result.setResourceKey(taskKey.getResourceKey());
        result.getActualResourceTypes().addAll(actualResourceKeys);
        return result;
    }
}
