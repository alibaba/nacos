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

package com.alibaba.nacos.test.adminapi.ai.prompt;

import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.test.adminapi.ai.AiAdminApiBaseITCase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for prompt admin OpenAPI {@code /nacos/v3/admin/ai/prompt}.
 *
 * <p>Scenario coverage:
 * <ul>
 *     <li>Expected capability: draft creation and update persist editable content; draft delete removes only the
 *     editing version while retaining prompt governance metadata; force-publish makes a version online and latest;
 *     governance, version detail, version list, list, Markdown download, labels, description, bizTags,
 *     online/offline, and delete expose the expected prompt state.</li>
 *     <li>Boundary/validation: namespace defaults to public; list supports accurate/blur and bizTags filters while
 *     clamping page arguments; promptKey, template or basedOnVersion, version, labels, latest label preservation,
 *     and description are required where controller forms require them;
 *     legacy version format is validated.</li>
 *     <li>Exception/error handling: absent prompts and versions return controlled RESOURCE_NOT_FOUND bodies, direct
 *     publish from draft and redraft from online return validation errors instead of HTTP 500, and invalid search
 *     returns HTTP 400.</li>
 * </ul>
 *
 * @author xiweng.yy
 */
public class PromptAdminApiOpenApiITCase extends AiAdminApiBaseITCase {

