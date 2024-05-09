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
import com.alibaba.nacos.plugin.control.configs.ControlConfigs;
import com.alibaba.nacos.sys.env.EnvUtil;
import io.grpc.netty.shaded.io.netty.channel.Channel;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.mock.env.MockEnvironment;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * {@link ConnectionManager} unit test.
 *
 * @author chenglu
 * @date 2021-07-02 14:57
 */
@RunWith(MockitoJUnitRunner.class)
public class ConnectionManagerTest {
    
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
    
    static MockedStatic<ControlConfigs> propertyUtilMockedStatic;
    
    @BeforeClass
    public static void setUpClass() {
        propertyUtilMockedStatic = Mockito.mockStatic(ControlConfigs.class);
        propertyUtilMockedStatic.when(ControlConfigs::getInstance).thenReturn(new ControlConfigs());
        
    }
    
    @AfterClass
    public static void afterClass() {
        if (propertyUtilMockedStatic != null) {
            propertyUtilMockedStatic.close();
        }
    }
    
    @Before
    public void setUp() {
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
    
    @After
    public void tearDown() {
        connectionManager.unregister(connectId);
        
    }
    
    @Test
    public void testCheckValid() {
        Assert.assertTrue(connectionManager.checkValid(connectId));
    }
    
    @Test
    public void testTraced() {
        Assert.assertFalse(connectionManager.traced(clientIp));
    }
    
    @Test
    public void testGetConnection() {
        Assert.assertEquals(connection, connectionManager.getConnection(connectId));
    }
    
    @Test
    public void testGetConnectionsByClientIp() {
        Assert.assertEquals(1, connectionManager.getConnectionByIp(clientIp).size());
    }
    
    @Test
    public void testGetCurrentConnectionCount() {
        Assert.assertEquals(1, connectionManager.getCurrentConnectionCount());
    }
    
    @Test
    public void testRefreshActiveTime() {
        try {
            connectionManager.refreshActiveTime(connectId);
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }
    }
    
    @Test
    public void testLoadSingle() throws NacosException {
        Mockito.when(connectionMeta.isSdkSource()).thenReturn(true);
        connectionManager.loadSingle(connectId, clientIp);
    }
    
    @Test
    public void testCurrentClientsCount() {
        Map<String, String> labels = new HashMap<>();
        labels.put("key", "value");
        Assert.assertEquals(1, connectionManager.currentClientsCount(labels));
    }
    
    @Test
    public void testCurrentSdkCount() {
        Assert.assertEquals(1, connectionManager.currentSdkClientCount());
    }
    
}

