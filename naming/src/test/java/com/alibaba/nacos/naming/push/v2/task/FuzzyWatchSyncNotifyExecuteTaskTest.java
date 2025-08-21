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
import com.alibaba.nacos.api.naming.remote.request.NamingFuzzyWatchSyncRequest;
import com.alibaba.nacos.api.naming.utils.NamingUtils;
import com.alibaba.nacos.common.task.BatchTaskCounter;
import com.alibaba.nacos.common.utils.FuzzyGroupKeyPattern;
import com.alibaba.nacos.naming.push.v2.executor.PushExecutor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.Set;

import static com.alibaba.nacos.api.common.Constants.FINISH_FUZZY_WATCH_INIT_NOTIFY;
import static com.alibaba.nacos.api.common.Constants.FUZZY_WATCH_DIFF_SYNC_NOTIFY;
import static com.alibaba.nacos.api.common.Constants.FUZZY_WATCH_INIT_NOTIFY;
import static com.alibaba.nacos.api.common.Constants.ServiceChangedType.ADD_SERVICE;
import static com.alibaba.nacos.naming.push.v2.task.FuzzyWatchPushDelayTaskEngine.getTaskKey;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class FuzzyWatchSyncNotifyExecuteTaskTest {
    
    @Mock
    FuzzyWatchPushDelayTaskEngine fuzzyWatchPushDelayTaskEngine;
    
    @Mock
    PushExecutor pushExecutor;
    
    @Test
    void testSyncNotifyRun() {
        
        when(fuzzyWatchPushDelayTaskEngine.getPushExecutor()).thenReturn(pushExecutor);
        
        String groupKeyPattern = FuzzyGroupKeyPattern.generatePattern("service*", "group123", "namespace");
        Set<NamingFuzzyWatchSyncRequest.Context> matchedServiceKeys = new HashSet<>();
        for (int i = 0; i < 5; i++) {
            matchedServiceKeys.add(NamingFuzzyWatchSyncRequest.Context.build(
                    NamingUtils.getServiceKey("namespace", "group123" + i, "service" + i), ADD_SERVICE));
        }
        String clientId = "conntion1234";
        
        FuzzyWatchSyncNotifyTask fuzzyWatchSyncNotifyTask = new FuzzyWatchSyncNotifyTask(clientId, groupKeyPattern,
                FUZZY_WATCH_DIFF_SYNC_NOTIFY, matchedServiceKeys, 0L);
        FuzzyWatchSyncNotifyExecuteTask fuzzyWatchSyncNotifyExecuteTask = new FuzzyWatchSyncNotifyExecuteTask(clientId,
                groupKeyPattern, fuzzyWatchPushDelayTaskEngine, fuzzyWatchSyncNotifyTask);
        fuzzyWatchSyncNotifyExecuteTask.run();
        verify(pushExecutor, times(1)).doFuzzyWatchNotifyPushWithCallBack(eq(clientId),
                any(NamingFuzzyWatchSyncRequest.class), any(FuzzyWatchSyncNotifyCallback.class));
    }
    
    @Test
    void testCallbackSuccessForInitNotify() {
        
        String groupKeyPattern = FuzzyGroupKeyPattern.generatePattern("service*", "group123", "namespace");
        Set<NamingFuzzyWatchSyncRequest.Context> matchedServiceKeys = new HashSet<>();
        for (int i = 0; i < 5; i++) {
            matchedServiceKeys.add(NamingFuzzyWatchSyncRequest.Context.build(
                    NamingUtils.getServiceKey("namespace", "group123" + i, "service" + i), ADD_SERVICE));
        }
        String clientId = "conntion1234";
        
        BatchTaskCounter batchTaskCounter = new BatchTaskCounter(5);
        for (int i = 1; i <= 5; i++) {
            FuzzyWatchSyncNotifyTask fuzzyWatchSyncNotifyTask = new FuzzyWatchSyncNotifyTask(clientId, groupKeyPattern,
                    FUZZY_WATCH_INIT_NOTIFY, matchedServiceKeys, 0L);
            fuzzyWatchSyncNotifyTask.setTotalBatch(5);
            fuzzyWatchSyncNotifyTask.setCurrentBatch(i);
            FuzzyWatchSyncNotifyCallback fuzzyWatchSyncNotifyCallback = new FuzzyWatchSyncNotifyCallback(
                    fuzzyWatchSyncNotifyTask, batchTaskCounter, fuzzyWatchPushDelayTaskEngine);
            fuzzyWatchSyncNotifyCallback.onSuccess();
        }
        //check batch completed
        Assertions.assertEquals(true, batchTaskCounter.batchCompleted());
        //check add init notify finish task.
        verify(fuzzyWatchPushDelayTaskEngine, times(1)).addTask(anyString(), any(FuzzyWatchSyncNotifyTask.class));
    }
    
    @Test
    void testCallbackSuccessForInitFinishNotify() {
        
        String groupKeyPattern = FuzzyGroupKeyPattern.generatePattern("service*", "group123", "namespace");
        String clientId = "conntion1234";
        FuzzyWatchSyncNotifyTask fuzzyWatchSyncNotifyTask = new FuzzyWatchSyncNotifyTask(clientId, groupKeyPattern,
                FINISH_FUZZY_WATCH_INIT_NOTIFY, null, 0L);
        FuzzyWatchSyncNotifyCallback fuzzyWatchSyncNotifyCallback = new FuzzyWatchSyncNotifyCallback(
                fuzzyWatchSyncNotifyTask, null, fuzzyWatchPushDelayTaskEngine);
        
        fuzzyWatchSyncNotifyCallback.onSuccess();
        //check add init notify finish task.
        verify(fuzzyWatchPushDelayTaskEngine, times(0)).addTask(anyString(), any(FuzzyWatchSyncNotifyTask.class));
    }
    
    @Test
    void testCallbackFail() {
        
        String groupKeyPattern = FuzzyGroupKeyPattern.generatePattern("service*", "group123", "namespace");
        String clientId = "conntion1234";
        FuzzyWatchSyncNotifyTask fuzzyWatchSyncNotifyTask = new FuzzyWatchSyncNotifyTask(clientId, groupKeyPattern,
                FINISH_FUZZY_WATCH_INIT_NOTIFY, null, 0L);
        FuzzyWatchSyncNotifyCallback fuzzyWatchSyncNotifyCallback = new FuzzyWatchSyncNotifyCallback(
                fuzzyWatchSyncNotifyTask, null, fuzzyWatchPushDelayTaskEngine);
        
        fuzzyWatchSyncNotifyCallback.onFail(new NacosRuntimeException(500, "exception"));
        //check add init notify finish task.
        verify(fuzzyWatchPushDelayTaskEngine, times(1)).addTask(eq(getTaskKey(fuzzyWatchSyncNotifyTask)),
                eq(fuzzyWatchSyncNotifyTask));
    }
}
