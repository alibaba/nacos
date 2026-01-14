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

package com.alibaba.nacos.api.ai.model.a2a;

import com.alibaba.nacos.api.remote.request.BasicRequestTest;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecuritySchemeTest extends BasicRequestTest {
    
    @Test
    void testSerialize() throws JsonProcessingException {
        SecurityScheme securityScheme = new SecurityScheme();
        securityScheme.put("type", "apiKey");
        securityScheme.put("name", "Authorization");
        securityScheme.put("in", "header");
        
        String json = mapper.writeValueAsString(securityScheme);
        assertNotNull(json);
        assertTrue(json.contains("\"type\":\"apiKey\""));
        assertTrue(json.contains("\"name\":\"Authorization\""));
        assertTrue(json.contains("\"in\":\"header\""));
    }
    
    @Test
    void testDeserialize() throws JsonProcessingException {
        String json = "{\"type\":\"apiKey\",\"name\":\"Authorization\",\"in\":\"header\"}";
        
        SecurityScheme securityScheme = mapper.readValue(json, SecurityScheme.class);
        assertNotNull(securityScheme);
        assertEquals("apiKey", securityScheme.get("type"));
        assertEquals("Authorization", securityScheme.get("name"));
        assertEquals("header", securityScheme.get("in"));
    }
}