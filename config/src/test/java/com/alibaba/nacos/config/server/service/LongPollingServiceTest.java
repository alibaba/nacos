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
import com.alibaba.nacos.config.server.model.SampleResult;
import com.alibaba.nacos.config.server.model.event.LocalDataChangeEvent;
import com.alibaba.nacos.config.server.utils.ConfigExecutor;
import com.alibaba.nacos.config.server.utils.GroupKey;
import com.alibaba.nacos.config.server.utils.MD5Util;
import com.alibaba.nacos.plugin.control.ControlManagerCenter;
import com.alibaba.nacos.plugin.control.connection.ConnectionControlManager;
import com.alibaba.nacos.plugin.control.connection.response.ConnectionCheckResponse;
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

import javax.servlet.AsyncContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
    
    @BeforeEach
    void before() {
        longPollingService = new LongPollingService();
        switchServiceMockedStatic = Mockito.mockStatic(SwitchService.class);
        configCacheServiceMockedStatic = Mockito.mockStatic(ConfigCacheService.class);
        configExecutorMocked = Mockito.mockStatic(ConfigExecutor.class);
        connectionControlManagerMockedStatic = Mockito.mockStatic(ControlManagerCenter.class);
        connectionControlManagerMockedStatic.when(() -> ControlManagerCenter.getInstance()).thenReturn(controlManagerCenter);
        Mockito.when(controlManagerCenter.getConnectionControlManager()).thenReturn(connectionControlManager);
        
    }
    
    @AfterEach
    void after() {
        configCacheServiceMockedStatic.close();
        if (!configExecutorMocked.isClosed()) {
            configExecutorMocked.close();
        }
        connectionControlManagerMockedStatic.close();
        switchServiceMockedStatic.close();
    }
    
    @Test
    void testAddLongPollingClientHasNotEqualsMd5() throws IOException {
        
        Map<String, String> clientMd5Map = new HashMap<>();
        String group = "group";
        String tenant = "tenat";
        String dataIdEquals = "dataIdEquals0";
        String dataIdNotEquals = "dataIdNotEquals0";
        String groupKeyEquals = GroupKey.getKeyTenant(dataIdEquals, group, tenant);
        String groupKeyNotEquals = GroupKey.getKeyTenant(dataIdNotEquals, group, tenant);
        String md5Equals0 = MD5Utils.md5Hex("countEquals0", "UTF-8");
        clientMd5Map.put(groupKeyEquals, md5Equals0);
        String md5NotEquals1 = MD5Utils.md5Hex("countNotEquals", "UTF-8");
        clientMd5Map.put(groupKeyNotEquals, md5NotEquals1);
        
        HttpServletRequest httpServletRequest = Mockito.mock(HttpServletRequest.class);
        Mockito.when(httpServletRequest.getHeader(eq(LongPollingService.LONG_POLLING_NO_HANG_UP_HEADER))).thenReturn(null);
        String clientIp = "192.168.0.1";
        Mockito.when(httpServletRequest.getHeader(eq("X-Forwarded-For"))).thenReturn(clientIp);
        
        configCacheServiceMockedStatic.when(
                () -> ConfigCacheService.isUptodate(eq(groupKeyNotEquals), eq(md5NotEquals1), eq(clientIp), eq(null))).thenReturn(false);
        configCacheServiceMockedStatic.when(() -> ConfigCacheService.isUptodate(eq(groupKeyEquals), eq(md5Equals0), eq(clientIp), eq(null)))
                .thenReturn(true);
        HttpServletResponse httpServletResponse = Mockito.mock(HttpServletResponse.class);
        PrintWriter printWriter = Mockito.mock(PrintWriter.class);
        Mockito.when(httpServletResponse.getWriter()).thenReturn(printWriter);
        int propSize = 3;
        longPollingService.addLongPollingClient(httpServletRequest, httpServletResponse, clientMd5Map, propSize);
        
        String responseString = MD5Util.compareMd5ResultString(Arrays.asList(groupKeyNotEquals));
        //expect print not equals group
        Mockito.verify(printWriter, times(1)).println(eq(responseString));
        Mockito.verify(httpServletResponse, times(1)).setStatus(eq(HttpServletResponse.SC_OK));
        
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
        Mockito.when(httpServletRequest.getHeader(eq(LongPollingService.LONG_POLLING_NO_HANG_UP_HEADER))).thenReturn(null);
        String clientIp = "192.168.0.1";
        Mockito.when(httpServletRequest.getHeader(eq("X-Forwarded-For"))).thenReturn(clientIp);
        Mockito.when(httpServletRequest.startAsync()).thenReturn(Mockito.mock(AsyncContext.class));
        int propSize = 3;
        Map<String, String> clientMd5Map = new HashMap<>();
        longPollingService.addLongPollingClient(httpServletRequest, httpServletResponse, clientMd5Map, propSize);
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
        
        Map<String, String> clientMd5Map = new HashMap<>();
        String group = "group";
        String tenant = "tenat";
        String dataIdEquals = "dataIdEquals01";
        String dataIdNotEquals = "dataIdNotEquals01";
        String groupKeyEquals = GroupKey.getKeyTenant(dataIdEquals, group, tenant);
        String groupKeyNotEquals = GroupKey.getKeyTenant(dataIdNotEquals, group, tenant);
        String md5Equals0 = MD5Utils.md5Hex("countEquals01", "UTF-8");
        clientMd5Map.put(groupKeyEquals, md5Equals0);
        String md5NotEquals1 = MD5Utils.md5Hex("countNotEquals1", "UTF-8");
        clientMd5Map.put(groupKeyNotEquals, md5NotEquals1);
        HttpServletRequest httpServletRequest = Mockito.mock(HttpServletRequest.class);
        
        Mockito.when(httpServletRequest.getHeader(eq(LongPollingService.LONG_POLLING_HEADER))).thenReturn("5000");
        Mockito.when(httpServletRequest.getHeader(eq(LongPollingService.LONG_POLLING_NO_HANG_UP_HEADER))).thenReturn(null);
        String clientIp = "192.168.0.1";
        Mockito.when(httpServletRequest.getHeader(eq("X-Forwarded-For"))).thenReturn(clientIp);
        Mockito.when(httpServletRequest.startAsync()).thenReturn(Mockito.mock(AsyncContext.class));
        configCacheServiceMockedStatic.when(
                () -> ConfigCacheService.isUptodate(eq(groupKeyNotEquals), eq(md5NotEquals1), eq(clientIp), eq(null))).thenReturn(true);
        configCacheServiceMockedStatic.when(() -> ConfigCacheService.isUptodate(eq(groupKeyEquals), eq(md5Equals0), eq(clientIp), eq(null)))
                .thenReturn(true);
        int propSize = 3;
        HttpServletResponse httpServletResponse = Mockito.mock(HttpServletResponse.class);
        longPollingService.addLongPollingClient(httpServletRequest, httpServletResponse, clientMd5Map, propSize);
        
        //expect response not returned
        Mockito.verify(httpServletResponse, times(0)).setStatus(anyInt());
        //expect to schedule a task
        configExecutorMocked.verify(() -> ConfigExecutor.executeLongPolling(any(LongPollingService.ClientLongPolling.class)), times(1));
        
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
        Map<String, String> clientMd5Map = new HashMap<>();
        clientMd5Map.put(groupKeyChanged, "mockMd5");
        HttpServletRequest httpServletRequest = Mockito.mock(HttpServletRequest.class);
        HttpServletResponse httpServletResponse = Mockito.mock(HttpServletResponse.class);
        PrintWriter printWriter = Mockito.mock(PrintWriter.class);
        Mockito.when(httpServletResponse.getWriter()).thenReturn(printWriter);
        
        Mockito.when(httpServletRequest.getHeader(eq(LongPollingService.LONG_POLLING_HEADER))).thenReturn("5000");
        Mockito.when(httpServletRequest.getHeader(eq(LongPollingService.LONG_POLLING_NO_HANG_UP_HEADER))).thenReturn(null);
        String clientIp = "192.168.0.1";
        Mockito.when(httpServletRequest.getHeader(eq("X-Forwarded-For"))).thenReturn(clientIp);
        AsyncContext asyncContext = Mockito.mock(AsyncContext.class);
        Mockito.when(httpServletRequest.startAsync()).thenReturn(asyncContext);
        Mockito.when(asyncContext.getRequest()).thenReturn(httpServletRequest);
        Mockito.when(asyncContext.getResponse()).thenReturn(httpServletResponse);
        
        configCacheServiceMockedStatic.when(() -> ConfigCacheService.isUptodate(anyString(), anyString(), anyString(), eq(null)))
                .thenReturn(true);
        
        longPollingService.addLongPollingClient(httpServletRequest, httpServletResponse, clientMd5Map, 3);
        
        //test getSubscribleInfo by groupKey
        SampleResult subscribleInfo = longPollingService.getCollectSubscribleInfo(dataIdChanged, group, tenant);
        Map<String, String> lisentersGroupkeyStatus = subscribleInfo.getLisentersGroupkeyStatus();
        assertFalse(lisentersGroupkeyStatus.isEmpty());
        assertEquals("mockMd5", lisentersGroupkeyStatus.get(clientIp));
        SampleResult collectSubscribleInfoByIp = longPollingService.getCollectSubscribleInfoByIp(clientIp);
        Map<String, String> lisentersGroupkeyStatus1 = collectSubscribleInfoByIp.getLisentersGroupkeyStatus();
        assertFalse(lisentersGroupkeyStatus1.isEmpty());
        assertEquals("mockMd5", lisentersGroupkeyStatus1.get(groupKeyChanged));
        
        //test receive config change event
        LocalDataChangeEvent localDataChangeEvent = new LocalDataChangeEvent(groupKeyChanged);
        
        NotifyCenter.publishEvent(localDataChangeEvent);
        Thread.sleep(1100L);
        String responseString = MD5Util.compareMd5ResultString(Arrays.asList(groupKeyChanged));
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
        
        Map<String, String> clientMd5Map = new HashMap<>();
        clientMd5Map.put(groupKeyChanged, "md5");
        switchServiceMockedStatic.when(() -> SwitchService.getSwitchInteger(eq("MIN_LONG_POOLING_TIMEOUT"), eq(10000))).thenReturn(1000);
        HttpServletRequest httpServletRequest = Mockito.mock(HttpServletRequest.class);
        
        Mockito.when(httpServletRequest.getHeader(eq(LongPollingService.LONG_POLLING_HEADER))).thenReturn("1000");
        Mockito.when(httpServletRequest.getHeader(eq(LongPollingService.LONG_POLLING_NO_HANG_UP_HEADER))).thenReturn(null);
        String clientIp = "192.168.0.1";
        Mockito.when(httpServletRequest.getHeader(eq("X-Forwarded-For"))).thenReturn(clientIp);
        AsyncContext asyncContext = Mockito.mock(AsyncContext.class);
        Mockito.when(httpServletRequest.startAsync()).thenReturn(asyncContext);
        Mockito.when(asyncContext.getRequest()).thenReturn(httpServletRequest);
        
        configCacheServiceMockedStatic.when(() -> ConfigCacheService.isUptodate(anyString(), anyString(), anyString(), eq(null)))
                .thenReturn(true);
        HttpServletResponse httpServletResponse = Mockito.mock(HttpServletResponse.class);
        longPollingService.addLongPollingClient(httpServletRequest, httpServletResponse, clientMd5Map, 3);
        
        //wait time out condition arrived.
        Thread.sleep(1200L);
        //expect print not equals group
        Mockito.verify(asyncContext, times(1)).complete();
        
    }
}
