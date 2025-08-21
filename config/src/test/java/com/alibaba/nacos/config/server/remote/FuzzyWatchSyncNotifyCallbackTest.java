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

package com.alibaba.nacos.config.server.remote;

import com.alibaba.nacos.api.config.remote.request.ConfigFuzzyWatchSyncRequest;
import com.alibaba.nacos.common.task.BatchTaskCounter;
import com.alibaba.nacos.config.server.utils.ConfigExecutor;
import com.alibaba.nacos.core.remote.ConnectionManager;
import com.alibaba.nacos.core.remote.RpcPushService;
import com.alibaba.nacos.plugin.control.ControlManagerCenter;
import com.alibaba.nacos.plugin.control.tps.TpsControlManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static com.alibaba.nacos.api.common.Constants.FUZZY_WATCH_INIT_NOTIFY;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class FuzzyWatchSyncNotifyCallbackTest {
    
    @Mock
    ConnectionManager connectionManager;
    
    @Mock
    ControlManagerCenter controlManagerCenter;
    
    @Mock
    TpsControlManager tpsControlManager;
    
    MockedStatic<ControlManagerCenter> controlManagerCenterMockedStatic;
    
    @Mock
    RpcPushService rpcPushService;
    
    MockedStatic<ConfigExecutor> tMockedStatic;
    
    FuzzyWatchSyncNotifyCallback fuzzyWatchSyncNotifyCallback;
    
    BatchTaskCounter taskCounter;
    
    @Mock
    ConfigFuzzyWatchSyncRequest configFuzzyWatchSyncRequest;
    
    @AfterEach
    void after() {
        tMockedStatic.close();
        controlManagerCenterMockedStatic.close();
    }
    
    @BeforeEach
    void setUp() throws IOException {
        taskCounter = new BatchTaskCounter(5);
        tMockedStatic = Mockito.mockStatic(ConfigExecutor.class);
        controlManagerCenterMockedStatic = Mockito.mockStatic(ControlManagerCenter.class);
        Mockito.when(ControlManagerCenter.getInstance()).thenReturn(controlManagerCenter);
        Mockito.when(ControlManagerCenter.getInstance().getTpsControlManager()).thenReturn(tpsControlManager);
        FuzzyWatchSyncNotifyTask fuzzyWatchSyncNotifyTask = new FuzzyWatchSyncNotifyTask(connectionManager,
                rpcPushService, configFuzzyWatchSyncRequest, taskCounter, 5, "con1");
        fuzzyWatchSyncNotifyCallback = new FuzzyWatchSyncNotifyCallback(fuzzyWatchSyncNotifyTask);
    }
    
    @Test
    void testOnSuccess() {
        
        when(configFuzzyWatchSyncRequest.getSyncType()).thenReturn(FUZZY_WATCH_INIT_NOTIFY);
        when(configFuzzyWatchSyncRequest.getCurrentBatch()).thenReturn(5);
        
        for (int i = 1; i < 5; i++) {
            taskCounter.batchSuccess(i);
        }
        fuzzyWatchSyncNotifyCallback.fuzzyWatchSyncNotifyTask.tryTimes++;
        fuzzyWatchSyncNotifyCallback.onSuccess();
        //create a new init finish task;
        tMockedStatic.verify(
                () -> ConfigExecutor.scheduleClientConfigNotifier(any(FuzzyWatchSyncNotifyTask.class), eq(0L),
                        eq(TimeUnit.SECONDS)), times(1));
    }
    
    @Test
    void testOnFail() {
        
        fuzzyWatchSyncNotifyCallback.fuzzyWatchSyncNotifyTask.tryTimes++;
        fuzzyWatchSyncNotifyCallback.onFail(new RuntimeException());
        // schedule self ,after 2 sec.
        tMockedStatic.verify(
                () -> ConfigExecutor.scheduleClientConfigNotifier(any(FuzzyWatchSyncNotifyTask.class), eq(2L),
                        eq(TimeUnit.SECONDS)), times(1));
    }
}
