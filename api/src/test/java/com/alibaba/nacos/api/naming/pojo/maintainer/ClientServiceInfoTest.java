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

class ClientServiceInfoTest {
    
    private ObjectMapper mapper;
    
    private ClientServiceInfo clientServiceInfo;
    
    @BeforeEach
    void setUp() throws Exception {
        mapper = new ObjectMapper();
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        clientServiceInfo = new ClientServiceInfo();
        clientServiceInfo.setNamespaceId("namespaceId");
        clientServiceInfo.setGroupName("groupName");
        clientServiceInfo.setServiceName("serviceName");
        
        ClientPublisherInfo publisherInfo = new ClientPublisherInfo();
        publisherInfo.setClientId("publisherId");
        publisherInfo.setIp("1.1.1.1");
        publisherInfo.setPort(8080);
        publisherInfo.setClusterName("publisherCluster");
        clientServiceInfo.setPublisherInfo(publisherInfo);
        
        ClientSubscriberInfo subscriberInfo = new ClientSubscriberInfo();
        subscriberInfo.setClientId("subscriberId");
        subscriberInfo.setAppName("subscriberApp");
        subscriberInfo.setAgent("subscriberAgent");
        subscriberInfo.setAddress("1.1.1.1:8080");
        clientServiceInfo.setSubscriberInfo(subscriberInfo);
    }
    
    @Test
    void testSerialize() throws JsonProcessingException {
        String json = mapper.writeValueAsString(clientServiceInfo);
        assertTrue(json.contains("\"namespaceId\":\"namespaceId\""));
        assertTrue(json.contains("\"groupName\":\"groupName\""));
        assertTrue(json.contains("\"serviceName\":\"serviceName\""));
        assertTrue(json.contains("\"publisherInfo\":{"));
        assertTrue(json.contains("\"subscriberInfo\":{"));
    }
    
    @Test
    void testDeserialize() throws IOException {
        String jsonString =
                "{\"namespaceId\":\"namespaceId\",\"groupName\":\"groupName\",\"serviceName\":\"serviceName\","
                        + "\"publisherInfo\":{\"clientId\":\"publisherId\",\"ip\":\"1.1.1.1\",\"port\":8080,\"clusterName\":\"publisherCluster\"},"
                        + "\"subscriberInfo\":{\"clientId\":\"subscriberId\",\"appName\":\"subscriberApp\",\"agent\":\"subscriberAgent\""
                        + ",\"address\":\"1.1.1.1:8080\"}}";
        ClientServiceInfo clientServiceInfo1 = mapper.readValue(jsonString, ClientServiceInfo.class);
        assertEquals(clientServiceInfo.getNamespaceId(), clientServiceInfo1.getNamespaceId());
        assertEquals(clientServiceInfo.getGroupName(), clientServiceInfo1.getGroupName());
        assertEquals(clientServiceInfo.getServiceName(), clientServiceInfo1.getServiceName());
        
        assertEquals(clientServiceInfo.getPublisherInfo().getClientId(),
                clientServiceInfo1.getPublisherInfo().getClientId());
        assertEquals(clientServiceInfo.getPublisherInfo().getIp(), clientServiceInfo1.getPublisherInfo().getIp());
        assertEquals(clientServiceInfo.getPublisherInfo().getPort(), clientServiceInfo1.getPublisherInfo().getPort());
        assertEquals(clientServiceInfo.getPublisherInfo().getClusterName(),
                clientServiceInfo1.getPublisherInfo().getClusterName());
        
        assertEquals(clientServiceInfo.getSubscriberInfo().getClientId(),
                clientServiceInfo1.getSubscriberInfo().getClientId());
        assertEquals(clientServiceInfo.getSubscriberInfo().getAppName(),
                clientServiceInfo1.getSubscriberInfo().getAppName());
        assertEquals(clientServiceInfo.getSubscriberInfo().getAgent(),
                clientServiceInfo1.getSubscriberInfo().getAgent());
        assertEquals(clientServiceInfo.getSubscriberInfo().getAddress(),
                clientServiceInfo1.getSubscriberInfo().getAddress());
    }
}