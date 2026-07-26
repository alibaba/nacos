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

package com.alibaba.nacos.ai.service.agent.identity;

import com.alibaba.nacos.api.ai.utils.AgentValidationUtils;

/**
 * Codec for the {@code rad-ascii-v1} physical Agent identifier.
 *
 * <p>The public Agent identity remains the original Agent name. This codec is only used when an
 * internal Config or Naming identifier must contain {@code [A-Za-z0-9-]} characters. Decode is
 * limited to typed physical segments produced by this codec; callers must not use it to infer a
 * public identity from an untyped key.</p>
 *
 * @author Nacos
 */
public final class RadAsciiAgentIdCodec {
    
    /**
     * Stable codec identifier recorded by the Agent storage contract.
     */
    public static final String CODEC_ID = "rad-ascii-v1";
    
    private static final String ENCODED_PREFIX = "enc-";
    
    private static final int MAX_ENCODED_LENGTH = 260;
    
    private RadAsciiAgentIdCodec() {
    }
    
    /**
     * Encode an Agent name using the {@code rad-ascii-v1} contract.
     *
     * <p>Names already containing only ASCII letters, digits, and hyphens are returned unchanged.
     * Otherwise the encoded form starts with {@code enc-}; letters and digits are retained, while
     * every other printable ASCII character, including a hyphen, is represented by a hyphen and
     * its three-digit decimal ASCII value.</p>
     *
     * @param agentName original public Agent name
     * @return physical identifier containing only {@code [A-Za-z0-9-]}
     * @throws IllegalArgumentException when the Agent name violates the Agent identity contract
     */
    public static String encode(String agentName) {
        AgentValidationUtils.validateAgentName(agentName);
        if (isSafe(agentName)) {
            return agentName;
        }
        StringBuilder result = new StringBuilder(ENCODED_PREFIX.length() + agentName.length() * 4);
        result.append(ENCODED_PREFIX);
        for (int i = 0; i < agentName.length(); i++) {
            char current = agentName.charAt(i);
            if (isAsciiLetterOrDigit(current)) {
                result.append(current);
            } else {
                appendDecimalEscape(result, current);
            }
        }
        return result.toString();
    }
    
    /**
     * Decode a typed {@code rad-ascii-v1} physical identifier.
     *
     * <p>Values without the encoded prefix must already be canonical safe Agent names. Encoded
     * values accept only literal ASCII letters or digits and canonical {@code -DDD} escapes. A
     * final encode comparison rejects any representation that this codec would not generate.</p>
     *
     * @param encodedAgentId typed physical Agent identifier
     * @return decoded public Agent name
     * @throws IllegalArgumentException when the identifier is malformed or non-canonical
     */
    public static String decode(String encodedAgentId) {
        validateEncodedCharacters(encodedAgentId);
        if (!encodedAgentId.startsWith(ENCODED_PREFIX)) {
            AgentValidationUtils.validateAgentName(encodedAgentId);
            return encodedAgentId;
        }
        StringBuilder result = new StringBuilder(encodedAgentId.length());
        for (int i = ENCODED_PREFIX.length(); i < encodedAgentId.length();) {
            char current = encodedAgentId.charAt(i);
            if (isAsciiLetterOrDigit(current)) {
                result.append(current);
                i++;
            } else {
                result.append(decodeEscape(encodedAgentId, i));
                i += 4;
            }
        }
        String decoded = result.toString();
        AgentValidationUtils.validateAgentName(decoded);
        if (!encodedAgentId.equals(encode(decoded))) {
            throw invalidEncodedId(encodedAgentId);
        }
        return decoded;
    }
    
    private static boolean isSafe(String value) {
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (!isAsciiLetterOrDigit(current) && current != '-') {
                return false;
            }
        }
        return true;
    }
    
    private static boolean isAsciiLetterOrDigit(char value) {
        return value >= 'A' && value <= 'Z' || value >= 'a' && value <= 'z'
            || value >= '0' && value <= '9';
    }
    
    private static void appendDecimalEscape(StringBuilder result, char value) {
        int decimal = value;
        result.append('-').append((char) ('0' + decimal / 100))
            .append((char) ('0' + decimal / 10 % 10)).append((char) ('0' + decimal % 10));
    }
    
    private static void validateEncodedCharacters(String encodedAgentId) {
        if (encodedAgentId == null || encodedAgentId.isEmpty()
            || encodedAgentId.length() > MAX_ENCODED_LENGTH) {
            throw invalidEncodedId(encodedAgentId);
        }
        for (int i = 0; i < encodedAgentId.length(); i++) {
            char current = encodedAgentId.charAt(i);
            if (!isAsciiLetterOrDigit(current) && current != '-') {
                throw invalidEncodedId(encodedAgentId);
            }
        }
    }
    
    private static char decodeEscape(String encodedAgentId, int offset) {
        if (offset + 4 > encodedAgentId.length()) {
            throw invalidEncodedId(encodedAgentId);
        }
        int hundreds = decimalDigit(encodedAgentId, offset + 1);
        int tens = decimalDigit(encodedAgentId, offset + 2);
        int ones = decimalDigit(encodedAgentId, offset + 3);
        char decoded = (char) (hundreds * 100 + tens * 10 + ones);
        if (decoded < 0x20 || decoded > 0x7E || isAsciiLetterOrDigit(decoded)) {
            throw invalidEncodedId(encodedAgentId);
        }
        return decoded;
    }
    
    private static int decimalDigit(String encodedAgentId, int offset) {
        char current = encodedAgentId.charAt(offset);
        if (current < '0' || current > '9') {
            throw invalidEncodedId(encodedAgentId);
        }
        return current - '0';
    }
    
    private static IllegalArgumentException invalidEncodedId(String encodedAgentId) {
        return new IllegalArgumentException("Invalid rad-ascii-v1 Agent identifier: "
            + encodedAgentId);
    }
}
