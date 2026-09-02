/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.test.adminapi.ai.a2a;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.service.a2a.identity.AsciiAgentIdCodec;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.http.HttpUtils;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.test.openapi.client.ai.AgentClientOpenApiBaseITCase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Standalone OpenAPI integration tests for historical A2A definition migration.
 *
 * <p>The dedicated workflow runs this class against an {@code AUTO/SYNCING} server. It covers
 * historical write-after convergence, migration-owned write protection, Search/RAD/ARD/Console
 * projection convergence, namespace and SERVICE isolation, conflict preservation, malformed
 * source recovery, and confirmed orphan cleanup.</p>
 *
 * @author Nacos
 */
public class A2aMigrationAdminApiOpenApiITCase extends AgentClientOpenApiBaseITCase {

    private static final String REGISTRATION_TYPE_URL = "URL";

    private static final String REGISTRATION_TYPE_SERVICE = "SERVICE";

    private static final String ADMIN_NAMESPACE_PATH = nacosPath("/v3/admin/core/namespace");

    private static final String ADMIN_CONFIG_PATH = nacosPath("/v3/admin/cs/config");

    private static final String CONSOLE_A2A_PATH = Constants.A2A.CONSOLE_PATH;

    private static final String CONSOLE_PORT =
            System.getProperty("nacos.console.port", "8080");

    private static final String CONSOLE_BASE_URL =
            "http://" + NACOS_HOST + ':' + CONSOLE_PORT;

    private static final String GENERIC_SEARCH_PATH =
            nacosPath(Constants.AI_RESOURCE_SEARCH_CLIENT_PATH);

    private static final String ARD_PORT =
            System.getProperty("nacos.ai.registry.port", "9080");

    private static final String ARD_BASE_URL = "http://" + NACOS_HOST + ':' + ARD_PORT;

    private static final String MIGRATION_PROGRESS_DATA_ID =
            "nacos.ai.a2a.reconciliation.progress.v1";

    private static final String MIGRATION_MARKER_DATA_ID = "nacos.ai.a2a.migration.v1";

    private static final String CUTOVER_BLOCKER_DATA_ID =
            "nacos.ai.a2a.migration.cutover.blocker";

    private static final String CUTOVER_PHASE_PROPERTY =
            "nacos.a2a.migration.cutover.phase";

    // Keep the direct historical Naming identity readable without importing a server codec
    // into this external API test. Encoding edge cases are covered by the codec unit suite.
    private static final String CUTOVER_AGENT = "migration-cutover-it";

    private static final String CUTOVER_VERSION_ONE = "1.0.0";

    private static final String CUTOVER_VERSION_TWO = "2.0.0";

    private static final String MIGRATION_INTERNAL_GROUP = "nacos_internal";

    private static final String HISTORICAL_AGENT_GROUP = "agent";

    private static final int MAX_RETRIES = 160;

    private static final long RETRY_INTERVAL_MILLIS = 250L;

    private static final AsciiAgentIdCodec AGENT_ID_CODEC = new AsciiAgentIdCodec();

