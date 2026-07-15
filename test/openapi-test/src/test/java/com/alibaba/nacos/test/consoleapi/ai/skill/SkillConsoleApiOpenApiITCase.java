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

package com.alibaba.nacos.test.consoleapi.ai.skill;

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
 * Integration tests for skill console OpenAPI {@code /v3/console/ai/skills}.
 *
 * <p>Scenario coverage:
 * <ul>
 *     <li>Expected capability: draft creation and update persist editable skill content; force-publish makes a
 *     version online and latest; console detail, version detail, list, ZIP download, labels, bizTags, scope,
 *     online/offline, and delete expose the expected skill state.</li>
 *     <li>Boundary/validation: namespace defaults to public; list supports accurate/blur, scope, and bizTag
 *     filters; skillName, skillCard, targetVersion, version, labels, latest label preservation, scope,
 *     and positive pagination validation follows the controller forms;
 *     draft forking and draft deletion are covered explicitly.</li>
 *     <li>Exception/error handling: absent skills and versions return controlled RESOURCE_NOT_FOUND bodies, direct
 *     publish from draft and redraft from online return validation errors instead of HTTP 500, and invalid search
 *     or scope parameters return HTTP 400.</li>
 * </ul>
 *
 * @author xiweng.yy
 */
public class SkillConsoleApiOpenApiITCase extends AiConsoleApiBaseITCase {

