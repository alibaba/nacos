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

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.model.mcp.McpVersionStorageDescriptor;
import com.alibaba.nacos.api.exception.runtime.NacosSerializationException;
import com.alibaba.nacos.common.utils.JacksonUtils;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class McpVersionStorageDescriptorSerializerTest {
    
    private static final String MCP_ID = "4d7939c0-72ea-4ef4-b232-418d1e16b45c";
    
    @Test
    void testRoundTripWithAllContentKeys() {
        McpVersionStorageDescriptor original = descriptor();
        
        String json = McpVersionStorageDescriptorSerializer.serialize(original);
        McpVersionStorageDescriptor restored =
            McpVersionStorageDescriptorSerializer.deserialize(json);
        
        assertEquals("{\"provider\":\"nacos_config\",\"keyFormat\":\"mcp-config-v1\","
            + "\"serverKey\":\"public:mcp-server:" + MCP_ID + "-1.0.0-mcp-server.json\","
            + "\"toolKey\":\"public:mcp-tools:" + MCP_ID + "-1.0.0-mcp-tools.json\","
            + "\"resourceKey\":\"public:mcp-resources:" + MCP_ID
            + "-1.0.0-mcp-resources.json\",\"schemaVersion\":1}", json);
        assertEquals(original.getProvider(), restored.getProvider());
        assertEquals(original.getKeyFormat(), restored.getKeyFormat());
        assertEquals(original.getServerKey(), restored.getServerKey());
        assertEquals(original.getToolKey(), restored.getToolKey());
        assertEquals(original.getResourceKey(), restored.getResourceKey());
        assertEquals(original.getSchemaVersion(), restored.getSchemaVersion());
    }
    
    @Test
    void testOptionalKeysAreOmittedRatherThanNull() {
        McpVersionStorageDescriptor descriptor = descriptor();
        descriptor.setToolKey(null);
        descriptor.setResourceKey(null);
        
        String json = McpVersionStorageDescriptorSerializer.serialize(descriptor);
        McpVersionStorageDescriptor restored =
            McpVersionStorageDescriptorSerializer.deserialize(json);
        
        assertEquals("{\"provider\":\"nacos_config\",\"keyFormat\":\"mcp-config-v1\","
            + "\"serverKey\":\"public:mcp-server:" + MCP_ID
            + "-1.0.0-mcp-server.json\",\"schemaVersion\":1}", json);
        assertNull(restored.getToolKey());
        assertNull(restored.getResourceKey());
    }
    
    @Test
    void testParserTreatsDataIdRemainderAsOpaque() {
        McpVersionStorageDescriptor descriptor = descriptor();
        descriptor.setServerKey("public:mcp-server:" + MCP_ID
            + "-version:build-mcp-server.json");
        McpVersionStorageDescriptorSerializer.validate(descriptor);
    }
    
    @Test
    void testRejectInvalidDescriptorValues() {
        assertThrows(IllegalArgumentException.class,
            () -> McpVersionStorageDescriptorSerializer.validate(null));
        assertInvalidDescriptorValue(value -> value.setProvider("object-store"));
        assertInvalidDescriptorValue(value -> value.setKeyFormat("other"));
        assertInvalidDescriptorValue(value -> value.setSchemaVersion(2));
        assertInvalidDescriptorValue(value -> value.setServerKey(null));
        assertInvalidDescriptorValue(
            value -> value.setServerKey("public:mcp-tools:x-mcp-tools.json"));
        assertInvalidDescriptorValue(
            value -> value.setServerKey("public:mcp-server:mcp-server.json"));
        assertInvalidDescriptorValue(
            value -> value.setToolKey("public:mcp-server:x-mcp-server.json"));
        assertInvalidDescriptorValue(value -> value.setResourceKey(
            "other:mcp-resources:x-mcp-resources.json"));
        assertInvalidDescriptorValue(value -> value.setServerKey(
            repeat("a", 1025) + ":mcp-server:x-mcp-server.json"));
    }
    
    @Test
    void testRejectMalformedStorageCoordinates() {
        assertInvalidServerKey(":mcp-server:x-mcp-server.json");
        assertInvalidServerKey("public::x-mcp-server.json");
        assertInvalidServerKey("public:mcp-server:");
        assertInvalidServerKey("public:mcp-server:" + Constants.MCP_SERVER_SPEC_DATA_ID_SUFFIX);
        assertInvalidServerKey("public:mcp-server:long-invalid-server-coordinate.json");
    }
    
    @Test
    void testSerializeWrapsJacksonFailure() {
        McpVersionStorageDescriptor descriptor = descriptor();
        try (MockedStatic<JacksonUtils> jacksonMock = Mockito.mockStatic(JacksonUtils.class)) {
            jacksonMock.when(() -> JacksonUtils.toJson(Mockito.any()))
                .thenThrow(new NacosSerializationException());
            assertThrows(IllegalArgumentException.class,
                () -> McpVersionStorageDescriptorSerializer.serialize(descriptor));
        }
    }
    
    @Test
    void testRejectInvalidJsonShape() {
        String valid = McpVersionStorageDescriptorSerializer.serialize(descriptor());
        assertInvalidJson(null);
        assertInvalidJson("");
        assertInvalidJson("[]");
        assertInvalidJson("null");
        assertInvalidJson("not-json");
        assertInvalidJson(valid + "{}");
        assertInvalidJson(valid.substring(0, valid.length() - 1) + ",\"extra\":true}");
        assertInvalidJson(valid.replace("\"provider\":\"nacos_config\"", "\"provider\":1"));
        assertInvalidJson(valid.replace("\"schemaVersion\":1", "\"schemaVersion\":1.0"));
        assertInvalidJson(valid.replace("\"serverKey\":", "\"serverKey\":null,\"ignored\":"));
        assertInvalidJson(valid.replace("{\"provider\":\"nacos_config\"",
            "{\"provider\":\"nacos_config\",\"provider\":\"nacos_config\""));
    }
    
    private void assertInvalidDescriptorValue(DescriptorMutation mutation) {
        McpVersionStorageDescriptor descriptor = descriptor();
        mutation.apply(descriptor);
        assertThrows(IllegalArgumentException.class,
            () -> McpVersionStorageDescriptorSerializer.serialize(descriptor));
    }
    
    private void assertInvalidJson(String json) {
        assertThrows(IllegalArgumentException.class,
            () -> McpVersionStorageDescriptorSerializer.deserialize(json));
    }
    
    private void assertInvalidServerKey(String key) {
        assertThrows(IllegalArgumentException.class,
            () -> McpVersionStorageDescriptorSerializer.parseKey(key,
                Constants.MCP_SERVER_GROUP, Constants.MCP_SERVER_SPEC_DATA_ID_SUFFIX,
                "serverKey"));
    }
    
    private McpVersionStorageDescriptor descriptor() {
        return McpVersionStorageKeyComposer.compose("public", MCP_ID, "1.0.0", true, true);
    }
    
    private static String repeat(String value, int count) {
        StringBuilder result = new StringBuilder(value.length() * count);
        for (int i = 0; i < count; i++) {
            result.append(value);
        }
        return result.toString();
    }
    
    private interface DescriptorMutation {
        
        void apply(McpVersionStorageDescriptor descriptor);
    }
}