    @Test
    @EnabledIfSystemProperty(named = CUTOVER_PHASE_PROPERTY, matches = "prepare")
    public void testQuiescingFencesHistoricalMutationsAndKeepsReadsAvailable()
            throws Exception {
        deleteQuietly(ADMIN_A2A_PATH, historicalIdentity(DEFAULT_NAMESPACE, CUTOVER_AGENT,
                null, REGISTRATION_TYPE_URL));
        deleteAgentDefinitionQuietly(DEFAULT_NAMESPACE, CUTOVER_AGENT);
        assertEquals("SYNCING", awaitMigrationState("SYNCING").path("state").asText());

        postFormOk(ADMIN_A2A_PATH, historicalForm(DEFAULT_NAMESPACE, CUTOVER_AGENT,
                CUTOVER_VERSION_ONE, REGISTRATION_TYPE_URL,
                buildV1AgentCard(CUTOVER_AGENT, CUTOVER_VERSION_ONE, "1.0")));
        awaitCanonical(CUTOVER_AGENT, DEFAULT_NAMESPACE, Set.of(CUTOVER_VERSION_ONE),
                CUTOVER_VERSION_ONE);
        awaitCrossSurface(CUTOVER_AGENT, DEFAULT_NAMESPACE, CUTOVER_VERSION_ONE);

        deleteConfig(CUTOVER_BLOCKER_DATA_ID, HISTORICAL_AGENT_GROUP, DEFAULT_NAMESPACE);
        JsonNode quiescing = awaitMigrationState("QUIESCING");
        // Hold this generation after proving the SYNCING -> QUIESCING transition. The
        // following Java SDK process releases the blocker only after both HTTP and gRPC
        // subscriptions are active, so this test does not depend on Maven startup speed.
        postFormOk(ADMIN_CONFIG_PATH, configForm(CUTOVER_BLOCKER_DATA_ID,
                HISTORICAL_AGENT_GROUP, DEFAULT_NAMESPACE, "{"));
        JsonNode completedAt = quiescing.path("completedAt");
        assertTrue(completedAt.isMissingNode() || completedAt.isNull(),
                quiescing.toString());

        Map<String, String> nextVersion = historicalForm(DEFAULT_NAMESPACE, CUTOVER_AGENT,
                CUTOVER_VERSION_TWO, REGISTRATION_TYPE_URL,
                buildV1AgentCard(CUTOVER_AGENT, CUTOVER_VERSION_TWO, "1.0"));
        assertError(postFormRaw(ADMIN_A2A_PATH, nextVersion), 409,
                ErrorCode.AGENT_MIGRATION_IN_PROGRESS,
                "definition migration is quiescing");
        assertError(putConsoleA2aRaw(nextVersion), 409,
                ErrorCode.AGENT_MIGRATION_IN_PROGRESS,
                "definition migration is quiescing");
        assertError(deleteRaw(ADMIN_A2A_PATH, historicalIdentity(DEFAULT_NAMESPACE,
                CUTOVER_AGENT, CUTOVER_VERSION_ONE, REGISTRATION_TYPE_URL)), 409,
                ErrorCode.AGENT_MIGRATION_IN_PROGRESS,
                "definition migration is quiescing");

        JsonNode historical = getJsonOk(ADMIN_A2A_PATH,
                historicalIdentity(DEFAULT_NAMESPACE, CUTOVER_AGENT, CUTOVER_VERSION_ONE,
                        REGISTRATION_TYPE_URL)).path("data");
        assertEquals(CUTOVER_VERSION_ONE, historical.path("version").asText(),
                historical.toString());
        JsonNode console = getConsoleA2a(CUTOVER_AGENT, CUTOVER_VERSION_ONE);
        assertEquals(CUTOVER_AGENT, console.path("name").asText(), console.toString());
        awaitCrossSurface(CUTOVER_AGENT, DEFAULT_NAMESPACE, CUTOVER_VERSION_ONE);
    }

    @Test
    @EnabledIfSystemProperty(named = CUTOVER_PHASE_PROPERTY, matches = "verify")
    public void testTerminalCanonicalMarkerAndCrossSurfaceProjectionArePermanent()
            throws Exception {
        JsonNode canonicalMarker = awaitMigrationState("CANONICAL");
        assertTrue(canonicalMarker.path("completedAt").asLong() > 0L,
                canonicalMarker.toString());
        awaitCanonical(CUTOVER_AGENT, DEFAULT_NAMESPACE,
                Set.of(CUTOVER_VERSION_ONE, CUTOVER_VERSION_TWO), CUTOVER_VERSION_TWO);
        JsonNode historicalLatest = getJsonOk(ADMIN_A2A_PATH,
                historicalIdentity(DEFAULT_NAMESPACE, CUTOVER_AGENT, null,
                        REGISTRATION_TYPE_URL)).path("data");
        assertEquals(CUTOVER_VERSION_TWO, historicalLatest.path("version").asText(),
                historicalLatest.toString());
        JsonNode consoleLatest = getConsoleA2a(CUTOVER_AGENT, CUTOVER_VERSION_TWO);
        assertEquals(CUTOVER_VERSION_TWO, consoleLatest.path("version").asText(),
                consoleLatest.toString());
        awaitCrossSurface(CUTOVER_AGENT, DEFAULT_NAMESPACE, CUTOVER_VERSION_TWO);

        deleteJsonOk(ADMIN_A2A_PATH, historicalIdentity(DEFAULT_NAMESPACE, CUTOVER_AGENT,
                null, REGISTRATION_TYPE_URL));
        awaitCanonicalAbsent(CUTOVER_AGENT, DEFAULT_NAMESPACE);
        assertEquals("CANONICAL", awaitMigrationState("CANONICAL").path("state").asText());
    }

