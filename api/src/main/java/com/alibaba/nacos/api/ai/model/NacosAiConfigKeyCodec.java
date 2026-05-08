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

/**
 * Reversible encoding for Nacos Config {@code dataId} / {@code group} segments.
 *
 * <p>Aligned with Config Server {@code ParamUtils.isValid}: only letters, digits, and {@code _ - . :}.
 * Invalid strings are stored as {@code enc.} + lowercase hexadecimal UTF-8 bytes (never contains {@code __}),
 * so {@code skill_{name}__{version}} group layout stays unambiguous.</p>
 */
public final class NacosAiConfigKeyCodec {
    
    /**
     * Prefix for encoded segments; every character is valid for Nacos config parameters.
     */
    public static final String ENCODED_PREFIX = "enc.";
    
    private static final String DOUBLE_UNDERSCORE = "__";
    
    private NacosAiConfigKeyCodec() {
    }
    
    /**
     * Return true if the character is allowed in Nacos dataId / group ({@code ParamUtils} rules).
     */
    public static boolean isValidNacosConfigChar(char ch) {
        return Character.isLetterOrDigit(ch) || ch == '_' || ch == '-' || ch == '.' || ch == ':';
    }
    
    /**
     * Return true if the string is non-null and every character is allowed for Nacos config parameters.
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
     * If already valid for Nacos, returned unchanged (including null/empty).
     */
    public static String encodeSegment(String raw) {
        if (raw == null || raw.isEmpty()) {
            return raw;
        }
        if (!isValidNacosConfigParam(raw)) {
            return ENCODED_PREFIX + toHex(raw.getBytes(StandardCharsets.UTF_8));
        }
        return raw;
    }
    
    /**
     * Encode a skill / AgentSpec <em>manifest</em> group name segment (single segment after the type prefix).
     * Like {@link #encodeSegment(String)} but also hex-wraps when the name contains {@code __} so it can be
     * distinguished from the {@code name__version} delimiter layout when parsing versioned groups is needed.
     */
    public static String encodeManifestGroupNameSegment(String raw) {
        if (raw == null || raw.isEmpty()) {
            return raw;
        }
        if (!isValidNacosConfigParam(raw) || raw.contains(DOUBLE_UNDERSCORE)) {
            return ENCODED_PREFIX + toHex(raw.getBytes(StandardCharsets.UTF_8));
        }
        return raw;
    }
    
    /**
     * Encode a name or version segment that appears in a <em>versioned</em> Nacos group.
     * Always uses prefix {@link #ENCODED_PREFIX} plus hex so the literal {@code __} delimiter cannot appear inside a segment,
     * and parsing by splitting on the last {@code __} is unambiguous.
     */
    public static String encodeVersionedGroupSegment(String raw) {
        if (raw == null) {
            return null;
        }
        return ENCODED_PREFIX + toHex(raw.getBytes(StandardCharsets.UTF_8));
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
}
