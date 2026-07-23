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

package com.alibaba.nacos.ai.model.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AgentVersionStorageDescriptorTest {
    
    @Test
    void testDefaultConstructorAndAccessors() {
        AgentVersionStorageDescriptor descriptor = new AgentVersionStorageDescriptor();
        assertNull(descriptor.getProvider());
        assertNull(descriptor.getKey());
        assertNull(descriptor.getKeyFormat());
        assertNull(descriptor.getAgentNameCodec());
        assertNull(descriptor.getContentDigest());
        assertNull(descriptor.getMediaType());
        assertNull(descriptor.getSchemaVersion());
        assertNull(descriptor.getSize());
        
        descriptor.setProvider("nacos_config");
        descriptor.setKey("logical-key");
        descriptor.setKeyFormat("agent-version-config-v1");
        descriptor.setAgentNameCodec("rad-ascii-v1");
        descriptor.setContentDigest(digest('a'));
        descriptor.setMediaType("application/vnd.nacos.agent-version+json");
        descriptor.setSchemaVersion(1);
        descriptor.setSize(128L);
        
        assertEquals("nacos_config", descriptor.getProvider());
        assertEquals("logical-key", descriptor.getKey());
        assertEquals("agent-version-config-v1", descriptor.getKeyFormat());
        assertEquals("rad-ascii-v1", descriptor.getAgentNameCodec());
        assertEquals(digest('a'), descriptor.getContentDigest());
        assertEquals("application/vnd.nacos.agent-version+json", descriptor.getMediaType());
        assertEquals(1, descriptor.getSchemaVersion());
        assertEquals(128L, descriptor.getSize());
    }
    
    @Test
    void testJacksonRoundTripUsesPlainBeanContract() throws Exception {
        AgentVersionStorageDescriptor original = createDescriptor();
        ObjectMapper mapper = new ObjectMapper();
        
        String json = mapper.writeValueAsString(original);
        AgentVersionStorageDescriptor restored =
            mapper.readValue(json, AgentVersionStorageDescriptor.class);
        
        assertEquals(original.getProvider(), restored.getProvider());
        assertEquals(original.getKey(), restored.getKey());
        assertEquals(original.getKeyFormat(), restored.getKeyFormat());
        assertEquals(original.getAgentNameCodec(), restored.getAgentNameCodec());
        assertEquals(original.getContentDigest(), restored.getContentDigest());
        assertEquals(original.getMediaType(), restored.getMediaType());
        assertEquals(original.getSchemaVersion(), restored.getSchemaVersion());
        assertEquals(original.getSize(), restored.getSize());
    }
    
    private AgentVersionStorageDescriptor createDescriptor() {
        AgentVersionStorageDescriptor result = new AgentVersionStorageDescriptor();
        result.setProvider("nacos_config");
        result.setKey("logical-key");
        result.setKeyFormat("agent-version-config-v1");
        result.setAgentNameCodec("rad-ascii-v1");
        result.setContentDigest(digest('a'));
        result.setMediaType("application/vnd.nacos.agent-version+json");
        result.setSchemaVersion(1);
        result.setSize(128L);
        return result;
    }
    
    private String digest(char value) {
        StringBuilder result = new StringBuilder("sha256:");
        while (result.length() < 71) {
            result.append(value);
        }
        return result.toString();
    }
}