    @Test
    public void testHistoricalMutationConvergesAcrossCanonicalSurfaces() throws Exception {
        String agentName = randomAiName("a2a-migration-write-after");
        String versionOne = "1.0.0";
        String versionTwo = "2.0.0";
        try {
            assertEquals("ok", postFormOk(ADMIN_A2A_PATH,
                    historicalForm(DEFAULT_NAMESPACE, agentName, versionOne,
                            REGISTRATION_TYPE_URL,
                            buildV1AgentCard(agentName, versionOne, "1.0")))
                    .get("data").asText());
            JsonNode first = awaitCanonical(agentName, DEFAULT_NAMESPACE,
                    Set.of(versionOne), versionOne);
            assertEquals("DECLARED", first.get("callInterfaces").get(0)
                    .get("endpointSourceOrder").get(0).asText(), first.toString());

            assertError(putFormRaw(ADMIN_AGENT_PATH,
                    agentForm(agentUpdateRequest(null, agentName, "blocked"))), 409,
                    ErrorCode.AGENT_MIGRATION_IN_PROGRESS,
                    "historical A2A remains authoritative");

            Map<String, String> versionTwoForm = historicalForm(DEFAULT_NAMESPACE, agentName,
                    versionTwo, REGISTRATION_TYPE_URL,
                    buildV1AgentCard(agentName, versionTwo, "1.0"));
            versionTwoForm.put("setAsLatest", "true");
            putConsoleA2a(versionTwoForm);
            awaitCanonical(agentName, DEFAULT_NAMESPACE,
                    Set.of(versionOne, versionTwo), versionTwo);

            Map<String, String> promoteVersionOne = historicalForm(DEFAULT_NAMESPACE, agentName,
                    versionOne, REGISTRATION_TYPE_URL,
                    buildV1AgentCard(agentName, versionOne, "1.0"));
            promoteVersionOne.put("setAsLatest", "true");
            putConsoleA2a(promoteVersionOne);
            awaitCanonical(agentName, DEFAULT_NAMESPACE,
                    Set.of(versionOne, versionTwo), versionOne);

            deleteJsonOk(ADMIN_A2A_PATH, historicalIdentity(DEFAULT_NAMESPACE, agentName,
                    versionOne, REGISTRATION_TYPE_URL));
            awaitCanonical(agentName, DEFAULT_NAMESPACE, Set.of(versionTwo), versionTwo);
            awaitCrossSurface(agentName, DEFAULT_NAMESPACE, versionTwo);

            JsonNode historicalLatest = getJsonOk(ADMIN_A2A_PATH,
                    historicalIdentity(DEFAULT_NAMESPACE, agentName, null,
                            REGISTRATION_TYPE_URL)).get("data");
            assertEquals(versionTwo, historicalLatest.get("version").asText(),
                    historicalLatest.toString());
            JsonNode consoleLatest = getConsoleA2a(agentName, versionTwo);
            assertEquals(versionTwo, consoleLatest.get("version").asText(),
                    consoleLatest.toString());

            deleteJsonOk(ADMIN_A2A_PATH, historicalIdentity(DEFAULT_NAMESPACE, agentName,
                    null, REGISTRATION_TYPE_URL));
            awaitCanonicalAbsent(agentName, DEFAULT_NAMESPACE);
        } finally {
            deleteQuietly(ADMIN_A2A_PATH, historicalIdentity(DEFAULT_NAMESPACE, agentName,
                    null, REGISTRATION_TYPE_URL));
        }
    }

