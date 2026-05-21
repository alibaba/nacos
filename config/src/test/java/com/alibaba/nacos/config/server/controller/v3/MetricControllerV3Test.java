/*
 * Copyright 1999-$toady.year Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.controller.v3;

import com.alibaba.nacos.api.config.remote.response.ClientConfigMetricResponse;
import com.alibaba.nacos.common.http.Callback;
import com.alibaba.nacos.common.http.HttpClientBeanHolder;
import com.alibaba.nacos.common.http.client.NacosAsyncRestTemplate;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.core.remote.Connection;
import com.alibaba.nacos.core.remote.ConnectionManager;
import com.fasterxml.jackson.databind.JsonNode;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.lang.reflect.Type;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetricControllerV3Test {
    
    MetricsControllerV3 metricsControllerV3;
    
    private MockMvc mockMvc;
    
    @Mock
    private ServerMemberManager memberManager;
    
    @Mock
    private ConnectionManager connectionManager;
    
    @BeforeEach
    void setUp() {
        System.setProperty("nacos.core.auth.admin.enabled", "false");
        EnvUtil.setEnvironment(new StandardEnvironment());
        metricsControllerV3 = new MetricsControllerV3(memberManager, connectionManager);
        mockMvc = MockMvcBuilders.standaloneSetup(metricsControllerV3).build();
    }
    
    @AfterEach
    void tearDown() {
        System.clearProperty("nacos.core.auth.admin.enabled");
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
        
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.get(Constants.METRICS_CONTROLLER_V3_ADMIN_PATH + "/cluster")
                .param("ip", "127.0.0.1").param("namespaceId", "test").param("dataId", "test")
                .param("groupName", "test");
        String actualValue =
            mockMvc.perform(builder).andReturn().getResponse().getContentAsString();
        String code = JacksonUtils.toObj(actualValue).get("code").toString();
        assertEquals("0", code);
    }
    
    @Test
    void testGetClusterMetricWithPartialFailureShouldSetCompleteFalse() throws Exception {
        List<Member> members = new ArrayList<>();
        Member m1 = new Member();
        m1.setIp("127.0.0.1");
        m1.setPort(8848);
        members.add(m1);
        Member m2 = new Member();
        m2.setIp("127.0.0.1");
        m2.setPort(9848);
        members.add(m2);
        when(memberManager.allMembers()).thenReturn(members);
        
        NacosAsyncRestTemplate nacosAsyncRestTemplate = Mockito.mock(NacosAsyncRestTemplate.class);
        AtomicBoolean firstCall = new AtomicBoolean(true);
        Mockito.doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Callback<Map> callback = (Callback<Map>) invocation.getArgument(4);
            if (firstCall.compareAndSet(true, false)) {
                RestResult<Map> result = new RestResult<>();
                Map<String, Object> successData = new HashMap<>();
                successData.put("test", "md5..");
                result.setCode(200);
                result.setData(successData);
                callback.onReceive(result);
            } else {
                callback.onError(new RuntimeException("mock partial failure"));
            }
            return null;
        }).when(nacosAsyncRestTemplate).get(any(String.class), any(Header.class), any(Query.class),
            any(Type.class),
            any());
        
        try (MockedStatic<HttpClientBeanHolder> mockedStatic =
            Mockito.mockStatic(HttpClientBeanHolder.class)) {
            mockedStatic
                .when(() -> HttpClientBeanHolder.getNacosAsyncRestTemplate(any(Logger.class)))
                .thenReturn(nacosAsyncRestTemplate);
            MockHttpServletRequestBuilder builder = MockMvcRequestBuilders
                .get(Constants.METRICS_CONTROLLER_V3_ADMIN_PATH + "/cluster")
                .param("ip", "127.0.0.1")
                .param("namespaceId", "test").param("dataId", "test").param("groupName", "test");
            String actualValue =
                mockMvc.perform(builder).andReturn().getResponse().getContentAsString();
            JsonNode response = JacksonUtils.toObj(actualValue);
            JsonNode data = response.get("data");
            assertNotNull(data);
            assertEquals(false, data.get("complete").asBoolean());
            assertEquals("md5..", data.get("test").asText());
        }
    }
    
    @Test
    void testGetClusterMetricTimeoutShouldSetCompleteFalse() throws Exception {
        List<Member> members = new ArrayList<>();
        Member member = new Member();
        member.setIp("127.0.0.1");
        member.setPort(8848);
        members.add(member);
        when(memberManager.allMembers()).thenReturn(members);
        
        NacosAsyncRestTemplate nacosAsyncRestTemplate = Mockito.mock(NacosAsyncRestTemplate.class);
        try (MockedStatic<HttpClientBeanHolder> mockedStatic =
            Mockito.mockStatic(HttpClientBeanHolder.class)) {
            mockedStatic
                .when(() -> HttpClientBeanHolder.getNacosAsyncRestTemplate(any(Logger.class)))
                .thenReturn(nacosAsyncRestTemplate);
            MockHttpServletRequestBuilder builder = MockMvcRequestBuilders
                .get(Constants.METRICS_CONTROLLER_V3_ADMIN_PATH + "/cluster")
                .param("ip", "127.0.0.1")
                .param("namespaceId", "test").param("dataId", "test").param("groupName", "test");
            
            String actualValue =
                mockMvc.perform(builder).andReturn().getResponse().getContentAsString();
            JsonNode data = JacksonUtils.toObj(actualValue).get("data");
            
            assertNotNull(data);
            assertFalse(data.get("complete").asBoolean());
        }
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
        AtomicBoolean complete = new AtomicBoolean(true);
        MetricsControllerV3.ClusterMetricsCallBack clusterMetricsCallBack =
            new MetricsControllerV3.ClusterMetricsCallBack(
                responseMap, latch, complete, dataId, group, tenant, ip, m1);
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
        assertFalse(complete.get());
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
        
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.get(Constants.METRICS_CONTROLLER_V3_ADMIN_PATH + "/ip")
                .param("ip", "127.0.0.1").param("namespaceId", "test").param("dataId", "test")
                .param("groupName", "test");
        String actualValue =
            mockMvc.perform(builder).andReturn().getResponse().getContentAsString();
        String data = JacksonUtils.toObj(actualValue).get("data").toString();
        assertEquals("{\"test\":\"test\"}", data);
        
    }
    
}