    @Test
    public void testSkillLifecycleGovernanceListDownloadAndDelete() throws Exception {
        String skillName = randomAiName("skill");
        String draftBody = "Use the initial skill instructions.";
        String updatedBody = "Use the updated skill instructions.";
        JsonNode draft = postFormOk(CONSOLE_SKILL_PATH + "/draft",
                skillDraftForm(skillName, "1.0.0", draftBody, "guide draft"));
        assertEquals("1.0.0", draft.get("data").asText(), draft.toString());
        addCleanup(() -> deleteSkillQuietly(skillName));

        JsonNode updated = putFormOk(CONSOLE_SKILL_PATH + "/draft",
                skillUpdateForm(skillName, updatedBody, "guide updated"));
        assertEquals("ok", updated.get("data").asText(), updated.toString());
        JsonNode draftDetail = getJsonOk(CONSOLE_SKILL_VERSION_PATH,
                skillVersionQuery(skillName, "1.0.0")).get("data");
        assertSkillContent(draftDetail, skillName, "1.0.0", updatedBody, "guide updated");
        assertSkillVersionStatus(skillName, "1.0.0", "draft");

        assertError(postRaw(CONSOLE_SKILL_PATH + "/publish", queryFrom(skillPublishForm(skillName, "1.0.0"))),
                400, ErrorCode.PARAMETER_VALIDATE_ERROR, "Only reviewing version can be published");
        JsonNode published = postFormOk(CONSOLE_SKILL_PATH + "/force-publish",
                withUpdateLatestLabel(skillPublishForm(skillName, "1.0.0")));
        assertEquals("ok", published.get("data").asText(), published.toString());

        JsonNode meta = getJsonOk(CONSOLE_SKILL_PATH, skillQuery(skillName)).get("data");
        assertEquals(skillName, meta.get("name").asText(), meta.toString());
        assertEquals("1.0.0", meta.get("labels").get("latest").asText(), meta.toString());
        assertEquals(1, meta.get("onlineCnt").asInt(), meta.toString());
        assertTrue(meta.get("enable").asBoolean(), meta.toString());
        assertFalse(meta.get("scope").asText().isBlank(), meta.toString());
        assertEquals("online", findSkillVersionSummary(meta, "1.0.0").get("status").asText(),
                meta.toString());

        JsonNode onlineDetail = getJsonOk(CONSOLE_SKILL_VERSION_PATH,
                skillVersionQuery(skillName, "1.0.0")).get("data");
        assertSkillContent(onlineDetail, skillName, "1.0.0", updatedBody, "guide updated");
        assertSkillListContains(Query.newInstance().addParam("namespaceId", DEFAULT_NAMESPACE)
                .addParam("skillName", skillName).addParam("search", "accurate")
                .addParam("pageNo", "1").addParam("pageSize", "10"), skillName);
        assertSkillListContains(Query.newInstance().addParam("namespaceId", DEFAULT_NAMESPACE)
                .addParam("skillName", skillName.substring(0, 8)).addParam("search", "blur")
                .addParam("pageNo", "1").addParam("pageSize", "1000"), skillName);

        assertSkillZip(getRawBytes(CONSOLE_SKILL_VERSION_DOWNLOAD_PATH,
                skillVersionQuery(skillName, "1.0.0")), skillName, "1.0.0", updatedBody,
                "guide updated");

        assertEquals("ok", putFormOk(CONSOLE_SKILL_PATH + "/labels",
                skillLabelsForm(skillName, "{\"stable\":\"1.0.0\"}")).get("data").asText());
        assertEquals("ok", putFormOk(CONSOLE_SKILL_PATH + "/biz-tags",
                skillBizTagsForm(skillName, "[\"openapi\",\"console\"]")).get("data").asText());
        assertEquals("ok", putFormOk(CONSOLE_SKILL_PATH + "/scope",
                skillScopeForm(skillName, "PUBLIC")).get("data").asText());
        assertEquals("PUBLIC", getJsonOk(CONSOLE_SKILL_PATH, skillQuery(skillName)).get("data")
                .get("scope").asText());
        assertEquals("ok", putFormOk(CONSOLE_SKILL_PATH + "/scope",
                skillScopeForm(skillName, "PRIVATE")).get("data").asText());
        JsonNode governed = getJsonOk(CONSOLE_SKILL_PATH, skillQuery(skillName)).get("data");
        assertEquals("1.0.0", governed.get("labels").get("stable").asText(), governed.toString());
        assertEquals("1.0.0", governed.get("labels").get("latest").asText(), governed.toString());
        assertEquals("[\"openapi\",\"console\"]", governed.get("bizTags").asText(), governed.toString());
        assertEquals("PRIVATE", governed.get("scope").asText(), governed.toString());
        assertEquals("ok", putFormOk(CONSOLE_SKILL_PATH + "/labels",
                skillLabelsForm(skillName, "{\"stable\":\"1.0.0\",\"latest\":\"9.9.9\"}"))
                .get("data").asText());
        governed = getJsonOk(CONSOLE_SKILL_PATH, skillQuery(skillName)).get("data");
        assertEquals("1.0.0", governed.get("labels").get("stable").asText(),
                governed.toString());
        assertEquals("1.0.0", governed.get("labels").get("latest").asText(),
                governed.toString());
        assertSkillListContains(Query.newInstance().addParam("namespaceId", DEFAULT_NAMESPACE)
                .addParam("scope", "PRIVATE").addParam("bizTag", "openapi")
                .addParam("skillName", skillName).addParam("search", "accurate"), skillName);

        assertEquals("ok", postFormOk(CONSOLE_SKILL_PATH + "/offline",
                skillOnlineForm(skillName, "1.0.0", null)).get("data").asText());
        assertSkillVersionStatus(skillName, "1.0.0", "offline");
        JsonNode offlineMeta = getJsonOk(CONSOLE_SKILL_PATH, skillQuery(skillName)).get("data");
        assertTrue(offlineMeta.get("labels").get("latest") == null, offlineMeta.toString());
        assertEquals("ok", postFormOk(CONSOLE_SKILL_PATH + "/online",
                skillOnlineForm(skillName, "1.0.0", null)).get("data").asText());
        assertSkillVersionStatus(skillName, "1.0.0", "online");
        JsonNode reonlineMeta = getJsonOk(CONSOLE_SKILL_PATH, skillQuery(skillName)).get("data");
        assertEquals("1.0.0", reonlineMeta.get("labels").get("latest").asText(),
                reonlineMeta.toString());

        assertEquals("ok", postFormOk(CONSOLE_SKILL_PATH + "/offline",
                skillOnlineForm(skillName, null, "skill")).get("data").asText());
        assertFalse(getJsonOk(CONSOLE_SKILL_PATH, skillQuery(skillName)).get("data").get("enable")
                .asBoolean());
        assertEquals("ok", postFormOk(CONSOLE_SKILL_PATH + "/online",
                skillOnlineForm(skillName, null, "skill")).get("data").asText());
        assertTrue(getJsonOk(CONSOLE_SKILL_PATH, skillQuery(skillName)).get("data").get("enable")
                .asBoolean());

        assertError(postRaw(CONSOLE_SKILL_PATH + "/redraft", queryFrom(skillPublishForm(skillName, "1.0.0"))),
                400, ErrorCode.PARAMETER_VALIDATE_ERROR, "Only reviewed version can be re-edited");

        JsonNode delete = deleteJsonOk(CONSOLE_SKILL_PATH, skillQuery(skillName));
        assertEquals("ok", delete.get("data").asText(), delete.toString());
        assertError(getRaw(CONSOLE_SKILL_PATH, skillQuery(skillName)), 404,
                ErrorCode.RESOURCE_NOT_FOUND, "Skill not found");
    }

