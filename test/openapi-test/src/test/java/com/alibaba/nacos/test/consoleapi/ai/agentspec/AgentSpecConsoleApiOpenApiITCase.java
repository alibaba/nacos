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

package com.alibaba.nacos.test.consoleapi.ai.agentspec;

import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.test.consoleapi.ai.AiConsoleApiBaseITCase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for AgentSpec console OpenAPI {@code /v3/console/ai/agentspecs}.
 *
 * <p>Scenario coverage:
 * <ul>
 *     <li>Expected capability: draft creation, draft update, force-publish, console detail, version detail, list,
 *     labels, bizTags, scope, online/offline, and delete expose the expected AgentSpec state.</li>
 *     <li>Boundary/validation: namespace defaults to public; list supports accurate/blur and scope filters while
 *     the shared {@code bizTag} request parameter is accepted but not applied by the AgentSpec service; required
 *     agentSpecName, agentSpecCard, targetVersion, version, labels, latest label preservation, scope,
 *     and positive pagination validation follows the controller forms;
 *     draft forking, auto-create-by-update, and draft deletion are covered.</li>
 *     <li>Exception/error handling: absent AgentSpecs and versions return controlled RESOURCE_NOT_FOUND bodies,
 *     direct publish from draft and redraft from online return validation errors instead of HTTP 500, and invalid
 *     search or scope parameters return HTTP 400.</li>
 * </ul>
 *
 * @author xiweng.yy
 */
public class AgentSpecConsoleApiOpenApiITCase extends AiConsoleApiBaseITCase {

