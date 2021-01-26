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

package com.alibaba.nacos.naming.core.v2.upgrade.doublewrite.delay;

import com.alibaba.nacos.common.task.NacosTaskProcessor;
import com.alibaba.nacos.common.task.engine.NacosDelayTaskExecuteEngine;
import com.alibaba.nacos.naming.misc.Loggers;
import org.springframework.stereotype.Component;

/**
 * Double Write task engine.
 *
 * @author xiweng.yy
 */
@Component
public class DoubleWriteDelayTaskEngine extends NacosDelayTaskExecuteEngine {
    
    public DoubleWriteDelayTaskEngine() {
        super(DoubleWriteDelayTaskEngine.class.getSimpleName(), Loggers.SRV_LOG);
        addProcessor("v1", new ServiceChangeV1Task.ServiceChangeV1TaskProcessor());
        addProcessor("v2", new ServiceChangeV2Task.ServiceChangeV2TaskProcessor());
    }
    
    @Override
    public NacosTaskProcessor getProcessor(Object key) {
        String actualKey = key.toString().split(":")[0];
        return super.getProcessor(actualKey);
    }
}
