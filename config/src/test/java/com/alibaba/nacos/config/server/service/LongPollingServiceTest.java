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

package com.alibaba.nacos.config.server.service;

import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.utils.MD5Utils;
import com.alibaba.nacos.config.server.model.ConfigListenState;
import com.alibaba.nacos.config.server.model.SampleResult;
import com.alibaba.nacos.config.server.model.event.LocalDataChangeEvent;
import com.alibaba.nacos.config.server.utils.ConfigExecutor;
import com.alibaba.nacos.config.server.utils.GroupKey;
import com.alibaba.nacos.config.server.utils.MD5Util;
import com.alibaba.nacos.plugin.control.ControlManagerCenter;
import com.alibaba.nacos.plugin.control.connection.ConnectionControlManager;
import com.alibaba.nacos.plugin.control.connection.response.ConnectionCheckResponse;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
// todo remove this
@MockitoSettings(strictness = Strictness.LENIENT)
class LongPollingServiceTest {
    
    LongPollingService longPollingService;
    
    MockedStatic<ConfigCacheService> configCacheServiceMockedStatic;
    
    MockedStatic<ConfigExecutor> configExecutorMocked;
    
    MockedStatic<ControlManagerCenter> connectionControlManagerMockedStatic;
    
    @Mock
    ControlManagerCenter controlManagerCenter;
    
    @Mock
    ConnectionControlManager connectionControlManager;
    
    MockedStatic<SwitchService> switchServiceMockedStatic;
    
    MockedStatic<EnvUtil> envUtilMockedStatic;
    
    @BeforeEach
    void before() {
        longPollingService = new LongPollingService();
        switchServiceMockedStatic = Mockito.mockStatic(SwitchService.class);
        configCacheServiceMockedStatic = Mockito.mockStatic(ConfigCacheService.class);
        configExecutorMocked = Mockito.mockStatic(ConfigExecutor.class);
        connectionControlManagerMockedStatic = Mockito.mockStatic(ControlManagerCenter.class);
        connectionControlManagerMockedStatic.when(() -> ControlManagerCenter.getInstance())
            .thenReturn(controlManagerCenter);
        envUtilMockedStatic = Mockito.mockStatic(EnvUtil.class);
        envUtilMockedStatic.when(() -> EnvUtil.getProperty("nacos.config.cache.type", "nacos"))
            .thenReturn("nacos");
        Mockito.when(controlManagerCenter.getConnectionControlManager())
            .thenReturn(connectionControlManager);
    }
    
    @AfterEach
    void after() {
        configCacheServiceMockedStatic.close();
        if (!configExecutorMocked.isClosed()) {
            configExecutorMocked.close();
        }
        connectionControlManagerMockedStatic.close();
        switchServiceMockedStatic.close();
        envUtilMockedStatic.close();
    }
    