    @Test
    public void testPromptLifecycleGovernanceListDownloadAndDelete() throws Exception {
        String promptKey = randomPromptKey("prompt");
        String draftTemplate = "Hello {{name}} from draft";
        String updatedTemplate = "Hello {{name}} from updated draft";
        JsonNode draft = postFormOk(ADMIN_PROMPT_PATH + "/draft",
                promptDraftForm(promptKey, "1.0.0", draftTemplate, "initial description", "openapi,admin"));
        assertEquals("1.0.0", draft.get("data").asText(), draft.toString());
        addCleanup(() -> deletePromptQuietly(promptKey));

        JsonNode updated = putFormOk(ADMIN_PROMPT_PATH + "/draft",
                promptUpdateDraftForm(promptKey, updatedTemplate));
        assertEquals("ok", updated.get("data").asText(), updated.toString());
        JsonNode draftDetail = getJsonOk(ADMIN_PROMPT_VERSION_PATH, promptVersionQuery(promptKey, "1.0.0"))
                .get("data");
        assertPromptVersion(draftDetail, promptKey, "1.0.0", "draft", updatedTemplate);

        assertError(postRaw(ADMIN_PROMPT_PATH + "/publish", queryFrom(promptPublishForm(promptKey, "1.0.0"))),
                400, ErrorCode.PARAMETER_VALIDATE_ERROR, "Only reviewing version can be published");
        JsonNode published = postFormOk(ADMIN_PROMPT_PATH + "/force-publish",
                withUpdateLatestLabel(promptPublishForm(promptKey, "1.0.0")));
        assertEquals("ok", published.get("data").asText(), published.toString());

        JsonNode governance = getJsonOk(ADMIN_PROMPT_GOVERNANCE_PATH, promptQuery(promptKey)).get("data");
        assertEquals(promptKey, governance.get("promptKey").asText(), governance.toString());
        assertEquals("1.0.0", governance.get("latestVersion").asText(), governance.toString());
        assertEquals(1, governance.get("onlineCnt").asInt(), governance.toString());
        assertEquals("1.0.0", governance.get("labels").get("latest").asText(), governance.toString());
        assertEquals("initial description", governance.get("description").asText(), governance.toString());
        assertEquals("openapi,admin", governance.get("bizTagsStr").asText(), governance.toString());

        JsonNode onlineDetail = getJsonOk(ADMIN_PROMPT_VERSION_PATH, promptVersionQuery(promptKey, "1.0.0"))
                .get("data");
        assertPromptVersion(onlineDetail, promptKey, "1.0.0", "online", updatedTemplate);
        assertNotNull(onlineDetail.get("md5").asText(), onlineDetail.toString());

        assertPromptVersionPageContains(promptKey, "1.0.0", "online");
        assertPromptListContains(Query.newInstance().addParam("namespaceId", DEFAULT_NAMESPACE)
                .addParam("promptKey", promptKey).addParam("search", "accurate")
                .addParam("pageNo", "1").addParam("pageSize", "10"), promptKey);
        assertPromptListContains(Query.newInstance().addParam("namespaceId", DEFAULT_NAMESPACE)
                .addParam("promptKey", promptKey.substring(0, 8)).addParam("search", "blur")
                .addParam("bizTags", "openapi").addParam("pageNo", "0").addParam("pageSize", "1000"),
                promptKey);

        ByteResponse download = getRawBytes(ADMIN_PROMPT_VERSION_DOWNLOAD_PATH,
                promptVersionQuery(promptKey, "1.0.0"));
        assertEquals(200, download.code(), new String(download.body(), StandardCharsets.UTF_8));
        assertTrue(download.contentDisposition().contains(promptKey + "_1.0.0.md"),
                download.contentDisposition());
        String markdown = new String(download.body(), StandardCharsets.UTF_8);
        assertTrue(markdown.contains("promptKey: \"" + promptKey + "\""), markdown);
        assertTrue(markdown.contains(updatedTemplate), markdown);

        assertEquals("ok", putFormOk(ADMIN_PROMPT_PATH + "/labels",
                promptLabelsForm(promptKey, "{\"stable\":\"1.0.0\"}")).get("data").asText());
        assertEquals("ok", putFormOk(ADMIN_PROMPT_PATH + "/description",
                promptDescriptionForm(promptKey, "updated description")).get("data").asText());
        assertEquals("ok", putFormOk(ADMIN_PROMPT_PATH + "/biz-tags",
                promptBizTagsForm(promptKey, "updated,openapi")).get("data").asText());
        JsonNode updatedGovernance = getJsonOk(ADMIN_PROMPT_GOVERNANCE_PATH, promptQuery(promptKey))
                .get("data");
        assertEquals("1.0.0", updatedGovernance.get("labels").get("stable").asText(),
                updatedGovernance.toString());
        assertEquals("1.0.0", updatedGovernance.get("labels").get("latest").asText(),
                updatedGovernance.toString());
        assertEquals("ok", putFormOk(ADMIN_PROMPT_PATH + "/labels",
                promptLabelsForm(promptKey, "{\"stable\":\"1.0.0\",\"latest\":\"9.9.9\"}"))
                .get("data").asText());
        updatedGovernance = getJsonOk(ADMIN_PROMPT_GOVERNANCE_PATH, promptQuery(promptKey))
                .get("data");
        assertEquals("1.0.0", updatedGovernance.get("labels").get("stable").asText(),
                updatedGovernance.toString());
        assertEquals("1.0.0", updatedGovernance.get("labels").get("latest").asText(),
                updatedGovernance.toString());
        assertEquals("updated description", updatedGovernance.get("description").asText(),
                updatedGovernance.toString());
        assertEquals("updated,openapi", updatedGovernance.get("bizTagsStr").asText(),
                updatedGovernance.toString());

        assertEquals("ok", postFormOk(ADMIN_PROMPT_PATH + "/offline",
                promptPublishForm(promptKey, "1.0.0")).get("data").asText());
        assertPromptVersion(getJsonOk(ADMIN_PROMPT_VERSION_PATH, promptVersionQuery(promptKey, "1.0.0"))
                .get("data"), promptKey, "1.0.0", "offline", updatedTemplate);
        JsonNode offlineGovernance = getJsonOk(ADMIN_PROMPT_GOVERNANCE_PATH, promptQuery(promptKey))
                .get("data");
        assertTrue(offlineGovernance.get("labels").get("latest") == null,
                offlineGovernance.toString());
        assertEquals("ok", postFormOk(ADMIN_PROMPT_PATH + "/online",
                promptPublishForm(promptKey, "1.0.0")).get("data").asText());
        assertPromptVersion(getJsonOk(ADMIN_PROMPT_VERSION_PATH, promptVersionQuery(promptKey, "1.0.0"))
                .get("data"), promptKey, "1.0.0", "online", updatedTemplate);
        JsonNode reonlineGovernance = getJsonOk(ADMIN_PROMPT_GOVERNANCE_PATH, promptQuery(promptKey))
                .get("data");
        assertEquals("1.0.0", reonlineGovernance.get("labels").get("latest").asText(),
                reonlineGovernance.toString());

        assertError(postRaw(ADMIN_PROMPT_PATH + "/redraft", queryFrom(promptPublishForm(promptKey, "1.0.0"))),
                400, ErrorCode.PARAMETER_VALIDATE_ERROR, "Only reviewed version can be re-edited");

        JsonNode delete = deleteJsonOk(ADMIN_PROMPT_PATH, promptQuery(promptKey));
        assertTrue(delete.get("data").asBoolean(), delete.toString());
        assertError(getRaw(ADMIN_PROMPT_GOVERNANCE_PATH, promptQuery(promptKey)), 404,
                ErrorCode.RESOURCE_NOT_FOUND, "Prompt not found");
    }

