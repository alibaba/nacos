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

import com.alibaba.nacos.common.task.NacosTaskProcessor;
import com.alibaba.nacos.common.task.engine.NacosDelayTaskExecuteEngine;
import com.alibaba.nacos.core.distributed.distro.entity.DistroKey;
import com.alibaba.nacos.core.utils.Loggers;

/**
 * Distro delay task execute engine.
 *
 * @author xiweng.yy
 */
public class DistroDelayTaskExecuteEngine extends NacosDelayTaskExecuteEngine {
    
    public DistroDelayTaskExecuteEngine() {
        super(DistroDelayTaskExecuteEngine.class.getName(), Loggers.DISTRO);
    }
    
    @Override
    public void addProcessor(Object key, NacosTaskProcessor taskProcessor) {
        Object actualKey = getActualKey(key);
        super.addProcessor(actualKey, taskProcessor);
    }
    
    @Override
    public NacosTaskProcessor getProcessor(Object key) {
        Object actualKey = getActualKey(key);
        return super.getProcessor(actualKey);
    }
    
    private Object getActualKey(Object key) {
        return key instanceof DistroKey ? ((DistroKey) key).getResourceType() : key;
    }
}
