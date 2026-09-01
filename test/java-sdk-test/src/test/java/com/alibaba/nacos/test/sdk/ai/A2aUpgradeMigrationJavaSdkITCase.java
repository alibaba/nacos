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
import com.alibaba.nacos.api.ai.model.a2a.AgentCapabilities;
import com.alibaba.nacos.api.ai.model.a2a.AgentCard;
import com.alibaba.nacos.api.ai.model.a2a.AgentEndpoint;
import com.alibaba.nacos.api.ai.model.a2a.AgentInterface;
import com.alibaba.nacos.api.ai.model.agent.Endpoint;
import com.alibaba.nacos.api.ai.model.agent.EndpointSource;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryCallInterface;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryResult;
import com.alibaba.nacos.api.ai.model.rad.AgentReference;
import com.alibaba.nacos.api.ai.model.rad.EndpointSet;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.maintainer.client.ai.A2aMaintainerService;
import com.alibaba.nacos.maintainer.client.ai.AiMaintainerFactory;
import com.alibaba.nacos.test.sdk.JavaSdkBaseITCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Real-client integration scenarios for temporary Nacos 3.0-3.2 A2A Runtime migration.
 *
 * <p>The dedicated workflow runs this class against an {@code AUTO/SYNCING} server. Each old
 * {@link AiService} Endpoint publication must be represented by both the historical exact-Version
 * Naming service and the canonical RAD Runtime service while consuming one logical capacity
 * reservation.</p>
 *
 * @author Nacos
 */
class A2aUpgradeMigrationJavaSdkITCase extends JavaSdkBaseITCase {

    private static final String VERSION_ONE = "1.0.0";

    private static final String VERSION_TWO = "2.0.0";

    private static final String A2A_PROTOCOL = "a2a";

    private static final String ENDPOINT_GROUP = "agent-endpoints";

    private static final String SERVER_CAPACITY_PROPERTY =
        "nacos.agent.it.server.publication.capacity";

    private static final String RECONNECT_ENABLED_PROPERTY =
        "nacos.a2a.migration.reconnect.enabled";

    private static final String RECONNECT_CONTROL_DIRECTORY_PROPERTY =
        "nacos.a2a.migration.reconnect.control.dir";

    private static final long RECONNECT_TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(2);

    @Test
    void shouldDualMaterializeReplaceDeregisterAndChargeCapacityOnce() throws Exception {
        int serverCapacity = Integer.getInteger(SERVER_CAPACITY_PROPERTY, 3);
        assertEquals(3, serverCapacity,
            "the migration capacity scenario requires the it-new three-Endpoint fixture");
        Properties properties = sdkProperties();
        properties.setProperty(AiConstants.AI_AGENT_ENDPOINT_MAX_PUBLICATIONS,
            String.valueOf(serverCapacity + 3));
        AiService service = createAiService(properties);
        NamingService namingService = createNamingService();
        A2aMaintainerService maintainer = createA2aMaintainerService();
        String agentName = randomUnencodedAgentName("migration-runtime");
        String overflowAgent = randomUnencodedAgentName("migration-overflow");
        releaseVersions(service, maintainer, agentName, VERSION_ONE, VERSION_TWO);
        releaseVersions(service, maintainer, overflowAgent, VERSION_ONE);

        AgentEndpoint initial = endpoint(VERSION_ONE, randomPort(), "/initial");
        service.registerAgentEndpoint(agentName, initial);
        addCleanup(() -> service.deregisterAgentEndpoint(agentName, initial));
        awaitLayouts(service, namingService, agentName, VERSION_ONE, 1);

        AgentEndpoint first = endpoint(VERSION_ONE, randomPort(), "/batch-first");
        AgentEndpoint second = endpoint(VERSION_ONE, randomPort(), "/batch-second");
        List<AgentEndpoint> replacement = Arrays.asList(first, second);
        service.registerAgentEndpoint(agentName, replacement);
        awaitLayouts(service, namingService, agentName, VERSION_ONE, 2);
        assertLayoutUris(service, namingService, agentName, VERSION_ONE,
            Arrays.asList(uri(first), uri(second)));

        AgentEndpoint versionTwo = endpoint(VERSION_TWO, randomPort(), "/version-two");
        service.registerAgentEndpoint(agentName, versionTwo);
        addCleanup(() -> service.deregisterAgentEndpoint(agentName, versionTwo));
        awaitLayouts(service, namingService, agentName, VERSION_TWO, 1);
        awaitLayouts(service, namingService, agentName, VERSION_ONE, 2);

        AgentEndpoint rejectedEndpoint = endpoint(VERSION_ONE, randomPort(), "/rejected");
        NacosException rejected = assertThrows(NacosException.class,
            () -> service.registerAgentEndpoint(overflowAgent, rejectedEndpoint));
        assertEquals(NacosException.OVER_THRESHOLD, rejected.getErrCode());
        assertTrue(rejected.getMessage().contains("publication limit"), rejected.getMessage());

        service.deregisterAgentEndpoint(agentName, first);
        awaitLayouts(service, namingService, agentName, VERSION_ONE, 0);
        service.registerAgentEndpoint(overflowAgent, rejectedEndpoint);
        addCleanup(() -> service.deregisterAgentEndpoint(overflowAgent, rejectedEndpoint));
        awaitLayouts(service, namingService, overflowAgent, VERSION_ONE, 1);
        assertLayoutUris(service, namingService, overflowAgent, VERSION_ONE,
            Collections.singletonList(uri(rejectedEndpoint)));
    }

