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
import com.alibaba.nacos.api.ai.model.agent.AgentCallInterface;
import com.alibaba.nacos.api.ai.model.agent.Endpoint;
import com.alibaba.nacos.api.ai.model.agent.EndpointSource;
import com.alibaba.nacos.api.exception.runtime.NacosDeserializationException;
import com.alibaba.nacos.api.exception.runtime.NacosSerializationException;
import com.alibaba.nacos.api.ai.utils.AgentModelValidator;
import com.alibaba.nacos.api.ai.utils.EndpointCanonicalizer;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Serializes and deserializes the JSON bytes stored for one Agent Version.
 *
 * <p>The returned bytes are the single fact used both for persistence and SHA-256 calculation, so
 * storage content, size and digest cannot diverge through a second serialization pass.</p>
 *
 * @author Nacos
 */
public final class AgentVersionContentSerializer {
    
    public static final String DIGEST_PREFIX = "sha256:";
    
    public static final int MAX_CONTENT_SIZE = 1024 * 1024;
    
    private static final int MAX_CALL_INTERFACES = 16;
    
    private static final char[] HEX = "0123456789abcdef".toCharArray();
    
    private static final JsonFactory STRICT_JSON_FACTORY = new JsonFactory()
        .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    
    private static final Set<String> CONTENT_FIELDS = new HashSet<String>(Arrays.asList(
        "kind", "schemaVersion", "callInterfaces"));
    
    private static final Set<String> CALL_INTERFACE_FIELDS = new HashSet<String>(Arrays.asList(
        "protocol", "protocolVersion", "descriptorMediaType", "nativeDescriptor",
        "endpointSourceOrder", "declaredEndpoints"));
    
    private static final Set<String> ENDPOINT_FIELDS = new HashSet<String>(Arrays.asList(
        "uri", "transport", "priority", "weight", "metadata"));
    
    private AgentVersionContentSerializer() {
    }
    
    /**
     * Validate and serialize one Agent Version content object.
     *
     * @param content Agent Version content
     * @return immutable serialized storage result
     * @throws IllegalArgumentException when content is invalid or exceeds the storage limit
     */
    public static SerializedContent serialize(AgentVersionContent content) {
        validate(content);
        final byte[] bytes;
        try {
            bytes = JacksonUtils.toJsonBytes(toStorageProjection(content));
        } catch (NacosSerializationException e) {
            throw new IllegalArgumentException("Unable to serialize AgentVersionContent", e);
        }
        if (bytes.length > MAX_CONTENT_SIZE) {
            throw new IllegalArgumentException(
                "AgentVersionContent exceeds " + MAX_CONTENT_SIZE + " bytes");
        }
        return new SerializedContent(bytes, digest(bytes));
    }
    
    /**
     * Compute the digest of the exact bytes read from or written to AI Storage.
     *
     * @param bytes persisted Agent Version content bytes
     * @return digest token in {@code sha256:<lowercase hex>} form
     * @throws IllegalArgumentException when bytes are null or exceed the storage limit
     */
    public static String digest(byte[] bytes) {
        if (bytes == null) {
            throw new IllegalArgumentException("AgentVersionContent bytes must not be null");
        }
        if (bytes.length > MAX_CONTENT_SIZE) {
            throw new IllegalArgumentException(
                "AgentVersionContent exceeds " + MAX_CONTENT_SIZE + " bytes");
        }
        return DIGEST_PREFIX + sha256Hex(bytes);
    }
    
    /**
     * Deserialize Agent Version bytes and verify the storage model.
     *
     * @param bytes persisted Agent Version content bytes
     * @return deserialized content
     * @throws IllegalArgumentException when bytes are invalid or out of contract
     */
    public static AgentVersionContent deserialize(byte[] bytes) {
        if (bytes == null) {
            throw new IllegalArgumentException("AgentVersionContent bytes must not be null");
        }
        if (bytes.length > MAX_CONTENT_SIZE) {
            throw new IllegalArgumentException(
                "AgentVersionContent exceeds " + MAX_CONTENT_SIZE + " bytes");
        }
        validateStorageJsonShape(bytes);
        final AgentVersionContent content;
        try {
            content = JacksonUtils.toObj(bytes, AgentVersionContent.class);
        } catch (NacosDeserializationException e) {
            throw new IllegalArgumentException("Invalid AgentVersionContent", e);
        }
        validate(content);
        return content;
    }
    
    private static void validate(AgentVersionContent content) {
        if (content == null) {
            throw new IllegalArgumentException("AgentVersionContent must not be null");
        }
        if (!AgentVersionContent.KIND.equals(content.getKind())) {
            throw new IllegalArgumentException("AgentVersionContent kind must be "
                + AgentVersionContent.KIND);
        }
        if (!Integer.valueOf(AgentVersionContent.SCHEMA_VERSION)
            .equals(content.getSchemaVersion())) {
            throw new IllegalArgumentException("AgentVersionContent schemaVersion must be "
                + AgentVersionContent.SCHEMA_VERSION);
        }
        List<AgentCallInterface> callInterfaces = content.getCallInterfaces();
        if (callInterfaces == null || callInterfaces.isEmpty()
            || callInterfaces.size() > MAX_CALL_INTERFACES) {
            throw new IllegalArgumentException(
                "AgentVersionContent callInterfaces must contain 1 to " + MAX_CALL_INTERFACES
                    + " items");
        }
        Set<String> protocols = new HashSet<String>();
        for (AgentCallInterface callInterface : callInterfaces) {
            AgentModelValidator.validateCallInterface(callInterface);
            if (!protocols.add(callInterface.getProtocol())) {
                throw new IllegalArgumentException(
                    "Duplicate CallInterface protocol: " + callInterface.getProtocol());
            }
        }
    }
    
