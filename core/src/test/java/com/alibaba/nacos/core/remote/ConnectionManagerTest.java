/*
 *  Copyright 1999-2021 Alibaba Group Holding Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package com.alibaba.nacos.core.remote;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.RemoteConstants;
import com.alibaba.nacos.core.remote.grpc.GrpcConnection;
import com.alibaba.nacos.plugin.control.ControlManagerCenter;
import com.alibaba.nacos.plugin.control.configs.ControlConfigs;
import com.alibaba.nacos.plugin.control.connection.ConnectionControlManager;
import com.alibaba.nacos.plugin.control.connection.request.ConnectionCheckRequest;
import com.alibaba.nacos.plugin.control.connection.response.ConnectionCheckResponse;
import com.alibaba.nacos.plugin.control.connection.rule.ConnectionControlRule;
import com.alibaba.nacos.sys.env.EnvUtil;
import io.grpc.netty.shaded.io.netty.channel.Channel;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * {@link ConnectionManager} unit test.
 *
 * @author chenglu
 * @date 2021-07-02 14:57
 */
@ExtendWith(MockitoExtension.class)
class ConnectionManagerTest {
    
    static MockedStatic<ControlConfigs> propertyUtilMockedStatic;
    
    @InjectMocks
    private ConnectionManager connectionManager;
    
    @Mock
    private ClientConnectionEventListenerRegistry clientConnectionEventListenerRegistry;
    
    @InjectMocks
    private GrpcConnection connection;
    
    @Mock
    private Channel channel;
    
    @Mock
    private ConnectionMeta connectionMeta;
    
    private String connectId;
    
    private String clientIp = "1.1.1.1";
    
    @BeforeAll
    static void setUpClass() {
        propertyUtilMockedStatic = Mockito.mockStatic(ControlConfigs.class);
        propertyUtilMockedStatic.when(ControlConfigs::getInstance).thenReturn(new ControlConfigs());
        
    }
    
    @AfterAll
    static void afterClass() {
        if (propertyUtilMockedStatic != null) {
            propertyUtilMockedStatic.close();
        }
    }
    
    @BeforeEach
    void setUp() {
        EnvUtil.setEnvironment(new MockEnvironment());
        // create base file path
        File baseDir = new File(EnvUtil.getNacosHome(), "data");
        if (!baseDir.exists()) {
            baseDir.mkdirs();
        }
        connectId = UUID.randomUUID().toString();
        connectionManager.start();
        Mockito.when(channel.isOpen()).thenReturn(true);
        Mockito.when(channel.isActive()).thenReturn(true);
        
        connectionMeta.clientIp = clientIp;
        Map<String, String> labels = new HashMap<>();
        labels.put("key", "value");
        labels.put(RemoteConstants.LABEL_SOURCE, RemoteConstants.LABEL_SOURCE_SDK);
        connectionMeta.labels = labels;
        connectionManager.loadCount(1, clientIp);
        
        connectionManager.register(connectId, connection);
    }
    
    @AfterEach
    void tearDown() {
        connectionManager.unregister(connectId);
        
    }
    
    @Test
    void testCheckValid() {
        assertTrue(connectionManager.checkValid(connectId));
    }
    
    @Test
    void testTraced() {
        assertFalse(connectionManager.traced(clientIp));
    }
    
    @Test
    void testGetConnection() {
        assertEquals(connection, connectionManager.getConnection(connectId));
    }
    
    @Test
    void testGetConnectionsByClientIp() {
        assertEquals(1, connectionManager.getConnectionByIp(clientIp).size());
    }
    
    @Test
    void testGetCurrentConnectionCount() {
        assertEquals(1, connectionManager.getCurrentConnectionCount());
    }
    
