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
import com.alibaba.nacos.plugin.ai.storage.model.StorageKey;

import java.util.Arrays;

/**
 * Immutable operation-scoped payload for one Agent Version storage write.
 *
 * <p>The serialized bytes and descriptor are calculated together before any storage write. This lets
 * the Agent persistence orchestration layer persist the descriptor with insert-only semantics
 * before writing the matching content object. This object is never persisted or exposed through
 * an Agent API.</p>
 *
 * @author Nacos
 */
public final class PreparedAgentVersionWrite {
    
    private final AgentVersionStorageDescriptor descriptor;
    
    private final byte[] bytes;
    
    PreparedAgentVersionWrite(AgentVersionStorageDescriptor descriptor,
        AgentVersionContentSerializer.SerializedContent serializedContent) {
        if (serializedContent == null) {
            throw new IllegalArgumentException("Serialized Agent Version content must not be null");
        }
        AgentVersionStorageDescriptorSerializer.validate(descriptor);
        if (descriptor.getSize().longValue() != serializedContent.getSize()) {
            throw new IllegalArgumentException(
                "Prepared Agent Version content size does not match its descriptor");
        }
        if (!descriptor.getContentDigest().equals(serializedContent.getContentDigest())) {
            throw new IllegalArgumentException(
                "Prepared Agent Version content digest does not match its descriptor");
        }
        this.descriptor = copyDescriptor(descriptor);
        bytes = serializedContent.getBytes();
    }
    
    /**
     * Return an independent descriptor for persistence in the Agent Version row.
     *
     * @return defensive descriptor copy
     */
    public AgentVersionStorageDescriptor getDescriptor() {
        return copyDescriptor(descriptor);
    }
    
    /**
     * Return a defensive copy of the exact bytes prepared for AI Storage.
     *
     * @return prepared content bytes
     */
    public byte[] getBytes() {
        return Arrays.copyOf(bytes, bytes.length);
    }
    
    StorageKey getStorageKey() {
        return new StorageKey(descriptor.getProvider(), descriptor.getKey());
    }
    
    private static AgentVersionStorageDescriptor copyDescriptor(
        AgentVersionStorageDescriptor source) {
        AgentVersionStorageDescriptor result = new AgentVersionStorageDescriptor();
        result.setProvider(source.getProvider());
        result.setKey(source.getKey());
        result.setKeyFormat(source.getKeyFormat());
        result.setAgentNameCodec(source.getAgentNameCodec());
        result.setContentDigest(source.getContentDigest());
        result.setMediaType(source.getMediaType());
        result.setSchemaVersion(source.getSchemaVersion());
        result.setSize(source.getSize());
        return result;
    }
}
