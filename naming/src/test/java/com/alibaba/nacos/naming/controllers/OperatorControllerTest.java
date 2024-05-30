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

package com.alibaba.nacos.naming.controllers;

import com.alibaba.nacos.naming.cluster.ServerStatus;
import com.alibaba.nacos.naming.cluster.ServerStatusManager;
import com.alibaba.nacos.naming.core.DistroMapper;
import com.alibaba.nacos.naming.core.v2.client.Client;
import com.alibaba.nacos.naming.core.v2.client.impl.IpPortBasedClient;
import com.alibaba.nacos.naming.core.v2.client.manager.ClientManager;
import com.alibaba.nacos.naming.core.v2.pojo.InstancePublishInfo;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import com.alibaba.nacos.naming.misc.SwitchManager;
import com.alibaba.nacos.naming.monitor.MetricsMonitor;
import com.alibaba.nacos.sys.env.Constants;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Collection;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * {@link OperatorController} unit test.
 *
 * @author chenglu
 * @date 2021-07-21 19:28
 */
@ExtendWith(MockitoExtension.class)
class OperatorControllerTest {
    
    @InjectMocks
    private OperatorController operatorController;
    
    @Mock
    private SwitchDomain switchDomain;
    
    @Mock
    private SwitchManager switchManager;
    
    @Mock
    private ServerStatusManager serverStatusManager;
    
    @Mock
    private ClientManager clientManager;
    
    @Mock
    private DistroMapper distroMapper;
    
    @BeforeEach
    void setUp() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty(Constants.SUPPORT_UPGRADE_FROM_1X, "true");
        EnvUtil.setEnvironment(environment);
    }
    
    @Test
    void testPushState() {
        MetricsMonitor.resetPush();
        ObjectNode objectNode = operatorController.pushState(true, true);
        assertTrue(objectNode.toString().contains("succeed\":0"));
    }
    
    @Test
    void testSwitchDomain() {
        SwitchDomain switchDomain = operatorController.switches(new MockHttpServletRequest());
        assertEquals(this.switchDomain, switchDomain);
    }
    
    @Test
    void testUpdateSwitch() {
        try {
            String res = operatorController.updateSwitch(true, "test", "test");
            assertEquals("ok", res);
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }
    
    @Test
    void testMetrics() {
        Mockito.when(serverStatusManager.getServerStatus()).thenReturn(ServerStatus.UP);
        Collection<String> clients = new HashSet<>();
        clients.add("1628132208793_127.0.0.1_8080");
        clients.add("127.0.0.1:8081#true");
        clients.add("127.0.0.1:8082#false");
        Mockito.when(clientManager.allClientId()).thenReturn(clients);
        Client client = new IpPortBasedClient("127.0.0.1:8081#true", true);
        client.addServiceInstance(Service.newService("", "", ""), new InstancePublishInfo());
        Mockito.when(clientManager.getClient("127.0.0.1:8081#true")).thenReturn(client);
        Mockito.when(clientManager.isResponsibleClient(client)).thenReturn(Boolean.TRUE);
        
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.addParameter("onlyStatus", "false");
        ObjectNode objectNode = operatorController.metrics(servletRequest);
        
        assertEquals(1, objectNode.get("responsibleInstanceCount").asInt());
        assertEquals(ServerStatus.UP.toString(), objectNode.get("status").asText());
        assertEquals(3, objectNode.get("clientCount").asInt());
        assertEquals(1, objectNode.get("connectionBasedClientCount").asInt());
        assertEquals(1, objectNode.get("ephemeralIpPortClientCount").asInt());
        assertEquals(1, objectNode.get("persistentIpPortClientCount").asInt());
        assertEquals(1, objectNode.get("responsibleClientCount").asInt());
    }
    
    @Test
    void testGetResponsibleServer4Client() {
        Mockito.when(distroMapper.mapSrv(Mockito.anyString())).thenReturn("test");
        ObjectNode objectNode = operatorController.getResponsibleServer4Client("test", "test");
        assertEquals("test", objectNode.get("responsibleServer").asText());
    }
    
    @Test
    void testSetLogLevel() {
        String res = operatorController.setLogLevel("test", "info");
        assertEquals("ok", res);
    }
}
