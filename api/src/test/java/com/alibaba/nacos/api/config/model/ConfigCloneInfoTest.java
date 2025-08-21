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

package com.alibaba.nacos.api.config.model;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigCloneInfoTest {
    
    private ObjectMapper mapper;
    
    ConfigCloneInfo configCloneInfo;
    
    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        configCloneInfo = new ConfigCloneInfo();
        configCloneInfo.setConfigId(1L);
        configCloneInfo.setTargetDataId("newDataId");
        configCloneInfo.setTargetGroupName("newGroup");
    }
    
    @Test
    public void testSerialize() throws Exception {
        String json = mapper.writeValueAsString(configCloneInfo);
        assertTrue(json.contains("\"configId\":1"));
        assertTrue(json.contains("\"targetGroupName\":\"newGroup\""));
        assertTrue(json.contains("\"targetDataId\":\"newDataId\""));
    }
    
    @Test
    public void testDeserialize() throws Exception {
        String json = "{\"configId\":1,\"targetGroupName\":\"newGroup\",\"targetDataId\":\"newDataId\"}";
        ConfigCloneInfo actual = mapper.readValue(json, ConfigCloneInfo.class);
        assertEquals(configCloneInfo.getConfigId(), actual.getConfigId());
        assertEquals(configCloneInfo.getTargetGroupName(), actual.getTargetGroupName());
        assertEquals(configCloneInfo.getTargetDataId(), actual.getTargetDataId());
    }
}