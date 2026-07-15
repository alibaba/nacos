/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.core.remote.Connection;
import com.alibaba.nacos.core.remote.ConnectionManager;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.env.StandardEnvironment;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class MetricsControllerV3Test {
    
    @BeforeEach
    void setUp() {
        EnvUtil.setEnvironment(new StandardEnvironment());
    }
    
    @Test
    void testClusterMetricsCallBackOnReceive() {
        Map<String, Object> responseMap = new HashMap<>();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean complete = new AtomicBoolean(true);
        Member member = new Member();
        MetricsControllerV3.ClusterMetricsCallBack cb =
            new MetricsControllerV3.ClusterMetricsCallBack(
                responseMap, latch, complete, "d", "g", "ns", "ip", member);
        
        RestResult<Map> result = new RestResult<>();
        result.setCode(200);
        Map<String, Object> data = new HashMap<>();
        data.put("key1", "value1");
        result.setData(data);
        cb.onReceive(result);
        
        assertEquals("value1", responseMap.get("key1"));
        assertTrue(complete.get());
        assertEquals(0, latch.getCount());
    }
    
    @Test
    void testClusterMetricsCallBackOnReceiveNull() {
        Map<String, Object> responseMap = new HashMap<>();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean complete = new AtomicBoolean(true);
        Member member = new Member();
        MetricsControllerV3.ClusterMetricsCallBack cb =
            new MetricsControllerV3.ClusterMetricsCallBack(
                responseMap, latch, complete, "d", "g", "ns", "ip", member);
        
        cb.onReceive(null);
        assertFalse(complete.get());
        assertEquals(0, latch.getCount());
    }
    
    @Test
    void testClusterMetricsCallBackOnError() {
        Map<String, Object> responseMap = new HashMap<>();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean complete = new AtomicBoolean(true);
        Member member = Member.builder().ip("127.0.0.1").port(8848).build();
        MetricsControllerV3.ClusterMetricsCallBack cb =
            new MetricsControllerV3.ClusterMetricsCallBack(
                responseMap, latch, complete, "d", "g", "ns", "ip", member);
        
        cb.onError(new RuntimeException("test error"));
        assertFalse(complete.get());
        assertEquals(0, latch.getCount());
    }
    
    @Test
    void testClusterMetricsCallBackOnCancel() {
        Map<String, Object> responseMap = new HashMap<>();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean complete = new AtomicBoolean(true);
        Member member = new Member();
        MetricsControllerV3.ClusterMetricsCallBack cb =
            new MetricsControllerV3.ClusterMetricsCallBack(
                responseMap, latch, complete, "d", "g", "ns", "ip", member);
        
        cb.onCancel();
        assertFalse(complete.get());
        assertEquals(0, latch.getCount());
    }
    
    @Test
    void testMetricInterruptedSetsCompleteFalse() throws Exception {
        ServerMemberManager serverMemberManager = Mockito.mock(ServerMemberManager.class);
        ConnectionManager connectionManager = Mockito.mock(ConnectionManager.class);
        when(serverMemberManager.allMembers()).thenReturn(Collections.emptyList());
        MetricsControllerV3 controller = new MetricsControllerV3(serverMemberManager,
            connectionManager);
        Thread.currentThread().interrupt();
        try {
            Result<Map<String, Object>> result =
                controller.metric("127.0.0.1", "dataId", "group", "");
            
            assertEquals(Boolean.FALSE, result.getData().get("complete"));
        } finally {
            Thread.interrupted();
        }
    }
    
    @Test
    void testGetClientMetrics() throws Exception {
        ServerMemberManager serverMemberManager = Mockito.mock(ServerMemberManager.class);
        ConnectionManager connectionManager = Mockito.mock(ConnectionManager.class);
        Connection connection = Mockito.mock(Connection.class);
        ClientConfigMetricResponse response = new ClientConfigMetricResponse();
        response.putMetric("cache", "ok");
        when(connectionManager.getConnectionByIp("127.0.0.1"))
            .thenReturn(Collections.singletonList(connection));
        when(connection.request(any(), eq(1000L))).thenReturn(response);
        MetricsControllerV3 controller = new MetricsControllerV3(serverMemberManager,
            connectionManager);
        
        Result<Map<String, Object>> result =
            controller.getClientMetrics("127.0.0.1", "dataId", "group", "");
        
        assertEquals("ok", result.getData().get("cache"));
    }
    
    @Test
    void testGetClientMetricsThrowsNacosExceptionWhenClientRequestFails() throws Exception {
        ServerMemberManager serverMemberManager = Mockito.mock(ServerMemberManager.class);
        ConnectionManager connectionManager = Mockito.mock(ConnectionManager.class);
        Connection connection = Mockito.mock(Connection.class);
        when(connectionManager.getConnectionByIp("127.0.0.1"))
            .thenReturn(Collections.singletonList(connection));
        when(connection.request(any(), eq(1000L)))
            .thenThrow(new RuntimeException("request failed"));
        MetricsControllerV3 controller = new MetricsControllerV3(serverMemberManager,
            connectionManager);
        
        assertThrows(NacosException.class,
            () -> controller.getClientMetrics("127.0.0.1", "dataId", "group", ""));
    }
}
