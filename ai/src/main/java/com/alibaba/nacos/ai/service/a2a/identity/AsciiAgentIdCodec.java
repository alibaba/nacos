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

import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.utils.ParamUtils;

import java.util.Set;

/**
 * Agent Identity Codec implement by ASCII.
 *
 * <p>
 *     Only consider show-able ASCII code from 32(x20) to 126(x7E).
 * </p>
 *
 * @author xiweng.yy
 */
public class AsciiAgentIdCodec implements AgentIdCodec {
    
    private static final String ENCODE_PREFIX = "____:";
    
    private static final char ENCODE_MARK_CHAR = '_';
    
    /**
     * Come From {@link ParamUtils#validChars} and remove {@link #ENCODE_MARK_CHAR}.
     */
    private static final Set<Character> VALID_CHAR = Set.of('-', '.', ':');
    
    @Override
    public String encode(String agentName) {
        if (!isNeedEncoded(agentName)) {
            return agentName;
        }
        
        StringBuilder sb = new StringBuilder(ENCODE_PREFIX);
        for (char ch : agentName.toCharArray()) {
            if (Character.isLetter(ch) || VALID_CHAR.contains(ch)) {
                // Keep letters, valid characters and non-underscores
                // number should be encoded, because the encoded result will contain numbers
                // which will cause search agentName contains unexpected results.
                sb.append(ch);
            } else {
                sb.append(ENCODE_MARK_CHAR).append(String.format("%03d", (int) ch));
            }
        }
        return sb.toString();
    }
    
    @Override
    public String encodeForSearch(String agentName) {
        String encodedName = encode(agentName);
        return isEncoded(encodedName) ? encodedName.substring(ENCODE_PREFIX.length()) : encodedName;
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
            if (ch == '_' && i + 4 <= body.length()) {
                String codePart = body.substring(i + 1, i + 4);
                if (isDigit(codePart)) {
                    int codePoint = Integer.parseInt(codePart, 10);
                    sb.append((char) codePoint);
                    i += 4;
                    continue;
                }
            }
            
            sb.append(ch);
            i++;
        }
        return sb.toString();
    }
    
    private boolean isDigit(String s) {
        for (char c : s.toCharArray()) {
            if (!Character.isDigit(c)) {
                return false;
            }
        }
        return s.length() == 3;
    }
    
    private boolean isEncoded(String name) {
        return name != null && name.startsWith(ENCODE_PREFIX);
    }
    
    private boolean isNeedEncoded(String name) {
        if (StringUtils.isEmpty(name)) {
            return false;
        }
        if (name.startsWith(ENCODE_PREFIX)) {
            return false;
        }
        for (char ch : name.toCharArray()) {
            if (!Character.isLetter(ch) && !VALID_CHAR.contains(ch)) {
                return true;
            }
        }
        return false;
    }
}