    @Test
    public void testNamespaceServiceAndCanonicalConflictRemainIsolated() throws Exception {
        String namespaceId = "oit_a2a_migration_"
                + UUID.randomUUID().toString().substring(0, 8);
        String serviceAgent = randomAiName("a2a-migration-service");
        String conflictAgent = randomAiName("a2a-migration-conflict");
        String version = "1.0.0";
        createNamespace(namespaceId);
        try {
            postFormOk(ADMIN_A2A_PATH, historicalForm(namespaceId, serviceAgent, version,
                    REGISTRATION_TYPE_SERVICE,
                    buildV1AgentCard(serviceAgent, version, "1.0")));
            JsonNode serviceVersion = awaitCanonical(serviceAgent, namespaceId,
                    Set.of(version), version);
            assertEquals("RUNTIME", serviceVersion.get("callInterfaces").get(0)
                    .get("endpointSourceOrder").get(0).asText(), serviceVersion.toString());
            assertError(getRaw(ADMIN_AGENT_PATH, agentIdentityQuery(null, serviceAgent)), 404,
                    ErrorCode.RESOURCE_NOT_FOUND, "agent not found");

            postFormOk(ADMIN_AGENT_PATH + "/draft",
                    agentForm(agentInitialDraftRequest(null, conflictAgent, version)));
            postFormOk(ADMIN_AGENT_PATH + "/force-publish",
                    agentForm(agentVersionCommand(null, conflictAgent, version)));
            postFormOk(ADMIN_A2A_PATH, historicalForm(DEFAULT_NAMESPACE, conflictAgent, version,
                    REGISTRATION_TYPE_URL,
                    buildV1AgentCard(conflictAgent, version, "9.9")));

            JsonNode historical = getJsonOk(ADMIN_A2A_PATH,
                    historicalIdentity(DEFAULT_NAMESPACE, conflictAgent, version,
                            REGISTRATION_TYPE_URL)).get("data");
            assertEquals("9.9", historical.get("protocolVersion").asText(),
                    historical.toString());
            JsonNode canonical = getJsonOk(ADMIN_AGENT_VERSION_PATH,
                    agentVersionIdentityQuery(null, conflictAgent, version)).get("data");
            assertEquals("1.0", canonical.get("callInterfaces").get(0)
                    .get("protocolVersion").asText(), canonical.toString());

            JsonNode unrelatedUpdated = putFormOk(ADMIN_AGENT_PATH,
                    agentForm(agentUpdateRequest(null, conflictAgent, "unrelated"))).get("data");
            assertEquals("OpenAPI Agent unrelated", unrelatedUpdated.get("displayName").asText(),
                    unrelatedUpdated.toString());

            deleteJsonOk(ADMIN_A2A_PATH, historicalIdentity(namespaceId, serviceAgent,
                    null, REGISTRATION_TYPE_SERVICE));
            awaitCanonicalAbsent(serviceAgent, namespaceId);
        } finally {
            deleteQuietly(ADMIN_A2A_PATH, historicalIdentity(DEFAULT_NAMESPACE, conflictAgent,
                    null, REGISTRATION_TYPE_URL));
            deleteAgentDefinitionQuietly(DEFAULT_NAMESPACE, conflictAgent);
            deleteQuietly(ADMIN_A2A_PATH, historicalIdentity(namespaceId, serviceAgent,
                    null, REGISTRATION_TYPE_SERVICE));
            deleteQuietly(ADMIN_NAMESPACE_PATH,
                    Query.newInstance().addParam("namespaceId", namespaceId));
        }
    }

