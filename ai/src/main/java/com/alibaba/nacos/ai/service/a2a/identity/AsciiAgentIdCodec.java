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

package com.alibaba.nacos.ai.service.a2a.identity;

import com.alibaba.nacos.config.server.utils.ParamUtils;

import java.util.Set;

/**
 * Agent Identity Codec implement by ASCII.
 *
 * @author xiweng.yy
 */
public class AsciiAgentIdCodec implements AgentIdCodec {
    
    private static final String ENCODE_PREFIX = "_-.SYSENC:";
    
    private static final char ENCODE_MARK_CHAR = '_';
    
    /**
     * Come From {@link ParamUtils#validChars} and remove {@link #ENCODE_MARK_CHAR}.
     */
    private static final Set<Character> VALID_CHAR = Set.of('-', '.', ':');
    
    @Override
    public String encode(String agentName) {
        if (ParamUtils.isValid(agentName)) {
            return agentName;
        }
        
        StringBuilder sb = new StringBuilder(ENCODE_PREFIX);
        for (char ch : agentName.toCharArray()) {
            if (Character.isLetterOrDigit(ch) || VALID_CHAR.contains(ch)) {
                // Keep letters, numbers, valid characters and non-underscores
                sb.append(ch);
            } else {
                sb.append(ENCODE_MARK_CHAR).append(String.format("%04x", (int) ch));
            }
        }
        return sb.toString();
    }
    
    @Override
    public String decode(String agentId) {
        if (!isEncoded(agentId)) {
            return agentId;
        }
        
        String body = agentId.substring(ENCODE_PREFIX.length());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < body.length(); ) {
            char ch = body.charAt(i);
            if (ch == '_' && i + 5 <= body.length()) {
                String hexPart = body.substring(i + 1, i + 5);
                if (isHex(hexPart)) {
                    try {
                        int codePoint = Integer.parseInt(hexPart, 16);
                        sb.append((char) codePoint);
                        i += 5;
                        continue;
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("invalid encoded name");
                    }
                }
            }
            
            sb.append(ch);
            i++;
        }
        return sb.toString();
    }
    
    private boolean isHex(String s) {
        for (char c : s.toCharArray()) {
            if (!Character.isDigit(c) && !isHexLetter(c)) {
                return false;
            }
        }
        return s.length() == 4;
    }
    
    private boolean isHexLetter(char c) {
        return (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }
    
    private boolean isEncoded(String name) {
        return name != null && name.startsWith(ENCODE_PREFIX);
    }
}