    @Test
    void testRefreshActiveTime() {
        try {
            connectionManager.refreshActiveTime(connectId);
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }
    
    @Test
    void testLoadSingle() throws NacosException {
        Mockito.when(connectionMeta.isSdkSource()).thenReturn(true);
        connectionManager.loadSingle(connectId, clientIp);
    }
    
    @Test
    void testCurrentClientsCount() {
        Map<String, String> labels = new HashMap<>();
        labels.put("key", "value");
        assertEquals(1, connectionManager.currentClientsCount(labels));
    }
    
    @Test
    void testCurrentSdkCount() {
        assertEquals(1, connectionManager.currentSdkClientCount());
    }
    
    @Test
    void testRegisterWhenAlreadyContainedReturnsTrue() {
        boolean again = connectionManager.register(connectId, connection);
        assertTrue(again);
    }
    
    @Test
    void testGetConnectionForClientIpAndCurrentClients() {
        assertNotNull(connectionManager.getConnectionForClientIp());
        assertEquals(1, connectionManager.getConnectionForClientIp().get(clientIp).get());
        assertTrue(connectionManager.currentClients().containsKey(connectId));
    }
    
    @Test
    void testLoadSingleWithRedirectAddressContainingColon() throws NacosException {
        Mockito.when(connectionMeta.isSdkSource()).thenReturn(true);
        connectionManager.loadSingle(connectId, "127.0.0.1:8848");
    }
    
    @Test
    void testUnregisterRemovesFromConnectionForClientIp() {
        connectionManager.unregister(connectId);
        assertFalse(connectionManager.getConnectionForClientIp().containsKey(clientIp));
    }
    
    @Test
    void testTracedReturnsTrueWhenIpInMonitorList() {
        try (MockedStatic<ControlManagerCenter> cmMock =
            Mockito.mockStatic(ControlManagerCenter.class)) {
            ConnectionControlManager connectionControlManager =
                Mockito.mock(ConnectionControlManager.class);
            ConnectionControlRule rule = new ConnectionControlRule();
            rule.setMonitorIpList(Set.of(clientIp));
            when(connectionControlManager.getConnectionLimitRule()).thenReturn(rule);
            ControlManagerCenter center = Mockito.mock(ControlManagerCenter.class);
            when(center.getConnectionControlManager()).thenReturn(connectionControlManager);
            cmMock.when(ControlManagerCenter::getInstance).thenReturn(center);
            assertTrue(connectionManager.traced(clientIp));
        }
    }
    
    @Test
    void testRegisterReturnsFalseWhenCheckLimitFails() {
        String connectionId2 = UUID.randomUUID().toString();
        try (MockedStatic<ControlManagerCenter> cmMock =
            Mockito.mockStatic(ControlManagerCenter.class)) {
            ConnectionControlManager connectionControlManager =
                Mockito.mock(ConnectionControlManager.class);
            ConnectionCheckResponse checkResponse = new ConnectionCheckResponse();
            checkResponse.setSuccess(false);
            when(connectionControlManager.check(any(ConnectionCheckRequest.class)))
                .thenReturn(checkResponse);
            ControlManagerCenter center = Mockito.mock(ControlManagerCenter.class);
            when(center.getConnectionControlManager()).thenReturn(connectionControlManager);
            cmMock.when(ControlManagerCenter::getInstance).thenReturn(center);
            assertFalse(connectionManager.register(connectionId2, connection));
        }
    }
    
    @Test
    void testLoadSingleWhenConnectionNullReturnsTrue() {
        boolean result = connectionManager.loadSingle("absent-conn-id", "127.0.0.1:8848");
        assertTrue(result);
    }
    
    @Test
    void testLoadSingleWhenNotSdkSourceReturnsTrue() {
        Mockito.when(connectionMeta.isSdkSource()).thenReturn(false);
        boolean result = connectionManager.loadSingle(connectId, "127.0.0.1:8848");
        assertTrue(result);
    }
    
    @Test
    void testLoadSingleWithBlankRedirectAddress() {
        Mockito.when(connectionMeta.isSdkSource()).thenReturn(true);
        boolean result = connectionManager.loadSingle(connectId, "");
        assertFalse(result);
    }
    
    @Test
    void testCurrentClientsCountWithFilterLabelsMismatch() {
        Map<String, String> filterLabels = new HashMap<>();
        filterLabels.put("key", "other-value");
        assertEquals(0, connectionManager.currentClientsCount(filterLabels));
    }
    
    @Test
    void testGetConnectionWhenAbsentReturnsNull() {
        assertNotNull(connectionManager.getConnection(connectId));
        assertNull(connectionManager.getConnection("absent-id"));
    }
    
    @Test
    void testUnregisterNotifiesClientDisConnected() {
        connectionManager.unregister(connectId);
        Mockito.verify(clientConnectionEventListenerRegistry).notifyClientDisConnected(connection);
    }
}
