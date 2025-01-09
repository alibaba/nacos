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
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.NamingExecuteTaskDispatcher;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import com.alibaba.nacos.naming.push.v2.executor.PushExecutor;
import com.alibaba.nacos.naming.push.v2.executor.PushExecutorDelegate;
import org.springframework.stereotype.Component;

/**
 * Nacos naming fuzzy watch notify service change push delay task execute engine.
 *
 * @author tanyongquan
 */
@Component
public class FuzzyWatchPushDelayTaskEngine extends NacosDelayTaskExecuteEngine {
    
    private final PushExecutorDelegate pushExecutor;
    
    private final SwitchDomain switchDomain;
    
    public FuzzyWatchPushDelayTaskEngine(PushExecutorDelegate pushExecutor, SwitchDomain switchDomain) {
        super(FuzzyWatchPushDelayTaskEngine.class.getSimpleName(), Loggers.PUSH);
        this.pushExecutor = pushExecutor;
        this.switchDomain = switchDomain;
        setDefaultTaskProcessor(new WatchPushDelayTaskProcessor(this));
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
        
        private final FuzzyWatchPushDelayTaskEngine fuzzyWatchPushExecuteEngine;
        
        public WatchPushDelayTaskProcessor(FuzzyWatchPushDelayTaskEngine fuzzyWatchPushExecuteEngine) {
            this.fuzzyWatchPushExecuteEngine = fuzzyWatchPushExecuteEngine;
        }
        
        @Override
        public boolean process(NacosTask task) {
            
            if (task instanceof FuzzyWatchChangeNotifyTask) {
                //process  fuzzy watch change notify when a service changed
                FuzzyWatchChangeNotifyTask fuzzyWatchChangeNotifyTask = (FuzzyWatchChangeNotifyTask) task;
                NamingExecuteTaskDispatcher.getInstance().dispatchAndExecuteTask(getTaskKey(task),
                        new FuzzyWatchChangeNotifyExecuteTask(fuzzyWatchPushExecuteEngine,
                                fuzzyWatchChangeNotifyTask.getServiceKey(), fuzzyWatchChangeNotifyTask.getChangedType(),
                                fuzzyWatchChangeNotifyTask.getClientId()));
            } else if (task instanceof FuzzyWatchSyncNotifyTask) {
                //process fuzzy watch sync notify when a new client fuzzy watch a pattern
                FuzzyWatchSyncNotifyTask fuzzyWatchSyncNotifyTask = (FuzzyWatchSyncNotifyTask) task;
                String pattern = fuzzyWatchSyncNotifyTask.getPattern();
                String clientId = fuzzyWatchSyncNotifyTask.getClientId();
                NamingExecuteTaskDispatcher.getInstance().dispatchAndExecuteTask(getTaskKey(task),
                        new FuzzyWatchSyncNotifyExecuteTask(clientId, pattern, fuzzyWatchPushExecuteEngine,
                                fuzzyWatchSyncNotifyTask));
            }
            return true;
        }
        
    }
    
    public static String getTaskKey(NacosTask task) {
        if (task instanceof FuzzyWatchChangeNotifyTask) {
            return "fwcnT-" + ((FuzzyWatchChangeNotifyTask) task).getClientId()
                    + ((FuzzyWatchChangeNotifyTask) task).getServiceKey();
        } else if (task instanceof FuzzyWatchSyncNotifyTask) {
            return "fwsnT-" + ((FuzzyWatchSyncNotifyTask) task).getSyncType() + "-"
                    + ((FuzzyWatchSyncNotifyTask) task).getClientId() + ((FuzzyWatchSyncNotifyTask) task).getPattern()
                    + "-" + ((FuzzyWatchSyncNotifyTask) task).getCurrentBatch();
        } else {
            throw new NacosRuntimeException(500, "unknown fuzzy task type");
        }
    }
}
