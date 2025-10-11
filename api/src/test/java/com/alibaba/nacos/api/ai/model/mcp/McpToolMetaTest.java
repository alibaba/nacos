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

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpToolMetaTest extends BasicRequestTest {
    
    @Test
    void testSerialize() throws JsonProcessingException {
        McpToolMeta toolMeta = new McpToolMeta();
        toolMeta.setEnabled(true);
        
        Map<String, String> invokeContext = new HashMap<>();
        invokeContext.put("path", "/api/tool");
        invokeContext.put("method", "POST");
        toolMeta.setInvokeContext(invokeContext);
        
        Map<String, Object> templates = new HashMap<>();
        Map<String, Object> template = new HashMap<>();
        template.put("templateType", "json");
        templates.put("default", template);
        toolMeta.setTemplates(templates);
        
        String json = mapper.writeValueAsString(toolMeta);
        assertTrue(json.contains("\"enabled\":true"));
        assertTrue(json.contains("\"invokeContext\":{"));
        assertTrue(json.contains("\"path\":\"/api/tool\""));
        assertTrue(json.contains("\"method\":\"POST\""));
        assertTrue(json.contains("\"templates\":{"));
        assertTrue(json.contains("\"default\":{"));
        assertTrue(json.contains("\"templateType\":\"json\""));
    }
    
    @Test
    void testDeserialize() throws JsonProcessingException {
        String json = "{\"invokeContext\":{\"path\":\"/api/tool\",\"method\":\"POST\"},"
                + "\"enabled\":true,\"templates\":{\"default\":{\"templateType\":\"json\"}}}";
        
        McpToolMeta result = mapper.readValue(json, McpToolMeta.class);
        assertNotNull(result);
        assertTrue(result.isEnabled());
        assertNotNull(result.getInvokeContext());
        assertEquals("/api/tool", result.getInvokeContext().get("path"));
        assertEquals("POST", result.getInvokeContext().get("method"));
        assertNotNull(result.getTemplates());
        assertNotNull(result.getTemplates().get("default"));
        Map<String, Object> template = (Map<String, Object>) result.getTemplates().get("default");
        assertEquals("json", template.get("templateType"));
    }
}