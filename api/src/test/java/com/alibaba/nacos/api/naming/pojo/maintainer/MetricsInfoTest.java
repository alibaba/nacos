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

package com.alibaba.nacos.api.naming.pojo.maintainer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetricsInfoTest {
    
    private ObjectMapper mapper;
    
    private MetricsInfo metricsInfo;
    
    @BeforeEach
    void setUp() throws Exception {
        mapper = new ObjectMapper();
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        metricsInfo = new MetricsInfo();
        metricsInfo.setStatus("UP");
        metricsInfo.setClientCount(10);
        metricsInfo.setInstanceCount(100);
        metricsInfo.setServiceCount(20);
        metricsInfo.setSubscribeCount(200);
        metricsInfo.setConnectionBasedClientCount(8);
        metricsInfo.setResponsibleClientCount(8);
        metricsInfo.setEphemeralIpPortClientCount(2);
        metricsInfo.setPersistentIpPortClientCount(0);
    }
    
    @Test
    void testSerialize() throws JsonProcessingException {
        String json = mapper.writeValueAsString(metricsInfo);
        assertTrue(json.contains("\"status\":\"UP\""));
        assertTrue(json.contains("\"serviceCount\":20"));
        assertTrue(json.contains("\"instanceCount\":100"));
        assertTrue(json.contains("\"subscribeCount\":200"));
        assertTrue(json.contains("\"clientCount\":10"));
        assertTrue(json.contains("\"connectionBasedClientCount\":8"));
        assertTrue(json.contains("\"ephemeralIpPortClientCount\":2"));
        assertTrue(json.contains("\"persistentIpPortClientCount\":0"));
        assertTrue(json.contains("\"responsibleClientCount\":8"));
    }
    
    @Test
    void testDeserialize() throws IOException {
        String jsonString = "{\"status\":\"UP\",\"serviceCount\":20,\"instanceCount\":100,\"subscribeCount\":200,"
                + "\"clientCount\":10,\"connectionBasedClientCount\":8,\"ephemeralIpPortClientCount\":2,"
                + "\"persistentIpPortClientCount\":0,\"responsibleClientCount\":8}\n";
        MetricsInfo metricsInfo1 = mapper.readValue(jsonString, MetricsInfo.class);
        assertEquals(metricsInfo.getStatus(), metricsInfo1.getStatus());
        assertEquals(metricsInfo.getClientCount(), metricsInfo1.getClientCount());
        assertEquals(metricsInfo.getInstanceCount(), metricsInfo1.getInstanceCount());
        assertEquals(metricsInfo.getServiceCount(), metricsInfo1.getServiceCount());
        assertEquals(metricsInfo.getSubscribeCount(), metricsInfo1.getSubscribeCount());
        assertEquals(metricsInfo.getConnectionBasedClientCount(), metricsInfo1.getConnectionBasedClientCount());
        assertEquals(metricsInfo.getResponsibleClientCount(), metricsInfo1.getResponsibleClientCount());
        assertEquals(metricsInfo.getEphemeralIpPortClientCount(), metricsInfo1.getEphemeralIpPortClientCount());
        assertEquals(metricsInfo.getPersistentIpPortClientCount(), metricsInfo1.getPersistentIpPortClientCount());
    }
    
}