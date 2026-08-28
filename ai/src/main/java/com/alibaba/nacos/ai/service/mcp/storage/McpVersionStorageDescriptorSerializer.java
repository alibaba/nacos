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
import com.alibaba.nacos.api.ai.utils.AgentValidationUtils;
import com.alibaba.nacos.api.exception.runtime.NacosSerializationException;
import com.alibaba.nacos.common.utils.JacksonUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Strict JSON serializer for an MCP Version storage descriptor.
 *
 * @author Nacos
 */
public final class McpVersionStorageDescriptorSerializer {
    
    private static final int MAX_KEY_LENGTH = 1024;
    
    private static final Set<String> FIELDS = Collections.unmodifiableSet(
        new HashSet<String>(Arrays.asList("provider", "keyFormat", "serverKey", "toolKey",
            "resourceKey", "schemaVersion")));
    
    private McpVersionStorageDescriptorSerializer() {
    }
    
    /**
     * Validate and serialize an MCP Version storage descriptor.
     *
     * @param descriptor storage descriptor
     * @return canonical JSON stored in {@code ai_resource_version.storage}
     */
    public static String serialize(McpVersionStorageDescriptor descriptor) {
        validate(descriptor);
        Map<String, Object> projection = new LinkedHashMap<String, Object>();
        projection.put("provider", descriptor.getProvider());
        projection.put("keyFormat", descriptor.getKeyFormat());
        projection.put("serverKey", descriptor.getServerKey());
        if (descriptor.getToolKey() != null) {
            projection.put("toolKey", descriptor.getToolKey());
        }
        if (descriptor.getResourceKey() != null) {
            projection.put("resourceKey", descriptor.getResourceKey());
        }
        projection.put("schemaVersion", McpVersionStorageDescriptor.SCHEMA_VERSION);
        try {
            return JacksonUtils.toJson(projection);
        } catch (NacosSerializationException e) {
            throw new IllegalArgumentException(
                "Unable to serialize MCP Version storage descriptor", e);
        }
    }
    
    /**
     * Deserialize and strictly validate an MCP Version storage descriptor.
     *
     * @param json persisted JSON
     * @return validated descriptor
     */
    public static McpVersionStorageDescriptor deserialize(String json) {
        Map<?, ?> root = McpJsonValidationSupport.parseObject(json,
            "MCP Version storage descriptor");
        McpJsonValidationSupport.rejectUnknownFields(root, FIELDS,
            "MCP Version storage descriptor");
        McpVersionStorageDescriptor result = new McpVersionStorageDescriptor();
        result.setProvider(McpJsonValidationSupport.requireText(root, "provider",
            "MCP Version storage descriptor"));
        result.setKeyFormat(McpJsonValidationSupport.requireText(root, "keyFormat",
            "MCP Version storage descriptor"));
        result.setServerKey(McpJsonValidationSupport.requireText(root, "serverKey",
            "MCP Version storage descriptor"));
        result.setToolKey(McpJsonValidationSupport.optionalText(root, "toolKey",
            "MCP Version storage descriptor"));
        result.setResourceKey(McpJsonValidationSupport.optionalText(root, "resourceKey",
            "MCP Version storage descriptor"));
        result.setSchemaVersion(McpJsonValidationSupport.requireInteger(root, "schemaVersion",
            "MCP Version storage descriptor"));
        validate(result);
        return result;
    }
    
    /**
     * Validate an MCP Version storage descriptor against schema version 1.
     *
     * @param descriptor storage descriptor
     */
    public static void validate(McpVersionStorageDescriptor descriptor) {
        if (descriptor == null) {
            throw new IllegalArgumentException(
                "MCP Version storage descriptor must not be null");
        }
        if (!McpVersionStorageDescriptor.PROVIDER.equals(descriptor.getProvider())) {
            throw new IllegalArgumentException("MCP Version storage provider must be "
                + McpVersionStorageDescriptor.PROVIDER);
        }
        if (!McpVersionStorageDescriptor.KEY_FORMAT.equals(descriptor.getKeyFormat())) {
            throw new IllegalArgumentException("MCP Version storage keyFormat must be "
                + McpVersionStorageDescriptor.KEY_FORMAT);
        }
        if (!Integer.valueOf(McpVersionStorageDescriptor.SCHEMA_VERSION)
            .equals(descriptor.getSchemaVersion())) {
            throw new IllegalArgumentException("MCP Version storage schemaVersion must be "
                + McpVersionStorageDescriptor.SCHEMA_VERSION);
        }
        StorageCoordinate server = parseKey(descriptor.getServerKey(),
            Constants.MCP_SERVER_GROUP, Constants.MCP_SERVER_SPEC_DATA_ID_SUFFIX, "serverKey");
        validateOptionalKey(descriptor.getToolKey(), Constants.MCP_SERVER_TOOL_GROUP,
            Constants.MCP_SERVER_TOOL_DATA_ID_SUFFIX, "toolKey", server.namespaceId());
        validateOptionalKey(descriptor.getResourceKey(), Constants.MCP_SERVER_RESOURCE_GROUP,
            Constants.MCP_SERVER_RESOURCE_DATA_ID_SUFFIX, "resourceKey", server.namespaceId());
    }
    
    static StorageCoordinate parseKey(String key, String expectedGroup, String expectedSuffix,
        String fieldName) {
        if (key == null || key.length() > MAX_KEY_LENGTH) {
            throw new IllegalArgumentException("Invalid MCP Version storage " + fieldName);
        }
        int namespaceSeparator = key.indexOf(':');
        int groupSeparator = key.indexOf(':', namespaceSeparator + 1);
        if (namespaceSeparator <= 0 || groupSeparator <= namespaceSeparator + 1
            || groupSeparator == key.length() - 1) {
            throw new IllegalArgumentException("Invalid MCP Version storage " + fieldName);
        }
        String namespaceId = key.substring(0, namespaceSeparator);
        String group = key.substring(namespaceSeparator + 1, groupSeparator);
        String dataId = key.substring(groupSeparator + 1);
        AgentValidationUtils.validateNamespaceId(namespaceId);
        if (!expectedGroup.equals(group) || dataId.length() <= expectedSuffix.length()
            || !dataId.endsWith(expectedSuffix)) {
            throw new IllegalArgumentException("Invalid MCP Version storage " + fieldName);
        }
        return new StorageCoordinate(namespaceId, group, dataId);
    }
    
    private static void validateOptionalKey(String key, String expectedGroup,
        String expectedSuffix, String fieldName, String expectedNamespaceId) {
        if (key == null) {
            return;
        }
        StorageCoordinate coordinate = parseKey(key, expectedGroup, expectedSuffix, fieldName);
        if (!expectedNamespaceId.equals(coordinate.namespaceId())) {
            throw new IllegalArgumentException(
                "MCP Version storage keys must use the same namespace");
        }
    }
    
    record StorageCoordinate(String namespaceId, String group, String dataId) {
    }
}
