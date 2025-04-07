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

import com.alibaba.nacos.consistency.cp.CPProtocol;
import com.alibaba.nacos.core.distributed.ProtocolManager;
import com.alibaba.nacos.core.distributed.distro.DistroProtocol;
import com.alibaba.nacos.naming.misc.GlobalConfig;
import com.alibaba.nacos.naming.misc.GlobalExecutor;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServerStatusManagerTest {
    
    @Mock
    SwitchDomain switchDomain;
    
    @Mock
    ProtocolManager protocolManager;
    
    @Mock
    GlobalConfig globalConfig;
    
    @Mock
    DistroProtocol distroProtocol;
    
    @Mock
    CPProtocol cpProtocol;
    
    ServerStatusManager serverStatusManager;
    
    @BeforeEach
    void setUp() {
        EnvUtil.setEnvironment(new MockEnvironment());
        serverStatusManager = new ServerStatusManager(globalConfig, distroProtocol, protocolManager, switchDomain);
    }
    
    @Test
    void testInit() {
        try (MockedStatic mocked = mockStatic(GlobalExecutor.class)) {
            serverStatusManager.init();
            mocked.verify(() -> GlobalExecutor.registerServerStatusUpdater(any()));
        }
    }
    
    @Test
    void testGetServerStatus() {
        ServerStatus serverStatus = serverStatusManager.getServerStatus();
        assertEquals(ServerStatus.STARTING, serverStatus);
        
    }
    
    @Test
    void testGetErrorMsgForDistroProtocol() {
        when(protocolManager.isCpInit()).thenReturn(true);
        when(globalConfig.isDataWarmup()).thenReturn(true);
        when(protocolManager.getCpProtocol()).thenReturn(cpProtocol);
        when(distroProtocol.isInitialized()).thenReturn(false);
        Optional<String> errorMsg = serverStatusManager.getErrorMsg();
        assertTrue(errorMsg.isPresent());
        assertTrue(errorMsg.get().contains("distro"));
    }
    
    @Test
    void testGetErrorMsgForRaft() {
        when(protocolManager.isCpInit()).thenReturn(true);
        when(globalConfig.isDataWarmup()).thenReturn(true);
        when(protocolManager.getCpProtocol()).thenReturn(cpProtocol);
        when(cpProtocol.isReady()).thenReturn(false);
        when(distroProtocol.isInitialized()).thenReturn(true);
        Optional<String> errorMsg = serverStatusManager.getErrorMsg();
        assertTrue(errorMsg.isPresent());
        assertTrue(errorMsg.get().contains("raft"));
    }
    
    @Test
    void testUpdaterFromSwitch() {
        String expect = ServerStatus.DOWN.toString();
        when(switchDomain.getOverriddenServerStatus()).thenReturn(expect);
        ServerStatusManager.ServerStatusUpdater updater = serverStatusManager.new ServerStatusUpdater();
        //then
        updater.run();
        //then
        ServerStatus serverStatus = serverStatusManager.getServerStatus();
        assertEquals(expect, serverStatus.toString());
    }
    
    @Test
    void testUpdaterStatusForWarmUpDisabled() {
        ServerStatusManager.ServerStatusUpdater updater = serverStatusManager.new ServerStatusUpdater();
        updater.run();
        assertEquals(ServerStatus.UP, serverStatusManager.getServerStatus());
        assertFalse(serverStatusManager.getErrorMsg().isPresent());
    }
    
    @Test
    void testUpdaterStatusBySwitch() {
        when(switchDomain.getOverriddenServerStatus()).thenReturn("UP");
        ServerStatusManager.ServerStatusUpdater updater = serverStatusManager.new ServerStatusUpdater();
        updater.run();
        assertEquals(ServerStatus.UP, serverStatusManager.getServerStatus());
        when(switchDomain.getOverriddenServerStatus()).thenReturn("DOWN");
        updater.run();
        assertEquals(ServerStatus.DOWN, serverStatusManager.getServerStatus());
    }
    
    @Test
    void testUpdaterStatusForDistroFailed() {
        when(protocolManager.isCpInit()).thenReturn(true);
        when(globalConfig.isDataWarmup()).thenReturn(true);
        when(protocolManager.getCpProtocol()).thenReturn(cpProtocol);
        ServerStatusManager.ServerStatusUpdater updater = serverStatusManager.new ServerStatusUpdater();
        updater.run();
        assertEquals(ServerStatus.DOWN, serverStatusManager.getServerStatus());
    }
    
    @Test
    void testUpdaterStatusForRaftFailed() {
        when(protocolManager.isCpInit()).thenReturn(true);
        when(globalConfig.isDataWarmup()).thenReturn(true);
        ServerStatusManager.ServerStatusUpdater updater = serverStatusManager.new ServerStatusUpdater();
        updater.run();
        assertEquals(ServerStatus.DOWN, serverStatusManager.getServerStatus());
        when(protocolManager.getCpProtocol()).thenReturn(cpProtocol);
        when(cpProtocol.isReady()).thenReturn(false);
        updater.run();
        assertEquals(ServerStatus.DOWN, serverStatusManager.getServerStatus());
    }
    
    @Test
    void testUpdaterStatus() {
        when(protocolManager.isCpInit()).thenReturn(true);
        when(globalConfig.isDataWarmup()).thenReturn(true);
        when(protocolManager.getCpProtocol()).thenReturn(cpProtocol);
        when(cpProtocol.isReady()).thenReturn(true);
        when(distroProtocol.isInitialized()).thenReturn(true);
        ServerStatusManager.ServerStatusUpdater updater = serverStatusManager.new ServerStatusUpdater();
        updater.run();
        assertEquals(ServerStatus.UP, serverStatusManager.getServerStatus());
    }
}