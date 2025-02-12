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

import com.alibaba.nacos.api.naming.pojo.healthcheck.AbstractHealthChecker;
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

class ServiceDetailInfoTest {
    
    private ObjectMapper mapper;
    
    private ServiceDetailInfo serviceDetailInfo;
    
    @BeforeEach
    void setUp() throws Exception {
        mapper = new ObjectMapper();
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        serviceDetailInfo = new ServiceDetailInfo();
        serviceDetailInfo.setNamespaceId("testNs");
        serviceDetailInfo.setGroupName("testG");
        serviceDetailInfo.setServiceName("testS");
        serviceDetailInfo.setEphemeral(false);
        serviceDetailInfo.setProtectThreshold(0.5f);
        Map<String, String> metadata = new HashMap<>();
        metadata.put("testKey", "testValue");
        serviceDetailInfo.setMetadata(metadata);
        ClusterInfo clusterInfo = new ClusterInfo();
        clusterInfo.setClusterName("testC");
        clusterInfo.setHealthChecker(new AbstractHealthChecker.None());
        clusterInfo.setMetadata(metadata);
        clusterInfo.setHosts(Collections.emptyList());
        clusterInfo.setHealthyCheckPort(8080);
        clusterInfo.setUseInstancePortForCheck(false);
        Map<String, ClusterInfo> clusterMap = new HashMap<>();
        clusterMap.put("testC", clusterInfo);
        serviceDetailInfo.setClusterMap(clusterMap);
    }
    
    @Test
    void testSerialize() throws JsonProcessingException {
        String json = mapper.writeValueAsString(serviceDetailInfo);
        assertTrue(json.contains("\"namespaceId\":\"testNs\""));
        assertTrue(json.contains("\"groupName\":\"testG\""));
        assertTrue(json.contains("\"serviceName\":\"testS\""));
        assertTrue(json.contains("\"ephemeral\":false"));
        assertTrue(json.contains("\"protectThreshold\":0.5"));
        assertTrue(json.contains("\"metadata\":{\"testKey\":\"testValue\"}"));
        assertTrue(json.contains("\"selector\":null"));
        assertTrue(json.contains("\"clusterMap\":{\"testC\":{"));
        assertTrue(json.contains("\"clusterName\":\"testC\""));
        assertTrue(json.contains("\"healthChecker\":{\"type\":\"NONE\"}"));
        assertTrue(json.contains("\"hosts\":[]"));
        assertTrue(json.contains("\"healthyCheckPort\":8080"));
        assertTrue(json.contains("\"useInstancePortForCheck\":false"));
    }
    
    @Test
    void testDeserialize() throws IOException {
        String jsonString = "{\"namespaceId\":\"testNs\",\"serviceName\":\"testS\",\"groupName\":\"testG\","
                + "\"clusterMap\":{\"testC\":{\"clusterName\":\"testC\",\"healthChecker\":{\"type\":\"NONE\"},"
                + "\"metadata\":{\"testKey\":\"testValue\"},\"hosts\":[],\"healthyCheckPort\":8080,\"useInstancePortForCheck\":false}},"
                + "\"metadata\":{\"testKey\":\"testValue\"},"
                + "\"protectThreshold\":0.5,\"selector\":null,\"ephemeral\":false}";
        ServiceDetailInfo serviceDetailInfo1 = mapper.readValue(jsonString, ServiceDetailInfo.class);
        assertEquals(serviceDetailInfo.getNamespaceId(), serviceDetailInfo1.getNamespaceId());
        assertEquals(serviceDetailInfo.getGroupName(), serviceDetailInfo1.getGroupName());
        assertEquals(serviceDetailInfo.getServiceName(), serviceDetailInfo1.getServiceName());
        assertEquals(serviceDetailInfo.isEphemeral(), serviceDetailInfo1.isEphemeral());
        assertEquals(serviceDetailInfo.getProtectThreshold(), serviceDetailInfo1.getProtectThreshold());
        assertEquals(serviceDetailInfo.getMetadata(), serviceDetailInfo1.getMetadata());
        assertEquals(serviceDetailInfo.getSelector(), serviceDetailInfo1.getSelector());
        assertEquals(serviceDetailInfo.getClusterMap().size(), serviceDetailInfo1.getClusterMap().size());
        assertEquals(serviceDetailInfo.getClusterMap().keySet(), serviceDetailInfo1.getClusterMap().keySet());
        for (Map.Entry<String, ClusterInfo> entry : serviceDetailInfo.getClusterMap().entrySet()) {
            ClusterInfo clusterInfo = entry.getValue();
            ClusterInfo clusterInfo1 = serviceDetailInfo1.getClusterMap().get(entry.getKey());
            assertEquals(clusterInfo.getClusterName(), clusterInfo1.getClusterName());
            assertEquals(clusterInfo.getHealthChecker().getType(), clusterInfo1.getHealthChecker().getType());
            assertEquals(clusterInfo.getMetadata(), clusterInfo1.getMetadata());
            assertEquals(clusterInfo.getHosts(), clusterInfo1.getHosts());
            assertEquals(clusterInfo.getHealthyCheckPort(), clusterInfo1.getHealthyCheckPort());
            assertEquals(clusterInfo.isUseInstancePortForCheck(), clusterInfo1.isUseInstancePortForCheck());
        }
    }
    
}