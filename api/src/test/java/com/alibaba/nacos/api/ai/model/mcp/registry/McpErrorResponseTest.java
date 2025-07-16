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
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpErrorResponseTest extends BasicRequestTest {
    
    @Test
    void testSerialize() throws JsonProcessingException {
        McpErrorResponse errorResponse = new McpErrorResponse();
        errorResponse.setError("test error");
        String json = mapper.writeValueAsString(errorResponse);
        assertTrue(json.contains("\"error\":\"test error\""));
    }
    
    @Test
    void testDeserialize() throws JsonProcessingException {
        String json = "{\"error\":\"test error\"}";
        McpErrorResponse errorResponse = mapper.readValue(json, McpErrorResponse.class);
        assertEquals("test error", errorResponse.getError());
    }
}