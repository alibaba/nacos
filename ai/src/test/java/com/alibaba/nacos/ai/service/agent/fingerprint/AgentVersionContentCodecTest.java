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

import com.alibaba.nacos.ai.model.agent.AgentVersionContent;
import com.alibaba.nacos.api.ai.model.agent.AgentCallInterface;
import com.alibaba.nacos.api.ai.model.agent.Endpoint;
import com.alibaba.nacos.api.ai.model.agent.EndpointSource;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentVersionContentCodecTest {
    
    @Test
    void testEncodePersistedContentAndDecodeRoundTrip() {
        AgentVersionContentCodec.EncodedContent encoded =
            AgentVersionContentCodec.encode(createGoldenContent());
        
        String storedJson = new String(encoded.getBytes(), StandardCharsets.UTF_8);
        assertTrue(storedJson.startsWith("{\"kind\":\"AgentVersionContent\","));
        assertTrue(storedJson.contains("\"uri\":\"https://example.com:443/a2a?b=2&a=1\""));
        assertTrue(storedJson.contains("\"priority\":0"));
        assertTrue(storedJson.contains("\"weight\":1.0"));
        assertFalse(storedJson.contains("HTTPS://Example.COM"));
        assertEquals(encoded.getBytes().length, encoded.getSize());
        assertEquals("sha256:" + DigestUtils.sha256Hex(encoded.getBytes()),
            encoded.getContentDigest());
        assertEquals(encoded.getContentDigest(),
            AgentVersionContentCodec.digest(encoded.getBytes()));
        
        AgentVersionContent decoded = AgentVersionContentCodec.decode(encoded.getBytes());
        assertEquals(AgentVersionContent.KIND, decoded.getKind());
        assertEquals(AgentVersionContent.SCHEMA_VERSION, decoded.getSchemaVersion());
        AgentCallInterface callInterface = decoded.getCallInterfaces().get(0);
        assertEquals(Arrays.asList(EndpointSource.RUNTIME, EndpointSource.DECLARED),
            callInterface.getEndpointSourceOrder());
        Endpoint endpoint = callInterface.getDeclaredEndpoints().get(0);
        assertEquals("https://example.com:443/a2a?b=2&a=1", endpoint.getUri());
        assertEquals(0, endpoint.getPriority());
        assertEquals(1D, endpoint.getWeight());
        assertEquals(Arrays.asList("az", "zone"),
            new ArrayList<String>(endpoint.getMetadata().keySet()));
    }
    
    @Test
    void testEndpointDefaultsAndMetadataOrderDoNotChangeEncoding() {
        AgentVersionContent normalized = createGoldenContent();
        AgentCallInterface normalizedInterface = normalized.getCallInterfaces().get(0);
        Endpoint endpoint = normalizedInterface.getDeclaredEndpoints().get(0);
        endpoint.setUri("https://example.com:443/a2a?b=2&a=1");
        endpoint.setPriority(0);
        endpoint.setWeight(1D);
        Map<String, String> metadata = new LinkedHashMap<String, String>();
        metadata.put("az", "h");
        metadata.put("zone", "cn-hangzhou-h");
        endpoint.setMetadata(metadata);
        
        AgentVersionContent original = createGoldenContent();
        AgentVersionContentCodec.EncodedContent first = AgentVersionContentCodec.encode(original);
        AgentVersionContentCodec.EncodedContent second =
            AgentVersionContentCodec.encode(normalized);
        assertArrayEquals(first.getBytes(), second.getBytes());
        assertEquals(first.getContentDigest(), second.getContentDigest());
    }
    
    @Test
    void testDescriptorObjectOrderChangesPersistedBytes() {
        AgentVersionContent first = createGoldenContent();
        AgentVersionContent second = createGoldenContent();
        Map<String, Object> reordered = new LinkedHashMap<String, Object>();
        Map<String, Object> capabilities = new LinkedHashMap<String, Object>();
        capabilities.put("push", false);
        capabilities.put("streaming", true);
        reordered.put("capabilities", capabilities);
        reordered.put("name", "Order Agent");
        reordered.put("title", "订单 Agent");
        reordered.put("version", "1.0.6");
        second.getCallInterfaces().get(0).setNativeDescriptor(reordered);
        
        AgentVersionContentCodec.EncodedContent firstEncoded =
            AgentVersionContentCodec.encode(first);
        AgentVersionContentCodec.EncodedContent secondEncoded =
            AgentVersionContentCodec.encode(second);
        assertFalse(Arrays.equals(firstEncoded.getBytes(), secondEncoded.getBytes()));
        assertNotEquals(firstEncoded.getContentDigest(), secondEncoded.getContentDigest());
    }
    
    @Test
    void testBusinessArrayOrderChangesDigest() {
        AgentCallInterface a2a = createCallInterface("a2a", "http://a.example/rpc");
        AgentCallInterface grpc = createCallInterface("grpc", "http://g.example/rpc");
        AgentVersionContent first = new AgentVersionContent(Arrays.asList(a2a, grpc));
        AgentVersionContent second = new AgentVersionContent(Arrays.asList(grpc, a2a));
        assertNotEquals(AgentVersionContentCodec.encode(first).getContentDigest(),
            AgentVersionContentCodec.encode(second).getContentDigest());
        
        AgentCallInterface firstEndpoints = createCallInterface("a2a", "http://a.example/rpc");
        firstEndpoints.getDeclaredEndpoints().add(createEndpoint("http://b.example/rpc"));
        AgentCallInterface secondEndpoints = createCallInterface("a2a", "http://b.example/rpc");
        secondEndpoints.getDeclaredEndpoints().add(createEndpoint("http://a.example/rpc"));
        assertNotEquals(AgentVersionContentCodec
            .encode(new AgentVersionContent(Collections.singletonList(firstEndpoints)))
            .getContentDigest(),
            AgentVersionContentCodec
                .encode(new AgentVersionContent(Collections.singletonList(secondEndpoints)))
                .getContentDigest());
        
        AgentCallInterface runtimeFirst = createCallInterface("a2a", null);
        runtimeFirst.setEndpointSourceOrder(
            Arrays.asList(EndpointSource.RUNTIME, EndpointSource.DECLARED));
        AgentCallInterface declaredFirst = createCallInterface("a2a", null);
        declaredFirst.setEndpointSourceOrder(
            Arrays.asList(EndpointSource.DECLARED, EndpointSource.RUNTIME));
        assertNotEquals(AgentVersionContentCodec
            .encode(new AgentVersionContent(Collections.singletonList(runtimeFirst)))
            .getContentDigest(),
            AgentVersionContentCodec
                .encode(new AgentVersionContent(Collections.singletonList(declaredFirst)))
                .getContentDigest());
    }
    
    @Test
    void testAbsentAndEmptyDeclaredEndpointsHaveSameEncoding() {
        AgentCallInterface absent = createCallInterface("a2a", null);
        AgentCallInterface empty = createCallInterface("a2a", null);
        empty.setDeclaredEndpoints(Collections.<Endpoint>emptyList());
        
        AgentVersionContentCodec.EncodedContent first = AgentVersionContentCodec
            .encode(new AgentVersionContent(Collections.singletonList(absent)));
        AgentVersionContentCodec.EncodedContent second = AgentVersionContentCodec
            .encode(new AgentVersionContent(Collections.singletonList(empty)));
        assertArrayEquals(first.getBytes(), second.getBytes());
        assertEquals(first.getContentDigest(), second.getContentDigest());
    }
    
    @Test
    void testAbsentAndEmptyEndpointMetadataHaveSameEncoding() {
        AgentCallInterface absent = createCallInterface("a2a", "http://example.com/rpc");
        AgentCallInterface empty = createCallInterface("a2a", "http://example.com/rpc");
        empty.getDeclaredEndpoints().get(0).setMetadata(Collections.<String, String>emptyMap());
        
        AgentVersionContentCodec.EncodedContent first = AgentVersionContentCodec
            .encode(new AgentVersionContent(Collections.singletonList(absent)));
        AgentVersionContentCodec.EncodedContent second = AgentVersionContentCodec
            .encode(new AgentVersionContent(Collections.singletonList(empty)));
        assertArrayEquals(first.getBytes(), second.getBytes());
        assertEquals(first.getContentDigest(), second.getContentDigest());
    }
    
    @Test
    void testEncodedBytesAreDefensiveAndSizeUsesUtf8Bytes() {
        AgentVersionContentCodec.EncodedContent encoded =
            AgentVersionContentCodec.encode(createGoldenContent());
        byte[] first = encoded.getBytes();
        byte originalFirstByte = first[0];
        first[0] = 0;
        
        assertEquals(originalFirstByte, encoded.getBytes()[0]);
        assertEquals(encoded.getBytes().length, encoded.getSize());
        String storedJson = new String(encoded.getBytes(), StandardCharsets.UTF_8);
        assertTrue(encoded.getSize() > storedJson.length());
    }
    
    @Test
    void testRejectInvalidEnvelopeAndCallInterfaceSets() {
        assertThrows(IllegalArgumentException.class,
            () -> AgentVersionContentCodec.encode(null));
        
        AgentVersionContent invalid = createGoldenContent();
        invalid.setKind("Other");
        assertEncodingRejected(invalid);
        invalid = createGoldenContent();
        invalid.setSchemaVersion(2);
        assertEncodingRejected(invalid);
        invalid = createGoldenContent();
        invalid.setCallInterfaces(null);
        assertEncodingRejected(invalid);
        invalid = new AgentVersionContent(Collections.<AgentCallInterface>emptyList());
        assertEncodingRejected(invalid);
        invalid = new AgentVersionContent(Collections.<AgentCallInterface>singletonList(null));
        assertEncodingRejected(invalid);
        
        List<AgentCallInterface> tooMany = new ArrayList<AgentCallInterface>();
        for (int i = 0; i < 17; i++) {
            tooMany.add(createCallInterface("p" + i, null));
        }
        AgentVersionContentCodec.encode(
            new AgentVersionContent(new ArrayList<AgentCallInterface>(tooMany.subList(0, 16))));
        assertThrows(IllegalArgumentException.class,
            () -> AgentVersionContentCodec.encode(new AgentVersionContent(tooMany)));
        
        AgentCallInterface duplicate = createCallInterface("a2a", null);
        invalid = new AgentVersionContent(
            Arrays.asList(createCallInterface("a2a", null), duplicate));
        assertEncodingRejected(invalid);
        
        AgentCallInterface boundedEndpoints = createCallInterface("a2a", null);
        List<Endpoint> endpoints = new ArrayList<Endpoint>();
        for (int i = 0; i < 64; i++) {
            endpoints.add(createEndpoint("http://e" + i + ".example.com/rpc"));
        }
        boundedEndpoints.setDeclaredEndpoints(endpoints);
        AgentVersionContent boundedContent =
            new AgentVersionContent(Collections.singletonList(boundedEndpoints));
        AgentVersionContentCodec.encode(boundedContent);
        endpoints.add(createEndpoint("http://overflow.example.com/rpc"));
        assertEncodingRejected(boundedContent);
    }
    
    @Test
    void testRejectMissingDescriptorAndOversizedContent() {
        AgentVersionContent invalid = createGoldenContent();
        invalid.getCallInterfaces().get(0).setNativeDescriptor(null);
        assertEncodingRejected(invalid);
        
        invalid = createGoldenContent();
        invalid.getCallInterfaces().get(0)
            .setNativeDescriptor(repeat('x', AgentVersionContentCodec.MAX_CONTENT_SIZE));
        assertEncodingRejected(invalid);
    }
    
    @Test
    void testContentSizeBoundaryUsesPersistedUtf8Bytes() {
        AgentCallInterface callInterface = createCallInterface("a2a", null);
        callInterface.setNativeDescriptor("");
        AgentVersionContent content =
            new AgentVersionContent(Collections.singletonList(callInterface));
        int envelopeSize = AgentVersionContentCodec.encode(content).getSize();
        callInterface.setNativeDescriptor(
            repeat('x', AgentVersionContentCodec.MAX_CONTENT_SIZE - envelopeSize));
        assertEquals(AgentVersionContentCodec.MAX_CONTENT_SIZE,
            AgentVersionContentCodec.encode(content).getSize());
        
        callInterface.setNativeDescriptor(
            repeat('x', AgentVersionContentCodec.MAX_CONTENT_SIZE - envelopeSize + 1));
        assertEncodingRejected(content);
    }
    
    @Test
    void testDecodeRejectsInvalidStorageBytes() {
        assertThrows(IllegalArgumentException.class,
            () -> AgentVersionContentCodec.digest(null));
        assertThrows(IllegalArgumentException.class,
            () -> AgentVersionContentCodec.digest(
                new byte[AgentVersionContentCodec.MAX_CONTENT_SIZE + 1]));
        assertThrows(IllegalArgumentException.class,
            () -> AgentVersionContentCodec.decode(null));
        assertThrows(IllegalArgumentException.class,
            () -> AgentVersionContentCodec.decode(
                new byte[AgentVersionContentCodec.MAX_CONTENT_SIZE + 1]));
        assertThrows(IllegalArgumentException.class,
            () -> AgentVersionContentCodec.decode(new byte[] {(byte) 0xC3, 0x28}));
        
        byte[] persisted = AgentVersionContentCodec.encode(createGoldenContent()).getBytes();
        byte[] whitespace = new byte[persisted.length + 1];
        whitespace[0] = ' ';
        System.arraycopy(persisted, 0, whitespace, 1, persisted.length);
        assertEquals(AgentVersionContent.KIND,
            AgentVersionContentCodec.decode(whitespace).getKind());
        
        byte[] invalidModel = ("{\"kind\":\"AgentVersionContent\","
            + "\"schemaVersion\":1,\"callInterfaces\":[]}")
            .getBytes(StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class,
            () -> AgentVersionContentCodec.decode(invalidModel));
    }
    
    @Test
    void testDecodeRejectsUnknownOwnedFieldsDuplicateMembersAndTrailingJson() {
        String valid = new String(AgentVersionContentCodec.encode(createGoldenContent()).getBytes(),
            StandardCharsets.UTF_8);
        assertDecodeRejected(valid.substring(0, valid.length() - 1) + ",\"unknown\":true}");
        assertDecodeRejected(valid.replace("\"protocol\":\"a2a\",",
            "\"protocol\":\"a2a\",\"unknown\":true,"));
        assertDecodeRejected(valid.replace(
            "\"uri\":\"https://example.com:443/a2a?b=2&a=1\",",
            "\"uri\":\"https://example.com:443/a2a?b=2&a=1\",\"unknown\":true,"));
        assertDecodeRejected(valid.replace("{\"kind\":\"AgentVersionContent\"",
            "{\"kind\":\"AgentVersionContent\",\"kind\":\"AgentVersionContent\""));
        assertDecodeRejected(valid + "{}");
        
        AgentVersionContent decoded = AgentVersionContentCodec.decode(
            valid.getBytes(StandardCharsets.UTF_8));
        assertTrue(decoded.getCallInterfaces().get(0).getNativeDescriptor() instanceof Map);
        assertEquals("h", decoded.getCallInterfaces().get(0).getDeclaredEndpoints().get(0)
            .getMetadata().get("az"));
    }
    
    private AgentVersionContent createGoldenContent() {
        AgentCallInterface callInterface = new AgentCallInterface();
        callInterface.setProtocol("a2a");
        callInterface.setProtocolVersion("1.0");
        callInterface.setDescriptorMediaType("application/json");
        Map<String, Object> capabilities = new LinkedHashMap<String, Object>();
        capabilities.put("streaming", true);
        capabilities.put("push", false);
        Map<String, Object> descriptor = new LinkedHashMap<String, Object>();
        descriptor.put("version", "1.0.6");
        descriptor.put("title", "订单 Agent");
        descriptor.put("name", "Order Agent");
        descriptor.put("capabilities", capabilities);
        callInterface.setNativeDescriptor(descriptor);
        callInterface.setEndpointSourceOrder(
            Arrays.asList(EndpointSource.RUNTIME, EndpointSource.DECLARED));
        Endpoint endpoint = createEndpoint("HTTPS://Example.COM/a2a?b=2&a=1");
        endpoint.setTransport("JSONRPC");
        Map<String, String> metadata = new LinkedHashMap<String, String>();
        metadata.put("zone", "cn-hangzhou-h");
        metadata.put("az", "h");
        endpoint.setMetadata(metadata);
        callInterface.setDeclaredEndpoints(Collections.singletonList(endpoint));
        return new AgentVersionContent(Collections.singletonList(callInterface));
    }
    
    private AgentCallInterface createCallInterface(String protocol, String endpointUri) {
        AgentCallInterface result = new AgentCallInterface();
        result.setProtocol(protocol);
        result.setDescriptorMediaType("application/json");
        result.setNativeDescriptor(Collections.singletonMap("name", protocol));
        result.setEndpointSourceOrder(Collections.singletonList(EndpointSource.DECLARED));
        if (endpointUri != null) {
            result.setDeclaredEndpoints(
                new ArrayList<Endpoint>(Collections.singletonList(createEndpoint(endpointUri))));
        }
        return result;
    }
    
    private Endpoint createEndpoint(String uri) {
        Endpoint result = new Endpoint();
        result.setUri(uri);
        result.setTransport("HTTP");
        return result;
    }
    
    private void assertEncodingRejected(AgentVersionContent content) {
        assertThrows(IllegalArgumentException.class,
            () -> AgentVersionContentCodec.encode(content));
    }
    
    private void assertDecodeRejected(String content) {
        assertThrows(IllegalArgumentException.class,
            () -> AgentVersionContentCodec.decode(content.getBytes(StandardCharsets.UTF_8)));
    }
    
    private String repeat(char value, int count) {
        char[] result = new char[count];
        Arrays.fill(result, value);
        return new String(result);
    }
}
