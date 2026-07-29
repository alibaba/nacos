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
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.test.consoleapi.ai.AiConsoleApiBaseITCase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for skill console upload OpenAPI {@code /v3/console/ai/skills/upload}.
 *
 * <p>Scenario coverage:
 * <ul>
 *     <li>Expected capability: single ZIP upload creates a draft from {@code SKILL.md} plus resource files, precheck
 *     reports existing-draft overwrite target, overwrite updates an editing draft, and batch upload reports successful skill
 *     folders with persisted content.</li>
 *     <li>Boundary/validation: namespace defaults to public; upload version resolves from SKILL.md before
 *     targetVersion; short numeric versions are normalized; invalid uploaded versions fall back to server-generated
 *     drafts; duplicate working drafts require overwrite; batch upload keeps valid folders while reporting a
 *     structured result for every folder.</li>
 *     <li>Exception/error handling: empty and malformed ZIP files and archives without
 *     {@code SKILL.md} return controlled HTTP 400 Result bodies instead of HTTP 500.</li>
 * </ul>
 *
 * @author xiweng.yy
 */
public class SkillUploadConsoleApiOpenApiITCase extends AiConsoleApiBaseITCase {

    @Test
    public void testSingleSkillUploadOverwriteAndVersionBump() throws Exception {
        String skillName = randomAiName("upload");
        Query uploadQuery = uploadQuery(false, "9.9.9", "openapi upload");
        HttpResponse uploaded = postMultipartRaw(CONSOLE_SKILL_PATH + "/upload", uploadQuery,
                "file", skillName + ".zip", "application/zip",
                buildSkillZip(skillName, "1.0.0", "Uploaded body v1.", "uploaded guide"));
        assertUploadSuccess(uploaded, skillName);
        addCleanup(() -> deleteSkillQuietly(skillName));

        JsonNode uploadedDetail = getJsonOk(CONSOLE_SKILL_VERSION_PATH,
                skillVersionQuery(skillName, "1.0.0")).get("data");
        assertSkillContent(uploadedDetail, skillName, "1.0.0", "Uploaded body v1.",
                "uploaded guide");

        assertError(postMultipartRaw(CONSOLE_SKILL_PATH + "/upload", uploadQuery,
                "file", skillName + ".zip", "application/zip",
                buildSkillZip(skillName, "1.0.0", "Duplicate body.", "duplicate guide")),
                409, ErrorCode.RESOURCE_CONFLICT, "working version");
        JsonNode precheck = assertUploadResult(postMultipartRaw(CONSOLE_SKILL_PATH
                        + "/upload/precheck", precheckQuery("9.9.9"), "file",
                skillName + ".zip", "application/zip",
                buildSkillZip(skillName, "1.0.0", "Duplicate body.", "duplicate guide")))
                .get("data").get(0);
        assertEquals("DRAFT_EXISTS", precheck.get("precheckCode").asText(), precheck.toString());
        assertEquals(skillName, precheck.get("skillName").asText(), precheck.toString());
        assertTrue(precheck.has("owner"), precheck.toString());
        assertEquals("1.0.0", precheck.get("parsedVersion").asText(), precheck.toString());
        assertEquals("1.0.0", precheck.get("targetVersion").asText(), precheck.toString());
        assertEquals("", precheck.get("entryPath").asText(), precheck.toString());
        assertCompactPrecheckResult(precheck);
        HttpResponse overwritten = postMultipartRaw(CONSOLE_SKILL_PATH + "/upload",
                uploadQuery(true, "9.9.9", "openapi overwrite"), "file", skillName + ".zip",
                "application/zip",
                buildSkillZip(skillName, "1.0.0", "Overwritten body.", "overwritten guide"));
        assertUploadSuccess(overwritten, skillName);
        assertSkillContent(getJsonOk(CONSOLE_SKILL_VERSION_PATH, skillVersionQuery(skillName, "1.0.0"))
                .get("data"), skillName, "1.0.0", "Overwritten body.", "overwritten guide");

        postFormOk(CONSOLE_SKILL_PATH + "/force-publish", skillPublishForm(skillName, "1.0.0"));
        assertEquals("ok", postFormOk(CONSOLE_SKILL_PATH + "/offline",
                skillOnlineForm(skillName, "1.0.0", null)).get("data").asText());
        JsonNode shortVersionPrecheck = assertUploadResult(postMultipartRaw(CONSOLE_SKILL_PATH
                        + "/upload/precheck", precheckQuery(null), "file",
                skillName + ".zip", "application/zip",
                buildSkillZip(skillName, "1.0", "Short version body.",
                        "short version guide")))
                .get("data").get(0);
        assertEquals("VERSION_ADJUSTED", shortVersionPrecheck.get("precheckCode").asText(),
                shortVersionPrecheck.toString());
        assertEquals("1.0", shortVersionPrecheck.get("parsedVersion").asText(),
                shortVersionPrecheck.toString());
        assertEquals("1.0.0", shortVersionPrecheck.get("maxPublishedVersion").asText(),
                shortVersionPrecheck.toString());
        assertEquals("1.0.1", shortVersionPrecheck.get("targetVersion").asText(),
                shortVersionPrecheck.toString());
        assertCompactPrecheckResult(shortVersionPrecheck);
        HttpResponse nextUpload = postMultipartRaw(CONSOLE_SKILL_PATH + "/upload",
                uploadQuery(false, null, "openapi next draft"), "file", skillName + ".zip",
                "application/zip",
                buildSkillZip(skillName, "1.0", "Uploaded body v2.", "uploaded guide v2"));
        assertUploadSuccess(nextUpload, skillName);
        JsonNode meta = getJsonOk(CONSOLE_SKILL_PATH, skillQuery(skillName)).get("data");
        assertEquals("1.0.1", meta.get("editingVersion").asText(), meta.toString());
        assertSkillContent(getJsonOk(CONSOLE_SKILL_VERSION_PATH, skillVersionQuery(skillName, "1.0.1"))
                .get("data"), skillName, "1.0.1", "Uploaded body v2.", "uploaded guide v2");
    }

