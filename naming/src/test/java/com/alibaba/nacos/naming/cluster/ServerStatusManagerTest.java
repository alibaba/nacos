/*
 *
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
 *
 */

package com.alibaba.nacos.naming.cluster;

import com.alibaba.nacos.naming.consistency.ConsistencyService;
import com.alibaba.nacos.naming.misc.GlobalExecutor;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.env.MockEnvironment;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class ServerStatusManagerTest {
    
    @BeforeEach
    void setUp() {
        EnvUtil.setEnvironment(new MockEnvironment());
    }
    
    @Test
    void testInit() {
        try (MockedStatic mocked = mockStatic(GlobalExecutor.class)) {
            ServerStatusManager serverStatusManager = new ServerStatusManager(mock(SwitchDomain.class));
            serverStatusManager.init();
            mocked.verify(() -> GlobalExecutor.registerServerStatusUpdater(any()));
        }
    }
    
    @Test
    void testGetServerStatus() {
        ServerStatusManager serverStatusManager = new ServerStatusManager(mock(SwitchDomain.class));
        ServerStatus serverStatus = serverStatusManager.getServerStatus();
        assertEquals(ServerStatus.STARTING, serverStatus);
        
    }
    
    @Test
    void testGetErrorMsg() throws NoSuchFieldException, IllegalAccessException {
        ServerStatusManager serverStatusManager = new ServerStatusManager(mock(SwitchDomain.class));
        Field field = ServerStatusManager.class.getDeclaredField("consistencyService");
        field.setAccessible(true);
        ConsistencyService consistencyService = mock(ConsistencyService.class);
        when(consistencyService.getErrorMsg()).thenReturn(Optional.empty());
        field.set(serverStatusManager, consistencyService);
        Optional<String> errorMsg = serverStatusManager.getErrorMsg();
        assertFalse(errorMsg.isPresent());
    }
    
    @Test
    void testUpdaterFromSwitch() {
        SwitchDomain switchDomain = mock(SwitchDomain.class);
        String expect = ServerStatus.DOWN.toString();
        when(switchDomain.getOverriddenServerStatus()).thenReturn(expect);
        ServerStatusManager serverStatusManager = new ServerStatusManager(switchDomain);
        ServerStatusManager.ServerStatusUpdater updater = serverStatusManager.new ServerStatusUpdater();
        //then
        updater.run();
        //then
        ServerStatus serverStatus = serverStatusManager.getServerStatus();
        assertEquals(expect, serverStatus.toString());
    }
    
    @Test
    void testUpdaterFromConsistency1() throws NoSuchFieldException, IllegalAccessException {
        SwitchDomain switchDomain = mock(SwitchDomain.class);
        ServerStatusManager serverStatusManager = new ServerStatusManager(switchDomain);
        Field field = ServerStatusManager.class.getDeclaredField("consistencyService");
        field.setAccessible(true);
        ConsistencyService consistencyService = mock(ConsistencyService.class);
        when(consistencyService.isAvailable()).thenReturn(true);
        field.set(serverStatusManager, consistencyService);
        ServerStatusManager.ServerStatusUpdater updater = serverStatusManager.new ServerStatusUpdater();
        //then
        updater.run();
        //then
        assertEquals(ServerStatus.UP, serverStatusManager.getServerStatus());
    }
    
    @Test
    void testUpdaterFromConsistency2() throws NoSuchFieldException, IllegalAccessException {
        SwitchDomain switchDomain = mock(SwitchDomain.class);
        ServerStatusManager serverStatusManager = new ServerStatusManager(switchDomain);
        Field field = ServerStatusManager.class.getDeclaredField("consistencyService");
        field.setAccessible(true);
        ConsistencyService consistencyService = mock(ConsistencyService.class);
        when(consistencyService.isAvailable()).thenReturn(false);
        field.set(serverStatusManager, consistencyService);
        ServerStatusManager.ServerStatusUpdater updater = serverStatusManager.new ServerStatusUpdater();
        //then
        updater.run();
        //then
        assertEquals(ServerStatus.DOWN, serverStatusManager.getServerStatus());
    }
}