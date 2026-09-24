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

import com.alibaba.nacos.api.ai.model.agent.EndpointSet;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.ai.AiService;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.listener.AbstractNacosAgentCardListener;
import com.alibaba.nacos.api.ai.listener.AbstractNacosAgentDiscoveryListener;
import com.alibaba.nacos.api.ai.listener.NacosAgentCardEvent;
import com.alibaba.nacos.api.ai.listener.NacosAgentDiscoveryEvent;
import com.alibaba.nacos.api.ai.listener.NacosAgentDiscoveryEventType;
import com.alibaba.nacos.api.ai.model.a2a.AgentCapabilities;
import com.alibaba.nacos.api.ai.model.a2a.AgentCard;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardDetailInfo;
import com.alibaba.nacos.api.ai.model.a2a.AgentEndpoint;
import com.alibaba.nacos.api.ai.model.a2a.AgentInterface;
import com.alibaba.nacos.api.ai.model.agent.AgentCallInterface;
import com.alibaba.nacos.api.ai.model.agent.AgentProvider;
import com.alibaba.nacos.api.ai.model.agent.client.AgentPublishRequest;
import com.alibaba.nacos.api.ai.model.agent.admin.AgentVersionRequest;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionDetail;
import com.alibaba.nacos.api.ai.model.agent.Endpoint;
import com.alibaba.nacos.api.ai.model.agent.EndpointSource;
import com.alibaba.nacos.api.ai.model.agent.AgentDiscoveryResult;
import com.alibaba.nacos.api.ai.model.agent.AgentReference;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.maintainer.client.ai.AgentMaintainerService;
import com.alibaba.nacos.maintainer.client.ai.AiMaintainerFactory;
import com.alibaba.nacos.test.sdk.JavaSdkBaseITCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.alibaba.nacos.api.ai.model.agent.admin.AgentDraftCreateRequest;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Standalone integration scenarios for Client Agent definition publication.
 *
 * <p>Scenario coverage:
 * <ul>
 *     <li>Expected capability: first-Version ordinary submit, complete draft replacement,
 *     later-Version flag handling, non-draft no-op, and management/discovery projections.</li>
 *     <li>Boundary/validation: direct or inherited content, namespace isolation, caller immutability,
 *     independent Endpoint registration and unchanged management draft creation.</li>
 *     <li>Exception/error handling: invalid identities and content sources are controlled errors;
 *     offline no-op preserves immutable content. Fault injection and write races are covered by
 *     domain/transport and real transactional unit tests.</li>
 * </ul>
 *
 * @author Nacos
 */
class AgentPublishJavaSdkITCase extends JavaSdkBaseITCase {

    private static final String VERSION_ONE = "1.0.0";

    private static final String VERSION_TWO = "2.0.0";

    private static final String VERSION_THREE = "3.0.0";

    private static final String VERSION_FOUR = "4.0.0";

    private static final long POLLING_TIMEOUT_SECONDS = 25L;

