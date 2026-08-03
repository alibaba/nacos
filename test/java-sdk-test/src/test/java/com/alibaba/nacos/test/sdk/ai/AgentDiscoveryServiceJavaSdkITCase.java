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
import com.alibaba.nacos.api.ai.AiFactory;
import com.alibaba.nacos.api.ai.AiService;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.listener.AbstractNacosAgentCardListener;
import com.alibaba.nacos.api.ai.listener.AbstractNacosAgentDiscoveryListener;
import com.alibaba.nacos.api.ai.listener.NacosAgentCardEvent;
import com.alibaba.nacos.api.ai.listener.NacosAgentDiscoveryEvent;
import com.alibaba.nacos.api.ai.model.a2a.AgentCapabilities;
import com.alibaba.nacos.api.ai.model.a2a.AgentCard;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardDetailInfo;
import com.alibaba.nacos.api.ai.model.a2a.AgentInterface;
import com.alibaba.nacos.api.ai.model.agent.AgentCallInterface;
import com.alibaba.nacos.api.ai.model.agent.AgentDraftCreateRequest;
import com.alibaba.nacos.api.ai.model.agent.AgentLabelsUpdateRequest;
import com.alibaba.nacos.api.ai.model.agent.AgentProvider;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionCommand;
import com.alibaba.nacos.api.ai.model.agent.Endpoint;
import com.alibaba.nacos.api.ai.model.agent.EndpointSource;
import com.alibaba.nacos.api.ai.model.rad.AgentCatalogEntry;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryCallInterface;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryFilter;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryResult;
import com.alibaba.nacos.api.ai.model.rad.AgentEndpointDeregistrationBatch;
import com.alibaba.nacos.api.ai.model.rad.AgentEndpointRegistrationBatch;
import com.alibaba.nacos.api.ai.model.rad.AgentReference;
import com.alibaba.nacos.api.ai.model.rad.AgentSearchRequest;
import com.alibaba.nacos.api.ai.model.rad.EndpointSet;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.maintainer.client.ai.AgentMaintainerService;
import com.alibaba.nacos.maintainer.client.ai.AiMaintainerFactory;
import com.alibaba.nacos.test.sdk.JavaSdkBaseITCase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Standalone integration scenarios for the protocol-neutral Agent Java SDK.
 *
 * <p>The Maintainer SDK is used only as an external setup and cleanup client. Every behavior
 * asserted by this class is exercised through the public {@link AiService} contract.
 *
 * <p>The complete scenario inventory and the deterministic fault-injection split are recorded in
 * {@code test/java-sdk-test/AGENT_DISCOVERY_SDK_IT_SCENARIOS.md}.
 *
 * @author Nacos
 */
class AgentDiscoveryServiceJavaSdkITCase extends JavaSdkBaseITCase {
    
    private static final String VERSION = "1.0.0";
    
    private static final String VERSION_2 = "2.0.0";
    
    private static final String VERSION_3 = "3.0.0";
    
    private static final String PROTOCOL_A2A = "a2a";
    
    private static final String PROTOCOL_MCP = "mcp";
    
    private static final String TRANSPORT_HTTP = "http";
    
    private static final String LABEL_STABLE = "stable";
    
    private static final long POLLING_TIMEOUT_MILLIS = 25000L;
    
    private static final long RECONNECT_TIMEOUT_MILLIS = 120000L;
    
    private static final String RECONNECT_ENABLED_PROPERTY = "nacos.agent.reconnect.enabled";
    
    private static final String RECONNECT_CONTROL_DIR_PROPERTY =
        "nacos.agent.reconnect.control.dir";

    private static final String CONSOLE_BASE_URL = "http://" + NACOS_HOST + ":"
        + System.getProperty("nacos.console.port", "8080");

    private static final String CONSOLE_AGENT_PATH = "/v3/console/ai/agents";

    @Test
    void shouldInteroperateWithLegacyA2aSdk() throws Exception {
        AgentMaintainerService maintainer = createAgentMaintainerService();
        AiService service = createAiService();
        String agentName = randomServiceName("agent-legacy-a2a");
        AgentCard firstCard = legacyCompatibleAgentCard(agentName, VERSION,
            "legacy A2A SDK release");

        service.releaseAgentCard(firstCard, AiConstants.A2a.A2A_ENDPOINT_TYPE_URL, true);
        addCleanup(() -> maintainer.deleteAgent(Constants.DEFAULT_NAMESPACE_ID, agentName));

        AgentDiscoveryResult firstDiscovery =
            service.discoverAgent(reference(agentName, null, null));
        assertEquals(VERSION, firstDiscovery.getVersion());
        assertEquals(1,
            sourceEndpoints(firstDiscovery, PROTOCOL_A2A, EndpointSource.DECLARED).size());
        Map<?, ?> firstDescriptor = (Map<?, ?>) firstDiscovery.getCallInterfaces().get(0)
            .getNativeDescriptor();
        assertEquals("legacy A2A SDK release", firstDescriptor.get("description"));
        JsonNode consoleOverview = getConsoleAgent(CONSOLE_AGENT_PATH, agentName, null);
        assertEquals(agentName, consoleOverview.get("agent").get("agentName").asText(),
            consoleOverview.toString());
        assertEquals("legacy A2A SDK release",
            consoleOverview.get("agent").get("description").asText(),
            consoleOverview.toString());
        assertEquals(VERSION,
            consoleOverview.get("agent").get("versionInfo").get("labels").get("latest")
                .asText(), consoleOverview.toString());
        JsonNode consoleVersion = getConsoleAgent(CONSOLE_AGENT_PATH + "/version", agentName,
            VERSION);
        assertEquals("a2a", consoleVersion.get("callInterfaces").get(0).get("protocol")
            .asText(), consoleVersion.toString());
        assertEquals(1, consoleVersion.get("callInterfaces").get(0)
            .get("declaredEndpoints").size(), consoleVersion.toString());

        com.alibaba.nacos.api.ai.model.a2a.AgentEndpoint legacyEndpoint =
            legacyEndpoint(VERSION);
        service.registerAgentEndpoint(agentName, legacyEndpoint);
        addCleanup(() -> service.deregisterAgentEndpoint(agentName, legacyEndpoint));
        waitUntil("legacy SERVICE query should expose the old Endpoint", () ->
            containsLegacyEndpoint(service.getAgentCard(agentName, VERSION,
                AiConstants.A2a.A2A_ENDPOINT_TYPE_SERVICE), legacyEndpoint));
        JsonNode consoleRuntime = getConsoleAgent(CONSOLE_AGENT_PATH + "/runtime-endpoints",
            agentName, VERSION);
        assertEquals(0, consoleRuntime.get("runtimeEndpointSnapshot").get("items").size(),
            "legacy exact-Version Naming Endpoints must remain isolated from the new Runtime "
                + "Registry: " + consoleRuntime);

        AgentCard duplicate = legacyCompatibleAgentCard(agentName, VERSION,
            "duplicate must not overwrite the online Version");
        service.releaseAgentCard(duplicate, AiConstants.A2a.A2A_ENDPOINT_TYPE_URL, false);
        AgentCardDetailInfo unchanged = service.getAgentCard(agentName, VERSION,
            AiConstants.A2a.A2A_ENDPOINT_TYPE_URL);
        assertEquals("legacy A2A SDK release", unchanged.getDescription());

        CountDownLatch legacyLatestChanged = new CountDownLatch(1);
        AtomicReference<AgentCardDetailInfo> legacySubscription = new AtomicReference<>();
        AbstractNacosAgentCardListener legacyListener = new AbstractNacosAgentCardListener() {
            @Override
            public void onEvent(NacosAgentCardEvent event) {
                if (event.getAgentCard() != null
                    && VERSION_2.equals(event.getAgentCard().getVersion())) {
                    legacySubscription.set(event.getAgentCard());
                    legacyLatestChanged.countDown();
                }
            }
        };
        assertEquals(VERSION, service.subscribeAgentCard(agentName, legacyListener).getVersion());
        addCleanup(() -> service.unsubscribeAgentCard(agentName, legacyListener));

        AgentCard secondCard = legacyCompatibleAgentCard(agentName, VERSION_2,
            "canonical Agent SDK publication");
        AgentDraftCreateRequest secondDraft = new AgentDraftCreateRequest();
        secondDraft.setAgentName(agentName);
        secondDraft.setVersion(VERSION_2);
        secondDraft.setCallInterfaces(Collections.singletonList(
            legacyCompatibleCallInterface(secondCard)));
        secondDraft.setAuthor("java-sdk-it");
        secondDraft.setChangeDescription("publish a legacy-compatible Agent Version");
        maintainer.createDraft(Constants.DEFAULT_NAMESPACE_ID, secondDraft);
        maintainer.forcePublish(Constants.DEFAULT_NAMESPACE_ID,
            versionCommand(agentName, VERSION_2));

        AgentCardDetailInfo legacyProjection = service.getAgentCard(agentName, VERSION_2,
            AiConstants.A2a.A2A_ENDPOINT_TYPE_URL);
        assertEquals(agentName, legacyProjection.getName());
        assertEquals(VERSION_2, legacyProjection.getVersion());
        assertEquals("canonical Agent SDK publication", legacyProjection.getDescription());
        assertTrue(legacyLatestChanged.await(POLLING_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS),
            "legacy latest subscription must observe a canonical Agent Version publication");
        assertNotNull(legacySubscription.get());
        assertEquals(VERSION_2, legacySubscription.get().getVersion());
        assertEquals(VERSION_2,
            service.discoverAgent(reference(agentName, null, null)).getVersion());
        JsonNode updatedConsoleOverview = getConsoleAgent(CONSOLE_AGENT_PATH, agentName, null);
        assertEquals(VERSION_2,
            updatedConsoleOverview.get("agent").get("versionInfo").get("labels")
                .get("latest").asText(), updatedConsoleOverview.toString());
    }
    