    @Test
    void testAddLongPollingClientHasNotEqualsMd5() throws IOException {
        
        Map<String, ConfigListenState> clientMd5Map = new HashMap<>();
        String group = "group";
        String tenant = "tenat";
        String dataIdEquals = "dataIdEquals0";
        String groupKeyEquals = GroupKey.getKeyTenant(dataIdEquals, group, tenant);
        String md5Equals0 = MD5Utils.md5Hex("countEquals0", "UTF-8");
        ConfigListenState configListenState1 = new ConfigListenState(md5Equals0);
        clientMd5Map.put(groupKeyEquals, configListenState1);
        String md5NotEquals1 = MD5Utils.md5Hex("countNotEquals", "UTF-8");
        ConfigListenState configListenState2 = new ConfigListenState(md5NotEquals1);
        String dataIdNotEquals = "dataIdNotEquals0";
        String groupKeyNotEquals = GroupKey.getKeyTenant(dataIdNotEquals, group, tenant);
        clientMd5Map.put(groupKeyNotEquals, configListenState2);
        MockedStatic<MD5Util> md5UtilMockedStatic = Mockito.mockStatic(MD5Util.class);
        md5UtilMockedStatic.when(() -> MD5Util.compareMd5(any(), any(), any()))
            .thenReturn(Collections.singletonMap(groupKeyNotEquals, configListenState2));
        
        HttpServletRequest httpServletRequest = Mockito.mock(HttpServletRequest.class);
        Mockito
            .when(
                httpServletRequest.getHeader(eq(LongPollingService.LONG_POLLING_NO_HANG_UP_HEADER)))
            .thenReturn(null);
        String clientIp = "192.168.0.1";
        Mockito.when(httpServletRequest.getHeader(eq("X-Forwarded-For"))).thenReturn(clientIp);
        
        configCacheServiceMockedStatic.when(
            () -> ConfigCacheService.isUptodate(eq(groupKeyNotEquals), eq(md5NotEquals1),
                eq(clientIp), eq(null)))
            .thenReturn(false);
        configCacheServiceMockedStatic
            .when(() -> ConfigCacheService.isUptodate(eq(groupKeyEquals), eq(md5Equals0),
                eq(clientIp), eq(null)))
            .thenReturn(true);
        HttpServletResponse httpServletResponse = Mockito.mock(HttpServletResponse.class);
        PrintWriter printWriter = Mockito.mock(PrintWriter.class);
        Mockito.when(httpServletResponse.getWriter()).thenReturn(printWriter);
        int propSize = 3;
        longPollingService.addLongPollingClient(httpServletRequest, httpServletResponse,
            clientMd5Map, propSize);
        
        String responseString = MD5Util.compareMd5ResultString(
            Collections.singletonMap(groupKeyNotEquals, configListenState2));
        //expect print not equals group
        Mockito.verify(printWriter, times(1)).println(eq(responseString));
        Mockito.verify(httpServletResponse, times(1)).setStatus(eq(HttpServletResponse.SC_OK));
        md5UtilMockedStatic.close();
    }
    
    @Test
    void testRejectByConnectionLimit() throws Exception {
        //mock connection no limit
        ConnectionCheckResponse connectionCheckResponse = new ConnectionCheckResponse();
        connectionCheckResponse.setSuccess(false);
        Mockito.when(connectionControlManager.check(any())).thenReturn(connectionCheckResponse);
        HttpServletResponse httpServletResponse = Mockito.mock(HttpServletResponse.class);
        PrintWriter printWriter = Mockito.mock(PrintWriter.class);
        Mockito.when(httpServletResponse.getWriter()).thenReturn(printWriter);
        
        HttpServletRequest httpServletRequest = Mockito.mock(HttpServletRequest.class);
        Mockito
            .when(
                httpServletRequest.getHeader(eq(LongPollingService.LONG_POLLING_NO_HANG_UP_HEADER)))
            .thenReturn(null);
        String clientIp = "192.168.0.1";
        Mockito.when(httpServletRequest.getHeader(eq("X-Forwarded-For"))).thenReturn(clientIp);
        Mockito.when(httpServletRequest.startAsync()).thenReturn(Mockito.mock(AsyncContext.class));
        int propSize = 3;
        Map<String, ConfigListenState> clientMd5Map = new HashMap<>();
        longPollingService.addLongPollingClient(httpServletRequest, httpServletResponse,
            clientMd5Map, propSize);
        Thread.sleep(3000L);
        //expect response not returned
        Mockito.verify(httpServletResponse, times(1)).setStatus(eq(503));
        
    }
    
