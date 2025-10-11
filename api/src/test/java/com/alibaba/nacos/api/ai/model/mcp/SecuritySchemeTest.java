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

package com.alibaba.nacos.api.ai.model.mcp;

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
        securityScheme.setId("sec-1");
        securityScheme.setType("apiKey");
        securityScheme.setScheme("bearer");
        securityScheme.setIn("header");
        securityScheme.setName("Authorization");
        securityScheme.setDefaultCredential("default-token");
        
        String json = mapper.writeValueAsString(securityScheme);
        assertTrue(json.contains("\"id\":\"sec-1\""));
        assertTrue(json.contains("\"type\":\"apiKey\""));
        assertTrue(json.contains("\"scheme\":\"bearer\""));
        assertTrue(json.contains("\"in\":\"header\""));
        assertTrue(json.contains("\"name\":\"Authorization\""));
        assertTrue(json.contains("\"defaultCredential\":\"default-token\""));
    }
    
    @Test
    void testDeserialize() throws JsonProcessingException {
        String json = "{\"id\":\"sec-1\",\"type\":\"apiKey\",\"scheme\":\"bearer\",\"in\":\"header\","
                + "\"name\":\"Authorization\",\"defaultCredential\":\"default-token\"}";
        
        SecurityScheme result = mapper.readValue(json, SecurityScheme.class);
        assertNotNull(result);
        assertEquals("sec-1", result.getId());
        assertEquals("apiKey", result.getType());
        assertEquals("bearer", result.getScheme());
        assertEquals("header", result.getIn());
        assertEquals("Authorization", result.getName());
        assertEquals("default-token", result.getDefaultCredential());
    }
}