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
import com.alibaba.nacos.naming.consistency.ConsistencyService;
import com.alibaba.nacos.naming.consistency.KeyBuilder;
import com.alibaba.nacos.naming.core.Instances;
import com.alibaba.nacos.naming.core.ServiceManager;
import com.alibaba.nacos.naming.core.v2.index.ServiceStorage;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.core.v2.upgrade.doublewrite.delay.DoubleWriteContent;
import com.alibaba.nacos.naming.core.v2.upgrade.doublewrite.delay.DoubleWriteDelayTaskEngine;
import com.alibaba.nacos.naming.core.v2.upgrade.doublewrite.delay.ServiceChangeV2Task;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.sys.utils.ApplicationUtils;

/**
 * Double write from 2.x to 1 task.
 *
 * @author xiweng.yy
 */
public class DoubleWriteInstanceChangeToV1Task extends AbstractExecuteTask {
    
    private final Service service;
    
    private static final String NAME = "consistencyDelegate";
    
    public DoubleWriteInstanceChangeToV1Task(Service service) {
        this.service = service;
    }
    
    @Override
    public void run() {
        try {
            ServiceManager serviceManager = ApplicationUtils.getBean(ServiceManager.class);
            com.alibaba.nacos.naming.core.Service serviceV1 = serviceManager
                    .getService(service.getNamespace(), service.getGroupedServiceName());
            if (null == serviceV1) {
                serviceManager.createEmptyService(service.getNamespace(), service.getGroupedServiceName(),
                        service.isEphemeral());
            }
            Instances newInstances = getNewInstances();
            String key = KeyBuilder.buildInstanceListKey(service.getNamespace(), service.getGroupedServiceName(),
                    service.isEphemeral());
            ConsistencyService consistencyService = ApplicationUtils
                    .getBean(NAME, ConsistencyService.class);
            consistencyService.put(key, newInstances);
        } catch (Exception e) {
            if (Loggers.SRV_LOG.isDebugEnabled()) {
                Loggers.SRV_LOG.debug("Double write task for {} instance from 2 to 1 failed", service, e);
            }
            ServiceChangeV2Task retryTask = new ServiceChangeV2Task(service, DoubleWriteContent.INSTANCE);
            retryTask.setTaskInterval(INTERVAL);
            String taskKey = ServiceChangeV2Task.getKey(service);
            ApplicationUtils.getBean(DoubleWriteDelayTaskEngine.class).addTask(taskKey, retryTask);
        }
    }
    
    private Instances getNewInstances() {
        Instances result = new Instances();
        ServiceStorage serviceStorage = ApplicationUtils.getBean(ServiceStorage.class);
        InstanceUpgradeHelper instanceUpgradeHelper = ApplicationUtils.getBean(InstanceUpgradeHelper.class);
        long currentTimeStamp = System.currentTimeMillis();
        for (Instance each : serviceStorage.getData(service).getHosts()) {
            com.alibaba.nacos.naming.core.Instance instance = instanceUpgradeHelper.toV1(each);
            instance.setLastBeat(currentTimeStamp);
            result.getInstanceList().add(instance);
        }
        return result;
    }
}
