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
import com.alibaba.nacos.api.remote.PushCallBack;
import com.alibaba.nacos.common.task.AbstractExecuteTask;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.monitor.MetricsMonitor;
import com.alibaba.nacos.naming.pojo.Subscriber;
import com.alibaba.nacos.naming.push.v2.NoRequiredRetryException;
import com.alibaba.nacos.naming.utils.ServiceUtil;

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
            ServiceInfo serviceInfo = delayTaskEngine.getServiceStorage().getPushData(service);
            serviceInfo = ServiceUtil.selectInstances(serviceInfo, false, true);
            for (String each : getTargetClientIds()) {
                Subscriber subscriber = delayTaskEngine.getClientManager().getClient(each).getSubscriber(service);
                delayTaskEngine.getPushExecutor()
                        .doPushWithCallback(each, subscriber, handleClusterData(serviceInfo, subscriber),
                                new NamingPushCallback(each, subscriber, serviceInfo));
            }
        } catch (Exception e) {
            Loggers.PUSH.error("Push task for service" + service.getGroupedServiceName() + " execute failed ", e);
            delayTaskEngine.addTask(service, new PushDelayTask(service, 1000L));
        }
    }
    
    private Collection<String> getTargetClientIds() {
        return delayTask.isPushToAll() ? delayTaskEngine.getIndexesManager().getAllClientsSubscribeService(service)
                : delayTask.getTargetClients();
    }
    
    /**
     * For adapt push cluster feature for v1.x.
     *
     * @param data       original data
     * @param subscriber subscriber information
     * @return cluster filtered data
     * @deprecated Will be removed after client can filter cluster
     */
    @Deprecated
    private ServiceInfo handleClusterData(ServiceInfo data, Subscriber subscriber) {
        return StringUtils.isBlank(subscriber.getCluster()) ? data
                : ServiceUtil.selectInstances(data, subscriber.getCluster());
    }
    
    private class NamingPushCallback implements PushCallBack {
        
        private final String clientId;
        
        private final Subscriber subscriber;
        
        private final ServiceInfo serviceInfo;
        
        /**
         * Record the push task execute start time.
         */
        private final long executeStartTime;
        
        private NamingPushCallback(String clientId, Subscriber subscriber, ServiceInfo serviceInfo) {
            this.clientId = clientId;
            this.subscriber = subscriber;
            this.serviceInfo = serviceInfo;
            this.executeStartTime = System.currentTimeMillis();
        }
        
        @Override
        public long getTimeout() {
            // TODO timeout should can be config
            return 3000L;
        }
        
        @Override
        public void onSuccess() {
            long pushFinishTime = System.currentTimeMillis();
            long pushCostTimeForNetWork = pushFinishTime - executeStartTime;
            long pushCostTimeForAll = pushFinishTime - delayTask.getLastProcessTime();
            long serviceLevelAgreementTime = pushFinishTime - service.getLastUpdatedTime();
            Loggers.PUSH.info("[PUSH-SUCC] {}ms, all delay time {}ms, SLA {}ms, {}, DataSize={}, target={}",
                    pushCostTimeForNetWork, pushCostTimeForAll, serviceLevelAgreementTime, service,
                    serviceInfo.getHosts().size(), subscriber.getIp());
            MetricsMonitor.incrementPush();
            MetricsMonitor.incrementPushCost(pushCostTimeForNetWork);
            MetricsMonitor.compareAndSetMaxPushCost(pushCostTimeForNetWork);
        }
        
        @Override
        public void onFail(Throwable e) {
            long pushCostTime = System.currentTimeMillis() - executeStartTime;
            Loggers.PUSH.error("[PUSH-FAIL] {}ms, {}, reason={}, target={}", pushCostTime, service, e.getMessage(),
                    subscriber.getIp());
            MetricsMonitor.incrementFailPush();
            if (!(e instanceof NoRequiredRetryException)) {
                Loggers.PUSH.error("Reason detail: ", e);
                delayTaskEngine.addTask(service, new PushDelayTask(service, 1000L, clientId));
            }
        }
    }
}
