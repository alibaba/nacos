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

package com.alibaba.nacos.naming.core.v2.upgrade.doublewrite.execute;

import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.common.task.AbstractExecuteTask;
import com.alibaba.nacos.naming.core.InstanceOperatorClientImpl;
import com.alibaba.nacos.naming.core.v2.upgrade.doublewrite.delay.DoubleWriteContent;
import com.alibaba.nacos.naming.core.v2.upgrade.doublewrite.delay.DoubleWriteDelayTaskEngine;
import com.alibaba.nacos.naming.core.v2.upgrade.doublewrite.delay.ServiceChangeV1Task;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.sys.utils.ApplicationUtils;

/**
 * Double write from 1.x to 2 task.
 *
 * @author xiweng.yy
 */
public class DoubleWriteInstanceChangeToV2Task extends AbstractExecuteTask {
    
    private final String namespace;
    
    private final String serviceName;
    
    private final Instance instance;
    
    private final boolean register;
    
    public DoubleWriteInstanceChangeToV2Task(String namespace, String serviceName, Instance instance,
            boolean register) {
        this.register = register;
        this.namespace = namespace;
        this.serviceName = serviceName;
        this.instance = instance;
    }
    
    @Override
    public void run() {
        try {
            InstanceOperatorClientImpl instanceOperator = ApplicationUtils.getBean(InstanceOperatorClientImpl.class);
            if (register) {
                instanceOperator.registerInstance(namespace, serviceName, instance);
            } else {
                instanceOperator.removeInstance(namespace, serviceName, instance);
            }
        } catch (Exception e) {
            if (Loggers.SRV_LOG.isDebugEnabled()) {
                Loggers.SRV_LOG
                        .debug("Double write task for {}#{} instance from 1 to 2 failed", namespace, serviceName, e);
            }
            ServiceChangeV1Task retryTask = new ServiceChangeV1Task(namespace, serviceName, instance.isEphemeral(),
                    DoubleWriteContent.INSTANCE);
            retryTask.setTaskInterval(INTERVAL);
            String taskKey = ServiceChangeV1Task.getKey(namespace, serviceName, instance.isEphemeral());
            ApplicationUtils.getBean(DoubleWriteDelayTaskEngine.class).addTask(taskKey, retryTask);
        }
    }
}
