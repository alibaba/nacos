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

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.naming.cluster.ServerStatus;
import com.alibaba.nacos.naming.cluster.ServerStatusManager;
import com.alibaba.nacos.naming.consistency.persistent.raft.RaftCore;
import com.alibaba.nacos.naming.core.DistroMapper;
import com.alibaba.nacos.naming.core.Service;
import com.alibaba.nacos.naming.core.ServiceManager;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import com.alibaba.nacos.naming.misc.SwitchManager;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * {@link OperatorController} unit test.
 *
 * @author chenglu
 * @date 2021-07-21 19:28
 */
@RunWith(MockitoJUnitRunner.class)
public class OperatorControllerTest {
    
    @InjectMocks
    private OperatorController operatorController;
    
    @Mock
    private SwitchDomain switchDomain;
    
    @Mock
    private SwitchManager switchManager;
    
    @Mock
    private ServerStatusManager serverStatusManager;
    
    @Mock
    private ServiceManager serviceManager;
    
    @Mock
    private RaftCore raftCore;
    
    @Mock
    private DistroMapper distroMapper;
    
    @Test
    public void testPushState() {
        ObjectNode objectNode = operatorController.pushState(true, true);
        Assert.assertTrue(objectNode.toString().contains("succeed\":0"));
    }
    
    @Test
    public void testSwitchDomain() {
        SwitchDomain switchDomain = operatorController.switches(new MockHttpServletRequest());
        Assert.assertEquals(this.switchDomain, switchDomain);
    }
    
    @Test
    public void testUpdateSwitch() {
        try {
            String res = operatorController.updateSwitch(true, "test", "test");
            Assert.assertEquals("ok", res);
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }
    }
    
    @Test
    public void testMetrics() {
        Mockito.when(serverStatusManager.getServerStatus()).thenReturn(ServerStatus.UP);
        Mockito.when(serviceManager.getResponsibleServiceCount()).thenReturn(1);
        Mockito.when(serviceManager.getResponsibleInstanceCount()).thenReturn(1);
        Mockito.when(raftCore.getNotifyTaskCount()).thenReturn(1);
        
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.addParameter("onlyStatus", "false");
        ObjectNode objectNode = operatorController.metrics(servletRequest);
        
        Assert.assertEquals(1, objectNode.get("responsibleServiceCount").asInt());
        Assert.assertEquals(1, objectNode.get("responsibleInstanceCount").asInt());
        Assert.assertEquals(ServerStatus.UP.toString(), objectNode.get("status").asText());
    }
    
    @Test
    public void testGetResponsibleServer4Service() {
        try {
            Mockito.when(serviceManager.getService(Mockito.anyString(), Mockito.anyString())).thenReturn(new Service());
            Mockito.when(distroMapper.mapSrv(Mockito.anyString())).thenReturn("test");
            
            ObjectNode objectNode = operatorController.getResponsibleServer4Service("test", "test");
            
            Assert.assertEquals("test", objectNode.get("responsibleServer").asText());
        } catch (NacosException e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }
    }
    
    @Test
    public void testGetResponsibleServer4Client() {
        Mockito.when(distroMapper.mapSrv(Mockito.anyString())).thenReturn("test");
        ObjectNode objectNode = operatorController.getResponsibleServer4Client("test", "test");
        Assert.assertEquals("test", objectNode.get("responsibleServer").asText());
    }
    
    @Test
    public void testSetLogLevel() {
        String res = operatorController.setLogLevel("test", "info");
        Assert.assertEquals("ok", res);
    }
}
