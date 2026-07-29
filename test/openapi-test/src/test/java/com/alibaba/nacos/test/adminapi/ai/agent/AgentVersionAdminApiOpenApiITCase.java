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
import com.alibaba.nacos.test.adminapi.ai.AiAdminApiBaseITCase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for Agent draft and Version lifecycle Admin APIs.
 *
 * <p>Scenario coverage:
 * <ul>
 *     <li>Expected capability: the first direct-content draft creates Agent metadata with
 *     server-owned governance defaults; equivalent create retries preserve content, conflicting
 *     create retries cannot replace it, while PUT draft replacement changes the digest.
 *     Force-publish, online/offline, custom labels, copy-based and direct draft creation, draft
 *     deletion, and no-Pipeline submit are observable through exact Version reads.</li>
 *     <li>Boundary/validation: an absent Agent cannot use {@code basedOnVersion}; draft content
 *     and {@code basedOnVersion} are mutually exclusive; first-create metadata is rejected on a
 *     later draft; only one editing draft is accepted; exact Version is mandatory for every
 *     transition; the server-managed {@code latest} label cannot be written.</li>
 *     <li>Exception/error handling: publish/redraft/update/online/offline from invalid states
 *     return controlled {@code ILLEGAL_STATE} responses, while editing-slot conflicts return
 *     {@code RESOURCE_CONFLICT}. A deterministic reviewed Pipeline is not installed in the
 *     standalone suite, so successful reviewed publish/redraft remains outside this class.</li>
 * </ul>
 *
 * @author xiweng.yy
 */
public class AgentVersionAdminApiOpenApiITCase extends AiAdminApiBaseITCase {