    @Test
    void shouldCleanBothLayoutsOnDisconnectAndAllowFreshPublisher() throws Exception {
        AiService definitionClient = createAiService();
        AiService publisher = createAiService();
        NamingService namingService = createNamingService();
        A2aMaintainerService maintainer = createA2aMaintainerService();
        String agentName = randomUnencodedAgentName("migration-disconnect");
        releaseVersions(definitionClient, maintainer, agentName, VERSION_ONE);
        AgentEndpoint endpoint = endpoint(VERSION_ONE, randomPort(), "/disconnect");

        publisher.registerAgentEndpoint(agentName, endpoint);
        awaitLayouts(definitionClient, namingService, agentName, VERSION_ONE, 1);
        publisher.shutdown();
        awaitLayouts(definitionClient, namingService, agentName, VERSION_ONE, 0);

        AiService replacementPublisher = createAiService();
        replacementPublisher.registerAgentEndpoint(agentName, endpoint);
        addCleanup(() -> replacementPublisher.deregisterAgentEndpoint(agentName, endpoint));
        awaitLayouts(definitionClient, namingService, agentName, VERSION_ONE, 1);
    }

    @Test
    @EnabledIfSystemProperty(named = RECONNECT_ENABLED_PROPERTY, matches = "true")
    void shouldRedoBothLayoutsAfterRealServerRestart() throws Exception {
        Path controlDirectory = Paths.get(
            System.getProperty(RECONNECT_CONTROL_DIRECTORY_PROPERTY));
        Files.createDirectories(controlDirectory);
        Path ready = controlDirectory.resolve("client-ready");
        Path stopped = controlDirectory.resolve("server-stopped");
        Path restarted = controlDirectory.resolve("server-restarted");
        Files.deleteIfExists(ready);
        Files.deleteIfExists(stopped);
        Files.deleteIfExists(restarted);

        AiService service = createAiService();
        NamingService namingService = createNamingService();
        A2aMaintainerService maintainer = createA2aMaintainerService();
        String agentName = randomUnencodedAgentName("migration-reconnect");
        releaseVersions(service, maintainer, agentName, VERSION_ONE, VERSION_TWO);
        AgentEndpoint first = endpoint(VERSION_ONE, randomPort(), "/before-restart");
        AgentEndpoint second = endpoint(VERSION_TWO, randomPort(), "/before-restart-vtwo");
        service.registerAgentEndpoint(agentName, first);
        service.registerAgentEndpoint(agentName, second);
        addCleanup(() -> service.deregisterAgentEndpoint(agentName, first));
        addCleanup(() -> service.deregisterAgentEndpoint(agentName, second));
        awaitLayouts(service, namingService, agentName, VERSION_ONE, 1);
        awaitLayouts(service, namingService, agentName, VERSION_TWO, 1);
        writeMarker(ready, agentName);

        waitForMarker(stopped, "the external fixture stops the AUTO server");
        waitForMarker(restarted, "the external fixture restarts the AUTO server");
        awaitLayoutsLong(service, namingService, agentName, VERSION_ONE, 1);
        awaitLayoutsLong(service, namingService, agentName, VERSION_TWO, 1);
        assertLayoutUris(service, namingService, agentName, VERSION_ONE,
            Collections.singletonList(uri(first)));
        assertLayoutUris(service, namingService, agentName, VERSION_TWO,
            Collections.singletonList(uri(second)));

        service.deregisterAgentEndpoint(agentName, first);
        awaitLayouts(service, namingService, agentName, VERSION_ONE, 0);
        awaitLayouts(service, namingService, agentName, VERSION_TWO, 1);
    }

    private void releaseVersions(AiService service, A2aMaintainerService maintainer,
        String agentName, String... versions) throws Exception {
        for (int i = 0; i < versions.length; i++) {
            service.releaseAgentCard(agentCard(agentName, versions[i]),
                AiConstants.A2a.A2A_ENDPOINT_TYPE_URL, i == 0);
        }
        addCleanup(() -> maintainer.deleteAgent(agentName, Constants.DEFAULT_NAMESPACE_ID));
    }

    private AgentCard agentCard(String agentName, String version) {
        AgentInterface declared = new AgentInterface();
        declared.setUrl("https://example.com/" + agentName + '/' + version);
        declared.setProtocolBinding(AiConstants.A2a.A2A_ENDPOINT_DEFAULT_TRANSPORT);
        declared.setProtocolVersion("1.0");
        AgentCapabilities capabilities = new AgentCapabilities();
        capabilities.setStreaming(true);
        AgentCard result = new AgentCard();
        result.setName(agentName);
        result.setVersion(version);
        result.setDescription("Historical A2A migration Java SDK IT");
        result.setSupportedInterfaces(Collections.singletonList(declared));
        result.setCapabilities(capabilities);
        return result;
    }

