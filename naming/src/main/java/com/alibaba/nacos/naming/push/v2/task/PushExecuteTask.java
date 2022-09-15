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

import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.task.AbstractExecuteTask;
import com.alibaba.nacos.common.trace.event.naming.PushServiceTraceEvent;
import com.alibaba.nacos.naming.core.v2.client.Client;
import com.alibaba.nacos.naming.core.v2.client.manager.ClientManager;
import com.alibaba.nacos.naming.core.v2.metadata.ServiceMetadata;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.pojo.Subscriber;
import com.alibaba.nacos.naming.push.v2.NoRequiredRetryException;
import com.alibaba.nacos.naming.push.v2.PushConfig;
import com.alibaba.nacos.naming.push.v2.PushDataWrapper;
import com.alibaba.nacos.naming.push.v2.hook.PushResult;
import com.alibaba.nacos.naming.push.v2.hook.PushResultHookHolder;

import java.util.Collection;

/**
 * Nacos naming push execute task.
 *
 * @author xiweng.yy
 */
public class PushExecuteTask extends AbstractExecuteTask {
    
    private final Service service;
    
    private final PushDelayTaskExecuteEngine delayTaskEngine;
    
    private final PushDelayTask delayTask;
    
    public PushExecuteTask(Service service, PushDelayTaskExecuteEngine delayTaskEngine, PushDelayTask delayTask) {
        this.service = service;
        this.delayTaskEngine = delayTaskEngine;
        this.delayTask = delayTask;
    }
    
    @Override
    public void run() {
        try {
            PushDataWrapper wrapper = generatePushData();
            ClientManager clientManager = delayTaskEngine.getClientManager();
            for (String each : getTargetClientIds()) {
                Client client = clientManager.getClient(each);
                if (null == client) {
                    // means this client has disconnect
                    continue;
                }
                Subscriber subscriber = clientManager.getClient(each).getSubscriber(service);
                delayTaskEngine.getPushExecutor().doPushWithCallback(each, subscriber, wrapper,
                        new ServicePushCallback(each, subscriber, wrapper.getOriginalData(), delayTask.isPushToAll()));
            }
        } catch (Exception e) {
            Loggers.PUSH.error("Push task for service" + service.getGroupedServiceName() + " execute failed ", e);
            delayTaskEngine.addTask(service, new PushDelayTask(service, 1000L));
        }
    }
    
    private PushDataWrapper generatePushData() {
        ServiceInfo serviceInfo = delayTaskEngine.getServiceStorage().getPushData(service);
        ServiceMetadata serviceMetadata = delayTaskEngine.getMetadataManager().getServiceMetadata(service).orElse(null);
        return new PushDataWrapper(serviceMetadata, serviceInfo);
    }
    
    private Collection<String> getTargetClientIds() {
        return delayTask.isPushToAll() ? delayTaskEngine.getIndexesManager().getAllClientsSubscribeService(service)
                : delayTask.getTargetClients();
    }
    
    private class ServicePushCallback implements NamingPushCallback {
        
        private final String clientId;
        
        private final Subscriber subscriber;
        
        private final ServiceInfo serviceInfo;
        
        /**
         * Record the push task execute start time.
         */
        private final long executeStartTime;
        
        private final boolean isPushToAll;
        
        /**
         * The actual pushed service info, the host list of service info may be changed by selector. Detail see
         * implement of {@link com.alibaba.nacos.naming.push.v2.executor.PushExecutor}.
         */
        private ServiceInfo actualServiceInfo;
        
        private ServicePushCallback(String clientId, Subscriber subscriber, ServiceInfo serviceInfo,
                boolean isPushToAll) {
            this.clientId = clientId;
            this.subscriber = subscriber;
            this.serviceInfo = serviceInfo;
            this.isPushToAll = isPushToAll;
            this.executeStartTime = System.currentTimeMillis();
            this.actualServiceInfo = serviceInfo;
        }
        
        @Override
        public long getTimeout() {
            return PushConfig.getInstance().getPushTaskTimeout();
        }
        
        @Override
        public void onSuccess() {
            long pushFinishTime = System.currentTimeMillis();
            long pushCostTimeForNetWork = pushFinishTime - executeStartTime;
            long pushCostTimeForAll = pushFinishTime - delayTask.getLastProcessTime();
            long serviceLevelAgreementTime = pushFinishTime - service.getLastUpdatedTime();
            if (isPushToAll) {
                Loggers.PUSH
                        .info("[PUSH-SUCC] {}ms, all delay time {}ms, SLA {}ms, {}, originalSize={}, DataSize={}, target={}",
                                pushCostTimeForNetWork, pushCostTimeForAll, serviceLevelAgreementTime, service,
                                serviceInfo.getHosts().size(), actualServiceInfo.getHosts().size(), subscriber.getIp());
            } else {
                Loggers.PUSH
                        .info("[PUSH-SUCC] {}ms, all delay time {}ms for subscriber {}, {}, originalSize={}, DataSize={}",
                                pushCostTimeForNetWork, pushCostTimeForAll, subscriber.getIp(), service,
                                serviceInfo.getHosts().size(), actualServiceInfo.getHosts().size());
            }
            PushResult result = PushResult
                    .pushSuccess(service, clientId, actualServiceInfo, subscriber, pushCostTimeForNetWork,
                            pushCostTimeForAll, serviceLevelAgreementTime, isPushToAll);
            NotifyCenter.publishEvent(getPushServiceTraceEvent(pushFinishTime, result));
            PushResultHookHolder.getInstance().pushSuccess(result);
        }
        
        @Override
        public void onFail(Throwable e) {
            long pushCostTime = System.currentTimeMillis() - executeStartTime;
            Loggers.PUSH.error("[PUSH-FAIL] {}ms, {}, reason={}, target={}", pushCostTime, service, e.getMessage(),
                    subscriber.getIp());
            if (!(e instanceof NoRequiredRetryException)) {
                Loggers.PUSH.error("Reason detail: ", e);
                delayTaskEngine.addTask(service,
                        new PushDelayTask(service, PushConfig.getInstance().getPushTaskRetryDelay(), clientId));
            }
            PushResult result = PushResult
                    .pushFailed(service, clientId, actualServiceInfo, subscriber, pushCostTime, e, isPushToAll);
            PushResultHookHolder.getInstance().pushFailed(result);
        }
        
        public void setActualServiceInfo(ServiceInfo actualServiceInfo) {
            this.actualServiceInfo = actualServiceInfo;
        }
        
        private PushServiceTraceEvent getPushServiceTraceEvent(long eventTime, PushResult result) {
            return new PushServiceTraceEvent(eventTime, result.getNetworkCost(), result.getAllCost(),
                    result.getSla(), result.getSubscriber().getIp(), result.getService().getNamespace(),
                    result.getService().getGroup(), result.getService().getName(), result.getData().getHosts().size());
        }
        
    }
}