    @Test
    public void testMalformedHistoricalSourceBlocksOneCycleAndRecovers() throws Exception {
        String validAgent = randomAiName("a2a-migration-malformed-control");
        String malformedDataId = randomAiName("a2a-migration-malformed");
        String missingVersionAgent = randomAiName("a2a-migration-missing-version");
        String invalidLatestAgent = randomAiName("a2a-migration-invalid-latest");
        String version = "1.0.0";
        try {
            postFormOk(ADMIN_A2A_PATH, historicalForm(DEFAULT_NAMESPACE, validAgent, version,
                    REGISTRATION_TYPE_URL,
                    buildV1AgentCard(validAgent, version, "1.0")));
            awaitCanonical(validAgent, DEFAULT_NAMESPACE, Set.of(version), version);

            postFormOk(ADMIN_CONFIG_PATH, configForm(malformedDataId,
                    HISTORICAL_AGENT_GROUP, DEFAULT_NAMESPACE, "{"));
            awaitProgressFailure(true);

            JsonNode historical = getJsonOk(ADMIN_A2A_PATH,
                    historicalIdentity(DEFAULT_NAMESPACE, validAgent, version,
                            REGISTRATION_TYPE_URL)).get("data");
            assertEquals(validAgent, historical.get("name").asText(), historical.toString());
            awaitCanonical(validAgent, DEFAULT_NAMESPACE, Set.of(version), version);

            deleteConfig(malformedDataId, HISTORICAL_AGENT_GROUP, DEFAULT_NAMESPACE);
            awaitProgressFailure(false);

            postFormOk(ADMIN_A2A_PATH, historicalForm(DEFAULT_NAMESPACE,
                    missingVersionAgent, version, REGISTRATION_TYPE_URL,
                    buildV1AgentCard(missingVersionAgent, version, "1.0")));
            awaitCanonical(missingVersionAgent, DEFAULT_NAMESPACE, Set.of(version), version);
            String missingVersionDataId = historicalVersionDataId(missingVersionAgent, version);
            deleteConfig(missingVersionDataId, Constants.A2A.AGENT_VERSION_GROUP,
                    DEFAULT_NAMESPACE);
            awaitProgressFailure(true);
            assertEquals(validAgent, getJsonOk(ADMIN_A2A_PATH,
                    historicalIdentity(DEFAULT_NAMESPACE, validAgent, version,
                            REGISTRATION_TYPE_URL)).path("data").path("name").asText());
            deleteConfig(AGENT_ID_CODEC.encode(missingVersionAgent), HISTORICAL_AGENT_GROUP,
                    DEFAULT_NAMESPACE);
            awaitProgressFailure(false);
            awaitCanonicalAbsent(missingVersionAgent, DEFAULT_NAMESPACE);

            postFormOk(ADMIN_A2A_PATH, historicalForm(DEFAULT_NAMESPACE,
                    invalidLatestAgent, version, REGISTRATION_TYPE_URL,
                    buildV1AgentCard(invalidLatestAgent, version, "1.0")));
            awaitCanonical(invalidLatestAgent, DEFAULT_NAMESPACE, Set.of(version), version);
            String invalidLatestDataId = AGENT_ID_CODEC.encode(invalidLatestAgent);
            String validSummary = getConfigContent(invalidLatestDataId,
                    HISTORICAL_AGENT_GROUP, DEFAULT_NAMESPACE);
            ObjectNode invalidSummary = (ObjectNode) JacksonUtils.toObj(validSummary);
            invalidSummary.put("latestPublishedVersion", "9.9.9");
            assertInvalidSummaryBlocks(invalidLatestDataId, validSummary, invalidSummary);
            invalidSummary = (ObjectNode) JacksonUtils.toObj(validSummary);
            invalidSummary.put("name", "bad/name");
            assertInvalidSummaryBlocks(invalidLatestDataId, validSummary, invalidSummary);
            invalidSummary = (ObjectNode) JacksonUtils.toObj(validSummary);
            ((ObjectNode) invalidSummary.path("versionDetails").path(0))
                    .put("version", "bad version");
            assertInvalidSummaryBlocks(invalidLatestDataId, validSummary, invalidSummary);
            deleteJsonOk(ADMIN_A2A_PATH, historicalIdentity(DEFAULT_NAMESPACE,
                    invalidLatestAgent, null, REGISTRATION_TYPE_URL));
            awaitCanonicalAbsent(invalidLatestAgent, DEFAULT_NAMESPACE);
        } finally {
            deleteConfig(malformedDataId, HISTORICAL_AGENT_GROUP, DEFAULT_NAMESPACE);
            deleteConfig(historicalVersionDataId(missingVersionAgent, version),
                    Constants.A2A.AGENT_VERSION_GROUP, DEFAULT_NAMESPACE);
            deleteConfig(AGENT_ID_CODEC.encode(missingVersionAgent), HISTORICAL_AGENT_GROUP,
                    DEFAULT_NAMESPACE);
            deleteQuietly(ADMIN_A2A_PATH, historicalIdentity(DEFAULT_NAMESPACE,
                    invalidLatestAgent, null, REGISTRATION_TYPE_URL));
            deleteQuietly(ADMIN_A2A_PATH, historicalIdentity(DEFAULT_NAMESPACE, validAgent,
                    null, REGISTRATION_TYPE_URL));
        }
    }

