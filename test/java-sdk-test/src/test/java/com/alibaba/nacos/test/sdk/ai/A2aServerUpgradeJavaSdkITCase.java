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
import com.alibaba.nacos.api.ability.constant.AbilityStatus;
import com.alibaba.nacos.api.ai.AiService;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.listener.AbstractNacosAgentCardListener;
import com.alibaba.nacos.api.ai.listener.AbstractNacosAgentDiscoveryListener;
import com.alibaba.nacos.api.ai.listener.NacosAgentCardEvent;
import com.alibaba.nacos.api.ai.listener.NacosAgentDiscoveryEvent;
import com.alibaba.nacos.api.ai.listener.NacosAgentDiscoveryEventType;
import com.alibaba.nacos.api.ai.model.a2a.AgentCapabilities;
import com.alibaba.nacos.api.ai.model.a2a.AgentCard;
import com.alibaba.nacos.api.ai.model.a2a.AgentEndpoint;
import com.alibaba.nacos.api.ai.model.agent.AgentCallInterface;
import com.alibaba.nacos.api.ai.model.agent.AgentDiscoveryResult;
import com.alibaba.nacos.api.ai.model.agent.AgentEndpointRegistrationBatch;
import com.alibaba.nacos.api.ai.model.agent.AgentReference;
import com.alibaba.nacos.api.ai.model.agent.Endpoint;
import com.alibaba.nacos.api.ai.model.agent.EndpointSet;
import com.alibaba.nacos.api.ai.model.agent.EndpointSource;
import com.alibaba.nacos.api.ai.model.agent.client.AgentPublishRequest;
import com.alibaba.nacos.api.utils.json.JsonUtils;
import com.alibaba.nacos.maintainer.client.ai.AiMaintainerFactory;
import com.alibaba.nacos.maintainer.client.ai.AiMaintainerService;
import com.alibaba.nacos.test.sdk.JavaSdkBaseITCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Server-first upgrade with persistent data and live clients, driven by an external harness.
 *
 * <p>Scenario coverage: UPG-01..06 in AGENT_SDK_CORE_REGRESSION_MATRIX.md. The current
 * SDK uses the legacy API against 3.2.3, keeps its original subscriptions across the
 * binary/data upgrade, then recreates AiService to select RAD. Every stage verifies
 * writes, reads and callbacks. Recreating AiService is a client lifecycle restart,
 * not a claim that a released SDK binary acquires new code or that the JVM restarts.</p>
 *
 * @author Nacos
 */
@EnabledIfSystemProperty(named = "nacos.ai.server-upgrade.enabled", matches = "true")
class A2aServerUpgradeJavaSdkITCase extends JavaSdkBaseITCase {

    private final List<Session> sessions = new ArrayList<>();

    @Test
    @Timeout(600)
    void shouldPreserveLegacyClientsThenUseRadAfterServerUpgrade() throws Exception {
        Path control = Paths.get(System.getProperty("nacos.ai.server-upgrade.control.dir"));
        Files.createDirectories(control);
        for (String mode : Arrays.asList("grpc", "http", "auto")) {
            Session session = new Session(mode, randomServiceName("server-upgrade-" + mode));
            sessions.add(session);
            prepareLegacy(session);
        }
        marker(control, "old-ready");
        waitUntil("external harness upgrades the persisted 3.2.3 data to 3.3", 240000L,
            () -> Files.exists(control.resolve("upgraded-ready")));
        for (Session session : sessions) {
            verifySurvivingLegacy(session);
            verifyRestartedFacade(session);
            verifyNative(session);
        }
        marker(control, "client-verified");
    }