    @Test
    void shouldSearchDiscoverAndIsolateNamespaces() throws Exception {
        AgentMaintainerService maintainer = createAgentMaintainerService();
        AiService defaultService = createAiService();
        String targetName = randomServiceName("agent-search-target");
        String decoyName = randomServiceName("agent-search-decoy");
        String customNamespace = randomServiceName("agent-namespace");
        String customName = randomServiceName("agent-search-custom");
        createPublishedAgent(maintainer, Constants.DEFAULT_NAMESPACE_ID, targetName,
            Arrays.asList("java-sdk-it", "blue"), Arrays.asList(PROTOCOL_A2A, PROTOCOL_MCP), true);
        createPublishedAgent(maintainer, Constants.DEFAULT_NAMESPACE_ID, decoyName,
            Arrays.asList("java-sdk-it", "red"), Collections.singletonList(PROTOCOL_MCP), false);
        createPublishedAgent(maintainer, customNamespace, customName,
            Arrays.asList("java-sdk-it", "blue"), Collections.singletonList(PROTOCOL_A2A), false);
        AiService customService = createAiService(customNamespace, null);
        
        AgentSearchRequest request = new AgentSearchRequest();
        request.setAgentNameContains(targetName);
        request.setTagsAll(Arrays.asList("java-sdk-it", "blue"));
        request.setProtocolsAny(Arrays.asList(PROTOCOL_MCP, PROTOCOL_A2A));
        request.setPageNo(1);
        request.setPageSize(1);
        Page<AgentCatalogEntry> page = defaultService.searchAgents(request);
        assertNull(request.getNamespaceId(), "the caller-owned Search request must not be changed");
        assertEquals(1, page.getTotalCount());
        assertEquals(1, page.getPageItems().size());
        assertEquals(targetName, page.getPageItems().get(0).getAgentName());
        assertEquals(VERSION, page.getPageItems().get(0).getLatestVersion());
        
        AgentSearchRequest defaultSearch = new AgentSearchRequest();
        assertEquals(2, defaultService.searchAgents(defaultSearch).getTotalCount());
        AgentSearchRequest tagSearch = new AgentSearchRequest();
        tagSearch.setTagsAll(Collections.singletonList("blue"));
        assertEquals(1, defaultService.searchAgents(tagSearch).getTotalCount());
        AgentSearchRequest protocolSearch = new AgentSearchRequest();
        protocolSearch.setProtocolsAny(Collections.singletonList(PROTOCOL_A2A));
        assertEquals(1, defaultService.searchAgents(protocolSearch).getTotalCount());
        
        AgentSearchRequest emptySearch = new AgentSearchRequest();
        emptySearch.setAgentNameContains("no-such-agent-" + targetName);
        assertEquals(0, defaultService.searchAgents(emptySearch).getTotalCount());
        AgentSearchRequest customSearch = new AgentSearchRequest();
        customSearch.setAgentNameContains(customName);
        assertEquals(1, customService.searchAgents(customSearch).getTotalCount());
        assertEquals(0, defaultService.searchAgents(customSearch).getTotalCount());
        AgentSearchRequest explicitlyBoundSearch = new AgentSearchRequest();
        explicitlyBoundSearch.setNamespaceId(customNamespace);
        explicitlyBoundSearch.setAgentNameContains(customName);
        assertEquals(1, customService.searchAgents(explicitlyBoundSearch).getTotalCount());
        
        AgentReference latest = reference(targetName, null, null);
        AgentDiscoveryResult latestResult = defaultService.discoverAgent(latest);
        assertEquals(VERSION, latestResult.getVersion());
        assertEquals(2, latestResult.getCallInterfaces().size());
        assertEquals(1,
            sourceEndpoints(latestResult, PROTOCOL_A2A, EndpointSource.DECLARED).size());
        AgentDiscoveryResult exact =
            defaultService.discoverAgent(reference(targetName, VERSION, null));
        assertEquals(latestResult.getContentDigest(), exact.getContentDigest());
        AgentDiscoveryResult labeled =
            defaultService.discoverAgent(reference(targetName, null, LABEL_STABLE));
        assertEquals(VERSION, labeled.getVersion());
        
        AgentDiscoveryFilter combined = new AgentDiscoveryFilter();
        combined.setProtocols(Collections.singletonList(PROTOCOL_A2A));
        combined.setProtocolVersion("1.0");
        combined.setTransports(Collections.singletonList(TRANSPORT_HTTP));
        combined.setEndpointSources(Collections.singletonList(EndpointSource.DECLARED));
        combined.setMetadataSelector(Collections.singletonMap("region", "declared"));
        AgentDiscoveryResult filtered = defaultService.discoverAgent(latest, combined);
        assertEquals(1, filtered.getCallInterfaces().size());
        assertEquals(PROTOCOL_A2A, filtered.getCallInterfaces().get(0).getProtocol());
        assertEquals(1, sourceEndpoints(filtered, PROTOCOL_A2A, EndpointSource.DECLARED).size());
        assertTrue(sourceEndpoints(filtered, PROTOCOL_A2A, EndpointSource.RUNTIME).isEmpty());
        
        AgentSearchRequest mismatched = new AgentSearchRequest();
        mismatched.setNamespaceId(customNamespace);
        assertInvalid(() -> defaultService.searchAgents(mismatched));
        
        Endpoint customEndpoint = endpoint(randomPort(), "/custom", "custom");
        AgentEndpointRegistrationBatch customRegistration =
            registration(customName, PROTOCOL_A2A, Collections.singletonList(customEndpoint));
        customRegistration.setNamespaceId(customNamespace);
        customService.registerAgentEndpoints(customRegistration);
        waitForEndpointCount(customService, customName, PROTOCOL_A2A, 1);
        NacosException isolated = assertThrows(NacosException.class,
            () -> defaultService.discoverAgent(reference(customName, null, null)));
        assertEquals(NacosException.NOT_FOUND, isolated.getErrCode());
        AgentEndpointDeregistrationBatch customDeregistration =
            deregistration(customName, PROTOCOL_A2A,
                Collections.singletonList(deregistrationEndpoint(customEndpoint)));
        customDeregistration.setNamespaceId(customNamespace);
        customService.deregisterAgentEndpoints(customDeregistration);
        waitForEndpointCount(customService, customName, PROTOCOL_A2A, 0);
    }
    
