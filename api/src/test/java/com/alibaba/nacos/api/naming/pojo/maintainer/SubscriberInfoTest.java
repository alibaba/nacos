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

class SubscriberInfoTest {
    
    private ObjectMapper mapper;
    
    private SubscriberInfo subscriberInfo;
    
    @BeforeEach
    void setUp() throws Exception {
        mapper = new ObjectMapper();
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        subscriberInfo = new SubscriberInfo();
        subscriberInfo.setNamespaceId("namespaceId");
        subscriberInfo.setGroupName("groupName");
        subscriberInfo.setServiceName("serviceName");
        subscriberInfo.setIp("1.1.1.1");
        subscriberInfo.setPort(8080);
        subscriberInfo.setAgent("agent");
        subscriberInfo.setAppName("appName");
    }
    
    @Test
    void testSerialize() throws JsonProcessingException {
        String json = mapper.writeValueAsString(subscriberInfo);
        assertTrue(json.contains("\"namespaceId\":\"namespaceId\""));
        assertTrue(json.contains("\"groupName\":\"groupName\""));
        assertTrue(json.contains("\"serviceName\":\"serviceName\""));
        assertTrue(json.contains("\"ip\":\"1.1.1.1\""));
        assertTrue(json.contains("\"port\":8080"));
        assertTrue(json.contains("\"agent\":\"agent\""));
        assertTrue(json.contains("\"appName\":\"appName\""));
    }
    
    @Test
    void testDeserialize() throws IOException {
        String jsonString = "{\"namespaceId\":\"namespaceId\",\"groupName\":\"groupName\",\"serviceName\":\"serviceName\","
                + "\"ip\":\"1.1.1.1\",\"port\":8080,\"agent\":\"agent\",\"appName\":\"appName\"}";
        SubscriberInfo subscriberInfo1 = mapper.readValue(jsonString, SubscriberInfo.class);
        assertEquals(subscriberInfo.getNamespaceId(), subscriberInfo1.getNamespaceId());
        assertEquals(subscriberInfo.getGroupName(), subscriberInfo1.getGroupName());
        assertEquals(subscriberInfo.getServiceName(), subscriberInfo1.getServiceName());
        assertEquals(subscriberInfo.getIp(), subscriberInfo1.getIp());
        assertEquals(subscriberInfo.getPort(), subscriberInfo1.getPort());
        assertEquals(subscriberInfo.getAgent(), subscriberInfo1.getAgent());
        assertEquals(subscriberInfo.getAppName(), subscriberInfo1.getAppName());
        assertEquals(subscriberInfo.getAddress(), subscriberInfo1.getAddress());
    }
}