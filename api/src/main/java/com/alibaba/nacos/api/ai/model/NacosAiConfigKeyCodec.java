/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.api.ai.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Segment encoding and physical key mapping for AI resources stored in Nacos Config.
 *
 * <p>Uses an ASCII-safe physical key set: letters, digits, and {@code _ - . :}.
 * Invalid strings and logical values in the reserved {@code enc.} namespace are stored as
 * {@code enc.} plus lowercase hexadecimal UTF-8 bytes and never contain {@code __}, so the
 * {@code skill_{name}__{version}} group layout stays unambiguous.
 * Encoded values that exceed Nacos Config storage limits use a deterministic, non-reversible
 * SHA-256 fallback.</p>
 */
public final class NacosAiConfigKeyCodec {
    
    /**
     * Prefix for encoded segments; every character is valid for Nacos config parameters.
     */
    public static final String ENCODED_PREFIX = "enc.";
    
    /**
     * Prefix for physical keys that use a SHA-256 length fallback.
     */
    public static final String HASHED_PREFIX = "sha256.";
    
    /**
     * Maximum physical Nacos Config dataId length.
     */
    public static final int MAX_DATA_ID_LENGTH = 255;
    
    /**
     * Maximum physical Nacos Config group length.
     */
    public static final int MAX_GROUP_LENGTH = 128;
    
    private static final String DOUBLE_UNDERSCORE = "__";
    
    private static final String SHA_256 = "SHA-256";
    
    private NacosAiConfigKeyCodec() {
    }
    
    /**
     * Return true if the character is allowed in an ASCII-safe Nacos dataId / group segment.
     */
    public static boolean isValidNacosConfigChar(char ch) {
        boolean asciiLetterOrDigit = ch >= 'a' && ch <= 'z'
            || ch >= 'A' && ch <= 'Z'
            || ch >= '0' && ch <= '9';
        return asciiLetterOrDigit || ch == '_' || ch == '-' || ch == '.' || ch == ':';
    }
    