    @Test
    void shouldReplaceAndPartiallyDeregisterCompletePublications() throws Exception {
        AgentMaintainerService maintainer = createAgentMaintainerService();
        AiService service = createAiService();
        String agentName = randomServiceName("agent-publication");
        createPublishedAgent(maintainer, Constants.DEFAULT_NAMESPACE_ID, agentName,
            Collections.singletonList("java-sdk-it"), Arrays.asList(PROTOCOL_A2A, PROTOCOL_MCP),
            false);
        
        Endpoint first = endpoint(randomPort(), "/first", "one");
        Endpoint second = endpoint(randomPort(), "/second", "two");
        Endpoint third = endpoint(randomPort(), "/third", "three");
        AgentEndpointRegistrationBatch initial =
            registration(agentName, PROTOCOL_A2A, Arrays.asList(first, second));
        service.registerAgentEndpoints(initial);
        assertNull(initial.getNamespaceId(),
            "the caller-owned registration Batch must not be changed");
        waitForEndpointCount(service, agentName, PROTOCOL_A2A, 2);
        
        service.registerAgentEndpoints(initial);
        waitForEndpointCount(service, agentName, PROTOCOL_A2A, 2);
        service.registerAgentEndpoints(
            registration(agentName, PROTOCOL_A2A, Arrays.asList(second, third)));
        waitUntil("complete publication replacement is visible", () -> {
            AgentDiscoveryResult result =
                service.discoverAgent(reference(agentName, null, null));
            return sourceEndpoints(result, PROTOCOL_A2A, EndpointSource.RUNTIME).size() == 2
                && !containsEndpoint(result, PROTOCOL_A2A, first.getUri())
                && containsEndpoint(result, PROTOCOL_A2A, third.getUri());
        });
        assertFalse(containsEndpoint(service.discoverAgent(reference(agentName, null, null)),
            PROTOCOL_A2A, first.getUri()));
        
        Endpoint sameNaturalKey = deregistrationEndpoint(second);
        sameNaturalKey.setUri(replacePath(second.getUri(), "/different/path?ignored=true"));
        service.deregisterAgentEndpoints(
            deregistration(agentName, PROTOCOL_A2A,
                Collections.singletonList(sameNaturalKey)));
        waitForEndpointCount(service, agentName, PROTOCOL_A2A, 1);
        assertTrue(containsEndpoint(service.discoverAgent(reference(agentName, null, null)),
            PROTOCOL_A2A, third.getUri()));
        
        service.deregisterAgentEndpoints(
            deregistration(agentName, PROTOCOL_A2A,
                Collections.singletonList(deregistrationEndpoint(first))));
        waitForEndpointCount(service, agentName, PROTOCOL_A2A, 1);
        
        Endpoint mcp = endpoint(randomPort(), "/mcp", "mcp");
        service.registerAgentEndpoints(
            registration(agentName, PROTOCOL_MCP, Collections.singletonList(mcp)));
        waitForEndpointCount(service, agentName, PROTOCOL_MCP, 1);
        service.deregisterAgentEndpoints(
            deregistration(agentName, PROTOCOL_A2A,
                Collections.singletonList(deregistrationEndpoint(third))));
        waitForEndpointCount(service, agentName, PROTOCOL_A2A, 0);
        waitForEndpointCount(service, agentName, PROTOCOL_MCP, 1);
        
        service.deregisterAgentEndpoints(
            deregistration(agentName, PROTOCOL_A2A,
                Collections.singletonList(deregistrationEndpoint(third))));
        service.deregisterAgentEndpoints(
            deregistration(agentName, PROTOCOL_MCP,
                Collections.singletonList(deregistrationEndpoint(mcp))));
        waitForEndpointCount(service, agentName, PROTOCOL_MCP, 0);
    }
    
    @Test
    void shouldAggregateIndependentSdkPublishers() throws Exception {
        AgentMaintainerService maintainer = createAgentMaintainerService();
        AiService reader = createAiService();
        AiService firstPublisher = createAiService();
        AiService secondPublisher = createAiService();
        String agentName = randomServiceName("agent-publishers");
        createPublishedAgent(maintainer, Constants.DEFAULT_NAMESPACE_ID, agentName,
            Collections.singletonList("java-sdk-it"), Collections.singletonList(PROTOCOL_A2A),
            false);
        Endpoint shared = endpoint(randomPort(), "/shared", "shared");
        
        firstPublisher.registerAgentEndpoints(
            registration(agentName, PROTOCOL_A2A, Collections.singletonList(shared)));
        secondPublisher.registerAgentEndpoints(
            registration(agentName, PROTOCOL_A2A, Collections.singletonList(shared)));
        waitForEndpointCount(reader, agentName, PROTOCOL_A2A, 1);
        
        firstPublisher.deregisterAgentEndpoints(
            deregistration(agentName, PROTOCOL_A2A,
                Collections.singletonList(deregistrationEndpoint(shared))));
        waitForEndpointCount(reader, agentName, PROTOCOL_A2A, 1);
        secondPublisher.deregisterAgentEndpoints(
            deregistration(agentName, PROTOCOL_A2A,
                Collections.singletonList(deregistrationEndpoint(shared))));
        waitForEndpointCount(reader, agentName, PROTOCOL_A2A, 0);
    }
    
    @Test
    void shouldDiscoverPreRegistrationAndPollUntilAgentAppears() throws Exception {
        AgentMaintainerService maintainer = createAgentMaintainerService();
        AiService service = createAiService();
        String agentName = randomServiceName("agent-pre-register");
        Endpoint initial = endpoint(randomPort(), "/initial", "initial");
        service.registerAgentEndpoints(
            registration(agentName, PROTOCOL_A2A, Collections.singletonList(initial)));
        
        NacosException absent = assertThrows(NacosException.class,
            () -> service.discoverAgent(reference(agentName, null, null)));
        assertEquals(NacosException.NOT_FOUND, absent.getErrCode());
        
        AtomicInteger callbackCount = new AtomicInteger();
        AtomicReference<AgentDiscoveryResult> callbackResult = new AtomicReference<>();
        CountDownLatch callback = new CountDownLatch(1);
        AbstractNacosAgentDiscoveryListener listener =
            new AbstractNacosAgentDiscoveryListener() {
                
                @Override
                public void onEvent(NacosAgentDiscoveryEvent event) {
                    callbackResult.set(event.getAgentDiscoveryResult());
                    callbackCount.incrementAndGet();
                    callback.countDown();
                }
            };
        AgentReference reference = reference(agentName, null, null);
        assertNull(service.subscribeAgent(reference, listener));
        
        createPublishedAgent(maintainer, Constants.DEFAULT_NAMESPACE_ID, agentName,
            Collections.singletonList("java-sdk-it"), Collections.singletonList(PROTOCOL_A2A),
            false);
        assertTrue(callback.await(POLLING_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS),
            "polling must discover an Agent created after subscription");
        assertNotNull(callbackResult.get());
        assertEquals(1,
            sourceEndpoints(callbackResult.get(), PROTOCOL_A2A, EndpointSource.RUNTIME).size());
        
        service.unsubscribeAgent(reference, listener);
        int countAfterUnsubscribe = callbackCount.get();
        Endpoint replacement = endpoint(randomPort(), "/replacement", "replacement");
        service.registerAgentEndpoints(
            registration(agentName, PROTOCOL_A2A, Collections.singletonList(replacement)));
        waitForEndpointCount(service, agentName, PROTOCOL_A2A, 1);
        Thread.sleep(AiConstants.DEFAULT_AI_CACHE_UPDATE_INTERVAL + 1500L);
        assertEquals(countAfterUnsubscribe, callbackCount.get(),
            "no callback may be delivered after unsubscribe");
        
        service.deregisterAgentEndpoints(
            deregistration(agentName, PROTOCOL_A2A,
                Collections.singletonList(deregistrationEndpoint(replacement))));
    }
    