    private JsonNode awaitCanonical(String agentName, String namespaceId,
            Set<String> versions, String latest) throws Exception {
        JsonNode last = null;
        for (int retry = 0; retry <= MAX_RETRIES; retry++) {
            HttpResponse overviewResponse = getRaw(ADMIN_AGENT_PATH,
                    agentIdentityQuery(namespaceId, agentName));
            if (overviewResponse.code() == 200) {
                JsonNode root = JacksonUtils.toObj(overviewResponse.body());
                if (root.path("code").asInt() == ErrorCode.SUCCESS.getCode()) {
                    JsonNode agent = root.path("data").path("agent");
                    Set<String> actualVersions = canonicalVersions(namespaceId, agentName);
                    if (versions.equals(actualVersions)
                            && latest.equals(agent.path("versionInfo").path("labels")
                            .path("latest").asText())) {
                        assertEquals("enable", agent.path("status").asText(), agent.toString());
                        last = getJsonOk(ADMIN_AGENT_VERSION_PATH,
                                agentVersionIdentityQuery(namespaceId, agentName, latest))
                                .get("data");
                        return last;
                    }
                    last = root;
                }
            }
            retry();
        }
        fail("Canonical Agent did not converge: " + last);
        return last;
    }

    private Set<String> canonicalVersions(String namespaceId, String agentName) throws Exception {
        HttpResponse response = getRaw(ADMIN_AGENT_VERSIONS_PATH,
                Query.newInstance().addParam("namespaceId", namespaceId)
                        .addParam("agentName", agentName).addParam("status", "online")
                        .addParam("pageNo", "1").addParam("pageSize", "100"));
        if (response.code() != 200) {
            return Collections.emptySet();
        }
        JsonNode root = JacksonUtils.toObj(response.body());
        Set<String> result = new LinkedHashSet<>();
        for (JsonNode item : root.path("data").path("pageItems")) {
            result.add(item.path("version").asText());
        }
        return result;
    }

    private void awaitCrossSurface(String agentName, String namespaceId, String version)
            throws Exception {
        JsonNode last = null;
        for (int retry = 0; retry <= MAX_RETRIES; retry++) {
            JsonNode dedicated = getJsonOk(AGENT_SEARCH_PATH,
                    Query.newInstance().addParam("namespaceId", namespaceId)
                            .addParam("agentNameContains", agentName)
                            .addParam("pageNo", "1").addParam("pageSize", "10"));
            JsonNode generic = getJsonOk(GENERIC_SEARCH_PATH,
                    Query.newInstance().addParam("namespaceId", namespaceId)
                            .addParam("query", agentName).addParam("resourceTypes", "agent")
                            .addParam("limit", "10"));
            HttpResponse discoveryResponse = getRaw(AGENT_CLIENT_PATH,
                    Query.newInstance().addParam("namespaceId", namespaceId)
                            .addParam("agentName", agentName));
            JsonNode catalog = getArdCatalog(namespaceId);
            last = dedicated;
            if (containsDedicatedAgent(dedicated, agentName)
                    && containsGenericAgent(generic, agentName)
                    && isDiscovered(discoveryResponse, agentName, version)
                    && containsArdAgent(catalog, agentName)) {
                JsonNode console = getConsoleJsonOk(Constants.Agent.CONSOLE_PATH,
                        agentIdentityQuery(namespaceId, agentName)).get("data");
                assertEquals(agentName, console.get("agent").get("agentName").asText(),
                        console.toString());
                return;
            }
            retry();
        }
        fail("Canonical Search/RAD/ARD projections did not converge: " + last);
    }

