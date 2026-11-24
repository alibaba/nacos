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

package com.alibaba.nacos.test.consoleapi.ai.copilot;

import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.test.consoleapi.ai.AiConsoleApiBaseITCase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for Copilot console OpenAPI {@code /v3/console/copilot}.
 *
 * <p>Scenario coverage:
 * <ul>
 *     <li>Expected capability: patch-only saves preserve omitted fields and explicit null clears;
 *     Automatic persists a clear declaration. Sequential saves and masked-key round trips preserve
 *     previous updates. Reads expose masked credentials and sanitized connection metadata.</li>
 *     <li>Boundary/validation: SSE skill/prompt endpoints accept empty bodies as controlled error
 *     events and validate required fields before invoking an LLM provider. Non-editable config
 *     fields are accepted but ignored; invalid selection rejects the whole patch.</li>
 *     <li>Exception/error handling: malformed config bodies return HTTP 400. Copilot SSE validation
 *     failures complete with an {@code error} event rather than HTTP 500 or a hanging stream.</li>
 * </ul>
 *
 * <p>Config cases share the documented coordinates and restore the previous document through the
 * generic Config API. Run on an isolated standalone server without concurrent config writers.
 * No LLM generation is invoked. Deployment precedence, remote CAS races, and injected failures
 * need controlled environments (see CONSOLE_API_TEST_SCENARIOS.md).
 *
 * @author xiweng.yy
 */
public class CopilotConsoleApiOpenApiITCase extends AiConsoleApiBaseITCase {

    private static final String CONFIG_PATH = "/v3/console/cs/config";

    private static final String COPILOT_CONFIG_DATA_ID = "copilot-config.json";

    private static final String COPILOT_CONFIG_GROUP = "nacos-copilot";

    private static final String SAVED_API_KEY = "openapi-it-key";

    @Test
    public void testCopilotConfigSaveAndGetSuccess() throws Exception {
        prepareStoredConfig("{\"enabled\":false,\"studioUrl\":null}");

        String configJson = "{\"apiKey\":\"" + SAVED_API_KEY + "\",\"model\":\"openapi-it-model\","
                + "\"studioUrl\":\"\","
                + "\"studioProject\":\"openapi-it-project\",\"enabled\":true}";
        JsonNode saved = postJsonOk(CONSOLE_COPILOT_CONFIG_PATH, Query.EMPTY, configJson);
        assertTrue(saved.get("data").asBoolean(), saved.toString());

        JsonNode data = getJsonOk(CONSOLE_COPILOT_CONFIG_PATH, Query.EMPTY).get("data");
        assertEquals("op******ey", data.get("apiKey").asText(), data.toString());
        assertEquals("openapi-it-model", data.get("model").asText(), data.toString());
        assertEquals("", data.get("studioUrl").asText(), data.toString());
        assertFalse(readStoredConfig().get("enabled").asBoolean(),
                "Non-editable enabled field must keep its stored value");
        assertFalse(data.toString().contains(SAVED_API_KEY),
                "Copilot response must not expose the stored credential");
        assertEquals("openapi-it-project", data.get("studioProject").asText(), data.toString());
        assertTrue(data.get("connections").isArray(), data.toString());
        assertFalse(data.get("connections").isEmpty(), data.toString());
        String selectionSource = data.get("selectedConnectionSource").asText();
        assertTrue("DEPLOYMENT".equals(selectionSource) || "STORED".equals(selectionSource)
            || "NONE".equals(selectionSource), data.toString());
        for (JsonNode connection : data.get("connections")) {
            assertTrue(connection.has("name") && connection.has("providerId")
                    && connection.has("source"), "Connection identity and source are required");
            assertTrue(connection.get("enabled").isBoolean(), data.toString());
            assertTrue(connection.get("available").isBoolean(), data.toString());
            assertTrue(connection.get("apiKeyConfigured").isBoolean(), data.toString());
            assertFalse(connection.has("apiKey"), "Connection metadata must not include apiKey");
            assertFalse(connection.has("options"), "Provider options are not console metadata");
            if (!connection.get("available").asBoolean()) {
                assertFalse(connection.path("unavailableReason").asText("").isBlank(),
                        "Unavailable connections must include a diagnostic reason");
            }
            if (connection.get("apiKeyConfigured").asBoolean()) {
                String actualSource = connection.get("actualSource").asText();
                assertTrue("DEPLOYMENT".equals(actualSource) || "CONSOLE".equals(actualSource)
                        || "ENVIRONMENT".equals(actualSource), data.toString());
            }
        }
    }