    private AgentEndpoint endpoint(String version, int port, String path) {
        AgentEndpoint result = new AgentEndpoint();
        result.setVersion(version);
        result.setAddress("127.0.0.1");
        result.setPort(port);
        result.setPath(path);
        result.setTransport(AiConstants.A2a.A2A_ENDPOINT_DEFAULT_TRANSPORT);
        result.setProtocolVersion("1.0");
        return result;
    }

    private void awaitLayouts(AiService service, NamingService namingService, String agentName,
        String version, int expected) throws Exception {
        waitUntil("both migration Runtime layouts should contain " + expected + " Endpoints",
            () -> historicalInstances(namingService, agentName, version).size() == expected
                && runtimeEndpoints(service.discoverAgent(reference(agentName, version)))
                    .size() == expected);
    }

    private void awaitLayoutsLong(AiService service, NamingService namingService,
        String agentName, String version, int expected) throws Exception {
        long deadline = System.currentTimeMillis() + RECONNECT_TIMEOUT_MILLIS;
        Throwable lastFailure = null;
        while (System.currentTimeMillis() < deadline) {
            try {
                if (historicalInstances(namingService, agentName, version).size() == expected
                    && runtimeEndpoints(service.discoverAgent(reference(agentName, version)))
                        .size() == expected) {
                    return;
                }
            } catch (Throwable throwable) {
                lastFailure = throwable;
            }
            Thread.sleep(500L);
        }
        AssertionError failure = new AssertionError(
            "both migration Runtime layouts did not recover after restart");
        if (lastFailure != null) {
            failure.initCause(lastFailure);
        }
        throw failure;
    }

    private void assertLayoutUris(AiService service, NamingService namingService,
        String agentName, String version, List<String> expectedUris) throws Exception {
        List<String> historical = new ArrayList<String>();
        for (Instance instance : historicalInstances(namingService, agentName, version)) {
            historical.add("http://" + instance.getIp() + ':' + instance.getPort()
                + instance.getMetadata().get("__nacos.agent.endpoint.path__"));
        }
        List<String> canonical = new ArrayList<String>();
        for (Endpoint endpoint : runtimeEndpoints(service.discoverAgent(
            reference(agentName, version)))) {
            canonical.add(endpoint.getUri());
        }
        Collections.sort(historical);
        Collections.sort(canonical);
        List<String> expected = new ArrayList<String>(expectedUris);
        Collections.sort(expected);
        assertEquals(expected, historical);
        assertEquals(expected, canonical);
    }

    private List<Instance> historicalInstances(NamingService namingService, String agentName,
        String version) throws NacosException {
        return namingService.getAllInstances(agentName + "::" + version, ENDPOINT_GROUP);
    }

    private List<Endpoint> runtimeEndpoints(AgentDiscoveryResult result) {
        if (result == null || result.getCallInterfaces() == null) {
            return Collections.emptyList();
        }
        for (AgentDiscoveryCallInterface callInterface : result.getCallInterfaces()) {
            if (!A2A_PROTOCOL.equals(callInterface.getProtocol())
                || callInterface.getEndpointSets() == null) {
                continue;
            }
            for (EndpointSet endpointSet : callInterface.getEndpointSets()) {
                if (EndpointSource.RUNTIME == endpointSet.getSource()) {
                    return endpointSet.getEndpoints() == null ? Collections.emptyList()
                        : new ArrayList<Endpoint>(endpointSet.getEndpoints());
                }
            }
        }
        return Collections.emptyList();
    }

    private AgentReference reference(String agentName, String version) {
        AgentReference result = new AgentReference();
        result.setAgentName(agentName);
        result.setVersion(version);
        return result;
    }

    private String uri(AgentEndpoint endpoint) {
        return "http://" + endpoint.getAddress() + ':' + endpoint.getPort()
            + endpoint.getPath();
    }

    private String randomUnencodedAgentName(String prefix) {
        String source = UUID.randomUUID().toString();
        StringBuilder result = new StringBuilder(prefix).append('-');
        for (int i = 0; i < source.length(); i++) {
            char each = source.charAt(i);
            result.append(Character.isDigit(each) ? (char) ('g' + each - '0') : each);
        }
        return result.toString();
    }

    private A2aMaintainerService createA2aMaintainerService() throws NacosException {
        Properties properties = sdkProperties();
        properties.setProperty(PropertyKeyConst.CONTEXT_PATH, "/nacos");
        return AiMaintainerFactory.createAiMaintainerService(properties).a2a();
    }

    private void writeMarker(Path path, String value) throws Exception {
        Files.write(path, value.getBytes(StandardCharsets.UTF_8));
    }

    private void waitForMarker(Path path, String reason) throws Exception {
        long deadline = System.currentTimeMillis() + RECONNECT_TIMEOUT_MILLIS;
        while (System.currentTimeMillis() < deadline) {
            if (Files.exists(path)) {
                return;
            }
            Thread.sleep(250L);
        }
        fail("Timed out waiting for " + reason + ": " + path);
    }
}
