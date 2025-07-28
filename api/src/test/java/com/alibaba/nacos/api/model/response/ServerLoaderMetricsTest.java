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

package com.alibaba.nacos.api.model.response;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerLoaderMetricsTest {
    
    private ObjectMapper mapper;
    
    ServerLoaderMetrics serverLoaderMetrics;
    
    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        serverLoaderMetrics = new ServerLoaderMetrics();
        Map<String, String> metrics = new HashMap<>();
        metrics.put("conCount", "100");
        metrics.put("cpu", "100");
        metrics.put("load", "1");
        ServerLoaderMetric metric = ServerLoaderMetric.Builder.newBuilder().withAddress("127.0.0.1:8848")
                .convertFromMap(metrics).build();
        serverLoaderMetrics.setDetail(Collections.singletonList(metric));
        serverLoaderMetrics.setMemberCount(1);
        serverLoaderMetrics.setMetricsCount(1);
        serverLoaderMetrics.setCompleted(true);
        serverLoaderMetrics.setMax(100);
        serverLoaderMetrics.setMin(100);
        serverLoaderMetrics.setAvg(100);
        serverLoaderMetrics.setThreshold(String.valueOf(serverLoaderMetrics.getAvg() * 1.1d));
        serverLoaderMetrics.setTotal(100);
    }
    
    @Test
    void testSerialize() throws JsonProcessingException {
        String json = mapper.writeValueAsString(serverLoaderMetrics);
        assertTrue(json.contains("\"memberCount\":1"));
        assertTrue(json.contains("\"metricsCount\":1"));
        assertTrue(json.contains("\"completed\":true"));
        assertTrue(json.contains("\"max\":100"));
        assertTrue(json.contains("\"min\":100"));
        assertTrue(json.contains("\"avg\":100"));
        assertTrue(json.contains("\"threshold\":\"110.0"));
        assertTrue(json.contains("\"total\":100"));
        assertTrue(json.contains("\"detail\":["));
        assertTrue(json.contains("\"address\":\"127.0.0.1:8848\""));
        assertTrue(json.contains("\"sdkConCount\":0"));
        assertTrue(json.contains("\"conCount\":100"));
        assertTrue(json.contains("\"load\":\"1\""));
        assertTrue(json.contains("\"cpu\":\"100\""));
    }
    
    @Test
    void testDeserialize() throws IOException {
        String jsonString = "{\"detail\":[{\"address\":\"127.0.0.1:8848\",\"sdkConCount\":0,\"conCount\":100,"
                + "\"load\":\"1\",\"cpu\":\"100\"}],\"memberCount\":1,\"metricsCount\":1,\"completed\":true,"
                + "\"max\":100,\"min\":100,\"avg\":100,\"threshold\":\"110.00000000000001\",\"total\":100}";
        ServerLoaderMetrics metricsInfo1 = mapper.readValue(jsonString, ServerLoaderMetrics.class);
        assertEquals(serverLoaderMetrics.getMemberCount(), metricsInfo1.getMemberCount());
        assertEquals(serverLoaderMetrics.getMetricsCount(), metricsInfo1.getMetricsCount());
        assertEquals(serverLoaderMetrics.isCompleted(), metricsInfo1.isCompleted());
        assertEquals(serverLoaderMetrics.getMax(), metricsInfo1.getMax());
        assertEquals(serverLoaderMetrics.getMin(), metricsInfo1.getMin());
        assertEquals(serverLoaderMetrics.getAvg(), metricsInfo1.getAvg());
        assertEquals(serverLoaderMetrics.getThreshold(), metricsInfo1.getThreshold());
        assertEquals(serverLoaderMetrics.getTotal(), metricsInfo1.getTotal());
        assertEquals(serverLoaderMetrics.getDetail().get(0).getAddress(), metricsInfo1.getDetail().get(0).getAddress());
        assertEquals(serverLoaderMetrics.getDetail().get(0).getConCount(),
                metricsInfo1.getDetail().get(0).getConCount());
        assertEquals(serverLoaderMetrics.getDetail().get(0).getSdkConCount(),
                metricsInfo1.getDetail().get(0).getSdkConCount());
        assertEquals(serverLoaderMetrics.getDetail().get(0).getCpu(), metricsInfo1.getDetail().get(0).getCpu());
        assertEquals(serverLoaderMetrics.getDetail().get(0).getLoad(), metricsInfo1.getDetail().get(0).getLoad());
    }
}