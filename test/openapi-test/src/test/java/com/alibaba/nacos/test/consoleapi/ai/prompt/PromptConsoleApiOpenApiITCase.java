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

package com.alibaba.nacos.test.consoleapi.ai.prompt;

import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.test.consoleapi.ai.AiConsoleApiBaseITCase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for prompt console OpenAPI {@code /v3/console/ai/prompt}.
 *
 * <p>Scenario coverage:
 * <ul>
 *     <li>Expected capability: draft creation and update persist editable content; draft delete removes only the
 *     editing version while retaining prompt governance metadata; force-publish makes a version online and latest;
 *     governance, version detail, version list, list, Markdown download, labels, description, bizTags,
 *     online/offline, and delete expose the expected prompt state.</li>
 *     <li>Boundary/validation: namespace defaults to public; list supports accurate/blur and bizTags filters while
 *     clamping page arguments; promptKey, template or basedOnVersion, version, labels, latest label preservation,
 *     and description are required where controller forms require them.
 *     Runtime-only legacy endpoints are intentionally not covered because they are not exposed by the console
 *     controller.</li>
 *     <li>Exception/error handling: absent prompts and versions return controlled RESOURCE_NOT_FOUND bodies, direct
 *     publish from draft and redraft from online return validation errors instead of HTTP 500, and invalid search
 *     returns HTTP 400.</li>
 * </ul>
 *
 * @author xiweng.yy
 */
public class PromptConsoleApiOpenApiITCase extends AiConsoleApiBaseITCase {

