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

package com.alibaba.nacos.ai.service.agent.fingerprint;

import com.alibaba.nacos.api.ai.model.agent.RuntimeVersionBinding;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryEndpoint;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeEndpointRevisionTest {
    
    private static final String DUAL_ENDPOINT_FRAME_HEX =
        "0000000200000017687474703a2f2f612e6578616d706c653a38302f727063000000044854"
            + "54500000000a3fe000000000000000000000010000000100000005312e302e30000000075b"
            + "312e302e305d0000002168747470733a2f2f622e6578616d706c653a3434332f6132613f62"
            + "3d3226613d31000000074a534f4e525043000000003ff00000000000000000000200000002"
            + "617a0000000162000000047a6f6e65000000027a32000000000100000005312e302e300000"
            + "00075b312e302e305d";
    
    private static final String NATURAL_KEY_FRAME_HEX =
        "0000000300000018687474703a2f2f73616d652e6578616d706c653a38302f61000000044752"
            + "5043000000003ff000000000000000000000010000000100000005312e302e30000000075b31"
            + "2e302e305d00000018687474703a2f2f73616d652e6578616d706c653a38302f62000000074a"
            + "534f4e525043000000003ff000000000000000000000000000000100000005312e302e300000"
            + "00075b312e302e305d0000001a687474703a2f2f73616d652e6578616d706c653a313030302f"
            + "7a0000000448545450000000003ff000000000000000000000010000000100000005312e302e"
            + "30000000075b312e302e305d";
    
    @Test
    void testEmptyProjectionGoldenVector() {
        byte[] frame = RuntimeEndpointRevision.revisionBytes("public", "Order Agent", "a2a",
            Collections.<AgentDiscoveryEndpoint>emptyList());
        assertEquals("00000000", toHex(frame));
        assertEquals("murmur3-x64-128-v1:cfa0f7ddd84c76bc589623161cf526f1",
            RuntimeEndpointRevision.compute("public", "Order Agent", "a2a",
                Collections.<AgentDiscoveryEndpoint>emptyList()));
        assertEquals("murmur3-x64-128-v1", RuntimeEndpointRevision.ALGORITHM_ID);
        assertEquals("murmur3-x64-128-v1:", RuntimeEndpointRevision.TOKEN_PREFIX);
    }
    
    @Test
    void testDualEndpointGoldenVectorAndNaturalKeyOrdering() {
        AgentDiscoveryEndpoint b =
            createEndpoint("HTTPS://B.EXAMPLE/a2a?b=2&a=1", "JSONRPC", false);
        Map<String, String> metadata = new LinkedHashMap<String, String>();
        metadata.put("zone", "z2");
        metadata.put("az", "b");
        b.setMetadata(metadata);
        AgentDiscoveryEndpoint a = createEndpoint("http://a.example/rpc", "HTTP", true);
        a.setPriority(10);
        a.setWeight(0.5D);
        List<AgentDiscoveryEndpoint> reversedInput = Arrays.asList(b, a);
        
        byte[] frame = RuntimeEndpointRevision.revisionBytes("public", "Order Agent", "a2a",
            reversedInput);
        assertEquals(194, frame.length);
        assertEquals(DUAL_ENDPOINT_FRAME_HEX, toHex(frame));
        assertEquals("murmur3-x64-128-v1:4e63c05c7d885f71da9921101b14c8b0",
            RuntimeEndpointRevision.compute("public", "Order Agent", "a2a", reversedInput));
        assertArrayEquals(frame,
            RuntimeEndpointRevision.revisionBytes("public", "Order Agent", "a2a",
                Arrays.asList(a, b)));
    }
    
    @Test
    void testNaturalKeyOrdersNumericPortThenTransport() {
        AgentDiscoveryEndpoint port1000 =
            createEndpoint("http://same.example:1000/z", "HTTP", true);
        AgentDiscoveryEndpoint port80Json =
            createEndpoint("http://same.example/b", "JSONRPC", false);
        AgentDiscoveryEndpoint port80Grpc =
            createEndpoint("http://same.example/a", "GRPC", true);
        List<AgentDiscoveryEndpoint> reversed =
            Arrays.asList(port1000, port80Json, port80Grpc);
        
        byte[] frame = RuntimeEndpointRevision.revisionBytes("public", "Agent", "a2a",
            reversed);
        assertEquals(240, frame.length);
        assertEquals(NATURAL_KEY_FRAME_HEX, toHex(frame));
        assertEquals("murmur3-x64-128-v1:7bea17b7faa9cb6c30a7bad931d2f4c1",
            RuntimeEndpointRevision.compute("public", "Agent", "a2a", reversed));
        assertArrayEquals(frame,
            RuntimeEndpointRevision.revisionBytes("public", "Agent", "a2a",
                Arrays.asList(port80Grpc, port80Json, port1000)));
    }
    
    @Test
    void testEquivalentDefaultsAndMetadataOrderHaveSameRevision() {
        AgentDiscoveryEndpoint omitted =
            createEndpoint("HTTP://EXAMPLE.COM/rpc", "HTTP", true);
        AgentDiscoveryEndpoint explicit =
            createEndpoint("http://example.com:80/rpc", "HTTP", true);
        explicit.setPriority(0);
        explicit.setWeight(1D);
        explicit.setMetadata(Collections.<String, String>emptyMap());
        assertEquals(revision(omitted), revision(explicit));
        
        AgentDiscoveryEndpoint firstOrder =
            createEndpoint("http://example.com/rpc", "HTTP", true);
        Map<String, String> firstMetadata = new LinkedHashMap<String, String>();
        firstMetadata.put("zone", "z1");
        firstMetadata.put("az", "a");
        firstOrder.setMetadata(firstMetadata);
        AgentDiscoveryEndpoint secondOrder =
            createEndpoint("http://example.com:80/rpc", "HTTP", true);
        Map<String, String> secondMetadata = new LinkedHashMap<String, String>();
        secondMetadata.put("az", "a");
        secondMetadata.put("zone", "z1");
        secondOrder.setMetadata(secondMetadata);
        assertEquals(revision(firstOrder), revision(secondOrder));
        
        AgentDiscoveryEndpoint firstBindingOrder =
            createEndpoint("http://bindings.example.com/rpc", "HTTP", true);
        firstBindingOrder.setBindings(Arrays.asList(binding("2.0.0", "[2.0.0]"),
            binding("1.0.0", "[1.0.0]")));
        AgentDiscoveryEndpoint secondBindingOrder =
            createEndpoint("http://bindings.example.com/rpc", "HTTP", true);
        secondBindingOrder.setBindings(Arrays.asList(binding("1.0.0", "[1.0.0]"),
            binding("2.0.0", "[2.0.0]")));
        assertEquals(revision(firstBindingOrder), revision(secondBindingOrder));
    }
    
    @Test
    void testEquivalentSignedZeroWeightsHaveSameRevision() {
        AgentDiscoveryEndpoint positiveZero =
            createEndpoint("http://example.com/rpc", "HTTP", true);
        positiveZero.setWeight(0D);
        AgentDiscoveryEndpoint negativeZero =
            createEndpoint("http://example.com/rpc", "HTTP", true);
        negativeZero.setWeight(-0D);
        assertEquals(revision(positiveZero), revision(negativeZero));
    }
    
    @Test
    void testAdjacentWeightsHaveDifferentRevisions() {
        AgentDiscoveryEndpoint baseline =
            createEndpoint("http://example.com/rpc", "HTTP", true);
        baseline.setWeight(1D);
        AgentDiscoveryEndpoint adjacent =
            createEndpoint("http://example.com/rpc", "HTTP", true);
        adjacent.setWeight(Math.nextUp(1D));
        assertNotEquals(revision(baseline), revision(adjacent));
    }
    
    @Test
    void testEveryIncludedEndpointFieldChangesRevision() {
        String baseline = revision(createEndpoint("http://example.com/a", "HTTP", true));
        
        AgentDiscoveryEndpoint uri = createEndpoint("http://example.com/b", "HTTP", true);
        assertNotEquals(baseline, revision(uri));
        AgentDiscoveryEndpoint transport =
            createEndpoint("http://example.com/a", "JSONRPC", true);
        assertNotEquals(baseline, revision(transport));
        AgentDiscoveryEndpoint priority =
            createEndpoint("http://example.com/a", "HTTP", true);
        priority.setPriority(1);
        assertNotEquals(baseline, revision(priority));
        AgentDiscoveryEndpoint weight =
            createEndpoint("http://example.com/a", "HTTP", true);
        weight.setWeight(0.5D);
        assertNotEquals(baseline, revision(weight));
        AgentDiscoveryEndpoint metadata =
            createEndpoint("http://example.com/a", "HTTP", true);
        metadata.setMetadata(Collections.singletonMap("zone", "z1"));
        assertNotEquals(baseline, revision(metadata));
        AgentDiscoveryEndpoint health =
            createEndpoint("http://example.com/a", "HTTP", false);
        assertNotEquals(baseline, revision(health));
        
        AgentDiscoveryEndpoint bindings =
            createEndpoint("http://example.com/a", "HTTP", true);
        bindings.setBindings(Collections.singletonList(binding("1.0.1", "[1.0.1]")));
        assertNotEquals(baseline, revision(bindings));
    }
    
    @Test
    void testProjectionIdentityIsValidatedButExcludedFromRevisionBytes() {
        AgentDiscoveryEndpoint endpoint =
            createEndpoint("http://example.com/rpc", "HTTP", true);
        byte[] first = RuntimeEndpointRevision.revisionBytes("public", "Agent A", "a2a",
            Collections.singletonList(endpoint));
        byte[] second = RuntimeEndpointRevision.revisionBytes("tenant", "Agent B", "custom",
            Collections.singletonList(endpoint));
        assertArrayEquals(first, second);
    }
    
    @Test
    void testRejectInvalidProjectionAndDuplicateNaturalKeys() {
        assertThrows(IllegalArgumentException.class,
            () -> RuntimeEndpointRevision.compute("public", "Agent", "a2a", null));
        assertThrows(IllegalArgumentException.class,
            () -> RuntimeEndpointRevision.compute("", "Agent", "a2a",
                Collections.<AgentDiscoveryEndpoint>emptyList()));
        assertThrows(IllegalArgumentException.class,
            () -> RuntimeEndpointRevision.compute("public", "", "a2a",
                Collections.<AgentDiscoveryEndpoint>emptyList()));
        assertThrows(IllegalArgumentException.class,
            () -> RuntimeEndpointRevision.compute("public", "Agent", "-a2a",
                Collections.<AgentDiscoveryEndpoint>emptyList()));
        assertThrows(IllegalArgumentException.class,
            () -> revision(null));
        
        AgentDiscoveryEndpoint missingHealth =
            createEndpoint("http://example.com/rpc", "HTTP", null);
        assertThrows(IllegalArgumentException.class,
            () -> revision(missingHealth));
        
        AgentDiscoveryEndpoint missingBindings =
            createEndpoint("http://missing.example.com/rpc", "HTTP", true);
        missingBindings.setBindings(null);
        assertThrows(IllegalArgumentException.class, () -> revision(missingBindings));
        
        AgentDiscoveryEndpoint incompleteBinding =
            createEndpoint("http://binding.example.com/rpc", "HTTP", true);
        incompleteBinding.setBindings(Collections.singletonList(new RuntimeVersionBinding()));
        assertThrows(IllegalArgumentException.class, () -> revision(incompleteBinding));
        
        AgentDiscoveryEndpoint first =
            createEndpoint("http://EXAMPLE.COM/a", "HTTP", true);
        AgentDiscoveryEndpoint duplicate =
            createEndpoint("http://example.com:80/b", "HTTP", false);
        assertThrows(IllegalArgumentException.class,
            () -> RuntimeEndpointRevision.compute("public", "Agent", "a2a",
                Arrays.asList(first, duplicate)));
    }
    
    @Test
    void testMaximumProjectionIsBoundedAndPractical() {
        List<AgentDiscoveryEndpoint> endpoints = new ArrayList<AgentDiscoveryEndpoint>();
        for (int i = 0; i < 1000; i++) {
            endpoints.add(createEndpoint("http://e" + i + ".example.com/rpc", "HTTP", true));
        }
        assertTimeout(Duration.ofSeconds(5), () -> {
            String revision = RuntimeEndpointRevision.compute("public", "Agent", "a2a", endpoints);
            assertTrue(revision.startsWith(RuntimeEndpointRevision.TOKEN_PREFIX));
        });
        
        endpoints.add(createEndpoint("http://overflow.example.com/rpc", "HTTP", true));
        assertThrows(IllegalArgumentException.class,
            () -> RuntimeEndpointRevision.compute("public", "Agent", "a2a", endpoints));
    }
    
    private String revision(AgentDiscoveryEndpoint endpoint) {
        return RuntimeEndpointRevision.compute("public", "Agent", "a2a",
            Collections.singletonList(endpoint));
    }
    
    private AgentDiscoveryEndpoint createEndpoint(String uri, String transport,
        Boolean healthy) {
        AgentDiscoveryEndpoint result = new AgentDiscoveryEndpoint();
        result.setUri(uri);
        result.setTransport(transport);
        result.setHealthy(healthy);
        result.setBindings(Collections.singletonList(binding("1.0.0", "[1.0.0]")));
        return result;
    }
    
    private RuntimeVersionBinding binding(String runtimeVersion, String versionRange) {
        RuntimeVersionBinding result = new RuntimeVersionBinding();
        result.setRuntimeVersion(runtimeVersion);
        result.setVersionRange(versionRange);
        return result;
    }
    
    private String toHex(byte[] value) {
        char[] alphabet = "0123456789abcdef".toCharArray();
        char[] result = new char[value.length * 2];
        for (int i = 0; i < value.length; i++) {
            int current = value[i] & 0xFF;
            result[i * 2] = alphabet[current >>> 4];
            result[i * 2 + 1] = alphabet[current & 0x0F];
        }
        return new String(result);
    }
}
