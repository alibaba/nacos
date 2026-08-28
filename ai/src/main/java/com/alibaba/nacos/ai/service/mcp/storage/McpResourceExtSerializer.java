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

import com.alibaba.nacos.ai.model.mcp.McpResourceExt;
import com.alibaba.nacos.api.exception.runtime.NacosSerializationException;
import com.alibaba.nacos.common.utils.JacksonUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Strict JSON serializer for the MCP content persisted in {@code ai_resource.ext}.
 *
 * @author Nacos
 */
public final class McpResourceExtSerializer {
    
    private static final Pattern MCP_ID_PATTERN = Pattern.compile(
        "[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}");
    
    private static final Set<String> FIELDS = Collections.unmodifiableSet(
        new HashSet<String>(Arrays.asList("schemaVersion", "mcpId")));
    
    private McpResourceExtSerializer() {
    }
    
    /**
     * Validate and serialize one MCP Resource extension.
     *
     * @param resourceExt extension object
     * @return canonical JSON stored in {@code ai_resource.ext}
     */
    public static String serialize(McpResourceExt resourceExt) {
        validate(resourceExt);
        Map<String, Object> projection = new LinkedHashMap<String, Object>();
        projection.put("schemaVersion", McpResourceExt.SCHEMA_VERSION);
        projection.put("mcpId", resourceExt.getMcpId());
        try {
            return JacksonUtils.toJson(projection);
        } catch (NacosSerializationException e) {
            throw new IllegalArgumentException("Unable to serialize McpResourceExt", e);
        }
    }
    
    /**
     * Deserialize and strictly validate one MCP Resource extension.
     *
     * @param json persisted JSON
     * @return validated extension object
     */
    public static McpResourceExt deserialize(String json) {
        Map<?, ?> root = McpJsonValidationSupport.parseObject(json, "McpResourceExt");
        McpJsonValidationSupport.rejectUnknownFields(root, FIELDS, "McpResourceExt");
        McpResourceExt result = new McpResourceExt();
        result.setSchemaVersion(McpJsonValidationSupport.requireInteger(root, "schemaVersion",
            "McpResourceExt"));
        result.setMcpId(McpJsonValidationSupport.requireText(root, "mcpId", "McpResourceExt"));
        validate(result);
        return result;
    }
    
    /**
     * Validate one MCP Resource extension against schema version 1.
     *
     * @param resourceExt extension object
     */
    public static void validate(McpResourceExt resourceExt) {
        if (resourceExt == null) {
            throw new IllegalArgumentException("McpResourceExt must not be null");
        }
        if (!Integer.valueOf(McpResourceExt.SCHEMA_VERSION)
            .equals(resourceExt.getSchemaVersion())) {
            throw new IllegalArgumentException("McpResourceExt schemaVersion must be "
                + McpResourceExt.SCHEMA_VERSION);
        }
        validateMcpId(resourceExt.getMcpId());
    }
    
    /**
     * Validate the UUID-shaped MCP compatibility alias.
     *
     * @param mcpId compatibility alias
     */
    public static void validateMcpId(String mcpId) {
        if (mcpId == null || !MCP_ID_PATTERN.matcher(mcpId).matches()) {
            throw new IllegalArgumentException("Invalid MCP compatibility mcpId");
        }
    }
}