    @Test
    void testAddLongPollingClientAllEqualsMd5() throws IOException {
        //mock connection no limit
        ConnectionCheckResponse connectionCheckResponse = new ConnectionCheckResponse();
        connectionCheckResponse.setSuccess(true);
        Mockito.when(connectionControlManager.check(any())).thenReturn(connectionCheckResponse);
        
        Map<String, ConfigListenState> clientMd5Map = new HashMap<>();
        String group = "group";
        String tenant = "tenat";
        String dataIdEquals = "dataIdEquals01";
        String groupKeyEquals = GroupKey.getKeyTenant(dataIdEquals, group, tenant);
        
        String md5Equals0 = MD5Utils.md5Hex("countEquals01", "UTF-8");
        ConfigListenState configListenState1 = new ConfigListenState(md5Equals0);
        clientMd5Map.put(groupKeyEquals, configListenState1);
        String md5NotEquals1 = MD5Utils.md5Hex("countNotEquals1", "UTF-8");
        ConfigListenState configListenState2 = new ConfigListenState(md5NotEquals1);
        String dataIdNotEquals = "dataIdNotEquals01";
        String groupKeyNotEquals = GroupKey.getKeyTenant(dataIdNotEquals, group, tenant);
        clientMd5Map.put(groupKeyNotEquals, configListenState2);
        HttpServletRequest httpServletRequest = Mockito.mock(HttpServletRequest.class);
        
        Mockito.when(httpServletRequest.getHeader(eq(LongPollingService.LONG_POLLING_HEADER)))
            .thenReturn("5000");
        Mockito
            .when(
                httpServletRequest.getHeader(eq(LongPollingService.LONG_POLLING_NO_HANG_UP_HEADER)))
            .thenReturn(null);
        String clientIp = "192.168.0.1";
        Mockito.when(httpServletRequest.getHeader(eq("X-Forwarded-For"))).thenReturn(clientIp);
        Mockito.when(httpServletRequest.startAsync()).thenReturn(Mockito.mock(AsyncContext.class));
        configCacheServiceMockedStatic.when(
            () -> ConfigCacheService.isUptodate(eq(groupKeyNotEquals), eq(md5NotEquals1),
                eq(clientIp), eq(null)))
            .thenReturn(true);
        configCacheServiceMockedStatic
            .when(() -> ConfigCacheService.isUptodate(eq(groupKeyEquals), eq(md5Equals0),
                eq(clientIp), eq(null)))
            .thenReturn(true);
        int propSize = 3;
        HttpServletResponse httpServletResponse = Mockito.mock(HttpServletResponse.class);
        longPollingService.addLongPollingClient(httpServletRequest, httpServletResponse,
            clientMd5Map, propSize);
        
        //expect response not returned
        Mockito.verify(httpServletResponse, times(0)).setStatus(anyInt());
        //expect to schedule a task
        configExecutorMocked.verify(() -> ConfigExecutor
            .executeLongPolling(any(LongPollingService.ClientLongPolling.class)), times(1));
        
    }
    
    @Test
    void testAddLongPollingClientWithNoHangUp() throws IOException {
        Map<String, ConfigListenState> clientMd5Map = new HashMap<>();
        HttpServletRequest httpServletRequest = Mockito.mock(HttpServletRequest.class);
        Mockito.when(httpServletRequest.getHeader(
            eq(LongPollingService.LONG_POLLING_NO_HANG_UP_HEADER)))
            .thenReturn("true");
        Mockito.when(httpServletRequest.getHeader(eq("X-Forwarded-For")))
            .thenReturn("192.168.0.1");
        HttpServletResponse httpServletResponse = Mockito.mock(HttpServletResponse.class);
        
        try (MockedStatic<MD5Util> md5UtilMockedStatic = Mockito.mockStatic(MD5Util.class)) {
            md5UtilMockedStatic.when(() -> MD5Util.compareMd5(httpServletRequest,
                httpServletResponse, clientMd5Map))
                .thenReturn(Collections.emptyMap());
            
            longPollingService.addLongPollingClient(httpServletRequest, httpServletResponse,
                clientMd5Map, 1);
        }
        
        Mockito.verify(httpServletRequest, times(0)).startAsync();
        Mockito.verify(httpServletResponse, times(0)).setStatus(anyInt());
    }
    
