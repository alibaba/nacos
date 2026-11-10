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

package com.alibaba.nacos.copilot.connection;

import io.agentscope.core.model.ModelCreationContext;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test for CopilotModelConnection.
 *
 * @author nacos
 */
class CopilotModelConnectionTest {
    
    @Test
    void testGetModelId() {
        CopilotModelConnection connection = CopilotModelConnection.builder()
            .providerId("dashscope")
            .model("qwen-plus")
            .build();
        
        assertEquals("dashscope:qwen-plus", connection.getModelId());
    }
    
    @Test
    void testToCreationContextPassesThroughFields() {
        CopilotModelConnection connection = CopilotModelConnection.builder()
            .apiKey("key")
            .baseUrl("https://example.com")
            .endpointPath("/v1")
            .stream(Boolean.TRUE)
            .enableThinking(Boolean.FALSE)
            .options(Collections.singletonMap("enableSearch", Boolean.TRUE))
            .build();
        
        ModelCreationContext context = connection.toCreationContext();
        
        assertEquals("key", context.getApiKey());
        assertEquals("https://example.com", context.getBaseUrl());
        assertEquals("/v1", context.getEndpointPath());
        assertTrue(context.getStream());
        assertEquals(Boolean.FALSE, context.getEnableThinking());
        assertEquals(Boolean.TRUE, context.option("enableSearch"));
    }
    
    @Test
    void testToCreationContextWithoutOptions() {
        CopilotModelConnection connection = CopilotModelConnection.builder().build();
        
        ModelCreationContext context = connection.toCreationContext();
        
        assertNull(context.getApiKey());
        assertTrue(context.getOptions().isEmpty());
    }
    
    @Test
    void testOptionsAreDefensivelyCopied() {
        Map<String, Object> options = new HashMap<>();
        options.put("enableSearch", Boolean.TRUE);
        CopilotModelConnection connection = CopilotModelConnection.builder()
            .options(options)
            .build();
        
        options.put("enableSearch", Boolean.FALSE);
        
        assertEquals(Boolean.TRUE, connection.getOptions().get("enableSearch"));
        assertThrows(UnsupportedOperationException.class,
            () -> connection.getOptions().put("another", Boolean.TRUE));
    }
    
    @Test
    void testEnabledByDefault() {
        assertTrue(CopilotModelConnection.builder().build().isEnabled());
    }
}
