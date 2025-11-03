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

package com.alibaba.nacos.api.ai.model.mcp.registry;

import com.alibaba.nacos.api.remote.request.BasicRequestTest;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemoteTest extends BasicRequestTest {
    
    @Test
    void testSerialize() throws JsonProcessingException {
        Remote remote = new Remote();
        remote.setType("https");
        remote.setUrl("https://test.server.com/api");
        
        KeyValueInput header = new KeyValueInput();
        header.setName("Authorization");
        header.setValue("Bearer token");
        remote.setHeaders(Collections.singletonList(header));
        
        String json = mapper.writeValueAsString(remote);
        assertNotNull(json);
        assertTrue(json.contains("\"type\":\"https\""));
        assertTrue(json.contains("\"url\":\"https://test.server.com/api\""));
        assertTrue(json.contains("\"headers\":["));
        assertTrue(json.contains("\"name\":\"Authorization\""));
        assertTrue(json.contains("\"value\":\"Bearer token\""));
    }
    
    @Test
    void testDeserialize() throws JsonProcessingException {
        String json = "{\"type\":\"https\",\"url\":\"https://test.server.com/api\","
                + "\"headers\":[{\"name\":\"Authorization\",\"value\":\"Bearer token\"}]}";
        
        Remote remote = mapper.readValue(json, Remote.class);
        assertNotNull(remote);
        assertEquals("https", remote.getType());
        assertEquals("https://test.server.com/api", remote.getUrl());
        assertEquals(1, remote.getHeaders().size());
        assertEquals("Authorization", remote.getHeaders().get(0).getName());
        assertEquals("Bearer token", remote.getHeaders().get(0).getValue());
    }
}