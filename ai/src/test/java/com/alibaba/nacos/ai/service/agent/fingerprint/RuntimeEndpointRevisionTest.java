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

import com.alibaba.nacos.api.ai.model.agent.Endpoint;
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
            + "54500000000a3fe000000000000000000000010000002168747470733a2f2f622e6578616d"
            + "706c653a3434332f6132613f623d3226613d31000000074a534f4e525043000000003ff000"
            + "00000000000000000200000002617a0000000162000000047a6f6e65000000027a3200";
    
    private static final String NATURAL_KEY_FRAME_HEX =
        "0000000300000018687474703a2f2f73616d652e6578616d706c653a38302f61000000044752"
            + "5043000000003ff0000000000000000000000100000018687474703a2f2f73616d652e657861"
            + "6d706c653a38302f62000000074a534f4e525043000000003ff0000000000000000000000000"
            + "00001a687474703a2f2f73616d652e6578616d706c653a313030302f7a000000044854545000"
            + "0000003ff00000000000000000000001";
    
    @Test
    void testEmptyProjectionGoldenVector() {
        byte[] frame = RuntimeEndpointRevision.revisionBytes("public", "Order Agent", "a2a",
            Collections.<Endpoint>emptyList());
        assertEquals("00000000", toHex(frame));
        assertEquals("murmur3-x64-128-v1:cfa0f7ddd84c76bc589623161cf526f1",
            RuntimeEndpointRevision.compute("public", "Order Agent", "a2a",
                Collections.<Endpoint>emptyList()));
        assertEquals("murmur3-x64-128-v1", RuntimeEndpointRevision.ALGORITHM_ID);
        assertEquals("murmur3-x64-128-v1:", RuntimeEndpointRevision.TOKEN_PREFIX);
    }
    
    @Test
    void testDualEndpointGoldenVectorAndNaturalKeyOrdering() {
        Endpoint b = createEndpoint("HTTPS://B.EXAMPLE/a2a?b=2&a=1", "JSONRPC", false);
        Map<String, String> metadata = new LinkedHashMap<String, String>();
        metadata.put("zone", "z2");
        metadata.put("az", "b");
        b.setMetadata(metadata);
        Endpoint a = createEndpoint("http://a.example/rpc", "HTTP", true);
        a.setPriority(10);
        a.setWeight(0.5D);
        List<Endpoint> reversedInput = Arrays.asList(b, a);
        
        byte[] frame = RuntimeEndpointRevision.revisionBytes("public", "Order Agent", "a2a",
            reversedInput);
        assertEquals(146, frame.length);
        assertEquals(DUAL_ENDPOINT_FRAME_HEX, toHex(frame));
        assertEquals("murmur3-x64-128-v1:6553cc8de6bd96d7077624a5b3a5178d",
            RuntimeEndpointRevision.compute("public", "Order Agent", "a2a", reversedInput));
        assertArrayEquals(frame,
            RuntimeEndpointRevision.revisionBytes("public", "Order Agent", "a2a",
                Arrays.asList(a, b)));
    }
    
    @Test
    void testNaturalKeyOrdersNumericPortThenTransport() {
        Endpoint port1000 = createEndpoint("http://same.example:1000/z", "HTTP", true);
        Endpoint port80Json = createEndpoint("http://same.example/b", "JSONRPC", false);
        Endpoint port80Grpc = createEndpoint("http://same.example/a", "GRPC", true);
        List<Endpoint> reversed = Arrays.asList(port1000, port80Json, port80Grpc);
        
        byte[] frame = RuntimeEndpointRevision.revisionBytes("public", "Agent", "a2a",
            reversed);
        assertEquals(168, frame.length);
        assertEquals(NATURAL_KEY_FRAME_HEX, toHex(frame));
        assertEquals("murmur3-x64-128-v1:910d3654e3044e56d7669c07465477ef",
            RuntimeEndpointRevision.compute("public", "Agent", "a2a", reversed));
        assertArrayEquals(frame,
            RuntimeEndpointRevision.revisionBytes("public", "Agent", "a2a",
                Arrays.asList(port80Grpc, port80Json, port1000)));
    }
    
    @Test
    void testEquivalentDefaultsAndMetadataOrderHaveSameRevision() {
        Endpoint omitted = createEndpoint("HTTP://EXAMPLE.COM/rpc", "HTTP", true);
        Endpoint explicit = createEndpoint("http://example.com:80/rpc", "HTTP", true);
        explicit.setPriority(0);
        explicit.setWeight(1D);
        explicit.setMetadata(Collections.<String, String>emptyMap());
        assertEquals(revision(omitted), revision(explicit));
        
        Endpoint firstOrder = createEndpoint("http://example.com/rpc", "HTTP", true);
        Map<String, String> firstMetadata = new LinkedHashMap<String, String>();
        firstMetadata.put("zone", "z1");
        firstMetadata.put("az", "a");
        firstOrder.setMetadata(firstMetadata);
        Endpoint secondOrder = createEndpoint("http://example.com:80/rpc", "HTTP", true);
        Map<String, String> secondMetadata = new LinkedHashMap<String, String>();
        secondMetadata.put("az", "a");
        secondMetadata.put("zone", "z1");
        secondOrder.setMetadata(secondMetadata);
        assertEquals(revision(firstOrder), revision(secondOrder));
    }
    
    @Test
    void testEquivalentSignedZeroWeightsHaveSameRevision() {
        Endpoint positiveZero = createEndpoint("http://example.com/rpc", "HTTP", true);
        positiveZero.setWeight(0D);
        Endpoint negativeZero = createEndpoint("http://example.com/rpc", "HTTP", true);
        negativeZero.setWeight(-0D);
        assertEquals(revision(positiveZero), revision(negativeZero));
    }
    
    @Test
    void testAdjacentWeightsHaveDifferentRevisions() {
        Endpoint baseline = createEndpoint("http://example.com/rpc", "HTTP", true);
        baseline.setWeight(1D);
        Endpoint adjacent = createEndpoint("http://example.com/rpc", "HTTP", true);
        adjacent.setWeight(Math.nextUp(1D));
        assertNotEquals(revision(baseline), revision(adjacent));
    }
    
    @Test
    void testEveryIncludedEndpointFieldChangesRevision() {
        String baseline = revision(createEndpoint("http://example.com/a", "HTTP", true));
        
        Endpoint uri = createEndpoint("http://example.com/b", "HTTP", true);
        assertNotEquals(baseline, revision(uri));
        Endpoint transport = createEndpoint("http://example.com/a", "JSONRPC", true);
        assertNotEquals(baseline, revision(transport));
        Endpoint priority = createEndpoint("http://example.com/a", "HTTP", true);
        priority.setPriority(1);
        assertNotEquals(baseline, revision(priority));
        Endpoint weight = createEndpoint("http://example.com/a", "HTTP", true);
        weight.setWeight(0.5D);
        assertNotEquals(baseline, revision(weight));
        Endpoint metadata = createEndpoint("http://example.com/a", "HTTP", true);
        metadata.setMetadata(Collections.singletonMap("zone", "z1"));
        assertNotEquals(baseline, revision(metadata));
        Endpoint health = createEndpoint("http://example.com/a", "HTTP", false);
        assertNotEquals(baseline, revision(health));
    }
    
    @Test
    void testProjectionIdentityIsValidatedButExcludedFromRevisionBytes() {
        Endpoint endpoint = createEndpoint("http://example.com/rpc", "HTTP", true);
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
                Collections.<Endpoint>emptyList()));
        assertThrows(IllegalArgumentException.class,
            () -> RuntimeEndpointRevision.compute("public", "", "a2a",
                Collections.<Endpoint>emptyList()));
        assertThrows(IllegalArgumentException.class,
            () -> RuntimeEndpointRevision.compute("public", "Agent", "-a2a",
                Collections.<Endpoint>emptyList()));
        assertThrows(IllegalArgumentException.class,
            () -> revision(null));
        
        Endpoint missingHealth = createEndpoint("http://example.com/rpc", "HTTP", null);
        assertThrows(IllegalArgumentException.class,
            () -> revision(missingHealth));
        
        Endpoint first = createEndpoint("http://EXAMPLE.COM/a", "HTTP", true);
        Endpoint duplicate = createEndpoint("http://example.com:80/b", "HTTP", false);
        assertThrows(IllegalArgumentException.class,
            () -> RuntimeEndpointRevision.compute("public", "Agent", "a2a",
                Arrays.asList(first, duplicate)));
    }
    
    @Test
    void testMaximumProjectionIsBoundedAndPractical() {
        List<Endpoint> endpoints = new ArrayList<Endpoint>();
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
    
    private String revision(Endpoint endpoint) {
        return RuntimeEndpointRevision.compute("public", "Agent", "a2a",
            Collections.singletonList(endpoint));
    }
    
    private Endpoint createEndpoint(String uri, String transport, Boolean healthy) {
        Endpoint result = new Endpoint();
        result.setUri(uri);
        result.setTransport(transport);
        result.setHealthy(healthy);
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