    private void prepareLegacy(Session session) throws Exception {
        session.service = open(session.mode);
        AiMaintainerService maintainer = AiMaintainerFactory.createAiMaintainerService(sdkProperties());
        addCleanup(() -> maintainer.a2a().deleteAgent(session.name));
        session.service.releaseAgentCard(card(session.name, "1.0.0"), "URL", true);
        waitUntil("old server capability is known after the first A2A operation",
            () -> session.service.getAgentDiscoveryCapability() == AbilityStatus.NOT_SUPPORTED);
        session.service.subscribeAgentCard(session.name, session.listener);
        addCleanup(() -> session.service.unsubscribeAgentCard(session.name, session.listener));
        awaitCard(session, "1.0.0");
        session.service.releaseAgentCard(card(session.name, "2.0.0"), "URL", true);
        awaitCard(session, "2.0.0");
        assertEquals("1.0.0", session.service.getAgentCard(session.name, "1.0.0").getVersion());
        session.endpoints = Arrays.asList(endpoint("2.0.0"), endpoint("2.0.0"));
        session.service.registerAgentEndpoint(session.name, session.endpoints);
        awaitEndpoint(session, "2.0.0", session.endpoints.get(0), true);
        awaitEndpoint(session, "2.0.0", session.endpoints.get(1), true);
        session.service.deregisterAgentEndpoint(session.name, session.endpoints.get(0));
        awaitEndpoint(session, "2.0.0", session.endpoints.get(0), false);
        awaitEndpoint(session, "2.0.0", session.endpoints.get(1), false);
        session.service.registerAgentEndpoint(session.name, session.endpoints);
        awaitEndpoint(session, "2.0.0", session.endpoints.get(1), true);
    }

    private void verifySurvivingLegacy(Session session) throws Exception {
        // Read persisted content BEFORE any post-upgrade write: a fresh empty database
        // followed by republishing must not accidentally pass this upgrade scenario.
        waitUntil("historical definition survives the actual data upgrade", 60000L,
            () -> "persisted-2.0.0".equals(session.service.getAgentCard(
                session.name, "2.0.0").getDescription()));
        assertEquals("persisted-1.0.0",
            session.service.getAgentCard(session.name, "1.0.0").getDescription());
        awaitEndpoint(session, "2.0.0", session.endpoints.get(0), true);
        awaitEndpoint(session, "2.0.0", session.endpoints.get(1), true);
        waitUntil("same client refreshes current capability without changing its old A2A route",
            () -> session.service.getAgentDiscoveryCapability() == AbilityStatus.SUPPORTED);
        // The old query wire fills latestVersion; the RAD Card adapter leaves it null.
        // This checks public output, without reflecting into the SDK's mode selector.
        assertNotNull(session.service.getAgentCard(session.name, "2.0.0").isLatestVersion());
        session.service.releaseAgentCard(card(session.name, "3.0.0"), "URL", true);
        awaitCard(session, "3.0.0");
        assertEquals("3.0.0", session.service.getAgentCard(session.name).getVersion());
        AgentEndpoint added = endpoint("3.0.0");
        session.service.registerAgentEndpoint(session.name, added);
        awaitEndpoint(session, "3.0.0", added, true);
        session.service.deregisterAgentEndpoint(session.name, session.endpoints.get(0));
        awaitEndpoint(session, "2.0.0", session.endpoints.get(0), false);
        // Legacy deregistration removes the complete exact-Version publication,
        // even when its DTO carries one address. Other Versions must remain.
        awaitEndpoint(session, "2.0.0", session.endpoints.get(1), false);
        awaitEndpoint(session, "3.0.0", added, true);
        assertNotNull(session.service.getAgentCard(session.name, "3.0.0").isLatestVersion());
        System.out.println("UPGRADE " + session.mode + " persisted data and live legacy writes/listener passed");
    }

    private void verifyRestartedFacade(Session session) throws Exception {
        session.service.unsubscribeAgentCard(session.name, session.listener);
        session.service.shutdown();
        session.service = open(session.mode);
        waitUntil("fresh client observes RAD capability",
            () -> session.service.getAgentDiscoveryCapability() == AbilityStatus.SUPPORTED);
        assertNull(session.service.getAgentCard(session.name, "3.0.0").isLatestVersion());
        session.card.set(null);
        session.service.subscribeAgentCard(session.name, session.listener);
        awaitCard(session, "3.0.0");
        session.service.releaseAgentCard(card(session.name, "4.0.0"), "URL", true);
        awaitCard(session, "4.0.0");
        AgentEndpoint first = endpoint("4.0.0");
        AgentEndpoint second = endpoint("4.0.0");
        session.service.registerAgentEndpoint(session.name, Arrays.asList(first, second));
        awaitEndpoint(session, "4.0.0", first, true);
        session.service.registerAgentEndpoint(session.name, Collections.singletonList(second));
        awaitEndpoint(session, "4.0.0", first, false);
        awaitEndpoint(session, "4.0.0", second, true);
        session.service.deregisterAgentEndpoint(session.name, second);
        awaitEndpoint(session, "4.0.0", second, false);
        assertNull(session.service.getAgentCard(session.name, "4.0.0").isLatestVersion());
        System.out.println("UPGRADE " + session.mode + " recreated client RAD-backed A2A passed");
    }