    @Test
    public void testBatchSkillUploadSuccessAndPartialFailure() throws Exception {
        String firstSkill = randomAiName("batch-a");
        String secondSkill = randomAiName("batch-b");
        Map<String, String> skills = new LinkedHashMap<>();
        skills.put(firstSkill, "Batch body A.");
        skills.put(secondSkill, "Batch body B.");
        HttpResponse batch = postMultipartRaw(CONSOLE_SKILL_PATH + "/upload/batch",
                uploadQuery(false, null, null), "file", "skills.zip", "application/zip",
                buildMultiSkillZip(skills));
        JsonNode data = assertUploadResult(batch).get("data");
        assertEquals(2, data.size(), data.toString());
        assertBatchSuccess(data, firstSkill);
        assertBatchSuccess(data, secondSkill);
        addCleanup(() -> deleteSkillQuietly(firstSkill));
        addCleanup(() -> deleteSkillQuietly(secondSkill));
        assertSkillContent(getJsonOk(CONSOLE_SKILL_VERSION_PATH, skillVersionQuery(firstSkill, "1.0.0"))
                .get("data"), firstSkill, "1.0.0", "Batch body A.", "guide for " + firstSkill);
        assertSkillContent(getJsonOk(CONSOLE_SKILL_VERSION_PATH, skillVersionQuery(secondSkill, "1.0.0"))
                .get("data"), secondSkill, "1.0.0", "Batch body B.", "guide for " + secondSkill);
        postFormOk(CONSOLE_SKILL_PATH + "/force-publish", skillPublishForm(firstSkill, "1.0.0"));
        Map<String, String> shortVersionSkills = new LinkedHashMap<>();
        shortVersionSkills.put(firstSkill, "Batch body A v2.");
        HttpResponse shortVersionBatch = postMultipartRaw(CONSOLE_SKILL_PATH + "/upload/batch",
                uploadQuery(false, null, null), "file", "short-version-skills.zip",
                "application/zip", buildMultiSkillZip(shortVersionSkills, "1.0"));
        JsonNode shortVersionData = assertUploadResult(shortVersionBatch).get("data");
        assertEquals(1, shortVersionData.size(), shortVersionData.toString());
        assertBatchSuccess(shortVersionData, firstSkill);
        assertSkillContent(getJsonOk(CONSOLE_SKILL_VERSION_PATH, skillVersionQuery(firstSkill, "1.0.1"))
                .get("data"), firstSkill, "1.0.1", "Batch body A v2.", "guide for " + firstSkill);

        String validSkill = randomAiName("batch-valid");
        byte[] partialZip = buildPartiallyInvalidMultiSkillZip(validSkill, "Valid batch body.");
        JsonNode partialPrecheck = assertUploadResult(postMultipartRaw(CONSOLE_SKILL_PATH
                        + "/upload/precheck", precheckQuery(null), "file", "partial.zip",
                "application/zip", partialZip)).get("data");
        assertEquals(3, partialPrecheck.size(), partialPrecheck.toString());
        assertPrecheckFailure(partialPrecheck, "INVALID_SKILL", "invalid-skill/",
                "YAML front matter");
        assertPrecheckFailure(partialPrecheck, "NOT_A_SKILL", "not-a-skill/",
                "SKILL.md not found");
        HttpResponse partial = postMultipartRaw(CONSOLE_SKILL_PATH + "/upload/batch",
                uploadQuery(false, null, null), "file", "partial.zip", "application/zip",
                partialZip);
        JsonNode partialData = assertUploadResult(partial).get("data");
        assertEquals(3, partialData.size(), partialData.toString());
        assertBatchSuccess(partialData, validSkill);
        assertBatchFailure(partialData, "invalid-skill", "INVALID_SKILL");
        assertBatchFailure(partialData, "not-a-skill", "NOT_A_SKILL");
        addCleanup(() -> deleteSkillQuietly(validSkill));
    }

