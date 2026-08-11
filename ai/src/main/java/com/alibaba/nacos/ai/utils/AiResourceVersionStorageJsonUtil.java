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

package com.alibaba.nacos.ai.utils;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure helpers to manipulate the JSON payload stored in
 * {@code ai_resource_version.storage}.
 *
 * <p>This logic is independent of the storage backend (external JDBC vs embedded
 * Derby + Raft), so it lives here as a stateless utility instead of being
 * duplicated across {@code AiResourceVersionPersistServiceImpl} and
 * {@code EmbeddedAiResourceVersionPersistServiceImpl}.</p>
 */
public final class AiResourceVersionStorageJsonUtil {
    
    private AiResourceVersionStorageJsonUtil() {
    }
    
    /**
     * Merge a new {@code contentMd5} into the existing {@code storage} JSON while preserving the
     * other entries (provider/scope/files/...). Returns a JSON string suitable for the
     * {@code storage} column.
     *
     * @param existingStorageJson current JSON value of the {@code storage} column; may be blank
     * @param contentMd5          MD5 to write under {@link Constants.Skills#STORAGE_KEY_CONTENT_MD5}
     * @return merged JSON string with the {@code contentMd5} entry set
     */
    public static String mergeContentMd5(String existingStorageJson, String contentMd5) {
        Map<String, Object> map;
        if (StringUtils.isBlank(existingStorageJson)) {
            map = new LinkedHashMap<>();
        } else {
            try {
                map = JacksonUtils.toObj(existingStorageJson,
                    new TypeReference<LinkedHashMap<String, Object>>() {
                    });
            } catch (Exception e) {
                map = new LinkedHashMap<>();
            }
        }
        map.put(Constants.Skills.STORAGE_KEY_CONTENT_MD5, contentMd5);
        return JacksonUtils.toJson(map);
    }
    
    /**
     * Read the persisted provider from a version storage descriptor.
     *
     * @param storageJson version storage descriptor
     * @return persisted provider
     * @throws NacosException when the descriptor is missing or invalid
     */
    public static String requireProvider(String storageJson) throws NacosException {
        Object provider = requireDescriptor(storageJson).get("provider");
        if (!(provider instanceof String) || StringUtils.isBlank((String) provider)) {
            throw invalidDescriptor("provider is missing");
        }
        return ((String) provider).trim();
    }
    
    /**
     * Read the persisted file list from a version storage descriptor.
     *
     * @param storageJson version storage descriptor
     * @return persisted file paths
     * @throws NacosException when the descriptor is missing or invalid
     */
    public static List<String> requireFiles(String storageJson) throws NacosException {
        Object files = requireDescriptor(storageJson).get("files");
        if (!(files instanceof List) || ((List<?>) files).isEmpty()) {
            throw invalidDescriptor("files are missing");
        }
        List<String> result = new ArrayList<>(((List<?>) files).size());
        for (Object file : (List<?>) files) {
            if (!(file instanceof String) || StringUtils.isBlank((String) file)) {
                throw invalidDescriptor("file path is invalid");
            }
            result.add((String) file);
        }
        return result;
    }
    
    private static Map<String, Object> requireDescriptor(String storageJson)
        throws NacosException {
        if (StringUtils.isBlank(storageJson)) {
            throw invalidDescriptor("descriptor is missing");
        }
        try {
            Map<String, Object> result = JacksonUtils.toObj(storageJson,
                new TypeReference<LinkedHashMap<String, Object>>() {
                });
            if (result == null) {
                throw invalidDescriptor("descriptor is missing");
            }
            return result;
        } catch (NacosException e) {
            throw e;
        } catch (Exception e) {
            throw new NacosException(NacosException.SERVER_ERROR,
                "AI resource storage descriptor is invalid", e);
        }
    }
    
    private static NacosException invalidDescriptor(String reason) {
        return new NacosException(NacosException.SERVER_ERROR,
            "AI resource storage descriptor is invalid: " + reason);
    }
}