    private void verifyNative(Session session) throws Exception {
        AgentReference reference = new AgentReference();
        reference.setAgentName(session.name);
        assertEquals("4.0.0", session.service.agent().discoverAgent(reference).getVersion());
        AtomicReference<AgentDiscoveryResult> latest = new AtomicReference<>();
        AtomicInteger unavailable = new AtomicInteger();
        AtomicInteger deliveries = new AtomicInteger();
        AbstractNacosAgentDiscoveryListener listener = new AbstractNacosAgentDiscoveryListener() {
            @Override
            public void onEvent(NacosAgentDiscoveryEvent event) {
                deliveries.incrementAndGet();
                if (event.getType() == NacosAgentDiscoveryEventType.UNAVAILABLE) {
                    unavailable.incrementAndGet();
                } else {
                    latest.set(event.getAgentDiscoveryResult());
                }
            }
        };
        session.service.agent().subscribeAgent(reference, listener);
        addCleanup(() -> session.service.agent().unsubscribeAgent(reference, listener));
        waitUntil("native Listener receives its initial state",
            () -> latest.get() != null && "4.0.0".equals(latest.get().getVersion()));
        AgentPublishRequest publish = new AgentPublishRequest();
        publish.setAgentName(session.name);
        publish.setVersion("5.0.0");
        publish.setCallInterfaces(Collections.singletonList(callInterface(session.name, "5.0.0")));
        publish.setAutoSubmit(true);
        assertEquals("online", session.service.agent().publishAgent(publish).getStatus());
        waitUntil("native Listener receives the new published version",
            () -> "5.0.0".equals(latest.get().getVersion()));
        awaitCard(session, "5.0.0");
        AgentEndpointRegistrationBatch batch = new AgentEndpointRegistrationBatch();
        batch.setAgentName(session.name);
        batch.setProtocol("a2a");
        batch.setRuntimeVersion("5.0.0");
        Endpoint first = nativeEndpoint();
        Endpoint replacement = nativeEndpoint();
        batch.setEndpoints(Collections.singletonList(first));
        session.service.agent().registerAgentEndpoints(batch);
        waitUntil("native registration reaches the original Listener",
            () -> hasRuntime(latest.get(), first.getUri()));
        batch.setEndpoints(Collections.singletonList(replacement));
        session.service.agent().registerAgentEndpoints(batch);
        waitUntil("native replacement removes the old endpoint and delivers the new one",
            () -> hasRuntime(latest.get(), replacement.getUri())
                && !hasRuntime(latest.get(), first.getUri()));
        session.service.agent().deregisterAgentEndpoints(session.name, "a2a", batch.getEndpoints());
        waitUntil("empty Runtime remains a complete snapshot",
            () -> !hasRuntime(latest.get(), replacement.getUri()));
        assertEquals(0, unavailable.get());
        session.service.agent().unsubscribeAgent(reference, listener);
        session.service.unsubscribeAgentCard(session.name, session.listener);
        int nativeCount = deliveries.get();
        int cardCount = session.deliveries.get();
        publish.setVersion("6.0.0");
        publish.setCallInterfaces(Collections.singletonList(callInterface(session.name, "6.0.0")));
        assertEquals("online", session.service.agent().publishAgent(publish).getStatus());
        assertEquals("6.0.0", session.service.agent().discoverAgent(reference).getVersion());
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(12);
        while (System.nanoTime() < deadline) {
            assertEquals(nativeCount, deliveries.get(), "canceled native Listener must stay canceled");
            assertEquals(cardCount, session.deliveries.get(), "canceled A2A Listener must stay canceled");
            Thread.sleep(100);
        }
        session.service.shutdown();
        System.out.println("UPGRADE " + session.mode + " native writes/discover/watch/cancellation passed");
    }

