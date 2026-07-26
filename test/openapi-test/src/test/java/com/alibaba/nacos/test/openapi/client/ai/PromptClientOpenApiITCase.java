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

package com.alibaba.nacos.test.openapi.client.ai;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.http.param.Query;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for the AI prompt client Open API {@code GET /nacos/v3/client/ai/prompt}.
 *
 * <p>Scenario coverage:
 * <ul>
 *     <li>Expected capability: a prompt published through admin APIs can be queried by latest, explicit version, and
 *     label, with promptKey, version, template, md5, and variables preserved.</li>
 *     <li>Boundary/validation: omitted namespaceId uses the public namespace; explicit version has priority over
 *     label; an unknown label falls back to latest; missing promptKey is rejected with HTTP 400; namespace values that
 *     pass the deployed param filter but do not contain the resource return not found.</li>
 *     <li>Exception/error handling: md5 conditional fetch returns HTTP 304; absent prompt and unknown version return
 *     controlled not-found responses instead of HTTP 500.</li>
 * </ul>
 *
 * <p>Draft, force-publish, label, and delete calls to {@code /nacos/v3/admin/ai/prompt} are helper calls only; this
 * class keeps its assertions focused on the runtime client query contract.
 *
 * @author xiweng.yy
 */
public class PromptClientOpenApiITCase extends AiOpenApiBaseITCase {
    
    private static final String PROMPT_CLIENT_PATH = nacosPath(Constants.Prompt.CLIENT_PATH);
    
    private static final String PROMPT_ADMIN_PATH = nacosPath(Constants.Prompt.ADMIN_PATH);
    
    @Test
    public void testQueryPromptByLatestVersionLabelAndMd5() throws Exception {
        String promptKey = randomPromptKey("prompt");
        publishPrompt(promptKey, "1.0.0", "Hello {{name}} from v1");
        addCleanup(() -> deletePrompt(promptKey));
        publishPrompt(promptKey, "2.0.0", "Hello {{name}} from v2");
        updateLabels(promptKey, "{\"stable\":\"1.0.0\"}");
        
        JsonNode latest = getPrompt(Query.newInstance().addParam("promptKey", promptKey));
        JsonNode latestData = latest.get("data");
        assertPrompt(latestData, promptKey, "2.0.0", "Hello {{name}} from v2");
        assertEquals("name", latestData.get("variables").get(0).get("name").asText());
        assertEquals("Nacos", latestData.get("variables").get(0).get("defaultValue").asText());
        assertNotNull(latestData.get("md5").asText());
        
        JsonNode byVersion = getPrompt(Query.newInstance().addParam("promptKey", promptKey)
                .addParam("version", "1.0.0"));
        assertPrompt(byVersion.get("data"), promptKey, "1.0.0", "Hello {{name}} from v1");
        
        JsonNode byLabel = getPrompt(Query.newInstance().addParam("promptKey", promptKey)
                .addParam("label", "stable"));
        assertPrompt(byLabel.get("data"), promptKey, "1.0.0", "Hello {{name}} from v1");
        
        JsonNode versionWins = getPrompt(Query.newInstance().addParam("promptKey", promptKey)
                .addParam("version", "2.0.0").addParam("label", "stable"));
        assertPrompt(versionWins.get("data"), promptKey, "2.0.0", "Hello {{name}} from v2");
        
        HttpResponse notModified = getRaw(PROMPT_CLIENT_PATH,
                Query.newInstance().addParam("promptKey", promptKey)
                        .addParam("md5", latestData.get("md5").asText()));
        assertEquals(304, notModified.code(), notModified.body());
    }
    
    @Test
    public void testQueryPromptMissingPromptKeyReturnsBadRequest() throws Exception {
        assertError(getRaw(PROMPT_CLIENT_PATH + "?namespaceId=" + DEFAULT_NAMESPACE), 400,
                ErrorCode.PARAMETER_MISSING, "promptKey");
    }
    
