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

package com.alibaba.nacos.test.consoleapi.ai.mcp;

import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.test.consoleapi.ai.AiConsoleApiBaseITCase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for {@code GET /v3/console/ai/mcp/importToolsFromMcp}.
 *
 * <p>Scenario coverage:
 * <ul>
 *     <li>Expected capability: public MCP targets are allowed by default, while private or local targets are rejected
 *     with a remediation message before a network connection is attempted. Public-target protocol success and
 *     operator-approved private-target success require an external MCP runtime and are not exercised by this
 *     standalone suite.</li>
 *     <li>Boundary/validation: transportType, baseUrl, and endpoint are required; unsupported transports are rejected
 *     before endpoint validation. The operator switch, private CIDR exceptions, and optional authToken forwarding
 *     are covered by focused tests because standalone configuration is fixed before the suite starts.</li>
 *     <li>Exception/error handling: private-target denial and unsupported transports return wrapped, explicit
 *     business errors instead of an unhandled server exception.</li>
 * </ul>
 *
 * @author Nacos
 */
public class McpToolsImportConsoleApiOpenApiITCase extends AiConsoleApiBaseITCase {

    @Test
    public void testImportToolsRejectsPrivateEndpointByDefault() throws Exception {
        HttpResponse response = getRaw(CONSOLE_MCP_IMPORT_TOOLS_PATH, Query.newInstance()
                .addParam("transportType", "mcp-streamable").addParam("baseUrl", "http://127.0.0.1:1")
                .addParam("endpoint", "/mcp"));

        assertEquals(200, response.code(), response.body());
        JsonNode root = JacksonUtils.toObj(response.body());
        assertEquals(ErrorCode.ACCESS_DENIED.getCode(), root.get("code").asInt(), response.body());
        assertTrue(root.get("message").asText().contains(
                "nacos.console.ai.mcp.import.allowed-private-addresses"), response.body());
        assertTrue(root.get("message").asText().contains("private or local"), response.body());
    }

    @Test
    public void testImportToolsRequestValidationAndUnsupportedTransport() throws Exception {
        assertError(getRaw(CONSOLE_MCP_IMPORT_TOOLS_PATH, Query.newInstance()
                .addParam("transportType", "mcp-streamable").addParam("baseUrl", "http://127.0.0.1:1")), 400,
                ErrorCode.PARAMETER_MISSING, "endpoint");

        HttpResponse unsupportedTransport = getRaw(CONSOLE_MCP_IMPORT_TOOLS_PATH, Query.newInstance()
                .addParam("transportType", "stdio").addParam("baseUrl", "http://127.0.0.1:1")
                .addParam("endpoint", "/mcp"));
        assertEquals(200, unsupportedTransport.code(), unsupportedTransport.body());
        JsonNode root = JacksonUtils.toObj(unsupportedTransport.body());
        assertEquals(ErrorCode.SERVER_ERROR.getCode(), root.get("code").asInt(), unsupportedTransport.body());
        assertTrue(root.get("message").asText().contains("Unsupported transport type"),
                unsupportedTransport.body());
    }
}
