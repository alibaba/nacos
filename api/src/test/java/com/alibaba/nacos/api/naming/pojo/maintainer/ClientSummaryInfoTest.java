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

class ClientSummaryInfoTest {
    
    private ObjectMapper mapper;
    
    private ClientSummaryInfo clientSummaryInfo;
    
    @BeforeEach
    void setUp() throws Exception {
        mapper = new ObjectMapper();
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        clientSummaryInfo = new ClientSummaryInfo();
        clientSummaryInfo.setClientId("clientId");
        clientSummaryInfo.setEphemeral(true);
        clientSummaryInfo.setLastUpdatedTime(1000L);
        clientSummaryInfo.setClientType("connection");
        clientSummaryInfo.setConnectType("grpc");
        clientSummaryInfo.setAppName("appName");
        clientSummaryInfo.setVersion("version");
        clientSummaryInfo.setClientIp("1.1.1.1");
        clientSummaryInfo.setClientPort(8080);
    }
    
    @Test
    void testSerialize() throws JsonProcessingException {
        String json = mapper.writeValueAsString(clientSummaryInfo);
        assertTrue(json.contains("\"clientId\":\"clientId\""));
        assertTrue(json.contains("\"ephemeral\":true"));
        assertTrue(json.contains("\"lastUpdatedTime\":1000"));
        assertTrue(json.contains("\"clientType\":\"connection\""));
        assertTrue(json.contains("\"connectType\":\"grpc\""));
        assertTrue(json.contains("\"appName\":\"appName\""));
        assertTrue(json.contains("\"version\":\"version\""));
        assertTrue(json.contains("\"clientIp\":\"1.1.1.1\""));
        assertTrue(json.contains("\"clientPort\":8080"));
    }
    
    @Test
    void testDeserialize() throws IOException {
        String jsonString = "{\"clientId\":\"clientId\",\"ephemeral\":true,\"lastUpdatedTime\":1000,"
                + "\"clientType\":\"connection\",\"connectType\":\"grpc\",\"appName\":\"appName\","
                + "\"version\":\"version\",\"clientIp\":\"1.1.1.1\",\"clientPort\":8080}";
        ClientSummaryInfo clientSummaryInfo1 = mapper.readValue(jsonString, ClientSummaryInfo.class);
        assertEquals(clientSummaryInfo.getClientId(), clientSummaryInfo1.getClientId());
        assertEquals(clientSummaryInfo.isEphemeral(), clientSummaryInfo1.isEphemeral());
        assertEquals(clientSummaryInfo.getLastUpdatedTime(), clientSummaryInfo1.getLastUpdatedTime());
        assertEquals(clientSummaryInfo.getClientType(), clientSummaryInfo1.getClientType());
        assertEquals(clientSummaryInfo.getConnectType(), clientSummaryInfo1.getConnectType());
        assertEquals(clientSummaryInfo.getAppName(), clientSummaryInfo1.getAppName());
        assertEquals(clientSummaryInfo.getVersion(), clientSummaryInfo1.getVersion());
        assertEquals(clientSummaryInfo.getClientIp(), clientSummaryInfo1.getClientIp());
        assertEquals(clientSummaryInfo.getClientPort(), clientSummaryInfo1.getClientPort());
    }
}