    @Test
    public void testPromptLifecycleGovernanceListDownloadAndDelete() throws Exception {
        String promptKey = randomPromptKey("prompt");
        String draftTemplate = "Hello {{name}} from draft";
        String updatedTemplate = "Hello {{name}} from updated draft";
        JsonNode draft = postFormOk(CONSOLE_PROMPT_PATH + "/draft",
                promptDraftForm(promptKey, "1.0.0", draftTemplate, "initial description", "openapi,console"));
        assertEquals("1.0.0", draft.get("data").asText(), draft.toString());
        addCleanup(() -> deletePromptQuietly(promptKey));

        JsonNode updated = putFormOk(CONSOLE_PROMPT_PATH + "/draft",
                promptUpdateDraftForm(promptKey, updatedTemplate));
        assertEquals("ok", updated.get("data").asText(), updated.toString());
        JsonNode draftDetail = getJsonOk(CONSOLE_PROMPT_VERSION_PATH, promptVersionQuery(promptKey, "1.0.0"))
                .get("data");
        assertPromptVersion(draftDetail, promptKey, "1.0.0", "draft", updatedTemplate);

        assertError(postRaw(CONSOLE_PROMPT_PATH + "/publish", queryFrom(promptPublishForm(promptKey, "1.0.0"))),
                400, ErrorCode.PARAMETER_VALIDATE_ERROR, "Only reviewing version can be published");
        JsonNode published = postFormOk(CONSOLE_PROMPT_PATH + "/force-publish",
                withUpdateLatestLabel(promptPublishForm(promptKey, "1.0.0")));
        assertEquals("ok", published.get("data").asText(), published.toString());

        JsonNode governance = getJsonOk(CONSOLE_PROMPT_GOVERNANCE_PATH, promptQuery(promptKey)).get("data");
        assertEquals(promptKey, governance.get("promptKey").asText(), governance.toString());
        assertEquals("1.0.0", governance.get("latestVersion").asText(), governance.toString());
        assertEquals(1, governance.get("onlineCnt").asInt(), governance.toString());
        assertEquals("1.0.0", governance.get("labels").get("latest").asText(), governance.toString());
        assertEquals("initial description", governance.get("description").asText(), governance.toString());
        assertEquals("openapi,console", governance.get("bizTagsStr").asText(), governance.toString());

        JsonNode onlineDetail = getJsonOk(CONSOLE_PROMPT_VERSION_PATH, promptVersionQuery(promptKey, "1.0.0"))
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

        ByteResponse download = getRawBytes(CONSOLE_PROMPT_VERSION_DOWNLOAD_PATH,
                promptVersionQuery(promptKey, "1.0.0"));
        assertEquals(200, download.code(), new String(download.body(), StandardCharsets.UTF_8));
        assertTrue(download.contentDisposition().contains(promptKey + "_1.0.0.md"),
                download.contentDisposition());
        String markdown = new String(download.body(), StandardCharsets.UTF_8);
        assertTrue(markdown.contains("promptKey: \"" + promptKey + "\""), markdown);
        assertTrue(markdown.contains(updatedTemplate), markdown);

        assertEquals("ok", putFormOk(CONSOLE_PROMPT_PATH + "/labels",
                promptLabelsForm(promptKey, "{\"stable\":\"1.0.0\"}")).get("data").asText());
        assertEquals("ok", putFormOk(CONSOLE_PROMPT_PATH + "/description",
                promptDescriptionForm(promptKey, "updated description")).get("data").asText());
        assertEquals("ok", putFormOk(CONSOLE_PROMPT_PATH + "/biz-tags",
                promptBizTagsForm(promptKey, "updated,openapi")).get("data").asText());
        JsonNode updatedGovernance = getJsonOk(CONSOLE_PROMPT_GOVERNANCE_PATH, promptQuery(promptKey))
                .get("data");
        assertEquals("1.0.0", updatedGovernance.get("labels").get("stable").asText(),
                updatedGovernance.toString());
        assertEquals("1.0.0", updatedGovernance.get("labels").get("latest").asText(),
                updatedGovernance.toString());
        assertEquals("ok", putFormOk(CONSOLE_PROMPT_PATH + "/labels",
                promptLabelsForm(promptKey, "{\"stable\":\"1.0.0\",\"latest\":\"9.9.9\"}"))
                .get("data").asText());
        updatedGovernance = getJsonOk(CONSOLE_PROMPT_GOVERNANCE_PATH, promptQuery(promptKey))
                .get("data");
        assertEquals("1.0.0", updatedGovernance.get("labels").get("stable").asText(),
                updatedGovernance.toString());
        assertEquals("1.0.0", updatedGovernance.get("labels").get("latest").asText(),
                updatedGovernance.toString());
        assertEquals("updated description", updatedGovernance.get("description").asText(),
                updatedGovernance.toString());
        assertEquals("updated,openapi", updatedGovernance.get("bizTagsStr").asText(),
                updatedGovernance.toString());

        assertEquals("ok", postFormOk(CONSOLE_PROMPT_PATH + "/offline",
                promptPublishForm(promptKey, "1.0.0")).get("data").asText());
        assertPromptVersion(getJsonOk(CONSOLE_PROMPT_VERSION_PATH, promptVersionQuery(promptKey, "1.0.0"))
                .get("data"), promptKey, "1.0.0", "offline", updatedTemplate);
        JsonNode offlineGovernance = getJsonOk(CONSOLE_PROMPT_GOVERNANCE_PATH, promptQuery(promptKey))
                .get("data");
        assertTrue(offlineGovernance.get("labels").get("latest") == null,
                offlineGovernance.toString());
        assertEquals("ok", postFormOk(CONSOLE_PROMPT_PATH + "/online",
                promptPublishForm(promptKey, "1.0.0")).get("data").asText());
        assertPromptVersion(getJsonOk(CONSOLE_PROMPT_VERSION_PATH, promptVersionQuery(promptKey, "1.0.0"))
                .get("data"), promptKey, "1.0.0", "online", updatedTemplate);
        JsonNode reonlineGovernance = getJsonOk(CONSOLE_PROMPT_GOVERNANCE_PATH, promptQuery(promptKey))
                .get("data");
        assertEquals("1.0.0", reonlineGovernance.get("labels").get("latest").asText(),
                reonlineGovernance.toString());

        assertError(postRaw(CONSOLE_PROMPT_PATH + "/redraft", queryFrom(promptPublishForm(promptKey, "1.0.0"))),
                400, ErrorCode.PARAMETER_VALIDATE_ERROR, "Only reviewed version can be re-edited");

        JsonNode delete = deleteJsonOk(CONSOLE_PROMPT_PATH, promptQuery(promptKey));
        assertTrue(delete.get("data").asBoolean(), delete.toString());
        assertError(getRaw(CONSOLE_PROMPT_GOVERNANCE_PATH, promptQuery(promptKey)), 404,
                ErrorCode.RESOURCE_NOT_FOUND, "Prompt not found");
    }

    @Test
    public void testPromptDeleteDraftRemovesEditingVersionOnly() throws Exception {
        String promptKey = randomPromptKey("delete-draft");
        postFormOk(CONSOLE_PROMPT_PATH + "/draft",
                promptDraftForm(promptKey, "1.0.0", "Draft to delete {{name}}", "delete draft desc",
                        "delete-draft"));
        addCleanup(() -> deletePromptQuietly(promptKey));
        assertPromptVersion(getJsonOk(CONSOLE_PROMPT_VERSION_PATH, promptVersionQuery(promptKey, "1.0.0"))
                .get("data"), promptKey, "1.0.0", "draft", "Draft to delete {{name}}");

        JsonNode deleted = deleteJsonOk(CONSOLE_PROMPT_PATH + "/draft", promptQuery(promptKey));
        assertEquals("ok", deleted.get("data").asText(), deleted.toString());

        JsonNode governance = getJsonOk(CONSOLE_PROMPT_GOVERNANCE_PATH, promptQuery(promptKey))
                .get("data");
        assertEquals(promptKey, governance.get("promptKey").asText(), governance.toString());
        assertTrue(governance.path("editingVersion").isNull()
                || governance.path("editingVersion").isMissingNode(), governance.toString());
        assertEquals(0, governance.get("versions").size(), governance.toString());
        assertError(getRaw(CONSOLE_PROMPT_VERSION_PATH, promptVersionQuery(promptKey, "1.0.0")), 404,
                ErrorCode.RESOURCE_NOT_FOUND, "Prompt version not found");
    }

