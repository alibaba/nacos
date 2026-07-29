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

package com.alibaba.nacos.test.adminapi.ai.agent;

import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.test.adminapi.ai.AiAdminApiBaseITCase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for Agent Admin API {@code /nacos/v3/admin/ai/agents}.
 *
 * <p>Scenario coverage:
 * <ul>
 *     <li>Expected capability: an initial draft creates the Agent definition, after which Overview
 *     reads, metadata update, filtered list, Version list/detail, and complete definition deletion
 *     are visible through the deployed HTTP API.</li>
 *     <li>Boundary/validation: omitted or blank namespace uses {@code public}; an explicit
 *     namespace remains isolated; list filters name, one business tag, scope, and owner
 *     before pagination; the orderBy allowlist is validated; and malformed identity and pagination
 *     inputs return HTTP 400.</li>
 *     <li>Exception/error handling: absent Agent and post-delete reads return controlled
 *     {@code RESOURCE_NOT_FOUND} envelopes.</li>
 * </ul>
 *
 * @author xiweng.yy
 */
public class AgentAdminApiOpenApiITCase extends AiAdminApiBaseITCase {

    @Test
    public void testDefaultNamespaceCrudOverviewListAndVersionReads() throws Exception {
        String agentName = randomAiName("agent-admin");
        String version = "1.0.0";
        JsonNode createdDraft = postFormOk(ADMIN_AGENT_PATH + "/draft",
                agentForm(agentInitialDraftRequest(null, agentName, version))).get("data");
        addCleanup(() -> deleteAgentDefinitionQuietly(DEFAULT_NAMESPACE, agentName));
        assertVersionDetail(createdDraft, DEFAULT_NAMESPACE, agentName, version, "draft");

        JsonNode created = getJsonOk(ADMIN_AGENT_PATH,
                agentIdentityQuery(null, agentName)).get("data");
        assertOverview(created, DEFAULT_NAMESPACE, agentName, version, "create");
        JsonNode blankNamespace = getJsonOk(ADMIN_AGENT_PATH,
                Query.newInstance().addParam("namespaceId", "").addParam("agentName", agentName))
                .get("data");
        assertOverview(blankNamespace, DEFAULT_NAMESPACE, agentName, version, "create");

        Map<String, Object> updateRequest = agentUpdateRequest(null, agentName, "updated");
        JsonNode updated = putFormOk(ADMIN_AGENT_PATH, agentForm(updateRequest)).get("data");
        assertEquals(DEFAULT_NAMESPACE, updated.get("namespaceId").asText(), updated.toString());
        assertEquals(agentName, updated.get("agentName").asText(), updated.toString());
        assertEquals("OpenAPI Agent updated", updated.get("displayName").asText(),
                updated.toString());
        assertEquals("Agent admin OpenAPI updated", updated.get("description").asText(),
                updated.toString());
        assertEquals("updated", updated.get("tags").get(1).asText(), updated.toString());
        assertEquals("updated", updated.get("extensions").get("x-openapi-it").get("marker")
                .asText(), updated.toString());
        assertEquals(created.get("agent").get("owner").asText(), updated.get("owner").asText(),
                updated.toString());
        assertEquals(created.get("agent").get("scope").asText(), updated.get("scope").asText(),
                updated.toString());
        assertTrue(updated.get("metaVersion").asLong() > created.get("agent").get("metaVersion")
                .asLong(), updated.toString());

        JsonNode page = getAgentList(agentName.substring(0, 8)).get("data");
        assertEmptyPageShape(page);
        JsonNode summary = findByName(page, "agentName", agentName);
        assertFalse(summary.isMissingNode(), page.toString());
        assertEquals("OpenAPI Agent updated", summary.get("displayName").asText(),
                summary.toString());
        assertEquals("updated", summary.get("tags").get(1).asText(), summary.toString());

        JsonNode versions = getJsonOk(ADMIN_AGENT_VERSIONS_PATH,
                Query.newInstance().addParam("agentName", agentName)
                        .addParam("status", "draft").addParam("pageNo", "1")
                        .addParam("pageSize", "10")).get("data");
        assertEmptyPageShape(versions);
        assertEquals(1, versions.get("totalCount").asInt(), versions.toString());
        assertVersionSummary(versions.get("pageItems").get(0), version, "draft");

        JsonNode versionDetail = getJsonOk(ADMIN_AGENT_VERSION_PATH,
                agentVersionIdentityQuery(null, agentName, version)).get("data");
        assertVersionDetail(versionDetail, DEFAULT_NAMESPACE, agentName, version, "draft");

        deleteJsonOk(ADMIN_AGENT_PATH, agentIdentityQuery(null, agentName));
        assertError(getRaw(ADMIN_AGENT_PATH, agentIdentityQuery(null, agentName)), 404,
                ErrorCode.RESOURCE_NOT_FOUND, "Agent not found");
        assertError(getRaw(ADMIN_AGENT_VERSION_PATH,
                agentVersionIdentityQuery(null, agentName, version)), 404,
                ErrorCode.RESOURCE_NOT_FOUND, "Agent not found");
    }

