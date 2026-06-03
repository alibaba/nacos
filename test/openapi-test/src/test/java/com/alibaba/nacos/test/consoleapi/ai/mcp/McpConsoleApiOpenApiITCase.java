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

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for MCP console OpenAPI {@code /v3/console/ai/mcp}.
 *
 * <p>Scenario coverage:
 * <ul>
 *     <li>Expected capability: create persists an MCP server with stdio server specification, tools, and a generated
 *     ID; detail can be queried by ID and by name; update adds a new latest version; list supports accurate and blur
 *     name filters; delete removes the server.</li>
 *     <li>Boundary/validation: omitted namespaceId defaults to public; detail/delete accept either mcpId or mcpName;
 *     list defaults search to accurate; invalid search, missing identity, missing serverSpecification, missing
 *     version, invalid custom ID, and import request field omissions are rejected with HTTP 400. Console currently
 *     accepts {@code resourceSpecification} but does not persist it because the controller does not parse resources.
 *     </li>
 *     <li>Exception/error handling: duplicate create returns conflict, absent server returns the MCP not-found result
 *     envelope, malformed JSON is rejected as a controlled validation error, and unsupported MCP tool import
 *     transports return a wrapped failure instead of an unhandled exception.</li>
 * </ul>
 *
 * @author xiweng.yy
 */
public class McpConsoleApiOpenApiITCase extends AiConsoleApiBaseITCase {
    
    @Test
    public void testCreateUpdateListGetAndDeleteMcpServer() throws Exception {
        String mcpName = randomAiName("mcp");
        String toolName = "tool_" + mcpName.replace('-', '_');
        String resourceName = "resource_" + mcpName.replace('-', '_');
        JsonNode created = postFormOk(CONSOLE_MCP_PATH,
                mcpServerForm(mcpName, "1.0.0", "initial MCP server", toolName, resourceName));
        String mcpId = created.get("data").asText();
        addCleanup(() -> deleteMcpServerQuietly(mcpName, mcpId));
        
        JsonNode detailById = getJsonOk(CONSOLE_MCP_PATH, mcpIdentityQuery(null, mcpId, null)).get("data");
        assertMcpDetail(detailById, mcpName, "1.0.0", "initial MCP server", toolName, resourceName);
        assertEquals(mcpId, detailById.get("id").asText(), detailById.toString());
        
        JsonNode detailByName = getJsonOk(CONSOLE_MCP_PATH, mcpIdentityQuery(mcpName, null, "1.0.0"))
                .get("data");
        assertMcpDetail(detailByName, mcpName, "1.0.0", "initial MCP server", toolName, resourceName);
        
        Map<String, String> updateForm = mcpServerForm(mcpName, "1.1.0", "updated MCP server",
                toolName + "_v2", resourceName + "_v2");
        JsonNode updated = putFormOk(CONSOLE_MCP_PATH, updateForm);
        assertEquals("ok", updated.get("data").asText(), updated.toString());
        
        JsonNode latest = getJsonOk(CONSOLE_MCP_PATH, mcpIdentityQuery(mcpName, null, null)).get("data");
        assertMcpDetail(latest, mcpName, "1.1.0", "updated MCP server", toolName + "_v2",
                resourceName + "_v2");
        assertEquals(2, latest.get("allVersions").size(), latest.toString());
        
        JsonNode accurateList = getJsonOk(CONSOLE_MCP_LIST_PATH, Query.newInstance()
                .addParam("namespaceId", DEFAULT_NAMESPACE).addParam("mcpName", mcpName)
                .addParam("pageNo", "1").addParam("pageSize", "10")).get("data");
        assertPageContains(accurateList, "name", mcpName);
        
        JsonNode blurList = getJsonOk(CONSOLE_MCP_LIST_PATH, Query.newInstance()
                .addParam("namespaceId", DEFAULT_NAMESPACE).addParam("mcpName", mcpName.substring(0, 8))
                .addParam("search", "blur").addParam("pageNo", "1").addParam("pageSize", "10")).get("data");
        assertPageContains(blurList, "name", mcpName);
        
        deleteJsonOk(CONSOLE_MCP_PATH, mcpIdentityQuery(null, mcpId, null));
        assertMcpServerNotFoundEventually(mcpId);
    }
    
    @Test
    public void testCreateMcpServerValidationAndConflictErrors() throws Exception {
        String mcpName = randomAiName("mcp-validation");
        assertError(postRaw(CONSOLE_MCP_PATH, Query.newInstance().addParam("mcpName", mcpName)), 400,
                ErrorCode.PARAMETER_MISSING, "serverSpecification");
        
        assertError(postRaw(CONSOLE_MCP_PATH, Query.newInstance().addParam("mcpName", mcpName)
                .addParam("serverSpecification", "{")), 400, ErrorCode.PARAMETER_VALIDATE_ERROR,
                "Can't be parsed");
        
        Map<String, String> missingVersion = mcpServerForm(mcpName, "1.0.0", "missing version",
                "tool_missing", "resource_missing");
        missingVersion.put("serverSpecification", "{\"name\":\"" + mcpName + "\",\"protocol\":\"stdio\"}");
        assertError(postRaw(CONSOLE_MCP_PATH, queryFrom(missingVersion)), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "Version must be specified");
        
        Map<String, String> invalidId = mcpServerForm(mcpName, "1.0.0", "invalid id",
                "tool_invalid_id", "resource_invalid_id");
        invalidId.put("serverSpecification", "{\"id\":\"not-a-uuid\",\"name\":\"" + mcpName
                + "\",\"protocol\":\"stdio\",\"version\":\"1.0.0\"}");
        assertError(postRaw(CONSOLE_MCP_PATH, queryFrom(invalidId)), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "uuid pattern");
        
        JsonNode created = postFormOk(CONSOLE_MCP_PATH,
                mcpServerForm(mcpName, "1.0.0", "conflict source", "tool_conflict", "resource_conflict"));
        String mcpId = created.get("data").asText();
        addCleanup(() -> deleteMcpServerQuietly(mcpName, mcpId));
        assertError(postRaw(CONSOLE_MCP_PATH, queryFrom(mcpServerForm(mcpName, "1.0.1",
                "conflict duplicate", "tool_conflict_2", "resource_conflict_2"))), 409,
                ErrorCode.RESOURCE_CONFLICT, "has existed");
    }
    
