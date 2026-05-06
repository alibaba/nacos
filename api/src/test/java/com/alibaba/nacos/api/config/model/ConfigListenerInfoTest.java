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

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigListenerInfoTest {
    
    private ObjectMapper mapper;
    
    private ConfigListenerInfo configListenerInfo;
    
    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        configListenerInfo = new ConfigListenerInfo();
        configListenerInfo.setQueryType(ConfigListenerInfo.QUERY_TYPE_CONFIG);
        configListenerInfo.setListenersStatus(Collections.singletonMap("1.1.1.1", "testMd5"));
    }
    
    @Test
    public void testSerialize() throws Exception {
        String json = mapper.writeValueAsString(configListenerInfo);
        assertTrue(json.contains("\"queryType\":\"config\""));
        assertTrue(json.contains("\"listenersStatus\":{\"1.1.1.1\":\"testMd5\"}"));
    }
    
    @Test
    public void testDeserialize() throws Exception {
        String json = "{\"queryType\":\"config\",\"listenersStatus\":{\"1.1.1.1\":\"testMd5\"}}";
        ConfigListenerInfo configListenerInfo = mapper.readValue(json, ConfigListenerInfo.class);
        assertEquals(ConfigListenerInfo.QUERY_TYPE_CONFIG, configListenerInfo.getQueryType());
        assertEquals("testMd5", configListenerInfo.getListenersStatus().get("1.1.1.1"));
    }
}