    private static Map<String, Object> toStorageProjection(AgentVersionContent content) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("kind", AgentVersionContent.KIND);
        result.put("schemaVersion", AgentVersionContent.SCHEMA_VERSION);
        List<Map<String, Object>> callInterfaces = new ArrayList<Map<String, Object>>();
        for (AgentCallInterface callInterface : content.getCallInterfaces()) {
            callInterfaces.add(toStorageProjection(callInterface));
        }
        result.put("callInterfaces", callInterfaces);
        return result;
    }
    
    private static Map<String, Object> toStorageProjection(AgentCallInterface callInterface) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("protocol", callInterface.getProtocol());
        if (callInterface.getProtocolVersion() != null) {
            result.put("protocolVersion", callInterface.getProtocolVersion());
        }
        result.put("descriptorMediaType", callInterface.getDescriptorMediaType());
        result.put("nativeDescriptor", callInterface.getNativeDescriptor());
        List<String> sources = new ArrayList<String>();
        for (EndpointSource source : callInterface.getEndpointSourceOrder()) {
            sources.add(source.name());
        }
        result.put("endpointSourceOrder", sources);
        if (callInterface.getDeclaredEndpoints() != null
            && !callInterface.getDeclaredEndpoints().isEmpty()) {
            List<Map<String, Object>> endpoints = new ArrayList<Map<String, Object>>();
            for (Endpoint endpoint : callInterface.getDeclaredEndpoints()) {
                endpoints.add(toStorageProjection(EndpointCanonicalizer.canonicalize(endpoint)));
            }
            result.put("declaredEndpoints", endpoints);
        }
        return result;
    }
    
    private static Map<String, Object> toStorageProjection(Endpoint endpoint) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("uri", endpoint.getUri());
        result.put("transport", endpoint.getTransport());
        result.put("priority", endpoint.getPriority());
        result.put("weight", endpoint.getWeight());
        if (endpoint.getMetadata() != null && !endpoint.getMetadata().isEmpty()) {
            result.put("metadata", endpoint.getMetadata());
        }
        return result;
    }
    
    private static String sha256Hex(byte[] bytes) {
        final byte[] digest;
        try {
            digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
        char[] encoded = new char[digest.length * 2];
        for (int i = 0; i < digest.length; i++) {
            int value = digest[i] & 0xFF;
            encoded[i * 2] = HEX[value >>> 4];
            encoded[i * 2 + 1] = HEX[value & 0x0F];
        }
        return new String(encoded);
    }
    
    private static void validateStorageJsonShape(byte[] bytes) {
        validateSingleJsonValue(bytes);
        final Map<?, ?> root;
        try {
            root = JacksonUtils.toObj(bytes, Map.class);
        } catch (NacosDeserializationException e) {
            throw new IllegalArgumentException("Invalid AgentVersionContent", e);
        }
        if (root == null) {
            throw new IllegalArgumentException("AgentVersionContent must be a JSON object");
        }
        rejectUnknownFields(root, CONTENT_FIELDS, "AgentVersionContent");
        Object callInterfaces = root.get("callInterfaces");
        if (!(callInterfaces instanceof List)) {
            return;
        }
        for (Object callInterface : (List<?>) callInterfaces) {
            if (!(callInterface instanceof Map)) {
                continue;
            }
            Map<?, ?> interfaceObject = (Map<?, ?>) callInterface;
            rejectUnknownFields(interfaceObject, CALL_INTERFACE_FIELDS, "AgentCallInterface");
            Object endpoints = interfaceObject.get("declaredEndpoints");
            if (!(endpoints instanceof List)) {
                continue;
            }
            for (Object endpoint : (List<?>) endpoints) {
                if (endpoint instanceof Map) {
                    rejectUnknownFields((Map<?, ?>) endpoint, ENDPOINT_FIELDS,
                        "DeclaredEndpoint");
                }
            }
        }
    }
    
    private static void validateSingleJsonValue(byte[] bytes) {
        try (JsonParser parser = STRICT_JSON_FACTORY.createParser(bytes)) {
            if (parser.nextToken() == null) {
                throw new IllegalArgumentException("AgentVersionContent JSON must not be empty");
            }
            parser.skipChildren();
            if (parser.nextToken() != null) {
                throw new IllegalArgumentException(
                    "AgentVersionContent must contain one JSON value");
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid AgentVersionContent", e);
        }
    }
    
    private static void rejectUnknownFields(Map<?, ?> value, Set<String> fields,
        String objectName) {
        for (Object field : value.keySet()) {
            if (!fields.contains(field)) {
                throw new IllegalArgumentException("Unknown " + objectName + " field: " + field);
            }
        }
    }
    
    /**
     * Immutable persisted bytes, digest and byte count for one Agent Version content object.
     */
    public static final class SerializedContent {
        
        private final byte[] bytes;
        
        private final String contentDigest;
        
        private SerializedContent(byte[] bytes, String contentDigest) {
            this.bytes = bytes;
            this.contentDigest = contentDigest;
        }
        
        /**
         * Return a defensive copy of the persisted storage bytes.
         *
         * @return persisted storage bytes
         */
        public byte[] getBytes() {
            return Arrays.copyOf(bytes, bytes.length);
        }
        
        /**
         * Return the SHA-256 digest of the persisted bytes.
         *
         * @return digest token
         */
        public String getContentDigest() {
            return contentDigest;
        }
        
        /**
         * Return the persisted UTF-8 byte count.
         *
         * @return byte count
         */
        public int getSize() {
            return bytes.length;
        }
    }
}
