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

import com.alibaba.nacos.api.ai.model.agent.Endpoint;

import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EndpointNaturalKeyTest {
    
    @Test
    void testNonIdentityFieldsAndUriPathDoNotChangeKey() {
        Endpoint first = endpoint("HTTPS://Example.COM/a?route=one", "JSONRPC");
        first.setPriority(1);
        first.setWeight(2D);
        first.setMetadata(Collections.singletonMap("zone", "z1"));
        first.setHealthy(true);
        
        Endpoint second = endpoint("https://example.com:443/b?route=two", "JSONRPC");
        second.setPriority(100);
        second.setWeight(999D);
        second.setMetadata(Collections.singletonMap("zone", "z2"));
        second.setHealthy(false);
        
        EndpointNaturalKey firstKey = EndpointNaturalKey.of("public", "Nacos Agent", "a2a", first);
        EndpointNaturalKey secondKey =
            EndpointNaturalKey.of("public", "Nacos Agent", "a2a", second);
        assertEquals(firstKey, secondKey);
        assertEquals(firstKey.hashCode(), secondKey.hashCode());
        assertEquals("example.com", firstKey.getNormalizedHost());
        assertEquals(443, firstKey.getEffectivePort());
    }
    
    @Test
    void testEveryIdentityFieldIsCaseSensitiveExceptCanonicalHost() {
        EndpointNaturalKey base =
            EndpointNaturalKey.of("public", "Agent", "a2a", "https://EXAMPLE.com/a",
                "JSONRPC");
        assertEquals(base,
            EndpointNaturalKey.of("public", "Agent", "a2a", "https://example.COM:443/b",
                "JSONRPC"));
        assertNotEquals(base,
            EndpointNaturalKey.of("public", "agent", "a2a", "https://example.com", "JSONRPC"));
        assertNotEquals(base,
            EndpointNaturalKey.of("public", "Agent", "A2A", "https://example.com", "JSONRPC"));
        assertNotEquals(base,
            EndpointNaturalKey.of("public", "Agent", "a2a", "https://example.com", "jsonrpc"));
        assertNotEquals(base,
            EndpointNaturalKey.of("another", "Agent", "a2a", "https://example.com", "JSONRPC"));
    }
    
    @Test
    void testAccessorsComparisonAndStringRepresentation() {
        EndpointNaturalKey base =
            EndpointNaturalKey.of("public", "Agent", "a2a", "https://example.com", "JSONRPC");
        assertEquals("public", base.getNamespaceId());
        assertEquals("Agent", base.getAgentName());
        assertEquals("a2a", base.getProtocol());
        assertEquals("JSONRPC", base.getTransport());
        assertEquals("public/Agent/a2a/example.com:443/JSONRPC", base.toString());
        assertEquals(0, base.compareTo(base));
        assertTrue(base.compareTo(key("z-public", "Agent", "a2a", "example.com", 443,
            "JSONRPC")) < 0);
        assertTrue(base.compareTo(key("public", "Z-Agent", "a2a", "example.com", 443,
            "JSONRPC")) < 0);
        assertTrue(base.compareTo(key("public", "Agent", "z-protocol", "example.com", 443,
            "JSONRPC")) < 0);
        assertTrue(base.compareTo(key("public", "Agent", "a2a", "z.example.com", 443,
            "JSONRPC")) < 0);
        assertTrue(base.compareTo(key("public", "Agent", "a2a", "example.com", 444,
            "JSONRPC")) < 0);
        assertTrue(base.compareTo(key("public", "Agent", "a2a", "example.com", 443,
            "Z-TRANSPORT")) < 0);
        assertThrows(NullPointerException.class, () -> base.compareTo(null));
    }
    
    @Test
    void testNullEndpointAndEqualityShortcuts() {
        assertThrows(IllegalArgumentException.class,
            () -> EndpointNaturalKey.of("public", "Agent", "a2a", (Endpoint) null));
        EndpointNaturalKey key =
            EndpointNaturalKey.of("public", "Agent", "a2a", "https://example.com", "JSONRPC");
        assertEquals(key, key);
        assertNotEquals(key, "public/Agent/a2a/example.com:443/JSONRPC");
    }
    
    private EndpointNaturalKey key(String namespaceId, String agentName, String protocol,
        String host, int port, String transport) {
        return EndpointNaturalKey.of(namespaceId, agentName, protocol,
            "https://" + host + ':' + port, transport);
    }
    
    private Endpoint endpoint(String uri, String transport) {
        Endpoint endpoint = new Endpoint();
        endpoint.setUri(uri);
        endpoint.setTransport(transport);
        return endpoint;
    }
}