    @Test
    public void testCopilotConfigPatchDoesNotBackfillAbsentFields() throws Exception {
        prepareStoredConfig("{\"enabled\":false,\"studioUrl\":null}");

        saveCopilotConfig("{\"studioProject\":\"patch-only-project\","
                + "\"defaultNamespace\":\"ignored\"}");

        JsonNode stored = readStoredConfig();
        assertEquals(3, stored.size(), stored.toString());
        assertEquals("patch-only-project", stored.get("studioProject").asText(), stored.toString());
        assertTrue(stored.get("studioUrl").isNull(), stored.toString());
        assertFalse(stored.get("enabled").asBoolean(), stored.toString());
        for (String absent : new String[] {"apiKey", "model", "selectedConnection",
                "defaultNamespace"}) {
            assertFalse(stored.has(absent), "Save must not backfill " + absent);
        }
    }

    @Test
    public void testCopilotConfigNullClearsSurviveUnrelatedSave() throws Exception {
        prepareStoredConfig("{\"enabled\":false,\"apiKey\":null,\"model\":null,"
                + "\"studioUrl\":null,\"studioProject\":null,\"selectedConnection\":null}");

        saveCopilotConfig("{\"studioProject\":\"unrelated-update\"}");

        JsonNode stored = readStoredConfig();
        for (String cleared : new String[] {"apiKey", "model", "studioUrl", "selectedConnection"}) {
            assertTrue(stored.has(cleared), "Clear declaration must remain present: " + cleared);
            assertTrue(stored.get(cleared).isNull(), "Clear must remain JSON null: " + cleared);
        }
        assertEquals("unrelated-update", stored.get("studioProject").asText(), stored.toString());
    }

    @Test
    public void testCopilotConfigAutomaticPersistsClearDeclaration() throws Exception {
        prepareStoredConfig("{\"enabled\":false,\"studioUrl\":null,"
                + "\"selectedConnection\":\"missing-old-connection\"}");

        saveCopilotConfig("{\"selectedConnection\":\"  \"}");
        JsonNode stored = readStoredConfig();
        assertTrue(stored.has("selectedConnection"), stored.toString());
        assertTrue(stored.get("selectedConnection").isNull(), stored.toString());

        saveCopilotConfig("{\"studioProject\":\"after-automatic\"}");
        stored = readStoredConfig();
        assertTrue(stored.has("selectedConnection"), stored.toString());
        assertTrue(stored.get("selectedConnection").isNull(), stored.toString());
        JsonNode data = getJsonOk(CONSOLE_COPILOT_CONFIG_PATH, Query.EMPTY).get("data");
        assertTrue(data.path("selectedConnection").isNull(), data.toString());
    }

    @Test
    public void testCopilotConfigSequentialUpdatesPreserveStoredFields() throws Exception {
        prepareStoredConfig("{\"enabled\":false,\"studioUrl\":null}");

        saveCopilotConfig("{\"apiKey\":\"" + SAVED_API_KEY + "\",\"model\":\"first-model\"}");
        saveCopilotConfig("{\"apiKey\":\"op******ey\",\"studioProject\":\"second-project\"}");
        JsonNode stored = readStoredConfig();
        assertEquals(SAVED_API_KEY, stored.get("apiKey").asText(), "Masked save must preserve key");
        assertEquals("first-model", stored.get("model").asText(),
                "Unrelated save must preserve model");
        assertEquals("second-project", stored.get("studioProject").asText(),
                "Project must be saved");

        saveCopilotConfig("{\"apiKey\":\"\"}");
        saveCopilotConfig("{\"model\":\"third-model\"}");
        stored = readStoredConfig();
        assertEquals("", stored.get("apiKey").asText(), stored.toString());
        assertTrue(stored.get("apiKeyCleared").asBoolean(), stored.toString());
        assertEquals("third-model", stored.get("model").asText(), stored.toString());
        assertEquals("second-project", stored.get("studioProject").asText(), stored.toString());
        assertTrue(stored.get("studioUrl").isNull(), stored.toString());
    }

    @Test
    public void testCopilotConfigInvalidSelectionDoesNotPersist() throws Exception {
        prepareStoredConfig("{\"enabled\":false,\"studioUrl\":null}");
        JsonNode before = readStoredConfig();

        assertError(postJsonRaw(CONSOLE_COPILOT_CONFIG_PATH, Query.EMPTY,
                "{\"selectedConnection\":\"missing-it-" + System.nanoTime() + "\","
                        + "\"studioProject\":\"must-not-persist\"}"),
                400, ErrorCode.PARAMETER_VALIDATE_ERROR, "does not exist");

        assertEquals(before, readStoredConfig(),
                "Validation failure must not persist any patch field");
    }