    @Test
    void shouldDiscoverDefaultPublicAndPreservePrivateOnPublishRetry() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(AUTH_ENABLED);
        AgentMaintainerService maintainer = createAgentMaintainerService();
        for (String transport : new String[] {"HTTP", "GRPC"}) {
            Properties publisherProperties = sdkProperties();
            publisherProperties.setProperty(AiConstants.AI_AGENT_TRANSPORT_MODE, transport);
            Properties readerProperties = sdkProperties(AuthIdentity.CLIENT_READ_ONLY);
            readerProperties.setProperty(AiConstants.AI_AGENT_TRANSPORT_MODE, transport);
            AiService publisher = createAiService(publisherProperties);
            AiService reader = createAiService(readerProperties);
            String name = randomServiceName("agent-scope-"
                    + transport.toLowerCase(java.util.Locale.ROOT));
            AgentPublishRequest request = initialRequest(name, VERSION_ONE, "scope", true);
            addCleanup(() -> maintainer.deleteAgent(name));
            AgentVersionDetail online = publisher.agent().publishAgent(request);
            assertEquals("PUBLIC", maintainer.getAgent(name).getAgent().getScope());
            assertEquals(online.getContentDigest(),
                    reader.agent().discoverAgent(reference(name, null)).getContentDigest());
            assertTrue(maintainer.updateScope(name, "PRIVATE"));
            assertNotFound(() -> reader.agent().discoverAgent(reference(name, null)));
            assertEquals("online", publisher.agent().publishAgent(request).getStatus());
            assertEquals("PRIVATE", maintainer.getAgent(name).getAgent().getScope());
            assertTrue(maintainer.updateScope(name, "PUBLIC"));
            assertEquals(online.getContentDigest(),
                    reader.agent().discoverAgent(reference(name, null)).getContentDigest());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"HTTP", "GRPC"})
    void shouldAuthorizeNonDraftNoopBeforeReturningContent(String transport) throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(AUTH_ENABLED);
        AgentMaintainerService maintainer = createAgentMaintainerService();
        Properties properties = sdkProperties();
        properties.setProperty(AiConstants.AI_AGENT_TRANSPORT_MODE, transport);
        AiService publisher = createAiService(properties);
        String name = randomServiceName("publish-noop-auth");
        addCleanup(() -> maintainer.deleteAgent(name));
        AgentPublishRequest request = initialRequest(name, VERSION_ONE, "original", false);
        AgentVersionDetail original = publisher.agent().publishAgent(request);
        for (AuthIdentity identity : new AuthIdentity[] {AuthIdentity.CLIENT_READ_ONLY,
                AuthIdentity.CLIENT_NO_PERMISSION}) {
            Properties deniedProperties = sdkProperties(identity);
            deniedProperties.setProperty(AiConstants.AI_AGENT_TRANSPORT_MODE, transport);
            AiService denied = createAiServiceWithoutReadiness(deniedProperties);
            NacosException failure = assertThrows(NacosException.class,
                    () -> denied.agent().publishAgent(request));
            assertEquals(NacosException.NO_RIGHT, failure.getErrCode());
        }
        AgentVersionDetail unchanged = maintainer.getAgentVersion(Constants.DEFAULT_NAMESPACE_ID,
                name, VERSION_ONE);
        assertEquals(original.getContentDigest(), unchanged.getContentDigest());
        assertEquals("online", unchanged.getStatus());
    }

    @Test
    void shouldInvalidateWatchAfterScopeBecomesPrivate() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(AUTH_ENABLED);
        AgentMaintainerService maintainer = createAgentMaintainerService();
        for (String transport : new String[] {"HTTP", "GRPC"}) {
            Properties readerProperties = sdkProperties(AuthIdentity.CLIENT_READ_ONLY);
            readerProperties.setProperty(AiConstants.AI_AGENT_TRANSPORT_MODE, transport);
            AiService publisher = createAiService();
            AiService reader = createAiService(readerProperties);
            String name = randomServiceName("agent-scope-watch");
            addCleanup(() -> maintainer.deleteAgent(name));
            publisher.agent().publishAgent(initialRequest(name, VERSION_ONE, "scope", true));
            CountDownLatch snapshotReceived = new CountDownLatch(1);
            CountDownLatch unavailableReceived = new CountDownLatch(1);
            AtomicReference<NacosAgentDiscoveryEvent> unavailable = new AtomicReference<>();
            AbstractNacosAgentDiscoveryListener listener =
                    new AbstractNacosAgentDiscoveryListener() {
                        @Override
                        public void onEvent(NacosAgentDiscoveryEvent event) {
                            if (event.getType() == NacosAgentDiscoveryEventType.SNAPSHOT) {
                                snapshotReceived.countDown();
                            } else {
                                unavailable.set(event);
                                unavailableReceived.countDown();
                            }
                        }
                    };
            AgentDiscoveryResult initial = reader.agent().subscribeAgent(reference(name, null), listener);
            if (initial != null) {
                snapshotReceived.countDown();
            }
            addCleanup(() -> reader.agent().unsubscribeAgent(reference(name, null), listener));
            assertTrue(snapshotReceived.await(25, TimeUnit.SECONDS),
                    transport + " initial Watch snapshot");
            assertTrue(maintainer.updateScope(name, "PRIVATE"));
            assertTrue(unavailableReceived.await(25, TimeUnit.SECONDS),
                    transport + " private scope Watch invalidation");
            assertEquals(Integer.valueOf("GRPC".equals(transport)
                            ? NacosException.RESOURCE_NOT_FOUND : NacosException.NOT_FOUND),
                    unavailable.get().getErrorCode());
            assertNull(unavailable.get().getAgentDiscoveryResult());
            reader.agent().unsubscribeAgent(reference(name, null), listener);
        }
    }

    @Test
    void shouldSubmitFirstVersionAndNoopAcrossCanonicalAndLegacyReads() throws Exception {
        AgentMaintainerService maintainer = createAgentMaintainerService();
        AiService service = createAiService();
        String agentName = randomServiceName("agent-code-publish");
        AgentPublishRequest request = initialRequest(agentName, VERSION_ONE, "initial", false);
        String callerSnapshot = JacksonUtils.toJson(request);
        addCleanup(() -> maintainer.deleteAgent(Constants.DEFAULT_NAMESPACE_ID, agentName));

        AgentVersionDetail draft = service.agent().publishAgent(request);
        assertEquals("online", draft.getStatus(), draft.toString());
        assertEquals(Constants.DEFAULT_NAMESPACE_ID, draft.getNamespaceId(), draft.toString());
        assertEquals(callerSnapshot, JacksonUtils.toJson(request),
                "the SDK must not mutate the caller-owned request");
        assertEquals(draft.getContentDigest(), service.agent().publishAgent(request).getContentDigest());

        request.setAutoSubmit(true);
        AgentVersionDetail online = service.agent().publishAgent(request);
        assertEquals("online", online.getStatus(), online.toString());
        assertEquals(draft.getContentDigest(), online.getContentDigest(), online.toString());
        assertEquals("online", service.agent().publishAgent(request).getStatus());
        assertEquals(online.getContentDigest(), maintainer.getAgentVersion(
                Constants.DEFAULT_NAMESPACE_ID, agentName, VERSION_ONE).getContentDigest());

        AgentDiscoveryResult discovery = service.agent().discoverAgent(reference(agentName, null));
        assertEquals(VERSION_ONE, discovery.getVersion(), discovery.toString());
        assertEquals(online.getContentDigest(), discovery.getContentDigest(),
                discovery.toString());
        assertTrue(discovery.getCallInterfaces().get(0).getEndpointSets().stream()
                .filter(each -> EndpointSource.RUNTIME == each.getSource())
                .allMatch(each -> each.getEndpoints().isEmpty()), discovery.toString());
        assertTrue(maintainer.getRuntimeEndpoints(Constants.DEFAULT_NAMESPACE_ID, agentName,
                "a2a", VERSION_ONE).getCallInterface().getEndpointSets().get(0).getEndpoints().isEmpty());

        AgentCardDetailInfo legacy = service.getAgentCard(agentName, VERSION_ONE,
                AiConstants.A2a.A2A_ENDPOINT_TYPE_URL);
        assertEquals(agentName, legacy.getName(), legacy.toString());
        assertEquals(VERSION_ONE, legacy.getVersion(), legacy.toString());
        assertEquals(2, legacy.getSupportedInterfaces().size(), legacy.toString());

        AgentPublishRequest draftOnlyRetry = initialRequest(agentName, VERSION_ONE,
                "initial", false);
        assertEquals(online.getContentDigest(), service.agent().publishAgent(draftOnlyRetry).getContentDigest());
        AgentPublishRequest contentConflict = initialRequest(agentName, VERSION_ONE,
                "different-content", true);
        assertEquals(online.getContentDigest(), service.agent().publishAgent(contentConflict).getContentDigest());
        AgentPublishRequest metadataConflict = initialRequest(agentName, VERSION_ONE,
                "initial", true);
        metadataConflict.setDescription("different initial metadata");
        assertEquals(online.getContentDigest(), service.agent().publishAgent(metadataConflict).getContentDigest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"HTTP", "GRPC", "AUTO"})
    void shouldReplaceDraftsCompletelyAndOnlyForceSubmitNewFirstVersion(String transport) throws Exception {
        AgentMaintainerService maintainer = createAgentMaintainerService();
        AiService service = createAiService(Constants.DEFAULT_NAMESPACE_ID, transport);
        String name = randomServiceName("client-publish-state");
        addCleanup(() -> maintainer.deleteAgent(Constants.DEFAULT_NAMESPACE_ID, name));
        AgentPublishRequest original = initialRequest(name, VERSION_ONE, "admin", false);
        AgentCallInterface extra = callInterface(name, VERSION_ONE, "extra");
        extra.setProtocol("custom");
        AgentDraftCreateRequest admin = new AgentDraftCreateRequest();
        admin.setAgentName(name);
        admin.setVersion(VERSION_ONE);
        admin.setCallInterfaces(Arrays.asList(original.getCallInterfaces().get(0), extra));
        admin.setAuthor("admin-author");
        assertEquals("draft", maintainer.createDraft(Constants.DEFAULT_NAMESPACE_ID, admin).getStatus());
        grantClientReadWriteVisibility(Constants.DEFAULT_NAMESPACE_ID, "agent", name);
        String owner = maintainer.getAgent(name).getAgent().getOwner();
        String scope = maintainer.getAgent(name).getAgent().getScope();
        AgentPublishRequest replacement = initialRequest(name, VERSION_ONE, "replacement", false);
        replacement.setAuthor("client-author");
        String caller = JacksonUtils.toJson(replacement);
        AgentVersionDetail changed = service.agent().publishAgent(replacement);
        assertEquals("draft", changed.getStatus());
        assertEquals(1, changed.getCallInterfaces().size());
        assertEquals("client-author", changed.getAuthor());
        assertEquals(owner, maintainer.getAgent(name).getAgent().getOwner());
        assertEquals(scope, maintainer.getAgent(name).getAgent().getScope());
        assertEquals(caller, JacksonUtils.toJson(replacement));
        assertNotFound(() -> service.agent().discoverAgent(reference(name, null)));
        replacement.setAutoSubmit(true);
        assertEquals("online", service.agent().publishAgent(replacement).getStatus());
        AgentPublishRequest next = versionRequest(name, VERSION_TWO, "next", false);
        assertEquals("draft", service.agent().publishAgent(next).getStatus());
        assertEquals(VERSION_ONE, service.agent().discoverAgent(reference(name, null)).getVersion());
        AgentPublishRequest copy = inheritedRequest(name, VERSION_TWO, VERSION_ONE, false);
        assertEquals(changed.getContentDigest(), service.agent().publishAgent(copy).getContentDigest());
        copy.setAutoSubmit(true);
        assertEquals("online", service.agent().publishAgent(copy).getStatus());
        assertEquals(VERSION_TWO, service.agent().discoverAgent(reference(name, null)).getVersion());
        maintainer.offline(Constants.DEFAULT_NAMESPACE_ID, versionCommand(name, VERSION_TWO));
        AgentVersionDetail offline = service.agent().publishAgent(next);
        assertEquals("offline", offline.getStatus());
        assertEquals(changed.getContentDigest(), offline.getContentDigest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"HTTP", "GRPC", "AUTO"})
    void shouldForceSubmitAfterDeletingAndExplicitlyRecreatingAgent(String transport) throws Exception {
        AgentMaintainerService maintainer = createAgentMaintainerService();
        AiService service = createAiService(Constants.DEFAULT_NAMESPACE_ID, transport);
        String name = randomServiceName("client-publish-recreate");
        addCleanup(() -> maintainer.deleteAgent(Constants.DEFAULT_NAMESPACE_ID, name));
        assertEquals("online", service.agent().publishAgent(
                initialRequest(name, VERSION_ONE, "original", false)).getStatus());
        maintainer.deleteAgent(Constants.DEFAULT_NAMESPACE_ID, name);
        AgentPublishRequest recreated = initialRequest(name, VERSION_ONE, "recreated", false);
        assertEquals("online", service.agent().publishAgent(recreated).getStatus());
        assertFalse(recreated.isAutoSubmit());
    }

    @Test
    void shouldKeepGrpcHttpAndNamespacePublicationParity() throws Exception {
        AgentMaintainerService maintainer = createAgentMaintainerService();
        AiService grpc = createAiService();
        AiService http = createAiService(Constants.DEFAULT_NAMESPACE_ID,
                AiConstants.AI_TRANSPORT_MODE_HTTP);
        String grpcAgent = randomServiceName("agent-publish-grpc");
        String httpAgent = randomServiceName("agent-publish-http");
        addCleanup(() -> maintainer.deleteAgent(Constants.DEFAULT_NAMESPACE_ID, grpcAgent));
        addCleanup(() -> maintainer.deleteAgent(Constants.DEFAULT_NAMESPACE_ID, httpAgent));

        AgentPublishRequest grpcRequest = initialRequest(grpcAgent, VERSION_ONE,
                "grpc", true);
        AgentVersionDetail grpcPublished = grpc.agent().publishAgent(grpcRequest);
        assertEquals("online", grpcPublished.getStatus(), grpcPublished.toString());
        assertEquals(VERSION_ONE, http.agent().discoverAgent(reference(grpcAgent, null)).getVersion());
        assertEquals(grpcPublished.getContentDigest(), http.agent().publishAgent(grpcRequest)
                .getContentDigest());

        AgentPublishRequest httpRequest = initialRequest(httpAgent, VERSION_ONE,
                "http", true);
        AgentVersionDetail httpPublished = http.agent().publishAgent(httpRequest);
        assertEquals("online", httpPublished.getStatus(), httpPublished.toString());
        assertEquals(VERSION_ONE, grpc.agent().discoverAgent(reference(httpAgent, null)).getVersion());
        assertEquals(httpPublished.getContentDigest(), grpc.agent().publishAgent(httpRequest)
                .getContentDigest());

        String namespaceId = randomServiceName("agent-publish-namespace");
        String customAgent = randomServiceName("agent-publish-custom");
        AiService custom = createAiService(namespaceId, AiConstants.AI_TRANSPORT_MODE_HTTP);
        addCleanup(() -> maintainer.deleteAgent(namespaceId, customAgent));
        AgentPublishRequest customRequest = initialRequest(customAgent, VERSION_ONE,
                "custom", true);
        AgentVersionDetail customPublished = custom.agent().publishAgent(customRequest);
        assertEquals(namespaceId, customPublished.getNamespaceId(), customPublished.toString());
        assertEquals(VERSION_ONE, custom.agent().discoverAgent(reference(customAgent, null)).getVersion());
        assertNotFound(() -> grpc.agent().discoverAgent(reference(customAgent, null)));
    }

    @Test
    void shouldPublishDirectAndInheritedVersionsAndRejectInvalidStates() throws Exception {
        AgentMaintainerService maintainer = createAgentMaintainerService();
        AiService grpc = createAiService();
        AiService http = createAiService(Constants.DEFAULT_NAMESPACE_ID,
                AiConstants.AI_TRANSPORT_MODE_HTTP);
        String agentName = randomServiceName("agent-publish-versions");
        addCleanup(() -> maintainer.deleteAgent(Constants.DEFAULT_NAMESPACE_ID, agentName));

        AgentVersionDetail first = grpc.agent().publishAgent(
                initialRequest(agentName, VERSION_ONE, "one", true));
        AgentPublishRequest secondRequest = versionRequest(agentName, VERSION_TWO,
                "two", true);
        AgentVersionDetail second = http.agent().publishAgent(secondRequest);
        assertFalse(first.getContentDigest().equals(second.getContentDigest()));

        AgentPublishRequest inherited = inheritedRequest(agentName, VERSION_THREE,
                VERSION_TWO, true);
        AgentVersionDetail third = grpc.agent().publishAgent(inherited);
        assertEquals(second.getContentDigest(), third.getContentDigest(), third.toString());
        assertEquals(VERSION_THREE, http.agent().discoverAgent(reference(agentName, null)).getVersion());

        AgentPublishRequest both = inheritedRequest(agentName, VERSION_FOUR, VERSION_TWO, false);
        both.setCallInterfaces(Collections.singletonList(callInterface(agentName,
                VERSION_FOUR, "both")));
        assertError(NacosException.INVALID_PARAM, () -> grpc.agent().publishAgent(both));

        AgentPublishRequest neither = new AgentPublishRequest();
        neither.setAgentName(agentName);
        neither.setVersion(VERSION_FOUR);
        assertError(NacosException.INVALID_PARAM, () -> grpc.agent().publishAgent(neither));

        AgentPublishRequest firstInheritance = inheritedRequest(
                randomServiceName("agent-publish-first-inherit"), VERSION_ONE,
                VERSION_TWO, false);
        assertError(NacosException.INVALID_PARAM, () -> grpc.agent().publishAgent(firstInheritance));

        AgentPublishRequest changedAuthor = versionRequest(agentName, VERSION_TWO,
                "two", true);
        changedAuthor.setAuthor("different-author");
        assertEquals(second.getAuthor(), grpc.agent().publishAgent(changedAuthor).getAuthor());

        AgentPublishRequest falseAgainstOnline = versionRequest(agentName, VERSION_TWO,
                "two", false);
        assertEquals(second.getContentDigest(), grpc.agent().publishAgent(falseAgainstOnline).getContentDigest());

        maintainer.offline(Constants.DEFAULT_NAMESPACE_ID,
                versionCommand(agentName, VERSION_TWO));
        assertEquals("offline", http.agent().publishAgent(secondRequest).getStatus());
    }

    @Test
    void shouldPreRegisterTwoLegacyEndpointVersionsBeforeDefinition() throws Exception {
        AgentMaintainerService maintainer = createAgentMaintainerService();
        AiService service = createAiService();
        String agentName = randomServiceName("agent-legacy-pre-register");
        AgentEndpoint firstEndpoint = legacyEndpoint(VERSION_ONE, "/legacy-v1");
        AgentEndpoint secondEndpoint = legacyEndpoint(VERSION_TWO, "/legacy-v2");
        addCleanup(() -> maintainer.deleteAgent(Constants.DEFAULT_NAMESPACE_ID, agentName));
        service.registerAgentEndpoint(agentName, firstEndpoint);
        service.registerAgentEndpoint(agentName, secondEndpoint);
        addCleanup(() -> service.deregisterAgentEndpoint(agentName, firstEndpoint));
        addCleanup(() -> service.deregisterAgentEndpoint(agentName, secondEndpoint));

        assertNotFound(() -> service.getAgentCard(agentName));
        service.agent().publishAgent(initialRequest(agentName, VERSION_ONE, "one", true));
        waitUntil("Version 1 pre-registered Endpoint should become visible", () ->
                containsLegacyEndpoint(service.getAgentCard(agentName, VERSION_ONE,
                        AiConstants.A2a.A2A_ENDPOINT_TYPE_SERVICE), firstEndpoint));

        service.agent().publishAgent(versionRequest(agentName, VERSION_TWO, "two", true));
        waitUntil("Version 2 pre-registered Endpoint should become visible", () ->
                containsLegacyEndpoint(service.getAgentCard(agentName, VERSION_TWO,
                        AiConstants.A2a.A2A_ENDPOINT_TYPE_SERVICE), secondEndpoint));
        assertTrue(containsLegacyEndpoint(service.getAgentCard(agentName, VERSION_ONE,
                AiConstants.A2a.A2A_ENDPOINT_TYPE_SERVICE), firstEndpoint));
    }

    @Test
    void shouldRouteExactLatestAndRestartPollingAfterCachedResubscribe() throws Exception {
        AgentMaintainerService maintainer = createAgentMaintainerService();
        AiService observer = createAiService();
        AiService publisher = createAiService();
        String agentName = randomServiceName("agent-card-routing");
        addCleanup(() -> maintainer.deleteAgent(Constants.DEFAULT_NAMESPACE_ID, agentName));

        VersionListener exactOne = new VersionListener(VERSION_ONE);
        assertNull(observer.subscribeAgentCard(agentName, VERSION_ONE, exactOne));
        addCleanup(() -> observer.unsubscribeAgentCard(agentName, VERSION_ONE, exactOne));
        publisher.agent().publishAgent(initialRequest(agentName, VERSION_ONE, "one", true));
        assertTrue(exactOne.latch.await(POLLING_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                "an exact subscription must receive a Version that is also latest");

        VersionListener latestTwo = new VersionListener(VERSION_TWO);
        assertEquals(VERSION_ONE,
                observer.subscribeAgentCard(agentName, latestTwo).getVersion());
        addCleanup(() -> observer.unsubscribeAgentCard(agentName, latestTwo));
        publisher.agent().publishAgent(versionRequest(agentName, VERSION_TWO, "two", true));
        assertTrue(latestTwo.latch.await(POLLING_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                "latest subscription must move to Version 2");

        VersionListener latestThree = new VersionListener(VERSION_THREE);
        observer.unsubscribeAgentCard(agentName, latestTwo);
        assertEquals(VERSION_TWO,
                observer.subscribeAgentCard(agentName, latestThree).getVersion());
        addCleanup(() -> observer.unsubscribeAgentCard(agentName, latestThree));
        publisher.agent().publishAgent(versionRequest(agentName, VERSION_THREE, "three", true));
        assertTrue(latestThree.latch.await(POLLING_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                "latest subscription must move to Version 3");

        VersionListener latestMovesBack = new VersionListener(VERSION_TWO);
        observer.unsubscribeAgentCard(agentName, latestThree);
        assertEquals(VERSION_THREE,
                observer.subscribeAgentCard(agentName, latestMovesBack).getVersion());
        addCleanup(() -> observer.unsubscribeAgentCard(agentName, latestMovesBack));
        maintainer.offline(Constants.DEFAULT_NAMESPACE_ID,
                versionCommand(agentName, VERSION_THREE));
        assertTrue(latestMovesBack.latch.await(POLLING_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                "latest must move to an exact Version that is already cached");

        observer.unsubscribeAgentCard(agentName, latestMovesBack);
        VersionListener resubscribed = new VersionListener(VERSION_FOUR);
        assertEquals(VERSION_TWO,
                observer.subscribeAgentCard(agentName, resubscribed).getVersion());
        addCleanup(() -> observer.unsubscribeAgentCard(agentName, resubscribed));
        publisher.agent().publishAgent(versionRequest(agentName, VERSION_FOUR, "four", true));
        assertTrue(resubscribed.latch.await(POLLING_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                "a cache-hit resubscribe must restart polling");
        assertNotNull(resubscribed.card.get());
    }

    private AgentPublishRequest initialRequest(String agentName, String version, String marker,
            boolean autoSubmit) {
        AgentPublishRequest result = versionRequest(agentName, version, marker, autoSubmit);
        result.setDisplayName("Display " + agentName);
        result.setDescription("Code-first Agent " + agentName);
        result.setIconUrl("https://example.com/" + agentName + "/icon.png");
        AgentProvider provider = new AgentProvider();
        provider.setName("Nacos Java SDK IT");
        provider.setUrl("https://nacos.io");
        result.setProvider(provider);
        result.setTags(Arrays.asList("java-sdk-it", marker));
        result.setExtensions(Collections.<String, Object>singletonMap("marker", marker));
        return result;
    }

    private AgentPublishRequest versionRequest(String agentName, String version, String marker,
            boolean autoSubmit) {
        AgentPublishRequest result = new AgentPublishRequest();
        result.setAgentName(agentName);
        result.setVersion(version);
        result.setCallInterfaces(Collections.singletonList(
                callInterface(agentName, version, marker)));
        result.setAuthor("java-sdk-it");
        result.setChangeDescription("publish " + version);
        result.setAutoSubmit(autoSubmit);
        return result;
    }

    private AgentPublishRequest inheritedRequest(String agentName, String version,
            String basedOnVersion, boolean autoSubmit) {
        AgentPublishRequest result = new AgentPublishRequest();
        result.setAgentName(agentName);
        result.setVersion(version);
        result.setBasedOnVersion(basedOnVersion);
        result.setAuthor("java-sdk-it");
        result.setChangeDescription("inherit " + basedOnVersion);
        result.setAutoSubmit(autoSubmit);
        return result;
    }

    private AgentCallInterface callInterface(String agentName, String version, String marker) {
        AgentInterface jsonRpc = new AgentInterface();
        jsonRpc.setUrl("https://example.com/" + agentName + "/jsonrpc");
        jsonRpc.setProtocolBinding("HTTP+JSON");
        jsonRpc.setProtocolVersion("1.0");
        AgentInterface grpc = new AgentInterface();
        grpc.setUrl("https://example.com/" + agentName + "/grpc");
        grpc.setProtocolBinding("GRPC");
        grpc.setProtocolVersion("1.0");
        AgentCapabilities capabilities = new AgentCapabilities();
        capabilities.setStreaming(Boolean.TRUE);
        AgentCard card = new AgentCard();
        card.setName(agentName);
        card.setVersion(version);
        card.setDescription(marker);
        card.setSupportedInterfaces(Arrays.asList(jsonRpc, grpc));
        card.setCapabilities(capabilities);

        Endpoint jsonRpcEndpoint = new Endpoint();
        jsonRpcEndpoint.setUri(jsonRpc.getUrl());
        jsonRpcEndpoint.setTransport(jsonRpc.getProtocolBinding());
        Endpoint grpcEndpoint = new Endpoint();
        grpcEndpoint.setUri(grpc.getUrl());
        grpcEndpoint.setTransport(grpc.getProtocolBinding());
        AgentCallInterface result = new AgentCallInterface();
        result.setProtocol("a2a");
        result.setProtocolVersion("1.0");
        result.setDescriptorMediaType("application/json");
        result.setNativeDescriptor(JacksonUtils.toObj(JacksonUtils.toJson(card), Map.class));
        result.setEndpointSourceOrder(Arrays.asList(EndpointSource.DECLARED,
                EndpointSource.RUNTIME));

        EndpointSet declaredSet1 = new EndpointSet();
        declaredSet1.setSource(EndpointSource.DECLARED);
        declaredSet1.setEndpoints(Arrays.asList(jsonRpcEndpoint, grpcEndpoint));
        result.setEndpointSets(Collections.singletonList(declaredSet1));
        return result;
    }

    private AgentEndpoint legacyEndpoint(String version, String path) {
        AgentEndpoint result = new AgentEndpoint();
        result.setVersion(version);
        result.setAddress("127.0.0.1");
        result.setPort(randomPort());
        result.setTransport(AiConstants.A2a.A2A_ENDPOINT_DEFAULT_TRANSPORT);
        result.setProtocolVersion("1.0");
        result.setPath(path);
        return result;
    }

    private boolean containsLegacyEndpoint(AgentCardDetailInfo detail, AgentEndpoint endpoint) {
        String expected = "http://" + endpoint.getAddress() + ':' + endpoint.getPort()
                + endpoint.getPath();
        List<AgentInterface> interfaces = detail.getSupportedInterfaces();
        return null != interfaces && interfaces.stream().anyMatch(each ->
                expected.equals(each.getUrl())
                        && endpoint.getTransport().equals(each.getProtocolBinding())
                        && endpoint.getProtocolVersion().equals(each.getProtocolVersion()));
    }

    private AgentReference reference(String agentName, String version) {
        AgentReference result = new AgentReference();
        result.setAgentName(agentName);
        result.setVersion(version);
        return result;
    }

    private AgentVersionRequest versionCommand(String agentName, String version) {
        AgentVersionRequest result = new AgentVersionRequest();
        result.setAgentName(agentName);
        result.setVersion(version);
        return result;
    }

    private AgentMaintainerService createAgentMaintainerService() throws NacosException {
        Properties properties = maintainerProperties();
        properties.setProperty(PropertyKeyConst.CONTEXT_PATH, "/nacos");
        return AiMaintainerFactory.createAiMaintainerService(properties).agent();
    }

    private AiService createAiService(String namespaceId, String transport)
            throws Exception {
        Properties properties = sdkProperties();
        properties.setProperty(PropertyKeyConst.NAMESPACE, namespaceId);
        properties.setProperty(AiConstants.AI_TRANSPORT_MODE, transport);
        return createAiService(properties);
    }

    private void assertNotFound(CheckedRunnable runnable) {
        assertError(NacosException.NOT_FOUND, runnable);
    }

    private void assertError(int expectedCode, CheckedRunnable runnable) {
        NacosException error = assertThrows(NacosException.class, runnable::run);
        assertEquals(expectedCode, error.getErrCode(), error.toString());
    }

    @FunctionalInterface
    private interface CheckedRunnable {

        void run() throws Exception;
    }

    private static final class VersionListener extends AbstractNacosAgentCardListener {

        private final String expectedVersion;

        private final CountDownLatch latch = new CountDownLatch(1);

        private final AtomicReference<AgentCardDetailInfo> card = new AtomicReference<>();

        private VersionListener(String expectedVersion) {
            this.expectedVersion = expectedVersion;
        }

        @Override
        public void onEvent(NacosAgentCardEvent event) {
            AgentCardDetailInfo current = event.getAgentCard();
            if (current != null && expectedVersion.equals(current.getVersion())) {
                card.set(current);
                latch.countDown();
            }
        }
    }
}