    @Test
    void testReceiveDataChangeEventAndNotify() throws Exception {
        configExecutorMocked.close();
        
        //mock connection no limit
        ConnectionCheckResponse connectionCheckResponse = new ConnectionCheckResponse();
        connectionCheckResponse.setSuccess(true);
        Mockito.when(connectionControlManager.check(any())).thenReturn(connectionCheckResponse);
        
        String dataIdChanged = "dataIdChanged";
        String group = "group";
        String tenant = "tenant";
        String groupKeyChanged = GroupKey.getKeyTenant(dataIdChanged, group, tenant);
        Map<String, ConfigListenState> clientMd5Map = new HashMap<>();
        ConfigListenState configListenState = new ConfigListenState("mockMd5");
        clientMd5Map.put(groupKeyChanged, configListenState);
        HttpServletRequest httpServletRequest = Mockito.mock(HttpServletRequest.class);
        HttpServletResponse httpServletResponse = Mockito.mock(HttpServletResponse.class);
        PrintWriter printWriter = Mockito.mock(PrintWriter.class);
        Mockito.when(httpServletResponse.getWriter()).thenReturn(printWriter);
        
        Mockito.when(httpServletRequest.getHeader(eq(LongPollingService.LONG_POLLING_HEADER)))
            .thenReturn("5000");
        Mockito
            .when(
                httpServletRequest.getHeader(eq(LongPollingService.LONG_POLLING_NO_HANG_UP_HEADER)))
            .thenReturn(null);
        String clientIp = "192.168.0.1";
        Mockito.when(httpServletRequest.getHeader(eq("X-Forwarded-For"))).thenReturn(clientIp);
        AsyncContext asyncContext = Mockito.mock(AsyncContext.class);
        Mockito.when(httpServletRequest.startAsync()).thenReturn(asyncContext);
        Mockito.when(asyncContext.getRequest()).thenReturn(httpServletRequest);
        Mockito.when(asyncContext.getResponse()).thenReturn(httpServletResponse);
        
        configCacheServiceMockedStatic.when(
            () -> ConfigCacheService.isUptodate(anyString(), anyString(), anyString(), eq(null)))
            .thenReturn(true);
        
        longPollingService.addLongPollingClient(httpServletRequest, httpServletResponse,
            clientMd5Map, 3);
        
        //test getSubscribleInfo by groupKey
        SampleResult subscribleInfo =
            longPollingService.getCollectSubscribleInfo(dataIdChanged, group, tenant);
        Map<String, String> lisentersGroupkeyStatus = subscribleInfo.getLisentersGroupkeyStatus();
        assertFalse(lisentersGroupkeyStatus.isEmpty());
        assertEquals("mockMd5", lisentersGroupkeyStatus.get(clientIp));
        SampleResult collectSubscribleInfoByIp =
            longPollingService.getCollectSubscribleInfoByIp(clientIp);
        Map<String, String> lisentersGroupkeyStatus1 =
            collectSubscribleInfoByIp.getLisentersGroupkeyStatus();
        assertFalse(lisentersGroupkeyStatus1.isEmpty());
        assertEquals("mockMd5", lisentersGroupkeyStatus1.get(groupKeyChanged));
        
        //test receive config change event
        LocalDataChangeEvent localDataChangeEvent = new LocalDataChangeEvent(groupKeyChanged);
        
        NotifyCenter.publishEvent(localDataChangeEvent);
        Thread.sleep(1100L);
        String responseString = MD5Util
            .compareMd5ResultString(Collections.singletonMap(groupKeyChanged, configListenState));
        //expect print not equals group
        Mockito.verify(printWriter, times(1)).println(eq(responseString));
        Mockito.verify(asyncContext, times(1)).complete();
        
    }
    
    @Test
    void testLongPollingTimeout() throws Exception {
        configExecutorMocked.close();
        String dataIdChanged = "dataIdChanged";
        String group = "group";
        String tenant = "tenant";
        String groupKeyChanged = GroupKey.getKeyTenant(dataIdChanged, group, tenant);
        
        //mock connection no limit
        ConnectionCheckResponse connectionCheckResponse = new ConnectionCheckResponse();
        connectionCheckResponse.setSuccess(true);
        Mockito.when(connectionControlManager.check(any())).thenReturn(connectionCheckResponse);
        
        Map<String, ConfigListenState> clientMd5Map = new HashMap<>();
        ConfigListenState configListenState = new ConfigListenState("md5");
        clientMd5Map.put(groupKeyChanged, configListenState);
        switchServiceMockedStatic
            .when(() -> SwitchService.getSwitchInteger(eq("MIN_LONG_POOLING_TIMEOUT"), eq(10000)))
            .thenReturn(1000);
        HttpServletRequest httpServletRequest = Mockito.mock(HttpServletRequest.class);
        
        Mockito.when(httpServletRequest.getHeader(eq(LongPollingService.LONG_POLLING_HEADER)))
            .thenReturn("1000");
        Mockito
            .when(
                httpServletRequest.getHeader(eq(LongPollingService.LONG_POLLING_NO_HANG_UP_HEADER)))
            .thenReturn(null);
        String clientIp = "192.168.0.1";
        Mockito.when(httpServletRequest.getHeader(eq("X-Forwarded-For"))).thenReturn(clientIp);
        AsyncContext asyncContext = Mockito.mock(AsyncContext.class);
        Mockito.when(httpServletRequest.startAsync()).thenReturn(asyncContext);
        Mockito.when(asyncContext.getRequest()).thenReturn(httpServletRequest);
        
        configCacheServiceMockedStatic.when(
            () -> ConfigCacheService.isUptodate(anyString(), anyString(), anyString(), eq(null)))
            .thenReturn(true);
        HttpServletResponse httpServletResponse = Mockito.mock(HttpServletResponse.class);
        longPollingService.addLongPollingClient(httpServletRequest, httpServletResponse,
            clientMd5Map, 3);
        
        //wait time out condition arrived.
        Thread.sleep(1200L);
        //expect print not equals group
        Mockito.verify(asyncContext, times(1)).complete();
        
    }
    