    @Test
    public void testSkillForkSubmitAndDeleteDraft() throws Exception {
        String skillName = randomAiName("skill-submit");
        postFormOk(CONSOLE_SKILL_PATH + "/draft",
                skillDraftForm(skillName, "1.0.0", "Version one body.", "guide v1"));
        addCleanup(() -> deleteSkillQuietly(skillName));
        postFormOk(CONSOLE_SKILL_PATH + "/force-publish", skillPublishForm(skillName, "1.0.0"));

        assertError(postRaw(CONSOLE_SKILL_PATH + "/draft",
                queryFrom(skillForkForm(skillName, "1.0.0", "1.0.0"))), 409,
                ErrorCode.RESOURCE_CONFLICT, "targetVersion already exists");

        JsonNode forked = postFormOk(CONSOLE_SKILL_PATH + "/draft",
                skillForkForm(skillName, "2.0.0", "1.0.0"));
        assertEquals("2.0.0", forked.get("data").asText(), forked.toString());
        assertSkillVersionStatus(skillName, "2.0.0", "draft");

        JsonNode deleteDraft = deleteJsonOk(CONSOLE_SKILL_PATH + "/draft", skillQuery(skillName));
        assertEquals("ok", deleteDraft.get("data").asText(), deleteDraft.toString());
        assertError(getRaw(CONSOLE_SKILL_VERSION_PATH, skillVersionQuery(skillName, "2.0.0")),
                404, ErrorCode.RESOURCE_NOT_FOUND, "Skill version not found");

        postFormOk(CONSOLE_SKILL_PATH + "/draft", skillForkForm(skillName, "2.0.0", "1.0.0"));
        putFormOk(CONSOLE_SKILL_PATH + "/draft",
                skillUpdateForm(skillName, "Version two submitted body.", "guide v2"));
        JsonNode submit = postFormOk(CONSOLE_SKILL_PATH + "/submit", skillQueryForm(skillName));
        assertEquals("2.0.0", submit.get("data").asText(), submit.toString());
        String submittedStatus = skillVersionStatus(skillName, "2.0.0");
        assertTrue("online".equals(submittedStatus) || "reviewing".equals(submittedStatus),
                submittedStatus);
        if ("reviewing".equals(submittedStatus)) {
            postFormOk(CONSOLE_SKILL_PATH + "/force-publish", skillPublishForm(skillName, "2.0.0"));
        }
        assertSkillContent(getJsonOk(CONSOLE_SKILL_VERSION_PATH, skillVersionQuery(skillName, "2.0.0"))
                .get("data"), skillName, "2.0.0", "Version two submitted body.", "guide v2");
    }

