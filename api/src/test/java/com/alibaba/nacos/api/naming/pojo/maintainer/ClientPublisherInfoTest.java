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

class ClientPublisherInfoTest {
    
    private ObjectMapper mapper;
    
    private ClientPublisherInfo clientPublisherInfo;
    
    @BeforeEach
    void setUp() throws Exception {
        mapper = new ObjectMapper();
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        clientPublisherInfo = new ClientPublisherInfo();
        clientPublisherInfo.setClientId("clientId");
        clientPublisherInfo.setIp("1.1.1.1");
        clientPublisherInfo.setPort(8080);
        clientPublisherInfo.setClusterName("clusterName");
    }
    
    @Test
    void testSerialize() throws JsonProcessingException {
        String json = mapper.writeValueAsString(clientPublisherInfo);
        assertTrue(json.contains("\"clientId\":\"clientId\""));
        assertTrue(json.contains("\"ip\":\"1.1.1.1\""));
        assertTrue(json.contains("\"port\":8080"));
        assertTrue(json.contains("\"clusterName\":\"clusterName\""));
    }
    
    @Test
    void testDeserialize() throws IOException {
        String jsonString = "{\"clientId\":\"clientId\",\"ip\":\"1.1.1.1\",\"port\":8080,\"clusterName\":\"clusterName\"}";
        ClientPublisherInfo clientPublisherInfo1 = mapper.readValue(jsonString, ClientPublisherInfo.class);
        assertEquals(clientPublisherInfo.getClientId(), clientPublisherInfo1.getClientId());
        assertEquals(clientPublisherInfo.getIp(), clientPublisherInfo1.getIp());
        assertEquals(clientPublisherInfo.getPort(), clientPublisherInfo1.getPort());
        assertEquals(clientPublisherInfo.getClusterName(), clientPublisherInfo1.getClusterName());
    }
}