    @Test
    public void testPromptDeleteDraftRemovesEditingVersionOnly() throws Exception {
        String promptKey = randomPromptKey("delete-draft");
        postFormOk(ADMIN_PROMPT_PATH + "/draft",
                promptDraftForm(promptKey, "1.0.0", "Draft to delete {{name}}", "delete draft desc",
                        "delete-draft"));
        addCleanup(() -> deletePromptQuietly(promptKey));
        assertPromptVersion(getJsonOk(ADMIN_PROMPT_VERSION_PATH, promptVersionQuery(promptKey, "1.0.0"))
                .get("data"), promptKey, "1.0.0", "draft", "Draft to delete {{name}}");

        JsonNode deleted = deleteJsonOk(ADMIN_PROMPT_PATH + "/draft", promptQuery(promptKey));
        assertEquals("ok", deleted.get("data").asText(), deleted.toString());

        JsonNode governance = getJsonOk(ADMIN_PROMPT_GOVERNANCE_PATH, promptQuery(promptKey))
                .get("data");
        assertEquals(promptKey, governance.get("promptKey").asText(), governance.toString());
        assertTrue(governance.path("editingVersion").isNull()
                || governance.path("editingVersion").isMissingNode(), governance.toString());
        assertEquals(0, governance.get("versions").size(), governance.toString());
        assertError(getRaw(ADMIN_PROMPT_VERSION_PATH, promptVersionQuery(promptKey, "1.0.0")), 404,
                ErrorCode.RESOURCE_NOT_FOUND, "Prompt version not found");
    }

