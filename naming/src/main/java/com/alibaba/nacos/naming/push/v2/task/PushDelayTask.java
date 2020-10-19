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

package com.alibaba.nacos.naming.push.v2.task;

import com.alibaba.nacos.common.task.AbstractDelayTask;
import com.alibaba.nacos.naming.core.v2.pojo.Service;

/**
 * Nacos naming push delay task.
 *
 * @author xiweng.yy
 */
public class PushDelayTask extends AbstractDelayTask {
    
    private final Service service;
    
    public PushDelayTask(Service service, long delay) {
        this.service = service;
        setTaskInterval(delay);
        setLastProcessTime(System.currentTimeMillis());
    }
    
    @Override
    public void merge(AbstractDelayTask task) {
        if (!(task instanceof PushDelayTask)) {
            return;
        }
        setLastProcessTime(Math.min(getLastProcessTime(), task.getLastProcessTime()));
    }
    
    public Service getService() {
        return service;
    }
}