    @Test
    public void testQueryPromptUnknownNamespaceReturnsNotFoundResultBody() throws Exception {
        Query query = Query.newInstance().addParam("promptKey", "openapi_it_prompt_invalid_ns")
                .addParam("namespaceId", "invalid namespace");
        assertError(getRaw(PROMPT_CLIENT_PATH, query), 404, ErrorCode.RESOURCE_NOT_FOUND, "Prompt not found");
    }
    
    @Test
    public void testQueryPromptUnknownResourceReturnsNotFoundResultBody() throws Exception {
        Query query = Query.newInstance().addParam("promptKey", randomPromptKey("absent"));
        assertError(getRaw(PROMPT_CLIENT_PATH, query), 404, ErrorCode.RESOURCE_NOT_FOUND, "Prompt not found");
    }
    
    @Test
    public void testQueryPromptUnknownVersionReturnsNotFoundAndUnknownLabelFallsBackLatest() throws Exception {
        String promptKey = randomPromptKey("missing");
        publishPrompt(promptKey, "1.0.0", "only version");
        addCleanup(() -> deletePrompt(promptKey));
        
        assertError(getRaw(PROMPT_CLIENT_PATH,
                Query.newInstance().addParam("promptKey", promptKey).addParam("version", "9.9.9")),
                404, ErrorCode.RESOURCE_NOT_FOUND, "Prompt version not online");
        JsonNode unknownLabel = getPrompt(Query.newInstance().addParam("promptKey", promptKey)
                .addParam("label", "missing"));
        assertPrompt(unknownLabel.get("data"), promptKey, "1.0.0", "only version");
    }
    
    private JsonNode getPrompt(Query query) throws Exception {
        JsonNode root = getJsonOk(PROMPT_CLIENT_PATH, query);
        assertNotNull(root.get("data"), root.toString());
        return root;
    }
    
    private void publishPrompt(String promptKey, String version, String template) throws Exception {
        JsonNode draft = postFormOk(PROMPT_ADMIN_PATH + "/draft", buildPromptForm(promptKey, version, template));
        assertEquals(version, draft.get("data").asText(), draft.toString());
        Map<String, String> form = new LinkedHashMap<>();
        form.put("promptKey", promptKey);
        form.put("version", version);
        JsonNode published = postFormOk(PROMPT_ADMIN_PATH + "/force-publish", form);
        assertEquals("ok", published.get("data").asText(), published.toString());
    }
    
    private void updateLabels(String promptKey, String labels) throws Exception {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("promptKey", promptKey);
        form.put("labels", labels);
        JsonNode root = putFormOk(PROMPT_ADMIN_PATH + "/labels", form);
        assertEquals("ok", root.get("data").asText(), root.toString());
    }
    
    private void deletePrompt(String promptKey) throws Exception {
        deleteQuietly(PROMPT_ADMIN_PATH, Query.newInstance().addParam("promptKey", promptKey));
    }
    
    private Map<String, String> buildPromptForm(String promptKey, String version, String template) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("promptKey", promptKey);
        form.put("targetVersion", version);
        form.put("template", template);
        form.put("variables", "[{\"name\":\"name\",\"defaultValue\":\"Nacos\",\"description\":\"target name\"}]");
        form.put("commitMsg", "openapi prompt client it");
        form.put("description", "prompt client openapi integration test");
        form.put("bizTags", "openapi-it");
        return form;
    }
    
    private String randomPromptKey(String scenario) {
        return "oit_" + scenario + "_" + UUID.randomUUID().toString().substring(0, 8);
    }
    
    private void assertPrompt(JsonNode actual, String promptKey, String version, String template) {
        assertNotNull(actual);
        assertEquals(promptKey, actual.get("promptKey").asText());
        assertEquals(version, actual.get("version").asText());
        assertEquals(template, actual.get("template").asText());
    }
}
