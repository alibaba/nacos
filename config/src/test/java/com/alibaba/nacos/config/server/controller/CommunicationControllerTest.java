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

package com.alibaba.nacos.config.server.controller;

import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.model.SampleResult;
import com.alibaba.nacos.config.server.remote.ConfigChangeListenContext;
import com.alibaba.nacos.config.server.service.LongPollingService;
import com.alibaba.nacos.config.server.service.dump.DumpService;
import com.alibaba.nacos.config.server.utils.GroupKey2;
import com.alibaba.nacos.core.remote.Connection;
import com.alibaba.nacos.core.remote.ConnectionManager;
import com.alibaba.nacos.core.remote.ConnectionMeta;
import com.alibaba.nacos.core.remote.grpc.GrpcConnection;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import javax.servlet.ServletContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.Mockito.when;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = MockServletContext.class)
@WebAppConfiguration
public class CommunicationControllerTest {
    
    @InjectMocks
    CommunicationController communicationController;
    
    private MockMvc mockMvc;
    
    @Mock
    private ServletContext servletContext;
    
    @Mock
    DumpService dumpService;
    
    @Mock
    LongPollingService longPollingService;
    
    @Mock
    ConfigChangeListenContext configChangeListenContext;
    
    @Mock
    ConnectionManager connectionManager;
    
    @Before
    public void setUp() {
        EnvUtil.setEnvironment(new StandardEnvironment());
        when(servletContext.getContextPath()).thenReturn("/nacos");
        ReflectionTestUtils.setField(communicationController, "dumpService", dumpService);
        ReflectionTestUtils.setField(communicationController, "longPollingService", longPollingService);
        ReflectionTestUtils.setField(communicationController, "configChangeListenContext", configChangeListenContext);
        ReflectionTestUtils.setField(communicationController, "connectionManager", connectionManager);
        mockMvc = MockMvcBuilders.standaloneSetup(communicationController).build();
    }
    
    @Test
    public void testNotifyConfigInfo() throws Exception {
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get(Constants.COMMUNICATION_CONTROLLER_PATH + "/dataChange")
                .param("dataId", "test").param("group", "test");
        String actualValue = mockMvc.perform(builder).andReturn().getResponse().getContentAsString();
        Assert.assertEquals("true", actualValue);
    }
    
    @Test
    public void testGetSubClientConfig1x() throws Exception {
    
        SampleResult result = new SampleResult();
        Map<String, String> lisentersGroupkeyStatus = new HashMap<>();
        lisentersGroupkeyStatus.put("test", "test");
        result.setLisentersGroupkeyStatus(lisentersGroupkeyStatus);
        when(longPollingService.getCollectSubscribleInfo("test", "test", "test")).thenReturn(result);
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get(Constants.COMMUNICATION_CONTROLLER_PATH + "/configWatchers")
                .param("dataId", "test").param("group", "test").param("tenant", "test");
        String actualValue = mockMvc.perform(builder).andReturn().getResponse().getContentAsString();
        Assert.assertEquals("{\"test\":\"test\"}", JacksonUtils.toObj(actualValue).get("lisentersGroupkeyStatus").toString());
    }
    
    @Test
    public void testGetSubClientConfig2x() throws Exception {
        
        SampleResult result = new SampleResult();
        result.setLisentersGroupkeyStatus(new HashMap<>());
        when(longPollingService.getCollectSubscribleInfo("test", "test", "test")).thenReturn(result);
        String groupKey = GroupKey2.getKey("test", "test", "test");
        Set<String> listenersClients = new HashSet<>();
        String connectionId = "127.0.0.1";
        listenersClients.add(connectionId);
        when(configChangeListenContext.getListeners(groupKey)).thenReturn(listenersClients);
        ConnectionMeta connectionMeta = new ConnectionMeta(connectionId, connectionId, connectionId, 8888, 9848, "GRPC", "", "", new HashMap<>());
        Connection client = new GrpcConnection(connectionMeta, null, null);
        when(connectionManager.getConnection(connectionId)).thenReturn(client);
        when(configChangeListenContext.getListenKeyMd5(connectionId, groupKey)).thenReturn("md5");
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get(Constants.COMMUNICATION_CONTROLLER_PATH + "/configWatchers")
                .param("dataId", "test").param("group", "test").param("tenant", "test");
        String actualValue = mockMvc.perform(builder).andReturn().getResponse().getContentAsString();
        Assert.assertEquals("{\"127.0.0.1\":\"md5\"}", JacksonUtils.toObj(actualValue).get("lisentersGroupkeyStatus").toString());
    }
    
    @Test
    public void testGetSubClientConfigByIp() throws Exception {
    
        String ip = "127.0.0.1";
        SampleResult result = new SampleResult();
        result.setLisentersGroupkeyStatus(new HashMap<>());
        when(longPollingService.getCollectSubscribleInfoByIp(ip)).thenReturn(result);
        ConnectionMeta connectionMeta = new ConnectionMeta(ip, ip, ip, 8888, 9848, "GRPC", "", "", new HashMap<>());
        Connection connection = new GrpcConnection(connectionMeta, null, null);
        List<Connection> connectionList = new ArrayList<>();
        connectionList.add(connection);
        when(connectionManager.getConnectionByIp(ip)).thenReturn(connectionList);
        Map<String, String> map = new HashMap<>();
        map.put("test", "test");
        when(configChangeListenContext.getListenKeys(ip)).thenReturn(map);
    
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get(Constants.COMMUNICATION_CONTROLLER_PATH + "/watcherConfigs")
                .param("ip", ip);
        String actualValue = mockMvc.perform(builder).andReturn().getResponse().getContentAsString();
        Assert.assertEquals("{\"test\":\"test\"}", JacksonUtils.toObj(actualValue).get("lisentersGroupkeyStatus").toString());
    
    }
}
