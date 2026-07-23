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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentProtocolSearchTagTest {
    
    @Test
    void testRoundTripPreservesCanonicalProtocol() {
        assertRoundTrip("a2a");
        assertRoundTrip("A2A-v1");
        assertRoundTrip("a");
        assertRoundTrip("a1234567890123456789012345678901");
    }
    
    @Test
    void testInternalNamespaceDetection() {
        assertTrue(AgentProtocolSearchTag.isInternal("__nacos.agent.protocol:a2a"));
        assertTrue(AgentProtocolSearchTag.isInternal("__nacos.agent.future:value"));
        assertFalse(AgentProtocolSearchTag.isInternal("a2a"));
        assertFalse(AgentProtocolSearchTag.isInternal("__nacos.other:value"));
        assertFalse(AgentProtocolSearchTag.isInternal(null));
    }
    
    @Test
    void testRejectInvalidProtocolOrToken() {
        assertThrows(IllegalArgumentException.class,
            () -> AgentProtocolSearchTag.encode(null));
        assertThrows(IllegalArgumentException.class,
            () -> AgentProtocolSearchTag.encode("a2a_rpc"));
        assertThrows(IllegalArgumentException.class,
            () -> AgentProtocolSearchTag.decode(null));
        assertThrows(IllegalArgumentException.class,
            () -> AgentProtocolSearchTag.decode("a2a"));
        assertThrows(IllegalArgumentException.class,
            () -> AgentProtocolSearchTag.decode(AgentProtocolSearchTag.PROTOCOL_PREFIX));
        assertThrows(IllegalArgumentException.class,
            () -> AgentProtocolSearchTag.decode(
                AgentProtocolSearchTag.PROTOCOL_PREFIX + "a2a_rpc"));
    }
    
    private void assertRoundTrip(String protocol) {
        String tag = AgentProtocolSearchTag.encode(protocol);
        assertEquals(AgentProtocolSearchTag.PROTOCOL_PREFIX + protocol, tag);
        assertEquals(protocol, AgentProtocolSearchTag.decode(tag));
        assertTrue(AgentProtocolSearchTag.isInternal(tag));
    }
}