    @Test
    public void testSkillValidationAndNotFoundErrors() throws Exception {
        assertError(getRaw(CONSOLE_SKILL_PATH,
                Query.newInstance().addParam("namespaceId", DEFAULT_NAMESPACE)), 400,
                ErrorCode.PARAMETER_MISSING, "skillName");
        assertError(postRaw(CONSOLE_SKILL_PATH + "/draft",
                queryFrom(skillQueryForm(randomAiName("missing-card")))), 400,
                ErrorCode.PARAMETER_MISSING, "skillCard");

        String mismatchSkill = randomAiName("mismatch");
        Map<String, String> mismatch = skillDraftForm(mismatchSkill, "1.0.0", "body", "guide");
        mismatch.put("skillName", randomAiName("other"));
        assertError(postRaw(CONSOLE_SKILL_PATH + "/draft", queryFrom(mismatch)), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "skillCard name must match skillName");

        Map<String, String> invalidVersion = skillDraftForm(randomAiName("bad-version"),
                "bad-version", "body", "guide");
        assertError(postRaw(CONSOLE_SKILL_PATH + "/draft", queryFrom(invalidVersion)), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "Invalid targetVersion format");
        assertError(postRaw(CONSOLE_SKILL_PATH + "/force-publish",
                queryFrom(skillQueryForm(randomAiName("missing-version")))), 400,
                ErrorCode.PARAMETER_MISSING, "version");
        assertError(putRaw(CONSOLE_SKILL_PATH + "/labels", skillQuery(randomAiName("missing-labels"))),
                400, ErrorCode.PARAMETER_MISSING, "labels");
        assertError(putRaw(CONSOLE_SKILL_PATH + "/scope", skillQuery(randomAiName("missing-scope"))),
                400, ErrorCode.PARAMETER_MISSING, "scope");
        assertError(putRaw(CONSOLE_SKILL_PATH + "/scope",
                queryFrom(skillScopeForm(randomAiName("invalid-scope"), "TEAM"))), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "PUBLIC or PRIVATE");
        assertError(getRaw(CONSOLE_SKILL_LIST_PATH, Query.newInstance().addParam("search", "invalid")),
                400, ErrorCode.PARAMETER_VALIDATE_ERROR, "search");
        assertError(getRaw(CONSOLE_SKILL_LIST_PATH, Query.newInstance().addParam("scope", "TEAM")),
                400, ErrorCode.PARAMETER_VALIDATE_ERROR, "scope");
        assertError(getRaw(CONSOLE_SKILL_LIST_PATH, Query.newInstance().addParam("pageNo", "0")),
                400, ErrorCode.PARAMETER_VALIDATE_ERROR, "pageNo");

        String absentSkill = randomAiName("absent");
        assertError(getRaw(CONSOLE_SKILL_PATH, skillQuery(absentSkill)), 404,
                ErrorCode.RESOURCE_NOT_FOUND, "Skill not found");
        assertError(getRaw(CONSOLE_SKILL_VERSION_PATH, skillVersionQuery(absentSkill, "1.0.0")),
                404, ErrorCode.RESOURCE_NOT_FOUND, "Skill not found");
        assertError(getRaw(CONSOLE_SKILL_VERSION_DOWNLOAD_PATH, skillVersionQuery(absentSkill, "1.0.0")),
                404, ErrorCode.RESOURCE_NOT_FOUND, "Skill not found");
    }

    private void assertSkillListContains(Query query, String skillName) throws Exception {
        JsonNode page = getJsonOk(CONSOLE_SKILL_LIST_PATH, query).get("data");
        assertEmptyPageShape(page);
        JsonNode found = findByName(page, "name", skillName);
        assertFalse(found.isMissingNode(), page.toString());
        assertEquals(skillName, found.get("name").asText(), found.toString());
        assertNotNull(found.get("labels"), found.toString());
    }

    private void assertSkillVersionStatus(String skillName, String version, String status)
            throws Exception {
        assertEquals(status, skillVersionStatus(skillName, version));
    }

    private String skillVersionStatus(String skillName, String version) throws Exception {
        JsonNode meta = getJsonOk(CONSOLE_SKILL_PATH, skillQuery(skillName)).get("data");
        JsonNode versionSummary = findSkillVersionSummary(meta, version);
        assertFalse(versionSummary.isMissingNode(), meta.toString());
        return versionSummary.get("status").asText();
    }
}
