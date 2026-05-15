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

import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