    @Test
    public void testPromptSubmitDraftSuccess() throws Exception {
        String promptKey = randomPromptKey("submit");
        postFormOk(CONSOLE_PROMPT_PATH + "/draft",
                promptDraftForm(promptKey, "1.0.0", "Submit {{name}} template", "submit desc", "submit"));
        addCleanup(() -> deletePromptQuietly(promptKey));

        JsonNode submit = postFormOk(CONSOLE_PROMPT_PATH + "/submit", promptQueryForm(promptKey));
        assertEquals("1.0.0", submit.get("data").asText(), submit.toString());
        JsonNode submitted = getJsonOk(CONSOLE_PROMPT_VERSION_PATH, promptVersionQuery(promptKey, "1.0.0"))
                .get("data");
        assertTrue("online".equals(submitted.get("status").asText())
                || "reviewing".equals(submitted.get("status").asText()), submitted.toString());
        if (!"online".equals(submitted.get("status").asText())) {
            postFormOk(CONSOLE_PROMPT_PATH + "/force-publish", promptPublishForm(promptKey, "1.0.0"));
        }
        assertPromptVersion(getJsonOk(CONSOLE_PROMPT_VERSION_PATH, promptVersionQuery(promptKey, "1.0.0"))
                .get("data"), promptKey, "1.0.0", "online", "Submit {{name}} template");
    }

    @Test
    public void testPromptValidationAndNotFoundErrors() throws Exception {
        assertError(getRaw(CONSOLE_PROMPT_GOVERNANCE_PATH,
                Query.newInstance().addParam("namespaceId", DEFAULT_NAMESPACE)), 400,
                ErrorCode.PARAMETER_MISSING, "promptKey");
        assertError(postRaw(CONSOLE_PROMPT_PATH + "/draft",
                queryFrom(promptQueryForm(randomPromptKey("missing-template")))), 400,
                ErrorCode.PARAMETER_MISSING, "Either 'basedOnVersion' or 'template'");
        assertError(putRaw(CONSOLE_PROMPT_PATH + "/draft",
                promptQuery(randomPromptKey("missing-template-update"))), 400,
                ErrorCode.PARAMETER_MISSING, "template");
        assertError(deleteRaw(CONSOLE_PROMPT_PATH + "/draft",
                Query.newInstance().addParam("namespaceId", DEFAULT_NAMESPACE)), 400,
                ErrorCode.PARAMETER_MISSING, "promptKey");
        assertError(postRaw(CONSOLE_PROMPT_PATH + "/force-publish",
                queryFrom(promptQueryForm(randomPromptKey("missing-version")))), 400,
                ErrorCode.PARAMETER_MISSING, "version");
        assertError(putRaw(CONSOLE_PROMPT_PATH + "/labels", promptQuery(randomPromptKey("missing-labels"))),
                400, ErrorCode.PARAMETER_MISSING, "labels");
        assertError(putRaw(CONSOLE_PROMPT_PATH + "/description",
                promptQuery(randomPromptKey("missing-description"))), 400,
                ErrorCode.PARAMETER_MISSING, "description");
        assertError(getRaw(CONSOLE_PROMPT_LIST_PATH, Query.newInstance().addParam("search", "invalid")),
                400, ErrorCode.PARAMETER_VALIDATE_ERROR, "search");

        String absentPrompt = randomPromptKey("absent");
        assertError(getRaw(CONSOLE_PROMPT_GOVERNANCE_PATH, promptQuery(absentPrompt)), 404,
                ErrorCode.RESOURCE_NOT_FOUND, "Prompt not found");
        assertError(getRaw(CONSOLE_PROMPT_VERSION_PATH, promptVersionQuery(absentPrompt, "1.0.0")), 404,
                ErrorCode.RESOURCE_NOT_FOUND, "Prompt not found");
        assertError(getRaw(CONSOLE_PROMPT_VERSION_DOWNLOAD_PATH, promptVersionQuery(absentPrompt, "1.0.0")),
                404, ErrorCode.RESOURCE_NOT_FOUND, "Prompt not found");
        assertError(deleteRaw(CONSOLE_PROMPT_PATH + "/draft", promptQuery(absentPrompt)), 404,
                ErrorCode.RESOURCE_NOT_FOUND, "not found");
    }

    private void assertPromptVersionPageContains(String promptKey, String version, String status)
            throws Exception {
        JsonNode page = getJsonOk(CONSOLE_PROMPT_VERSIONS_PATH, promptQuery(promptKey)
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
        JsonNode page = getJsonOk(CONSOLE_PROMPT_LIST_PATH, query).get("data");
        assertEmptyPageShape(page);
        JsonNode found = findByName(page, "promptKey", promptKey);
        assertTrue(!found.isMissingNode(), page.toString());
        assertEquals(promptKey, found.get("promptKey").asText(), found.toString());
    }

}