    @Test
    public void testAgentSpecLifecycleGovernanceListAndDelete() throws Exception {
        String agentSpecName = randomAiName("agentspec");
        JsonNode draft = postFormOk(CONSOLE_AGENT_SPEC_PATH + "/draft",
                agentSpecDraftForm(agentSpecName, "1.0.0"));
        assertEquals("1.0.0", draft.get("data").asText(), draft.toString());
        addCleanup(() -> deleteAgentSpecQuietly(agentSpecName));

        JsonNode updated = putFormOk(CONSOLE_AGENT_SPEC_PATH + "/draft",
                agentSpecUpdateForm(agentSpecName, "1.0.0", "AgentSpec v1",
                        "scenario-v1", "soul v1"));
        assertEquals("ok", updated.get("data").asText(), updated.toString());
        JsonNode draftDetail = getJsonOk(CONSOLE_AGENT_SPEC_VERSION_PATH,
                agentSpecVersionQuery(agentSpecName, "1.0.0")).get("data");
        assertAgentSpecContent(draftDetail, agentSpecName, "1.0.0", "AgentSpec v1",
                "scenario-v1", "soul v1");
        assertAgentSpecVersionStatus(agentSpecName, "1.0.0", "draft");

        assertError(postRaw(CONSOLE_AGENT_SPEC_PATH + "/publish",
                queryFrom(agentSpecPublishForm(agentSpecName, "1.0.0"))), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "Only reviewing version can be published");
        assertEquals("ok", postFormOk(CONSOLE_AGENT_SPEC_PATH + "/force-publish",
                withUpdateLatestLabel(agentSpecPublishForm(agentSpecName, "1.0.0"))).get("data")
                .asText());

        JsonNode meta = getJsonOk(CONSOLE_AGENT_SPEC_PATH, agentSpecQuery(agentSpecName)).get("data");
        assertEquals(agentSpecName, meta.get("name").asText(), meta.toString());
        assertEquals("AgentSpec v1", meta.get("description").asText(), meta.toString());
        assertEquals("1.0.0", meta.get("labels").get("latest").asText(), meta.toString());
        assertEquals(1, meta.get("onlineCnt").asInt(), meta.toString());
        assertTrue(meta.get("enable").asBoolean(), meta.toString());
        assertFalse(meta.get("scope").asText().isBlank(), meta.toString());
        assertEquals("online", findAgentSpecVersionSummary(meta, "1.0.0").get("status").asText(),
                meta.toString());

        assertAgentSpecListContains(Query.newInstance().addParam("namespaceId", DEFAULT_NAMESPACE)
                .addParam("agentSpecName", agentSpecName).addParam("search", "accurate")
                .addParam("pageNo", "1").addParam("pageSize", "10"), agentSpecName);
        assertAgentSpecListContains(Query.newInstance().addParam("namespaceId", DEFAULT_NAMESPACE)
                .addParam("agentSpecName", agentSpecName.substring(0, 8)).addParam("search", "blur")
                .addParam("pageNo", "1").addParam("pageSize", "1000"), agentSpecName);

        assertEquals("ok", putFormOk(CONSOLE_AGENT_SPEC_PATH + "/labels",
                agentSpecLabelsForm(agentSpecName, "{\"stable\":\"1.0.0\"}")).get("data").asText());
        assertEquals("ok", putFormOk(CONSOLE_AGENT_SPEC_PATH + "/biz-tags",
                agentSpecBizTagsForm(agentSpecName, "[\"openapi\",\"console\"]")).get("data").asText());
        assertEquals("ok", putFormOk(CONSOLE_AGENT_SPEC_PATH + "/scope",
                agentSpecScopeForm(agentSpecName, "PUBLIC")).get("data").asText());
        assertEquals("PUBLIC", getJsonOk(CONSOLE_AGENT_SPEC_PATH, agentSpecQuery(agentSpecName))
                .get("data").get("scope").asText());
        assertEquals("ok", putFormOk(CONSOLE_AGENT_SPEC_PATH + "/scope",
                agentSpecScopeForm(agentSpecName, "PRIVATE")).get("data").asText());
        JsonNode governed = getJsonOk(CONSOLE_AGENT_SPEC_PATH, agentSpecQuery(agentSpecName)).get("data");
        assertEquals("1.0.0", governed.get("labels").get("stable").asText(), governed.toString());
        assertEquals("1.0.0", governed.get("labels").get("latest").asText(), governed.toString());
        assertEquals("[\"openapi\",\"console\"]", governed.get("bizTags").asText(), governed.toString());
        assertEquals("PRIVATE", governed.get("scope").asText(), governed.toString());
        assertEquals("ok", putFormOk(CONSOLE_AGENT_SPEC_PATH + "/labels",
                agentSpecLabelsForm(agentSpecName, "{\"stable\":\"1.0.0\",\"latest\":\"9.9.9\"}"))
                .get("data").asText());
        governed = getJsonOk(CONSOLE_AGENT_SPEC_PATH, agentSpecQuery(agentSpecName)).get("data");
        assertEquals("1.0.0", governed.get("labels").get("stable").asText(),
                governed.toString());
        assertEquals("1.0.0", governed.get("labels").get("latest").asText(),
                governed.toString());
        assertAgentSpecListContains(Query.newInstance().addParam("namespaceId", DEFAULT_NAMESPACE)
                .addParam("scope", "PRIVATE").addParam("agentSpecName", agentSpecName)
                .addParam("search", "accurate"), agentSpecName);
        assertAgentSpecListContains(Query.newInstance().addParam("namespaceId", DEFAULT_NAMESPACE)
                .addParam("bizTag", "does-not-filter").addParam("agentSpecName", agentSpecName)
                .addParam("search", "accurate"), agentSpecName);

        assertEquals("ok", postFormOk(CONSOLE_AGENT_SPEC_PATH + "/offline",
                agentSpecOnlineForm(agentSpecName, "1.0.0", null)).get("data").asText());
        assertAgentSpecVersionStatus(agentSpecName, "1.0.0", "offline");
        JsonNode offlineMeta = getJsonOk(CONSOLE_AGENT_SPEC_PATH, agentSpecQuery(agentSpecName))
                .get("data");
        assertTrue(offlineMeta.get("labels").get("latest") == null, offlineMeta.toString());
        assertEquals("ok", postFormOk(CONSOLE_AGENT_SPEC_PATH + "/online",
                agentSpecOnlineForm(agentSpecName, "1.0.0", null)).get("data").asText());
        assertAgentSpecVersionStatus(agentSpecName, "1.0.0", "online");
        JsonNode reonlineMeta = getJsonOk(CONSOLE_AGENT_SPEC_PATH, agentSpecQuery(agentSpecName))
                .get("data");
        assertEquals("1.0.0", reonlineMeta.get("labels").get("latest").asText(),
                reonlineMeta.toString());

        assertEquals("ok", postFormOk(CONSOLE_AGENT_SPEC_PATH + "/offline",
                agentSpecOnlineForm(agentSpecName, null, "agentspec")).get("data").asText());
        assertFalse(getJsonOk(CONSOLE_AGENT_SPEC_PATH, agentSpecQuery(agentSpecName))
                .get("data").get("enable").asBoolean());
        assertEquals("ok", postFormOk(CONSOLE_AGENT_SPEC_PATH + "/online",
                agentSpecOnlineForm(agentSpecName, null, "agentspec")).get("data").asText());
        assertTrue(getJsonOk(CONSOLE_AGENT_SPEC_PATH, agentSpecQuery(agentSpecName))
                .get("data").get("enable").asBoolean());

        assertError(postRaw(CONSOLE_AGENT_SPEC_PATH + "/redraft",
                queryFrom(agentSpecPublishForm(agentSpecName, "1.0.0"))), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "Only reviewed version can be re-edited");

        JsonNode delete = deleteJsonOk(CONSOLE_AGENT_SPEC_PATH, agentSpecQuery(agentSpecName));
        assertEquals("ok", delete.get("data").asText(), delete.toString());
        assertError(getRaw(CONSOLE_AGENT_SPEC_PATH, agentSpecQuery(agentSpecName)), 404,
                ErrorCode.RESOURCE_NOT_FOUND, "AgentSpec not found");
    }