    private void awaitCanonicalAbsent(String agentName, String namespaceId) throws Exception {
        JsonNode lastSearch = null;
        for (int retry = 0; retry <= MAX_RETRIES; retry++) {
            HttpResponse overview = getRaw(ADMIN_AGENT_PATH,
                    agentIdentityQuery(namespaceId, agentName));
            lastSearch = getJsonOk(AGENT_SEARCH_PATH,
                    Query.newInstance().addParam("namespaceId", namespaceId)
                            .addParam("agentNameContains", agentName)
                            .addParam("pageNo", "1").addParam("pageSize", "10"));
            if (overview.code() == 404 && !containsDedicatedAgent(lastSearch, agentName)) {
                return;
            }
            retry();
        }
        fail("Migrated Agent orphan was not removed: " + lastSearch);
    }

    private void awaitProgressFailure(boolean expectedFailure) throws Exception {
        JsonNode last = null;
        for (int retry = 0; retry <= MAX_RETRIES; retry++) {
            HttpResponse response = getRaw(ADMIN_CONFIG_PATH,
                    configIdentity(MIGRATION_PROGRESS_DATA_ID, MIGRATION_INTERNAL_GROUP,
                            DEFAULT_NAMESPACE));
            if (response.code() == 200) {
                JsonNode root = JacksonUtils.toObj(response.body());
                String content = root.path("data").path("content").asText();
                if (!content.isEmpty()) {
                    last = JacksonUtils.toObj(content);
                    boolean failed = last.path("failed").asLong() > 0L;
                    if (failed == expectedFailure) {
                        return;
                    }
                }
            }
            retry();
        }
        fail("Migration progress failure state did not converge: " + last);
    }

    private JsonNode getConsoleA2a(String agentName, String version) throws Exception {
        return getConsoleJsonOk(Constants.A2A.CONSOLE_PATH,
                historicalIdentity(DEFAULT_NAMESPACE, agentName, version,
                        REGISTRATION_TYPE_URL)).get("data");
    }

    private JsonNode putConsoleA2a(Map<String, String> form) throws Exception {
        HttpResponse response = putConsoleA2aRaw(form);
        assertEquals(200, response.code(), response.body());
        JsonNode root = JacksonUtils.toObj(response.body());
        assertSuccess(root);
        return root;
    }

    private HttpResponse putConsoleA2aRaw(Map<String, String> form) throws Exception {
        HttpPut request = new HttpPut(CONSOLE_BASE_URL + CONSOLE_A2A_PATH);
        HttpUtils.initRequestFromEntity(request, form, StandardCharsets.UTF_8.name());
        return executeRaw(request);
    }

    private JsonNode awaitMigrationState(String expectedState) throws Exception {
        JsonNode last = null;
        for (int retry = 0; retry <= MAX_RETRIES; retry++) {
            HttpResponse response = getRaw(ADMIN_CONFIG_PATH,
                    configIdentity(MIGRATION_MARKER_DATA_ID, MIGRATION_INTERNAL_GROUP,
                            DEFAULT_NAMESPACE));
            if (response.code() == 200) {
                String content = JacksonUtils.toObj(response.body()).path("data")
                        .path("content").asText();
                if (!content.isEmpty()) {
                    last = JacksonUtils.toObj(content);
                    if (expectedState.equals(last.path("state").asText())) {
                        return last;
                    }
                }
            }
            retry();
        }
        fail("Migration marker did not reach " + expectedState + ": " + last);
        return last;
    }

    private String getConfigContent(String dataId, String groupName, String namespaceId)
            throws Exception {
        return getJsonOk(ADMIN_CONFIG_PATH, configIdentity(dataId, groupName, namespaceId))
                .path("data").path("content").asText();
    }

    private String historicalVersionDataId(String agentName, String version) {
        return AGENT_ID_CODEC.encode(agentName) + '-' + version;
    }

