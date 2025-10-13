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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NamedArgumentTest extends BasicRequestTest {
    
    @Test
    void testSerialize() throws JsonProcessingException {
        NamedArgument namedArgument = new NamedArgument();
        namedArgument.setType("named");
        namedArgument.setName("testArg");
        namedArgument.setValue("testValue");
        namedArgument.setDescription("test description");
        namedArgument.setIsRequired(true);
        namedArgument.setIsRepeated(false);
        
        String json = mapper.writeValueAsString(namedArgument);
        assertNotNull(json);
        assertTrue(json.contains("\"type\":\"named\""));
        assertTrue(json.contains("\"name\":\"testArg\""));
        assertTrue(json.contains("\"value\":\"testValue\""));
        assertTrue(json.contains("\"description\":\"test description\""));
        assertTrue(json.contains("\"isRequired\":true"));
        assertTrue(json.contains("\"isRepeated\":false"));
    }
    
    @Test
    void testDeserialize() throws JsonProcessingException {
        String json = "{\"type\":\"named\",\"name\":\"testArg\",\"value\":\"testValue\","
                + "\"description\":\"test description\",\"isRequired\":true,\"isRepeated\":false}";
        
        NamedArgument namedArgument = mapper.readValue(json, NamedArgument.class);
        assertNotNull(namedArgument);
        assertEquals("named", namedArgument.getType());
        assertEquals("testArg", namedArgument.getName());
        assertEquals("testValue", namedArgument.getValue());
        assertEquals("test description", namedArgument.getDescription());
        assertEquals(true, namedArgument.getIsRequired());
        assertEquals(false, namedArgument.getIsRepeated());
    }
}