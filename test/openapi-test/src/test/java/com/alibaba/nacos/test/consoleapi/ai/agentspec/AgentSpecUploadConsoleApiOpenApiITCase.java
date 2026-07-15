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
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.test.consoleapi.ai.AiConsoleApiBaseITCase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for AgentSpec console upload OpenAPI {@code /v3/console/ai/agentspecs/upload}.
 *
 * <p>Scenario coverage:
 * <ul>
 *     <li>Expected capability: single ZIP upload creates a draft from {@code manifest.json} and resources, overwrite
 *     updates an existing editing draft, later uploads create the next draft version, and seed archives import
 *     multiple AgentSpecs.</li>
 *     <li>Boundary/validation: namespace defaults to public; upload-created versions use the service-managed
 *     {@code 0.0.x} sequence; duplicate working drafts require overwrite; seed archive upload returns the imported
 *     names summary.</li>
 *     <li>Exception/error handling: empty files, malformed ZIP files, and archives without {@code manifest.json}
 *     return controlled HTTP 400 Result bodies instead of HTTP 500.</li>
 * </ul>
 *
 * @author xiweng.yy
 */
public class AgentSpecUploadConsoleApiOpenApiITCase extends AiConsoleApiBaseITCase {

    @Test
    public void testSingleAgentSpecUploadOverwriteAndVersionBump() throws Exception {
        String agentSpecName = randomAiName("agentspec-upload");
        Query uploadQuery = uploadQuery(false);
        HttpResponse uploaded = postMultipartRaw(CONSOLE_AGENT_SPEC_PATH + "/upload", uploadQuery,
                "file", agentSpecName + ".zip", "application/zip",
                buildAgentSpecZip(agentSpecName, "0.0.1", "Uploaded AgentSpec v1",
                        "upload-v1", "uploaded soul"));
        assertUploadSuccess(uploaded, agentSpecName);
        addCleanup(() -> deleteAgentSpecQuietly(agentSpecName));
        assertAgentSpecContent(getJsonOk(CONSOLE_AGENT_SPEC_VERSION_PATH,
                agentSpecVersionQuery(agentSpecName, "0.0.1")).get("data"), agentSpecName,
                "0.0.1", "Uploaded AgentSpec v1", "upload-v1", "uploaded soul");

        assertError(postMultipartRaw(CONSOLE_AGENT_SPEC_PATH + "/upload", uploadQuery,
                "file", agentSpecName + ".zip", "application/zip",
                buildAgentSpecZip(agentSpecName, "0.0.1", "Duplicate AgentSpec",
                        "duplicate", "duplicate soul")), 409,
                ErrorCode.RESOURCE_CONFLICT, "working version");
        HttpResponse overwritten = postMultipartRaw(CONSOLE_AGENT_SPEC_PATH + "/upload",
                uploadQuery(true), "file", agentSpecName + ".zip", "application/zip",
                buildAgentSpecZip(agentSpecName, "0.0.1", "Overwritten AgentSpec",
                        "overwrite", "overwritten soul"));
        assertUploadSuccess(overwritten, agentSpecName);
        assertAgentSpecContent(getJsonOk(CONSOLE_AGENT_SPEC_VERSION_PATH,
                agentSpecVersionQuery(agentSpecName, "0.0.1")).get("data"), agentSpecName,
                "0.0.1", "Overwritten AgentSpec", "overwrite", "overwritten soul");

        postFormOk(CONSOLE_AGENT_SPEC_PATH + "/force-publish",
                agentSpecPublishForm(agentSpecName, "0.0.1"));
        HttpResponse nextUpload = postMultipartRaw(CONSOLE_AGENT_SPEC_PATH + "/upload",
                uploadQuery(false), "file", agentSpecName + ".zip", "application/zip",
                buildAgentSpecZip(agentSpecName, "0.0.2", "Uploaded AgentSpec v2",
                        "upload-v2", "uploaded soul v2"));
        assertUploadSuccess(nextUpload, agentSpecName);
        JsonNode meta = getJsonOk(CONSOLE_AGENT_SPEC_PATH, agentSpecQuery(agentSpecName)).get("data");
        assertEquals("0.0.2", meta.get("editingVersion").asText(), meta.toString());
        assertAgentSpecContent(getJsonOk(CONSOLE_AGENT_SPEC_VERSION_PATH,
                agentSpecVersionQuery(agentSpecName, "0.0.2")).get("data"), agentSpecName,
                "0.0.2", "Uploaded AgentSpec v2", "upload-v2", "uploaded soul v2");
    }

