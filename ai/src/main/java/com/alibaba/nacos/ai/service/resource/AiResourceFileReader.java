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

package com.alibaba.nacos.ai.service.resource;

import com.alibaba.nacos.ai.constant.AiResourceConstants;
import com.alibaba.nacos.ai.model.AiResourceVersion;
import com.alibaba.nacos.ai.storage.NacosConfigAiResourceStorage;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.ai.storage.AiResourceStorageRouter;
import com.alibaba.nacos.plugin.ai.storage.model.StorageKey;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * Reads versioned AI resource files without exposing storage metadata to protocol adaptors.
 *
 * @author nacos
 */
@Service
public class AiResourceFileReader {
    
    private static final TypeReference<Map<String, Object>> MAP_TYPE =
        new TypeReference<Map<String, Object>>() {
        };
    
    private final AiResourceStorageRouter storageRouter;
    
    public AiResourceFileReader() {
        this(AiResourceStorageRouter.getInstance());
    }
    
    AiResourceFileReader(AiResourceStorageRouter storageRouter) {
        this.storageRouter = storageRouter;
    }
    
    /**
     * Read one file declared by an AI resource version.
     *
     * @return file bytes, or {@code null} when the version does not declare the file
     */
    public byte[] read(AiResourceVersion version, String namespaceId, String resourceType,
        String resourceName, String resourceVersion, String filePath) throws NacosException {
        if (version == null || StringUtils.isBlank(version.getStorage())) {
            return null;
        }
        Map<String, Object> storage = parseStorage(version.getStorage());
        if (!containsFile(storage.get("files"), filePath)) {
            return null;
        }
        String provider = provider(storage.get("provider"));
        StorageKey key = buildStorageKey(provider, namespaceId, resourceType, resourceName,
            resourceVersion, filePath);
        return storageRouter.route(key).get(key);
    }
    
    private StorageKey buildStorageKey(String provider, String namespaceId, String resourceType,
        String resourceName, String resourceVersion, String filePath) {
        if (AiResourceConstants.RESOURCE_TYPE_SKILL.equals(resourceType)) {
            return NacosConfigAiResourceStorage.buildStorageKey(provider, namespaceId, resourceName,
                resourceVersion, filePath);
        }
        return NacosConfigAiResourceStorage.buildStorageKey(provider, namespaceId, resourceType,
            resourceName, resourceVersion, filePath);
    }
    
    private Map<String, Object> parseStorage(String storageJson) {
        try {
            Map<String, Object> parsed = JacksonUtils.toObj(storageJson, MAP_TYPE);
            return parsed == null ? Collections.emptyMap() : parsed;
        } catch (Exception ignored) {
            return Collections.emptyMap();
        }
    }
    
    private String provider(Object provider) {
        String value = provider == null ? null : String.valueOf(provider);
        return StringUtils.isBlank(value) ? NacosConfigAiResourceStorage.TYPE : value;
    }
    
    private boolean containsFile(Object files, String filePath) {
        if (!(files instanceof Collection)) {
            return false;
        }
        for (Object file : (Collection<?>) files) {
            if (filePath.equals(String.valueOf(file))) {
                return true;
            }
        }
        return false;
    }
}
