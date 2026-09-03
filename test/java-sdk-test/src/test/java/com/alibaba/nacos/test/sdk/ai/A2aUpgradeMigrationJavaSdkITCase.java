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
import com.alibaba.nacos.api.ai.AgentTransportMode;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.listener.AbstractNacosAgentDiscoveryListener;
import com.alibaba.nacos.api.ai.listener.NacosAgentDiscoveryEvent;
import com.alibaba.nacos.api.ai.model.a2a.AgentCapabilities;
import com.alibaba.nacos.api.ai.model.a2a.AgentCard;
import com.alibaba.nacos.api.ai.model.a2a.AgentEndpoint;
import com.alibaba.nacos.api.ai.model.a2a.AgentInterface;
import com.alibaba.nacos.api.ai.model.agent.Endpoint;
import com.alibaba.nacos.api.ai.model.agent.EndpointSource;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryCallInterface;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryResult;
import com.alibaba.nacos.api.ai.model.rad.AgentReference;
import com.alibaba.nacos.api.ai.model.rad.AgentSearchRequest;
import com.alibaba.nacos.api.ai.model.rad.EndpointSet;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.ConfigFactory;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.NamingFactory;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.common.utils.JacksonUtils;
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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
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

    private static final String MIGRATION_MARKER_DATA_ID = "nacos.ai.a2a.migration.v1";

    private static final String MIGRATION_INTERNAL_GROUP = "nacos_internal";

    private static final String MIGRATION_INTERNAL_NAMESPACE = "_nacos_internal_";

    private static final String CUTOVER_BLOCKER_DATA_ID =
        "nacos.ai.a2a.migration.cutover.blocker";

    private static final String HISTORICAL_AGENT_GROUP = "agent";

    // This scenario queries the historical Naming service directly, so use an identity that
    // never requires the server-only historical Agent codec.
    private static final String CUTOVER_AGENT = "migration-cutover-it";

    private static final String CUTOVER_ENABLED_PROPERTY =
        "nacos.a2a.migration.cutover.enabled";

    private static final String CUTOVER_SHADOW_ENABLED_PROPERTY =
        "nacos.a2a.migration.cutover.shadow-enabled";

    private static final String TERMINAL_ROLLBACK_ENABLED_PROPERTY =
        "nacos.a2a.migration.terminal-rollback.enabled";

    private static final String SERVER_CAPACITY_PROPERTY =
        "nacos.agent.it.server.publication.capacity";

    private static final String SYNCING_ENABLED_PROPERTY =
        "nacos.a2a.migration.syncing.enabled";

    private static final String RECONNECT_ENABLED_PROPERTY =
        "nacos.a2a.migration.reconnect.enabled";

    private static final String RECONNECT_CONTROL_DIRECTORY_PROPERTY =
        "nacos.a2a.migration.reconnect.control.dir";

    private static final String CLUSTER_RUNTIME_ENABLED_PROPERTY =
        "nacos.a2a.migration.cluster.runtime.enabled";

    private static final String CLUSTER_CUTOVER_ENABLED_PROPERTY =
        "nacos.a2a.migration.cluster.cutover.enabled";

    private static final String CLUSTER_CUTOVER_CONTROL_DIRECTORY_PROPERTY =
        "nacos.a2a.migration.cluster.cutover.control.dir";

    private static final String CLUSTER_NODE_A_ADDRESS_PROPERTY =
        "nacos.a2a.migration.cluster.node-a.address";

    private static final String CLUSTER_NODE_B_ADDRESS_PROPERTY =
        "nacos.a2a.migration.cluster.node-b.address";

    private static final String CLUSTER_NODE_C_ADDRESS_PROPERTY =
        "nacos.a2a.migration.cluster.node-c.address";

    private static final long RECONNECT_TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(2);

    private static final long EXTERNAL_CONTROL_TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(5);

    @Test
    @EnabledIfSystemProperty(named = CUTOVER_ENABLED_PROPERTY, matches = "true")
    void shouldKeepGrpcHttpWatchAndEndpointAvailableThroughPermanentCutover()
        throws Exception {
        boolean shadowEnabled = Boolean.parseBoolean(
            System.getProperty(CUTOVER_SHADOW_ENABLED_PROPERTY, "true"));
        ConfigService configService = createConfigService();
        ConfigService migrationConfigService = createMigrationConfigService();
        assertEquals("QUIESCING", awaitMigrationState(migrationConfigService, "QUIESCING"));
        assertEquals(shadowEnabled, currentLegacyNamingShadow(migrationConfigService));

        AiService grpcService = createAiService(transportProperties(
            AiConstants.AI_TRANSPORT_MODE_GRPC));
        AiService httpService = createAiService(transportProperties(
            AiConstants.AI_TRANSPORT_MODE_HTTP));
        NamingService namingService = createNamingService();
        assertEquals(VERSION_ONE, grpcService.getAgentCard(CUTOVER_AGENT).getVersion());
        assertEquals(VERSION_ONE, httpService.getAgentCard(CUTOVER_AGENT).getVersion());

        NacosException fenced = assertThrows(NacosException.class,
            () -> grpcService.releaseAgentCard(agentCard(CUTOVER_AGENT, VERSION_TWO),
                AiConstants.A2a.A2A_ENDPOINT_TYPE_URL, true));
        assertEquals(ErrorCode.AGENT_MIGRATION_IN_PROGRESS.getCode(), fenced.getErrCode(),
            fenced.toString());

        AgentEndpoint initial = endpoint(VERSION_ONE, randomPort(), "/quiescing-initial");
        grpcService.registerAgentEndpoint(CUTOVER_AGENT, initial);
        awaitRuntimeEndpoint(grpcService, CUTOVER_AGENT, VERSION_ONE, uri(initial));

        AgentReference reference = reference(CUTOVER_AGENT, VERSION_ONE);
        RecordingListener grpcListener = new RecordingListener();
        RecordingListener httpListener = new RecordingListener();
        assertTrue(containsRuntimeEndpoint(grpcService.subscribeAgent(reference, grpcListener),
            uri(initial)));
        assertTrue(containsRuntimeEndpoint(httpService.subscribeAgent(reference, httpListener),
            uri(initial)));
        addCleanup(() -> grpcService.unsubscribeAgent(reference, grpcListener));
        addCleanup(() -> httpService.unsubscribeAgent(reference, httpListener));

        AgentEndpoint replacement = endpoint(VERSION_ONE, randomPort(),
            "/quiescing-replacement");
        grpcService.registerAgentEndpoint(CUTOVER_AGENT,
            Collections.singletonList(replacement));
        assertWatchEndpoint(grpcListener, uri(replacement), "gRPC");
        assertWatchEndpoint(httpListener, uri(replacement), "HTTP");
        awaitLayouts(grpcService, namingService, CUTOVER_AGENT, VERSION_ONE, 1);
        assertEquals("QUIESCING", awaitMigrationState(migrationConfigService, "QUIESCING"));

        assertTrue(configService.removeConfig(CUTOVER_BLOCKER_DATA_ID,
            HISTORICAL_AGENT_GROUP), "the Java SDK cutover phase must release its hold");
        assertEquals("CANONICAL", awaitMigrationState(migrationConfigService, "CANONICAL"));
        assertEquals(shadowEnabled, currentLegacyNamingShadow(migrationConfigService));
        AgentEndpoint terminalReplacement = endpoint(VERSION_ONE, randomPort(),
            "/terminal-replacement");
        awaitTerminalLayout(grpcService, namingService, CUTOVER_AGENT, terminalReplacement,
            shadowEnabled);
        addCleanup(() -> grpcService.deregisterAgentEndpoint(CUTOVER_AGENT,
            terminalReplacement));
        assertWatchEndpoint(grpcListener, uri(terminalReplacement), "terminal gRPC");
        assertWatchEndpoint(httpListener, uri(terminalReplacement), "terminal HTTP");
        assertNoDuplicateEndpointEvent(grpcListener, uri(terminalReplacement),
            "terminal gRPC");
        assertNoDuplicateEndpointEvent(httpListener, uri(terminalReplacement),
            "terminal HTTP");

        grpcService.deregisterAgentEndpoint(CUTOVER_AGENT, terminalReplacement);
        awaitRuntimeAndHistoricalCounts(grpcService, namingService, CUTOVER_AGENT,
            VERSION_ONE, 0, 0);
        grpcService.releaseAgentCard(agentCard(CUTOVER_AGENT, VERSION_TWO),
            AiConstants.A2a.A2A_ENDPOINT_TYPE_URL, true);
        waitUntil("terminal legacy facade should publish canonical Version 2",
            () -> VERSION_TWO.equals(grpcService.getAgentCard(CUTOVER_AGENT).getVersion()));
        assertEquals(VERSION_TWO,
            grpcService.discoverAgent(reference(CUTOVER_AGENT, VERSION_TWO)).getVersion());
        AgentSearchRequest search = new AgentSearchRequest();
        search.setAgentNameContains(CUTOVER_AGENT);
        waitUntil("terminal Agent should remain searchable", () -> grpcService
            .searchAgents(search).getPageItems().stream()
            .anyMatch(each -> CUTOVER_AGENT.equals(each.getAgentName())
                && VERSION_TWO.equals(each.getLatestVersion())));
    }

    @Test
    @EnabledIfSystemProperty(named = TERMINAL_ROLLBACK_ENABLED_PROPERTY, matches = "true")
    void shouldKeepTerminalCanonicalAuthorityWhenCurrentServerIsConfiguredLegacy()
        throws Exception {
        ConfigService configService = createMigrationConfigService();
        assertEquals("CANONICAL", awaitMigrationState(configService, "CANONICAL"));
        boolean shadowEnabled = currentLegacyNamingShadow(configService);
        AiService service = createAiService();
        NamingService namingService = createNamingService();
        A2aMaintainerService maintainer = createA2aMaintainerService();
        String agentName = randomUnencodedAgentName("migration-terminal-rollback");
        releaseVersions(service, maintainer, agentName, VERSION_ONE);

        assertEquals(VERSION_ONE, service.getAgentCard(agentName).getVersion());
        assertEquals(VERSION_ONE,
            service.discoverAgent(reference(agentName, VERSION_ONE)).getVersion());
        AgentEndpoint endpoint = endpoint(VERSION_ONE, randomPort(), "/terminal-legacy-mode");
        awaitTerminalLayout(service, namingService, agentName, endpoint, shadowEnabled);
        addCleanup(() -> service.deregisterAgentEndpoint(agentName, endpoint));
        service.deregisterAgentEndpoint(agentName, endpoint);
        awaitRuntimeAndHistoricalCounts(service, namingService, agentName, VERSION_ONE, 0, 0);
        assertEquals("CANONICAL", awaitMigrationState(configService, "CANONICAL"));
    }

    @Test
    @EnabledIfSystemProperty(named = SYNCING_ENABLED_PROPERTY, matches = "true")
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
    @EnabledIfSystemProperty(named = SYNCING_ENABLED_PROPERTY, matches = "true")
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
    void shouldRedoEnabledLayoutsAfterRealServerRestart() throws Exception {
        Path controlDirectory = Paths.get(
            System.getProperty(RECONNECT_CONTROL_DIRECTORY_PROPERTY));
        Files.createDirectories(controlDirectory);
        Path ready = controlDirectory.resolve("client-ready");
        Path stopped = controlDirectory.resolve("server-stopped");
        Path restarted = controlDirectory.resolve("server-restarted");
        Files.deleteIfExists(ready);
        Files.deleteIfExists(stopped);
        Files.deleteIfExists(restarted);

        ConfigService configService = createMigrationConfigService();
        boolean shadowEnabled = currentLegacyNamingShadow(configService);
        int expectedHistorical = shadowEnabled ? 1 : 0;
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
        awaitRuntimeAndHistoricalCounts(service, namingService, agentName, VERSION_ONE, 1,
            expectedHistorical);
        awaitRuntimeAndHistoricalCounts(service, namingService, agentName, VERSION_TWO, 1,
            expectedHistorical);
        writeMarker(ready, agentName);

        waitForMarker(stopped, "the external fixture stops the AUTO server");
        waitForMarker(restarted, "the external fixture restarts the AUTO server");
        awaitRuntimeAndHistoricalCounts(service, namingService, agentName, VERSION_ONE, 1,
            expectedHistorical);
        awaitRuntimeAndHistoricalCounts(service, namingService, agentName, VERSION_TWO, 1,
            expectedHistorical);
        assertConfiguredLayoutUris(service, namingService, agentName, VERSION_ONE,
            Collections.singletonList(uri(first)), shadowEnabled);
        assertConfiguredLayoutUris(service, namingService, agentName, VERSION_TWO,
            Collections.singletonList(uri(second)), shadowEnabled);

        service.deregisterAgentEndpoint(agentName, first);
        awaitRuntimeAndHistoricalCounts(service, namingService, agentName, VERSION_ONE, 0, 0);
        awaitRuntimeAndHistoricalCounts(service, namingService, agentName, VERSION_TWO, 1,
            expectedHistorical);
    }

    @Test
    @EnabledIfSystemProperty(named = CLUSTER_RUNTIME_ENABLED_PROPERTY, matches = "true")
    void shouldConvergeExactVersionDualLayoutsAcrossThreeMembers() throws Exception {
        String nodeAAddress = requiredDistinctClusterAddress(CLUSTER_NODE_A_ADDRESS_PROPERTY,
            Collections.<String>emptyList());
        String nodeBAddress = requiredDistinctClusterAddress(CLUSTER_NODE_B_ADDRESS_PROPERTY,
            Collections.singletonList(nodeAAddress));
        String nodeCAddress = requiredDistinctClusterAddress(CLUSTER_NODE_C_ADDRESS_PROPERTY,
            Arrays.asList(nodeAAddress, nodeBAddress));
        ConfigService configServiceA = createMigrationConfigServiceAt(nodeAAddress);
        assertEquals("SYNCING", awaitMigrationState(configServiceA, "SYNCING"));

        AiService publisherA = createAiServiceAt(nodeAAddress,
            AgentTransportMode.GRPC.getValue());
        List<AiService> readers = Arrays.asList(publisherA,
            createAiServiceAt(nodeBAddress, AgentTransportMode.HTTP.getValue()),
            createAiServiceAt(nodeCAddress, AgentTransportMode.HTTP.getValue()));
        List<NamingService> namingReaders = Arrays.asList(createNamingServiceAt(nodeAAddress),
            createNamingServiceAt(nodeBAddress), createNamingServiceAt(nodeCAddress));
        A2aMaintainerService maintainerA = createA2aMaintainerServiceAt(nodeAAddress);
        String agentName = randomUnencodedAgentName("migration-cluster-runtime");
        releaseVersions(publisherA, maintainerA, agentName, VERSION_ONE, VERSION_TWO);

        AgentEndpoint initial = endpoint(VERSION_ONE, randomPort(), "/cluster-initial");
        publisherA.registerAgentEndpoint(agentName, initial);
        addCleanup(() -> publisherA.deregisterAgentEndpoint(agentName, initial));
        awaitClusterLayouts(readers, namingReaders, agentName, VERSION_ONE, 1);
        assertClusterLayoutUris(readers, namingReaders, agentName, VERSION_ONE,
            Collections.singletonList(uri(initial)));

        AgentEndpoint first = endpoint(VERSION_ONE, randomPort(), "/cluster-replaced-first");
        AgentEndpoint second = endpoint(VERSION_ONE, randomPort(), "/cluster-replaced-second");
        publisherA.registerAgentEndpoint(agentName, Arrays.asList(first, second));
        awaitClusterLayouts(readers, namingReaders, agentName, VERSION_ONE, 2);
        assertClusterLayoutUris(readers, namingReaders, agentName, VERSION_ONE,
            Arrays.asList(uri(first), uri(second)));

        AgentEndpoint versionTwo = endpoint(VERSION_TWO, randomPort(), "/cluster-version-two");
        publisherA.registerAgentEndpoint(agentName, versionTwo);
        addCleanup(() -> publisherA.deregisterAgentEndpoint(agentName, versionTwo));
        awaitClusterLayouts(readers, namingReaders, agentName, VERSION_TWO, 1);
        awaitClusterLayouts(readers, namingReaders, agentName, VERSION_ONE, 2);

        publisherA.deregisterAgentEndpoint(agentName, first);
        awaitClusterLayouts(readers, namingReaders, agentName, VERSION_ONE, 0);
        awaitClusterLayouts(readers, namingReaders, agentName, VERSION_TWO, 1);
        publisherA.deregisterAgentEndpoint(agentName, versionTwo);
        awaitClusterLayouts(readers, namingReaders, agentName, VERSION_TWO, 0);
    }

    @Test
    @EnabledIfSystemProperty(named = CLUSTER_CUTOVER_ENABLED_PROPERTY, matches = "true")
    void shouldKeepClusterReadsAndWatchesEquivalentDuringTerminalCutover() throws Exception {
        Path controlDirectory = Paths.get(
            System.getProperty(CLUSTER_CUTOVER_CONTROL_DIRECTORY_PROPERTY));
        Files.createDirectories(controlDirectory);
        Path ready = controlDirectory.resolve("client-ready");
        Path nodeCUpgraded = controlDirectory.resolve("node-c-upgraded");
        Files.deleteIfExists(ready);
        Files.deleteIfExists(nodeCUpgraded);

        String nodeAAddress = requiredDistinctClusterAddress(CLUSTER_NODE_A_ADDRESS_PROPERTY,
            Collections.<String>emptyList());
        String nodeBAddress = requiredDistinctClusterAddress(CLUSTER_NODE_B_ADDRESS_PROPERTY,
            Collections.singletonList(nodeAAddress));
        String nodeCAddress = requiredDistinctClusterAddress(CLUSTER_NODE_C_ADDRESS_PROPERTY,
            Arrays.asList(nodeAAddress, nodeBAddress));
        List<ConfigService> configReaders = Arrays.asList(
            createMigrationConfigServiceAt(nodeAAddress),
            createMigrationConfigServiceAt(nodeBAddress),
            createMigrationConfigServiceAt(nodeCAddress));
        assertClusterMigrationState(configReaders, "SYNCING");

        AiService readerA = createAiServiceAt(nodeAAddress,
            AgentTransportMode.GRPC.getValue());
        AiService readerB = createAiServiceAt(nodeBAddress,
            AgentTransportMode.HTTP.getValue());
        AiService readerC = createAiServiceAt(nodeCAddress,
            AgentTransportMode.HTTP.getValue());
        AiService loadBalancedReader = createAiServiceAt(
            nodeAAddress + ',' + nodeBAddress + ',' + nodeCAddress,
            AgentTransportMode.HTTP.getValue());
        List<AiService> readers = Arrays.asList(readerA, readerB, readerC,
            loadBalancedReader);
        A2aMaintainerService maintainerA = createA2aMaintainerServiceAt(nodeAAddress);
        String agentName = randomUnencodedAgentName("migration-cluster-cutover");
        releaseVersions(readerA, maintainerA, agentName, VERSION_ONE);
        AgentEndpoint initial = endpoint(VERSION_ONE, randomPort(), "/cluster-cutover-initial");
        readerA.registerAgentEndpoint(agentName, initial);
        addCleanup(() -> readerA.deregisterAgentEndpoint(agentName, initial));
        awaitClusterRuntime(readers, agentName, VERSION_ONE, uri(initial));
        assertClusterDefinitionAndRuntime(readers, agentName, VERSION_ONE, uri(initial));

        AgentReference reference = reference(agentName, VERSION_ONE);
        RecordingListener grpcListener = new RecordingListener();
        RecordingListener httpListener = new RecordingListener();
        assertTrue(containsRuntimeEndpoint(readerA.subscribeAgent(reference, grpcListener),
            uri(initial)));
        assertTrue(containsRuntimeEndpoint(
            loadBalancedReader.subscribeAgent(reference, httpListener), uri(initial)));
        addCleanup(() -> readerA.unsubscribeAgent(reference, grpcListener));
        addCleanup(() -> loadBalancedReader.unsubscribeAgent(reference, httpListener));
        writeMarker(ready, agentName);

        waitForMarker(nodeCUpgraded,
            "the external fixture upgrades the third migration-capable member");
        assertClusterMigrationStateAtOrPastQuiescing(configReaders);
        AgentEndpoint replacement = endpoint(VERSION_ONE, randomPort(),
            "/cluster-cutover-replacement");
        readerA.registerAgentEndpoint(agentName, Collections.singletonList(replacement));
        assertWatchEndpoint(grpcListener, uri(replacement), "cluster gRPC");
        assertWatchEndpoint(httpListener, uri(replacement), "cluster HTTP");
        assertClusterDefinitionAndRuntime(readers, agentName, VERSION_ONE, uri(replacement));
        assertNoDuplicateEndpointEvent(grpcListener, uri(replacement), "cluster gRPC");
        assertNoDuplicateEndpointEvent(httpListener, uri(replacement), "cluster HTTP");

        assertClusterMigrationState(configReaders, "CANONICAL");
        assertClusterDefinitionAndRuntime(readers, agentName, VERSION_ONE, uri(replacement));
        readerA.deregisterAgentEndpoint(agentName, replacement);
        awaitClusterRuntimeCount(readers, agentName, VERSION_ONE, 0);
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

    private void assertConfiguredLayoutUris(AiService service, NamingService namingService,
        String agentName, String version, List<String> expectedUris, boolean shadowEnabled)
        throws Exception {
        List<String> expected = new ArrayList<String>(expectedUris);
        Collections.sort(expected);
        assertEquals(expected, runtimeUris(service, agentName, version));
        if (shadowEnabled) {
            assertEquals(expected, historicalUris(namingService, agentName, version));
        } else {
            assertTrue(historicalInstances(namingService, agentName, version).isEmpty());
        }
    }

    private void awaitClusterLayouts(List<AiService> services,
        List<NamingService> namingServices, String agentName, String version, int expected)
        throws Exception {
        long deadline = System.currentTimeMillis() + RECONNECT_TIMEOUT_MILLIS;
        Throwable lastFailure = null;
        while (System.currentTimeMillis() < deadline) {
            try {
                if (clusterLayoutsHaveCount(services, namingServices, agentName, version,
                    expected)) {
                    return;
                }
            } catch (Throwable throwable) {
                lastFailure = throwable;
            }
            Thread.sleep(500L);
        }
        AssertionError failure = new AssertionError("all cluster members did not expose "
            + expected + " Endpoints in both migration Runtime layouts for " + agentName
            + " Version " + version);
        if (lastFailure != null) {
            failure.initCause(lastFailure);
        }
        throw failure;
    }

    private boolean clusterLayoutsHaveCount(List<AiService> services,
        List<NamingService> namingServices, String agentName, String version, int expected)
        throws NacosException {
        for (AiService service : services) {
            if (runtimeEndpoints(service.discoverAgent(reference(agentName, version)))
                .size() != expected) {
                return false;
            }
        }
        for (NamingService namingService : namingServices) {
            if (historicalInstances(namingService, agentName, version).size() != expected) {
                return false;
            }
        }
        return true;
    }

    private void assertClusterLayoutUris(List<AiService> services,
        List<NamingService> namingServices, String agentName, String version,
        List<String> expectedUris) throws Exception {
        for (int i = 0; i < services.size(); i++) {
            assertLayoutUris(services.get(i), namingServices.get(i), agentName, version,
                expectedUris);
        }
    }

    private void awaitClusterRuntime(List<AiService> services, String agentName, String version,
        String expectedUri) throws Exception {
        waitUntilLong("all cluster members should expose Runtime Endpoint " + expectedUri,
            () -> {
                for (AiService service : services) {
                    if (!containsRuntimeEndpoint(service.discoverAgent(
                        reference(agentName, version)), expectedUri)) {
                        return false;
                    }
                }
                return true;
            });
    }

    private void awaitClusterRuntimeCount(List<AiService> services, String agentName,
        String version, int expected) throws Exception {
        waitUntilLong("all cluster members should expose " + expected + " Runtime Endpoints",
            () -> {
                for (AiService service : services) {
                    if (runtimeEndpoints(service.discoverAgent(reference(agentName, version)))
                        .size() != expected) {
                        return false;
                    }
                }
                return true;
            });
    }

    private void assertClusterDefinitionAndRuntime(List<AiService> services, String agentName,
        String version, String expectedUri) throws Exception {
        for (AiService service : services) {
            assertEquals(version, service.getAgentCard(agentName).getVersion());
            AgentDiscoveryResult discovered = service.discoverAgent(
                reference(agentName, version));
            assertEquals(version, discovered.getVersion());
            assertTrue(containsRuntimeEndpoint(discovered, expectedUri),
                "missing Runtime Endpoint " + expectedUri);
        }
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

    private Properties transportProperties(String transport) {
        Properties result = sdkProperties();
        result.setProperty(AiConstants.AI_TRANSPORT_MODE, transport);
        return result;
    }

    private String awaitMigrationState(ConfigService configService, String... expected)
        throws Exception {
        long deadline = System.currentTimeMillis() + RECONNECT_TIMEOUT_MILLIS;
        String last = null;
        while (System.currentTimeMillis() < deadline) {
            String content = configService.getConfig(MIGRATION_MARKER_DATA_ID,
                MIGRATION_INTERNAL_GROUP, DEFAULT_TIMEOUT_MS);
            if (content != null) {
                last = JacksonUtils.toObj(content).path("state").asText();
                if (Arrays.asList(expected).contains(last)) {
                    return last;
                }
            }
            Thread.sleep(100L);
        }
        fail("Migration marker did not reach one of " + Arrays.toString(expected)
            + ", last state: " + last);
        return last;
    }

    private void assertClusterMigrationState(List<ConfigService> configServices,
        String expected) throws Exception {
        for (ConfigService configService : configServices) {
            assertEquals(expected, awaitMigrationState(configService, expected));
        }
    }

    private void assertClusterMigrationStateAtOrPastQuiescing(
        List<ConfigService> configServices) throws Exception {
        for (ConfigService configService : configServices) {
            assertTrue(Arrays.asList("QUIESCING", "CANONICAL")
                .contains(awaitMigrationState(configService, "QUIESCING", "CANONICAL")));
        }
    }

    private void awaitRuntimeEndpoint(AiService service, String agentName, String version,
        String endpointUri) throws Exception {
        waitUntil("Runtime Endpoint should become discoverable during cutover",
            () -> containsRuntimeEndpoint(service.discoverAgent(reference(agentName, version)),
                endpointUri));
    }

    private void awaitRuntimeEndpointCount(AiService service, String agentName, String version,
        int expected) throws Exception {
        waitUntil("Runtime Endpoint count should converge to " + expected,
            () -> runtimeEndpoints(service.discoverAgent(reference(agentName, version)))
                .size() == expected);
    }

    private void awaitTerminalLayout(AiService service, NamingService namingService,
        String agentName, AgentEndpoint endpoint, boolean shadowEnabled) throws Exception {
        int expectedHistorical = shadowEnabled ? 1 : 0;
        List<String> expectedUris = Collections.singletonList(uri(endpoint));
        waitUntilLong("terminal Runtime layouts should honor frozen shadow=" + shadowEnabled,
            () -> {
                service.registerAgentEndpoint(agentName,
                    Collections.singletonList(endpoint));
                return expectedUris.equals(runtimeUris(service, agentName,
                    endpoint.getVersion()))
                    && historicalInstances(namingService, agentName, endpoint.getVersion())
                        .size() == expectedHistorical
                    && (!shadowEnabled || expectedUris.equals(historicalUris(namingService,
                        agentName, endpoint.getVersion())));
            });
        if (shadowEnabled) {
            assertLayoutUris(service, namingService, agentName, endpoint.getVersion(),
                expectedUris);
        } else {
            assertEquals(expectedUris, runtimeUris(service, agentName, endpoint.getVersion()));
            assertTrue(historicalInstances(namingService, agentName, endpoint.getVersion())
                .isEmpty());
        }
    }

    private void awaitRuntimeAndHistoricalCounts(AiService service,
        NamingService namingService, String agentName, String version, int expectedRuntime,
        int expectedHistorical) throws Exception {
        waitUntilLong("Runtime and historical Endpoint counts should converge",
            () -> runtimeEndpoints(service.discoverAgent(reference(agentName, version)))
                .size() == expectedRuntime
                && historicalInstances(namingService, agentName, version)
                    .size() == expectedHistorical);
    }

    private List<String> runtimeUris(AiService service, String agentName, String version)
        throws NacosException {
        List<String> result = new ArrayList<String>();
        for (Endpoint endpoint : runtimeEndpoints(service.discoverAgent(
            reference(agentName, version)))) {
            result.add(endpoint.getUri());
        }
        Collections.sort(result);
        return result;
    }

    private List<String> historicalUris(NamingService namingService, String agentName,
        String version) throws NacosException {
        List<String> result = new ArrayList<String>();
        for (Instance instance : historicalInstances(namingService, agentName, version)) {
            result.add("http://" + instance.getIp() + ':' + instance.getPort()
                + instance.getMetadata().get("__nacos.agent.endpoint.path__"));
        }
        Collections.sort(result);
        return result;
    }

    private void assertWatchEndpoint(RecordingListener listener, String endpointUri,
        String transport) throws Exception {
        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(30);
        while (System.currentTimeMillis() < deadline) {
            AgentDiscoveryResult result = listener.events.poll(1L, TimeUnit.SECONDS);
            if (containsRuntimeEndpoint(result, endpointUri)) {
                return;
            }
        }
        fail(transport + " Watch did not receive Runtime Endpoint " + endpointUri);
    }

    private void assertNoDuplicateEndpointEvent(RecordingListener listener, String endpointUri,
        String transport) throws Exception {
        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(3);
        while (System.currentTimeMillis() < deadline) {
            AgentDiscoveryResult result = listener.events.poll(250L, TimeUnit.MILLISECONDS);
            if (containsRuntimeEndpoint(result, endpointUri)) {
                fail(transport + " Watch delivered a duplicate business Snapshot for "
                    + endpointUri);
            }
        }
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
            Thread.sleep(500L);
        }
        if (lastFailure == null) {
            fail(reason);
        }
        fail(reason + ", last failure: " + lastFailure.getMessage(), lastFailure);
    }

    private boolean containsRuntimeEndpoint(AgentDiscoveryResult result, String endpointUri) {
        for (Endpoint each : runtimeEndpoints(result)) {
            if (endpointUri.equals(each.getUri())) {
                return true;
            }
        }
        return false;
    }

    private boolean currentLegacyNamingShadow(ConfigService configService)
        throws NacosException {
        String content = configService.getConfig(MIGRATION_MARKER_DATA_ID,
            MIGRATION_INTERNAL_GROUP, DEFAULT_TIMEOUT_MS);
        assertTrue(content != null && !content.isEmpty(), "migration marker is missing");
        return JacksonUtils.toObj(content).path("legacyNamingShadow").asBoolean();
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

    private A2aMaintainerService createA2aMaintainerServiceAt(String serverAddress)
        throws NacosException {
        Properties properties = sdkProperties(serverAddress);
        properties.setProperty(PropertyKeyConst.CONTEXT_PATH, "/nacos");
        return AiMaintainerFactory.createAiMaintainerService(properties).a2a();
    }

    private AiService createAiServiceAt(String serverAddress, String transport)
        throws Exception {
        Properties properties = sdkProperties(serverAddress);
        properties.setProperty(AiConstants.AI_TRANSPORT_MODE, transport);
        return createAiService(properties);
    }

    private NamingService createNamingServiceAt(String serverAddress) throws Exception {
        NamingService result = NamingFactory.createNamingService(sdkProperties(serverAddress));
        addCleanup(result::shutDown);
        waitUntil("Naming SDK client should connect to " + serverAddress,
            () -> "UP".equals(result.getServerStatus()));
        return result;
    }

    private ConfigService createMigrationConfigService() throws Exception {
        return createMigrationConfigServiceAt(SERVER_ADDR);
    }

    private ConfigService createMigrationConfigServiceAt(String serverAddress) throws Exception {
        Properties properties = sdkProperties(serverAddress);
        properties.setProperty(PropertyKeyConst.NAMESPACE, MIGRATION_INTERNAL_NAMESPACE);
        ConfigService result = ConfigFactory.createConfigService(properties);
        addCleanup(result::shutDown);
        waitUntil("Config SDK client should connect to " + serverAddress,
            () -> "UP".equals(result.getServerStatus()));
        return result;
    }

    private Properties sdkProperties(String serverAddress) {
        Properties result = new Properties();
        result.setProperty(PropertyKeyConst.SERVER_ADDR, serverAddress);
        return result;
    }

    private String requiredDistinctClusterAddress(String property,
        List<String> otherAddresses) {
        String value = System.getProperty(property);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException("Missing required cluster IT property: " + property);
        }
        if (otherAddresses.contains(value)) {
            throw new IllegalStateException("Cluster IT node addresses must be different: "
                + value);
        }
        return value;
    }

    private void writeMarker(Path path, String value) throws Exception {
        Files.write(path, value.getBytes(StandardCharsets.UTF_8));
    }

    private void waitForMarker(Path path, String reason) throws Exception {
        long deadline = System.currentTimeMillis() + EXTERNAL_CONTROL_TIMEOUT_MILLIS;
        while (System.currentTimeMillis() < deadline) {
            if (Files.exists(path)) {
                return;
            }
            Thread.sleep(250L);
        }
        fail("Timed out waiting for " + reason + ": " + path);
    }

    private static final class RecordingListener
        extends AbstractNacosAgentDiscoveryListener {

        private final BlockingQueue<AgentDiscoveryResult> events =
            new LinkedBlockingQueue<>();

        @Override
        public void onEvent(NacosAgentDiscoveryEvent event) {
            if (event.getAgentDiscoveryResult() != null) {
                events.add(event.getAgentDiscoveryResult());
            }
        }
    }
}
