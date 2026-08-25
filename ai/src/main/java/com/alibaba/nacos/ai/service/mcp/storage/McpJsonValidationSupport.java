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

import com.alibaba.nacos.api.exception.runtime.NacosDeserializationException;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

/**
 * Shared strict JSON shape validation for MCP internal storage contracts.
 *
 * @author Nacos
 */
final class McpJsonValidationSupport {
    
    private static final JsonFactory STRICT_JSON_FACTORY = new JsonFactory()
        .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    
    private McpJsonValidationSupport() {
    }
    
    static Object parseSingleValue(String json, String contractName) {
        if (json == null || json.trim().isEmpty()) {
            throw new IllegalArgumentException(contractName + " JSON must not be empty");
        }
        validateSingleJsonValue(json, contractName);
        try {
            return JacksonUtils.toObj(json, Object.class);
        } catch (NacosDeserializationException e) {
            throw new IllegalArgumentException("Invalid " + contractName, e);
        }
    }
    
    static Map<?, ?> parseObject(String json, String contractName) {
        Object value = parseSingleValue(json, contractName);
        if (!(value instanceof Map)) {
            throw new IllegalArgumentException(contractName + " must be a JSON object");
        }
        return (Map<?, ?>) value;
    }
    
    static void rejectUnknownFields(Map<?, ?> object, Set<String> fields, String contractName) {
        for (Object field : object.keySet()) {
            if (!(field instanceof String) || !fields.contains(field)) {
                throw new IllegalArgumentException("Unknown " + contractName + " field: " + field);
            }
        }
    }
    
    static String requireText(Map<?, ?> object, String field, String contractName) {
        Object value = object.get(field);
        if (!(value instanceof String)) {
            throw new IllegalArgumentException(contractName + " " + field + " must be a string");
        }
        return (String) value;
    }
    
    static String optionalText(Map<?, ?> object, String field, String contractName) {
        if (!object.containsKey(field)) {
            return null;
        }
        return requireText(object, field, contractName);
    }
    
    static int requireInteger(Map<?, ?> object, String field, String contractName) {
        Object value = object.get(field);
        if (!(value instanceof Byte || value instanceof Short || value instanceof Integer
            || value instanceof Long)) {
            throw new IllegalArgumentException(contractName + " " + field + " must be an integer");
        }
        long result = ((Number) value).longValue();
        if (result < Integer.MIN_VALUE || result > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(contractName + " " + field + " is out of range");
        }
        return (int) result;
    }
    
    private static void validateSingleJsonValue(String json, String contractName) {
        try (JsonParser parser = STRICT_JSON_FACTORY.createParser(json)) {
            if (parser.nextToken() == null) {
                throw new IllegalArgumentException(contractName + " JSON must not be empty");
            }
            parser.skipChildren();
            if (parser.nextToken() != null) {
                throw new IllegalArgumentException(contractName + " must contain one JSON value");
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid " + contractName, e);
        }
    }
}