    @Test
    public void testCopilotConfigInvalidStoredDocumentRejectsReadAndSave() throws Exception {
        String invalid = "{\"apiKey\":\"probe-secret-key\"} trailing-not-json";
        prepareStoredConfig(invalid);

        HttpResponse read = getRaw(CONSOLE_COPILOT_CONFIG_PATH, Query.EMPTY);
        assertError(read, 503, ErrorCode.SERVER_ERROR, "Unable to read stored Copilot configuration");
        assertFalse(read.body().contains("probe-secret-key"), read.body());
        HttpResponse save = postJsonRaw(CONSOLE_COPILOT_CONFIG_PATH, Query.EMPTY,
                "{\"studioProject\":\"must-not-persist\"}");
        assertError(save, 503, ErrorCode.SERVER_ERROR, "Unable to read stored Copilot configuration");
        assertFalse(save.body().contains("probe-secret-key"), save.body());
        assertEquals(invalid, getJsonOk(CONFIG_PATH, configQuery()).get("data").get("content").asText());
    }

    @Test
    public void testCopilotConfigMalformedJsonReturnsBadRequest() throws Exception {
        assertError(postJsonRaw(CONSOLE_COPILOT_CONFIG_PATH, Query.EMPTY, "{"), 400,
                ErrorCode.PARAMETER_MISSING, "JSON parse error");
    }

    @Test
    public void testCopilotSseValidationErrorEvents() throws Exception {
        assertSseErrorEvent(postRaw(CONSOLE_COPILOT_PATH + "/skill/optimize", Query.EMPTY),
                "\"done\":true");
        assertSseErrorEvent(postJsonRaw(CONSOLE_COPILOT_PATH + "/skill/optimize", Query.EMPTY,
                "{}"), "Skill is required");
        assertSseErrorEvent(postRaw(CONSOLE_COPILOT_PATH + "/skill/generate", Query.EMPTY),
                "\"done\":true");
        assertSseErrorEvent(postJsonRaw(CONSOLE_COPILOT_PATH + "/skill/generate", Query.EMPTY,
                "{}"), "Background information is required");
        assertSseErrorEvent(postRaw(CONSOLE_COPILOT_PATH + "/prompt/optimize", Query.EMPTY),
                "\"done\":true");
        assertSseErrorEvent(postJsonRaw(CONSOLE_COPILOT_PATH + "/prompt/optimize", Query.EMPTY,
                "{}"), "Prompt is required");
        assertSseErrorEvent(postRaw(CONSOLE_COPILOT_PATH + "/prompt/debug", Query.EMPTY),
                "\"done\":true");
        assertSseErrorEvent(postJsonRaw(CONSOLE_COPILOT_PATH + "/prompt/debug", Query.EMPTY,
                "{\"prompt\":\"debug prompt\"}"), "\"done\":true");
    }

    private void prepareStoredConfig(String content) throws Exception {
        JsonNode previous = getJsonOk(CONFIG_PATH, configQuery()).path("data");
        String original = previous.isNull() || previous.isMissingNode()
                ? null : previous.get("content").asText();
        addCleanup(() -> {
            publishStoredConfig(original == null ? "{}" : original);
            saveCopilotConfig("{}");
            if (original == null) {
                deleteQuietly(CONFIG_PATH, configQuery().addParam("tag", ""));
            }
        });
        publishStoredConfig(content);
    }

    private Query configQuery() {
        return Query.newInstance().addParam("dataId", COPILOT_CONFIG_DATA_ID)
                .addParam("groupName", COPILOT_CONFIG_GROUP)
                .addParam("namespaceId", DEFAULT_NAMESPACE);
    }

    private void publishStoredConfig(String content) throws Exception {
        JsonNode result = postFormOk(CONFIG_PATH, Map.of("dataId", COPILOT_CONFIG_DATA_ID,
                "groupName", COPILOT_CONFIG_GROUP, "namespaceId", DEFAULT_NAMESPACE,
                "content", content, "type", "json", "tag", ""));
        assertTrue(result.get("data").asBoolean(), "Config fixture must be persisted");
    }

    private JsonNode readStoredConfig() throws Exception {
        String content = getJsonOk(CONFIG_PATH, configQuery()).get("data").get("content").asText();
        return JacksonUtils.toObj(content, JsonNode.class);
    }

    private void saveCopilotConfig(String patch) throws Exception {
        JsonNode result = postJsonOk(CONSOLE_COPILOT_CONFIG_PATH, Query.EMPTY, patch);
        assertTrue(result.get("data").asBoolean(), "Copilot patch must be persisted and activated");
    }

    private void assertSseErrorEvent(HttpResponse response, String expectedFragment) {
        assertEquals(200, response.code(), response.body());
        assertTrue(response.body().contains("event:error"), response.body());
        assertTrue(response.body().contains(expectedFragment), response.body());
    }
}
