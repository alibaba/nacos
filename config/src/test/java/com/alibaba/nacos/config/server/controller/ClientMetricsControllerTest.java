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
import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.core.remote.Connection;
import com.alibaba.nacos.core.remote.ConnectionManager;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import javax.servlet.ServletContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = MockServletContext.class)
@WebAppConfiguration
class ClientMetricsControllerTest {
    
    @InjectMocks
    ClientMetricsController clientMetricsController;
    
    private MockMvc mockMvc;
    
    @Mock
    private ServerMemberManager memberManager;
    
    @Mock
    private ConnectionManager connectionManager;
    
    @Mock
    private ServletContext servletContext;
    
    @BeforeEach
    void setUp() {
        EnvUtil.setEnvironment(new StandardEnvironment());
        when(servletContext.getContextPath()).thenReturn("/nacos");
        ReflectionTestUtils.setField(clientMetricsController, "serverMemberManager", memberManager);
        ReflectionTestUtils.setField(clientMetricsController, "connectionManager", connectionManager);
        mockMvc = MockMvcBuilders.standaloneSetup(clientMetricsController).build();
    }
    
    @Test
    void testGetClusterMetric() throws Exception {
        List<Member> members = new ArrayList<>();
        Member m1 = new Member();
        m1.setIp("127.0.0.1");
        m1.setPort(8848);
        members.add(m1);
        Member m2 = new Member();
        m2.setIp("127.0.0.1");
        m2.setPort(9848);
        members.add(m2);
        Member m3 = new Member();
        m3.setIp("127.0.0.1");
        m3.setPort(7848);
        members.add(m3);
        
        when(memberManager.allMembers()).thenReturn(members);
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get(Constants.METRICS_CONTROLLER_PATH + "/cluster")
                .param("ip", "127.0.0.1").param("tenant", "test").param("dataId", "test").param("group", "test");
        int actualValue = mockMvc.perform(builder).andReturn().getResponse().getStatus();
        assertEquals(200, actualValue);
    }
    
    @Test
    void testClusterMetricsCallBack() {
        
        Member m1 = new Member();
        m1.setIp("127.0.0.1");
        m1.setPort(8848);
        
        //success result
        RestResult<Map> result1 = new RestResult<>();
        HashMap<String, Object> stringObjectHashMap = new HashMap<>();
        stringObjectHashMap.put("test", "md5..");
        result1.setData(stringObjectHashMap);
        result1.setCode(200);
        
        CountDownLatch latch = new CountDownLatch(5);
        String dataId = "d1";
        String group = "g1";
        String tenant = "t1";
        String ip = "192.168.0.1";
        Map<String, Object> responseMap = new HashMap<>();
        ClientMetricsController.ClusterMetricsCallBack clusterMetricsCallBack = new ClientMetricsController.ClusterMetricsCallBack(
                responseMap, latch, dataId, group, tenant, ip, m1);
        clusterMetricsCallBack.onReceive(result1);
        //fail result
        RestResult<Map> result2 = new RestResult<>();
        HashMap<String, Object> stringObjectHashMap2 = new HashMap<>();
        stringObjectHashMap2.put("test2", "md5..");
        result2.setData(stringObjectHashMap2);
        result2.setCode(500);
        clusterMetricsCallBack.onReceive(result2);
        //error and cancel
        clusterMetricsCallBack.onError(new NullPointerException());
        clusterMetricsCallBack.onCancel();
        clusterMetricsCallBack.onCancel();
        assertEquals(stringObjectHashMap, responseMap);
        assertEquals(0, latch.getCount());
    }
    
    @Test
    void testGetCurrentMetric() throws Exception {
        
        ClientConfigMetricResponse response = new ClientConfigMetricResponse();
        response.putMetric("test", "test");
        Connection connection = Mockito.mock(Connection.class);
        when(connection.request(any(), anyLong())).thenReturn(response);
        List<Connection> connections = new ArrayList<>();
        connections.add(connection);
        when(connectionManager.getConnectionByIp(eq("127.0.0.1"))).thenReturn(connections);
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get(Constants.METRICS_CONTROLLER_PATH + "/current")
                .param("ip", "127.0.0.1").param("tenant", "test").param("dataId", "test").param("group", "test");
        String actualValue = mockMvc.perform(builder).andReturn().getResponse().getContentAsString();
        assertEquals("{\"test\":\"test\"}", actualValue);
        
    }
    
}
