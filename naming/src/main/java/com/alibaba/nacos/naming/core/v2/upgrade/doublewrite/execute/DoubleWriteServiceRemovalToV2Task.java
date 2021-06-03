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

import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.task.AbstractExecuteTask;
import com.alibaba.nacos.naming.core.InstanceOperatorClientImpl;
import com.alibaba.nacos.naming.core.v2.ServiceManager;
import com.alibaba.nacos.naming.core.v2.event.metadata.MetadataEvent;
import com.alibaba.nacos.naming.core.v2.index.ClientServiceIndexesManager;
import com.alibaba.nacos.naming.core.v2.index.ServiceStorage;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.core.v2.upgrade.doublewrite.delay.DoubleWriteAction;
import com.alibaba.nacos.naming.core.v2.upgrade.doublewrite.delay.DoubleWriteContent;
import com.alibaba.nacos.naming.core.v2.upgrade.doublewrite.delay.DoubleWriteDelayTaskEngine;
import com.alibaba.nacos.naming.core.v2.upgrade.doublewrite.delay.ServiceChangeV1Task;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.sys.utils.ApplicationUtils;

import java.util.concurrent.TimeUnit;

/**
 * Double write task for removal of service from v1 to v2.
 * 
 * @author gengtuo.ygt
 * on 2021/5/13
 */
public class DoubleWriteServiceRemovalToV2Task extends AbstractExecuteTask {

    private static final int MAX_WAIT_TIMES = 5;

    private final Service service;

    public DoubleWriteServiceRemovalToV2Task(Service service) {
        this.service = service;
    }

    @Override
    public void run() {
        try {
            InstanceOperatorClientImpl instanceOperator = ApplicationUtils.getBean(InstanceOperatorClientImpl.class);
            ClientServiceIndexesManager clientServiceIndexesManager = ApplicationUtils.getBean(ClientServiceIndexesManager.class);
            ServiceStorage serviceStorage = ApplicationUtils.getBean(ServiceStorage.class);
            ServiceInfo serviceInfo = serviceStorage.getPushData(service);
            for (Instance instance : serviceInfo.getHosts()) {
                instanceOperator.removeInstance(service.getNamespace(), service.getName(), instance);
            }
            int count = 0;
            while (!clientServiceIndexesManager.getAllClientsRegisteredService(service).isEmpty()
                    && count < MAX_WAIT_TIMES) {
                TimeUnit.MILLISECONDS.sleep(100);
                count += 1;
            }
            clientServiceIndexesManager.removePublisherIndexesByEmptyService(service);
            ServiceManager.getInstance().removeSingleton(service);
            serviceStorage.removeData(service);
            NotifyCenter.publishEvent(new MetadataEvent.ServiceMetadataEvent(service, true));
        } catch (Exception e) {
            if (Loggers.SRV_LOG.isDebugEnabled()) {
                Loggers.SRV_LOG.debug("Double write removal of {} from 1 to 2 failed", service, e);
            }
            ServiceChangeV1Task retryTask = new ServiceChangeV1Task(service.getNamespace(),
                    service.getGroupedServiceName(), service.isEphemeral(),
                    DoubleWriteContent.BOTH, DoubleWriteAction.REMOVE);
            retryTask.setTaskInterval(INTERVAL);
            String taskKey = ServiceChangeV1Task
                    .getKey(service.getNamespace(), service.getGroupedServiceName(), service.isEphemeral());
            ApplicationUtils.getBean(DoubleWriteDelayTaskEngine.class).addTask(taskKey, retryTask);
        }
    }

}
