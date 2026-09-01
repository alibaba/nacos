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

import com.alibaba.nacos.api.ai.model.agent.EndpointSource;
import com.alibaba.nacos.api.ai.model.agent.RuntimeVersionBinding;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryCallInterface;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryEndpoint;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryFilter;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryRequest;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryResult;
import com.alibaba.nacos.api.ai.model.rad.AgentReference;
import com.alibaba.nacos.api.ai.model.rad.EndpointSet;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentDiscoveryCanonicalizerTest {
    
    private static final String CONTENT_DIGEST = "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    
    private static final String RUNTIME_REVISION =
        "murmur3-x64-128-v1:0123456789abcdef0123456789abcdef";
    
    @Test
    void canonicalRequestDefaultsNamespaceAndPreservesSelectorSemantics() {
        AgentDiscoveryRequest omitted = request(null, null, null, null);
        AgentDiscoveryRequest exact = request("tenant", "1.2.3", null, null);
        AgentDiscoveryRequest labeled = request("tenant", null, "Latest", null);
        
        AgentDiscoveryRequest canonical =
            AgentDiscoveryCanonicalizer.canonicalizeRequest(omitted);
        
        assertEquals("public", canonical.getNamespaceId());
        assertEquals("AgentA", canonical.getReference().getAgentName());
        assertNull(canonical.getReference().getVersion());
        assertNull(canonical.getReference().getLabel());
        assertNull(canonical.getFilter());
        assertNotEquals(AgentDiscoveryCanonicalizer.canonicalRequestKey(omitted),
            AgentDiscoveryCanonicalizer.canonicalRequestKey(exact));
        assertNotEquals(AgentDiscoveryCanonicalizer.canonicalRequestKey(exact),
            AgentDiscoveryCanonicalizer.canonicalRequestKey(labeled));
        assertNotEquals(AgentDiscoveryCanonicalizer.canonicalRequestKey(omitted),
            AgentDiscoveryCanonicalizer.canonicalRequestKey(labeled));
    }
    
    @Test
    void nullAndEffectiveEmptyFiltersHaveOneIdentity() {
        AgentDiscoveryFilter empty = new AgentDiscoveryFilter();
        empty.setProtocols(Collections.<String>emptyList());
        empty.setTransports(Collections.<String>emptyList());
        empty.setEndpointSources(Collections.<EndpointSource>emptyList());
        empty.setMetadataSelector(Collections.<String, String>emptyMap());
        
        AgentDiscoveryRequest absent = request(null, null, null, null);
        AgentDiscoveryRequest materialized = request("public", null, null, empty);
        
        assertEquals(AgentDiscoveryCanonicalizer.canonicalRequestKey(absent),
            AgentDiscoveryCanonicalizer.canonicalRequestKey(materialized));
        assertNull(AgentDiscoveryCanonicalizer.canonicalizeRequest(materialized).getFilter());
    }
    
    @Test
    void setFiltersAreSortedDeduplicatedAndMetadataKeysAreCanonical() {
        AgentDiscoveryFilter first = filter(
            Arrays.asList("custom", "a2a", "custom"),
            Arrays.asList("websocket", "http", "http"),
            Arrays.asList(EndpointSource.RUNTIME, EndpointSource.DECLARED,
                EndpointSource.RUNTIME),
            metadata("zone", "east", "rack", "r1"));
        AgentDiscoveryFilter second = filter(Arrays.asList("a2a", "custom"),
            Arrays.asList("http", "websocket"),
            Arrays.asList(EndpointSource.DECLARED, EndpointSource.RUNTIME),
            metadata("rack", "r1", "zone", "east"));
        
        AgentDiscoveryRequest canonical = AgentDiscoveryCanonicalizer.canonicalizeRequest(
            request("public", null, null, first));
        
        assertEquals(Arrays.asList("a2a", "custom"), canonical.getFilter().getProtocols());
        assertEquals(Arrays.asList("http", "websocket"),
            canonical.getFilter().getTransports());
        assertEquals(Arrays.asList(EndpointSource.DECLARED, EndpointSource.RUNTIME),
            canonical.getFilter().getEndpointSources());
        assertEquals(Arrays.asList("rack", "zone"),
            new ArrayList<String>(canonical.getFilter().getMetadataSelector().keySet()));
        assertEquals(AgentDiscoveryCanonicalizer.canonicalRequestKey(
            request("public", null, null, first)),
            AgentDiscoveryCanonicalizer.canonicalRequestKey(
                request("public", null, null, second)));
    }
    
    @Test
    void meaningfulRequestDifferencesRemainDistinct() {
        AgentDiscoveryFilter baseFilter = filter(Collections.singletonList("a2a"),
            Collections.singletonList("http"),
            Collections.singletonList(EndpointSource.RUNTIME),
            Collections.singletonMap("zone", "east"));
        baseFilter.setProtocolVersion("1.0");
        String base = AgentDiscoveryCanonicalizer.canonicalRequestKey(
            request("public", null, "latest", baseFilter));
        
        AgentDiscoveryRequest agentCase = request("public", null, "latest", baseFilter);
        agentCase.getReference().setAgentName("Agenta");
        assertNotEquals(base, AgentDiscoveryCanonicalizer.canonicalRequestKey(agentCase));
        
        AgentDiscoveryFilter protocolCase = copyFilter(baseFilter);
        protocolCase.setProtocols(Collections.singletonList("A2A"));
        assertNotEquals(base, AgentDiscoveryCanonicalizer.canonicalRequestKey(
            request("public", null, "latest", protocolCase)));
        
        AgentDiscoveryFilter version = copyFilter(baseFilter);
        version.setProtocolVersion("1.1");
        assertNotEquals(base, AgentDiscoveryCanonicalizer.canonicalRequestKey(
            request("public", null, "latest", version)));
        
        AgentDiscoveryFilter metadataValue = copyFilter(baseFilter);
        metadataValue.setMetadataSelector(Collections.singletonMap("zone", "west"));
        assertNotEquals(base, AgentDiscoveryCanonicalizer.canonicalRequestKey(
            request("public", null, "latest", metadataValue)));
    }
    
    @Test
    void canonicalCopiesAreDefensiveAcrossNestedModels() {
        AgentDiscoveryFilter filter = filter(new ArrayList<String>(Arrays.asList("a2a", "custom")),
            new ArrayList<String>(Collections.singletonList("http")),
            new ArrayList<EndpointSource>(Collections.singletonList(EndpointSource.RUNTIME)),
            new HashMap<String, String>(Collections.singletonMap("zone", "east")));
        AgentDiscoveryRequest sourceRequest = request("public", null, "latest", filter);
        AgentDiscoveryRequest requestCopy =
            AgentDiscoveryCanonicalizer.canonicalizeRequest(sourceRequest);
        String requestKey = AgentDiscoveryCanonicalizer.canonicalRequestKey(requestCopy);
        
        AgentDiscoveryResult sourceResult = fullResult();
        AgentDiscoveryResult resultCopy =
            AgentDiscoveryCanonicalizer.canonicalizeResult(sourceResult);
        final String resultFingerprint = AgentDiscoveryCanonicalizer.fingerprint(resultCopy);
        final AgentDiscoveryEndpoint sourceEndpoint = runtimeEndpoint(sourceResult);
        
        sourceRequest.getReference().setAgentName("ChangedAgent");
        filter.getProtocols().clear();
        filter.getMetadataSelector().put("zone", "west");
        Map<String, Object> descriptor = descriptor(sourceResult);
        descriptor.put("name", "changed");
        runtimeEndpoint(sourceResult).getMetadata().put("zone", "west");
        runtimeEndpoint(sourceResult).getBindings().get(0).setRuntimeVersion("1.1.0");
        sourceResult.setCallInterfaces(Collections.<AgentDiscoveryCallInterface>emptyList());
        
        assertEquals("AgentA", requestCopy.getReference().getAgentName());
        assertEquals(Arrays.asList("a2a", "custom"), requestCopy.getFilter().getProtocols());
        assertEquals("east", requestCopy.getFilter().getMetadataSelector().get("zone"));
        assertEquals(requestKey, AgentDiscoveryCanonicalizer.canonicalRequestKey(requestCopy));
        assertEquals("Agent A", descriptor(resultCopy).get("name"));
        assertEquals("east", runtimeEndpoint(resultCopy).getMetadata().get("zone"));
        assertEquals("1.0.0",
            runtimeEndpoint(resultCopy).getBindings().get(0).getRuntimeVersion());
        assertEquals(resultFingerprint, AgentDiscoveryCanonicalizer.fingerprint(resultCopy));
        assertNotSame(sourceEndpoint, runtimeEndpoint(resultCopy));
    }
    
    @Test
    void fingerprintCoversEveryCompletePublicSnapshotLayer() {
        String base = AgentDiscoveryCanonicalizer.fingerprint(fullResult());
        
        AgentDiscoveryResult namespace = fullResult();
        namespace.setNamespaceId("tenant");
        assertDifferent(base, namespace);
        AgentDiscoveryResult agent = fullResult();
        agent.setAgentName("AgentB");
        assertDifferent(base, agent);
        AgentDiscoveryResult version = fullResult();
        version.setVersion("1.1.0");
        assertDifferent(base, version);
        AgentDiscoveryResult digest = fullResult();
        digest.setContentDigest("sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
            + "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
        assertDifferent(base, digest);
        
        AgentDiscoveryResult protocol = fullResult();
        protocol.getCallInterfaces().get(0).setProtocol("custom");
        assertDifferent(base, protocol);
        AgentDiscoveryResult protocolVersion = fullResult();
        protocolVersion.getCallInterfaces().get(0).setProtocolVersion("1.1");
        assertDifferent(base, protocolVersion);
        AgentDiscoveryResult mediaType = fullResult();
        mediaType.getCallInterfaces().get(0).setDescriptorMediaType("application/yaml");
        assertDifferent(base, mediaType);
        AgentDiscoveryResult descriptor = fullResult();
        descriptor(descriptor).put("name", "Agent B");
        assertDifferent(base, descriptor);
        
        AgentDiscoveryResult sourceRevision = fullResult();
        sourceRevision.getCallInterfaces().get(0).getEndpointSets().get(0)
            .setSourceRevision("murmur3-x64-128-v1:fedcba9876543210fedcba9876543210");
        assertDifferent(base, sourceRevision);
        AgentDiscoveryResult uri = fullResult();
        runtimeEndpoint(uri).setUri("https://example.com:443/other");
        assertDifferent(base, uri);
        AgentDiscoveryResult transport = fullResult();
        runtimeEndpoint(transport).setTransport("websocket");
        assertDifferent(base, transport);
        AgentDiscoveryResult priority = fullResult();
        runtimeEndpoint(priority).setPriority(1);
        assertDifferent(base, priority);
        AgentDiscoveryResult weight = fullResult();
        runtimeEndpoint(weight).setWeight(2D);
        assertDifferent(base, weight);
        AgentDiscoveryResult metadata = fullResult();
        runtimeEndpoint(metadata).setMetadata(Collections.singletonMap("zone", "west"));
        assertDifferent(base, metadata);
        AgentDiscoveryResult health = fullResult();
        runtimeEndpoint(health).setHealthy(false);
        assertDifferent(base, health);
        AgentDiscoveryResult runtimeVersion = fullResult();
        runtimeEndpoint(runtimeVersion).getBindings().get(0).setRuntimeVersion("1.1.0");
        assertDifferent(base, runtimeVersion);
        AgentDiscoveryResult range = fullResult();
        runtimeEndpoint(range).getBindings().get(0).setVersionRange("[1.0.0,3.0.0)");
        assertDifferent(base, range);
    }
    
    @Test
    void mapOrderIsIgnoredButContractArrayOrderChangesFingerprint() {
        AgentDiscoveryResult first = twoInterfaceResult();
        AgentDiscoveryResult mapReordered = twoInterfaceResult();
        descriptor(mapReordered).clear();
        descriptor(mapReordered).put("nested", metadata("z", "last", "a", "first"));
        descriptor(mapReordered).put("skills", Arrays.asList("search", "verify"));
        descriptor(mapReordered).put("name", "Agent A");
        runtimeEndpoint(mapReordered).setMetadata(metadata("rack", "r1", "zone", "east"));
        
        assertEquals(AgentDiscoveryCanonicalizer.fingerprint(first),
            AgentDiscoveryCanonicalizer.fingerprint(mapReordered));
        
        AgentDiscoveryResult interfaceReordered = twoInterfaceResult();
        Collections.reverse(interfaceReordered.getCallInterfaces());
        assertNotEquals(AgentDiscoveryCanonicalizer.fingerprint(first),
            AgentDiscoveryCanonicalizer.fingerprint(interfaceReordered));
        
        AgentDiscoveryResult sourceReordered = sourceOrderedResult();
        AgentDiscoveryResult reverseSources = sourceOrderedResult();
        Collections.reverse(reverseSources.getCallInterfaces().get(0).getEndpointSets());
        assertNotEquals(AgentDiscoveryCanonicalizer.fingerprint(sourceReordered),
            AgentDiscoveryCanonicalizer.fingerprint(reverseSources));
    }
    
    @Test
    void defaultsNegativeZeroUnicodeUriAndIpv6HaveDeterministicFrames() {
        AgentDiscoveryResult implicit = fullResult();
        AgentDiscoveryEndpoint endpoint = runtimeEndpoint(implicit);
        endpoint.setUri("HTTPS://[2001:0DB8:0:0:0:0:0:1]/路径");
        endpoint.setPriority(null);
        endpoint.setWeight(-0.0D);
        endpoint.setMetadata(Collections.<String, String>emptyMap());
        descriptor(implicit).put("text", "你好\nrad");
        descriptor(implicit).put("negativeZero", -0.0D);
        
        AgentDiscoveryResult explicit = fullResult();
        AgentDiscoveryEndpoint explicitEndpoint = runtimeEndpoint(explicit);
        explicitEndpoint.setUri("https://[2001:db8::1]:443/路径");
        explicitEndpoint.setPriority(0);
        explicitEndpoint.setWeight(0D);
        explicitEndpoint.setMetadata(null);
        descriptor(explicit).put("text", "你好\nrad");
        descriptor(explicit).put("negativeZero", 0D);
        
        AgentDiscoveryResult canonical =
            AgentDiscoveryCanonicalizer.canonicalizeResult(implicit);
        String frame = AgentDiscoveryCanonicalizer.canonicalResultJson(implicit);
        
        assertEquals("https://[2001:db8::1]:443/路径",
            runtimeEndpoint(canonical).getUri());
        assertEquals(Integer.valueOf(0), runtimeEndpoint(canonical).getPriority());
        assertEquals(Double.valueOf(0D), runtimeEndpoint(canonical).getWeight());
        assertNull(runtimeEndpoint(canonical).getMetadata());
        assertTrue(frame.contains("你好\\nrad"));
        assertTrue(frame.contains("\"negativeZero\":0"));
        assertEquals(AgentDiscoveryCanonicalizer.fingerprint(implicit),
            AgentDiscoveryCanonicalizer.fingerprint(explicit));
        
        AgentDiscoveryResult implicitNamespace = fullResult();
        implicitNamespace.setNamespaceId(null);
        assertEquals(AgentDiscoveryCanonicalizer.fingerprint(fullResult()),
            AgentDiscoveryCanonicalizer.fingerprint(implicitNamespace));
    }
    
    @Test
    void algorithmIdAndKnownCanonicalFixtureRemainStable() {
        AgentDiscoveryResult result = new AgentDiscoveryResult();
        result.setNamespaceId("public");
        result.setAgentName("AgentA");
        result.setVersion("1.0.0");
        result.setContentDigest(CONTENT_DIGEST);
        result.setCallInterfaces(Collections.<AgentDiscoveryCallInterface>emptyList());
        
        String frame = AgentDiscoveryCanonicalizer.canonicalResultJson(result);
        
        assertEquals("sha256-canonical-json-v1", AgentDiscoveryCanonicalizer.ALGORITHM_ID);
        assertEquals("{\"agentName\":\"AgentA\",\"callInterfaces\":[],"
            + "\"contentDigest\":\"" + CONTENT_DIGEST + "\","
            + "\"namespaceId\":\"public\",\"version\":\"1.0.0\"}", frame);
        assertEquals("sha256-canonical-json-v1:"
            + "3362e4414540623a15d86c0c0b99a0b76f82a203c3f26eb905b9db43444436d5",
            AgentDiscoveryCanonicalizer.fingerprint(result));
    }
    
    @Test
    void invalidAndOversizedInputsFailWithoutPartialCanonicalState() {
        assertThrows(IllegalArgumentException.class,
            () -> AgentDiscoveryCanonicalizer.canonicalizeRequest(null));
        assertThrows(IllegalArgumentException.class,
            () -> AgentDiscoveryCanonicalizer.canonicalizeResult(null));
        assertThrows(IllegalArgumentException.class,
            () -> AgentDiscoveryCanonicalizer.fingerprint(null));
        
        List<String> protocols = new ArrayList<String>();
        for (int i = 0; i < 17; i++) {
            protocols.add("protocol" + i);
        }
        AgentDiscoveryFilter oversized = filter(protocols, null, null, null);
        assertThrows(IllegalArgumentException.class,
            () -> AgentDiscoveryCanonicalizer.canonicalizeRequest(
                request("public", null, null, oversized)));
        
        AgentDiscoveryFilter nullProtocol = filter(
            Arrays.asList("a2a", null), null, null, null);
        assertThrows(IllegalArgumentException.class,
            () -> AgentDiscoveryCanonicalizer.canonicalizeRequest(
                request("public", null, null, nullProtocol)));
        AgentDiscoveryFilter nullSource = filter(null, null,
            Arrays.asList(EndpointSource.RUNTIME, null), null);
        assertThrows(IllegalArgumentException.class,
            () -> AgentDiscoveryCanonicalizer.canonicalizeRequest(
                request("public", null, null, nullSource)));
        Map<String, String> invalidMetadata = new HashMap<String, String>();
        invalidMetadata.put(null, "value");
        AgentDiscoveryFilter nullMetadataKey = filter(null, null, null, invalidMetadata);
        assertThrows(IllegalArgumentException.class,
            () -> AgentDiscoveryCanonicalizer.canonicalizeRequest(
                request("public", null, null, nullMetadataKey)));
        
        AgentDiscoveryResult oversizedSnapshot = baseResult();
        List<AgentDiscoveryCallInterface> interfaces =
            new ArrayList<AgentDiscoveryCallInterface>();
        for (int i = 0; i < 17; i++) {
            interfaces.add(interfaceWithProtocol("protocol" + i));
        }
        oversizedSnapshot.setCallInterfaces(interfaces);
        assertThrows(IllegalArgumentException.class,
            () -> AgentDiscoveryCanonicalizer.fingerprint(oversizedSnapshot));
        
        AgentDiscoveryResult duplicateEndpoints = fullResult();
        EndpointSet endpointSet = duplicateEndpoints.getCallInterfaces().get(0)
            .getEndpointSets().get(0);
        endpointSet.setEndpoints(Arrays.asList(runtimeEndpoint(duplicateEndpoints),
            runtimeEndpoint(duplicateEndpoints)));
        assertThrows(IllegalArgumentException.class,
            () -> AgentDiscoveryCanonicalizer.fingerprint(duplicateEndpoints));
        
        AgentDiscoveryResult missingHealth = fullResult();
        runtimeEndpoint(missingHealth).setHealthy(null);
        assertThrows(IllegalArgumentException.class,
            () -> AgentDiscoveryCanonicalizer.fingerprint(missingHealth));
        AgentDiscoveryResult missingBindings = fullResult();
        runtimeEndpoint(missingBindings).setBindings(null);
        assertThrows(IllegalArgumentException.class,
            () -> AgentDiscoveryCanonicalizer.fingerprint(missingBindings));
        
        AgentDiscoveryResult nonJson = fullResult();
        nonJson.getCallInterfaces().get(0).setNativeDescriptor(new Object());
        assertThrows(IllegalArgumentException.class,
            () -> AgentDiscoveryCanonicalizer.fingerprint(nonJson));
        AgentDiscoveryResult nonFinite = fullResult();
        descriptor(nonFinite).put("number", Double.NaN);
        assertThrows(IllegalArgumentException.class,
            () -> AgentDiscoveryCanonicalizer.fingerprint(nonFinite));
        AgentDiscoveryResult cyclic = fullResult();
        Map<String, Object> cycle = new HashMap<String, Object>();
        cycle.put("self", cycle);
        cyclic.getCallInterfaces().get(0).setNativeDescriptor(cycle);
        assertThrows(IllegalArgumentException.class,
            () -> AgentDiscoveryCanonicalizer.fingerprint(cyclic));
        
        AgentDiscoveryResult floatInfinity = fullResult();
        descriptor(floatInfinity).put("number", Float.POSITIVE_INFINITY);
        assertThrows(IllegalArgumentException.class,
            () -> AgentDiscoveryCanonicalizer.fingerprint(floatInfinity));
        
        AgentDiscoveryResult nonStringKey = fullResult();
        Map<Object, Object> invalidMap = new HashMap<Object, Object>();
        invalidMap.put(Integer.valueOf(1), "value");
        nonStringKey.getCallInterfaces().get(0).setNativeDescriptor(invalidMap);
        assertThrows(IllegalArgumentException.class,
            () -> AgentDiscoveryCanonicalizer.fingerprint(nonStringKey));
    }
    
    @Test
    void malformedNestedSnapshotShapesFailThroughValidation() {
        AgentDiscoveryRequest missingReference = request("public", null, null, null);
        missingReference.setReference(null);
        assertThrows(IllegalArgumentException.class,
            () -> AgentDiscoveryCanonicalizer.canonicalizeRequest(missingReference));
        
        AgentDiscoveryResult missingInterfaces = baseResult();
        assertThrows(IllegalArgumentException.class,
            () -> AgentDiscoveryCanonicalizer.fingerprint(missingInterfaces));
        AgentDiscoveryResult nullInterface = baseResult();
        nullInterface.setCallInterfaces(Collections.singletonList(null));
        assertThrows(IllegalArgumentException.class,
            () -> AgentDiscoveryCanonicalizer.fingerprint(nullInterface));
        
        AgentDiscoveryResult missingSets = fullResult();
        missingSets.getCallInterfaces().get(0).setEndpointSets(null);
        assertThrows(IllegalArgumentException.class,
            () -> AgentDiscoveryCanonicalizer.fingerprint(missingSets));
        AgentDiscoveryResult nullSet = fullResult();
        nullSet.getCallInterfaces().get(0).setEndpointSets(Collections.singletonList(null));
        assertThrows(IllegalArgumentException.class,
            () -> AgentDiscoveryCanonicalizer.fingerprint(nullSet));
        
        AgentDiscoveryResult missingEndpoints = fullResult();
        missingEndpoints.getCallInterfaces().get(0).getEndpointSets().get(0).setEndpoints(null);
        assertThrows(IllegalArgumentException.class,
            () -> AgentDiscoveryCanonicalizer.fingerprint(missingEndpoints));
        AgentDiscoveryResult nullEndpoint = fullResult();
        nullEndpoint.getCallInterfaces().get(0).getEndpointSets().get(0)
            .setEndpoints(Collections.singletonList(null));
        assertThrows(IllegalArgumentException.class,
            () -> AgentDiscoveryCanonicalizer.fingerprint(nullEndpoint));
        
        AgentDiscoveryResult nullBinding = fullResult();
        runtimeEndpoint(nullBinding).setBindings(Collections.singletonList(null));
        assertThrows(IllegalArgumentException.class,
            () -> AgentDiscoveryCanonicalizer.fingerprint(nullBinding));
    }
    
    @Test
    void jsonValueCanonicalizationCoversArraysNumbersAndEscapes() {
        AgentDiscoveryResult iterable = fullResult();
        descriptor(iterable).put("values", Arrays.asList("value", Boolean.TRUE, null, 1.5F));
        AgentDiscoveryResult array = fullResult();
        descriptor(array).put("values", new Object[] {"value", Boolean.TRUE, null, 1.5D});
        assertEquals(AgentDiscoveryCanonicalizer.fingerprint(iterable),
            AgentDiscoveryCanonicalizer.fingerprint(array));
        
        AgentDiscoveryResult primitiveArray = fullResult();
        descriptor(primitiveArray).put("values", new int[] {1, 2});
        AgentDiscoveryResult boxedList = fullResult();
        descriptor(boxedList).put("values", Arrays.asList(1L, 2L));
        assertEquals(AgentDiscoveryCanonicalizer.fingerprint(primitiveArray),
            AgentDiscoveryCanonicalizer.fingerprint(boxedList));
        
        String escaped = "\"\\\b\f\n\r\t" + (char) 1;
        assertEquals("{\"value\":\"\\\"\\\\\\b\\f\\n\\r\\t\\u0001\"}",
            AgentDiscoveryCanonicalizer.toCanonicalJson(
                Collections.<String, Object>singletonMap("value", escaped)));
        
        String supplementary = new String(Character.toChars(0x1F680));
        assertEquals("{\"value\":\"" + supplementary + "\"}",
            AgentDiscoveryCanonicalizer.toCanonicalJson(
                Collections.<String, Object>singletonMap("value", supplementary)));
        String loneHighSurrogate = String.valueOf((char) 0xD83D);
        String escapedHighSurrogate = "{\"value\":\"" + '\\' + "ud83d\"}";
        assertEquals(escapedHighSurrogate,
            AgentDiscoveryCanonicalizer.toCanonicalJson(
                Collections.<String, Object>singletonMap("value", loneHighSurrogate)));
        assertNotEquals(
            AgentDiscoveryCanonicalizer.toCanonicalJson(
                Collections.<String, Object>singletonMap("value", loneHighSurrogate)),
            AgentDiscoveryCanonicalizer.toCanonicalJson(
                Collections.<String, Object>singletonMap("value", "?")));
        String loneLowSurrogate = String.valueOf((char) 0xDE80);
        String escapedLowSurrogate = "{\"value\":\"" + '\\' + "ude80\"}";
        assertEquals(escapedLowSurrogate,
            AgentDiscoveryCanonicalizer.toCanonicalJson(
                Collections.<String, Object>singletonMap("value", loneLowSurrogate)));
        
        assertThrows(IllegalArgumentException.class,
            () -> AgentDiscoveryCanonicalizer.toCanonicalJson(new Object()));
        Map<Object, Object> invalidMap = new HashMap<Object, Object>();
        invalidMap.put(Integer.valueOf(1), "value");
        assertThrows(IllegalArgumentException.class,
            () -> AgentDiscoveryCanonicalizer.toCanonicalJson(invalidMap));
        
        Number invalidNumber = new Number() {
            
            private static final long serialVersionUID = 1L;
            
            @Override
            public int intValue() {
                return 0;
            }
            
            @Override
            public long longValue() {
                return 0L;
            }
            
            @Override
            public float floatValue() {
                return 0F;
            }
            
            @Override
            public double doubleValue() {
                return 0D;
            }
            
            @Override
            public String toString() {
                return "not-a-number";
            }
        };
        assertThrows(IllegalArgumentException.class,
            () -> AgentDiscoveryCanonicalizer.toCanonicalJson(invalidNumber));
        assertThrows(IllegalStateException.class,
            () -> AgentDiscoveryCanonicalizer.digestHex("missing-algorithm", "value"));
    }
    
    private void assertDifferent(String base, AgentDiscoveryResult changed) {
        assertNotEquals(base, AgentDiscoveryCanonicalizer.fingerprint(changed));
    }
    
    private AgentDiscoveryRequest request(String namespaceId, String version, String label,
        AgentDiscoveryFilter filter) {
        AgentReference reference = new AgentReference();
        reference.setAgentName("AgentA");
        reference.setVersion(version);
        reference.setLabel(label);
        AgentDiscoveryRequest result = new AgentDiscoveryRequest();
        result.setNamespaceId(namespaceId);
        result.setReference(reference);
        result.setFilter(filter);
        return result;
    }
    
    private AgentDiscoveryFilter filter(List<String> protocols, List<String> transports,
        List<EndpointSource> sources, Map<String, String> metadata) {
        AgentDiscoveryFilter result = new AgentDiscoveryFilter();
        result.setProtocols(protocols);
        result.setTransports(transports);
        result.setEndpointSources(sources);
        result.setMetadataSelector(metadata);
        return result;
    }
    
    private AgentDiscoveryFilter copyFilter(AgentDiscoveryFilter source) {
        AgentDiscoveryFilter result = filter(source.getProtocols(), source.getTransports(),
            source.getEndpointSources(), source.getMetadataSelector());
        result.setProtocolVersion(source.getProtocolVersion());
        return result;
    }
    
    private Map<String, String> metadata(String firstKey, String firstValue, String secondKey,
        String secondValue) {
        Map<String, String> result = new LinkedHashMap<String, String>();
        result.put(firstKey, firstValue);
        result.put(secondKey, secondValue);
        return result;
    }
    
    private AgentDiscoveryResult fullResult() {
        AgentDiscoveryEndpoint endpoint = new AgentDiscoveryEndpoint();
        endpoint.setUri("https://example.com:443/agent");
        endpoint.setTransport("http");
        endpoint.setPriority(0);
        endpoint.setWeight(1D);
        endpoint.setMetadata(metadata("rack", "r1", "zone", "east"));
        endpoint.setHealthy(true);
        RuntimeVersionBinding binding = new RuntimeVersionBinding();
        binding.setRuntimeVersion("1.0.0");
        binding.setVersionRange("[1.0.0,2.0.0)");
        endpoint.setBindings(Collections.singletonList(binding));
        
        EndpointSet endpointSet = new EndpointSet();
        endpointSet.setSource(EndpointSource.RUNTIME);
        endpointSet.setSourceRevision(RUNTIME_REVISION);
        endpointSet.setEndpoints(Collections.singletonList(endpoint));
        
        AgentDiscoveryCallInterface callInterface = interfaceWithProtocol("a2a");
        callInterface.setEndpointSets(Collections.singletonList(endpointSet));
        Map<String, Object> nativeDescriptor = new LinkedHashMap<String, Object>();
        nativeDescriptor.put("name", "Agent A");
        nativeDescriptor.put("nested", metadata("a", "first", "z", "last"));
        nativeDescriptor.put("skills", Arrays.asList("search", "verify"));
        callInterface.setNativeDescriptor(nativeDescriptor);
        
        AgentDiscoveryResult result = baseResult();
        result.setCallInterfaces(Collections.singletonList(callInterface));
        return result;
    }
    
    private AgentDiscoveryResult twoInterfaceResult() {
        AgentDiscoveryResult result = fullResult();
        AgentDiscoveryCallInterface custom = interfaceWithProtocol("custom");
        custom.setNativeDescriptor(Collections.<String, Object>singletonMap("name", "Custom"));
        result.setCallInterfaces(new ArrayList<AgentDiscoveryCallInterface>(
            Arrays.asList(result.getCallInterfaces().get(0), custom)));
        return result;
    }
    
    private AgentDiscoveryResult sourceOrderedResult() {
        AgentDiscoveryResult result = fullResult();
        EndpointSet declared = new EndpointSet();
        declared.setSource(EndpointSource.DECLARED);
        declared.setSourceRevision(CONTENT_DIGEST);
        AgentDiscoveryEndpoint endpoint = new AgentDiscoveryEndpoint();
        endpoint.setUri("https://declared.example.com:443/agent");
        endpoint.setTransport("http");
        endpoint.setPriority(0);
        endpoint.setWeight(1D);
        declared.setEndpoints(Collections.singletonList(endpoint));
        result.getCallInterfaces().get(0).setEndpointSets(
            new ArrayList<EndpointSet>(Arrays.asList(
                result.getCallInterfaces().get(0).getEndpointSets().get(0), declared)));
        return result;
    }
    
    private AgentDiscoveryCallInterface interfaceWithProtocol(String protocol) {
        AgentDiscoveryCallInterface result = new AgentDiscoveryCallInterface();
        result.setProtocol(protocol);
        result.setProtocolVersion("1.0");
        result.setDescriptorMediaType("application/json");
        result.setNativeDescriptor(Collections.<String, Object>singletonMap("name", protocol));
        result.setEndpointSets(Collections.<EndpointSet>emptyList());
        return result;
    }
    
    private AgentDiscoveryResult baseResult() {
        AgentDiscoveryResult result = new AgentDiscoveryResult();
        result.setNamespaceId("public");
        result.setAgentName("AgentA");
        result.setVersion("1.0.0");
        result.setContentDigest(CONTENT_DIGEST);
        return result;
    }
    
    @SuppressWarnings("unchecked")
    private Map<String, Object> descriptor(AgentDiscoveryResult result) {
        Object descriptor = result.getCallInterfaces().get(0).getNativeDescriptor();
        assertNotNull(descriptor);
        assertTrue(descriptor instanceof Map);
        return (Map<String, Object>) descriptor;
    }
    
    private AgentDiscoveryEndpoint runtimeEndpoint(AgentDiscoveryResult result) {
        return result.getCallInterfaces().get(0).getEndpointSets().get(0).getEndpoints().get(0);
    }
}
