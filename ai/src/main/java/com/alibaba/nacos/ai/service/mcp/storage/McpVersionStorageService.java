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

import com.alibaba.nacos.ai.model.mcp.McpVersionStorageDescriptor;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.plugin.ai.storage.AiResourceStorageRouter;
import com.alibaba.nacos.plugin.ai.storage.model.StorageKey;
import com.alibaba.nacos.plugin.ai.storage.spi.AiResourceStorage;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Stores the three content objects of one MCP Version in dependency order.
 *
 * <p>This component owns content bytes only. Resource and Version rows, lifecycle state and
 * compatibility projections remain orchestration responsibilities.</p>
 *
 * @author Nacos
 */
@Service
public class McpVersionStorageService {
    
    private final AiResourceStorageRouter storageRouter;
    
    public McpVersionStorageService() {
        this(AiResourceStorageRouter.getInstance());
    }
    
    McpVersionStorageService(AiResourceStorageRouter storageRouter) {
        this.storageRouter = Objects.requireNonNull(storageRouter, "storageRouter");
    }
    
    /**
     * Save Tools and Resources before the Server object that references them.
     *
     * @param descriptor Version storage descriptor
     * @param contents exact content bytes
     * @throws NacosException when the selected provider fails
     */
    public void save(McpVersionStorageDescriptor descriptor, McpVersionStorageContents contents)
        throws NacosException {
        McpVersionStorageDescriptorSerializer.validate(descriptor);
        validateContents(descriptor, contents);
        if (descriptor.getToolKey() != null) {
            saveContent(descriptor, descriptor.getToolKey(), contents.getToolContent());
        }
        if (descriptor.getResourceKey() != null) {
            saveContent(descriptor, descriptor.getResourceKey(), contents.getResourceContent());
        }
        saveContent(descriptor, descriptor.getServerKey(), contents.getServerContent());
    }
    
    /**
     * Load the Server and every optional object selected by the persisted descriptor.
     *
     * @param descriptor Version storage descriptor
     * @return exact content bytes
     * @throws NacosException when content is missing or cannot be read
     */
    public McpVersionStorageContents load(McpVersionStorageDescriptor descriptor)
        throws NacosException {
        checkedDescriptor(descriptor);
        byte[] server = loadRequired(descriptor, descriptor.getServerKey(), "Server");
        byte[] tools = descriptor.getToolKey() == null ? null
            : loadRequired(descriptor, descriptor.getToolKey(), "Tools");
        byte[] resources = descriptor.getResourceKey() == null ? null
            : loadRequired(descriptor, descriptor.getResourceKey(), "Resources");
        return new McpVersionStorageContents(server, tools, resources);
    }
    
    /**
     * Attempt every referenced content deletion in Resources, Tools, Server order.
     *
     * @param descriptor Version storage descriptor
     * @throws NacosException after all attempts when at least one deletion fails
     */
    public void delete(McpVersionStorageDescriptor descriptor) throws NacosException {
        checkedDescriptor(descriptor);
        NacosException failure = null;
        if (descriptor.getResourceKey() != null) {
            failure = deleteContent(descriptor, descriptor.getResourceKey(), failure);
        }
        if (descriptor.getToolKey() != null) {
            failure = deleteContent(descriptor, descriptor.getToolKey(), failure);
        }
        failure = deleteContent(descriptor, descriptor.getServerKey(), failure);
        if (failure != null) {
            throw failure;
        }
    }
    
    private void validateContents(McpVersionStorageDescriptor descriptor,
        McpVersionStorageContents contents) {
        if (contents == null) {
            throw new IllegalArgumentException("MCP Version storage contents must not be null");
        }
        if ((descriptor.getToolKey() != null) != contents.hasToolContent()) {
            throw new IllegalArgumentException(
                "MCP Tools content and storage key presence must match");
        }
        if ((descriptor.getResourceKey() != null) != contents.hasResourceContent()) {
            throw new IllegalArgumentException(
                "MCP Resources content and storage key presence must match");
        }
    }
    
    private void checkedDescriptor(McpVersionStorageDescriptor descriptor) throws NacosException {
        try {
            McpVersionStorageDescriptorSerializer.validate(descriptor);
        } catch (IllegalArgumentException e) {
            throw storageFailure("Invalid MCP Version storage descriptor", e);
        }
    }
    
    private void saveContent(McpVersionStorageDescriptor descriptor, String key, byte[] content)
        throws NacosException {
        StorageKey storageKey = new StorageKey(descriptor.getProvider(), key);
        try {
            route(storageKey).save(storageKey, content);
        } catch (IllegalArgumentException e) {
            throw storageFailure("MCP Version content cannot be saved", e);
        }
    }
    
    private byte[] loadRequired(McpVersionStorageDescriptor descriptor, String key,
        String contentName) throws NacosException {
        StorageKey storageKey = new StorageKey(descriptor.getProvider(), key);
        final byte[] result;
        try {
            result = route(storageKey).get(storageKey);
        } catch (IllegalArgumentException e) {
            throw storageFailure("Invalid MCP Version storage key", e);
        }
        if (result == null || result.length == 0) {
            throw storageFailure("MCP " + contentName + " content does not exist", null);
        }
        return result;
    }
    
    private NacosException deleteContent(McpVersionStorageDescriptor descriptor, String key,
        NacosException previous) {
        StorageKey storageKey = new StorageKey(descriptor.getProvider(), key);
        try {
            route(storageKey).delete(storageKey);
            return previous;
        } catch (NacosException e) {
            return appendFailure(previous, e);
        } catch (RuntimeException e) {
            return appendFailure(previous, storageFailure("Invalid MCP Version storage key", e));
        }
    }
    
    private AiResourceStorage route(StorageKey storageKey) throws NacosException {
        try {
            return storageRouter.route(storageKey);
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw storageFailure(
                "MCP Version storage provider is unavailable: " + storageKey.getProvider(), e);
        }
    }
    
    private static NacosException appendFailure(NacosException previous, NacosException current) {
        if (previous == null) {
            return current;
        }
        previous.addSuppressed(current);
        return previous;
    }
    
    private static NacosException storageFailure(String message, Throwable cause) {
        return cause == null ? new NacosException(NacosException.SERVER_ERROR, message)
            : new NacosException(NacosException.SERVER_ERROR, message, cause);
    }
}
