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
import com.alibaba.nacos.naming.constants.Constants;
import com.alibaba.nacos.naming.misc.GlobalExecutor;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alipay.sofa.jraft.RouteTable;
import com.alipay.sofa.jraft.entity.PeerId;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServerStatusManagerTest {
    
    @Mock
    SwitchDomain switchDomain;
    
    @Mock
    ProtocolManager protocolManager;
    
    @Mock
    CPProtocol cpProtocol;
    
    @BeforeEach
    void setUp() {
        EnvUtil.setEnvironment(new MockEnvironment());
    }
    
    @Test
    void testInit() {
        try (MockedStatic mocked = mockStatic(GlobalExecutor.class)) {
            ServerStatusManager serverStatusManager = new ServerStatusManager(protocolManager, switchDomain);
            serverStatusManager.init();
            mocked.verify(() -> GlobalExecutor.registerServerStatusUpdater(any()));
        }
    }
    
    @Test
    void testGetServerStatus() {
        ServerStatusManager serverStatusManager = new ServerStatusManager(protocolManager, switchDomain);
        ServerStatus serverStatus = serverStatusManager.getServerStatus();
        assertEquals(ServerStatus.STARTING, serverStatus);
        
    }
    
    @Test
    void testGetErrorMsg() {
        ServerStatusManager serverStatusManager = new ServerStatusManager(protocolManager, switchDomain);
        Optional<String> errorMsg = serverStatusManager.getErrorMsg();
        assertTrue(errorMsg.isPresent());
    }
    
    @Test
    void testUpdaterFromSwitch() {
        String expect = ServerStatus.DOWN.toString();
        when(switchDomain.getOverriddenServerStatus()).thenReturn(expect);
        ServerStatusManager serverStatusManager = new ServerStatusManager(protocolManager, switchDomain);
        ServerStatusManager.ServerStatusUpdater updater = serverStatusManager.new ServerStatusUpdater();
        //then
        updater.run();
        //then
        ServerStatus serverStatus = serverStatusManager.getServerStatus();
        assertEquals(expect, serverStatus.toString());
    }
    
    @Test
    void testUpdaterFromConsistency1() {
        try (MockedStatic mocked = mockStatic(RouteTable.class)) {
            RouteTable mockTable = mock(RouteTable.class);
            when(mockTable.selectLeader(Constants.NAMING_PERSISTENT_SERVICE_GROUP)).thenReturn(new PeerId());
            mocked.when(RouteTable::getInstance).thenReturn(mockTable);
            when(protocolManager.getCpProtocol()).thenReturn(cpProtocol);
            ServerStatusManager serverStatusManager = new ServerStatusManager(protocolManager, switchDomain);
            ServerStatusManager.ServerStatusUpdater updater = serverStatusManager.new ServerStatusUpdater();
            //then
            updater.run();
            //then
            assertEquals(ServerStatus.UP, serverStatusManager.getServerStatus());
            assertFalse(serverStatusManager.getErrorMsg().isPresent());
        }
    }
    
    @Test
    void testUpdaterFromConsistency2() {
        when(protocolManager.getCpProtocol()).thenReturn(cpProtocol);
        ServerStatusManager serverStatusManager = new ServerStatusManager(protocolManager, switchDomain);
        ServerStatusManager.ServerStatusUpdater updater = serverStatusManager.new ServerStatusUpdater();
        //then
        updater.run();
        //then
        assertEquals(ServerStatus.DOWN, serverStatusManager.getServerStatus());
    }
    
    @Test
    void testUpdaterFromConsistency3() {
        ServerStatusManager serverStatusManager = new ServerStatusManager(protocolManager, switchDomain);
        ServerStatusManager.ServerStatusUpdater updater = serverStatusManager.new ServerStatusUpdater();
        //then
        updater.run();
        //then
        assertEquals(ServerStatus.DOWN, serverStatusManager.getServerStatus());
    }
}