    @Test
    public void testPromptSubmitAndLegacyCompatibilityEndpoints() throws Exception {
        String legacyPublishKey = randomPromptKey("legacy-publish");
        Map<String, String> legacyPublish = promptQueryForm(legacyPublishKey);
        legacyPublish.put("version", "1.0.0");
        legacyPublish.put("template", "Legacy publish {{name}} template");
        legacyPublish.put("variables", "[{\"name\":\"name\",\"defaultValue\":\"Nacos\",\"description\":\"target name\"}]");
        legacyPublish.put("commitMsg", "openapi prompt admin legacy publish");
        legacyPublish.put("description", "legacy publish desc");
        legacyPublish.put("bizTags", "legacy-publish");
        assertTrue(postFormOk(ADMIN_PROMPT_PATH, legacyPublish).get("data").asBoolean());
        addCleanup(() -> deletePromptQuietly(legacyPublishKey));
        JsonNode legacyVersion = getJsonOk(ADMIN_PROMPT_VERSION_PATH,
                promptVersionQuery(legacyPublishKey, "1.0.0")).get("data");
        assertTrue("online".equals(legacyVersion.get("status").asText())
                || "reviewing".equals(legacyVersion.get("status").asText()), legacyVersion.toString());
        if (!"online".equals(legacyVersion.get("status").asText())) {
            postFormOk(ADMIN_PROMPT_PATH + "/force-publish",
                    promptPublishForm(legacyPublishKey, "1.0.0"));
        }
        assertPromptRuntimeDetail(getJsonOk(ADMIN_PROMPT_PATH + "/detail",
                promptVersionQuery(legacyPublishKey, "1.0.0")).get("data"), legacyPublishKey, "1.0.0",
                "Legacy publish {{name}} template");

        String promptKey = randomPromptKey("legacy");
        postFormOk(ADMIN_PROMPT_PATH + "/draft",
                promptDraftForm(promptKey, "1.0.0", "Legacy {{name}} template", "legacy desc", "legacy"));
        addCleanup(() -> deletePromptQuietly(promptKey));

        JsonNode submit = postFormOk(ADMIN_PROMPT_PATH + "/submit", promptQueryForm(promptKey));
        assertEquals("1.0.0", submit.get("data").asText(), submit.toString());
        JsonNode submitted = getJsonOk(ADMIN_PROMPT_VERSION_PATH, promptVersionQuery(promptKey, "1.0.0"))
                .get("data");
        assertTrue("online".equals(submitted.get("status").asText())
                || "reviewing".equals(submitted.get("status").asText()), submitted.toString());
        if (!"online".equals(submitted.get("status").asText())) {
            postFormOk(ADMIN_PROMPT_PATH + "/force-publish", promptPublishForm(promptKey, "1.0.0"));
        }

        JsonNode metadata = getJsonOk(ADMIN_PROMPT_PATH + "/metadata", promptQuery(promptKey)).get("data");
        assertEquals(promptKey, metadata.get("promptKey").asText(), metadata.toString());
        JsonNode detail = getJsonOk(ADMIN_PROMPT_PATH + "/detail",
                promptVersionQuery(promptKey, "1.0.0")).get("data");
        assertPromptRuntimeDetail(detail, promptKey, "1.0.0", "Legacy {{name}} template");

        Map<String, String> bindLabel = promptQueryForm(promptKey);
        bindLabel.put("label", "legacy");
        bindLabel.put("version", "1.0.0");
        assertTrue(putFormOk(ADMIN_PROMPT_PATH + "/label", bindLabel).get("data").asBoolean());
        assertEquals("1.0.0", getJsonOk(ADMIN_PROMPT_GOVERNANCE_PATH, promptQuery(promptKey))
                .get("data").get("labels").get("legacy").asText());
        Map<String, String> latestBindLabel = promptQueryForm(promptKey);
        latestBindLabel.put("label", "latest");
        latestBindLabel.put("version", "1.0.0");
        assertError(putRaw(ADMIN_PROMPT_PATH + "/label", queryFrom(latestBindLabel)), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "reserved");

        Map<String, String> metadataUpdate = promptQueryForm(promptKey);
        metadataUpdate.put("description", "legacy metadata");
        metadataUpdate.put("bizTags", "legacy,compat");
        assertTrue(putFormOk(ADMIN_PROMPT_PATH + "/metadata", metadataUpdate).get("data").asBoolean());
        JsonNode updated = getJsonOk(ADMIN_PROMPT_GOVERNANCE_PATH, promptQuery(promptKey)).get("data");
        assertEquals("legacy metadata", updated.get("description").asText(), updated.toString());
        assertEquals("legacy,compat", updated.get("bizTagsStr").asText(), updated.toString());

        Map<String, String> unbindLabel = promptQueryForm(promptKey);
        unbindLabel.put("label", "legacy");
        assertTrue(deleteJsonOk(ADMIN_PROMPT_PATH + "/label", queryFrom(unbindLabel)).get("data").asBoolean());
        Map<String, String> latestUnbindLabel = promptQueryForm(promptKey);
        latestUnbindLabel.put("label", "latest");
        assertError(deleteRaw(ADMIN_PROMPT_PATH + "/label", queryFrom(latestUnbindLabel)), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "reserved");
    }

