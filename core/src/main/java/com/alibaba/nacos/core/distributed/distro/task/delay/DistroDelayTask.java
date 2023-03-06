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

package com.alibaba.nacos.core.distributed.distro.task.delay;

import com.alibaba.nacos.common.task.AbstractDelayTask;
import com.alibaba.nacos.consistency.DataOperation;
import com.alibaba.nacos.core.distributed.distro.entity.DistroKey;

/**
 * Distro delay task.
 *
 * @author xiweng.yy
 */
public class DistroDelayTask extends AbstractDelayTask {
    
    private final DistroKey distroKey;
    
    private DataOperation action;
    
    private long createTime;
    
    public DistroDelayTask(DistroKey distroKey, long delayTime) {
        this(distroKey, DataOperation.CHANGE, delayTime);
    }
    
    public DistroDelayTask(DistroKey distroKey, DataOperation action, long delayTime) {
        this.distroKey = distroKey;
        this.action = action;
        this.createTime = System.currentTimeMillis();
        setLastProcessTime(createTime);
        setTaskInterval(delayTime);
    }
    
    public DistroKey getDistroKey() {
        return distroKey;
    }
    
    public DataOperation getAction() {
        return action;
    }
    
    public long getCreateTime() {
        return createTime;
    }
    
    @Override
    public void merge(AbstractDelayTask task) {
        if (!(task instanceof DistroDelayTask)) {
            return;
        }
        DistroDelayTask oldTask = (DistroDelayTask) task;
        if (!action.equals(oldTask.getAction()) && createTime < oldTask.getCreateTime()) {
            action = oldTask.getAction();
            createTime = oldTask.getCreateTime();
        }
        setLastProcessTime(oldTask.getLastProcessTime());
    }
}
