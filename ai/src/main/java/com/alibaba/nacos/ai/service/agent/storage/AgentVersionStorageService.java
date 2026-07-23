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

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.model.agent.AgentVersionContent;
import com.alibaba.nacos.ai.model.agent.AgentVersionStorageDescriptor;
import com.alibaba.nacos.ai.service.agent.fingerprint.AgentVersionContentCodec;
import com.alibaba.nacos.ai.storage.NacosConfigAiResourceStorage;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.ai.storage.AiResourceStorageRouter;
import com.alibaba.nacos.plugin.ai.storage.model.StorageKey;
import com.alibaba.nacos.plugin.ai.storage.spi.AiResourceStorage;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Stores and verifies the complete content of one Agent Version.
 *
 * <p>This component owns only the content object and its storage pointer. Resource and Version row
 * lifecycle, cross-store compensation, and derived catalog rebuilding remain the responsibility of
 * the Agent persistence orchestration layer.</p>
 *
 * @author Nacos
 */
@Service
public class AgentVersionStorageService {
    
    private static final String DEFAULT_STORAGE_PROVIDER = NacosConfigAiResourceStorage.TYPE;
    
    private final AiResourceStorageRouter storageRouter;
    
    private final Supplier<String> storageProviderSupplier;
    
    public AgentVersionStorageService() {
        this(AiResourceStorageRouter.getInstance(), AgentVersionStorageService::configuredProvider);
    }
    
    AgentVersionStorageService(AiResourceStorageRouter storageRouter,
        Supplier<String> storageProviderSupplier) {
        this.storageRouter = Objects.requireNonNull(storageRouter, "storageRouter");
        this.storageProviderSupplier = Objects.requireNonNull(storageProviderSupplier,
            "storageProviderSupplier");
    }
    
    /**
     * Encode and save one Agent Version content object at its stable logical key.
     *
     * @param namespaceId namespace identifier
     * @param agentName Agent public name
     * @param version exact Agent Version
     * @param content complete Agent Version content
     * @return descriptor persisted in the matching {@code ai_resource_version.storage} field
     * @throws NacosException when the selected storage provider fails
     */
    public AgentVersionStorageDescriptor save(String namespaceId, String agentName, String version,
        AgentVersionContent content) throws NacosException {
        String provider = resolveStorageProvider();
        StorageKey storageKey = AgentVersionStorageKeyComposer.compose(provider, namespaceId,
            agentName, version);
        AgentVersionContentCodec.EncodedContent encoded = AgentVersionContentCodec.encode(content);
        AgentVersionStorageDescriptor descriptor = buildDescriptor(storageKey, encoded);
        AgentVersionStorageDescriptorCodec.validate(descriptor);
        try {
            route(storageKey).save(storageKey, encoded.getBytes());
        } catch (IllegalArgumentException e) {
            throw new NacosException(NacosException.SERVER_ERROR,
                "Agent Version content cannot be saved", e);
        }
        return descriptor;
    }
    
    /**
     * Read, verify, and decode one Agent Version content object.
     *
     * <p>Size and digest are checked against the exact bytes returned by AI Storage before JSON
     * decoding. Unverified content is never returned.</p>
     *
     * @param descriptor Version storage pointer
     * @return verified Agent Version content
     * @throws NacosException when content is missing, corrupted, or cannot be read
     */
    public AgentVersionContent load(AgentVersionStorageDescriptor descriptor)
        throws NacosException {
        StorageKey storageKey = checkedStorageKey(descriptor);
        byte[] bytes;
        try {
            bytes = route(storageKey).get(storageKey);
        } catch (IllegalArgumentException e) {
            throw corruptedContent("Invalid Agent Version storage key", e);
        }
        if (bytes == null) {
            throw corruptedContent("Agent Version content does not exist", null);
        }
        if (descriptor.getSize().longValue() != bytes.length) {
            throw corruptedContent("Agent Version content size does not match its descriptor",
                null);
        }
        String actualDigest = AgentVersionContentCodec.digest(bytes);
        if (!descriptor.getContentDigest().equals(actualDigest)) {
            throw corruptedContent("Agent Version content digest does not match its descriptor",
                null);
        }
        try {
            return AgentVersionContentCodec.decode(bytes);
        } catch (IllegalArgumentException e) {
            throw corruptedContent("Agent Version content cannot be decoded", e);
        }
    }
    
    /**
     * Delete one Agent Version content object through its persisted storage pointer.
     *
     * @param descriptor Version storage pointer
     * @throws NacosException when the selected storage provider fails
     */
    public void delete(AgentVersionStorageDescriptor descriptor) throws NacosException {
        StorageKey storageKey = checkedStorageKey(descriptor);
        try {
            route(storageKey).delete(storageKey);
        } catch (IllegalArgumentException e) {
            throw corruptedContent("Invalid Agent Version storage key", e);
        }
    }
    
    private AgentVersionStorageDescriptor buildDescriptor(StorageKey storageKey,
        AgentVersionContentCodec.EncodedContent encoded) {
        AgentVersionStorageDescriptor result = new AgentVersionStorageDescriptor();
        result.setProvider(storageKey.getProvider());
        result.setKey(storageKey.getKey());
        if (NacosConfigAiResourceStorage.TYPE.equals(storageKey.getProvider())) {
            result.setKeyFormat(AgentVersionStorageDescriptor.NACOS_CONFIG_KEY_FORMAT);
            result.setAgentNameCodec(AgentVersionStorageDescriptor.RAD_AGENT_NAME_CODEC);
        }
        result.setContentDigest(encoded.getContentDigest());
        result.setMediaType(AgentVersionStorageDescriptor.MEDIA_TYPE);
        result.setSchemaVersion(AgentVersionStorageDescriptor.SCHEMA_VERSION);
        result.setSize((long) encoded.getSize());
        return result;
    }
    
    private StorageKey checkedStorageKey(AgentVersionStorageDescriptor descriptor)
        throws NacosException {
        try {
            AgentVersionStorageDescriptorCodec.validate(descriptor);
        } catch (IllegalArgumentException e) {
            throw corruptedContent("Invalid Agent Version storage descriptor", e);
        }
        return new StorageKey(descriptor.getProvider(), descriptor.getKey());
    }
    
    private String resolveStorageProvider() {
        String configured = storageProviderSupplier.get();
        return StringUtils.isBlank(configured) ? DEFAULT_STORAGE_PROVIDER : configured.trim();
    }
    
    private AiResourceStorage route(StorageKey storageKey) throws NacosException {
        try {
            return storageRouter.route(storageKey);
        } catch (IllegalStateException e) {
            throw new NacosException(NacosException.SERVER_ERROR,
                "Agent Version storage provider is unavailable: " + storageKey.getProvider(), e);
        }
    }
    
    private static String configuredProvider() {
        return EnvUtil.getProperty(Constants.Agent.AGENT_STORAGE_PROVIDER_CONFIG_KEY,
            DEFAULT_STORAGE_PROVIDER);
    }
    
    private static NacosException corruptedContent(String message, Throwable cause) {
        return cause == null ? new NacosException(NacosException.SERVER_ERROR, message)
            : new NacosException(NacosException.SERVER_ERROR, message, cause);
    }
}