    @Test
    public void testGetListAndDeleteMcpServerValidationErrors() throws Exception {
        assertError(getRaw(CONSOLE_MCP_PATH, Query.newInstance().addParam("namespaceId", DEFAULT_NAMESPACE)), 400,
                ErrorCode.PARAMETER_MISSING, "mcpId");
        assertError(deleteRaw(CONSOLE_MCP_PATH, Query.newInstance().addParam("namespaceId", DEFAULT_NAMESPACE)),
                400, ErrorCode.PARAMETER_MISSING, "mcpId");
        assertError(getRaw(CONSOLE_MCP_LIST_PATH, Query.newInstance().addParam("search", "invalid")
                .addParam("pageNo", "1").addParam("pageSize", "10")), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "search");
        assertError(getRaw(CONSOLE_MCP_LIST_PATH, Query.newInstance().addParam("search", "accurate")
                .addParam("pageNo", "0").addParam("pageSize", "10")), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "pageNo");
        
        JsonNode emptyPage = getJsonOk(CONSOLE_MCP_LIST_PATH, Query.newInstance()
                .addParam("namespaceId", DEFAULT_NAMESPACE).addParam("mcpName", randomAiName("absent-mcp"))
                .addParam("pageNo", "1").addParam("pageSize", "10")).get("data");
        assertEmptyPageShape(emptyPage);
        assertFalse(emptyPage.get("pageItems").elements().hasNext(), emptyPage.toString());
    }

    @Test
    public void testImportToolsAndImportRequestValidationErrors() throws Exception {
        HttpResponse unsupportedTransport = getRaw(CONSOLE_MCP_IMPORT_TOOLS_PATH, Query.newInstance()
                .addParam("transportType", "stdio").addParam("baseUrl", "http://127.0.0.1:1")
                .addParam("endpoint", "/mcp"));
        assertEquals(200, unsupportedTransport.code(), unsupportedTransport.body());
        JsonNode root = JacksonUtils.toObj(unsupportedTransport.body());
        assertEquals(ErrorCode.SERVER_ERROR.getCode(), root.get("code").asInt(),
                unsupportedTransport.body());
        assertTrue(root.get("message").asText().contains("Unsupported transport type"),
                unsupportedTransport.body());

        assertError(postRaw(CONSOLE_MCP_IMPORT_VALIDATE_PATH, Query.newInstance()
                .addParam("namespaceId", DEFAULT_NAMESPACE).addParam("data", "{}")), 400,
                ErrorCode.PARAMETER_MISSING, "importType");
        assertError(postRaw(CONSOLE_MCP_IMPORT_VALIDATE_PATH, Query.newInstance()
                .addParam("namespaceId", DEFAULT_NAMESPACE).addParam("importType", "json")), 400,
                ErrorCode.PARAMETER_MISSING, "data");
        assertError(postRaw(CONSOLE_MCP_IMPORT_VALIDATE_PATH, Query.newInstance()
                .addParam("namespaceId", DEFAULT_NAMESPACE).addParam("importType", "invalid")
                .addParam("data", "{}")), 400, ErrorCode.PARAMETER_VALIDATE_ERROR,
                "json, url, file");

        assertError(postRaw(CONSOLE_MCP_IMPORT_EXECUTE_PATH, Query.newInstance()
                .addParam("namespaceId", DEFAULT_NAMESPACE).addParam("data", "{}")
                .addParam("skipInvalid", "true")), 400, ErrorCode.PARAMETER_MISSING,
                "importType");
        assertError(postRaw(CONSOLE_MCP_IMPORT_EXECUTE_PATH, Query.newInstance()
                .addParam("namespaceId", DEFAULT_NAMESPACE).addParam("importType", "invalid")
                .addParam("data", "{}").addParam("overrideExisting", "true")), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "json, url, file");
    }
    
    private void assertMcpServerNotFoundEventually(String mcpId) throws Exception {
        HttpResponse lastResponse = null;
        for (int i = 0; i < 10; i++) {
            lastResponse = getRaw(CONSOLE_MCP_PATH, mcpIdentityQuery(null, mcpId, null));
            if (404 == lastResponse.code()) {
                assertError(lastResponse, 404, ErrorCode.MCP_SERVER_NOT_FOUND, "not found");
                return;
            }
            Thread.sleep(200L);
        }
        assertError(lastResponse, 404, ErrorCode.MCP_SERVER_NOT_FOUND, "not found");
    }
}