    @Test
    void testGetSubscribleInfoEmpty() {
        SampleResult result = longPollingService.getSubscribleInfo("d", "g", "t");
        assertEquals(0, result.getLisentersGroupkeyStatus().size());
    }
    
    @Test
    void testGetSubscribleInfoByIpEmpty() {
        SampleResult result = longPollingService.getSubscribleInfoByIp("10.0.0.1");
        assertEquals(0, result.getLisentersGroupkeyStatus().size());
    }
    
    @Test
    void testMergeSampleResult() {
        SampleResult r1 = new SampleResult();
        Map<String, String> map1 = new HashMap<>();
        map1.put("key1", "md5a");
        r1.setLisentersGroupkeyStatus(map1);
        
        SampleResult r2 = new SampleResult();
        Map<String, String> map2 = new HashMap<>();
        map2.put("key2", "md5b");
        r2.setLisentersGroupkeyStatus(map2);
        
        List<SampleResult> list = new ArrayList<>();
        list.add(r1);
        list.add(r2);
        SampleResult merged = longPollingService.mergeSampleResult(list);
        assertEquals(2, merged.getLisentersGroupkeyStatus().size());
    }
    
    @Test
    void testCollectSubscribleInfoWhenInterrupted() {
        Thread.currentThread().interrupt();
        try {
            SampleResult result = longPollingService.getCollectSubscribleInfo("d", "g", "t");
            assertEquals(0, result.getLisentersGroupkeyStatus().size());
        } finally {
            Thread.interrupted();
        }
    }
    
    @Test
    void testCollectSubscribleInfoByIpWhenInterrupted() {
        Thread.currentThread().interrupt();
        try {
            SampleResult result = longPollingService.getCollectSubscribleInfoByIp("10.0.0.1");
            assertEquals(0, result.getLisentersGroupkeyStatus().size());
        } finally {
            Thread.interrupted();
        }
    }
    
    @Test
    void testGetRetainIpsAndSubscriberCount() {
        assertFalse(longPollingService.getRetainIps() == null);
        assertEquals(0, longPollingService.getSubscriberCount());
    }
    
    @Test
    void testIsSupportLongPolling() {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        Mockito.when(request.getHeader(eq(LongPollingService.LONG_POLLING_HEADER)))
            .thenReturn("30000");
        assertEquals(true, LongPollingService.isSupportLongPolling(request));
    }
    
    @Test
    void testIsSupportLongPollingFalse() {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        Mockito.when(request.getHeader(eq(LongPollingService.LONG_POLLING_HEADER)))
            .thenReturn(null);
        assertFalse(LongPollingService.isSupportLongPolling(request));
    }
    
    @Test
    void testDataChangeTaskCatchesException() {
        String groupKey = GroupKey.getKeyTenant("data", "group", "tenant");
        AsyncContext asyncContext = Mockito.mock(AsyncContext.class);
        Mockito.when(asyncContext.getRequest()).thenThrow(new RuntimeException("mock-error"));
        LongPollingService.ClientLongPolling clientLongPolling = newClientLongPolling(asyncContext,
            Collections.singletonMap(groupKey, new ConfigListenState("md5")));
        longPollingService.allSubs.add(clientLongPolling);
        
        longPollingService.new DataChangeTask(groupKey).run();
        
        assertEquals(0, longPollingService.getSubscriberCount());
    }
    
