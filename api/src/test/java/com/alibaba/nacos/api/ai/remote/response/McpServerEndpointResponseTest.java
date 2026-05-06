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

package com.alibaba.nacos.api.ai.remote.response;

import com.alibaba.nacos.api.ai.remote.AiRemoteConstants;
import com.alibaba.nacos.api.remote.request.BasicRequestTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpServerEndpointResponseTest extends BasicRequestTest {
    
    @Test
    void testSerialize() throws Exception {
        McpServerEndpointResponse response = new McpServerEndpointResponse();
        response.setRequestId("1");
        response.setType(AiRemoteConstants.REGISTER_ENDPOINT);
        String json = mapper.writeValueAsString(response);
        assertNotNull(json);
        assertTrue(json.contains("\"requestId\":\"1\""));
        assertTrue(json.contains(String.format("\"type\":\"%s\"", AiRemoteConstants.REGISTER_ENDPOINT)));
    }
    
    @Test
    void testDeserialize() throws Exception {
        String json = "{\"resultCode\":200,\"errorCode\":0,\"requestId\":\"1\",\"type\":\"registerEndpoint\",\"success\":true}";
        McpServerEndpointResponse result = mapper.readValue(json, McpServerEndpointResponse.class);
        assertNotNull(result);
        assertEquals("1", result.getRequestId());
        assertEquals(AiRemoteConstants.REGISTER_ENDPOINT, result.getType());
    }
}