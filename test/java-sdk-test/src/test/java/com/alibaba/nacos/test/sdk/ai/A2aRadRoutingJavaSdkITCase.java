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

package com.alibaba.nacos.test.sdk.ai;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.ai.A2aService;
import com.alibaba.nacos.api.ai.AiService;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.listener.AbstractNacosAgentCardListener;
import com.alibaba.nacos.api.ai.listener.AbstractNacosAgentDiscoveryListener;
import com.alibaba.nacos.api.ai.listener.NacosAgentCardEvent;
import com.alibaba.nacos.api.ai.listener.NacosAgentDiscoveryEvent;
import com.alibaba.nacos.api.ai.model.a2a.AgentCard;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardDetailInfo;
import com.alibaba.nacos.api.ai.model.a2a.AgentCapabilities;
import com.alibaba.nacos.api.ai.model.a2a.AgentEndpoint;
import com.alibaba.nacos.api.ai.model.agent.AgentReference;
import com.alibaba.nacos.api.ai.model.agent.AgentDiscoveryFilter;
import com.alibaba.nacos.api.ai.model.agent.AgentDiscoveryResult;
import com.alibaba.nacos.api.ai.model.agent.AgentEndpointRegistrationBatch;
import com.alibaba.nacos.api.ai.model.agent.AgentSearchRequest;
import com.alibaba.nacos.api.ai.model.agent.Endpoint;
import com.alibaba.nacos.api.ai.model.agent.EndpointSet;
import com.alibaba.nacos.api.ai.model.agent.EndpointSource;
import com.alibaba.nacos.api.ai.model.agent.client.AgentPublishRequest;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.maintainer.client.ai.AiMaintainerFactory;
import com.alibaba.nacos.maintainer.client.ai.AiMaintainerService;
import com.alibaba.nacos.test.sdk.JavaSdkBaseITCase;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Public factory coverage for all 18 legacy and 10 native signatures across actual bindings. */
class A2aRadRoutingJavaSdkITCase extends JavaSdkBaseITCase {
    private static final String V1 = "1.0.0";
    private static final String V2 = "1.2.0";

    @ParameterizedTest
    @CsvSource({"grpc,false", "grpc,true", "http,false", "http,true", "auto,false", "auto,true"})
    void allSignaturesOnRad(String mode, boolean resource) throws Exception {
        verifySignatures(mode, resource, SERVER_ADDR, true);
    }

    @ParameterizedTest
    @CsvSource({"http,false", "http,true", "auto,false", "auto,true"})
    @EnabledIfSystemProperty(named = "nacos.ai.adaptation.http-only-address", matches = ".+")
    void allSignaturesWithGrpcPortClosed(String mode, boolean resource) throws Exception {
        verifySignatures(mode, resource,
            System.getProperty("nacos.ai.adaptation.http-only-address"), true);
    }