    @Test
    public void testAgentSpecForkSubmitDeleteDraftAndUpdateAutoCreate() throws Exception {
        String autoName = randomAiName("agentspec-auto");
        assertEquals("ok", putFormOk(CONSOLE_AGENT_SPEC_PATH + "/draft",
                agentSpecUpdateForm(autoName, "0.0.1", "Auto AgentSpec", "auto-scenario",
                        "auto soul")).get("data").asText());
        addCleanup(() -> deleteAgentSpecQuietly(autoName));
        assertAgentSpecVersionStatus(autoName, "0.0.1", "draft");
        assertAgentSpecContent(getJsonOk(CONSOLE_AGENT_SPEC_VERSION_PATH,
                agentSpecVersionQuery(autoName, "0.0.1")).get("data"), autoName, "0.0.1",
                "Auto AgentSpec", "auto-scenario", "auto soul");

        String agentSpecName = randomAiName("agentspec-submit");
        postFormOk(CONSOLE_AGENT_SPEC_PATH + "/draft", agentSpecDraftForm(agentSpecName, "1.0.0"));
        addCleanup(() -> deleteAgentSpecQuietly(agentSpecName));
        putFormOk(CONSOLE_AGENT_SPEC_PATH + "/draft",
                agentSpecUpdateForm(agentSpecName, "1.0.0", "AgentSpec v1",
                        "scenario-v1", "soul v1"));
        postFormOk(CONSOLE_AGENT_SPEC_PATH + "/force-publish",
                agentSpecPublishForm(agentSpecName, "1.0.0"));

        assertError(postRaw(CONSOLE_AGENT_SPEC_PATH + "/draft",
                queryFrom(agentSpecForkForm(agentSpecName, "1.0.0", "1.0.0"))), 409,
                ErrorCode.RESOURCE_CONFLICT, "targetVersion already exists");

        JsonNode forked = postFormOk(CONSOLE_AGENT_SPEC_PATH + "/draft",
                agentSpecForkForm(agentSpecName, "2.0.0", "1.0.0"));
        assertEquals("2.0.0", forked.get("data").asText(), forked.toString());
        assertAgentSpecVersionStatus(agentSpecName, "2.0.0", "draft");

        JsonNode deleteDraft = deleteJsonOk(CONSOLE_AGENT_SPEC_PATH + "/draft",
                agentSpecQuery(agentSpecName));
        assertEquals("ok", deleteDraft.get("data").asText(), deleteDraft.toString());
        assertError(getRaw(CONSOLE_AGENT_SPEC_VERSION_PATH,
                agentSpecVersionQuery(agentSpecName, "2.0.0")), 404,
                ErrorCode.RESOURCE_NOT_FOUND, "AgentSpec version not found");

        postFormOk(CONSOLE_AGENT_SPEC_PATH + "/draft",
                agentSpecForkForm(agentSpecName, "2.0.0", "1.0.0"));
        putFormOk(CONSOLE_AGENT_SPEC_PATH + "/draft",
                agentSpecUpdateForm(agentSpecName, "2.0.0", "AgentSpec v2",
                        "scenario-v2", "soul v2"));
        JsonNode submit = postFormOk(CONSOLE_AGENT_SPEC_PATH + "/submit",
                agentSpecQueryForm(agentSpecName));
        assertEquals("2.0.0", submit.get("data").asText(), submit.toString());
        String submittedStatus = agentSpecVersionStatus(agentSpecName, "2.0.0");
        assertTrue("online".equals(submittedStatus) || "reviewing".equals(submittedStatus),
                submittedStatus);
        if ("reviewing".equals(submittedStatus)) {
            postFormOk(CONSOLE_AGENT_SPEC_PATH + "/force-publish",
                    agentSpecPublishForm(agentSpecName, "2.0.0"));
        }
        assertAgentSpecContent(getJsonOk(CONSOLE_AGENT_SPEC_VERSION_PATH,
                agentSpecVersionQuery(agentSpecName, "2.0.0")).get("data"), agentSpecName,
                "2.0.0", "AgentSpec v2", "scenario-v2", "soul v2");
    }

