/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
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

package com.alibaba.nacos.test.consoleapi.ai.agent;

import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.test.consoleapi.ai.AiConsoleApiBaseITCase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for Agent Console API {@code /v3/console/ai/agents}.
 *
 * <p>Scenario coverage:
 * <ul>
 *     <li>Expected capability: the Console facade mirrors every Agent Admin relative path for
 *     metadata, draft, Version lifecycle, labels, list/detail, Runtime Endpoint inspection, and
 *     deletion while preserving the same form-encoded contract.</li>
 *     <li>Boundary/validation: omitted namespace defaults to {@code public}; explicit namespace
 *     is preserved in the Runtime snapshot and generated Naming reference; malformed form JSON,
 *     required identity, protocol, order, and pagination validation return HTTP 400.</li>
 *     <li>Exception/error handling: invalid lifecycle transitions and absent Agent reads retain
 *     the controlled Admin error envelopes. Runtime inspection additionally returns the
 *     Console-only Naming service reference without changing the Runtime fact.</li>
 * </ul>
 *
 * @author xiweng.yy
 */
public class AgentConsoleApiOpenApiITCase extends AiConsoleApiBaseITCase {

    @Test
    public void testAllConsoleManagementPathsAndRuntimeNamingReference() throws Exception {
        String agentName = randomAiName("agent-console");
        String firstVersion = "1.0.0";
        String secondVersion = "2.0.0";
        JsonNode draft = postFormOk(CONSOLE_AGENT_PATH + "/draft",
                agentForm(agentInitialDraftRequest(null, agentName, firstVersion))).get("data");
        addCleanup(() -> deleteAgentDefinitionQuietly(DEFAULT_NAMESPACE, agentName));
        assertVersion(draft, firstVersion, "draft");

        JsonNode overview = getJsonOk(CONSOLE_AGENT_PATH,
                agentIdentityQuery(null, agentName)).get("data");
        assertEquals(DEFAULT_NAMESPACE, overview.get("agent").get("namespaceId").asText(),
                overview.toString());
        assertEquals(agentName, overview.get("agent").get("agentName").asText(),
                overview.toString());

        JsonNode updated = putFormOk(CONSOLE_AGENT_PATH,
                agentForm(agentUpdateRequest(null, agentName, "console"))).get("data");
        assertEquals("OpenAPI Agent console", updated.get("displayName").asText(),
                updated.toString());
        assertEquals("console", updated.get("tags").get(1).asText(), updated.toString());

        JsonNode page = getJsonOk(CONSOLE_AGENT_LIST_PATH,
                Query.newInstance().addParam("agentName", agentName)
                        .addParam("bizTag", "console").addParam("scope", "private")
                        .addParam("owner", "nacos").addParam("orderBy", "download_count")
                        .addParam("pageNo", "1").addParam("pageSize", "10")).get("data");
        assertEmptyPageShape(page);
        assertFalse(findByName(page, "agentName", agentName).isMissingNode(), page.toString());

        JsonNode versions = getJsonOk(CONSOLE_AGENT_VERSIONS_PATH,
                Query.newInstance().addParam("agentName", agentName)
                        .addParam("status", "draft").addParam("pageNo", "1")
                        .addParam("pageSize", "10")).get("data");
        assertEquals(1, versions.get("totalCount").asInt(), versions.toString());
        assertVersion(versions.get("pageItems").get(0), firstVersion, "draft");

        JsonNode versionDetail = getJsonOk(CONSOLE_AGENT_VERSION_PATH,
                agentVersionIdentityQuery(null, agentName, firstVersion)).get("data");
        assertVersion(versionDetail, firstVersion, "draft");

        JsonNode replaced = putFormOk(CONSOLE_AGENT_PATH + "/draft",
                agentForm(agentDraftUpdateRequest(null, agentName, firstVersion,
                        "console-update"))).get("data");
        assertEquals("console-update", replaced.get("callInterfaces").get(0)
                .get("nativeDescriptor").get("marker").asText(), replaced.toString());

        assertError(postFormRaw(CONSOLE_AGENT_PATH + "/publish",
                agentForm(agentVersionCommand(null, agentName, firstVersion))), 400,
                ErrorCode.ILLEGAL_STATE, "must be reviewed");
        assertError(postFormRaw(CONSOLE_AGENT_PATH + "/redraft",
                agentForm(agentVersionCommand(null, agentName, firstVersion))), 400,
                ErrorCode.ILLEGAL_STATE, "must be reviewed");

        JsonNode online = postFormOk(CONSOLE_AGENT_PATH + "/force-publish",
                agentForm(agentVersionCommand(null, agentName, firstVersion))).get("data");
        assertVersion(online, firstVersion, "online");
        JsonNode labels = putFormOk(CONSOLE_AGENT_PATH + "/labels",
                agentForm(agentLabelsUpdateRequest(null, agentName,
                        Collections.singletonMap("stable", firstVersion)))).get("data");
        assertEquals(firstVersion, labels.get("versionInfo").get("labels").get("stable").asText(),
                labels.toString());

        JsonNode offline = postFormOk(CONSOLE_AGENT_PATH + "/offline",
                agentForm(agentVersionCommand(null, agentName, firstVersion))).get("data");
        assertVersion(offline, firstVersion, "offline");
        JsonNode reonline = postFormOk(CONSOLE_AGENT_PATH + "/online",
                agentForm(agentVersionCommand(null, agentName, firstVersion))).get("data");
        assertVersion(reonline, firstVersion, "online");

        JsonNode runtimeView = getJsonOk(CONSOLE_AGENT_RUNTIME_ENDPOINTS_PATH,
                Query.newInstance().addParam("agentName", agentName)
                        .addParam("protocol", "a2a").addParam("version", firstVersion))
                .get("data");
        assertRuntimeView(runtimeView, DEFAULT_NAMESPACE, agentName, firstVersion);

        JsonNode copiedDraft = postFormOk(CONSOLE_AGENT_PATH + "/draft",
                agentForm(agentDraftCreateRequest(null, agentName, secondVersion, firstVersion)))
                .get("data");
        assertVersion(copiedDraft, secondVersion, "draft");
        JsonNode changedDraft = putFormOk(CONSOLE_AGENT_PATH + "/draft",
                agentForm(agentDraftUpdateRequest(null, agentName, secondVersion,
                        "console-v2"))).get("data");
        assertEquals("console-v2", changedDraft.get("callInterfaces").get(0)
                .get("nativeDescriptor").get("marker").asText(), changedDraft.toString());
        deleteJsonOk(CONSOLE_AGENT_PATH + "/draft",
                agentVersionIdentityQuery(null, agentName, secondVersion));

        postFormOk(CONSOLE_AGENT_PATH + "/draft",
                agentForm(agentDraftCreateRequest(null, agentName, secondVersion, firstVersion)));
        JsonNode submitted = postFormOk(CONSOLE_AGENT_PATH + "/submit",
                agentForm(agentVersionCommand(null, agentName, secondVersion))).get("data");
        String submittedStatus = submitted.get("status").asText();
        assertTrue("online".equals(submittedStatus) || "reviewing".equals(submittedStatus),
                submitted.toString());

        deleteJsonOk(CONSOLE_AGENT_PATH, agentIdentityQuery(null, agentName));
        assertError(getRaw(CONSOLE_AGENT_PATH, agentIdentityQuery(null, agentName)), 404,
                ErrorCode.RESOURCE_NOT_FOUND, "agent not found");
    }