    @Test
    public void testSeedArchiveUploadImportsMultipleAgentSpecs() throws Exception {
        String firstName = randomAiName("seed-a");
        String secondName = randomAiName("seed-b");
        Map<String, String> specs = new LinkedHashMap<>();
        specs.put(firstName, "seed-scenario-a");
        specs.put(secondName, "seed-scenario-b");
        HttpResponse uploaded = postMultipartRaw(CONSOLE_AGENT_SPEC_PATH + "/upload", uploadQuery(false),
                "file", "agentspec-seed.zip", "application/zip", buildAgentSpecSeedArchive(specs));
        JsonNode root = assertUploadResult(uploaded);
        String summary = root.get("data").asText();
        assertTrue(summary.contains("Imported 2 agentspecs"), summary);
        assertTrue(summary.contains(firstName), summary);
        assertTrue(summary.contains(secondName), summary);
        addCleanup(() -> deleteAgentSpecQuietly(firstName));
        addCleanup(() -> deleteAgentSpecQuietly(secondName));

        assertAgentSpecContent(getJsonOk(CONSOLE_AGENT_SPEC_VERSION_PATH,
                agentSpecVersionQuery(firstName, "0.0.1")).get("data"), firstName, "0.0.1",
                "uploaded seed " + firstName, "seed-scenario-a", "seed soul for " + firstName);
        assertAgentSpecContent(getJsonOk(CONSOLE_AGENT_SPEC_VERSION_PATH,
                agentSpecVersionQuery(secondName, "0.0.1")).get("data"), secondName, "0.0.1",
                "uploaded seed " + secondName, "seed-scenario-b", "seed soul for " + secondName);
    }

    @Test
    public void testAgentSpecUploadValidationErrors() throws Exception {
        assertError(postMultipartRaw(CONSOLE_AGENT_SPEC_PATH + "/upload", uploadQuery(false),
                "file", "empty.zip", "application/zip", new byte[0]), 400,
                ErrorCode.DATA_EMPTY, "File is required");
        assertError(postMultipartRaw(CONSOLE_AGENT_SPEC_PATH + "/upload", uploadQuery(false),
                "file", "plain.zip", "application/zip", "not a zip".getBytes()), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "Failed to read agentspec zip archive");
        assertError(postMultipartRaw(CONSOLE_AGENT_SPEC_PATH + "/upload", uploadQuery(false),
                "file", "no-manifest.zip", "application/zip", buildZipWithoutAgentSpecManifest()),
                400, ErrorCode.PARAMETER_VALIDATE_ERROR, "manifest.json file not found");
    }

    private Query uploadQuery(boolean overwrite) {
        return Query.newInstance().addParam("namespaceId", DEFAULT_NAMESPACE)
                .addParam("overwrite", String.valueOf(overwrite));
    }

    private void assertUploadSuccess(HttpResponse response, String agentSpecName) {
        JsonNode root = assertUploadResult(response);
        assertEquals(agentSpecName, root.get("data").asText(), root.toString());
    }

    private JsonNode assertUploadResult(HttpResponse response) {
        assertEquals(200, response.code(), response.body());
        JsonNode root = JacksonUtils.toObj(response.body());
        assertSuccess(root);
        return root;
    }
}