    @Test
    public void testSkillUploadValidationErrors() throws Exception {
        String skillName = randomAiName("upload-invalid");
        assertError(postMultipartRaw(CONSOLE_SKILL_PATH + "/upload", uploadQuery(false, null, null),
                "file", "empty.zip", "application/zip", new byte[0]), 400,
                ErrorCode.DATA_EMPTY, "File is required");
        assertError(postMultipartRaw(CONSOLE_SKILL_PATH + "/upload", uploadQuery(false, null, null),
                "file", "plain.zip", "application/zip", "not a zip".getBytes()), 400,
                ErrorCode.PARSING_DATA_FAILED, "Failed to parse zip file");
        assertUploadSuccess(postMultipartRaw(CONSOLE_SKILL_PATH + "/upload",
                uploadQuery(false, "bad-version", null), "file", "skill.zip", "application/zip",
                buildSkillZip(skillName, null, "Body.", "Guide.")), skillName);
        addCleanup(() -> deleteSkillQuietly(skillName));
        assertSkillContent(getJsonOk(CONSOLE_SKILL_VERSION_PATH, skillVersionQuery(skillName, "0.0.1"))
                .get("data"), skillName, "0.0.1", "Body.", "Guide.");
        assertError(postMultipartRaw(CONSOLE_SKILL_PATH + "/upload/batch", uploadQuery(false, null, null),
                "file", "plain.zip", "application/zip", "not a zip".getBytes()), 400,
                ErrorCode.PARSING_DATA_FAILED, "Failed to parse zip file");
    }

    private Query uploadQuery(boolean overwrite, String targetVersion, String commitMsg) {
        Query query = Query.newInstance().addParam("namespaceId", DEFAULT_NAMESPACE)
                .addParam("overwrite", String.valueOf(overwrite));
        addIfNotBlank(query, "targetVersion", targetVersion);
        addIfNotBlank(query, "commitMsg", commitMsg);
        return query;
    }

    private Query precheckQuery(String targetVersion) {
        Query query = Query.newInstance().addParam("namespaceId", DEFAULT_NAMESPACE);
        addIfNotBlank(query, "targetVersion", targetVersion);
        return query;
    }

    private void assertUploadSuccess(HttpResponse response, String skillName) {
        JsonNode root = assertUploadResult(response);
        assertEquals(skillName, root.get("data").asText(), root.toString());
    }

    private JsonNode assertUploadResult(HttpResponse response) {
        assertEquals(200, response.code(), response.body());
        JsonNode root = JacksonUtils.toObj(response.body());
        assertSuccess(root);
        return root;
    }

    private void assertBatchSuccess(JsonNode array, String expected) {
        for (JsonNode item : array) {
            if (expected.equals(item.get("name").asText())) {
                assertTrue(item.get("success").asBoolean(), item.toString());
                assertEquals("SUCCESS", item.get("errorCode").asText(), item.toString());
                assertEquals("success", item.get("errorMessage").asText(), item.toString());
                return;
            }
        }
        throw new AssertionError("Expected successful batch item " + expected + " in " + array);
    }

    private void assertPrecheckFailure(JsonNode array, String precheckCode, String entryPath,
            String reasonFragment) {
        for (JsonNode item : array) {
            if (precheckCode.equals(item.get("precheckCode").asText())
                    && entryPath.equals(item.get("entryPath").asText())) {
                assertTrue(item.get("reason").asText().contains(reasonFragment), item.toString());
                return;
            }
        }
        throw new AssertionError("Expected precheck failure " + entryPath + " in " + array);
    }

    private void assertBatchFailure(JsonNode array, String name, String errorCode) {
        for (JsonNode item : array) {
            if (name.equals(item.get("name").asText())) {
                assertFalse(item.get("success").asBoolean(), item.toString());
                assertEquals(errorCode, item.get("errorCode").asText(), item.toString());
                assertFalse(item.get("errorMessage").asText().isBlank(), item.toString());
                return;
            }
        }
        throw new AssertionError("Expected batch failure " + name + " in " + array);
    }

    private void assertCompactPrecheckResult(JsonNode precheck) {
        String[] removedFields = {"description", "latestVersion", "resolvedVersion",
                "versionSource", "writable",
                "versionExists", "draftExists", "reviewingExists", "status", "conflictTypes",
                "warnings", "errors", "actions"};
        for (String field : removedFields) {
            assertFalse(precheck.has(field), precheck.toString());
        }
    }
}