    @ParameterizedTest
    @CsvSource({"grpc,false", "grpc,true", "http,false", "http,true", "auto,false", "auto,true"})
    @EnabledIfSystemProperty(named = "nacos.ai.compatibility.old-server-address", matches = ".+")
    void allSignaturesOnReleasedOldServer(String mode, boolean resource) throws Exception {
        verifySignatures(mode, resource,
            System.getProperty("nacos.ai.compatibility.old-server-address"), false);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @EnabledIfSystemProperty(named = "nacos.ai.adaptation.observed-address", matches = ".+")
    void allSignaturesOnObservedTransport(boolean resource) throws Exception {
        verifySignatures(System.getProperty("nacos.ai.adaptation.observed-mode"), resource,
            System.getProperty("nacos.ai.adaptation.observed-address"), true);
    }

    @ParameterizedTest
    @ValueSource(strings = {"grpc", "http", "auto"})
    @EnabledIfSystemProperty(named = "nacos.ai.adaptation.unreachable-address", matches = ".+")
    void unavailableServerNeverBecomesUnsupportedAndLocalCleanupWorks(String mode) throws Exception {
        Properties properties = sdkProperties();
        properties.setProperty(PropertyKeyConst.SERVER_ADDR,
            System.getProperty("nacos.ai.adaptation.unreachable-address"));
        properties.setProperty(AiConstants.AI_TRANSPORT_MODE, mode);
        AiService service;
        try {
            service = createAiServiceWithoutReadiness(properties);
        } catch (NacosException error) {
            assertConnectionFailure(error);
            return;
        }
        assertConnectionFailure(assertThrows(NacosException.class,
            () -> service.getAgentCard("unreachable-agent")));
        assertConnectionFailure(assertThrows(NacosException.class,
            () -> service.agent().discoverAgent(reference("unreachable-agent", V1))));
        localCleanup(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"grpc", "http", "auto"})
    @EnabledIfSystemProperty(named = "nacos.ai.adaptation.old-http-only-address", matches = ".+")
    void oldHttpWithoutNegotiationDoesNotInventRadUnsupported(String mode) throws Exception {
        Properties properties = new Properties();
        properties.setProperty(PropertyKeyConst.SERVER_ADDR,
            System.getProperty("nacos.ai.adaptation.old-http-only-address"));
        properties.setProperty(AiConstants.AI_TRANSPORT_MODE, mode);
        AiService service = createAiServiceWithoutReadiness(properties);
        assertConnectionFailure(assertThrows(NacosException.class,
            () -> service.agent().getAgentCard("old-http-only")));
        NacosException nativeFailure = assertThrows(NacosException.class,
            () -> service.agent().discoverAgent(reference("old-http-only", V1)));
        assertTrue(nativeFailure.getErrCode() != NacosException.SERVER_NOT_IMPLEMENTED,
            nativeFailure.toString());
        localCleanup(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"http", "auto"})
    @EnabledIfSystemProperty(named = "nacos.ai.adaptation.mixed-address", matches = ".+")
    void differentCapabilityAndBusinessMembersNeverCauseLegacyFallback(String mode) throws Exception {
        String name = randomServiceName("mixed-binding");
        String oldAddress = System.getProperty("nacos.ai.compatibility.old-server-address");
        Properties legacyProperties = new Properties();
        legacyProperties.setProperty(PropertyKeyConst.SERVER_ADDR, oldAddress);
        LegacyA2aClient legacy = new LegacyA2aClient(legacyProperties);
        addCleanup(legacy::shutdown);
        AiMaintainerService management = AiMaintainerFactory.createAiMaintainerService(legacyProperties);
        addCleanup(() -> management.a2a().deleteAgent(name));
        legacy.releaseAgentCard(card(name, V1), "URL", true);
        waitUntil("the released server makes the fixture Agent Card readable",
            () -> V1.equals(legacy.getAgentCard(name).getVersion()));
        Properties properties = sdkProperties();
        properties.setProperty(PropertyKeyConst.SERVER_ADDR,
            System.getProperty("nacos.ai.adaptation.mixed-address"));
        properties.setProperty(AiConstants.AI_TRANSPORT_MODE, mode);
        AiService service = createAiServiceWithoutReadiness(properties);
        assertEquals(NacosException.NOT_FOUND, assertThrows(NacosException.class,
            () -> service.getAgentCard(name, V1)).getErrCode());
        assertEquals(NacosException.NOT_FOUND, assertThrows(NacosException.class,
            () -> service.releaseAgentCard(card(name, V2))).getErrCode());
        assertThrows(NacosException.class, () -> legacy.getAgentCard(name, V2));
        assertEquals(V1, legacy.getAgentCard(name).getVersion());
        localCleanup(service);
    }

    private void localCleanup(AiService service) throws Exception {
        AbstractNacosAgentCardListener oldListener = new AbstractNacosAgentCardListener() {
            @Override
            public void onEvent(NacosAgentCardEvent event) {
            }
        };
        AbstractNacosAgentDiscoveryListener nativeListener = new AbstractNacosAgentDiscoveryListener() {
            @Override
            public void onEvent(NacosAgentDiscoveryEvent event) {
            }
        };
        service.unsubscribeAgentCard("cleanup", oldListener);
        service.agent().unsubscribeAgentCard("cleanup", V1, oldListener);
        service.agent().unsubscribeAgent(reference("cleanup", V1), nativeListener);
        service.shutdown();
        service.shutdown();
    }

    private void assertConnectionFailure(NacosException error) {
        assertTrue(error.getErrCode() != NacosException.SERVER_NOT_IMPLEMENTED, error.toString());
        StringBuilder diagnostic = new StringBuilder();
        for (Throwable current = error; current != null; current = current.getCause()) {
            diagnostic.append(current.getMessage()).append(' ');
        }
        String message = diagnostic.toString().toLowerCase(java.util.Locale.ROOT);
        assertTrue(message.contains("connect") || message.contains("unreachable")
            || message.contains("capability"), message);
    }

    @ParameterizedTest
    @ValueSource(strings = {"facade", "resource", "native"})
    @EnabledIfSystemProperty(named = "nacos.ai.adaptation.lost-publish-address", matches = ".+")
    void lostPublishResponseIsNotReplayedAndExplicitRetryUsesStoredState(String entry)
        throws Exception {
        AiService setup = createAiService();
        AiMaintainerService maintainer = AiMaintainerFactory.createAiMaintainerService(
            maintainerProperties());
        String name = randomServiceName("lost-publish-" + entry);
        addCleanup(() -> maintainer.a2a().deleteAgent(name));
        setup.releaseAgentCard(card(name, V1));
        Properties properties = sdkProperties();
        properties.setProperty(PropertyKeyConst.SERVER_ADDR,
            System.getProperty("nacos.ai.adaptation.lost-publish-address"));
        properties.setProperty(AiConstants.AI_AGENT_TRANSPORT_MODE, "http");
        AiService service = createAiServiceWithoutReadiness(properties);
        AgentPublishRequest request = publication(name);
        request.setBasedOnVersion(V1);
        request.setAutoSubmit(true);
        AgentCard next = card(name, V2);
        CheckedCall publish = () -> {
            if ("native".equals(entry)) {
                service.agent().publishAgent(request);
            } else {
                A2aService selected = "resource".equals(entry) ? service.agent() : service;
                selected.releaseAgentCard(next, "URL", true);
            }
        };
        NacosException failure = assertThrows(NacosException.class, publish::run);
        assertTrue(failure.getErrCode() != NacosException.SERVER_NOT_IMPLEMENTED);
        com.alibaba.nacos.api.ai.model.agent.AgentVersionDetail stored =
            maintainer.agent().getAgentVersion(name, V2);
        assertEquals("online", stored.getStatus());
        next.setDescription("explicit caller retry must not alter an online Version");
        request.setDisplayName("explicit retry must not replace this online definition");
        publish.run();
        assertEquals(stored.getContentDigest(),
            maintainer.agent().getAgentVersion(name, V2).getContentDigest());
        if ("native".equals(entry)) {
            assertEquals(V2, service.agent().discoverAgent(reference(name, V2)).getVersion());
        } else {
            assertEquals(V2, service.getAgentCard(name).getVersion());
        }
    }

    @Test
    @EnabledIfSystemProperty(named = "nacos.ai.adaptation.reconnect.enabled", matches = "true")
    void legacyInstancesRemainLegacyWhenAddressReconnectsToRadServer() throws Exception {
        Path control = Paths.get(System.getProperty("nacos.ai.adaptation.reconnect.control.dir"));
        Files.createDirectories(control);
        Files.deleteIfExists(control.resolve("old-ready"));
        Files.deleteIfExists(control.resolve("rad-ready"));
        String address = System.getProperty("nacos.ai.compatibility.old-server-address");
        List<AiService> existing = new ArrayList<>();
        List<AiService> radSelected = new ArrayList<>();
        List<String> names = new ArrayList<>();
        List<List<AgentEndpoint>> registrations = new ArrayList<>();
        List<AtomicReference<String>> observed = new ArrayList<>();
        for (String mode : Arrays.asList("grpc", "http", "auto")) {
            Properties properties = new Properties();
            properties.setProperty(PropertyKeyConst.SERVER_ADDR, address);
            properties.setProperty(AiConstants.AI_TRANSPORT_MODE, mode);
            AiService service = createAiServiceWithoutReadiness(properties);
            String name = randomServiceName("fixed-legacy-" + mode);
            service.releaseAgentCard(card(name, V1));
            assertTrue(service.getAgentCard(name, V1).isLatestVersion());
            service.releaseAgentCard(card(name, V2));
            List<AgentEndpoint> endpoints = Arrays.asList(endpoint(V1, randomPort()),
                endpoint(V2, randomPort()));
            for (AgentEndpoint endpoint : endpoints) {
                service.registerAgentEndpoint(name, endpoint);
            }
            registrations.add(endpoints);
            AtomicReference<String> description = new AtomicReference<>();
            AbstractNacosAgentCardListener listener = new AbstractNacosAgentCardListener() {
                @Override
                public void onEvent(NacosAgentCardEvent event) {
                    description.set(event.getAgentCard().getDescription());
                }
            };
            service.subscribeAgentCard(name, V1, listener);
            addCleanup(() -> service.unsubscribeAgentCard(name, V1, listener));
            existing.add(service);
            names.add(name);
            observed.add(description);
        }
        Files.write(control.resolve("old-ready"), Collections.singletonList("ready"));
        waitUntil("external harness replaces the old listener with a RAD-capable server",
            180000L, () -> Files.exists(control.resolve("rad-ready")));
        for (int index = 0; index < existing.size(); index++) {
            AiService legacy = existing.get(index);
            String name = names.get(index);
            waitUntil("old instance reconnects before issuing one publication", 30000L, () -> {
                try {
                    legacy.getAgentCard(name, V1);
                    return true;
                } catch (NacosException error) {
                    return error.getErrCode() == NacosException.NOT_FOUND
                        || error.getErrCode() == NacosException.RESOURCE_NOT_FOUND;
                }
            });
            AgentCard replacement = card(name, V1);
            replacement.setDescription("after reconnect");
            // Legacy polling observes Version/interface changes, not description-only edits.
            replacement.setUrl("http://127.0.0.1:19002/agent");
            legacy.releaseAgentCard(replacement);
            legacy.releaseAgentCard(card(name, V2));
            List<AgentEndpoint> endpoints = registrations.get(index);
            for (AgentEndpoint endpoint : endpoints) {
                waitUntil("same current SDK restores both exact-Version legacy publications",
                    30000L, () -> legacy.getAgentCard(name, endpoint.getVersion(), "SERVICE")
                        .getSupportedInterfaces().stream().anyMatch(value -> value.getUrl()
                            .contains(":" + endpoint.getPort() + "/")));
            }
            legacy.deregisterAgentEndpoint(name, endpoints.get(0));
            assertTrue(legacy.getAgentCard(name, V2, "SERVICE").getSupportedInterfaces().stream()
                .anyMatch(value -> value.getUrl().contains(":" + endpoints.get(1).getPort() + "/")));
            // The old server binding includes latestVersion for an exact read; the RAD
            // adapter deliberately omits it. This proves public routing without reflection.
            assertTrue(legacy.getAgentCard(name, V1).isLatestVersion());
            assertEquals(V1, legacy.agent().discoverAgent(reference(name, V1)).getVersion());
            AtomicReference<String> description = observed.get(index);
            waitUntil("the existing legacy subscription survives reconnect", 30000L,
                () -> "after reconnect".equals(description.get()));
            Properties properties = new Properties();
            properties.setProperty(PropertyKeyConst.SERVER_ADDR, address);
            properties.setProperty(AiConstants.AI_TRANSPORT_MODE,
                Arrays.asList("grpc", "http", "auto").get(index));
            AiService fresh = createAiServiceWithoutReadiness(properties);
            radSelected.add(fresh);
            assertNull(fresh.getAgentCard(name, V1).isLatestVersion());
            assertEquals(V1, fresh.agent().discoverAgent(reference(name, V1)).getVersion());
        }
        if (Boolean.getBoolean("nacos.ai.adaptation.reconnect.roundtrip")) {
            Files.write(control.resolve("rad-verified"), Collections.singletonList("ready"));
            waitUntil("external harness restores the released server at the same address",
                180000L, () -> Files.exists(control.resolve("old-restored")));
            for (int index = 0; index < existing.size(); index++) {
                AiService legacy = existing.get(index);
                String name = names.get(index);
                AgentEndpoint remaining = registrations.get(index).get(1);
                waitUntil("legacy instance restores its remaining intent after returning to old server",
                    30000L, () -> legacy.getAgentCard(name, V2, "SERVICE")
                        .getSupportedInterfaces().stream().anyMatch(value -> value.getUrl()
                            .contains(":" + remaining.getPort() + "/")));
                assertNotNull(legacy.getAgentCard(name, V1).isLatestVersion());
                AiService rad = radSelected.get(index);
                assertThrows(NacosException.class, () -> rad.getAgentCard(name, V1),
                    "a RAD-selected instance must not downgrade to a successful legacy read");
            }
        }
    }

    private void verifySignatures(String mode, boolean resource, String address, boolean rad)
        throws Exception {
        Properties properties = rad ? sdkProperties() : new Properties();
        properties.setProperty(PropertyKeyConst.SERVER_ADDR, address);
        properties.setProperty(AiConstants.AI_TRANSPORT_MODE, mode);
        AiService service = createAiServiceWithoutReadiness(properties);
        A2aService entry = resource ? service.agent() : service;
        Properties admin = rad ? maintainerProperties() : new Properties();
        admin.setProperty(PropertyKeyConst.SERVER_ADDR, rad
            ? System.getProperty("nacos.ai.adaptation.management-address", SERVER_ADDR) : address);
        AiMaintainerService maintainer = AiMaintainerFactory.createAiMaintainerService(admin);
        String name = randomServiceName("a2a-routing-" + mode);
        addCleanup(() -> maintainer.a2a().deleteAgent(name));
        AgentCard first = card(name, V1);
        // The default false must still publish a newly created first Version.
        entry.releaseAgentCard(first);
        assertEquals(V1, entry.getAgentCard(name).getVersion());
        assertEquals(V1, entry.getAgentCard(name, V1).getVersion());
        AgentCardDetailInfo exact = entry.getAgentCard(name, V1, "URL");
        if (rad) {
            assertNull(exact.isLatestVersion());
        } else {
            assertTrue(exact.isLatestVersion());
        }
        AtomicInteger callbacks = new AtomicInteger();
        AtomicBoolean nextVersionObserved = new AtomicBoolean();
        AbstractNacosAgentCardListener listener = new AbstractNacosAgentCardListener() {
            @Override
            public void onEvent(NacosAgentCardEvent event) {
                assertNotNull(event.getAgentCard());
                callbacks.incrementAndGet();
                if (V2.equals(event.getAgentCard().getVersion())) {
                    nextVersionObserved.set(true);
                }
            }
        };
        assertNotNull(entry.subscribeAgentCard(name, listener));
        assertNotNull(entry.subscribeAgentCard(name, V1, listener));
        waitUntil("both old subscription signatures deliver current Cards", () -> callbacks.get() >= 2);
        AgentCard second = card(name, V2);
        entry.releaseAgentCard(second, "URL");
        assertEquals(V1, entry.getAgentCard(name).getVersion());
        if (rad) {
            assertEquals("draft", maintainer.agent().getAgentVersion(name, V2).getStatus());
        }
        entry.releaseAgentCard(second, "URL", true);
        // Old A2A duplicate release is a no-op, so its exact Version remains readable even
        // if latest stays V1; the RAD draft is submitted by the third overload.
        assertEquals(V2, entry.getAgentCard(name, V2).getVersion());
        if (rad) {
            assertEquals(V2, entry.getAgentCard(name).getVersion());
        }
        if (rad) {
            waitUntil("legacy Watch observes published next Version", nextVersionObserved::get);
        }
        entry.unsubscribeAgentCard(name, listener);
        entry.unsubscribeAgentCard(name, V1, listener);
        int port = randomPort();
        entry.registerAgentEndpoint(name, V2, "127.0.0.1", port);
        entry.registerAgentEndpoint(name, V2, "127.0.0.1", port, "JSONRPC");
        entry.registerAgentEndpoint(name, V2, "127.0.0.1", port, "JSONRPC", "/agent");
        entry.registerAgentEndpoint(name, V2, "127.0.0.1", port, "JSONRPC", "/agent", true);
        AgentEndpoint one = endpoint(V2, port);
        AgentEndpoint two = endpoint(V2, randomPort());
        entry.registerAgentEndpoint(name, one);
        entry.registerAgentEndpoint(name, Arrays.asList(one, two));
        waitUntil("batch replaces single endpoint through selected binding", () ->
            entry.getAgentCard(name, V2, "SERVICE").getSupportedInterfaces().size() == 2);
        if (rad) {
            assertEquals(NacosException.CONFLICT, assertThrows(NacosException.class,
                () -> service.agent().registerAgentEndpoints(batch(name))).getErrCode());
            // One address belongs to two legacy exact intents; removing V1 preserves range.
            entry.registerAgentEndpoint(name, endpoint(V1, port));
            AgentReference reference = reference(name, V2);
            waitUntil("same-address registrations merge", () -> runtime(
                service.agent().discoverAgent(reference)).stream().anyMatch(e -> e.getBindings()
                    .stream().anyMatch(b -> "[1.0.0,1.2.0]".equals(b.getVersionRange()))));
            entry.deregisterAgentEndpoint(name, V1, "127.0.0.1", port);
            waitUntil("partial legacy removal preserves compatibility range", () -> runtime(
                service.agent().discoverAgent(reference)).stream().anyMatch(e -> e.getBindings()
                    .stream().anyMatch(b -> "[1.0.0,1.2.0]".equals(b.getVersionRange())
                        && V2.equals(b.getRuntimeVersion()))));
        } else {
            entry.deregisterAgentEndpoint(name, V2, "127.0.0.1", port);
        }
        entry.deregisterAgentEndpoint(name, two);
        if (rad) {
            verifyNative(service, name);
        } else {
            verifyUnsupportedNative(service, name);
        }
        // Local cancellation and repeat shutdown have no capability dependency.
        entry.unsubscribeAgentCard(name, listener);
        entry.unsubscribeAgentCard(name, V1, listener);
        service.shutdown();
        service.shutdown();
    }

    private void verifyNative(AiService service, String name) throws Exception {
        AgentPublishRequest publish = publication(name);
        assertEquals("online", service.agent().publishAgent(publish).getStatus());
        AgentSearchRequest search = new AgentSearchRequest();
        search.setAgentNameContains(name);
        waitUntil("Search index exposes native publication", 30000L,
            () -> !service.agent().searchAgents(search).getPageItems().isEmpty());
        AgentReference reference = reference(name, V2);
        AgentDiscoveryFilter filter = new AgentDiscoveryFilter();
        filter.setProtocols(Collections.singletonList("a2a"));
        assertEquals(V2, service.agent().discoverAgent(reference).getVersion());
        assertEquals(V2, service.agent().discoverAgent(reference, filter).getVersion());
        AgentEndpointRegistrationBatch batch = batch(name);
        String expectedUri = batch.getEndpoints().get(0).getUri();
        AtomicInteger changes = new AtomicInteger();
        AbstractNacosAgentDiscoveryListener listener = new AbstractNacosAgentDiscoveryListener() {
            @Override
            public void onEvent(NacosAgentDiscoveryEvent event) {
                if (event.getAgentDiscoveryResult() != null
                    && runtime(event.getAgentDiscoveryResult()).stream()
                        .anyMatch(endpoint -> expectedUri.equals(endpoint.getUri()))) {
                    changes.incrementAndGet();
                }
            }
        };
        assertNotNull(service.agent().subscribeAgent(reference, listener));
        assertNotNull(service.agent().subscribeAgent(reference, filter, listener));
        service.agent().registerAgentEndpoints(batch);
        // Polling compatibility uses the configured ten-second refresh interval.
        waitUntil("native subscriptions receive runtime updates", 30000L, () -> changes.get() >= 2);
        assertEquals(1, runtime(service.agent().discoverAgent(reference)).size());
        service.agent().deregisterAgentEndpoints(name, "a2a", batch.getEndpoints());
        waitUntil("native partial deregistration removes final endpoint", () ->
            runtime(service.agent().discoverAgent(reference)).isEmpty());
        service.agent().unsubscribeAgent(reference, listener);
        service.agent().unsubscribeAgent(reference, filter, listener);
    }

    private void verifyUnsupportedNative(AiService service, String name) throws Exception {
        AgentReference reference = reference(name, V2);
        AgentDiscoveryFilter filter = new AgentDiscoveryFilter();
        AbstractNacosAgentDiscoveryListener listener = new AbstractNacosAgentDiscoveryListener() {
            @Override
            public void onEvent(NacosAgentDiscoveryEvent event) { }
        };
        unsupported(() -> service.agent().publishAgent(publication(name)));
        unsupported(() -> service.agent().searchAgents(new AgentSearchRequest()));
        unsupported(() -> service.agent().discoverAgent(reference));
        unsupported(() -> service.agent().discoverAgent(reference, filter));
        unsupported(() -> service.agent().subscribeAgent(reference, listener));
        unsupported(() -> service.agent().subscribeAgent(reference, filter, listener));
        unsupported(() -> service.agent().registerAgentEndpoints(batch(name)));
        service.agent().unsubscribeAgent(reference, listener);
        service.agent().unsubscribeAgent(reference, filter, listener);
        service.agent().deregisterAgentEndpoints(name, "a2a", batch(name).getEndpoints());
    }

    private void unsupported(CheckedCall call) {
        assertEquals(NacosException.SERVER_NOT_IMPLEMENTED,
            assertThrows(NacosException.class, call::run).getErrCode());
    }

    private static AgentCard card(String name, String version) {
        AgentCard card = new AgentCard();
        card.setName(name);
        card.setVersion(version);
        card.setDescription("A2A routing compatibility");
        card.setCapabilities(new AgentCapabilities());
        card.setUrl("http://127.0.0.1:19001/agent");
        card.setProtocolVersion("0.3.0");
        card.setPreferredTransport("JSONRPC");
        return card;
    }

    private static AgentEndpoint endpoint(String version, int port) {
        AgentEndpoint endpoint = new AgentEndpoint();
        endpoint.setVersion(version);
        endpoint.setAddress("127.0.0.1");
        endpoint.setPort(port);
        endpoint.setTransport("JSONRPC");
        endpoint.setProtocolVersion("0.3.0");
        endpoint.setTenant("tenant-a");
        endpoint.setPath("/agent");
        return endpoint;
    }

    private AgentEndpointRegistrationBatch batch(String name) {
        AgentEndpointRegistrationBatch batch = new AgentEndpointRegistrationBatch();
        batch.setAgentName(name);
        batch.setProtocol("a2a");
        batch.setRuntimeVersion(V2);
        Endpoint endpoint = new Endpoint();
        endpoint.setTransport("JSONRPC");
        endpoint.setUri("http://127.0.0.1:" + randomPort() + "/native");
        batch.setEndpoints(Collections.singletonList(endpoint));
        return batch;
    }

    private static AgentPublishRequest publication(String name) {
        AgentPublishRequest request = new AgentPublishRequest();
        request.setAgentName(name);
        request.setVersion(V2);
        request.setBasedOnVersion(V2);
        return request;
    }

    private static AgentReference reference(String name, String version) {
        AgentReference reference = new AgentReference();
        reference.setAgentName(name);
        reference.setVersion(version);
        return reference;
    }

    private static List<Endpoint> runtime(AgentDiscoveryResult result) {
        List<Endpoint> endpoints = new ArrayList<>();
        for (EndpointSet set : result.getCallInterfaces().get(0).getEndpointSets()) {
            if (set.getSource() == EndpointSource.RUNTIME) {
                endpoints.addAll(set.getEndpoints());
            }
        }
        return endpoints;
    }

    private interface CheckedCall {
        void run() throws Exception;
    }
}