    @Test
    public void testDraftLifecycleLabelsAndNoPipelineSubmit() throws Exception {
        String agentName = randomAiName("agent-version");
        String firstVersion = "1.0.0";
        String secondVersion = "2.0.0";
        Map<String, Object> initialRequest =
                agentInitialDraftRequest(null, agentName, firstVersion);
        JsonNode firstDraft = postFormOk(ADMIN_AGENT_PATH + "/draft",
                agentForm(initialRequest)).get("data");
        addCleanup(() -> deleteAgentDefinitionQuietly(DEFAULT_NAMESPACE, agentName));
        assertVersion(firstDraft, firstVersion, "draft");
        JsonNode initialAgent = getOverview(agentName).get("agent");
        assertEquals("OpenAPI Agent create", initialAgent.get("displayName").asText(),
                initialAgent.toString());
        assertEquals("Agent admin OpenAPI create", initialAgent.get("description").asText(),
                initialAgent.toString());
        assertEquals("enable", initialAgent.get("status").asText(), initialAgent.toString());
        assertTrue(initialAgent.has("owner"), initialAgent.toString());
        assertEquals("PRIVATE", initialAgent.get("scope").asText(), initialAgent.toString());
        assertEquals("create", initialAgent.get("extensions").get("x-openapi-it").get("marker")
                .asText(), initialAgent.toString());

        JsonNode original = getVersion(agentName, firstVersion);
        String originalDigest = original.get("contentDigest").asText();
        JsonNode retriedDraft = postFormOk(ADMIN_AGENT_PATH + "/draft",
                agentForm(initialRequest)).get("data");
        assertEquals(originalDigest, retriedDraft.get("contentDigest").asText(),
                retriedDraft.toString());
        Map<String, Object> conflictingCreate =
                agentInitialDraftRequest(null, agentName, firstVersion);
        conflictingCreate.put("callInterfaces",
                agentDraftUpdateRequest(null, agentName, firstVersion, "conflicting-create")
                        .get("callInterfaces"));
        assertError(postFormRaw(ADMIN_AGENT_PATH + "/draft", agentForm(conflictingCreate)), 409,
                ErrorCode.RESOURCE_CONFLICT, "already exists");
        assertEquals(originalDigest, getVersion(agentName, firstVersion)
                .get("contentDigest").asText());

        JsonNode updatedDraft = putFormOk(ADMIN_AGENT_PATH + "/draft",
                agentForm(agentDraftUpdateRequest(null, agentName, firstVersion, "updated-v1")))
                .get("data");
        assertVersion(updatedDraft, firstVersion, "draft");
        assertEquals("updated-v1", updatedDraft.get("callInterfaces").get(0)
                .get("nativeDescriptor").get("marker").asText(), updatedDraft.toString());
        assertNotEquals(originalDigest, updatedDraft.get("contentDigest").asText(),
                updatedDraft.toString());

        JsonNode forcePublished = postFormOk(ADMIN_AGENT_PATH + "/force-publish",
                agentForm(agentVersionCommand(null, agentName, firstVersion)))
                .get("data");
        assertVersion(forcePublished, firstVersion, "online");
        assertLatest(agentName, firstVersion, 1);

        JsonNode labels = putFormOk(ADMIN_AGENT_PATH + "/labels",
                agentForm(agentLabelsUpdateRequest(null, agentName,
                        Collections.singletonMap("stable", firstVersion)))).get("data");
        assertEquals(firstVersion, labels.get("versionInfo").get("labels").get("stable").asText(),
                labels.toString());
        assertEquals(firstVersion, labels.get("versionInfo").get("labels").get("latest").asText(),
                labels.toString());

        assertError(putFormRaw(ADMIN_AGENT_PATH + "/labels",
                agentForm(agentLabelsUpdateRequest(null, agentName,
                        Collections.singletonMap("latest", "9.9.9")))), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "latest");

        JsonNode offline = postFormOk(ADMIN_AGENT_PATH + "/offline",
                agentForm(agentVersionCommand(null, agentName, firstVersion)))
                .get("data");
        assertVersion(offline, firstVersion, "offline");
        assertNoLatest(agentName);
        assertError(postFormRaw(ADMIN_AGENT_PATH + "/offline",
                agentForm(agentVersionCommand(null, agentName, firstVersion))), 400,
                ErrorCode.ILLEGAL_STATE, "must be online");

        JsonNode online = postFormOk(ADMIN_AGENT_PATH + "/online",
                agentForm(agentVersionCommand(null, agentName, firstVersion)))
                .get("data");
        assertVersion(online, firstVersion, "online");
        assertLatest(agentName, firstVersion, 1);
        assertError(postFormRaw(ADMIN_AGENT_PATH + "/online",
                agentForm(agentVersionCommand(null, agentName, firstVersion))), 400,
                ErrorCode.ILLEGAL_STATE, "must be offline");
        assertError(postFormRaw(ADMIN_AGENT_PATH + "/publish",
                agentForm(agentVersionCommand(null, agentName, firstVersion))), 400,
                ErrorCode.ILLEGAL_STATE, "must be reviewed");
        assertError(postFormRaw(ADMIN_AGENT_PATH + "/redraft",
                agentForm(agentVersionCommand(null, agentName, firstVersion))), 400,
                ErrorCode.ILLEGAL_STATE, "must be reviewed");
        assertError(putFormRaw(ADMIN_AGENT_PATH + "/draft",
                agentForm(agentDraftUpdateRequest(null, agentName, firstVersion,
                        "illegal-online-update"))), 400, ErrorCode.ILLEGAL_STATE, "not a draft");

        assertError(postFormRaw(ADMIN_AGENT_PATH + "/draft",
                agentForm(agentInitialDraftRequest(null, agentName, secondVersion))), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "only allowed");

        JsonNode copiedDraft = postFormOk(ADMIN_AGENT_PATH + "/draft",
                agentForm(agentDraftCreateRequest(null, agentName, secondVersion, firstVersion)))
                .get("data");
        assertVersion(copiedDraft, secondVersion, "draft");
        assertEquals("updated-v1", copiedDraft.get("callInterfaces").get(0)
                .get("nativeDescriptor").get("marker").asText(), copiedDraft.toString());

        assertError(postFormRaw(ADMIN_AGENT_PATH + "/draft",
                agentForm(agentDraftCreateRequest(null, agentName, "3.0.0", null))),
                409, ErrorCode.RESOURCE_CONFLICT, "editing Version");

        deleteJsonOk(ADMIN_AGENT_PATH + "/draft",
                agentVersionIdentityQuery(null, agentName, secondVersion));
        assertError(getRaw(ADMIN_AGENT_VERSION_PATH,
                agentVersionIdentityQuery(null, agentName, secondVersion)), 404,
                ErrorCode.RESOURCE_NOT_FOUND, "Agent Version not found");

        JsonNode directDraft = postFormOk(ADMIN_AGENT_PATH + "/draft",
                agentForm(agentDraftCreateRequest(null, agentName, secondVersion, null)))
                .get("data");
        assertVersion(directDraft, secondVersion, "draft");
        JsonNode submitted = postFormOk(ADMIN_AGENT_PATH + "/submit",
                agentForm(agentVersionCommand(null, agentName, secondVersion)))
                .get("data");
        assertVersion(submitted, secondVersion, "online");
        assertLatest(agentName, secondVersion, 2);
        JsonNode governed = getOverview(agentName).get("agent");
        assertEquals(firstVersion, governed.get("versionInfo").get("labels").get("stable").asText(),
                governed.toString());
    }

