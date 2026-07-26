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

import com.alibaba.nacos.ai.model.agent.AgentVersionContent;
import com.alibaba.nacos.ai.model.agent.AgentVersionStorageDescriptor;
import com.alibaba.nacos.ai.storage.NacosConfigAiResourceStorage;
import com.alibaba.nacos.api.ai.model.agent.AgentCallInterface;
import com.alibaba.nacos.api.ai.model.agent.EndpointSource;

import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PreparedAgentVersionWriteTest {
    
    private static final String STORAGE_KEY =
        "public:agent-version:agent__enc-Nacos-032Agent__1.0.0-RC1.json";
    
    private static final String DIFFERENT_DIGEST =
        "sha256:0000000000000000000000000000000000000000000000000000000000000000";
    
    @Test
    void testReturnsDefensiveDescriptorAndByteCopies() {
        AgentVersionContentSerializer.SerializedContent encoded = encodedContent();
        AgentVersionStorageDescriptor inputDescriptor = descriptor(encoded);
        PreparedAgentVersionWrite prepared =
            new PreparedAgentVersionWrite(inputDescriptor, encoded);
        inputDescriptor.setProvider("tampered-input");
        inputDescriptor.setKey("tampered-input");
        
        AgentVersionStorageDescriptor firstDescriptor = prepared.getDescriptor();
        byte[] firstBytes = prepared.getBytes();
        firstDescriptor.setProvider("tampered");
        firstDescriptor.setKey("tampered");
        firstDescriptor.setContentDigest(DIFFERENT_DIGEST);
        firstBytes[0] = (byte) (firstBytes[0] + 1);
        
        AgentVersionStorageDescriptor secondDescriptor = prepared.getDescriptor();
        byte[] secondBytes = prepared.getBytes();
        assertNotSame(firstDescriptor, secondDescriptor);
        assertNotSame(firstBytes, secondBytes);
        assertEquals(NacosConfigAiResourceStorage.TYPE, secondDescriptor.getProvider());
        assertEquals(STORAGE_KEY, secondDescriptor.getKey());
        assertEquals(encoded.getContentDigest(), secondDescriptor.getContentDigest());
        assertArrayEquals(encoded.getBytes(), secondBytes);
        assertEquals(NacosConfigAiResourceStorage.TYPE, prepared.getStorageKey().getProvider());
        assertEquals(STORAGE_KEY, prepared.getStorageKey().getKey());
    }
    
    @Test
    void testRejectsInvalidDescriptor() {
        AgentVersionContentSerializer.SerializedContent encoded = encodedContent();
        AgentVersionStorageDescriptor descriptor = descriptor(encoded);
        descriptor.setKey(null);
        
        assertThrows(IllegalArgumentException.class,
            () -> new PreparedAgentVersionWrite(descriptor, encoded));
        assertThrows(IllegalArgumentException.class,
            () -> new PreparedAgentVersionWrite(null, encoded));
    }
    
    @Test
    void testRejectsMismatchedSize() {
        AgentVersionContentSerializer.SerializedContent encoded = encodedContent();
        AgentVersionStorageDescriptor descriptor = descriptor(encoded);
        descriptor.setSize(descriptor.getSize() + 1);
        
        assertThrows(IllegalArgumentException.class,
            () -> new PreparedAgentVersionWrite(descriptor, encoded));
    }
    
    @Test
    void testRejectsMismatchedDigest() {
        AgentVersionContentSerializer.SerializedContent encoded = encodedContent();
        AgentVersionStorageDescriptor descriptor = descriptor(encoded);
        descriptor.setContentDigest(DIFFERENT_DIGEST);
        
        assertThrows(IllegalArgumentException.class,
            () -> new PreparedAgentVersionWrite(descriptor, encoded));
    }
    
    @Test
    void testRejectsNullSerializedContent() {
        assertThrows(IllegalArgumentException.class,
            () -> new PreparedAgentVersionWrite(descriptor(encodedContent()), null));
    }
    
    private AgentVersionContentSerializer.SerializedContent encodedContent() {
        AgentCallInterface callInterface = new AgentCallInterface();
        callInterface.setProtocol("a2a");
        callInterface.setDescriptorMediaType("application/json");
        callInterface.setNativeDescriptor("descriptor");
        callInterface.setEndpointSourceOrder(Collections.singletonList(EndpointSource.RUNTIME));
        AgentVersionContent content =
            new AgentVersionContent(Collections.singletonList(callInterface));
        return AgentVersionContentSerializer.serialize(content);
    }
    
    private AgentVersionStorageDescriptor descriptor(
        AgentVersionContentSerializer.SerializedContent encoded) {
        AgentVersionStorageDescriptor result = new AgentVersionStorageDescriptor();
        result.setProvider(NacosConfigAiResourceStorage.TYPE);
        result.setKey(STORAGE_KEY);
        result.setKeyFormat(AgentVersionStorageDescriptor.NACOS_CONFIG_KEY_FORMAT);
        result.setAgentNameCodec(AgentVersionStorageDescriptor.RAD_AGENT_NAME_CODEC);
        result.setContentDigest(encoded.getContentDigest());
        result.setMediaType(AgentVersionStorageDescriptor.MEDIA_TYPE);
        result.setSchemaVersion(AgentVersionStorageDescriptor.SCHEMA_VERSION);
        result.setSize((long) encoded.getSize());
        return result;
    }
}
