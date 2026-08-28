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
import com.alibaba.nacos.api.ai.AiService;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.listener.AbstractNacosAgentCardListener;
import com.alibaba.nacos.api.ai.listener.NacosAgentCardEvent;
import com.alibaba.nacos.api.ai.model.a2a.AgentCapabilities;
import com.alibaba.nacos.api.ai.model.a2a.AgentCard;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardDetailInfo;
import com.alibaba.nacos.api.ai.model.a2a.AgentEndpoint;
import com.alibaba.nacos.api.ai.model.a2a.AgentInterface;
import com.alibaba.nacos.api.ai.model.agent.AgentCallInterface;
import com.alibaba.nacos.api.ai.model.agent.AgentProvider;
import com.alibaba.nacos.api.ai.model.agent.AgentPublishRequest;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionCommand;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionDetail;
import com.alibaba.nacos.api.ai.model.agent.Endpoint;
import com.alibaba.nacos.api.ai.model.agent.EndpointSource;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryResult;
import com.alibaba.nacos.api.ai.model.rad.AgentReference;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.maintainer.client.ai.AgentMaintainerService;
import com.alibaba.nacos.maintainer.client.ai.AiMaintainerFactory;
import com.alibaba.nacos.test.sdk.JavaSdkBaseITCase;
import org.junit.jupiter.api.Test;

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
 * Standalone integration scenarios for code-first Agent definition publication.
 *
 * <p>The full scenario inventory and unit-test fault-injection split are recorded in
 * {@code test/java-sdk-test/AGENT_PUBLISH_SDK_IT_SCENARIOS.md}.
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
    void shouldPublishDraftResumeAndConvergeAcrossCanonicalAndLegacyReads() throws Exception {
        AgentMaintainerService maintainer = createAgentMaintainerService();
        AiService service = createAiService();
        String agentName = randomServiceName("agent-code-publish");
        AgentPublishRequest request = initialRequest(agentName, VERSION_ONE, "initial", false);
        String callerSnapshot = JacksonUtils.toJson(request);
        addCleanup(() -> maintainer.deleteAgent(Constants.DEFAULT_NAMESPACE_ID, agentName));

        AgentVersionDetail draft = service.publishAgent(request);
        assertEquals("draft", draft.getStatus(), draft.toString());
        assertEquals(Constants.DEFAULT_NAMESPACE_ID, draft.getNamespaceId(), draft.toString());
        assertEquals(callerSnapshot, JacksonUtils.toJson(request),
                "the SDK must not mutate the caller-owned request");
        assertEquals(draft.getContentDigest(), service.publishAgent(request).getContentDigest());
        assertNotFound(() -> service.discoverAgent(reference(agentName, null)));
        assertNotFound(() -> service.getAgentCard(agentName));

        request.setAutoSubmit(true);
        AgentVersionDetail online = service.publishAgent(request);
        assertEquals("online", online.getStatus(), online.toString());
        assertEquals(draft.getContentDigest(), online.getContentDigest(), online.toString());
        assertEquals("online", service.publishAgent(request).getStatus());
        assertEquals(online.getContentDigest(), maintainer.getAgentVersion(
                Constants.DEFAULT_NAMESPACE_ID, agentName, VERSION_ONE).getContentDigest());

        AgentDiscoveryResult discovery = service.discoverAgent(reference(agentName, null));
        assertEquals(VERSION_ONE, discovery.getVersion(), discovery.toString());
        assertEquals(online.getContentDigest(), discovery.getContentDigest(),
                discovery.toString());
        assertTrue(discovery.getCallInterfaces().get(0).getEndpointSets().stream()
                .filter(each -> EndpointSource.RUNTIME == each.getSource())
                .allMatch(each -> each.getEndpoints().isEmpty()), discovery.toString());
        assertTrue(maintainer.getRuntimeEndpoints(Constants.DEFAULT_NAMESPACE_ID, agentName,
                "a2a", VERSION_ONE).getItems().isEmpty());

        AgentCardDetailInfo legacy = service.getAgentCard(agentName, VERSION_ONE,
                AiConstants.A2a.A2A_ENDPOINT_TYPE_URL);
        assertEquals(agentName, legacy.getName(), legacy.toString());
        assertEquals(VERSION_ONE, legacy.getVersion(), legacy.toString());
        assertEquals(2, legacy.getSupportedInterfaces().size(), legacy.toString());

        AgentPublishRequest draftOnlyRetry = initialRequest(agentName, VERSION_ONE,
                "initial", false);
        assertError(NacosException.CONFLICT,
                () -> service.publishAgent(draftOnlyRetry));
        AgentPublishRequest contentConflict = initialRequest(agentName, VERSION_ONE,
                "different-content", true);
        assertError(NacosException.CONFLICT, () -> service.publishAgent(contentConflict));
        AgentPublishRequest metadataConflict = initialRequest(agentName, VERSION_ONE,
                "initial", true);
        metadataConflict.setDescription("different initial metadata");
        assertError(NacosException.CONFLICT, () -> service.publishAgent(metadataConflict));
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
        AgentVersionDetail grpcPublished = grpc.publishAgent(grpcRequest);
        assertEquals("online", grpcPublished.getStatus(), grpcPublished.toString());
        assertEquals(VERSION_ONE, http.discoverAgent(reference(grpcAgent, null)).getVersion());
        assertEquals(grpcPublished.getContentDigest(), http.publishAgent(grpcRequest)
                .getContentDigest());

        AgentPublishRequest httpRequest = initialRequest(httpAgent, VERSION_ONE,
                "http", true);
        AgentVersionDetail httpPublished = http.publishAgent(httpRequest);
        assertEquals("online", httpPublished.getStatus(), httpPublished.toString());
        assertEquals(VERSION_ONE, grpc.discoverAgent(reference(httpAgent, null)).getVersion());
        assertEquals(httpPublished.getContentDigest(), grpc.publishAgent(httpRequest)
                .getContentDigest());

        String namespaceId = randomServiceName("agent-publish-namespace");
        String customAgent = randomServiceName("agent-publish-custom");
        AiService custom = createAiService(namespaceId, AiConstants.AI_TRANSPORT_MODE_HTTP);
        addCleanup(() -> maintainer.deleteAgent(namespaceId, customAgent));
        AgentPublishRequest customRequest = initialRequest(customAgent, VERSION_ONE,
                "custom", true);
        AgentVersionDetail customPublished = custom.publishAgent(customRequest);
        assertEquals(namespaceId, customPublished.getNamespaceId(), customPublished.toString());
        assertEquals(VERSION_ONE, custom.discoverAgent(reference(customAgent, null)).getVersion());
        assertNotFound(() -> grpc.discoverAgent(reference(customAgent, null)));
    }

    @Test
    void shouldPublishDirectAndInheritedVersionsAndRejectInvalidStates() throws Exception {
        AgentMaintainerService maintainer = createAgentMaintainerService();
        AiService grpc = createAiService();
        AiService http = createAiService(Constants.DEFAULT_NAMESPACE_ID,
                AiConstants.AI_TRANSPORT_MODE_HTTP);
        String agentName = randomServiceName("agent-publish-versions");
        addCleanup(() -> maintainer.deleteAgent(Constants.DEFAULT_NAMESPACE_ID, agentName));

        AgentVersionDetail first = grpc.publishAgent(
                initialRequest(agentName, VERSION_ONE, "one", true));
        AgentPublishRequest secondRequest = versionRequest(agentName, VERSION_TWO,
                "two", true);
        AgentVersionDetail second = http.publishAgent(secondRequest);
        assertFalse(first.getContentDigest().equals(second.getContentDigest()));

        AgentPublishRequest inherited = inheritedRequest(agentName, VERSION_THREE,
                VERSION_TWO, true);
        AgentVersionDetail third = grpc.publishAgent(inherited);
        assertEquals(second.getContentDigest(), third.getContentDigest(), third.toString());
        assertEquals(VERSION_THREE, http.discoverAgent(reference(agentName, null)).getVersion());

        AgentPublishRequest both = inheritedRequest(agentName, VERSION_FOUR, VERSION_TWO, false);
        both.setCallInterfaces(Collections.singletonList(callInterface(agentName,
                VERSION_FOUR, "both")));
        assertError(NacosException.INVALID_PARAM, () -> grpc.publishAgent(both));

        AgentPublishRequest neither = new AgentPublishRequest();
        neither.setAgentName(agentName);
        neither.setVersion(VERSION_FOUR);
        assertError(NacosException.INVALID_PARAM, () -> grpc.publishAgent(neither));

        AgentPublishRequest firstInheritance = inheritedRequest(
                randomServiceName("agent-publish-first-inherit"), VERSION_ONE,
                VERSION_TWO, false);
        assertError(NacosException.INVALID_PARAM, () -> grpc.publishAgent(firstInheritance));

        AgentPublishRequest changedAuthor = versionRequest(agentName, VERSION_TWO,
                "two", true);
        changedAuthor.setAuthor("different-author");
        assertError(NacosException.CONFLICT, () -> grpc.publishAgent(changedAuthor));

        AgentPublishRequest falseAgainstOnline = versionRequest(agentName, VERSION_TWO,
                "two", false);
        assertError(NacosException.CONFLICT,
                () -> grpc.publishAgent(falseAgainstOnline));

        maintainer.offline(Constants.DEFAULT_NAMESPACE_ID,
                versionCommand(agentName, VERSION_TWO));
        assertError(NacosException.INVALID_PARAM, () -> http.publishAgent(secondRequest));
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
        service.publishAgent(initialRequest(agentName, VERSION_ONE, "one", true));
        waitUntil("Version 1 pre-registered Endpoint should become visible", () ->
                containsLegacyEndpoint(service.getAgentCard(agentName, VERSION_ONE,
                        AiConstants.A2a.A2A_ENDPOINT_TYPE_SERVICE), firstEndpoint));

        service.publishAgent(versionRequest(agentName, VERSION_TWO, "two", true));
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
        publisher.publishAgent(initialRequest(agentName, VERSION_ONE, "one", true));
        assertTrue(exactOne.latch.await(POLLING_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                "an exact subscription must receive a Version that is also latest");

        VersionListener latestTwo = new VersionListener(VERSION_TWO);
        assertEquals(VERSION_ONE,
                observer.subscribeAgentCard(agentName, latestTwo).getVersion());
        addCleanup(() -> observer.unsubscribeAgentCard(agentName, latestTwo));
        publisher.publishAgent(versionRequest(agentName, VERSION_TWO, "two", true));
        assertTrue(latestTwo.latch.await(POLLING_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                "latest subscription must move to Version 2");

        VersionListener latestThree = new VersionListener(VERSION_THREE);
        observer.unsubscribeAgentCard(agentName, latestTwo);
        assertEquals(VERSION_TWO,
                observer.subscribeAgentCard(agentName, latestThree).getVersion());
        addCleanup(() -> observer.unsubscribeAgentCard(agentName, latestThree));
        publisher.publishAgent(versionRequest(agentName, VERSION_THREE, "three", true));
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
        publisher.publishAgent(versionRequest(agentName, VERSION_FOUR, "four", true));
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
        result.setDeclaredEndpoints(Arrays.asList(jsonRpcEndpoint, grpcEndpoint));
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

    private AgentVersionCommand versionCommand(String agentName, String version) {
        AgentVersionCommand result = new AgentVersionCommand();
        result.setAgentName(agentName);
        result.setVersion(version);
        return result;
    }

    private AgentMaintainerService createAgentMaintainerService() throws NacosException {
        Properties properties = sdkProperties();
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