    @Test
    public void testListAppliesEveryFilterBeforePaging() throws Exception {
        String token = UUID.randomUUID().toString().substring(0, 8);
        String targetName = "oit-agent-filter-" + token;
        createListFilterAgent(targetName, null);
        createListFilterAgent(randomAiName("agent-filter-name"), null);
        createListFilterAgent(targetName + "-tag", List.of("openapi-it", "other"));

        JsonNode page = getJsonOk(ADMIN_AGENT_LIST_PATH,
                Query.newInstance().addParam("agentName", targetName)
                        .addParam("bizTag", "create").addParam("scope", "PRIVATE")
                        .addParam("owner", "nacos").addParam("orderBy", "download_count")
                        .addParam("pageNo", "1").addParam("pageSize", "1")).get("data");

        assertEmptyPageShape(page);
        assertEquals(1, page.get("totalCount").asInt(), page.toString());
        assertEquals(1, page.get("pageItems").size(), page.toString());
        assertEquals(targetName, page.get("pageItems").get(0).get("agentName").asText(),
                page.toString());

        JsonNode wrongScope = getJsonOk(ADMIN_AGENT_LIST_PATH,
                Query.newInstance().addParam("agentName", targetName)
                        .addParam("scope", "PUBLIC").addParam("pageNo", "1")
                        .addParam("pageSize", "1")).get("data");
        assertEquals(0, wrongScope.get("totalCount").asInt(), wrongScope.toString());
        JsonNode wrongOwner = getJsonOk(ADMIN_AGENT_LIST_PATH,
                Query.newInstance().addParam("agentName", targetName)
                        .addParam("owner", "other-owner").addParam("pageNo", "1")
                        .addParam("pageSize", "1")).get("data");
        assertEquals(0, wrongOwner.get("totalCount").asInt(), wrongOwner.toString());
    }

    @Test
    public void testExplicitNamespaceIsolationAndValidationErrors() throws Exception {
        String namespaceId = "oit_agent_" + UUID.randomUUID().toString().substring(0, 8);
        String agentName = randomAiName("agent-namespace");
        String version = "2.0.0";
        postFormOk(ADMIN_AGENT_PATH + "/draft",
                agentForm(agentInitialDraftRequest(namespaceId, agentName, version)));
        addCleanup(() -> deleteAgentDefinitionQuietly(namespaceId, agentName));

        JsonNode explicit = getJsonOk(ADMIN_AGENT_PATH,
                agentIdentityQuery(namespaceId, agentName)).get("data");
        assertOverview(explicit, namespaceId, agentName, version, "create");
        assertError(getRaw(ADMIN_AGENT_PATH, agentIdentityQuery(null, agentName)), 404,
                ErrorCode.RESOURCE_NOT_FOUND, "Agent not found");

        assertError(getRaw(ADMIN_AGENT_PATH, Query.newInstance()), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "agentName");
        assertError(getRaw(ADMIN_AGENT_LIST_PATH,
                Query.newInstance().addParam("pageNo", "0")), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "pageNo");
        assertError(getRaw(ADMIN_AGENT_LIST_PATH,
                Query.newInstance().addParam("pageSize", "0")), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "pageSize");
        assertError(getRaw(ADMIN_AGENT_LIST_PATH,
                Query.newInstance().addParam("orderBy", "invalid")), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "orderBy");

        Map<String, Object> missingName = agentInitialDraftRequest(null,
                randomAiName("agent-missing-name"), "1.0.0");
        missingName.remove("agentName");
        assertError(postFormRaw(ADMIN_AGENT_PATH + "/draft", agentForm(missingName)), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR,
                "agentName");

        Map<String, Object> invalidVersion = agentInitialDraftRequest(null,
                randomAiName("agent-invalid-version"), "invalid");
        assertError(postFormRaw(ADMIN_AGENT_PATH + "/draft", agentForm(invalidVersion)), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "version");

        Map<String, Object> duplicateProtocol = agentInitialDraftRequest(null,
                randomAiName("agent-duplicate-protocol"), "1.0.0");
        List<Object> callInterfaces = castList(duplicateProtocol.get("callInterfaces"));
        duplicateProtocol.put("callInterfaces",
                List.of(callInterfaces.get(0), callInterfaces.get(0)));
        assertError(postFormRaw(ADMIN_AGENT_PATH + "/draft", agentForm(duplicateProtocol)), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "protocol");

        Map<String, String> malformedDraft = agentForm(agentInitialDraftRequest(null,
                randomAiName("agent-malformed-draft"), "1.0.0"));
        malformedDraft.put("callInterfaces", "{");
        assertError(postFormRaw(ADMIN_AGENT_PATH + "/draft", malformedDraft), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "callInterfaces");

        String absentAgent = randomAiName("agent-absent");
        assertError(getRaw(ADMIN_AGENT_PATH, agentIdentityQuery(namespaceId, absentAgent)), 404,
                ErrorCode.RESOURCE_NOT_FOUND, "Agent not found");
        assertError(getRaw(ADMIN_AGENT_VERSION_PATH,
                agentVersionIdentityQuery(namespaceId, absentAgent, "1.0.0")), 404,
                ErrorCode.RESOURCE_NOT_FOUND, "Agent not found");
    }

