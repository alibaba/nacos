/*
 * Copyright (c) 1999-2021 Alibaba Group Holding Ltd.
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

import com.alibaba.nacos.common.task.AbstractExecuteTask;
import com.alibaba.nacos.naming.consistency.ConsistencyService;
import com.alibaba.nacos.naming.consistency.KeyBuilder;
import com.alibaba.nacos.naming.core.ServiceManager;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.core.v2.upgrade.doublewrite.delay.DoubleWriteAction;
import com.alibaba.nacos.naming.core.v2.upgrade.doublewrite.delay.DoubleWriteContent;
import com.alibaba.nacos.naming.core.v2.upgrade.doublewrite.delay.DoubleWriteDelayTaskEngine;
import com.alibaba.nacos.naming.core.v2.upgrade.doublewrite.delay.ServiceChangeV2Task;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.sys.utils.ApplicationUtils;

/**
 * Double write task for removal of service from v2 to v1.
 * 
 * @author gengtuo.ygt
 * on 2021/5/13
 */
public class DoubleWriteServiceRemovalToV1Task extends AbstractExecuteTask {

    private final Service service;

    public DoubleWriteServiceRemovalToV1Task(Service service) {
        this.service = service;
    }

    @Override
    public void run() {
        try {
            ServiceManager serviceManager = ApplicationUtils.getBean(ServiceManager.class);
            com.alibaba.nacos.naming.core.Service serviceV1 = serviceManager
                    .getService(service.getNamespace(), service.getGroupedServiceName());
            if (serviceV1 == null) {
                if (Loggers.SRV_LOG.isDebugEnabled()) {
                    Loggers.SRV_LOG.debug("Double write task is removing a non-exist service: {}", service);
                }
                return;
            }
            ConsistencyService consistencyService = ApplicationUtils
                    .getBean("consistencyDelegate", ConsistencyService.class);
            // remove instances
            String instanceListKey = KeyBuilder.buildInstanceListKey(service.getNamespace(),
                    service.getGroupedServiceName(), service.isEphemeral());
            consistencyService.remove(instanceListKey);
            // remove metadata
            serviceManager.easyRemoveService(service.getNamespace(), service.getGroupedServiceName());
        } catch (Exception e) {
            if (Loggers.SRV_LOG.isDebugEnabled()) {
                Loggers.SRV_LOG.debug("Double write task for removal of {} from 2 to 1 failed", service, e);
            }
            ServiceChangeV2Task retryTask = new ServiceChangeV2Task(service,
                    DoubleWriteContent.BOTH, DoubleWriteAction.REMOVE);
            retryTask.setTaskInterval(INTERVAL);
            String taskKey = ServiceChangeV2Task.getKey(service);
            ApplicationUtils.getBean(DoubleWriteDelayTaskEngine.class).addTask(taskKey, retryTask);
        }
    }

}
