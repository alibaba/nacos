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

import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.remote.request.BasicRequestTest;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueryMcpServerResponseTest extends BasicRequestTest {
    
    @Test
    void testSerialize() throws Exception {
        QueryMcpServerResponse response = new QueryMcpServerResponse();
        String id = UUID.randomUUID().toString();
        McpServerDetailInfo mcpServerDetailInfo = new McpServerDetailInfo();
        mcpServerDetailInfo.setId(id);
        mcpServerDetailInfo.setName("testMcpName");
        response.setMcpServerDetailInfo(mcpServerDetailInfo);
        response.setRequestId("1");
        String json = mapper.writeValueAsString(response);
        assertNotNull(json);
        assertTrue(json.contains("\"requestId\":\"1\""));
        assertTrue(json.contains("\"mcpServerDetailInfo\":{"));
        assertTrue(json.contains(String.format("\"id\":\"%s\"", id)));
        assertTrue(json.contains("\"name\":\"testMcpName\""));
        assertTrue(json.contains("\"enabled\":true"));
    }
    
    @Test
    void testDeserialize() throws Exception {
        String json = "{\"resultCode\":200,\"errorCode\":0,\"requestId\":\"1\",\"mcpServerDetailInfo\":"
                + "{\"id\":\"27dde181-cc8f-442f-a63d-2d2dc64735d8\",\"name\":\"testMcpName\",\"enabled\":true},\"success\":true}";
        QueryMcpServerResponse result = mapper.readValue(json, QueryMcpServerResponse.class);
        assertNotNull(result);
        assertEquals("1", result.getRequestId());
        McpServerDetailInfo mcpServerDetailInfo = result.getMcpServerDetailInfo();
        assertNotNull(mcpServerDetailInfo);
        assertEquals("27dde181-cc8f-442f-a63d-2d2dc64735d8", mcpServerDetailInfo.getId());
        assertEquals("testMcpName", mcpServerDetailInfo.getName());
        assertTrue(mcpServerDetailInfo.isEnabled());
    }
}