    @Test
    public void testAgentSpecValidationAndNotFoundErrors() throws Exception {
        assertError(getRaw(CONSOLE_AGENT_SPEC_PATH,
                Query.newInstance().addParam("namespaceId", DEFAULT_NAMESPACE)), 400,
                ErrorCode.PARAMETER_MISSING, "agentSpecName");
        assertError(postRaw(CONSOLE_AGENT_SPEC_PATH + "/draft",
                Query.newInstance().addParam("namespaceId", DEFAULT_NAMESPACE)), 400,
                ErrorCode.PARAMETER_MISSING, "agentSpecName");
        assertError(putRaw(CONSOLE_AGENT_SPEC_PATH + "/draft",
                agentSpecQuery(randomAiName("missing-card"))), 400,
                ErrorCode.PARAMETER_MISSING, "agentSpecCard");

        Map<String, String> invalidVersion = agentSpecDraftForm(randomAiName("bad-version"),
                "bad-version");
        assertError(postRaw(CONSOLE_AGENT_SPEC_PATH + "/draft", queryFrom(invalidVersion)), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "Invalid targetVersion format");
        assertError(postRaw(CONSOLE_AGENT_SPEC_PATH + "/force-publish",
                queryFrom(agentSpecQueryForm(randomAiName("missing-version")))), 400,
                ErrorCode.PARAMETER_MISSING, "version");
        assertError(putRaw(CONSOLE_AGENT_SPEC_PATH + "/labels",
                agentSpecQuery(randomAiName("missing-labels"))), 400,
                ErrorCode.PARAMETER_MISSING, "labels");
        assertError(putRaw(CONSOLE_AGENT_SPEC_PATH + "/scope",
                agentSpecQuery(randomAiName("missing-scope"))), 400,
                ErrorCode.PARAMETER_MISSING, "scope");
        assertError(putRaw(CONSOLE_AGENT_SPEC_PATH + "/scope",
                queryFrom(agentSpecScopeForm(randomAiName("invalid-scope"), "TEAM"))), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "PUBLIC or PRIVATE");
        assertError(getRaw(CONSOLE_AGENT_SPEC_LIST_PATH, Query.newInstance().addParam("search", "invalid")),
                400, ErrorCode.PARAMETER_VALIDATE_ERROR, "search");
        assertError(getRaw(CONSOLE_AGENT_SPEC_LIST_PATH, Query.newInstance().addParam("scope", "TEAM")),
                400, ErrorCode.PARAMETER_VALIDATE_ERROR, "scope");
        assertError(getRaw(CONSOLE_AGENT_SPEC_LIST_PATH, Query.newInstance().addParam("pageNo", "0")),
                400, ErrorCode.PARAMETER_VALIDATE_ERROR, "pageNo");

        String absentName = randomAiName("absent");
        assertError(getRaw(CONSOLE_AGENT_SPEC_PATH, agentSpecQuery(absentName)), 404,
                ErrorCode.RESOURCE_NOT_FOUND, "AgentSpec not found");
        assertError(getRaw(CONSOLE_AGENT_SPEC_VERSION_PATH, agentSpecVersionQuery(absentName, "1.0.0")),
                404, ErrorCode.RESOURCE_NOT_FOUND, "AgentSpec not found");
    }

    private void assertAgentSpecListContains(Query query, String agentSpecName) throws Exception {
        JsonNode page = getJsonOk(CONSOLE_AGENT_SPEC_LIST_PATH, query).get("data");
        assertEmptyPageShape(page);
        JsonNode found = findByName(page, "name", agentSpecName);
        assertFalse(found.isMissingNode(), page.toString());
        assertEquals(agentSpecName, found.get("name").asText(), found.toString());
        assertNotNull(found.get("labels"), found.toString());
    }

    private void assertAgentSpecVersionStatus(String agentSpecName, String version, String status)
            throws Exception {
        assertEquals(status, agentSpecVersionStatus(agentSpecName, version));
    }

    private String agentSpecVersionStatus(String agentSpecName, String version) throws Exception {
        JsonNode meta = getJsonOk(CONSOLE_AGENT_SPEC_PATH, agentSpecQuery(agentSpecName)).get("data");
        JsonNode versionSummary = findAgentSpecVersionSummary(meta, version);
        assertFalse(versionSummary.isMissingNode(), meta.toString());
        return versionSummary.get("status").asText();
    }
}
