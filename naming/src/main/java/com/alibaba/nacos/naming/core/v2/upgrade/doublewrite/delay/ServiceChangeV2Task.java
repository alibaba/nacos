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

import com.alibaba.nacos.common.task.AbstractDelayTask;
import com.alibaba.nacos.common.task.NacosTask;
import com.alibaba.nacos.common.task.NacosTaskProcessor;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.core.v2.upgrade.doublewrite.execute.DoubleWriteInstanceChangeToV1Task;
import com.alibaba.nacos.naming.core.v2.upgrade.doublewrite.execute.DoubleWriteMetadataChangeToV1Task;
import com.alibaba.nacos.naming.core.v2.upgrade.doublewrite.execute.DoubleWriteServiceRemovalToV1Task;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.NamingExecuteTaskDispatcher;

/**
 * Double write delay task for service from v2 to v1 during downgrading.
 *
 * @author xiweng.yy
 */
public class ServiceChangeV2Task extends AbstractDelayTask {
    
    private final Service changedService;
    
    private DoubleWriteContent content;

    private DoubleWriteAction action;
    
    public ServiceChangeV2Task(Service service, DoubleWriteContent content) {
        this(service, content, DoubleWriteAction.UPDATE);
    }
    
    public ServiceChangeV2Task(Service service, DoubleWriteContent content, DoubleWriteAction action) {
        this.changedService = service;
        this.content = action == DoubleWriteAction.REMOVE ? DoubleWriteContent.BOTH : content;
        this.action = action;
        setTaskInterval(INTERVAL);
        setLastProcessTime(System.currentTimeMillis());
    }
    
    public Service getChangedService() {
        return changedService;
    }
    
    public DoubleWriteContent getContent() {
        return content;
    }
    
    public DoubleWriteAction getAction() {
        return action;
    }

    @Override
    public void merge(AbstractDelayTask task) {
        if (!(task instanceof ServiceChangeV2Task)) {
            return;
        }
        ServiceChangeV2Task oldTask = (ServiceChangeV2Task) task;
        if (!action.equals(oldTask.getAction())) {
            action = DoubleWriteAction.REMOVE;
            content = DoubleWriteContent.BOTH;
            return;
        }
        if (!content.equals(oldTask.getContent())) {
            content = DoubleWriteContent.BOTH;
        }
    }
    
    public static String getKey(Service service) {
        return "v2:" + service.getNamespace() + "_" + service.getGroupedServiceName() + "_" + service.isEphemeral();
    }
    
    public static class ServiceChangeV2TaskProcessor implements NacosTaskProcessor {
        
        @Override
        public boolean process(NacosTask task) {
            ServiceChangeV2Task serviceTask = (ServiceChangeV2Task) task;
            Service changedService = serviceTask.getChangedService();
            if (serviceTask.getAction() == DoubleWriteAction.REMOVE) {
                Loggers.SRV_LOG.info("double write removal of service {}", changedService);
                dispatchRemoveServiceTask(changedService);
                return true;
            }
            Loggers.SRV_LOG.info("double write for service {}, content {}", changedService, serviceTask.getContent());
            switch (serviceTask.getContent()) {
                case INSTANCE:
                    dispatchInstanceChangeTask(changedService);
                    break;
                case METADATA:
                    dispatchMetadataChangeTask(changedService);
                    break;
                default:
                    dispatchAllTask(changedService);
            }
            return true;
        }
        
        private void dispatchRemoveServiceTask(Service changedService) {
            DoubleWriteServiceRemovalToV1Task serviceRemovalTask = new DoubleWriteServiceRemovalToV1Task(changedService);
            NamingExecuteTaskDispatcher.getInstance()
                    .dispatchAndExecuteTask(changedService.getGroupedServiceName(), serviceRemovalTask);
        }

        private void dispatchInstanceChangeTask(Service changedService) {
            DoubleWriteInstanceChangeToV1Task instanceTask = new DoubleWriteInstanceChangeToV1Task(changedService);
            NamingExecuteTaskDispatcher.getInstance()
                    .dispatchAndExecuteTask(changedService.getGroupedServiceName(), instanceTask);
        }
        
        private void dispatchMetadataChangeTask(Service changedService) {
            DoubleWriteMetadataChangeToV1Task metadataTask = new DoubleWriteMetadataChangeToV1Task(changedService);
            NamingExecuteTaskDispatcher.getInstance()
                    .dispatchAndExecuteTask(changedService.getGroupedServiceName(), metadataTask);
        }
        
        private void dispatchAllTask(Service changedService) {
            dispatchMetadataChangeTask(changedService);
            dispatchInstanceChangeTask(changedService);
        }
    }
}