    @Test
    void testClientLongPollingTimeoutWhenRemoveFailed() {
        Runnable[] timeoutRunnable = new Runnable[1];
        ScheduledFuture<?> future = Mockito.mock(ScheduledFuture.class);
        configExecutorMocked.when(() -> ConfigExecutor.scheduleLongPolling(any(Runnable.class),
            anyLong(), any(TimeUnit.class)))
            .thenAnswer(invocation -> {
                timeoutRunnable[0] = invocation.getArgument(0);
                return future;
            });
        LongPollingService.ClientLongPolling clientLongPolling = newClientLongPolling(
            Mockito.mock(AsyncContext.class), Collections.emptyMap());
        
        clientLongPolling.run();
        longPollingService.allSubs.remove(clientLongPolling);
        timeoutRunnable[0].run();
        
        assertEquals(0, longPollingService.getSubscriberCount());
    }
    
    @Test
    void testClientLongPollingTimeoutCatchesException() {
        Runnable[] timeoutRunnable = new Runnable[1];
        ScheduledFuture<?> future = Mockito.mock(ScheduledFuture.class);
        configExecutorMocked.when(() -> ConfigExecutor.scheduleLongPolling(any(Runnable.class),
            anyLong(), any(TimeUnit.class)))
            .thenAnswer(invocation -> {
                timeoutRunnable[0] = invocation.getArgument(0);
                return future;
            });
        AsyncContext asyncContext = Mockito.mock(AsyncContext.class);
        Mockito.when(asyncContext.getRequest()).thenThrow(new RuntimeException("mock-error"));
        LongPollingService.ClientLongPolling clientLongPolling = newClientLongPolling(asyncContext,
            Collections.emptyMap());
        
        clientLongPolling.run();
        timeoutRunnable[0].run();
        
        assertEquals(0, longPollingService.getSubscriberCount());
    }
    
    @Test
    void testClientLongPollingGenerateResponseWhenWriterThrows() throws Exception {
        AsyncContext asyncContext = Mockito.mock(AsyncContext.class);
        HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
        Mockito.when(asyncContext.getResponse()).thenReturn(response);
        Mockito.when(response.getWriter()).thenThrow(new IOException("mock-error"));
        LongPollingService.ClientLongPolling clientLongPolling = newClientLongPolling(asyncContext,
            Collections.emptyMap());
        
        clientLongPolling.generateResponse(
            Collections.singletonMap("data+group+tenant", new ConfigListenState("md5")));
        
        Mockito.verify(asyncContext, times(1)).complete();
    }
    
    @Test
    void testClientLongPollingToString() {
        LongPollingService.ClientLongPolling clientLongPolling = newClientLongPolling(
            Mockito.mock(AsyncContext.class), Collections.emptyMap());
        
        String actual = clientLongPolling.toString();
        
        assertFalse(actual.isEmpty());
    }
    
    @Test
    void testGenerateResponseWithNullChangedGroups() throws Exception {
        HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
        
        longPollingService.generateResponse(Mockito.mock(HttpServletRequest.class), response, null);
        
        Mockito.verify(response, times(0)).getWriter();
    }
    
    @Test
    void testGenerateResponseWhenWriterThrows() throws Exception {
        HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
        Mockito.when(response.getWriter()).thenThrow(new IOException("mock-error"));
        
        longPollingService.generateResponse(Mockito.mock(HttpServletRequest.class), response,
            Collections.singletonMap("data+group+tenant", new ConfigListenState("md5")));
        
        Mockito.verify(response, times(1)).setStatus(HttpServletResponse.SC_OK);
    }
    
    @Test
    void testGenerate503ResponseWhenWriterThrows() throws Exception {
        AsyncContext asyncContext = Mockito.mock(AsyncContext.class);
        HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
        Mockito.when(response.getWriter()).thenThrow(new IOException("mock-error"));
        
        longPollingService.generate503Response(asyncContext, response, "over limit");
        
        Mockito.verify(response, times(1)).setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        Mockito.verify(asyncContext, times(0)).complete();
    }
    
    private LongPollingService.ClientLongPolling newClientLongPolling(AsyncContext asyncContext,
        Map<String, ConfigListenState> clientMd5Map) {
        return longPollingService.new ClientLongPolling(asyncContext, clientMd5Map, "127.0.0.1",
            1, 1L, "app", "tag");
    }
}