    @Test
    public void testConsoleBindingValidationAndExplicitRuntimeNamespace() throws Exception {
        String namespaceId = "oit_console_" + UUID.randomUUID().toString().substring(0, 8);
        String agentName = randomAiName("agent-console-runtime");
        JsonNode runtimeView = getJsonOk(CONSOLE_AGENT_RUNTIME_ENDPOINTS_PATH,
                Query.newInstance().addParam("namespaceId", namespaceId)
                        .addParam("agentName", agentName).addParam("protocol", "a2a"))
                .get("data");
        assertRuntimeView(runtimeView, namespaceId, agentName, null);

        assertError(getRaw(CONSOLE_AGENT_PATH, Query.newInstance()), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "agentName");
        assertError(getRaw(CONSOLE_AGENT_LIST_PATH,
                Query.newInstance().addParam("pageNo", "0")), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "pageNo");
        assertError(getRaw(CONSOLE_AGENT_LIST_PATH,
                Query.newInstance().addParam("orderBy", "invalid")), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "orderBy");
        assertError(getRaw(CONSOLE_AGENT_RUNTIME_ENDPOINTS_PATH,
                Query.newInstance().addParam("agentName", agentName)), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "protocol");
        assertError(getRaw(CONSOLE_AGENT_RUNTIME_ENDPOINTS_PATH,
                Query.newInstance().addParam("agentName", agentName)
                        .addParam("protocol", "a2a").addParam("version", "invalid")), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "version");

        Map<String, String> malformedDraft = agentForm(agentInitialDraftRequest(null,
                randomAiName("agent-console-malformed"), "1.0.0"));
        malformedDraft.put("callInterfaces", "{");
        assertError(postFormRaw(CONSOLE_AGENT_PATH + "/draft", malformedDraft), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "callInterfaces");

        assertError(getRaw(CONSOLE_AGENT_VERSION_PATH,
                agentVersionIdentityQuery(null, randomAiName("agent-console-absent"), "1.0.0")),
                404, ErrorCode.RESOURCE_NOT_FOUND, "agent not found");
    }

    private void assertVersion(JsonNode version, String expectedVersion, String expectedStatus) {
        assertEquals(expectedVersion, version.get("version").asText(), version.toString());
        assertEquals(expectedStatus, version.get("status").asText(), version.toString());
        assertTrue(version.get("contentDigest").asText().startsWith("sha256:"),
                version.toString());
    }

    private void assertRuntimeView(JsonNode runtimeView, String namespaceId, String agentName,
            String version) {
        JsonNode snapshot = runtimeView.get("runtimeEndpointSnapshot");
        assertEquals(namespaceId, snapshot.get("namespaceId").asText(), runtimeView.toString());
        assertEquals(agentName, snapshot.get("agentName").asText(), runtimeView.toString());
        assertEquals("a2a", snapshot.get("protocol").asText(), runtimeView.toString());
        if (null == version) {
            assertTrue(snapshot.get("version") == null || snapshot.get("version").isNull(),
                    runtimeView.toString());
        } else {
            assertEquals(version, snapshot.get("version").asText(), runtimeView.toString());
        }
        assertTrue(snapshot.get("items").isArray(), runtimeView.toString());
        assertFalse(snapshot.get("items").elements().hasNext(), runtimeView.toString());

        JsonNode namingReference = runtimeView.get("namingServiceRef");
        assertEquals(namespaceId, namingReference.get("namespaceId").asText(),
                runtimeView.toString());
        assertEquals("agent-endpoints", namingReference.get("groupName").asText(),
                runtimeView.toString());
        assertEquals("rad-" + agentName + "-a2a", namingReference.get("serviceName").asText(),
                runtimeView.toString());
    }
}
