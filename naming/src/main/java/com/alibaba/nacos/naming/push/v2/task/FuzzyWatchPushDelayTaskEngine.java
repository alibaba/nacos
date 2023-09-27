/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

import com.alibaba.nacos.common.task.NacosTask;
import com.alibaba.nacos.common.task.NacosTaskProcessor;
import com.alibaba.nacos.common.task.engine.NacosDelayTaskExecuteEngine;
import com.alibaba.nacos.naming.core.v2.client.manager.ClientManager;
import com.alibaba.nacos.naming.core.v2.index.ClientServiceIndexesManager;
import com.alibaba.nacos.naming.core.v2.index.ServiceStorage;
import com.alibaba.nacos.naming.core.v2.metadata.NamingMetadataManager;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.NamingExecuteTaskDispatcher;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import com.alibaba.nacos.naming.push.v2.executor.PushExecutor;

/**
 * Nacos naming fuzzy watch notify service change push delay task execute engine.
 *
 * @author tanyongquan
 */
public class FuzzyWatchPushDelayTaskEngine extends NacosDelayTaskExecuteEngine {
    
    private final ClientManager clientManager;
    
    private final ClientServiceIndexesManager indexesManager;
    
    private final ServiceStorage serviceStorage;
    
    private final NamingMetadataManager metadataManager;
    
    private final PushExecutor pushExecutor;
    
    private final SwitchDomain switchDomain;
    
    public FuzzyWatchPushDelayTaskEngine(ClientManager clientManager, ClientServiceIndexesManager indexesManager,
            ServiceStorage serviceStorage, NamingMetadataManager metadataManager,
            PushExecutor pushExecutor, SwitchDomain switchDomain) {
        super(FuzzyWatchPushDelayTaskEngine.class.getSimpleName(), Loggers.PUSH);
        this.clientManager = clientManager;
        this.indexesManager = indexesManager;
        this.serviceStorage = serviceStorage;
        this.metadataManager = metadataManager;
        this.pushExecutor = pushExecutor;
        this.switchDomain = switchDomain;
        setDefaultTaskProcessor(new WatchPushDelayTaskProcessor(this));
    }
    
    public ClientManager getClientManager() {
        return clientManager;
    }
    
    public ClientServiceIndexesManager getIndexesManager() {
        return indexesManager;
    }
    
    public ServiceStorage getServiceStorage() {
        return serviceStorage;
    }
    
    public NamingMetadataManager getMetadataManager() {
        return metadataManager;
    }
    
    public PushExecutor getPushExecutor() {
        return pushExecutor;
    }
    
    @Override
    protected void processTasks() {
        if (!switchDomain.isPushEnabled()) {
            return;
        }
        super.processTasks();
    }
    
    private static class WatchPushDelayTaskProcessor implements NacosTaskProcessor {
        
        private final FuzzyWatchPushDelayTaskEngine executeEngine;
        
        public WatchPushDelayTaskProcessor(FuzzyWatchPushDelayTaskEngine executeEngine) {
            this.executeEngine = executeEngine;
        }
        
        @Override
        public boolean process(NacosTask task) {
            if (task instanceof FuzzyWatchNotifyChangeDelayTask) {
                FuzzyWatchNotifyChangeDelayTask notifyDelayTask = (FuzzyWatchNotifyChangeDelayTask) task;
                Service service = notifyDelayTask.getService();
                NamingExecuteTaskDispatcher.getInstance()
                        .dispatchAndExecuteTask(service, new FuzzyWatchNotifyChangeExecuteTask(service, executeEngine, notifyDelayTask));
            } else if (task instanceof FuzzyWatchInitDelayTask) {
                FuzzyWatchInitDelayTask fuzzyWatchInitDelayTask = (FuzzyWatchInitDelayTask) task;
                String pattern = fuzzyWatchInitDelayTask.getPattern();
                String clientId = fuzzyWatchInitDelayTask.getClientId();
                String taskKey = fuzzyWatchInitDelayTask.getTaskKey();
                NamingExecuteTaskDispatcher.getInstance()
                        .dispatchAndExecuteTask(taskKey, new FuzzyWatchInitExecuteTask(taskKey, clientId, pattern,
                                fuzzyWatchInitDelayTask.getOriginSize(), executeEngine, fuzzyWatchInitDelayTask,
                                fuzzyWatchInitDelayTask.isFinishInit()));
            }
            return true;
        }
    }
}
