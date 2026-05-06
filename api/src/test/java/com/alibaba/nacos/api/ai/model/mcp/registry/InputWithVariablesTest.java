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

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InputWithVariablesTest extends BasicRequestTest {
    
    @Test
    void testSerialize() throws JsonProcessingException {
        InputWithVariables inputWithVariables = new InputWithVariables();
        inputWithVariables.setDescription("test description");
        
        Map<String, Input> variables = new HashMap<>();
        Input varInput = new Input();
        varInput.setDescription("variable description");
        variables.put("var1", varInput);
        inputWithVariables.setVariables(variables);
        
        String json = mapper.writeValueAsString(inputWithVariables);
        assertNotNull(json);
        assertTrue(json.contains("\"description\":\"test description\""));
        assertTrue(json.contains("\"variables\":"));
        assertTrue(json.contains("\"var1\":"));
        assertTrue(json.contains("\"variable description\""));
    }
    
    @Test
    void testDeserialize() throws JsonProcessingException {
        String json = "{\"description\":\"test description\",\"variables\":{\"var1\":{\"description\":\"variable description\"}}}";
        
        InputWithVariables inputWithVariables = mapper.readValue(json, InputWithVariables.class);
        assertNotNull(inputWithVariables);
        assertEquals("test description", inputWithVariables.getDescription());
        assertEquals(1, inputWithVariables.getVariables().size());
        assertEquals("variable description", inputWithVariables.getVariables().get("var1").getDescription());
    }
}