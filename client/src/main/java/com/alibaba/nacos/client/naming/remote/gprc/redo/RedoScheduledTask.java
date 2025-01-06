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

package com.alibaba.nacos.client.naming.remote.gprc.redo;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.naming.remote.gprc.NamingGrpcClientProxy;
import com.alibaba.nacos.client.naming.remote.gprc.redo.data.BatchInstanceRedoData;
import com.alibaba.nacos.client.naming.remote.gprc.redo.data.InstanceRedoData;
import com.alibaba.nacos.client.naming.remote.gprc.redo.data.RedoData;
import com.alibaba.nacos.client.naming.remote.gprc.redo.data.SubscriberRedoData;
import com.alibaba.nacos.client.utils.LogUtils;
import com.alibaba.nacos.common.task.AbstractExecuteTask;

/**
 * Redo task.
 *
 * @author xiweng.yy
 */
public class RedoScheduledTask extends AbstractExecuteTask {
    
    private final NamingGrpcClientProxy clientProxy;
    
    private final NamingGrpcRedoService redoService;
    
    public RedoScheduledTask(NamingGrpcClientProxy clientProxy, NamingGrpcRedoService redoService) {
        this.clientProxy = clientProxy;
        this.redoService = redoService;
    }
    
    @Override
    public void run() {
        if (!redoService.isConnected()) {
            LogUtils.NAMING_LOGGER.warn("Grpc Connection is disconnect, skip current redo task");
            return;
        }
        try {
            redoForInstances();
            redoForSubscribes();
        } catch (Exception e) {
            LogUtils.NAMING_LOGGER.warn("Redo task run with unexpected exception: ", e);
        }
    }
    
    private void redoForInstances() {
        for (InstanceRedoData each : redoService.findInstanceRedoData()) {
            try {
                redoForInstance(each);
            } catch (NacosException e) {
                LogUtils.NAMING_LOGGER.error("Redo instance operation {} for {}@@{} failed. ", each.getRedoType(),
                        each.getGroupName(), each.getServiceName(), e);
            }
        }
    }
    
    private void redoForInstance(InstanceRedoData redoData) throws NacosException {
        RedoData.RedoType redoType = redoData.getRedoType();
        String serviceName = redoData.getServiceName();
        String groupName = redoData.getGroupName();
        LogUtils.NAMING_LOGGER.info("Redo instance operation {} for {}@@{}", redoType, groupName, serviceName);
        switch (redoType) {
            case REGISTER:
                if (isClientDisabled()) {
                    return;
                }
                processRegisterRedoType(redoData, serviceName, groupName);
                break;
            case UNREGISTER:
                if (isClientDisabled()) {
                    return;
                }
                clientProxy.doDeregisterService(serviceName, groupName, redoData.get());
                break;
            case REMOVE:
                redoService.removeInstanceForRedo(serviceName, groupName);
                break;
            default:
        }
        
    }
    
    private void processRegisterRedoType(InstanceRedoData redoData, String serviceName, String groupName) throws NacosException {
        if (redoData instanceof BatchInstanceRedoData) {
            // Execute Batch Register
            BatchInstanceRedoData batchInstanceRedoData = (BatchInstanceRedoData) redoData;
            clientProxy.doBatchRegisterService(serviceName, groupName, batchInstanceRedoData.getInstances());
            return;
        }
        clientProxy.doRegisterService(serviceName, groupName, redoData.get());
    }
    
    private void redoForSubscribes() {
        for (SubscriberRedoData each : redoService.findSubscriberRedoData()) {
            try {
                redoForSubscribe(each);
            } catch (NacosException e) {
                LogUtils.NAMING_LOGGER.error("Redo subscriber operation {} for {}@@{}#{} failed. ", each.getRedoType(),
                        each.getGroupName(), each.getServiceName(), each.get(), e);
            }
        }
    }
    
    private void redoForSubscribe(SubscriberRedoData redoData) throws NacosException {
        RedoData.RedoType redoType = redoData.getRedoType();
        String serviceName = redoData.getServiceName();
        String groupName = redoData.getGroupName();
        String cluster = redoData.get();
        LogUtils.NAMING_LOGGER.info("Redo subscriber operation {} for {}@@{}#{}", redoType, groupName, serviceName, cluster);
        switch (redoData.getRedoType()) {
            case REGISTER:
                if (isClientDisabled()) {
                    return;
                }
                clientProxy.doSubscribe(serviceName, groupName, cluster);
                break;
            case UNREGISTER:
                if (isClientDisabled()) {
                    return;
                }
                clientProxy.doUnsubscribe(serviceName, groupName, cluster);
                break;
            case REMOVE:
                redoService.removeSubscriberForRedo(redoData.getServiceName(), redoData.getGroupName(), redoData.get());
                break;
            default:
        }
    }
    
    private boolean isClientDisabled() {
        return !clientProxy.isEnable();
    }
}