    @Test
    public void testPromptValidationAndNotFoundErrors() throws Exception {
        assertError(getRaw(ADMIN_PROMPT_GOVERNANCE_PATH,
                Query.newInstance().addParam("namespaceId", DEFAULT_NAMESPACE)), 400,
                ErrorCode.PARAMETER_MISSING, "promptKey");
        assertError(postRaw(ADMIN_PROMPT_PATH + "/draft",
                queryFrom(promptQueryForm(randomPromptKey("missing-template")))), 400,
                ErrorCode.PARAMETER_MISSING, "Either 'basedOnVersion' or 'template'");
        assertError(putRaw(ADMIN_PROMPT_PATH + "/draft",
                promptQuery(randomPromptKey("missing-template-update"))), 400,
                ErrorCode.PARAMETER_MISSING, "template");
        assertError(deleteRaw(ADMIN_PROMPT_PATH + "/draft",
                Query.newInstance().addParam("namespaceId", DEFAULT_NAMESPACE)), 400,
                ErrorCode.PARAMETER_MISSING, "promptKey");
        assertError(postRaw(ADMIN_PROMPT_PATH + "/force-publish",
                queryFrom(promptQueryForm(randomPromptKey("missing-version")))), 400,
                ErrorCode.PARAMETER_MISSING, "version");
        assertError(putRaw(ADMIN_PROMPT_PATH + "/labels", promptQuery(randomPromptKey("missing-labels"))),
                400, ErrorCode.PARAMETER_MISSING, "labels");
        assertError(putRaw(ADMIN_PROMPT_PATH + "/description",
                promptQuery(randomPromptKey("missing-description"))), 400,
                ErrorCode.PARAMETER_MISSING, "description");
        assertError(getRaw(ADMIN_PROMPT_LIST_PATH, Query.newInstance().addParam("search", "invalid")),
                400, ErrorCode.PARAMETER_VALIDATE_ERROR, "search");

        String absentPrompt = randomPromptKey("absent");
        assertError(getRaw(ADMIN_PROMPT_GOVERNANCE_PATH, promptQuery(absentPrompt)), 404,
                ErrorCode.RESOURCE_NOT_FOUND, "Prompt not found");
        assertError(getRaw(ADMIN_PROMPT_VERSION_PATH, promptVersionQuery(absentPrompt, "1.0.0")), 404,
                ErrorCode.RESOURCE_NOT_FOUND, "Prompt not found");
        assertError(getRaw(ADMIN_PROMPT_VERSION_DOWNLOAD_PATH, promptVersionQuery(absentPrompt, "1.0.0")),
                404, ErrorCode.RESOURCE_NOT_FOUND, "Prompt not found");
        assertError(deleteRaw(ADMIN_PROMPT_PATH + "/draft", promptQuery(absentPrompt)), 404,
                ErrorCode.RESOURCE_NOT_FOUND, "not found");

        Map<String, String> legacyPublish = promptQueryForm(randomPromptKey("legacy-invalid-version"));
        legacyPublish.put("version", "bad-version");
        legacyPublish.put("template", "invalid legacy version");
        assertError(postRaw(ADMIN_PROMPT_PATH, queryFrom(legacyPublish)), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "major.minor.patch");
    }

    private void assertPromptVersionPageContains(String promptKey, String version, String status)
            throws Exception {
        JsonNode page = getJsonOk(ADMIN_PROMPT_VERSIONS_PATH, promptQuery(promptKey)
                .addParam("pageNo", "1").addParam("pageSize", "10")).get("data");
        assertEmptyPageShape(page);
        for (JsonNode item : page.get("pageItems")) {
            if (version.equals(item.get("version").asText())) {
                assertEquals(status, item.get("status").asText(), item.toString());
                return;
            }
        }
        throw new AssertionError("Version " + version + " not found in " + page);
    }

    private void assertPromptListContains(Query query, String promptKey) throws Exception {
        JsonNode page = getJsonOk(ADMIN_PROMPT_LIST_PATH, query).get("data");
        assertEmptyPageShape(page);
        JsonNode found = findByName(page, "promptKey", promptKey);
        assertTrue(!found.isMissingNode(), page.toString());
        assertEquals(promptKey, found.get("promptKey").asText(), found.toString());
    }

    private void assertPromptRuntimeDetail(JsonNode data, String promptKey, String version, String template) {
        assertEquals(promptKey, data.get("promptKey").asText(), data.toString());
        assertEquals(version, data.get("version").asText(), data.toString());
        assertEquals(template, data.get("template").asText(), data.toString());
        assertEquals("name", data.get("variables").get(0).get("name").asText(), data.toString());
    }
}
