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

import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for MCP HTTP query and release.
 *
 * <p>Scenario coverage:
 * <ul>
 *     <li>Expected capability: an omitted or false {@code createDraft} keeps historical
 *     direct-online release, exact/latest queries return the same Version, and true creates a
 *     lifecycle draft after managed cutover.</li>
 *     <li>Boundary/validation: namespace defaulting, optional Tools/Resources/Endpoint content,
 *     strict boolean parsing, required Server name/Version, and name consistency are covered.</li>
 *     <li>Exception/error handling: duplicate release, missing query target, malformed JSON,
 *     and pre-cutover draft release return controlled error envelopes.</li>
 * </ul>
 *
 * @author Nacos
 */
public class McpPublishClientOpenApiITCase extends McpClientOpenApiBaseITCase {

    @Test
    public void testCompatibilityReleaseQueryAndDraftChoice() throws Exception {
        String defaultName = randomAiName("mcp-client-default");
        Map<String, String> defaultRelease = mcpServerForm(defaultName, "1.0.0",
                "default direct-online", "tool_default", "resource_default");
        String defaultId = postFormOk(MCP_CLIENT_PATH, defaultRelease).get("data").asText();
        assertNotNull(defaultId);
        addCleanup(() -> deleteMcpServerQuietly(defaultName, defaultId));
        assertServing(defaultName, "1.0.0", "default direct-online");

        String explicitName = randomAiName("mcp-client-false");
        Map<String, String> explicitRelease = mcpServerForm(explicitName, "1.0.0",
                "explicit direct-online", "tool_false", "resource_false");
        explicitRelease.put("createDraft", "false");
        String explicitId = postFormOk(MCP_CLIENT_PATH, explicitRelease).get("data").asText();
        addCleanup(() -> deleteMcpServerQuietly(explicitName, explicitId));
        assertServing(explicitName, "1.0.0", "explicit direct-online");

        assertError(postFormRaw(MCP_CLIENT_PATH, explicitRelease), 409,
                ErrorCode.MCP_SERVER_VERSION_EXIST, "already exist");

        String draftName = randomAiName("mcp-client-draft");
        Map<String, String> draftRelease = mcpServerForm(draftName, "1.0.0",
                "lifecycle draft", "tool_draft", "resource_draft");
        draftRelease.put("createDraft", "true");
        HttpResponse draftResponse = postFormRaw(MCP_CLIENT_PATH, draftRelease);
        if (409 == draftResponse.code()) {
            assertError(draftResponse, 409, ErrorCode.RESOURCE_CONFLICT,
                    "LIFECYCLE_MANAGED cutover");
        } else {
            assertEquals(200, draftResponse.code(), draftResponse.body());
            JsonNode root = JacksonUtils.toObj(draftResponse.body());
            assertSuccess(root);
            String draftId = root.get("data").asText();
            addCleanup(() -> deleteMcpServerQuietly(draftName, draftId));
            assertError(getMcp(null, Query.newInstance().addParam("mcpName", draftName)), 404,
                    ErrorCode.MCP_SERVER_NOT_FOUND, "not found");
            JsonNode draft = getJsonOk(ADMIN_MCP_PATH + "/version",
                    mcpLifecycleVersionQuery(draftName, "1.0.0")).get("data");
            assertEquals("draft", draft.get("status").asText(), draft.toString());
        }
    }

    @Test
    public void testReleaseAndQueryValidation() throws Exception {
        assertError(getMcp(null, Query.newInstance()), 400, ErrorCode.PARAMETER_MISSING,
                "mcpName");
        assertError(getMcp(null, Query.newInstance().addParam("mcpName",
                randomAiName("missing-mcp"))), 404, ErrorCode.MCP_SERVER_NOT_FOUND,
                "not found");

        String name = randomAiName("mcp-client-invalid");
        Map<String, String> missingServer = new LinkedHashMap<>();
        missingServer.put("mcpName", name);
        assertError(postFormRaw(MCP_CLIENT_PATH, missingServer), 400,
                ErrorCode.PARAMETER_MISSING, "serverSpecification");

        Map<String, String> malformed = mcpServerForm(name, "1.0.0", "malformed",
                "tool_malformed", "resource_malformed");
        malformed.put("toolSpecification", "{");
        assertError(postFormRaw(MCP_CLIENT_PATH, malformed), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "toolSpecification");

        Map<String, String> invalidBoolean = mcpServerForm(name, "1.0.0", "boolean",
                "tool_boolean", "resource_boolean");
        invalidBoolean.put("createDraft", "sometimes");
        assertError(postFormRaw(MCP_CLIENT_PATH, invalidBoolean), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "createDraft");

        Map<String, String> mismatchedName = mcpServerForm(name, "1.0.0", "mismatch",
                "tool_mismatch", "resource_mismatch");
        mismatchedName.put("mcpName", name + "-other");
        assertError(postFormRaw(MCP_CLIENT_PATH, mismatchedName), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "must match");

        Map<String, String> missingVersion = mcpServerForm(name, "1.0.0", "missing version",
                "tool_missing_version", "resource_missing_version");
        Map<String, Object> server = JacksonUtils.toObj(missingVersion.get("serverSpecification"),
                Map.class);
        server.remove("versionDetail");
        missingVersion.put("serverSpecification", JacksonUtils.toJson(server));
        assertError(postFormRaw(MCP_CLIENT_PATH, missingVersion), 400,
                ErrorCode.PARAMETER_MISSING, "versionDetail.version");
    }

    private void assertServing(String mcpName, String version, String description)
            throws Exception {
        JsonNode exact = getJsonOk(MCP_CLIENT_PATH, Query.newInstance()
                .addParam("mcpName", mcpName).addParam("version", version)).get("data");
        assertEquals(mcpName, exact.get("name").asText(), exact.toString());
        assertEquals(version, exact.get("versionDetail").get("version").asText(),
                exact.toString());
        assertEquals(description, exact.get("description").asText(), exact.toString());
        assertTrue(exact.get("toolSpec").get("tools").size() > 0, exact.toString());
        assertTrue(exact.get("resourceSpec").get("resources").size() > 0, exact.toString());

        JsonNode latest = getJsonOk(MCP_CLIENT_PATH,
                Query.newInstance().addParam("mcpName", mcpName)).get("data");
        assertEquals(version, latest.get("versionDetail").get("version").asText(),
                latest.toString());
    }
}