    @Test
    void shouldPollExistingAgentOnlyWhenCompleteFingerprintChanges() throws Exception {
        AgentMaintainerService maintainer = createAgentMaintainerService();
        AiService service = createAiService();
        String agentName = randomServiceName("agent-existing-subscription");
        createPublishedAgent(maintainer, Constants.DEFAULT_NAMESPACE_ID, agentName,
            Collections.singletonList("java-sdk-it"), Collections.singletonList(PROTOCOL_A2A),
            false);
        Endpoint initial = endpoint(randomPort(), "/initial", "initial");
        service.registerAgentEndpoints(
            registration(agentName, PROTOCOL_A2A, Collections.singletonList(initial)));
        waitForEndpointCount(service, agentName, PROTOCOL_A2A, 1);
        
        AtomicInteger callbackCount = new AtomicInteger();
        AtomicReference<AgentDiscoveryResult> callbackResult = new AtomicReference<>();
        CountDownLatch replacementCallback = new CountDownLatch(1);
        AbstractNacosAgentDiscoveryListener listener =
            new AbstractNacosAgentDiscoveryListener() {
                
                @Override
                public void onEvent(NacosAgentDiscoveryEvent event) {
                    callbackResult.set(event.getAgentDiscoveryResult());
                    callbackCount.incrementAndGet();
                    replacementCallback.countDown();
                }
            };
        AgentReference reference = reference(agentName, null, null);
        AgentDiscoveryResult current = service.subscribeAgent(reference, listener);
        assertNotNull(current);
        assertEquals(0, callbackCount.get());
        
        Endpoint replacement = endpoint(randomPort(), "/replacement", "replacement");
        AgentEndpointRegistrationBatch replacementBatch =
            registration(agentName, PROTOCOL_A2A, Collections.singletonList(replacement));
        service.registerAgentEndpoints(replacementBatch);
        assertTrue(replacementCallback.await(POLLING_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS),
            "Runtime source-revision change must deliver a complete replacement snapshot");
        assertTrue(containsEndpoint(callbackResult.get(), PROTOCOL_A2A, replacement.getUri()));
        
        int countAfterReplacement = callbackCount.get();
        service.registerAgentEndpoints(replacementBatch);
        Thread.sleep(AiConstants.DEFAULT_AI_CACHE_UPDATE_INTERVAL + 1500L);
        assertEquals(countAfterReplacement, callbackCount.get(),
            "an unchanged complete fingerprint must not deliver a duplicate callback");
        
        service.unsubscribeAgent(reference, listener);
        service.deregisterAgentEndpoints(
            deregistration(agentName, PROTOCOL_A2A,
                Collections.singletonList(deregistrationEndpoint(replacement))));
        waitForEndpointCount(service, agentName, PROTOCOL_A2A, 0);
    }
    
    @Test
    void shouldTrackVersionEvolutionAcrossRegistrationOrders() throws Exception {
        AgentMaintainerService maintainer = createAgentMaintainerService();
        AiService service = createAiService();
        String agentName = randomServiceName("agent-version-evolution");
        createPublishedAgent(maintainer, Constants.DEFAULT_NAMESPACE_ID, agentName,
            Collections.singletonList("java-sdk-it"), Collections.singletonList(PROTOCOL_A2A),
            false);
        Endpoint versionOneEndpoint = endpoint(randomPort(), "/v1", "version-one");
        service.registerAgentEndpoints(registration(agentName, VERSION, PROTOCOL_A2A,
            Collections.singletonList(versionOneEndpoint)));
        waitForEndpointCount(service, reference(agentName, VERSION, null), PROTOCOL_A2A, 1);
        
        RecordingAgentListener latestListener = new RecordingAgentListener();
        RecordingAgentListener stableListener = new RecordingAgentListener();
        RecordingAgentListener exactVersionOneListener = new RecordingAgentListener();
        AgentReference latestReference = reference(agentName, null, null);
        AgentReference stableReference = reference(agentName, null, LABEL_STABLE);
        AgentReference exactVersionOneReference = reference(agentName, VERSION, null);
        assertEquals(VERSION,
            service.subscribeAgent(latestReference, latestListener).getVersion());
        assertEquals(VERSION,
            service.subscribeAgent(stableReference, stableListener).getVersion());
        assertEquals(VERSION,
            service.subscribeAgent(exactVersionOneReference, exactVersionOneListener).getVersion());
        
        Endpoint versionTwoEndpoint = endpoint(randomPort(), "/v2", "version-two");
        service.registerAgentEndpoints(registration(agentName, VERSION_2, PROTOCOL_A2A,
            Collections.singletonList(versionTwoEndpoint)));
        waitForEndpointCount(service, reference(agentName, VERSION, null), PROTOCOL_A2A, 0);
        awaitEvent(latestListener, "latest Version 1 Runtime set becomes empty",
            result -> VERSION.equals(result.getVersion())
                && sourceEndpoints(result, PROTOCOL_A2A, EndpointSource.RUNTIME).isEmpty());
        awaitEvent(stableListener, "stable Version 1 Runtime set becomes empty",
            result -> VERSION.equals(result.getVersion())
                && sourceEndpoints(result, PROTOCOL_A2A, EndpointSource.RUNTIME).isEmpty());
        awaitEvent(exactVersionOneListener, "exact Version 1 Runtime set becomes empty",
            result -> VERSION.equals(result.getVersion())
                && sourceEndpoints(result, PROTOCOL_A2A, EndpointSource.RUNTIME).isEmpty());
        
        createPublishedVersion(maintainer, Constants.DEFAULT_NAMESPACE_ID, agentName, VERSION_2);
        waitForEndpointCount(service, reference(agentName, VERSION_2, null), PROTOCOL_A2A, 1);
        AgentDiscoveryResult latestVersionTwo =
            service.discoverAgent(latestReference);
        assertEquals(VERSION_2, latestVersionTwo.getVersion());
        assertTrue(containsEndpoint(latestVersionTwo, PROTOCOL_A2A,
            versionTwoEndpoint.getUri()));
        awaitEvent(latestListener, "latest subscription moves to pre-registered Version 2",
            result -> VERSION_2.equals(result.getVersion())
                && containsEndpoint(result, PROTOCOL_A2A, versionTwoEndpoint.getUri()));
        assertEquals(VERSION, service.discoverAgent(stableReference).getVersion());
        
        updateLabel(maintainer, Constants.DEFAULT_NAMESPACE_ID, agentName, LABEL_STABLE,
            VERSION_2);
        awaitEvent(stableListener, "stable subscription moves to Version 2",
            result -> VERSION_2.equals(result.getVersion()));
        assertEquals(VERSION_2, service.discoverAgent(stableReference).getVersion());
        
        AgentCatalogEntry versionTwoCatalog = searchOne(service, agentName);
        assertEquals(VERSION_2, versionTwoCatalog.getLatestVersion());
        assertEquals(Arrays.asList(VERSION_2, VERSION),
            Arrays.asList(versionTwoCatalog.getVersions().get(0).getVersion(),
                versionTwoCatalog.getVersions().get(1).getVersion()));
        
        createPublishedVersion(maintainer, Constants.DEFAULT_NAMESPACE_ID, agentName, VERSION_3);
        waitForEndpointCount(service, reference(agentName, VERSION_3, null), PROTOCOL_A2A, 0);
        awaitEvent(latestListener, "definition-first Version 3 initially has no Runtime Endpoint",
            result -> VERSION_3.equals(result.getVersion())
                && sourceEndpoints(result, PROTOCOL_A2A, EndpointSource.RUNTIME).isEmpty());
        
        Endpoint versionThreeEndpoint = endpoint(randomPort(), "/v3", "version-three");
        service.registerAgentEndpoints(registration(agentName, VERSION_3, PROTOCOL_A2A,
            Collections.singletonList(versionThreeEndpoint)));
        waitForEndpointCount(service, reference(agentName, VERSION_3, null), PROTOCOL_A2A, 1);
        awaitEvent(latestListener, "Version 3 Endpoint registration changes source revision",
            result -> VERSION_3.equals(result.getVersion())
                && containsEndpoint(result, PROTOCOL_A2A, versionThreeEndpoint.getUri()));
        
        maintainer.offline(Constants.DEFAULT_NAMESPACE_ID,
            versionCommand(agentName, VERSION_3));
        awaitEvent(latestListener, "offlining latest recalculates to Version 2",
            result -> VERSION_2.equals(result.getVersion()));
        assertEquals(VERSION_2, searchOne(service, agentName).getLatestVersion());
        maintainer.online(Constants.DEFAULT_NAMESPACE_ID,
            versionCommand(agentName, VERSION_3));
        awaitEvent(latestListener, "onlining Version 3 restores latest",
            result -> VERSION_3.equals(result.getVersion()));
        assertNull(exactVersionOneListener.events.poll(),
            "an exact Version 1 subscription must not follow later latest changes");
        
        service.unsubscribeAgent(latestReference, latestListener);
        service.unsubscribeAgent(stableReference, stableListener);
        service.unsubscribeAgent(exactVersionOneReference, exactVersionOneListener);
        service.deregisterAgentEndpoints(deregistration(agentName, PROTOCOL_A2A,
            Collections.singletonList(deregistrationEndpoint(versionThreeEndpoint))));
        waitForEndpointCount(service, reference(agentName, VERSION_3, null), PROTOCOL_A2A, 0);
    }
    
