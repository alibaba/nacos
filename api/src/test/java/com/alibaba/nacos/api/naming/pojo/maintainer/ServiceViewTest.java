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

class ServiceViewTest {
    
    private ObjectMapper mapper;
    
    private ServiceView serviceView;
    
    @BeforeEach
    void setUp() throws Exception {
        mapper = new ObjectMapper();
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        serviceView = new ServiceView();
        serviceView.setName("service");
        serviceView.setGroupName("group");
        serviceView.setClusterCount(2);
        serviceView.setIpCount(10);
        serviceView.setHealthyInstanceCount(8);
        serviceView.setTriggerFlag("flag");
    }
    
    @Test
    void testSerialize() throws JsonProcessingException {
        String json = mapper.writeValueAsString(serviceView);
        assertTrue(json.contains("\"name\":\"service\""));
        assertTrue(json.contains("\"groupName\":\"group\""));
        assertTrue(json.contains("\"clusterCount\":2"));
        assertTrue(json.contains("\"ipCount\":10"));
        assertTrue(json.contains("\"healthyInstanceCount\":8"));
        assertTrue(json.contains("\"triggerFlag\":\"flag\""));
    }
    
    @Test
    void testDeserialize() throws IOException {
        String jsonString = "{\"name\":\"service\",\"groupName\":\"group\",\"clusterCount\":2,"
                + "\"ipCount\":10,\"healthyInstanceCount\":8,\"triggerFlag\":\"flag\"}";
        ServiceView serviceView1 = mapper.readValue(jsonString, ServiceView.class);
        assertEquals(serviceView.getName(), serviceView1.getName());
        assertEquals(serviceView.getGroupName(), serviceView1.getGroupName());
        assertEquals(serviceView.getClusterCount(), serviceView1.getClusterCount());
        assertEquals(serviceView.getIpCount(), serviceView1.getIpCount());
        assertEquals(serviceView.getHealthyInstanceCount(), serviceView1.getHealthyInstanceCount());
        assertEquals(serviceView.getTriggerFlag(), serviceView1.getTriggerFlag());
    }
    
    @Test
    void testToString() {
        String expected = "ServiceView{name='service', groupName='group', clusterCount=2, ipCount=10, healthyInstanceCount=8, triggerFlag='flag'}";
        assertEquals(expected, serviceView.toString());
    }
}