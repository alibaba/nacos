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

package com.alibaba.nacos.airegistry.service.ard;

import com.alibaba.nacos.airegistry.constant.ArdProtocolConstants;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UT-C04: JSON-LD semantics and bounded deployment-controlled document loading.
 *
 * @author nacos
 */
class ArdContextResolverTest {
    
    private static final String NS = "https://agenticresourcediscovery.org/ns#";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private HttpServer server;
    private String base;
    private ArdContextResolver resolver;
    
    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
        resolver = resolver(Set.of(), 1048576, 3000);
    }
    
    @AfterEach
    void tearDown() {
        server.stop(0);
    }
    
    @Test
    void shouldExpandBaseOfflineAndPreserveIriIdentity() throws Exception {
        assertEquals(NS + "mediaType", resolver.resolve(null).expand("type"));
        var context = resolver.resolve(json("\"" + ArdProtocolConstants.BASE_CONTEXT + "\""));
        assertEquals(NS + "mediaType", context.expand("type"));
        assertEquals(NS + "mediaType", context.expand("ard:mediaType"));
        assertEquals("https://Example.org/a.b#C", context.expand("https://Example.org/a.b#C"));
        assertEquals("@type", context.expand("@type"));
    }
    
    @Test
    void shouldProcessPrefixesDefinitionsVocabularyAndArrays() throws Exception {
        var context = resolver.resolve(json("[{\"a\":\"" + NS + "\"},"
            + "{\"b\":{\"@id\":\"" + NS + "\",\"@prefix\":true},"
            + "\"t\":\"a:tags\",\"@vocab\":\"https://other.example/\"}]"));
        assertEquals(NS + "tags", context.expand("a:tags"));
        assertEquals(NS + "tags", context.expand("b:tags"));
        assertEquals(NS + "tags", context.expand("t"));
        assertEquals(NS + "mediaType", context.expand("type"));
        assertEquals("https://other.example/custom", context.expand("custom"));
    }
    
    @Test
    void shouldRespectReboundAndDisabledTerms() throws Exception {
        var context =
            resolver.resolve(json("{\"type\":\"https://other.example/type\",\"tags\":null}"));
        assertEquals("https://other.example/type", context.expand("type"));
        assertNull(context.expand("tags"));
        assertEquals(NS + "tags", resolver.resolve(null).expand("tags"));
    }
    
    @Test
    void shouldRestoreBaseAfterResetAndKeepLaterDefinitions() throws Exception {
        var context = resolver.resolve(json("[{\"a\":\"https://other.example/\"},null,"
            + "{\"b\":\"" + NS + "\"}]"));
        assertEquals(NS + "mediaType", context.expand("type"));
        assertEquals(NS + "tags", context.expand("b:tags"));
        assertEquals("a:tags", context.expand("a:tags"));
    }
    
    @Test
    void shouldPreserveProtectedTermResetFailure() throws Exception {
        JsonNode input = json("[{\"t\":{\"@id\":\"" + NS
            + "tags\",\"@protected\":true}},null]");
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(input));
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"null", "123", "true", "[123]", "{\"t\":{\"@id\":123}}",
        "{\"a\":\"b\",\"b\":\"a\"}", "{\"@version\":1.0}"})
    void shouldRejectInvalidContextsWithoutFallback(String value) throws Exception {
        JsonNode input = json(value);
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(input));
    }
    
    @Test
    void shouldResolveAllowedRelativeRemoteContextsWithRequestScopedCache() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        AtomicReference<String> document =
            new AtomicReference<>("{\"@context\":{\"t\":\"" + NS + "tags\"}}");
        server.createContext("/child", exchange -> {
            requests.incrementAndGet();
            assertNull(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] bytes = document.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (var output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });
        serve("/parent", "{\"@context\":\"child\"}");
        var allowed = resolver(Set.of(base + "/parent", base + "/child"), 1048576, 3000);
        JsonNode input = json("[\"" + base + "/parent\",\"" + base + "/child\"]");
        assertEquals(NS + "tags", allowed.resolve(input).expand("t"));
        assertEquals(1, requests.get());
        document.set("{\"@context\":{\"t\":\"" + NS + "mediaType\"}}");
        assertEquals(NS + "mediaType", allowed.resolve(input).expand("t"));
        assertEquals(2, requests.get());
    }
    
    @Test
    void shouldRestoreBaseAfterRemoteReset() throws Exception {
        serve("/reset", "{\"@context\":null}");
        var allowed = resolver(Set.of(base + "/reset"), 1048576, 3000);
        assertEquals(NS + "mediaType",
            allowed.resolve(json("\"" + base + "/reset\"")).expand("type"));
    }
    
    @Test
    void shouldRejectDeniedUrisAndRedirectsWithoutLoadingTheirTargets() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        server.createContext("/target", exchange -> {
            requests.incrementAndGet();
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().add("Location", base + "/target");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        for (String uri : Set.of(base + "/target", "file:///tmp/context", base + "/target#fragment",
            base.replace("http://", "http://user@") + "/target")) {
            JsonNode input = MAPPER.valueToTree(uri);
            assertThrows(IllegalArgumentException.class, () -> resolver.resolve(input));
        }
        var allowed = resolver(Set.of(base + "/redirect", base + "/target"), 1048576, 3000);
        assertThrows(IllegalArgumentException.class,
            () -> allowed.resolve(MAPPER.valueToTree(base + "/redirect")));
        assertEquals(0, requests.get());
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"not json", "{}", "[]", "{\"@context\":123}",
        "{\"@context\":{\"t\":{\"@id\":123}}}"})
    void shouldRejectMalformedRemoteDocuments(String document) {
        serve("/context", document);
        var allowed = resolver(Set.of(base + "/context"), 1048576, 3000);
        assertThrows(IllegalArgumentException.class,
            () -> allowed.resolve(MAPPER.valueToTree(base + "/context")));
    }
    
    @Test
    void shouldRejectCyclesAndUnavailableDocuments() {
        serve("/a", "{\"@context\":\"b\"}");
        serve("/b", "{\"@context\":\"a\"}");
        var allowed = resolver(Set.of(base + "/a", base + "/b", base + "/missing"), 1048576, 3000);
        for (String path : Set.of("/a", "/missing")) {
            assertThrows(IllegalArgumentException.class,
                () -> allowed.resolve(MAPPER.valueToTree(base + path)));
        }
    }
    
    @Test
    void shouldCountUtf8BytesAcrossInlineAndRemoteContexts() throws Exception {
        JsonNode inline = json("{\"标签\":\"" + NS + "tags\"}");
        int inlineBytes = inline.toString().getBytes(StandardCharsets.UTF_8).length;
        assertEquals(NS + "tags",
            resolver(Set.of(), inlineBytes, 3000).resolve(inline).expand("标签"));
        assertThrows(IllegalArgumentException.class,
            () -> resolver(Set.of(), inlineBytes - 1, 3000).resolve(inline));
        String document = "{\"@context\":{\"t\":\"" + NS + "tags\"}}";
        serve("/context", document);
        JsonNode input = json("[\"" + base + "/context\",\"" + base + "/context\"]");
        int bytes = input.toString().getBytes(StandardCharsets.UTF_8).length
            + document.getBytes(StandardCharsets.UTF_8).length;
        var allowed = Set.of(base + "/context");
        assertEquals(NS + "tags", resolver(allowed, bytes, 3000).resolve(input).expand("t"));
        assertThrows(IllegalArgumentException.class,
            () -> resolver(allowed, bytes - 1, 3000).resolve(input));
    }
    
    @Test
    void shouldRejectRemoteReadTimeout() {
        server.createContext("/slow", exchange -> {
            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        var allowed = resolver(Set.of(base + "/slow"), 1048576, 20);
        assertThrows(IllegalArgumentException.class,
            () -> allowed.resolve(MAPPER.valueToTree(base + "/slow")));
    }
    
    @Test
    void shouldRejectInvalidDeploymentBudgets() {
        assertThrows(IllegalArgumentException.class, () -> resolver(Set.of(), 0, 3000));
        assertThrows(IllegalArgumentException.class, () -> resolver(Set.of(), 1048576, -1));
    }
    
    @Test
    void shouldApplyCurrentDeploymentConfigurationForEachResolution() throws Exception {
        String key = "nacos.ai.ard.context.allowed-uris";
        String previous = System.getProperty(key);
        serve("/configured", "{\"@context\":{\"t\":\"" + NS + "tags\"}}");
        var configured = new ArdContextResolver();
        JsonNode input = MAPPER.valueToTree(base + "/configured");
        try {
            System.setProperty(key, base + "/configured");
            assertEquals(NS + "tags", configured.resolve(input).expand("t"));
            System.setProperty(key, "");
            assertThrows(IllegalArgumentException.class, () -> configured.resolve(input));
            assertEquals(NS + "mediaType", configured.resolve(null).expand("type"));
        } finally {
            if (previous == null) {
                System.clearProperty(key);
            } else {
                System.setProperty(key, previous);
            }
        }
    }
    
    @Test
    void shouldBoundTheWholeResponseIncludingAStalledBody() {
        server.createContext("/slow-body", exchange -> {
            exchange.sendResponseHeaders(200, 100);
            try (var output = exchange.getResponseBody()) {
                output.write('{');
                output.flush();
                Thread.sleep(250);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (java.io.IOException ignored) {
                // The bounded client can cancel before this incomplete body closes.
            }
        });
        var allowed = resolver(Set.of(base + "/slow-body"), 1048576, 20);
        assertThrows(IllegalArgumentException.class,
            () -> allowed.resolve(MAPPER.valueToTree(base + "/slow-body")));
    }
    
    @Test
    void shouldNotFollowHttpLinkHeaders() {
        server.createContext("/link", exchange -> {
            exchange.getResponseHeaders().add("Link", "<" + base
                + "/target>; rel=\"http://www.w3.org/ns/json-ld#context\"");
            byte[] bytes = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (var output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });
        var allowed = resolver(Set.of(base + "/link"), 1048576, 3000);
        assertThrows(IllegalArgumentException.class,
            () -> allowed.resolve(MAPPER.valueToTree(base + "/link")));
    }
    
    @Test
    void shouldPreserveInterruptStatusAfterCancelledLoading() {
        serve("/context", "{\"@context\":{}}");
        var allowed = resolver(Set.of(base + "/context"), 1048576, 3000);
        Thread.currentThread().interrupt();
        try {
            assertThrows(IllegalArgumentException.class,
                () -> allowed.resolve(MAPPER.valueToTree(base + "/context")));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }
    
    private JsonNode json(String value) throws Exception {
        return MAPPER.readTree(value);
    }
    
    private ArdContextResolver resolver(Set<String> uris, int bytes, int timeout) {
        return new ArdContextResolver(new ArdContextLoader(uris, bytes, timeout));
    }
    
    private void serve(String path, String document) {
        server.createContext(path, exchange -> {
            byte[] bytes = document.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/ld+json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (var output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });
    }
}