    @Test
    public void testDraftAndLifecycleValidationErrors() throws Exception {
        String agentName = randomAiName("agent-version-validation");
        postFormOk(ADMIN_AGENT_PATH + "/draft",
                agentForm(agentInitialDraftRequest(null, agentName, "1.0.0")));
        addCleanup(() -> deleteAgentDefinitionQuietly(DEFAULT_NAMESPACE, agentName));

        Map<String, Object> ambiguousDraft =
                agentDraftCreateRequest(null, agentName, "2.0.0", null);
        ambiguousDraft.put("basedOnVersion", "1.0.0");
        assertError(postFormRaw(ADMIN_AGENT_PATH + "/draft", agentForm(ambiguousDraft)), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "either callInterfaces or basedOnVersion");

        Map<String, Object> emptyDraft = agentVersionCommand(null, agentName, "2.0.0");
        assertError(postFormRaw(ADMIN_AGENT_PATH + "/draft", agentForm(emptyDraft)), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR,
                "either callInterfaces or basedOnVersion");

        Map<String, Object> missingVersion = agentVersionCommand(null, agentName, "1.0.0");
        missingVersion.remove("version");
        assertError(postFormRaw(ADMIN_AGENT_PATH + "/force-publish", agentForm(missingVersion)), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "version");

        assertError(putFormRaw(ADMIN_AGENT_PATH + "/labels",
                agentForm(agentLabelsUpdateRequest(null, agentName, null))), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "labels");

        String absentAgent = randomAiName("agent-version-absent");
        assertError(postFormRaw(ADMIN_AGENT_PATH + "/draft",
                agentForm(agentDraftCreateRequest(null, absentAgent, "1.0.0", "0.9.0"))), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "basedOnVersion");
        assertError(postFormRaw(ADMIN_AGENT_PATH + "/force-publish",
                agentForm(agentVersionCommand(null, absentAgent, "1.0.0"))), 404,
                ErrorCode.RESOURCE_NOT_FOUND, "Agent not found");
    }

    private JsonNode getOverview(String agentName) throws Exception {
        return getJsonOk(ADMIN_AGENT_PATH, agentIdentityQuery(null, agentName)).get("data");
    }

    private JsonNode getVersion(String agentName, String version) throws Exception {
        return getJsonOk(ADMIN_AGENT_VERSION_PATH,
                agentVersionIdentityQuery(null, agentName, version)).get("data");
    }

    private void assertVersion(JsonNode version, String expectedVersion, String expectedStatus) {
        assertEquals(expectedVersion, version.get("version").asText(), version.toString());
        assertEquals(expectedStatus, version.get("status").asText(), version.toString());
        assertTrue(version.get("contentDigest").asText().startsWith("sha256:"),
                version.toString());
    }

    private void assertLatest(String agentName, String expectedVersion, int onlineCount)
            throws Exception {
        JsonNode agent = getOverview(agentName).get("agent");
        assertEquals(expectedVersion, agent.get("versionInfo").get("labels").get("latest")
                .asText(), agent.toString());
        assertEquals(expectedVersion, agent.get("versionCatalog").get("latestVersion").asText(),
                agent.toString());
        assertEquals(onlineCount, agent.get("versionInfo").get("onlineCnt").asInt(),
                agent.toString());
    }

    private void assertNoLatest(String agentName) throws Exception {
        JsonNode agent = getOverview(agentName).get("agent");
        assertTrue(agent.get("versionInfo").get("labels").get("latest") == null,
                agent.toString());
        assertTrue(agent.get("versionCatalog").get("latestVersion") == null,
                agent.toString());
        assertEquals(0, agent.get("versionInfo").get("onlineCnt").asInt(), agent.toString());
        assertFalse(agent.get("versionCatalog").get("onlineVersions").elements().hasNext(),
                agent.toString());
    }
}
