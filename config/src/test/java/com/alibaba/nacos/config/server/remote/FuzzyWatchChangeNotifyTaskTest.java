/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

import com.alibaba.nacos.api.config.remote.request.ConfigFuzzyWatchChangeNotifyRequest;
import com.alibaba.nacos.core.remote.Connection;
import com.alibaba.nacos.core.remote.ConnectionManager;
import com.alibaba.nacos.core.remote.RpcPushService;
import com.alibaba.nacos.plugin.control.ControlManagerCenter;
import com.alibaba.nacos.plugin.control.tps.TpsControlManager;
import com.alibaba.nacos.plugin.control.tps.response.TpsCheckResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FuzzyWatchChangeNotifyTaskTest {
    
    @Mock
    private ConnectionManager connectionManager;
    
    @Mock
    private RpcPushService rpcPushService;
    
    @Mock
    private ControlManagerCenter controlManagerCenter;
    
    @Mock
    private TpsControlManager tpsControlManager;
    
    @Mock
    private Connection connection;
    
    MockedStatic<ControlManagerCenter> controlManagerCenterMockedStatic;
    
    @BeforeEach
    void setUp() {
        controlManagerCenterMockedStatic =
            Mockito.mockStatic(ControlManagerCenter.class);
        controlManagerCenterMockedStatic.when(ControlManagerCenter::getInstance)
            .thenReturn(controlManagerCenter);
        Mockito.lenient().when(controlManagerCenter.getTpsControlManager())
            .thenReturn(tpsControlManager);
    }
    
    @AfterEach
    void tearDown() {
        controlManagerCenterMockedStatic.close();
    }
    
    @Test
    void testIsOverTimesWhenNotOver() {
        ConfigFuzzyWatchChangeNotifyRequest request =
            new ConfigFuzzyWatchChangeNotifyRequest();
        FuzzyWatchChangeNotifyTask task = new FuzzyWatchChangeNotifyTask(
            connectionManager, rpcPushService, request, 3, "conn1");
        assertFalse(task.isOverTimes());
    }
    
    @Test
    void testIsOverTimesWhenOver() {
        ConfigFuzzyWatchChangeNotifyRequest request =
            new ConfigFuzzyWatchChangeNotifyRequest();
        FuzzyWatchChangeNotifyTask task = new FuzzyWatchChangeNotifyTask(
            connectionManager, rpcPushService, request, 1, "conn1");
        task.tryTimes = 1;
        assertTrue(task.isOverTimes());
    }
    
    @Test
    void testIsOverTimesWhenMaxRetryZero() {
        ConfigFuzzyWatchChangeNotifyRequest request =
            new ConfigFuzzyWatchChangeNotifyRequest();
        FuzzyWatchChangeNotifyTask task = new FuzzyWatchChangeNotifyTask(
            connectionManager, rpcPushService, request, 0, "conn1");
        task.tryTimes = 100;
        assertFalse(task.isOverTimes());
    }
    
    @Test
    void testRunOverTimes() {
        ConfigFuzzyWatchChangeNotifyRequest request =
            new ConfigFuzzyWatchChangeNotifyRequest();
        FuzzyWatchChangeNotifyTask task = new FuzzyWatchChangeNotifyTask(
            connectionManager, rpcPushService, request, 1, "conn1");
        task.tryTimes = 1;
        TpsCheckResponse resp = new TpsCheckResponse(true, 200, "ok");
        when(tpsControlManager.check(any())).thenReturn(resp);
        task.run();
        verify(connectionManager).unregister("conn1");
    }
    
    @Test
    void testRunNullConnection() {
        ConfigFuzzyWatchChangeNotifyRequest request =
            new ConfigFuzzyWatchChangeNotifyRequest();
        FuzzyWatchChangeNotifyTask task = new FuzzyWatchChangeNotifyTask(
            connectionManager, rpcPushService, request, 3, "conn1");
        when(connectionManager.getConnection("conn1")).thenReturn(null);
        task.run();
        verify(rpcPushService, Mockito.never())
            .pushWithCallback(any(), any(), any(), any());
    }
    
    @Test
    void testRunPushSuccess() {
        ConfigFuzzyWatchChangeNotifyRequest request =
            new ConfigFuzzyWatchChangeNotifyRequest();
        FuzzyWatchChangeNotifyTask task = new FuzzyWatchChangeNotifyTask(
            connectionManager, rpcPushService, request, 3, "conn1");
        when(connectionManager.getConnection("conn1")).thenReturn(connection);
        TpsCheckResponse successResp = new TpsCheckResponse(true, 200, "ok");
        when(tpsControlManager.check(any())).thenReturn(successResp);
        task.run();
        verify(rpcPushService).pushWithCallback(any(), any(), any(), any());
    }
}