    private void assertOverview(JsonNode overview, String namespaceId, String agentName,
            String version, String marker) {
        assertNotNull(overview, "overview");
        JsonNode agent = overview.get("agent");
        assertEquals(namespaceId, agent.get("namespaceId").asText(), overview.toString());
        assertEquals(agentName, agent.get("agentName").asText(), overview.toString());
        assertEquals("OpenAPI Agent " + marker, agent.get("displayName").asText(),
                overview.toString());
        assertEquals("enable", agent.get("status").asText(), overview.toString());
        assertTrue(agent.has("owner"), overview.toString());
        assertEquals("PRIVATE", agent.get("scope").asText(), overview.toString());
        assertEquals(version, agent.get("versionInfo").get("editingVersion").asText(),
                overview.toString());
        assertEquals(0, agent.get("versionInfo").get("onlineCnt").asInt(), overview.toString());
        assertTrue(agent.get("metaVersion").asLong() >= 1L, overview.toString());

        JsonNode versionPage = overview.get("versionPage");
        assertEmptyPageShape(versionPage);
        assertEquals(1, versionPage.get("totalCount").asInt(), overview.toString());
        assertVersionSummary(versionPage.get("pageItems").get(0), version, "draft");
    }

    private void assertVersionSummary(JsonNode summary, String version, String status) {
        assertEquals(version, summary.get("version").asText(), summary.toString());
        assertEquals(status, summary.get("status").asText(), summary.toString());
        assertTrue(summary.get("contentDigest").asText().startsWith("sha256:"),
                summary.toString());
    }

    private void assertVersionDetail(JsonNode detail, String namespaceId, String agentName,
            String version, String status) {
        assertEquals(namespaceId, detail.get("namespaceId").asText(), detail.toString());
        assertEquals(agentName, detail.get("agentName").asText(), detail.toString());
        assertEquals(version, detail.get("version").asText(), detail.toString());
        assertEquals(status, detail.get("status").asText(), detail.toString());
        assertEquals("a2a", detail.get("callInterfaces").get(0).get("protocol").asText(),
                detail.toString());
        assertTrue(detail.get("contentDigest").asText().startsWith("sha256:"),
                detail.toString());
    }

    private JsonNode getAgentList(String nameContains) throws Exception {
        return getJsonOk(ADMIN_AGENT_LIST_PATH,
                Query.newInstance().addParam("agentName", nameContains)
                        .addParam("bizTag", "updated").addParam("scope", "PRIVATE")
                        .addParam("owner", "nacos").addParam("orderBy", "download_count")
                        .addParam("pageNo", "1").addParam("pageSize", "10"));
    }

    private void createListFilterAgent(String agentName, List<String> tags) throws Exception {
        postFormOk(ADMIN_AGENT_PATH + "/draft",
                agentForm(agentInitialDraftRequest(null, agentName, "1.0.0")));
        Map<String, Object> request = agentUpdateRequest(null, agentName, "create");
        if (null != tags) {
            request.put("tags", tags);
        }
        putFormOk(ADMIN_AGENT_PATH, agentForm(request));
        addCleanup(() -> deleteAgentDefinitionQuietly(DEFAULT_NAMESPACE, agentName));
    }

    @SuppressWarnings("unchecked")
    private List<Object> castList(Object value) {
        return (List<Object>) value;
    }
}
