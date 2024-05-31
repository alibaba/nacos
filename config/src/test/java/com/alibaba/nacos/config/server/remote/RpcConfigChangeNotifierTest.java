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

import com.alibaba.nacos.api.config.remote.request.ConfigChangeNotifyRequest;
import com.alibaba.nacos.config.server.model.event.LocalDataChangeEvent;
import com.alibaba.nacos.config.server.utils.ConfigExecutor;
import com.alibaba.nacos.config.server.utils.GroupKey2;
import com.alibaba.nacos.core.remote.ConnectionManager;
import com.alibaba.nacos.core.remote.ConnectionMeta;
import com.alibaba.nacos.core.remote.RpcPushService;
import com.alibaba.nacos.core.remote.grpc.GrpcConnection;
import com.alibaba.nacos.plugin.control.ControlManagerCenter;
import com.alibaba.nacos.plugin.control.tps.TpsControlManager;
import com.alibaba.nacos.plugin.control.tps.request.TpsCheckRequest;
import com.alibaba.nacos.plugin.control.tps.response.TpsCheckResponse;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class RpcConfigChangeNotifierTest {
    
    static final String POINT_CONFIG_PUSH = "CONFIG_PUSH_COUNT";
    
    static final String POINT_CONFIG_PUSH_SUCCESS = "CONFIG_PUSH_SUCCESS";
    
    static final String POINT_CONFIG_PUSH_FAIL = "CONFIG_PUSH_FAIL";
    
    @Mock
    ControlManagerCenter controlManagerCenter;
    
    @Mock
    TpsControlManager tpsControlManager;
    
    MockedStatic<ControlManagerCenter> controlManagerCenterMockedStatic;
    
    MockedStatic<EnvUtil> envUtilMockedStatic;
    
    private RpcConfigChangeNotifier rpcConfigChangeNotifier;
    
    @Mock
    private ConfigChangeListenContext configChangeListenContext;
    
    @Mock
    private RpcPushService rpcPushService;
    
    @Mock
    private ConnectionManager connectionManager;
    
    @BeforeEach
    void setUp() {
        
        envUtilMockedStatic = Mockito.mockStatic(EnvUtil.class);
        envUtilMockedStatic.when(() -> EnvUtil.getProperty(eq("nacos.config.push.maxRetryTime"), eq(Integer.class), anyInt()))
                .thenReturn(3);
        controlManagerCenterMockedStatic = Mockito.mockStatic(ControlManagerCenter.class);
        Mockito.when(ControlManagerCenter.getInstance()).thenReturn(controlManagerCenter);
        Mockito.when(ControlManagerCenter.getInstance().getTpsControlManager()).thenReturn(tpsControlManager);
        rpcConfigChangeNotifier = new RpcConfigChangeNotifier();
        ReflectionTestUtils.setField(rpcConfigChangeNotifier, "configChangeListenContext", configChangeListenContext);
        ReflectionTestUtils.setField(rpcConfigChangeNotifier, "rpcPushService", rpcPushService);
        ReflectionTestUtils.setField(rpcConfigChangeNotifier, "connectionManager", connectionManager);
        
    }
    
    @AfterEach
    void after() {
        envUtilMockedStatic.close();
        controlManagerCenterMockedStatic.close();
    }
    
    @Test
    void testOnDataEvent() throws InterruptedException {
        
        final String groupKey = GroupKey2.getKey("nacos.internal.tps.control_rule_1", "nacos", "tenant");
        
        List<String> betaIps = new ArrayList<>();
        
        betaIps.add("1.1.1.1");
        Set<String> mockConnectionIds = new HashSet<>();
        mockConnectionIds.add("con1");
        mockConnectionIds.add("con2");
        mockConnectionIds.add("con3");
        GrpcConnection mockConn1 = Mockito.mock(GrpcConnection.class);
        GrpcConnection mockConn3 = Mockito.mock(GrpcConnection.class);
        //mock con1 push normal
        Mockito.when(connectionManager.getConnection(eq("con1"))).thenReturn(mockConn1);
        Mockito.when(mockConn1.getMetaInfo())
                .thenReturn(new ConnectionMeta("con1", "192.168.0.1", "192.168.0.2", 34567, 9848, "GRPC", "2.2.0", null, new HashMap<>()));
        //mock con1 noy exist
        Mockito.when(connectionManager.getConnection(eq("con2"))).thenReturn(null);
        Mockito.when(connectionManager.getConnection(eq("con3"))).thenReturn(mockConn3);
        Mockito.when(mockConn3.getMetaInfo())
                .thenReturn(new ConnectionMeta("con3", "192.168.0.1", "192.168.0.2", 34567, 9848, "GRPC", "2.2.0", null, new HashMap<>()));
        Mockito.when(configChangeListenContext.getListeners(eq(groupKey))).thenReturn(mockConnectionIds);
        //mock push tps passed
        Mockito.when(tpsControlManager.check(any(TpsCheckRequest.class))).thenReturn(new TpsCheckResponse(true, 200, "success"));
        
        rpcConfigChangeNotifier.onEvent(new LocalDataChangeEvent(groupKey, true, betaIps));
        //wait rpc push executed.
        Thread.sleep(50L);
        //expect rpc push task run.
        Mockito.verify(rpcPushService, times(1))
                .pushWithCallback(eq("con1"), any(ConfigChangeNotifyRequest.class), any(RpcConfigChangeNotifier.RpcPushCallback.class),
                        any(Executor.class));
        Mockito.verify(rpcPushService, times(1))
                .pushWithCallback(eq("con3"), any(ConfigChangeNotifyRequest.class), any(RpcConfigChangeNotifier.RpcPushCallback.class),
                        any(Executor.class));
        
    }
    
    @Test
    void testRpcCallBack() {
        MockedStatic<ConfigExecutor> configExecutorMockedStatic = Mockito.mockStatic(ConfigExecutor.class);
        try {
            RpcConfigChangeNotifier.RpcPushTask task = Mockito.mock(RpcConfigChangeNotifier.RpcPushTask.class);
            
            Mockito.when(task.getConnectionId()).thenReturn("testconn1");
            Mockito.when(connectionManager.getConnection(eq("testconn1"))).thenReturn(Mockito.mock(GrpcConnection.class));
            ConfigChangeNotifyRequest notifyRequest = new ConfigChangeNotifyRequest();
            notifyRequest.setDataId("d1");
            notifyRequest.setGroup("g1");
            Mockito.when(task.getNotifyRequest()).thenReturn(notifyRequest);
            //mock task not overtimes and receive exception on callback
            Mockito.when(task.isOverTimes()).thenReturn(false);
            Mockito.when(task.getTryTimes()).thenReturn(2);
            RpcConfigChangeNotifier.RpcPushCallback rpcPushCallback = new RpcConfigChangeNotifier.RpcPushCallback(task, tpsControlManager,
                    connectionManager);
            rpcPushCallback.onFail(new RuntimeException());
            
            //expect config push fail be recorded.
            Mockito.verify(tpsControlManager, times(1)).check(any(TpsCheckRequest.class));
            //expect schedule this task next retry times
            configExecutorMockedStatic.verify(
                    () -> ConfigExecutor.scheduleClientConfigNotifier(any(RpcConfigChangeNotifier.RpcPushTask.class), eq(2 * 2L),
                            eq(TimeUnit.SECONDS)));
            //mock
            rpcPushCallback.onSuccess();
            //expect config push success be recorded.
            Mockito.verify(tpsControlManager, times(2)).check(any(TpsCheckRequest.class));
            
            //mock task is over times
            Mockito.when(task.isOverTimes()).thenReturn(true);
            rpcPushCallback.onFail(new NullPointerException());
            Mockito.verify(connectionManager, times(1)).unregister(eq("testconn1"));
            
        } finally {
            configExecutorMockedStatic.close();
        }
        
    }
    
    @Test
    void testRegisterTpsPoint() {
        
        rpcConfigChangeNotifier.registerTpsPoint();
        Mockito.verify(tpsControlManager, Mockito.times(1)).registerTpsPoint(eq(POINT_CONFIG_PUSH));
        Mockito.verify(tpsControlManager, Mockito.times(1)).registerTpsPoint(eq(POINT_CONFIG_PUSH_SUCCESS));
        Mockito.verify(tpsControlManager, Mockito.times(1)).registerTpsPoint(eq(POINT_CONFIG_PUSH_FAIL));
        
    }
    
}