    @Test
    void shouldApplyPublicationRangeAcrossOnlineVersions() throws Exception {
        AgentMaintainerService maintainer = createAgentMaintainerService();
        AiService service = createAiService();
        String agentName = randomServiceName("agent-version-range");
        createPublishedAgent(maintainer, Constants.DEFAULT_NAMESPACE_ID, agentName,
            Collections.singletonList("java-sdk-it"), Collections.singletonList(PROTOCOL_A2A),
            false);
        createPublishedVersion(maintainer, Constants.DEFAULT_NAMESPACE_ID, agentName, VERSION_2);
        createPublishedVersion(maintainer, Constants.DEFAULT_NAMESPACE_ID, agentName, VERSION_3);
        Endpoint endpoint = endpoint(randomPort(), "/range", "range");
        
        AgentEndpointRegistrationBatch firstRange = registration(agentName, VERSION_2,
            PROTOCOL_A2A, Collections.singletonList(endpoint));
        firstRange.setVersionRange("[1.0.0,2.0.0]");
        service.registerAgentEndpoints(firstRange);
        waitForEndpointCount(service, reference(agentName, VERSION, null), PROTOCOL_A2A, 1);
        waitForEndpointCount(service, reference(agentName, VERSION_2, null), PROTOCOL_A2A, 1);
        waitForEndpointCount(service, reference(agentName, VERSION_3, null), PROTOCOL_A2A, 0);
        
        AgentEndpointRegistrationBatch secondRange = registration(agentName, VERSION_2,
            PROTOCOL_A2A, Collections.singletonList(endpoint));
        secondRange.setVersionRange("[2.0.0,3.0.0]");
        service.registerAgentEndpoints(secondRange);
        waitForEndpointCount(service, reference(agentName, VERSION, null), PROTOCOL_A2A, 0);
        waitForEndpointCount(service, reference(agentName, VERSION_2, null), PROTOCOL_A2A, 1);
        waitForEndpointCount(service, reference(agentName, VERSION_3, null), PROTOCOL_A2A, 1);
        
        service.deregisterAgentEndpoints(deregistration(agentName, PROTOCOL_A2A,
            Collections.singletonList(deregistrationEndpoint(endpoint))));
        waitForEndpointCount(service, reference(agentName, VERSION_3, null), PROTOCOL_A2A, 0);
    }
    
    @Test
    @EnabledIfSystemProperty(named = RECONNECT_ENABLED_PROPERTY, matches = "true")
    void shouldRestoreGrpcAndHttpPublicationsAndPollingAfterRealServerRestart()
        throws Exception {
        Path controlDirectory =
            Paths.get(System.getProperty(RECONNECT_CONTROL_DIR_PROPERTY));
        Files.createDirectories(controlDirectory);
        Path ready = controlDirectory.resolve("client-ready");
        Path serverStopped = controlDirectory.resolve("server-stopped");
        Path downObserved = controlDirectory.resolve("client-observed-down");
        Path serverRestarted = controlDirectory.resolve("server-restarted");
        Files.deleteIfExists(ready);
        Files.deleteIfExists(serverStopped);
        Files.deleteIfExists(downObserved);
        Files.deleteIfExists(serverRestarted);
        
        AgentMaintainerService maintainer = createAgentMaintainerService();
        AiService grpcService = createAiService();
        AiService httpService =
            createAiService(Constants.DEFAULT_NAMESPACE_ID, AiConstants.AI_TRANSPORT_MODE_HTTP);
        String agentName = randomServiceName("agent-real-reconnect");
        createPublishedAgent(maintainer, Constants.DEFAULT_NAMESPACE_ID, agentName,
            Collections.singletonList("java-sdk-it"), Collections.singletonList(PROTOCOL_A2A),
            false);
        Endpoint grpcVersionOne =
            endpoint(randomPort(), "/reconnect-grpc-v1", "grpc-before-restart");
        Endpoint httpVersionOne =
            endpoint(randomPort(), "/reconnect-http-v1", "http-before-restart");
        grpcService.registerAgentEndpoints(registration(agentName, VERSION, PROTOCOL_A2A,
            Collections.singletonList(grpcVersionOne)));
        httpService.registerAgentEndpoints(registration(agentName, VERSION, PROTOCOL_A2A,
            Collections.singletonList(httpVersionOne)));
        waitForEndpointCount(grpcService, reference(agentName, VERSION, null), PROTOCOL_A2A, 2);
        waitForEndpointCount(httpService, reference(agentName, VERSION, null), PROTOCOL_A2A, 2);
        assertEquals(agentName, searchOne(grpcService, agentName).getAgentName());
        assertEquals(agentName, searchOne(httpService, agentName).getAgentName());
        
        RecordingAgentListener grpcLatestListener = new RecordingAgentListener();
        RecordingAgentListener httpLatestListener = new RecordingAgentListener();
        AgentReference latestReference = reference(agentName, null, null);
        assertEquals(VERSION,
            grpcService.subscribeAgent(latestReference, grpcLatestListener).getVersion());
        assertEquals(VERSION,
            httpService.subscribeAgent(latestReference, httpLatestListener).getVersion());
        writeMarker(ready, agentName);
        
        waitForMarker(serverStopped, "external harness stops the standalone server");
        waitUntilLong("the same gRPC and HTTP SDKs observe connection unavailability",
            () -> searchUnavailable(grpcService, agentName)
                && searchUnavailable(httpService, agentName));
        writeMarker(downObserved, agentName);
        waitForMarker(serverRestarted, "external harness restarts the standalone server");
        
        waitUntilLong("the same gRPC and HTTP SDKs can Search after restart",
            () -> agentName.equals(searchOne(grpcService, agentName).getAgentName())
                && agentName.equals(searchOne(httpService, agentName).getAgentName()));
        waitUntilLong("gRPC redo and HTTP 50404 recovery restore both Version 1 publications",
            () -> {
                AgentDiscoveryResult grpcResult =
                    grpcService.discoverAgent(reference(agentName, VERSION, null));
                AgentDiscoveryResult httpResult =
                    httpService.discoverAgent(reference(agentName, VERSION, null));
                return containsEndpoint(grpcResult, PROTOCOL_A2A, grpcVersionOne.getUri())
                    && containsEndpoint(grpcResult, PROTOCOL_A2A, httpVersionOne.getUri())
                    && containsEndpoint(httpResult, PROTOCOL_A2A, grpcVersionOne.getUri())
                    && containsEndpoint(httpResult, PROTOCOL_A2A, httpVersionOne.getUri());
            });
        
        createPublishedVersion(maintainer, Constants.DEFAULT_NAMESPACE_ID, agentName, VERSION_2);
        awaitEvent(grpcLatestListener, "gRPC polling resumes with Version 2 after reconnect",
            result -> VERSION_2.equals(result.getVersion())
                && sourceEndpoints(result, PROTOCOL_A2A, EndpointSource.RUNTIME).isEmpty());
        awaitEvent(httpLatestListener, "HTTP polling resumes with Version 2 after reconnect",
            result -> VERSION_2.equals(result.getVersion())
                && sourceEndpoints(result, PROTOCOL_A2A, EndpointSource.RUNTIME).isEmpty());
        Endpoint grpcVersionTwo =
            endpoint(randomPort(), "/reconnect-grpc-v2", "grpc-after-restart");
        grpcService.registerAgentEndpoints(registration(agentName, VERSION_2, PROTOCOL_A2A,
            Collections.singletonList(grpcVersionTwo)));
        waitForEndpointCount(httpService, reference(agentName, VERSION_2, null), PROTOCOL_A2A, 1);
        awaitEvent(grpcLatestListener,
            "gRPC polling observes the Version 2 gRPC Endpoint after reconnect",
            result -> VERSION_2.equals(result.getVersion())
                && containsEndpoint(result, PROTOCOL_A2A, grpcVersionTwo.getUri()));
        awaitEvent(httpLatestListener,
            "HTTP polling observes the Version 2 gRPC Endpoint after reconnect",
            result -> VERSION_2.equals(result.getVersion())
                && containsEndpoint(result, PROTOCOL_A2A, grpcVersionTwo.getUri()));
        Endpoint httpVersionTwo =
            endpoint(randomPort(), "/reconnect-http-v2", "http-after-restart");
        httpService.registerAgentEndpoints(registration(agentName, VERSION_2, PROTOCOL_A2A,
            Collections.singletonList(httpVersionTwo)));
        waitForEndpointCount(grpcService, reference(agentName, VERSION_2, null), PROTOCOL_A2A, 2);
        awaitEvent(grpcLatestListener,
            "gRPC polling observes both Version 2 Endpoints after reconnect",
            result -> VERSION_2.equals(result.getVersion())
                && containsEndpoint(result, PROTOCOL_A2A, grpcVersionTwo.getUri())
                && containsEndpoint(result, PROTOCOL_A2A, httpVersionTwo.getUri()));
        awaitEvent(httpLatestListener,
            "HTTP polling observes both Version 2 Endpoints after reconnect",
            result -> VERSION_2.equals(result.getVersion())
                && containsEndpoint(result, PROTOCOL_A2A, grpcVersionTwo.getUri())
                && containsEndpoint(result, PROTOCOL_A2A, httpVersionTwo.getUri()));
        
        updateLabel(maintainer, Constants.DEFAULT_NAMESPACE_ID, agentName, LABEL_STABLE,
            VERSION_2);
        assertEquals(VERSION,
            grpcService.discoverAgent(reference(agentName, VERSION, null)).getVersion());
        assertEquals(VERSION,
            httpService.discoverAgent(reference(agentName, VERSION, null)).getVersion());
        assertEquals(VERSION_2,
            grpcService.discoverAgent(reference(agentName, VERSION_2, null)).getVersion());
        assertEquals(VERSION_2,
            httpService.discoverAgent(reference(agentName, VERSION_2, null)).getVersion());
        assertEquals(VERSION_2, grpcService.discoverAgent(latestReference).getVersion());
        assertEquals(VERSION_2, httpService.discoverAgent(latestReference).getVersion());
        assertEquals(VERSION_2,
            grpcService.discoverAgent(reference(agentName, null, LABEL_STABLE)).getVersion());
        assertEquals(VERSION_2,
            httpService.discoverAgent(reference(agentName, null, LABEL_STABLE)).getVersion());
        assertEquals(VERSION_2, searchOne(grpcService, agentName).getLatestVersion());
        assertEquals(VERSION_2, searchOne(httpService, agentName).getLatestVersion());
        
        grpcService.unsubscribeAgent(latestReference, grpcLatestListener);
        httpService.unsubscribeAgent(latestReference, httpLatestListener);
        grpcService.deregisterAgentEndpoints(deregistration(agentName, PROTOCOL_A2A,
            Collections.singletonList(deregistrationEndpoint(grpcVersionTwo))));
        httpService.deregisterAgentEndpoints(deregistration(agentName, PROTOCOL_A2A,
            Collections.singletonList(deregistrationEndpoint(httpVersionTwo))));
        waitForEndpointCount(grpcService, reference(agentName, VERSION_2, null), PROTOCOL_A2A, 0);
    }
    