    private AiService open(String mode) throws Exception {
        Properties properties = sdkProperties();
        properties.setProperty(PropertyKeyConst.SERVER_ADDR, SERVER_ADDR);
        properties.setProperty(AiConstants.AI_TRANSPORT_MODE, mode);
        return createAiServiceWithoutReadiness(properties);
    }

    private void awaitCard(Session session, String version) throws Exception {
        waitUntil(session.mode + " original Card Listener receives " + version, 45000L,
            () -> session.card.get() != null && version.equals(session.card.get().getVersion()));
    }

    private void awaitEndpoint(Session session, String version, AgentEndpoint endpoint,
        boolean expected) throws Exception {
        waitUntil(session.mode + " SERVICE projection endpoint presence=" + expected, 60000L,
            () -> session.service.getAgentCard(session.name, version, "SERVICE")
                .getSupportedInterfaces().stream().anyMatch(value -> value.getUrl()
                    .contains(":" + endpoint.getPort() + "/")) == expected);
    }

    private AgentEndpoint endpoint(String version) {
        AgentEndpoint result = new AgentEndpoint();
        result.setVersion(version);
        result.setAddress("127.0.0.1");
        result.setPort(randomPort());
        result.setTransport("JSONRPC");
        result.setPath("/upgrade");
        return result;
    }

    private Endpoint nativeEndpoint() {
        Endpoint result = new Endpoint();
        result.setUri("http://127.0.0.1:" + randomPort() + "/native-upgrade");
        result.setTransport("JSONRPC");
        return result;
    }

    private boolean hasRuntime(AgentDiscoveryResult result, String uri) {
        return result.getCallInterfaces().stream().flatMap(value -> value.getEndpointSets().stream())
            .filter(value -> value.getSource() == EndpointSource.RUNTIME)
            .flatMap(value -> value.getEndpoints().stream()).anyMatch(value -> uri.equals(value.getUri()));
    }

    private AgentCallInterface callInterface(String name, String version) {
        AgentCard descriptor = card(name, version);
        AgentCallInterface result = new AgentCallInterface();
        result.setProtocol("a2a");
        result.setProtocolVersion(descriptor.getProtocolVersion());
        result.setDescriptorMediaType("application/json");
        // Native publication owns the complete protocol descriptor. Inheriting v4
        // verbatim would retain its Card.version and cannot be projected as A2A v5.
        result.setNativeDescriptor(JsonUtils.toObj(JsonUtils.toJson(descriptor), Map.class));
        result.setEndpointSourceOrder(Arrays.asList(EndpointSource.DECLARED, EndpointSource.RUNTIME));
        Endpoint declared = new Endpoint();
        declared.setUri(descriptor.getUrl());
        declared.setTransport("JSONRPC");
        EndpointSet set = new EndpointSet();
        set.setSource(EndpointSource.DECLARED);
        set.setEndpoints(Collections.singletonList(declared));
        result.setEndpointSets(Collections.singletonList(set));
        return result;
    }

    private AgentCard card(String name, String version) {
        AgentCard result = new AgentCard();
        result.setName(name);
        result.setVersion(version);
        result.setDescription("persisted-" + version);
        result.setUrl("http://127.0.0.1:19001/upgrade/" + version);
        result.setPreferredTransport("JSONRPC");
        result.setProtocolVersion("0.3.0");
        result.setCapabilities(new AgentCapabilities());
        return result;
    }

    private void marker(Path directory, String name) throws Exception {
        Files.write(directory.resolve(name), "ready".getBytes(StandardCharsets.UTF_8));
    }

    private static final class Session {
        private final String mode;
        private final String name;
        private final AtomicReference<AgentCard> card = new AtomicReference<>();
        private final AtomicInteger deliveries = new AtomicInteger();
        private final AbstractNacosAgentCardListener listener = new AbstractNacosAgentCardListener() {
            @Override
            public void onEvent(NacosAgentCardEvent event) {
                deliveries.incrementAndGet();
                card.set(event.getAgentCard());
            }
        };
        private AiService service;
        private List<AgentEndpoint> endpoints;

        private Session(String mode, String name) {
            this.mode = mode;
            this.name = name;
        }
    }
}
