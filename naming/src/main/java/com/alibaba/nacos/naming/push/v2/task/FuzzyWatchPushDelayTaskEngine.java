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

import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.common.task.NacosTask;
import com.alibaba.nacos.common.task.NacosTaskProcessor;
import com.alibaba.nacos.common.task.engine.NacosDelayTaskExecuteEngine;
import com.alibaba.nacos.naming.core.v2.index.NamingFuzzyWatchContextService;
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
    
    private final NamingFuzzyWatchContextService namingFuzzyWatchContextService;
    
    private final ServiceStorage serviceStorage;
    
    private final NamingMetadataManager metadataManager;
    
    private final PushExecutor pushExecutor;
    
    private final SwitchDomain switchDomain;
    
    public FuzzyWatchPushDelayTaskEngine(NamingFuzzyWatchContextService namingFuzzyWatchContextService,
            ServiceStorage serviceStorage, NamingMetadataManager metadataManager, PushExecutor pushExecutor,
            SwitchDomain switchDomain) {
        super(FuzzyWatchPushDelayTaskEngine.class.getSimpleName(), Loggers.PUSH);
        this.namingFuzzyWatchContextService = namingFuzzyWatchContextService;
        this.serviceStorage = serviceStorage;
        this.metadataManager = metadataManager;
        this.pushExecutor = pushExecutor;
        this.switchDomain = switchDomain;
        setDefaultTaskProcessor(new WatchPushDelayTaskProcessor(this));
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
            
            if (task instanceof FuzzyWatchChangeNotifyTask) {
                //process  fuzzy watch change notify when a service changed
                FuzzyWatchChangeNotifyTask fuzzyWatchChangeNotifyTask = (FuzzyWatchChangeNotifyTask) task;
                NamingExecuteTaskDispatcher.getInstance().dispatchAndExecuteTask(
                        getTaskKey(task),
                        new FuzzyWatchChangeNotifyExecuteTask(executeEngine, fuzzyWatchChangeNotifyTask.getServiceKey(),
                                fuzzyWatchChangeNotifyTask.getChangedType(), fuzzyWatchChangeNotifyTask.getClientId()));
            } else if (task instanceof FuzzyWatchInitNotifyTask) {
                //process fuzzy watch init notify when a new client fuzzy watch a pattern
                FuzzyWatchInitNotifyTask fuzzyWatchInitNotifyTask = (FuzzyWatchInitNotifyTask) task;
                String pattern = fuzzyWatchInitNotifyTask.getPattern();
                String clientId = fuzzyWatchInitNotifyTask.getClientId();
                NamingExecuteTaskDispatcher.getInstance().dispatchAndExecuteTask(getTaskKey(task),
                        new FuzzyWatchInitNotifyExecuteTask(clientId, pattern,
                                fuzzyWatchInitNotifyTask.getOriginSize(), executeEngine, fuzzyWatchInitNotifyTask,
                                fuzzyWatchInitNotifyTask.isFinishInit()));
            }
            return true;
        }
        
    }
    
    public NamingFuzzyWatchContextService getNamingFuzzyWatchContextService() {
        return namingFuzzyWatchContextService;
    }
    
    public static String getTaskKey(NacosTask task){
        if(task instanceof FuzzyWatchChangeNotifyTask){
            return "fwcnT-"+((FuzzyWatchChangeNotifyTask) task).getClientId()+((FuzzyWatchChangeNotifyTask) task).getServiceKey();
        }else if (task instanceof FuzzyWatchInitNotifyTask){
            return "fwinT-"+((FuzzyWatchInitNotifyTask) task).getClientId()+((FuzzyWatchInitNotifyTask) task).getPattern();
        }else {
            throw new NacosRuntimeException(500,"unknown fuzzy task type");
        }
    }
}
