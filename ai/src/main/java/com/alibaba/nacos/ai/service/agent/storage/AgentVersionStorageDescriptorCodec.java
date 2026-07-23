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
import com.alibaba.nacos.ai.service.agent.fingerprint.AgentVersionContentCodec;
import com.alibaba.nacos.api.exception.runtime.NacosDeserializationException;
import com.alibaba.nacos.api.exception.runtime.NacosSerializationException;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * JSON codec for the storage descriptor persisted in one Agent Version row.
 *
 * @author Nacos
 */
public final class AgentVersionStorageDescriptorCodec {
    
    public static final String NACOS_CONFIG_PROVIDER = "nacos_config";
    
    public static final String NACOS_CONFIG_KEY_FORMAT =
        AgentVersionStorageDescriptor.NACOS_CONFIG_KEY_FORMAT;
    
    public static final String RAD_ASCII_AGENT_NAME_CODEC =
        AgentVersionStorageDescriptor.RAD_AGENT_NAME_CODEC;
    
    public static final String AGENT_VERSION_MEDIA_TYPE =
        AgentVersionStorageDescriptor.MEDIA_TYPE;
    
    public static final int SCHEMA_VERSION = AgentVersionStorageDescriptor.SCHEMA_VERSION;
    
    public static final int MAX_CONTENT_SIZE = AgentVersionContentCodec.MAX_CONTENT_SIZE;
    
    private static final int MAX_PROVIDER_LENGTH = 64;
    
    private static final int MAX_KEY_LENGTH = 1024;
    
    private static final int MAX_FORMAT_LENGTH = 64;
    
