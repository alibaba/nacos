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

import com.alibaba.nacos.api.config.remote.response.ClientConfigMetricResponse;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.core.remote.Connection;
import com.alibaba.nacos.core.remote.ConnectionManager;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = MockServletContext.class)
@WebAppConfiguration
public class ClientMetricsControllerTest {

    @InjectMocks
    ClientMetricsController clientMetricsController;
    
    private MockMvc mockMvc;
    
    @Mock
    private ServerMemberManager memberManager;
    
    @Mock
    private ConnectionManager connectionManager;
    
    @Mock
    private ServletContext servletContext;
    
    @Before
    public void setUp() {
        EnvUtil.setEnvironment(new StandardEnvironment());
        when(servletContext.getContextPath()).thenReturn("/nacos");
        ReflectionTestUtils.setField(clientMetricsController, "serverMemberManager", memberManager);
        ReflectionTestUtils.setField(clientMetricsController, "connectionManager", connectionManager);
        mockMvc = MockMvcBuilders.standaloneSetup(clientMetricsController).build();
    }
    
    @Test
    public void testGetClusterMetric() throws Exception {
    
        when(memberManager.allMembers()).thenReturn(new ArrayList<>());
    
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get(Constants.METRICS_CONTROLLER_PATH + "/cluster")
                .param("ip", "127.0.0.1").param("tenant", "test")
                .param("dataId", "test").param("group", "test");
        int actualValue = mockMvc.perform(builder).andReturn().getResponse().getStatus();
        Assert.assertEquals(200, actualValue);
    }
    
    @Test
    public void testGetCurrentMetric() throws Exception {
    
        ClientConfigMetricResponse response = new ClientConfigMetricResponse();
        response.putMetric("test", "test");
        Connection connection = Mockito.mock(Connection.class);
        when(connection.request(any(), anyLong())).thenReturn(response);
        List<Connection> connections = new ArrayList<>();
        connections.add(connection);
        when(connectionManager.getConnectionByIp(eq("127.0.0.1"))).thenReturn(connections);
    
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get(Constants.METRICS_CONTROLLER_PATH + "/current")
                .param("ip", "127.0.0.1").param("tenant", "test")
                .param("dataId", "test").param("group", "test");
        String actualValue = mockMvc.perform(builder).andReturn().getResponse().getContentAsString();
        Assert.assertEquals("{\"test\":\"test\"}", actualValue);
    
    }
    
}
