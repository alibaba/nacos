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

package com.alibaba.nacos.api.ai.utils;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Shared validation rules for Agent management and RAD public values.
 *
 * @author Nacos
 */
public final class AgentValidationUtils {
    
    private static final int MAX_NAMESPACE_LENGTH = 128;
    
    private static final int MAX_AGENT_NAME_LENGTH = 64;
    
    private static final int MAX_PROTOCOL_LENGTH = 32;
    
    private static final int MAX_PROTOCOL_VERSION_LENGTH = 64;
    
    private static final int MAX_LABEL_LENGTH = 64;
    
    private static final int MAX_TRANSPORT_LENGTH = 64;
    
    private static final int MAX_MEDIA_TYPE_LENGTH = 128;
    
    private static final int MAX_METADATA_SIZE = 32;
    
    private static final int MAX_METADATA_KEY_LENGTH = 64;
    
    private static final int MAX_METADATA_VALUE_LENGTH = 256;
    
    private static final Pattern NAMESPACE_PATTERN = Pattern.compile("[A-Za-z0-9_-]+");
    
    private static final Pattern PROTOCOL_PATTERN =
        Pattern.compile("[A-Za-z0-9][A-Za-z0-9-]{0,31}");
    
    private static final Pattern LABEL_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");
    
    private static final Pattern TRANSPORT_PATTERN = Pattern.compile("[0-9A-Za-z+-]{1,64}");
    
    private static final Pattern CONTENT_DIGEST_PATTERN = Pattern.compile("sha256:[0-9a-f]{64}");
    
    private static final Pattern MEDIA_TYPE_PATTERN = Pattern.compile("[!-~]+/[!-~]+");
    
    private static final String INTERNAL_ENDPOINT_METADATA_PREFIX = "__nacos.agent.endpoint.";
    
    private AgentValidationUtils() {
    }
    
    /**
     * Validate a namespace identifier.
     *
     * @param namespaceId namespace identifier
     * @throws IllegalArgumentException when invalid
     */
    public static void validateNamespaceId(String namespaceId) {
        if (namespaceId == null || namespaceId.length() > MAX_NAMESPACE_LENGTH
            || !NAMESPACE_PATTERN.matcher(namespaceId).matches()) {
            throw new IllegalArgumentException("Invalid namespaceId: " + namespaceId);
        }
    }
    
    /**
     * Validate an Agent name without rewriting it.
     *
     * @param agentName Agent name
     * @throws IllegalArgumentException when invalid
     */
    public static void validateAgentName(String agentName) {
        if (agentName == null || agentName.isEmpty()
            || agentName.length() > MAX_AGENT_NAME_LENGTH) {
            throw new IllegalArgumentException("Invalid agentName: " + agentName);
        }
        boolean containsNonSpace = false;
        for (int i = 0; i < agentName.length(); i++) {
            char current = agentName.charAt(i);
            if (current < 0x20 || current > 0x7E) {
                throw new IllegalArgumentException("Invalid agentName: " + agentName);
            }
            if (current > 0x20) {
                containsNonSpace = true;
            }
        }
        if (!containsNonSpace) {
            throw new IllegalArgumentException("Invalid agentName: " + agentName);
        }
    }
    
    /**
     * Validate an Agent version.
     *
     * @param version version text
     * @throws IllegalArgumentException when invalid
     */
    public static void validateVersion(String version) {
        AgentVersion.parse(version);
    }
    
    /**
     * Validate an Agent version range.
     *
     * @param versionRange version range text
     * @throws IllegalArgumentException when invalid
     */
    public static void validateVersionRange(String versionRange) {
        AgentVersionRange.parse(versionRange);
    }
    
    /**
     * Validate a protocol token.
     *
     * @param protocol protocol token
     * @throws IllegalArgumentException when invalid
     */
    public static void validateProtocol(String protocol) {
        if (protocol == null || protocol.length() > MAX_PROTOCOL_LENGTH
            || !PROTOCOL_PATTERN.matcher(protocol).matches()) {
            throw new IllegalArgumentException("Invalid protocol: " + protocol);
        }
    }
    
