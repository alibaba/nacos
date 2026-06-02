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
import com.alibaba.nacos.test.consoleapi.ai.AiConsoleApiBaseITCase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for Copilot console OpenAPI {@code /v3/console/copilot}.
 *
 * <p>Scenario coverage:
 * <ul>
 *     <li>Expected capability: console config save persists the visible API key, model, studio URL, and studio
 *     project fields; a subsequent read returns the configuration shape used by the controller.</li>
 *     <li>Boundary/validation: SSE skill/prompt generation, optimization, and debug endpoints accept empty request
 *     bodies as a controlled error event and validate required JSON fields before invoking any LLM provider; config
 *     fields outside the four editable fields are accepted but ignored by the save path.</li>
 *     <li>Exception/error handling: malformed config bodies return HTTP 400, and Copilot SSE validation failures
 *     complete with an {@code error} event instead of surfacing an HTTP 500 or hanging stream.</li>
 * </ul>
 *
 * @author xiweng.yy
 */
public class CopilotConsoleApiOpenApiITCase extends AiConsoleApiBaseITCase {

    private static final String CONFIG_PATH = "/v3/console/cs/config";

    private static final String COPILOT_CONFIG_DATA_ID = "copilot-config.json";

    private static final String COPILOT_CONFIG_GROUP = "nacos-copilot";

    @Test
    public void testCopilotConfigSaveAndGetSuccess() throws Exception {
        addCleanup(() -> deleteQuietly(CONFIG_PATH, Query.newInstance()
                .addParam("dataId", COPILOT_CONFIG_DATA_ID)
                .addParam("groupName", COPILOT_CONFIG_GROUP)
                .addParam("namespaceId", DEFAULT_NAMESPACE)
                .addParam("tag", "")));

        String configJson = "{\"apiKey\":\"openapi-it-key\",\"model\":\"openapi-it-model\","
                + "\"studioUrl\":\"http://127.0.0.1/studio\","
                + "\"studioProject\":\"openapi-it-project\",\"enabled\":false}";
        JsonNode saved = postJsonOk(CONSOLE_COPILOT_CONFIG_PATH, Query.EMPTY, configJson);
        assertTrue(saved.get("data").asBoolean(), saved.toString());

        JsonNode data = getJsonOk(CONSOLE_COPILOT_CONFIG_PATH, Query.EMPTY).get("data");
        assertEquals("openapi-it-key", data.get("apiKey").asText(), data.toString());
        assertEquals("openapi-it-model", data.get("model").asText(), data.toString());
        assertEquals("http://127.0.0.1/studio", data.get("studioUrl").asText(), data.toString());
        assertEquals("openapi-it-project", data.get("studioProject").asText(), data.toString());
        assertTrue(data.get("enabled").asBoolean(), data.toString());
        assertEquals(DEFAULT_NAMESPACE, data.get("defaultNamespace").asText(), data.toString());
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

    private void assertSseErrorEvent(HttpResponse response, String expectedFragment) {
        assertEquals(200, response.code(), response.body());
        assertTrue(response.body().contains("event:error"), response.body());
        assertTrue(response.body().contains(expectedFragment), response.body());
    }
}
