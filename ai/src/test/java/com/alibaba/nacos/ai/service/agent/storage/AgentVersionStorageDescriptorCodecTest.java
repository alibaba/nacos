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

package com.alibaba.nacos.ai.service.agent.storage;

import com.alibaba.nacos.ai.model.agent.AgentVersionStorageDescriptor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentVersionStorageDescriptorCodecTest {
    
    @Test
    void testNacosConfigRoundTrip() {
        AgentVersionStorageDescriptor original = createNacosConfigDescriptor();
        String json = AgentVersionStorageDescriptorCodec.encode(original);
        
        assertTrue(json.contains("\"provider\":\"nacos_config\""));
        assertTrue(json.contains("\"keyFormat\":\"agent-version-config-v1\""));
        AgentVersionStorageDescriptor restored = AgentVersionStorageDescriptorCodec.decode(json);
        assertDescriptorEquals(original, restored);
    }
    
    @Test
    void testCustomProviderMayOmitProviderSpecificFields() {
        AgentVersionStorageDescriptor descriptor = createNacosConfigDescriptor();
        descriptor.setProvider("object_store-v2");
        descriptor.setKeyFormat(null);
        descriptor.setAgentNameCodec(null);
        
        String json = AgentVersionStorageDescriptorCodec.encode(descriptor);
        assertFalse(json.contains("keyFormat"));
        assertFalse(json.contains("agentNameCodec"));
        AgentVersionStorageDescriptor restored = AgentVersionStorageDescriptorCodec.decode(json);
        assertNull(restored.getKeyFormat());
        assertNull(restored.getAgentNameCodec());
    }
    
    @Test
    void testAcceptSchemaBoundaries() {
        AgentVersionStorageDescriptor descriptor = createNacosConfigDescriptor();
        descriptor.setProvider("a" + repeat('b', 63));
        descriptor.setKey(repeat('k', 1024));
        descriptor.setKeyFormat(repeat('f', 64));
        descriptor.setAgentNameCodec(repeat('c', 64));
        descriptor.setSize((long) AgentVersionStorageDescriptorCodec.MAX_CONTENT_SIZE);
        AgentVersionStorageDescriptorCodec.decode(
            AgentVersionStorageDescriptorCodec.encode(descriptor));
        
        descriptor.setProvider("nacos_config");
        descriptor.setKeyFormat(AgentVersionStorageDescriptorCodec.NACOS_CONFIG_KEY_FORMAT);
        descriptor.setAgentNameCodec(
            AgentVersionStorageDescriptorCodec.RAD_ASCII_AGENT_NAME_CODEC);
        descriptor.setSize(0L);
        AgentVersionStorageDescriptorCodec.encode(descriptor);
    }
    
    @Test
    void testRejectUnknownOrInvalidJsonShape() {
        String valid = AgentVersionStorageDescriptorCodec.encode(createNacosConfigDescriptor());
        assertThrows(IllegalArgumentException.class,
            () -> AgentVersionStorageDescriptorCodec.encode(null));
        assertThrows(IllegalArgumentException.class,
            () -> AgentVersionStorageDescriptorCodec.decode(null));
        assertThrows(IllegalArgumentException.class,
            () -> AgentVersionStorageDescriptorCodec.decode(""));
        assertThrows(IllegalArgumentException.class,
            () -> AgentVersionStorageDescriptorCodec.decode("not-json"));
        assertThrows(IllegalArgumentException.class,
            () -> AgentVersionStorageDescriptorCodec.decode("[]"));
        assertThrows(IllegalArgumentException.class,
            () -> AgentVersionStorageDescriptorCodec.decode("null"));
        assertThrows(IllegalArgumentException.class,
            () -> AgentVersionStorageDescriptorCodec.decode("1"));
        assertThrows(IllegalArgumentException.class,
            () -> AgentVersionStorageDescriptorCodec.decode("\"descriptor\""));
        assertThrows(IllegalArgumentException.class,
            () -> AgentVersionStorageDescriptorCodec.decode(
                valid.replace("\"provider\":\"nacos_config\",", "")));
        assertThrows(IllegalArgumentException.class,
            () -> AgentVersionStorageDescriptorCodec.decode(
                valid.replace("\"provider\":\"nacos_config\"", "\"provider\":1")));
        assertThrows(IllegalArgumentException.class,
            () -> AgentVersionStorageDescriptorCodec.decode(
                valid.substring(0, valid.length() - 1) + ",\"extra\":true}"));
        assertThrows(IllegalArgumentException.class,
            () -> AgentVersionStorageDescriptorCodec.decode(valid + "{}"));
        assertThrows(IllegalArgumentException.class,
            () -> AgentVersionStorageDescriptorCodec.decode(
                valid.replace("{\"provider\":\"nacos_config\"",
                    "{\"provider\":\"nacos_config\",\"provider\":\"nacos_config\"")));
        assertThrows(IllegalArgumentException.class,
            () -> AgentVersionStorageDescriptorCodec.decode(
                valid.replace("\"size\":128", "\"size\":\"large\"")));
    }
    
    @Test
    void testRejectMissingRequiredJsonFields() {
        String valid = AgentVersionStorageDescriptorCodec.encode(createNacosConfigDescriptor());
        assertThrows(IllegalArgumentException.class,
            () -> AgentVersionStorageDescriptorCodec.decode(
                valid.replace("\"key\":\"namespace:agent-version:agent__Agent__1.0.0.json\",",
                    "")));
        assertThrows(IllegalArgumentException.class,
            () -> AgentVersionStorageDescriptorCodec.decode(
                valid.replace("\"contentDigest\":\"sha256:" + repeat('a', 64) + "\",", "")));
        assertThrows(IllegalArgumentException.class,
            () -> AgentVersionStorageDescriptorCodec.decode(
                valid.replace("\"mediaType\":\"application/vnd.nacos.agent-version+json\",",
                    "")));
        assertThrows(IllegalArgumentException.class,
            () -> AgentVersionStorageDescriptorCodec.decode(
                valid.replace("\"schemaVersion\":1,", "")));
        assertThrows(IllegalArgumentException.class,
            () -> AgentVersionStorageDescriptorCodec.decode(
                valid.replace(",\"size\":128", "")));
    }
    
    @Test
    void testRejectWrongJsonFieldTypes() {
        String valid = AgentVersionStorageDescriptorCodec.encode(createNacosConfigDescriptor());
        assertThrows(IllegalArgumentException.class,
            () -> AgentVersionStorageDescriptorCodec.decode(
                valid.replace("\"key\":\"namespace:agent-version:agent__Agent__1.0.0.json\"",
                    "\"key\":1")));
        assertThrows(IllegalArgumentException.class,
            () -> AgentVersionStorageDescriptorCodec.decode(
                valid.replace("\"keyFormat\":\"agent-version-config-v1\"",
                    "\"keyFormat\":null")));
        assertThrows(IllegalArgumentException.class,
            () -> AgentVersionStorageDescriptorCodec.decode(
                valid.replace("\"agentNameCodec\":\"rad-ascii-v1\"",
                    "\"agentNameCodec\":false")));
        assertThrows(IllegalArgumentException.class,
            () -> AgentVersionStorageDescriptorCodec.decode(
                valid.replace("\"contentDigest\":\"sha256:" + repeat('a', 64) + "\"",
                    "\"contentDigest\":{}")));
        assertThrows(IllegalArgumentException.class,
            () -> AgentVersionStorageDescriptorCodec.decode(
                valid.replace("\"mediaType\":\"application/vnd.nacos.agent-version+json\"",
                    "\"mediaType\":[]")));
        assertThrows(IllegalArgumentException.class,
            () -> AgentVersionStorageDescriptorCodec.decode(
                valid.replace("\"schemaVersion\":1", "\"schemaVersion\":1.0")));
        assertThrows(IllegalArgumentException.class,
            () -> AgentVersionStorageDescriptorCodec.decode(
                valid.replace("\"size\":128", "\"size\":128.5")));
    }
    
    @Test
    void testRejectInvalidProvider() {
        assertRejected(null, descriptor -> descriptor.setProvider(null));
        assertRejected(null, descriptor -> descriptor.setProvider(""));
        assertRejected(null, descriptor -> descriptor.setProvider("-provider"));
        assertRejected(null, descriptor -> descriptor.setProvider("provider.dot"));
        assertRejected(null, descriptor -> descriptor.setProvider("a" + repeat('b', 64)));
    }
    
    @Test
    void testRejectInvalidKeyAndOptionalProviderFields() {
        assertRejected(null, descriptor -> descriptor.setKey(null));
        assertRejected(null, descriptor -> descriptor.setKey(""));
        assertRejected(null, descriptor -> descriptor.setKey(repeat('k', 1025)));
        assertRejected("custom", descriptor -> descriptor.setKeyFormat(""));
        assertRejected("custom", descriptor -> descriptor.setKeyFormat(repeat('f', 65)));
        assertRejected("custom", descriptor -> descriptor.setAgentNameCodec(""));
        assertRejected("custom", descriptor -> descriptor.setAgentNameCodec(repeat('c', 65)));
    }
    
    @Test
    void testRejectInvalidDigestMediaTypeSchemaAndSize() {
        assertRejected(null, descriptor -> descriptor.setContentDigest(null));
        assertRejected(null,
            descriptor -> descriptor.setContentDigest("sha256:" + repeat('a', 63)));
        assertRejected(null,
            descriptor -> descriptor.setContentDigest("sha256:" + repeat('A', 64)));
        assertRejected(null, descriptor -> descriptor.setMediaType(null));
        assertRejected(null, descriptor -> descriptor.setMediaType("application/json"));
        assertRejected(null, descriptor -> descriptor.setSchemaVersion(null));
        assertRejected(null, descriptor -> descriptor.setSchemaVersion(2));
        assertRejected(null, descriptor -> descriptor.setSize(null));
        assertRejected(null, descriptor -> descriptor.setSize(-1L));
        assertRejected(null,
            descriptor -> descriptor.setSize(
                (long) AgentVersionStorageDescriptorCodec.MAX_CONTENT_SIZE + 1));
    }
    
    @Test
    void testRejectInvalidNacosConfigProviderContract() {
        assertRejected(null, descriptor -> descriptor.setKeyFormat(null));
        assertRejected(null, descriptor -> descriptor.setKeyFormat("other"));
        assertRejected(null, descriptor -> descriptor.setAgentNameCodec(null));
        assertRejected(null, descriptor -> descriptor.setAgentNameCodec("other"));
    }
    
    private void assertRejected(String provider, DescriptorMutation mutation) {
        AgentVersionStorageDescriptor descriptor = createNacosConfigDescriptor();
        if (provider != null) {
            descriptor.setProvider(provider);
        }
        mutation.apply(descriptor);
        assertThrows(IllegalArgumentException.class,
            () -> AgentVersionStorageDescriptorCodec.encode(descriptor));
    }
    
    private void assertDescriptorEquals(AgentVersionStorageDescriptor expected,
        AgentVersionStorageDescriptor actual) {
        assertEquals(expected.getProvider(), actual.getProvider());
        assertEquals(expected.getKey(), actual.getKey());
        assertEquals(expected.getKeyFormat(), actual.getKeyFormat());
        assertEquals(expected.getAgentNameCodec(), actual.getAgentNameCodec());
        assertEquals(expected.getContentDigest(), actual.getContentDigest());
        assertEquals(expected.getMediaType(), actual.getMediaType());
        assertEquals(expected.getSchemaVersion(), actual.getSchemaVersion());
        assertEquals(expected.getSize(), actual.getSize());
    }
    
    private AgentVersionStorageDescriptor createNacosConfigDescriptor() {
        AgentVersionStorageDescriptor result = new AgentVersionStorageDescriptor();
        result.setProvider(AgentVersionStorageDescriptorCodec.NACOS_CONFIG_PROVIDER);
        result.setKey("namespace:agent-version:agent__Agent__1.0.0.json");
        result.setKeyFormat(AgentVersionStorageDescriptorCodec.NACOS_CONFIG_KEY_FORMAT);
        result.setAgentNameCodec(AgentVersionStorageDescriptorCodec.RAD_ASCII_AGENT_NAME_CODEC);
        result.setContentDigest("sha256:" + repeat('a', 64));
        result.setMediaType(AgentVersionStorageDescriptorCodec.AGENT_VERSION_MEDIA_TYPE);
        result.setSchemaVersion(AgentVersionStorageDescriptorCodec.SCHEMA_VERSION);
        result.setSize(128L);
        return result;
    }
    
    private String repeat(char value, int count) {
        StringBuilder result = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            result.append(value);
        }
        return result.toString();
    }
    
    private interface DescriptorMutation {
        
        void apply(AgentVersionStorageDescriptor descriptor);
    }
}
