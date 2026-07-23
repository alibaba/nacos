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

package com.alibaba.nacos.ai.service.agent.metadata;

import com.alibaba.nacos.api.ai.utils.AgentValidationUtils;

/**
 * Internal protocol-search token contract used in the Agent {@code biz_tags} projection.
 *
 * @author Nacos
 */
public final class AgentProtocolSearchTag {
    
    public static final String INTERNAL_PREFIX = "__nacos.agent.";
    
    public static final String PROTOCOL_PREFIX = INTERNAL_PREFIX + "protocol:";
    
    private AgentProtocolSearchTag() {
    }
    
    /**
     * Create the reserved search token for a canonical Agent protocol.
     *
     * @param protocol canonical protocol token
     * @return internal protocol-search token
     */
    public static String encode(String protocol) {
        AgentValidationUtils.validateProtocol(protocol);
        return PROTOCOL_PREFIX + protocol;
    }
    
    /**
     * Decode and validate one reserved protocol-search token.
     *
     * @param tag persisted internal tag
     * @return canonical protocol token
     */
    public static String decode(String tag) {
        if (tag == null || !tag.startsWith(PROTOCOL_PREFIX)) {
            throw new IllegalArgumentException("Invalid Agent protocol search tag: " + tag);
        }
        String protocol = tag.substring(PROTOCOL_PREFIX.length());
        AgentValidationUtils.validateProtocol(protocol);
        return protocol;
    }
    
    /**
     * Test whether a tag belongs to the server-reserved Agent namespace.
     *
     * @param tag tag value
     * @return {@code true} when the tag must not be exposed as a public Agent tag
     */
    public static boolean isInternal(String tag) {
        return tag != null && tag.startsWith(INTERNAL_PREFIX);
    }
}
