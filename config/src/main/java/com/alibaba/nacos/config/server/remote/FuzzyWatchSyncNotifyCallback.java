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

package com.alibaba.nacos.config.server.remote;

import com.alibaba.nacos.api.config.remote.request.ConfigFuzzyWatchSyncRequest;
import com.alibaba.nacos.api.remote.AbstractPushCallBack;
import com.alibaba.nacos.core.utils.Loggers;
import com.alibaba.nacos.plugin.control.ControlManagerCenter;
import com.alibaba.nacos.plugin.control.tps.request.TpsCheckRequest;

import static com.alibaba.nacos.api.common.Constants.FUZZY_WATCH_INIT_NOTIFY;

/**
 * Represents a callback for handling the result of an RPC push operation.
 *
 * @author stone-98
 */
class FuzzyWatchSyncNotifyCallback extends AbstractPushCallBack {
    
    /**
     * The RpcPushTask associated with the callback.
     */
    FuzzyWatchSyncNotifyTask fuzzyWatchSyncNotifyTask;
    
    /**
     * Constructs a new RpcPushCallback with the specified parameters.
     *
     * @param fuzzyWatchSyncNotifyTask The RpcPushTask associated with the callback
     */
    public FuzzyWatchSyncNotifyCallback(FuzzyWatchSyncNotifyTask fuzzyWatchSyncNotifyTask) {
        super(3000L);
        this.fuzzyWatchSyncNotifyTask = fuzzyWatchSyncNotifyTask;
    }
    
    /**
     * Handles the successful completion of the RPC push operation.
     */
    @Override
    public void onSuccess() {
        // Check TPS limits
        TpsCheckRequest tpsCheckRequest = new TpsCheckRequest();
        tpsCheckRequest.setPointName(FuzzyWatchSyncNotifyTask.CONFIG_FUZZY_WATCH_CONFIG_SYNC_SUCCESS);
        ControlManagerCenter.getInstance().getTpsControlManager().check(tpsCheckRequest);
        
        if (fuzzyWatchSyncNotifyTask.batchTaskCounter != null) {
            fuzzyWatchSyncNotifyTask.batchTaskCounter.batchSuccess(
                    fuzzyWatchSyncNotifyTask.notifyRequest.getCurrentBatch());
            if (fuzzyWatchSyncNotifyTask.batchTaskCounter.batchCompleted()
                    && fuzzyWatchSyncNotifyTask.notifyRequest.getSyncType().equals(FUZZY_WATCH_INIT_NOTIFY)) {
                ConfigFuzzyWatchSyncRequest request = ConfigFuzzyWatchSyncRequest.buildInitFinishRequest(
                        fuzzyWatchSyncNotifyTask.notifyRequest.getGroupKeyPattern());
                
                // Create RPC push task and push the request to the client
                FuzzyWatchSyncNotifyTask fuzzyWatchSyncNotifyTaskFinish = new FuzzyWatchSyncNotifyTask(
                        fuzzyWatchSyncNotifyTask.connectionManager, fuzzyWatchSyncNotifyTask.rpcPushService, request,
                        null, fuzzyWatchSyncNotifyTask.maxRetryTimes, fuzzyWatchSyncNotifyTask.connectionId);
                fuzzyWatchSyncNotifyTaskFinish.scheduleSelf();
            }
        }
    }
    
    /**
     * Handles the failure of the RPC push operation.
     *
     * @param e The exception thrown during the operation
     */
    @Override
    public void onFail(Throwable e) {
        // Check TPS limits
        TpsCheckRequest tpsCheckRequest = new TpsCheckRequest();
        tpsCheckRequest.setPointName(FuzzyWatchSyncNotifyTask.CONFIG_FUZZY_WATCH_CONFIG_SYNC_FAIL);
        ControlManagerCenter.getInstance().getTpsControlManager().check(tpsCheckRequest);
        
        // Log the failure and retry the task
        Loggers.REMOTE_PUSH.warn("Push fail, groupKeyPattern={}, clientId={}",
                fuzzyWatchSyncNotifyTask.notifyRequest.getGroupKeyPattern(), fuzzyWatchSyncNotifyTask.connectionId, e);
        fuzzyWatchSyncNotifyTask.scheduleSelf();
    }
}