    @Test
    void shouldDeregisterActiveHttpPublicationDuringIdempotentShutdown() throws Exception {
        AgentMaintainerService maintainer = createAgentMaintainerService();
        AiService reader = createAiService();
        AiService publisher =
            createAiService(Constants.DEFAULT_NAMESPACE_ID, AiConstants.AI_TRANSPORT_MODE_HTTP);
        String agentName = randomServiceName("agent-http-shutdown");
        createPublishedAgent(maintainer, Constants.DEFAULT_NAMESPACE_ID, agentName,
            Collections.singletonList("java-sdk-it"), Collections.singletonList(PROTOCOL_A2A),
            false);
        Endpoint endpoint = endpoint(randomPort(), "/shutdown", "shutdown");
        publisher.registerAgentEndpoints(
            registration(agentName, PROTOCOL_A2A, Collections.singletonList(endpoint)));
        waitForEndpointCount(reader, agentName, PROTOCOL_A2A, 1);
        
        publisher.shutdown();
        publisher.shutdown();
        waitForEndpointCount(reader, agentName, PROTOCOL_A2A, 0);
    }
    
    @Test
    void shouldKeepHttpAndGrpcDiscoverySemanticsEquivalent() throws Exception {
        AgentMaintainerService maintainer = createAgentMaintainerService();
        AiService grpcService = createAiService();
        AiService httpService =
            createAiService(Constants.DEFAULT_NAMESPACE_ID, AiConstants.AI_TRANSPORT_MODE_HTTP);
        String agentName = randomServiceName("agent-http-grpc");
        createPublishedAgent(maintainer, Constants.DEFAULT_NAMESPACE_ID, agentName,
            Arrays.asList("java-sdk-it", "transport"), Collections.singletonList(PROTOCOL_A2A),
            false);
        
        AgentSearchRequest search = new AgentSearchRequest();
        search.setAgentNameContains(agentName);
        assertEquals(1, grpcService.searchAgents(search).getTotalCount());
        assertEquals(1, httpService.searchAgents(search).getTotalCount());
        
        Endpoint endpoint = endpoint(randomPort(), "/http", "http");
        httpService.registerAgentEndpoints(
            registration(agentName, PROTOCOL_A2A, Collections.singletonList(endpoint)));
        waitForEndpointCount(grpcService, agentName, PROTOCOL_A2A, 1);
        AgentDiscoveryResult grpcResult =
            grpcService.discoverAgent(reference(agentName, null, null));
        AgentDiscoveryResult httpResult =
            httpService.discoverAgent(reference(agentName, null, null));
        assertEquals(grpcResult.getVersion(), httpResult.getVersion());
        assertEquals(grpcResult.getContentDigest(), httpResult.getContentDigest());
        assertEquals(sourceEndpoints(grpcResult, PROTOCOL_A2A, EndpointSource.RUNTIME).get(0)
                .getUri(),
            sourceEndpoints(httpResult, PROTOCOL_A2A, EndpointSource.RUNTIME).get(0).getUri());
        
        httpService.deregisterAgentEndpoints(
            deregistration(agentName, PROTOCOL_A2A,
                Collections.singletonList(deregistrationEndpoint(endpoint))));
        waitForEndpointCount(grpcService, agentName, PROTOCOL_A2A, 0);
    }
    
    @Test
    void shouldRejectInvalidBoundariesBeforeRemoteMutation() throws Exception {
        AiService service = createAiService();
        assertInvalid(() -> service.searchAgents(null));
        
        AgentSearchRequest invalidPage = new AgentSearchRequest();
        invalidPage.setPageNo(0);
        assertInvalid(() -> service.searchAgents(invalidPage));
        AgentSearchRequest invalidPageSize = new AgentSearchRequest();
        invalidPageSize.setPageSize(0);
        assertInvalid(() -> service.searchAgents(invalidPageSize));
        AgentSearchRequest duplicateTags = new AgentSearchRequest();
        duplicateTags.setTagsAll(Arrays.asList("duplicate", "duplicate"));
        assertInvalid(() -> service.searchAgents(duplicateTags));
        AgentSearchRequest duplicateProtocols = new AgentSearchRequest();
        duplicateProtocols.setProtocolsAny(Arrays.asList(PROTOCOL_A2A, PROTOCOL_A2A));
        assertInvalid(() -> service.searchAgents(duplicateProtocols));
        AgentSearchRequest invalidProtocol = new AgentSearchRequest();
        invalidProtocol.setProtocolsAny(Collections.singletonList("not a protocol"));
        assertInvalid(() -> service.searchAgents(invalidProtocol));
        
        assertInvalid(() -> service.discoverAgent((AgentReference) null));
        AgentReference ambiguous = reference(randomServiceName("agent-invalid"), VERSION,
            LABEL_STABLE);
        assertInvalid(() -> service.discoverAgent(ambiguous));
        AgentSearchRequest mismatched = new AgentSearchRequest();
        mismatched.setNamespaceId(randomServiceName("another-namespace"));
        assertInvalid(() -> service.searchAgents(mismatched));
        
        String agentName = randomServiceName("agent-invalid-publication");
        AgentEndpointRegistrationBatch empty =
            registration(agentName, PROTOCOL_A2A, Collections.<Endpoint>emptyList());
        assertInvalid(() -> service.registerAgentEndpoints(empty));
        Endpoint duplicate = endpoint(randomPort(), "/duplicate", "duplicate");
        assertInvalid(() -> service.registerAgentEndpoints(
            registration(agentName, PROTOCOL_A2A, Arrays.asList(duplicate, duplicate))));
        Endpoint unhealthyInput = endpoint(randomPort(), "/healthy", "healthy");
        unhealthyInput.setHealthy(Boolean.TRUE);
        assertInvalid(() -> service.registerAgentEndpoints(
            registration(agentName, PROTOCOL_A2A,
                Collections.singletonList(unhealthyInput))));
        Endpoint invalidUri = endpoint(randomPort(), "/invalid-uri", "invalid-uri");
        invalidUri.setUri("not-a-uri");
        assertInvalid(() -> service.registerAgentEndpoints(
            registration(agentName, PROTOCOL_A2A, Collections.singletonList(invalidUri))));
        Endpoint invalidTransport = endpoint(randomPort(), "/invalid-transport",
            "invalid-transport");
        invalidTransport.setTransport("invalid transport");
        assertInvalid(() -> service.registerAgentEndpoints(
            registration(agentName, PROTOCOL_A2A, Collections.singletonList(invalidTransport))));
        AgentEndpointRegistrationBatch invalidVersion =
            registration(agentName, PROTOCOL_A2A, Collections.singletonList(duplicate));
        invalidVersion.setRuntimeVersion("not-semver");
        assertInvalid(() -> service.registerAgentEndpoints(invalidVersion));
        AgentEndpointRegistrationBatch invalidRange =
            registration(agentName, PROTOCOL_A2A, Collections.singletonList(duplicate));
        invalidRange.setVersionRange("[2.0.0,1.0.0]");
        assertInvalid(() -> service.registerAgentEndpoints(invalidRange));
        
        Endpoint invalidDeregistration = deregistrationEndpoint(duplicate);
        invalidDeregistration.setPriority(1);
        assertInvalid(() -> service.deregisterAgentEndpoints(
            deregistration(agentName, PROTOCOL_A2A,
                Collections.singletonList(invalidDeregistration))));
        service.deregisterAgentEndpoints(
            deregistration(agentName, PROTOCOL_A2A,
                Collections.singletonList(deregistrationEndpoint(duplicate))));
        
        NacosException missing = assertThrows(NacosException.class,
            () -> service.discoverAgent(reference(agentName, null, null)));
        assertEquals(NacosException.NOT_FOUND, missing.getErrCode());
    }