    /**
     * Return true if the string is non-null and every character is allowed for Nacos config
     * parameters.
     */
    public static boolean isValidNacosConfigParam(String s) {
        if (s == null) {
            return false;
        }
        int len = s.length();
        for (int i = 0; i < len; i++) {
            if (!isValidNacosConfigChar(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Encode a logical segment for use in Nacos {@code dataId} or other keys.
     * If already valid for Nacos and outside the reserved {@code enc.} namespace, returned
     * unchanged (including null/empty).
     */
    public static String encodeSegment(String raw) {
        if (raw == null || raw.isEmpty()) {
            return raw;
        }
        if (!isValidNacosConfigParam(raw) || hasReservedEncodedPrefix(raw)) {
            return ENCODED_PREFIX + toHex(raw.getBytes(StandardCharsets.UTF_8));
        }
        return raw;
    }
    
    /**
     * Encode a skill / AgentSpec <em>manifest</em> group name segment (single segment after
     * the type prefix). Like {@link #encodeSegment(String)} but also hex-wraps when the name
     * contains {@code __} or matches the reserved SHA-256 fallback shape, so it can be
     * distinguished from physical group syntax and the {@code name__version} delimiter layout.
     */
    public static String encodeManifestGroupNameSegment(String raw) {
        if (raw == null || raw.isEmpty()) {
            return raw;
        }
        if (!isValidNacosConfigParam(raw) || hasReservedEncodedPrefix(raw)
            || isHashedPhysicalKey(raw, "")
            || raw.contains(DOUBLE_UNDERSCORE)) {
            return ENCODED_PREFIX + toHex(raw.getBytes(StandardCharsets.UTF_8));
        }
        return raw;
    }
    
    /**
     * Encode a name or version segment that appears in a <em>versioned</em> Nacos group.
     * Always uses prefix {@link #ENCODED_PREFIX} plus hex so the literal {@code __} delimiter
     * cannot appear inside a segment, and parsing by splitting on the last {@code __} is
     * unambiguous.
     */
    public static String encodeVersionedGroupSegment(String raw) {
        if (raw == null) {
            return null;
        }
        return ENCODED_PREFIX + toHex(raw.getBytes(StandardCharsets.UTF_8));
    }
    
    /**
     * Map a logical dataId to its stable physical Nacos Config dataId.
     *
     * <p>The reversible segment encoding is retained while it fits the storage limit. An encoded
     * value that exceeds the limit uses {@code sha256.} plus the complete SHA-256 digest.</p>
     *
     * @param logicalDataId logical dataId
     * @return stable physical dataId
     */
    public static String toPhysicalDataId(String logicalDataId) {
        return fitToLength(encodeSegment(logicalDataId), MAX_DATA_ID_LENGTH, "");
    }
    
    /**
     * Map a canonical AI resource group to its stable physical Nacos Config group.
     *
     * <p>The canonical group is retained while it fits the storage limit. An overlong group uses
     * the resource prefix followed by {@code sha256.} and the complete SHA-256 digest.</p>
     *
     * @param canonicalGroup canonical group built from resource name and version
     * @param resourcePrefix stable resource prefix to preserve in a hashed group
     * @return stable physical group
     */
    public static String toPhysicalGroup(String canonicalGroup, String resourcePrefix) {
        return fitToLength(canonicalGroup, MAX_GROUP_LENGTH, resourcePrefix);
    }
    
    /**
     * Decode a segment produced by {@link #encodeSegment(String)}.
     * If not encoded with {@link #ENCODED_PREFIX}, returned unchanged.
     */
    public static String decodeSegment(String encoded) {
        if (encoded == null || encoded.isEmpty()) {
            return encoded;
        }
        if (!encoded.startsWith(ENCODED_PREFIX)) {
            return encoded;
        }
        String hex = encoded.substring(ENCODED_PREFIX.length());
        if (hex.isEmpty()) {
            throw new IllegalArgumentException("empty payload after " + ENCODED_PREFIX);
        }
        return new String(fromHex(hex), StandardCharsets.UTF_8);
    }
    
    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
    
    private static byte[] fromHex(String hex) {
        int len = hex.length();
        if ((len & 1) != 0) {
            throw new IllegalArgumentException("illegal hex length: " + len);
        }
        byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            int hi = Character.digit(hex.charAt(i), 16);
            int lo = Character.digit(hex.charAt(i + 1), 16);
            if (hi < 0 || lo < 0) {
                throw new IllegalArgumentException("illegal hex at index " + i);
            }
            out[i / 2] = (byte) ((hi << 4) + lo);
        }
        return out;
    }
    
    private static boolean hasReservedEncodedPrefix(String value) {
        return value.regionMatches(true, 0, ENCODED_PREFIX, 0, ENCODED_PREFIX.length());
    }
    
    private static String fitToLength(String candidate, int maxLength, String preservedPrefix) {
        if (candidate == null) {
            return null;
        }
        String prefix = preservedPrefix == null ? "" : preservedPrefix;
        if (candidate.length() <= maxLength && !isHashedPhysicalKey(candidate, prefix)) {
            return candidate;
        }
        String result = prefix + HASHED_PREFIX + sha256Hex(candidate);
        if (result.length() > maxLength) {
            throw new IllegalArgumentException(
                "Physical key prefix leaves insufficient room for a SHA-256 digest");
        }
        return result;
    }
    
    private static boolean isHashedPhysicalKey(String candidate, String preservedPrefix) {
        if (candidate == null) {
            return false;
        }
        String marker = (preservedPrefix == null ? "" : preservedPrefix) + HASHED_PREFIX;
        if (candidate.length() != marker.length() + 64
            || !candidate.regionMatches(true, 0, marker, 0, marker.length())) {
            return false;
        }
        for (int i = marker.length(); i < candidate.length(); i++) {
            if (Character.digit(candidate.charAt(i), 16) < 0) {
                return false;
            }
        }
        return true;
    }
    
    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance(SHA_256);
            return toHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
