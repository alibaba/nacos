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

import com.alibaba.nacos.api.ai.model.agent.RuntimeVersionBinding;
import com.alibaba.nacos.api.ai.model.agent.Endpoint;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.IDN;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EndpointCanonicalizerTest {
    
    @ParameterizedTest
    @CsvSource(value = {
        "1.0.0;NULL;NULL;NULL;1.0.0;[1.0.0]",
        "1.0.0;NULL;2.0.0;NULL;2.0.0;[2.0.0]",
        "1.0.0;[1.0.0,2.0.0];2.0.0;NULL;2.0.0;[1.0.0,2.0.0]",
        "1.0.0;[1.0.0,2.0.0);2.0.0;[2.0.0];2.0.0;[2.0.0]",
        "1.0.0;NULL;NULL;[1.0.0,2.0.0];1.0.0;[1.0.0,2.0.0]",
        "NULL;NULL;2.0.0;[2.0.0,2.0.0];2.0.0;[2.0.0]"
    }, delimiter = ';', nullValues = "NULL")
    void shouldResolveBindingFieldsBeforeApplyingExactDefault(String batchVersion,
        String batchRange, String endpointVersion, String endpointRange,
        String expectedVersion, String expectedRange) {
        Endpoint source = endpoint("https://example.com/a", "HTTP");
        RuntimeVersionBinding input = new RuntimeVersionBinding();
        input.setRuntimeVersion(endpointVersion);
        input.setVersionRange(endpointRange);
        source.setBindings(Collections.singletonList(input));
        RuntimeVersionBinding result = EndpointCanonicalizer.canonicalizeRuntimeBinding(
            source, batchVersion, batchRange);
        assertEquals(expectedVersion, result.getRuntimeVersion());
        assertEquals(expectedRange, result.getVersionRange());
        assertNotSame(input, result);
        assertEquals(endpointVersion, input.getRuntimeVersion());
        assertEquals(endpointRange, input.getVersionRange());
    }
    
    @Test
    void shouldRejectInvalidInputBindingWithoutFallingBackToBatch() {
        Endpoint source = endpoint("https://example.com/a", "HTTP");
        source.setBindings(Collections.emptyList());
        assertThrows(IllegalArgumentException.class,
            () -> EndpointCanonicalizer.canonicalizeRuntimeBinding(source, "1.0.0", null));
        source.setBindings(Collections.singletonList(null));
        assertThrows(IllegalArgumentException.class,
            () -> EndpointCanonicalizer.canonicalizeRuntimeBinding(source, "1.0.0", null));
        RuntimeVersionBinding input = new RuntimeVersionBinding();
        source.setBindings(Arrays.asList(input, input));
        assertThrows(IllegalArgumentException.class,
            () -> EndpointCanonicalizer.canonicalizeRuntimeBinding(source, "1.0.0", null));
        source.setBindings(Collections.singletonList(input));
        input.setRuntimeVersion("");
        assertThrows(IllegalArgumentException.class,
            () -> EndpointCanonicalizer.canonicalizeRuntimeBinding(source, "1.0.0", null));
        input.setRuntimeVersion("2.0.0");
        assertThrows(IllegalArgumentException.class,
            () -> EndpointCanonicalizer.canonicalizeRuntimeBinding(source, "1.0.0",
                "[1.0.0,2.0.0)"));
        input.setVersionRange("");
        assertThrows(IllegalArgumentException.class,
            () -> EndpointCanonicalizer.canonicalizeRuntimeBinding(source, "1.0.0", "[2.0.0]"));
        source.setBindings(null);
        assertThrows(IllegalArgumentException.class,
            () -> EndpointCanonicalizer.canonicalizeRuntimeBinding(source, null, "[1.0.0]"));
        assertThrows(IllegalArgumentException.class,
            () -> EndpointCanonicalizer.canonicalizeRuntimeBinding(null, "1.0.0", null));
        RuntimeVersionBinding inherited = EndpointCanonicalizer.canonicalizeRuntimeBinding(
            source, "1.0.0", null);
        assertEquals("[1.0.0]", inherited.getVersionRange());
        assertNull(source.getBindings());
    }
    
    @Test
    void shouldRejectNullPriorityAndWeightInsteadOfSilentlyDefaulting() {
        Endpoint source = endpoint("https://example.com/a", "HTTP");
        source.setPriority(null);
        assertEquals("Endpoint priority must not be null",
            assertThrows(IllegalArgumentException.class,
                () -> EndpointCanonicalizer.canonicalize(source)).getMessage());
        source.setPriority(0);
        source.setWeight(null);
        assertEquals("Endpoint weight must not be null",
            assertThrows(IllegalArgumentException.class,
                () -> EndpointCanonicalizer.canonicalize(source)).getMessage());
    }
    
    @Test
    void shouldDeepCopyRuntimeBindingsAndManagementState() {
        Endpoint source = endpoint("HTTPS://Example.COM/a", "JSONRPC");
        RuntimeVersionBinding binding = new RuntimeVersionBinding();
        binding.setRuntimeVersion("1.0.0");
        binding.setVersionRange("[1.0.0]");
        source.setBindings(Collections.singletonList(binding));
        source.setHealthy(false);
        source.setEnabled(true);
        Endpoint copy = EndpointCanonicalizer.canonicalize(source);
        assertEquals(Boolean.FALSE, copy.getHealthy());
        assertEquals(Boolean.TRUE, copy.getEnabled());
        assertEquals("[1.0.0]", copy.getBindings().get(0).getVersionRange());
        copy.getBindings().get(0).setVersionRange("[2.0.0]");
        assertEquals("[1.0.0]", source.getBindings().get(0).getVersionRange());
        assertNotSame(source.getBindings(), copy.getBindings());
    }
    
    @Test
    void testCanonicalizesDnsAndIdnWithDefaultPorts() {
        assertEquals("https://example.com:443/a2a?mode=sync",
            EndpointCanonicalizer.canonicalizeUri("HTTPS://Example.COM/a2a?mode=sync"));
        assertEquals("https://xn--bcher-kva.example:443/a2a",
            EndpointCanonicalizer.canonicalizeUri("https://BÜCHER.example/a2a"));
        assertEquals("ws://example.com:80",
            EndpointCanonicalizer.canonicalizeUri("WS://EXAMPLE.COM"));
        assertEquals("wss://example.com:443",
            EndpointCanonicalizer.canonicalizeUri("WSS://EXAMPLE.COM"));
    }
    
    @Test
    void testCanonicalizesIpv4AndIpv6() {
        assertEquals("http://192.168.1.10:80/a2a",
            EndpointCanonicalizer.canonicalizeUri("http://192.168.001.010/a2a"));
        assertEquals("https://[2001:db8::1]:443/a2a",
            EndpointCanonicalizer.canonicalizeUri("https://[2001:0DB8:0:0:0:0:0:1]/a2a"));
        assertEquals("https://[::ffff:c000:201]:443",
            EndpointCanonicalizer.canonicalizeUri("https://[::FFFF:192.0.2.1]"));
        assertEquals("https://[1:2:3:4:5:6:7:8]:8443",
            EndpointCanonicalizer.canonicalizeUri("https://[1:2:3:4:5:6:7:8]:8443"));
        assertEquals("2001:db8::1", EndpointCanonicalizer.normalizedHost("https://[2001:DB8::1]"));
        assertEquals(443, EndpointCanonicalizer.effectivePort("https://[2001:DB8::1]"));
    }
    
    @Test
    void testOtherSchemeRequiresExplicitPort() {
        assertEquals("grpc://example.com:9090/call",
            EndpointCanonicalizer.canonicalizeUri("grpc://Example.COM:9090/call"));
        assertThrows(IllegalArgumentException.class,
            () -> EndpointCanonicalizer.canonicalizeUri("grpc://example.com/call"));
    }
    
    @Test
    void testCanonicalizeReturnsIndependentEndpointAndSortedMetadata() {
        Endpoint original = endpoint("HTTPS://Example.COM/a", "JSONRPC");
        Map<String, String> metadata = new LinkedHashMap<String, String>();
        metadata.put("zone", "z2");
        metadata.put("environment", "prod");
        original.setMetadata(metadata);
        
        Endpoint canonical = EndpointCanonicalizer.canonicalize(original);
        assertNotSame(original, canonical);
        assertNotSame(original.getMetadata(), canonical.getMetadata());
        assertEquals("https://example.com:443/a", canonical.getUri());
        assertEquals(Integer.valueOf(0), canonical.getPriority());
        assertEquals(Double.valueOf(1D), canonical.getWeight());
        assertEquals(true, canonical.getHealthy());
        assertEquals(Arrays.asList("environment", "zone"),
            new ArrayList<String>(canonical.getMetadata().keySet()));
        
        canonical.getMetadata().put("new", "value");
        assertFalse(original.getMetadata().containsKey("new"));
        assertEquals("HTTPS://Example.COM/a", original.getUri());
        assertEquals(0, original.getPriority());
    }
    
    @Test
    void testCanonicalizePreservesExplicitEndpointValues() {
        Endpoint original = endpoint("https://example.com:8443/a", "JSONRPC");
        original.setPriority(10);
        original.setWeight(0D);
        original.setHealthy(Boolean.FALSE);
        original.setMetadata(new LinkedHashMap<String, String>());
        
        Endpoint canonical = EndpointCanonicalizer.canonicalize(original);
        assertEquals(Integer.valueOf(10), canonical.getPriority());
        assertEquals(Double.valueOf(0D), canonical.getWeight());
        assertEquals(Boolean.FALSE, canonical.getHealthy());
        assertNull(canonical.getMetadata());
    }
    
    @Test
    void testRejectsInvalidEndpointValues() {
        assertThrows(IllegalArgumentException.class,
            () -> EndpointCanonicalizer.canonicalize(null));
        
        Endpoint negativePriority = endpoint("https://example.com", "JSONRPC");
        negativePriority.setPriority(-1);
        assertThrows(IllegalArgumentException.class,
            () -> EndpointCanonicalizer.canonicalize(negativePriority));
        
        Endpoint invalidWeight = endpoint("https://example.com", "JSONRPC");
        invalidWeight.setWeight(Double.NaN);
        assertThrows(IllegalArgumentException.class,
            () -> EndpointCanonicalizer.canonicalize(invalidWeight));
        
        for (Double weight : Arrays.asList(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY,
            -1D, 10000.1D)) {
            Endpoint endpoint = endpoint("https://example.com", "JSONRPC");
            endpoint.setWeight(weight);
            assertThrows(IllegalArgumentException.class,
                () -> EndpointCanonicalizer.canonicalize(endpoint));
        }
        
        Endpoint invalidTransport = endpoint("https://example.com", "json_rpc");
        assertThrows(IllegalArgumentException.class,
            () -> EndpointCanonicalizer.canonicalize(invalidTransport));
    }
    
    @Test
    void testRejectsInvalidUris() {
        for (String uri : Arrays.asList(null, "", "/relative", "mailto:test@example.com",
            "http:///missing-host", "http://", "http://@/", "http://:80", "http://host:",
            "http://host:80:90", "https://[]/", "https://[::1]x",
            "https://user@example.com/a",
            "https://example.com/a#fragment", "https://example.com:0", "https://example.com:65536",
            "https://example.com:port", "https://256.1.1.1/a", "https://.1.2.3/a",
            "https://0000.1.1.1/a", "https://[2001:::1]/a", "https://[fe80::1%25eth0]/a")) {
            assertThrows(IllegalArgumentException.class,
                () -> EndpointCanonicalizer.canonicalizeUri(uri), uri);
        }
    }
    
    @Test
    void testRejectsUriWhenCanonicalFormExceedsLimit() {
        String prefix = "http://example.com/";
        String uri = prefix + repeat('a', 2048 - prefix.length());
        assertEquals(2048, uri.length());
        assertThrows(IllegalArgumentException.class,
            () -> EndpointCanonicalizer.canonicalizeUri(uri));
        
        String tooLong = uri + 'a';
        assertThrows(IllegalArgumentException.class,
            () -> EndpointCanonicalizer.canonicalizeUri(tooLong));
    }
    
    @Test
    void testPreservesUriSyntaxFailureAsCause() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> EndpointCanonicalizer.canonicalizeUri("https://exa mple.com/a"));
        assertInstanceOf(URISyntaxException.class, exception.getCause());
    }
    
    @Test
    void testPreservesHostAndPortParsingFailuresAsCause() {
        IllegalArgumentException hostException = assertThrows(IllegalArgumentException.class,
            () -> EndpointCanonicalizer.canonicalizeUri("https://_/a"));
        assertNotNull(hostException.getCause());
        
        IllegalArgumentException portException = assertThrows(IllegalArgumentException.class,
            () -> EndpointCanonicalizer
                .canonicalizeUri("https://example.com:999999999999999999999/a"));
        assertInstanceOf(NumberFormatException.class, portException.getCause());
    }
    
    @Test
    void testRejectsDefensiveAuthorityAndAddressBranches() throws Exception {
        assertPrivateInvocationFails("parseAuthority", new Class<?>[] {String.class, String.class},
            new Object[] {"", "http://"}, null);
        assertPrivateInvocationFails("parseAuthority", new Class<?>[] {String.class, String.class},
            new Object[] {"[]", "https://[]"}, null);
        assertPrivateInvocationFails("parseAuthority", new Class<?>[] {String.class, String.class},
            new Object[] {"[::1]suffix", "https://[::1]suffix"}, null);
        invokePrivate("parseAuthority", new Class<?>[] {String.class, String.class},
            new Object[] {"[::1]:443", "https://[::1]:443"});
        assertPrivateInvocationFails("parseAuthority", new Class<?>[] {String.class, String.class},
            new Object[] {":443", "https://:443"}, null);
        
        assertPrivateInvocationFails("normalizeIpv4", new Class<?>[] {String.class, String.class},
            new Object[] {"1.2.3", "https://1.2.3"}, null);
        assertPrivateInvocationFails("normalizeIpv4", new Class<?>[] {String.class, String.class},
            new Object[] {"a.1.2.3", "https://a.1.2.3"}, NumberFormatException.class);
        try (MockedStatic<IDN> idnMock = Mockito.mockStatic(IDN.class)) {
            idnMock.when(() -> IDN.toASCII("empty-after-idn", IDN.USE_STD3_ASCII_RULES))
                .thenReturn("");
            assertPrivateInvocationFails("normalizeNonIpv6",
                new Class<?>[] {String.class, String.class},
                new Object[] {"empty-after-idn", "https://empty-after-idn"}, null);
        }
        assertPrivateInvocationFails("normalizeIpv6", new Class<?>[] {String.class, String.class},
            new Object[] {"1::2::3", "https://[1::2::3]"}, null);
        assertPrivateInvocationFails("normalizeIpv6", new Class<?>[] {String.class, String.class},
            new Object[] {"1:2:3:4:5:6:7:8::", "https://[1:2:3:4:5:6:7:8::]"}, null);
        assertPrivateInvocationFails("normalizeIpv6", new Class<?>[] {String.class, String.class},
            new Object[] {"1:2:3", "https://[1:2:3]"}, null);
        assertPrivateInvocationFails("normalizeIpv6", new Class<?>[] {String.class, String.class},
            new Object[] {"1:::2", "https://[1:::2]"}, null);
        assertPrivateInvocationFails("normalizeIpv6", new Class<?>[] {String.class, String.class},
            new Object[] {"192.0.2.1:1::", "https://[192.0.2.1:1::]"}, null);
        assertPrivateInvocationFails("normalizeIpv6", new Class<?>[] {String.class, String.class},
            new Object[] {"gggg::", "https://[gggg::]"}, null);
        assertPrivateInvocationFails("parseIpv6Section",
            new Class<?>[] {String.class, String.class},
            new Object[] {"1::2", "https://[1::2]"}, null);
    }
    
    private void assertPrivateInvocationFails(String methodName, Class<?>[] parameterTypes,
        Object[] arguments, Class<? extends Throwable> expectedCauseType) throws Exception {
        InvocationTargetException exception = assertThrows(InvocationTargetException.class,
            () -> invokePrivate(methodName, parameterTypes, arguments));
        IllegalArgumentException failure = assertInstanceOf(IllegalArgumentException.class,
            exception.getCause());
        if (expectedCauseType != null) {
            assertInstanceOf(expectedCauseType, failure.getCause());
        }
    }
    
    private Object invokePrivate(String methodName, Class<?>[] parameterTypes, Object[] arguments)
        throws Exception {
        Method method = EndpointCanonicalizer.class.getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return method.invoke(null, arguments);
    }
    
    private String repeat(char value, int length) {
        char[] characters = new char[length];
        Arrays.fill(characters, value);
        return new String(characters);
    }
    
    private Endpoint endpoint(String uri, String transport) {
        Endpoint endpoint = new Endpoint();
        endpoint.setUri(uri);
        endpoint.setTransport(transport);
        return endpoint;
    }
}