    private JsonNode getConsoleAgent(String path, String agentName, String version)
        throws Exception {
        StringBuilder query = new StringBuilder("namespaceId=")
            .append(encode(Constants.DEFAULT_NAMESPACE_ID)).append("&agentName=")
            .append(encode(agentName));
        if (version != null) {
            query.append("&version=").append(encode(version));
        }
        if (path.endsWith("/runtime-endpoints")) {
            query.append("&protocol=").append(PROTOCOL_A2A);
        }
        HttpURLConnection connection = (HttpURLConnection) new URL(
            CONSOLE_BASE_URL + path + '?' + query).openConnection();
        connection.setConnectTimeout(DEFAULT_TIMEOUT_MS);
        connection.setReadTimeout(DEFAULT_TIMEOUT_MS);
        connection.setRequestMethod("GET");
        try {
            int responseCode = connection.getResponseCode();
            try (InputStream input = responseCode >= 400 ? connection.getErrorStream()
                : connection.getInputStream()) {
                String body = input == null ? "" : new String(input.readAllBytes(),
                    StandardCharsets.UTF_8);
                assertEquals(HttpURLConnection.HTTP_OK, responseCode, body);
                JsonNode root = JacksonUtils.toObj(body);
                assertEquals(0, root.get("code").asInt(), root.toString());
                return root.get("data");
            }
        } finally {
            connection.disconnect();
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private com.alibaba.nacos.api.ai.model.a2a.AgentEndpoint legacyEndpoint(String version) {
        com.alibaba.nacos.api.ai.model.a2a.AgentEndpoint result =
            new com.alibaba.nacos.api.ai.model.a2a.AgentEndpoint();
        result.setVersion(version);
        result.setAddress("127.0.0.1");
        result.setPort(randomPort());
        result.setTransport(AiConstants.A2a.A2A_ENDPOINT_DEFAULT_TRANSPORT);
        result.setProtocolVersion("1.0");
        result.setPath("/legacy-a2a");
        return result;
    }

    private boolean containsLegacyEndpoint(AgentCardDetailInfo detail,
        com.alibaba.nacos.api.ai.model.a2a.AgentEndpoint endpoint) {
        String expectedUrl = "http://" + endpoint.getAddress() + ':' + endpoint.getPort()
            + endpoint.getPath();
        return detail.getSupportedInterfaces() != null
            && detail.getSupportedInterfaces().stream()
                .anyMatch(each -> expectedUrl.equals(each.getUrl())
                    && endpoint.getTransport().equals(each.getProtocolBinding())
                    && endpoint.getProtocolVersion().equals(each.getProtocolVersion()));
    }
    
    private AgentMaintainerService createAgentMaintainerService() throws NacosException {
        Properties properties = sdkProperties();
        properties.setProperty(PropertyKeyConst.CONTEXT_PATH, "/nacos");
        return AiMaintainerFactory.createAiMaintainerService(properties).agent();
    }
    
    private AiService createAiService(String namespaceId, String transport) throws NacosException {
        Properties properties = sdkProperties();
        properties.setProperty(PropertyKeyConst.NAMESPACE, namespaceId);
        if (transport != null) {
            properties.setProperty(AiConstants.AI_TRANSPORT_MODE, transport);
        }
        AiService result = AiFactory.createAiService(properties);
        addCleanup(result::shutdown);
        return result;
    }
    
    private void createPublishedAgent(AgentMaintainerService maintainer, String namespaceId,
        String agentName, List<String> tags, List<String> protocols, boolean declaredEndpoint)
        throws NacosException {
        AgentDraftCreateRequest request = new AgentDraftCreateRequest();
        request.setAgentName(agentName);
        request.setDisplayName("Display " + agentName);
        request.setDescription("Java SDK Agent discovery integration test");
        AgentProvider provider = new AgentProvider();
        provider.setName("Nacos Java SDK IT");
        provider.setUrl("https://nacos.io");
        request.setProvider(provider);
        request.setTags(tags);
        request.setVersion(VERSION);
        List<AgentCallInterface> interfaces = new ArrayList<>();
        for (String protocol : protocols) {
            interfaces.add(callInterface(agentName, protocol, declaredEndpoint));
        }
        request.setCallInterfaces(interfaces);
        request.setAuthor("java-sdk-it");
        request.setChangeDescription("create Agent for Java SDK integration tests");
        maintainer.createDraft(namespaceId, request);
        addCleanup(() -> maintainer.deleteAgent(namespaceId, agentName));
        maintainer.forcePublish(namespaceId, versionCommand(agentName, VERSION));
        updateLabel(maintainer, namespaceId, agentName, LABEL_STABLE, VERSION);
    }
    
    private void createPublishedVersion(AgentMaintainerService maintainer, String namespaceId,
        String agentName, String version) throws NacosException {
        AgentDraftCreateRequest request = new AgentDraftCreateRequest();
        request.setAgentName(agentName);
        request.setVersion(version);
        request.setCallInterfaces(Collections.singletonList(
            callInterface(agentName, PROTOCOL_A2A, false, version)));
        request.setAuthor("java-sdk-it");
        request.setChangeDescription("publish Agent Version " + version);
        maintainer.createDraft(namespaceId, request);
        maintainer.forcePublish(namespaceId, versionCommand(agentName, version));
    }
    
    private void updateLabel(AgentMaintainerService maintainer, String namespaceId,
        String agentName, String label, String version) throws NacosException {
        AgentLabelsUpdateRequest labels = new AgentLabelsUpdateRequest();
        labels.setAgentName(agentName);
        labels.setLabels(Collections.singletonMap(label, version));
        maintainer.updateLabels(namespaceId, labels);
    }
    
    private AgentCallInterface callInterface(String agentName, String protocol,
        boolean declaredEndpoint) {
        return callInterface(agentName, protocol, declaredEndpoint, VERSION);
    }
    
    private AgentCallInterface callInterface(String agentName, String protocol,
        boolean declaredEndpoint, String version) {
        AgentCallInterface result = new AgentCallInterface();
        result.setProtocol(protocol);
        result.setProtocolVersion("1.0");
        result.setDescriptorMediaType("application/json");
        Map<String, Object> descriptor = new HashMap<>();
        descriptor.put("agentName", agentName);
        descriptor.put("protocol", protocol);
        descriptor.put("version", version);
        result.setNativeDescriptor(descriptor);
        if (declaredEndpoint && PROTOCOL_A2A.equals(protocol)) {
            result.setEndpointSourceOrder(Arrays.asList(EndpointSource.RUNTIME,
                EndpointSource.DECLARED));
            Endpoint endpoint = endpoint(randomPort(), "/declared", "declared");
            endpoint.setMetadata(Collections.singletonMap("region", "declared"));
            result.setDeclaredEndpoints(Collections.singletonList(endpoint));
        } else {
            result.setEndpointSourceOrder(Collections.singletonList(EndpointSource.RUNTIME));
        }
        return result;
    }

    private AgentCard legacyCompatibleAgentCard(String agentName, String version,
        String description) {
        AgentInterface agentInterface = new AgentInterface();
        agentInterface.setUrl("https://example.com/" + agentName + "/a2a");
        agentInterface.setProtocolBinding("HTTP+JSON");
        agentInterface.setProtocolVersion("1.0");
        AgentCapabilities capabilities = new AgentCapabilities();
        capabilities.setStreaming(Boolean.TRUE);
        AgentCard result = new AgentCard();
        result.setName(agentName);
        result.setVersion(version);
        result.setDescription(description);
        result.setSupportedInterfaces(Collections.singletonList(agentInterface));
        result.setCapabilities(capabilities);
        return result;
    }

    private AgentCallInterface legacyCompatibleCallInterface(AgentCard card) {
        AgentInterface agentInterface = card.getSupportedInterfaces().get(0);
        Endpoint declaredEndpoint = new Endpoint();
        declaredEndpoint.setUri(agentInterface.getUrl());
        declaredEndpoint.setTransport(agentInterface.getProtocolBinding());
        AgentCallInterface result = new AgentCallInterface();
        result.setProtocol(PROTOCOL_A2A);
        result.setProtocolVersion(agentInterface.getProtocolVersion());
        result.setDescriptorMediaType("application/json");
        result.setNativeDescriptor(JacksonUtils.toObj(JacksonUtils.toJson(card), Map.class));
        result.setEndpointSourceOrder(Arrays.asList(EndpointSource.DECLARED,
            EndpointSource.RUNTIME));
        result.setDeclaredEndpoints(Collections.singletonList(declaredEndpoint));
        return result;
    }
    
    private AgentVersionCommand versionCommand(String agentName, String version) {
        AgentVersionCommand result = new AgentVersionCommand();
        result.setAgentName(agentName);
        result.setVersion(version);
        return result;
    }
    
    private AgentReference reference(String agentName, String version, String label) {
        AgentReference result = new AgentReference();
        result.setAgentName(agentName);
        result.setVersion(version);
        result.setLabel(label);
        return result;
    }
    
    private AgentEndpointRegistrationBatch registration(String agentName, String protocol,
        List<Endpoint> endpoints) {
        return registration(agentName, VERSION, protocol, endpoints);
    }
    
    private AgentEndpointRegistrationBatch registration(String agentName, String runtimeVersion,
        String protocol, List<Endpoint> endpoints) {
        AgentEndpointRegistrationBatch result = new AgentEndpointRegistrationBatch();
        result.setAgentName(agentName);
        result.setRuntimeVersion(runtimeVersion);
        result.setProtocol(protocol);
        result.setEndpoints(endpoints);
        return result;
    }
    
    private AgentEndpointDeregistrationBatch deregistration(String agentName, String protocol,
        List<Endpoint> endpoints) {
        AgentEndpointDeregistrationBatch result = new AgentEndpointDeregistrationBatch();
        result.setAgentName(agentName);
        result.setProtocol(protocol);
        result.setEndpoints(endpoints);
        return result;
    }
    
    private Endpoint endpoint(int port, String path, String region) {
        Endpoint result = new Endpoint();
        result.setUri("http://127.0.0.1:" + port + path);
        result.setTransport(TRANSPORT_HTTP);
        result.setPriority(0);
        result.setWeight(1D);
        result.setMetadata(Collections.singletonMap("region", region));
        return result;
    }
    
    private Endpoint deregistrationEndpoint(Endpoint source) {
        Endpoint result = new Endpoint();
        result.setUri(source.getUri());
        result.setTransport(source.getTransport());
        return result;
    }
    
    private String replacePath(String uri, String replacement) {
        int pathStart = uri.indexOf('/', uri.indexOf("://") + 3);
        return (pathStart < 0 ? uri : uri.substring(0, pathStart)) + replacement;
    }
    
    private void waitForEndpointCount(AiService service, String agentName, String protocol,
        int expected) throws Exception {
        waitForEndpointCount(service, reference(agentName, null, null), protocol, expected);
    }
    
    private void waitForEndpointCount(AiService service, AgentReference reference,
        String protocol, int expected) throws Exception {
        waitUntil("expected " + expected + " " + protocol + " Runtime Endpoints",
            () -> sourceEndpoints(service.discoverAgent(reference), protocol,
                EndpointSource.RUNTIME).size() == expected);
    }
    
    private List<Endpoint> sourceEndpoints(AgentDiscoveryResult result, String protocol,
        EndpointSource source) {
        if (result == null || result.getCallInterfaces() == null) {
            return Collections.emptyList();
        }
        for (AgentDiscoveryCallInterface callInterface : result.getCallInterfaces()) {
            if (!protocol.equals(callInterface.getProtocol())
                || callInterface.getEndpointSets() == null) {
                continue;
            }
            for (EndpointSet endpointSet : callInterface.getEndpointSets()) {
                if (source == endpointSet.getSource()) {
                    return endpointSet.getEndpoints() == null ? Collections.<Endpoint>emptyList()
                        : endpointSet.getEndpoints();
                }
            }
        }
        return Collections.emptyList();
    }
    
    private boolean containsEndpoint(AgentDiscoveryResult result, String protocol, String uri) {
        String authority = uri.substring(0, uri.indexOf('/', uri.indexOf("://") + 3));
        for (Endpoint endpoint :
            sourceEndpoints(result, protocol, EndpointSource.RUNTIME)) {
            if (endpoint.getUri().startsWith(authority)) {
                return true;
            }
        }
        return false;
    }
    
    private AgentCatalogEntry searchOne(AiService service, String agentName)
        throws NacosException {
        AgentSearchRequest request = new AgentSearchRequest();
        request.setAgentNameContains(agentName);
        Page<AgentCatalogEntry> result = service.searchAgents(request);
        for (AgentCatalogEntry entry : result.getPageItems()) {
            if (agentName.equals(entry.getAgentName())) {
                return entry;
            }
        }
        return null;
    }
    
    private boolean searchUnavailable(AiService service, String agentName) {
        try {
            searchOne(service, agentName);
            return false;
        } catch (NacosException e) {
            return true;
        }
    }
    
    private AgentDiscoveryResult awaitEvent(RecordingAgentListener listener, String reason,
        Predicate<AgentDiscoveryResult> predicate) throws Exception {
        long deadline = System.currentTimeMillis() + POLLING_TIMEOUT_MILLIS;
        while (System.currentTimeMillis() < deadline) {
            long remaining = deadline - System.currentTimeMillis();
            AgentDiscoveryResult result =
                listener.events.poll(Math.max(1L, remaining), TimeUnit.MILLISECONDS);
            if (result != null && predicate.test(result)) {
                return result;
            }
        }
        fail(reason);
        return null;
    }
    
    private void waitUntilLong(String reason, CheckedCondition condition) throws Exception {
        long deadline = System.currentTimeMillis() + RECONNECT_TIMEOUT_MILLIS;
        Throwable lastFailure = null;
        while (System.currentTimeMillis() < deadline) {
            try {
                if (condition.evaluate()) {
                    return;
                }
            } catch (Throwable throwable) {
                lastFailure = throwable;
            }
            Thread.sleep(1000L);
        }
        if (lastFailure == null) {
            fail(reason);
        }
        fail(reason + ", last failure: " + lastFailure.getMessage(), lastFailure);
    }
    
    private void waitForMarker(Path marker, String reason) throws Exception {
        waitUntilLong(reason, () -> Files.isRegularFile(marker));
    }
    
    private void writeMarker(Path marker, String value) throws Exception {
        Files.write(marker, Collections.singletonList(value), StandardCharsets.UTF_8);
    }
    
    private void assertInvalid(CheckedAction action) {
        NacosException exception = assertThrows(NacosException.class, action::run);
        assertEquals(NacosException.INVALID_PARAM, exception.getErrCode(), exception.toString());
    }
    
    private static final class RecordingAgentListener
        extends AbstractNacosAgentDiscoveryListener {
        
        private final BlockingQueue<AgentDiscoveryResult> events =
            new LinkedBlockingQueue<>();
        
        @Override
        public void onEvent(NacosAgentDiscoveryEvent event) {
            events.add(event.getAgentDiscoveryResult());
        }
    }
    
    @FunctionalInterface
    private interface CheckedAction {
        
        void run() throws Exception;
    }
}
