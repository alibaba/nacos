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

import com.alibaba.nacos.api.ai.model.a2a.AgentEndpoint;
import com.alibaba.nacos.api.ai.model.a2a.AgentInterface;
import com.alibaba.nacos.api.ai.model.agent.Endpoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class A2aEndpointUtilsTest {
    
    @ParameterizedTest
    @CsvSource({"127.0.0.1,80,HTTP,false,http://127.0.0.1:80/rpc?q=1",
        "example.com,443,HTTP,true,https://example.com:443/rpc?q=1",
        "2001:db8::1,8080,HTTP,false,http://[2001:db8::1]:8080/rpc?q=1",
        "[2001:db8::1],8080,HTTP,false,http://[2001:db8::1]:8080/rpc?q=1",
        "example.com,443,WSS,true,wss://example.com:443/rpc?q=1"})
    void convertsAddressWithoutMutatingCaller(String host, int port, String scheme, boolean tls,
        String expected) {
        AgentEndpoint source = source();
        source.setAddress(host);
        source.setPort(port);
        source.setProtocol(scheme);
        source.setSupportTls(tls);
        source.setPath("rpc");
        source.setQuery("q=1");
        source.setTenant("tenant-a");
        source.setProtocolVersion("1.0");
        String before = source.toString();
        Endpoint endpoint = A2aEndpointUtils.toEndpoint(source);
        assertEquals(expected, endpoint.getUri());
        assertEquals("tenant-a", endpoint.getMetadata().get("__nacos.agent.endpoint.tenant__"));
        assertEquals("1.0", endpoint.getMetadata().get("__nacos.agent.endpoint.protocolVersion__"));
        assertNull(endpoint.getBindings());
        assertEquals(before, source.toString());
        AgentInterface projected = A2aEndpointUtils.toAgentInterface(endpoint, "0.3");
        assertEquals("1.0", projected.getProtocolVersion());
        assertEquals("tenant-a", projected.getTenant());
        assertEquals(endpoint.getTransport(), projected.getProtocolBinding());
        assertEquals(endpoint.getTransport(), projected.getTransport());
    }
    
    @Test
    void usesLegacyUnicodeWhitespaceRulesWithoutTreatingNulAsWhitespace() {
        AgentEndpoint endpoint = source();
        endpoint.setProtocol("\u2003");
        endpoint.setPath("\u2003");
        assertEquals("http://example.com:80", A2aEndpointUtils.toEndpoint(endpoint).getUri());
        endpoint.setProtocol("\u0000");
        assertThrows(IllegalArgumentException.class, () -> A2aEndpointUtils.toEndpoint(endpoint));
    }
    
    @Test
    void missingProtocolAndEmptyPathUseLegacyDefaults() {
        AgentEndpoint source = source();
        source.setProtocol(" ");
        source.setPath(" ");
        source.setQuery(" ");
        source.setTenant("");
        source.setProtocolVersion("");
        Endpoint endpoint = A2aEndpointUtils.toEndpoint(source);
        assertEquals("http://example.com:80", endpoint.getUri());
        AgentInterface projected = A2aEndpointUtils.toAgentInterface(endpoint, "0.3");
        assertEquals("0.3", projected.getProtocolVersion());
        assertEquals("", projected.getTenant());
    }
    
    @Test
    void publicKeysWinIncludingEmptyTenantButInvalidValuesNeverFallBack() {
        Map<String, String> metadata = new LinkedHashMap<String, String>();
        assertEquals("old", A2aEndpointUtils.protocolVersion(metadata, "old"));
        assertEquals("old-tenant", A2aEndpointUtils.tenant(metadata, "old-tenant"));
        metadata.put("__nacos.agent.endpoint.protocolVersion__", "1.0");
        metadata.put("__nacos.agent.endpoint.tenant__", "");
        AgentValidationUtils.validateEndpointMetadata(metadata);
        assertEquals("1.0", A2aEndpointUtils.protocolVersion(metadata, "old"));
        assertEquals("", A2aEndpointUtils.tenant(metadata, "old-tenant"));
        for (String invalid : new String[] {null, "", "bad space",
            String.join("", Collections.nCopies(65, "x"))}) {
            metadata.put("__nacos.agent.endpoint.protocolVersion__", invalid);
            assertThrows(IllegalArgumentException.class,
                () -> A2aEndpointUtils.protocolVersion(metadata, "old"));
            assertThrows(IllegalArgumentException.class,
                () -> AgentValidationUtils.validateEndpointMetadata(metadata));
        }
        metadata.put("__nacos.agent.endpoint.tenant__", null);
        assertThrows(IllegalArgumentException.class,
            () -> A2aEndpointUtils.tenant(metadata, "old"));
        metadata.put("__nacos.agent.endpoint.tenant__",
            String.join("", Collections.nCopies(257, "x")));
        assertThrows(IllegalArgumentException.class,
            () -> A2aEndpointUtils.tenant(metadata, "old"));
    }
    
    @Test
    void invalidAddressesAndInternalControlKeysAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> A2aEndpointUtils.toEndpoint(null));
        AgentEndpoint source = source();
        source.setPort(0);
        assertThrows(IllegalArgumentException.class, () -> A2aEndpointUtils.toEndpoint(source));
        source.setPort(80);
        source.setAddress("");
        assertThrows(IllegalArgumentException.class, () -> A2aEndpointUtils.toEndpoint(source));
        assertThrows(IllegalArgumentException.class,
            () -> AgentValidationUtils.validateEndpointMetadata(
                Collections.singletonMap("__nacos.agent.endpoint.path__", "spoof")));
    }
    
    @Test
    void rootedPathAndMissingMetadataUseExplicitFallbackRules() {
        AgentEndpoint input = source();
        input.setPath("/rpc");
        assertEquals("http://example.com:80/rpc", A2aEndpointUtils.toEndpoint(input).getUri());
        assertNull(A2aEndpointUtils.protocolVersion(null, null));
        assertNull(A2aEndpointUtils.protocolVersion(null, ""));
        assertEquals("1.0", A2aEndpointUtils.protocolVersion(null, "1.0"));
        assertNull(A2aEndpointUtils.tenant(null, null));
        assertNull(A2aEndpointUtils.tenant(null, ""));
        assertEquals("team", A2aEndpointUtils.tenant(null, "team"));
        assertNull(A2aEndpointUtils.protocolVersion(Collections.emptyMap(), ""));
        assertNull(A2aEndpointUtils.tenant(Collections.emptyMap(), ""));
    }
    
    private AgentEndpoint source() {
        AgentEndpoint source = new AgentEndpoint();
        source.setAddress("example.com");
        source.setPort(80);
        source.setVersion("1.0.0");
        return source;
    }
}