    /**
     * Validate an optional protocol-version value when present.
     *
     * @param protocolVersion protocol-version value
     * @throws IllegalArgumentException when invalid
     */
    public static void validateProtocolVersion(String protocolVersion) {
        if (protocolVersion == null || protocolVersion.isEmpty()
            || protocolVersion.length() > MAX_PROTOCOL_VERSION_LENGTH) {
            throw new IllegalArgumentException("Invalid protocolVersion: " + protocolVersion);
        }
        validatePrintableAscii(protocolVersion, "protocolVersion");
    }
    
    /**
     * Validate a version label, including the server-managed {@code latest} label.
     *
     * @param label version label
     * @throws IllegalArgumentException when invalid
     */
    public static void validateLabel(String label) {
        if (label == null || label.length() > MAX_LABEL_LENGTH
            || !LABEL_PATTERN.matcher(label).matches()) {
            throw new IllegalArgumentException("Invalid label: " + label);
        }
    }
    
    /**
     * Validate a client-writable version label.
     *
     * @param label version label
     * @throws IllegalArgumentException when invalid or reserved
     */
    public static void validateNonLatestLabel(String label) {
        validateLabel(label);
        if ("latest".equals(label)) {
            throw new IllegalArgumentException("The latest label is server-managed");
        }
    }
    
    /**
     * Validate a transport token without changing its case.
     *
     * @param transport transport token
     * @throws IllegalArgumentException when invalid
     */
    public static void validateTransport(String transport) {
        if (transport == null || transport.length() > MAX_TRANSPORT_LENGTH
            || !TRANSPORT_PATTERN.matcher(transport).matches()) {
            throw new IllegalArgumentException("Invalid transport: " + transport);
        }
    }
    
    /**
     * Validate a Version content digest.
     *
     * @param contentDigest content digest
     * @throws IllegalArgumentException when invalid
     */
    public static void validateContentDigest(String contentDigest) {
        if (contentDigest == null || !CONTENT_DIGEST_PATTERN.matcher(contentDigest).matches()) {
            throw new IllegalArgumentException("Invalid contentDigest: " + contentDigest);
        }
    }
    
    /**
     * Validate a descriptor media type.
     *
     * @param mediaType media type
     * @throws IllegalArgumentException when invalid
     */
    public static void validateMediaType(String mediaType) {
        if (mediaType == null || mediaType.length() > MAX_MEDIA_TYPE_LENGTH
            || !MEDIA_TYPE_PATTERN.matcher(mediaType).matches()) {
            throw new IllegalArgumentException("Invalid descriptorMediaType: " + mediaType);
        }
    }
    
    /**
     * Validate a required JSON-compatible value after request binding.
     *
     * @param value JSON-compatible value
     * @param fieldName field name used in validation errors
     * @throws IllegalArgumentException when the value is {@code null}
     */
    public static void validateNonNullJsonValue(Object value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be JSON null");
        }
    }
    
    /**
     * Validate optional public Endpoint metadata.
     *
     * @param metadata Endpoint metadata, or {@code null}
     * @throws IllegalArgumentException when invalid or using a reserved key
     */
    public static void validateEndpointMetadata(Map<String, String> metadata) {
        if (metadata == null) {
            return;
        }
        if (metadata.size() > MAX_METADATA_SIZE) {
            throw new IllegalArgumentException(
                "Endpoint metadata exceeds " + MAX_METADATA_SIZE + " entries");
        }
        for (Map.Entry<String, String> entry : metadata.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key == null || key.isEmpty() || codePointLength(key) > MAX_METADATA_KEY_LENGTH
                || isReservedMetadataKey(key)) {
                throw new IllegalArgumentException("Invalid Endpoint metadata key: " + key);
            }
            if (value == null || codePointLength(value) > MAX_METADATA_VALUE_LENGTH) {
                throw new IllegalArgumentException(
                    "Invalid Endpoint metadata value for key: " + key);
            }
        }
    }
    
    private static boolean isReservedMetadataKey(String key) {
        return "preserved.heart.beat.interval".equals(key)
            || "preserved.heart.beat.timeout".equals(key)
            || "preserved.ip.delete.timeout".equals(key)
            || key.startsWith(INTERNAL_ENDPOINT_METADATA_PREFIX);
    }
    
    private static int codePointLength(String value) {
        return value.codePointCount(0, value.length());
    }
    
    private static void validatePrintableAscii(String value, String fieldName) {
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (current < 0x21 || current > 0x7E) {
                throw new IllegalArgumentException("Invalid " + fieldName + ": " + value);
            }
        }
    }
}