    private static final Pattern PROVIDER_PATTERN =
        Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]{0,63}");
    
    private static final Pattern DIGEST_PATTERN = Pattern.compile("sha256:[0-9a-f]{64}");
    
    private static final JsonFactory STRICT_JSON_FACTORY = new JsonFactory()
        .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    
    private static final Set<String> FIELDS = Collections.unmodifiableSet(
        new HashSet<String>(Arrays.asList("provider", "key", "keyFormat", "agentNameCodec",
            "contentDigest", "mediaType", "schemaVersion", "size")));
    
    private AgentVersionStorageDescriptorCodec() {
    }
    
    /**
     * Validate and serialize an Agent Version storage descriptor.
     *
     * @param descriptor storage descriptor
     * @return JSON stored in {@code ai_resource_version.storage}
     * @throws IllegalArgumentException when the descriptor is invalid
     */
    public static String encode(AgentVersionStorageDescriptor descriptor) {
        validate(descriptor);
        try {
            return JacksonUtils.toJson(descriptor);
        } catch (NacosSerializationException e) {
            throw new IllegalArgumentException(
                "Unable to serialize Agent Version storage descriptor",
                e);
        }
    }
    
    /**
     * Deserialize and validate an Agent Version storage descriptor.
     *
     * @param json JSON read from {@code ai_resource_version.storage}
     * @return decoded storage descriptor
     * @throws IllegalArgumentException when JSON or descriptor fields are invalid
     */
    public static AgentVersionStorageDescriptor decode(String json) {
        validateJsonShape(json);
        final AgentVersionStorageDescriptor descriptor;
        try {
            descriptor = JacksonUtils.toObj(json, AgentVersionStorageDescriptor.class);
        } catch (NacosDeserializationException e) {
            throw new IllegalArgumentException("Invalid Agent Version storage descriptor", e);
        }
        validate(descriptor);
        return descriptor;
    }
    
    private static void validateJsonShape(String json) {
        if (json == null || json.isEmpty()) {
            throw new IllegalArgumentException(
                "Agent Version storage descriptor JSON must not be empty");
        }
        validateSingleJsonValue(json);
        final Map<?, ?> root;
        try {
            root = JacksonUtils.toObj(json, Map.class);
        } catch (NacosDeserializationException e) {
            throw new IllegalArgumentException("Invalid Agent Version storage descriptor", e);
        }
        if (root == null) {
            throw new IllegalArgumentException(
                "Agent Version storage descriptor must be a JSON object");
        }
        for (Object fieldName : root.keySet()) {
            if (!FIELDS.contains(fieldName)) {
                throw new IllegalArgumentException(
                    "Unknown Agent Version storage descriptor field: " + fieldName);
            }
        }
        validateJsonText(root, "provider", false);
        validateJsonText(root, "key", false);
        validateJsonText(root, "keyFormat", true);
        validateJsonText(root, "agentNameCodec", true);
        validateJsonText(root, "contentDigest", false);
        validateJsonText(root, "mediaType", false);
        validateJsonInteger(root, "schemaVersion");
        validateJsonInteger(root, "size");
    }
    
    /**
     * Validate an Agent Version storage descriptor against the internal storage schema.
     *
     * @param descriptor storage descriptor
     * @throws IllegalArgumentException when the descriptor is invalid
     */
    public static void validate(AgentVersionStorageDescriptor descriptor) {
        if (descriptor == null) {
            throw new IllegalArgumentException("Agent Version storage descriptor must not be null");
        }
        String provider = descriptor.getProvider();
        if (provider == null || provider.length() > MAX_PROVIDER_LENGTH
            || !PROVIDER_PATTERN.matcher(provider).matches()) {
            throw new IllegalArgumentException("Invalid Agent Version storage provider");
        }
        validateRequiredText("key", descriptor.getKey(), MAX_KEY_LENGTH);
        validateOptionalText("keyFormat", descriptor.getKeyFormat(), MAX_FORMAT_LENGTH);
        validateOptionalText("agentNameCodec", descriptor.getAgentNameCodec(), MAX_FORMAT_LENGTH);
        if (descriptor.getContentDigest() == null
            || !DIGEST_PATTERN.matcher(descriptor.getContentDigest()).matches()) {
            throw new IllegalArgumentException("Invalid Agent Version contentDigest");
        }
        if (!AGENT_VERSION_MEDIA_TYPE.equals(descriptor.getMediaType())) {
            throw new IllegalArgumentException("Agent Version mediaType must be "
                + AGENT_VERSION_MEDIA_TYPE);
        }
        if (!Integer.valueOf(SCHEMA_VERSION).equals(descriptor.getSchemaVersion())) {
            throw new IllegalArgumentException("Agent Version storage schemaVersion must be "
                + SCHEMA_VERSION);
        }
        Long size = descriptor.getSize();
        if (size == null || size < 0 || size > MAX_CONTENT_SIZE) {
            throw new IllegalArgumentException(
                "Agent Version storage size must be between 0 and " + MAX_CONTENT_SIZE);
        }
        if (NACOS_CONFIG_PROVIDER.equals(provider)) {
            if (!NACOS_CONFIG_KEY_FORMAT.equals(descriptor.getKeyFormat())) {
                throw new IllegalArgumentException("nacos_config keyFormat must be "
                    + NACOS_CONFIG_KEY_FORMAT);
            }
            if (!RAD_ASCII_AGENT_NAME_CODEC.equals(descriptor.getAgentNameCodec())) {
                throw new IllegalArgumentException("nacos_config agentNameCodec must be "
                    + RAD_ASCII_AGENT_NAME_CODEC);
            }
        }
    }
    
    private static void validateRequiredText(String field, String value, int maxLength) {
        if (value == null || value.isEmpty() || value.length() > maxLength) {
            throw new IllegalArgumentException("Invalid Agent Version storage " + field);
        }
    }
    
    private static void validateOptionalText(String field, String value, int maxLength) {
        if (value != null) {
            validateRequiredText(field, value, maxLength);
        }
    }
    
    private static void validateJsonText(Map<?, ?> root, String field, boolean optional) {
        if (!root.containsKey(field)) {
            if (optional) {
                return;
            }
            throw new IllegalArgumentException("Missing Agent Version storage field: " + field);
        }
        if (!(root.get(field) instanceof String)) {
            throw new IllegalArgumentException(
                "Agent Version storage " + field + " must be a string");
        }
    }
    
    private static void validateJsonInteger(Map<?, ?> root, String field) {
        Object value = root.get(field);
        if (!(value instanceof Byte || value instanceof Short || value instanceof Integer
            || value instanceof Long)) {
            throw new IllegalArgumentException(
                "Agent Version storage " + field + " must be an integer");
        }
    }
    
    private static void validateSingleJsonValue(String json) {
        try (JsonParser parser = STRICT_JSON_FACTORY.createParser(json)) {
            if (parser.nextToken() == null) {
                throw new IllegalArgumentException(
                    "Agent Version storage descriptor JSON must not be empty");
            }
            parser.skipChildren();
            if (parser.nextToken() != null) {
                throw new IllegalArgumentException(
                    "Agent Version storage descriptor must contain one JSON value");
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid Agent Version storage descriptor", e);
        }
    }
}