    private void assertInvalidSummaryBlocks(String dataId, String validSummary,
            ObjectNode invalidSummary) throws Exception {
        postFormOk(ADMIN_CONFIG_PATH, configForm(dataId, HISTORICAL_AGENT_GROUP,
                DEFAULT_NAMESPACE, invalidSummary.toString()));
        awaitProgressFailure(true);
        postFormOk(ADMIN_CONFIG_PATH, configForm(dataId, HISTORICAL_AGENT_GROUP,
                DEFAULT_NAMESPACE, validSummary));
        awaitProgressFailure(false);
    }

    private JsonNode getArdCatalog(String namespaceId) throws Exception {
        Query query = Query.newInstance().addParam("namespaceId", namespaceId);
        HttpResponse response = executeRaw(new HttpGet(ARD_BASE_URL
                + "/v3/ai/ard/ai-catalog.json?" + query.toQueryUrl()));
        if (response.code() != 200) {
            return JacksonUtils.toObj("{}");
        }
        return JacksonUtils.toObj(response.body());
    }

    private boolean containsDedicatedAgent(JsonNode root, String agentName) {
        for (JsonNode item : root.path("data").path("pageItems")) {
            if (agentName.equals(item.path("agentName").asText())) {
                return true;
            }
        }
        return false;
    }

    private boolean containsGenericAgent(JsonNode root, String agentName) {
        for (JsonNode item : root.path("data").path("items")) {
            if ("agent".equals(item.path("resourceType").asText())
                    && agentName.equals(item.path("resourceName").asText())) {
                return true;
            }
        }
        return false;
    }

    private boolean containsArdAgent(JsonNode root, String agentName) {
        for (JsonNode item : root.path("entries")) {
            if (agentName.equals(item.path("metadata").path("resourceName").asText())) {
                return true;
            }
        }
        return false;
    }

    private boolean isDiscovered(HttpResponse response, String agentName, String version) {
        if (response.code() != 200) {
            return false;
        }
        JsonNode data = JacksonUtils.toObj(response.body()).path("data");
        return agentName.equals(data.path("agentName").asText())
                && version.equals(data.path("version").asText());
    }

    private void createNamespace(String namespaceId) throws Exception {
        postFormOk(ADMIN_NAMESPACE_PATH,
                Query.newInstance().addParam("namespaceId", namespaceId)
                        .addParam("namespaceName", namespaceId)
                        .addParam("namespaceDesc", "historical A2A migration IT"));
    }

    private Map<String, String> historicalForm(String namespaceId, String agentName,
            String version, String registrationType, String card) {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("namespaceId", namespaceId);
        result.put("agentName", agentName);
        result.put("version", version);
        result.put("registrationType", registrationType);
        result.put("agentCard", card);
        return result;
    }

    private Query historicalIdentity(String namespaceId, String agentName, String version,
            String registrationType) {
        Query result = Query.newInstance().addParam("namespaceId", namespaceId)
                .addParam("agentName", agentName);
        if (version != null) {
            result.addParam("version", version);
        }
        if (registrationType != null) {
            result.addParam("registrationType", registrationType);
        }
        return result;
    }

    private Map<String, String> configForm(String dataId, String groupName, String namespaceId,
            String content) {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("dataId", dataId);
        result.put("groupName", groupName);
        result.put("namespaceId", namespaceId);
        result.put("content", content);
        result.put("type", "text");
        result.put("tag", "");
        return result;
    }

    private Query configIdentity(String dataId, String groupName, String namespaceId) {
        return Query.newInstance().addParam("dataId", dataId)
                .addParam("groupName", groupName).addParam("namespaceId", namespaceId);
    }

    private void deleteConfig(String dataId, String groupName, String namespaceId)
            throws Exception {
        Query query = configIdentity(dataId, groupName, namespaceId).addParam("tag", "");
        deleteQuietly(ADMIN_CONFIG_PATH, query);
    }

    private void retry() throws InterruptedException {
        Thread.sleep(RETRY_INTERVAL_MILLIS);
    }
}
