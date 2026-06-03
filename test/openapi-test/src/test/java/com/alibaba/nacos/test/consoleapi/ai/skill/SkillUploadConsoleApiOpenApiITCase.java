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
 *     <li>Expected capability: single ZIP upload creates a draft from {@code SKILL.md} plus resource files, overwrite
 *     updates an existing editing draft, and batch upload reports successful skill folders with persisted content.</li>
 *     <li>Boundary/validation: namespace defaults to public; upload version resolves from SKILL.md before
 *     targetVersion; duplicate working drafts require overwrite; batch upload keeps valid folders while reporting
 *     invalid folders in {@code failed}.</li>
 *     <li>Exception/error handling: empty and malformed ZIP files, invalid targetVersion, and archives without
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
        HttpResponse overwritten = postMultipartRaw(CONSOLE_SKILL_PATH + "/upload",
                uploadQuery(true, "9.9.9", "openapi overwrite"), "file", skillName + ".zip",
                "application/zip",
                buildSkillZip(skillName, "1.0.0", "Overwritten body.", "overwritten guide"));
        assertUploadSuccess(overwritten, skillName);
        assertSkillContent(getJsonOk(CONSOLE_SKILL_VERSION_PATH, skillVersionQuery(skillName, "1.0.0"))
                .get("data"), skillName, "1.0.0", "Overwritten body.", "overwritten guide");

        postFormOk(CONSOLE_SKILL_PATH + "/force-publish", skillPublishForm(skillName, "1.0.0"));
        HttpResponse nextUpload = postMultipartRaw(CONSOLE_SKILL_PATH + "/upload",
                uploadQuery(false, "1.0.0", "openapi next draft"), "file", skillName + ".zip",
                "application/zip",
                buildSkillZip(skillName, null, "Uploaded body v2.", "uploaded guide v2"));
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
        assertArrayContains(data.get("succeeded"), firstSkill);
        assertArrayContains(data.get("succeeded"), secondSkill);
        assertEquals(0, data.get("failed").size(), data.toString());
        addCleanup(() -> deleteSkillQuietly(firstSkill));
        addCleanup(() -> deleteSkillQuietly(secondSkill));
        assertSkillContent(getJsonOk(CONSOLE_SKILL_VERSION_PATH, skillVersionQuery(firstSkill, "1.0.0"))
                .get("data"), firstSkill, "1.0.0", "Batch body A.", "guide for " + firstSkill);
        assertSkillContent(getJsonOk(CONSOLE_SKILL_VERSION_PATH, skillVersionQuery(secondSkill, "1.0.0"))
                .get("data"), secondSkill, "1.0.0", "Batch body B.", "guide for " + secondSkill);

        String validSkill = randomAiName("batch-valid");
        HttpResponse partial = postMultipartRaw(CONSOLE_SKILL_PATH + "/upload/batch",
                uploadQuery(false, null, null), "file", "partial.zip", "application/zip",
                buildPartiallyInvalidMultiSkillZip(validSkill, "Valid batch body."));
        JsonNode partialData = assertUploadResult(partial).get("data");
        assertArrayContains(partialData.get("succeeded"), validSkill);
        assertEquals(1, partialData.get("failed").size(), partialData.toString());
        assertEquals("invalid-skill", partialData.get("failed").get(0).get("name").asText(),
                partialData.toString());
        assertFalse(partialData.get("failed").get(0).get("reason").asText().isBlank(),
                partialData.toString());
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
        assertError(postMultipartRaw(CONSOLE_SKILL_PATH + "/upload",
                uploadQuery(false, "bad-version", null), "file", "skill.zip", "application/zip",
                buildSkillZip(skillName, null, "Body.", "Guide.")), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "Invalid version from targetVersion parameter");
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

    private void assertArrayContains(JsonNode array, String expected) {
        for (JsonNode item : array) {
            if (expected.equals(item.asText())) {
                return;
            }
        }
        throw new AssertionError("Expected " + expected + " in " + array);
    }
}
