/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.ai.service.mcp.storage;

import com.alibaba.nacos.ai.model.mcp.McpResourceExt;
import com.alibaba.nacos.api.exception.runtime.NacosSerializationException;
import com.alibaba.nacos.common.utils.JacksonUtils;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class McpResourceExtSerializerTest {
    
    private static final String MCP_ID = "4d7939c0-72ea-4ef4-b232-418d1e16b45c";
    
    @Test
    void testRoundTripUsesCanonicalProjection() {
        McpResourceExt original = new McpResourceExt();
        original.setSchemaVersion(McpResourceExt.SCHEMA_VERSION);
        original.setMcpId(MCP_ID);
        
        String json = McpResourceExtSerializer.serialize(original);
        McpResourceExt restored = McpResourceExtSerializer.deserialize(json);
        
        assertEquals("{\"schemaVersion\":1,\"mcpId\":\"" + MCP_ID + "\"}", json);
        assertEquals(McpResourceExt.SCHEMA_VERSION, restored.getSchemaVersion());
        assertEquals(MCP_ID, restored.getMcpId());
    }
    
    @Test
    void testAcceptUppercaseUuidHex() {
        String mcpId = "4D7939C0-72EA-4EF4-B232-418D1E16B45C";
        McpResourceExt result = McpResourceExtSerializer.deserialize(
            "{\"schemaVersion\":1,\"mcpId\":\"" + mcpId + "\"}");
        assertEquals(mcpId, result.getMcpId());
    }
    
    @Test
    void testRejectInvalidTypedValues() {
        McpResourceExt ext = new McpResourceExt();
        assertThrows(IllegalArgumentException.class, () -> McpResourceExtSerializer.validate(null));
        assertThrows(IllegalArgumentException.class, () -> McpResourceExtSerializer.serialize(ext));
        ext.setSchemaVersion(2);
        ext.setMcpId(MCP_ID);
        assertThrows(IllegalArgumentException.class, () -> McpResourceExtSerializer.serialize(ext));
        ext.setSchemaVersion(McpResourceExt.SCHEMA_VERSION);
        ext.setMcpId("not-a-uuid");
        assertThrows(IllegalArgumentException.class, () -> McpResourceExtSerializer.serialize(ext));
    }
    
    @Test
    void testSerializeWrapsJacksonFailure() {
        McpResourceExt ext = new McpResourceExt();
        ext.setSchemaVersion(McpResourceExt.SCHEMA_VERSION);
        ext.setMcpId(MCP_ID);
        try (MockedStatic<JacksonUtils> jacksonMock = Mockito.mockStatic(JacksonUtils.class)) {
            jacksonMock.when(() -> JacksonUtils.toJson(Mockito.any()))
                .thenThrow(new NacosSerializationException());
            assertThrows(IllegalArgumentException.class,
                () -> McpResourceExtSerializer.serialize(ext));
        }
    }
    
    @Test
    void testRejectInvalidJsonShape() {
        assertInvalid(null);
        assertInvalid("");
        assertInvalid("   ");
        assertInvalid("null");
        assertInvalid("[]");
        assertInvalid("not-json");
        assertInvalid("{\"schemaVersion\":1}");
        assertInvalid("{\"schemaVersion\":1.0,\"mcpId\":\"" + MCP_ID + "\"}");
        assertInvalid("{\"schemaVersion\":1,\"mcpId\":1}");
        assertInvalid("{\"schemaVersion\":1,\"mcpId\":\"" + MCP_ID
            + "\",\"extra\":true}");
        assertInvalid("{\"schemaVersion\":1,\"schemaVersion\":1,\"mcpId\":\""
            + MCP_ID + "\"}");
        assertInvalid("{\"schemaVersion\":1,\"mcpId\":\"" + MCP_ID + "\"}{}");
    }
    
    private void assertInvalid(String json) {
        assertThrows(IllegalArgumentException.class,
            () -> McpResourceExtSerializer.deserialize(json));
    }
}
