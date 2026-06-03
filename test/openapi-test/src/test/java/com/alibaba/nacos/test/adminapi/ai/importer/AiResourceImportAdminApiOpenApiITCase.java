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

package com.alibaba.nacos.test.adminapi.ai.importer;

import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.test.adminapi.ai.AiAdminApiBaseITCase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for AI resource import admin OpenAPI {@code /nacos/v3/admin/ai/import}.
 *
 * <p>Scenario coverage:
 * <ul>
 *     <li>Expected capability: configured import sources are listed with sanitized source information and can be
 *     filtered by {@code mcp}, {@code skill}, or an unsupported resource type without exposing runtime endpoint or
 *     secret fields.</li>
 *     <li>Boundary/validation: {@code resourceType}, {@code sourceId}, {@code selectedItems}, JSON
 *     {@code selectedItems}, JSON {@code options}, and empty selected item lists are validated for search,
 *     validate, and execute. {@code query}, {@code cursor}, {@code limit}, {@code overwriteExisting},
 *     {@code skipInvalid}, and {@code validationToken} are accepted form fields; the IT avoids successful external
 *     network search/import and verifies their local form boundary through controlled source errors.</li>
 *     <li>Exception/error handling: unknown sources return RESOURCE_NOT_FOUND, unsupported source/resourceType
 *     combinations return HTTP 400, and all malformed form requests return standard v3 Result error bodies instead
 *     of HTTP 500.</li>
 * </ul>
 *
 * @author xiweng.yy
 */
public class AiResourceImportAdminApiOpenApiITCase extends AiAdminApiBaseITCase {

    private static final String SOURCE_MCP_OFFICIAL = "mcp-official";

    private static final String SOURCE_SKILLS_SH = "skills-sh";

    private static final String UNKNOWN_SOURCE = "openapi-it-unknown-source";

    @Test
    public void testListSourcesFiltersAndSanitizesSourceInfo() throws Exception {
        JsonNode allSources = getJsonOk(ADMIN_IMPORT_SOURCES_PATH, importSourceQuery(null))
                .get("data");
        assertTrue(allSources.isArray(), allSources.toString());
        assertImportSource(findImportSource(allSources, SOURCE_MCP_OFFICIAL), SOURCE_MCP_OFFICIAL,
                "mcp-registry", IMPORT_RESOURCE_TYPE_MCP);
        assertImportSource(findImportSource(allSources, SOURCE_SKILLS_SH), SOURCE_SKILLS_SH,
                "skills-sh", IMPORT_RESOURCE_TYPE_SKILL);
        assertSourcesDoNotExposeRuntimeFields(allSources);

        JsonNode mcpSources = getJsonOk(ADMIN_IMPORT_SOURCES_PATH,
                importSourceQuery(IMPORT_RESOURCE_TYPE_MCP)).get("data");
        assertImportSource(findImportSource(mcpSources, SOURCE_MCP_OFFICIAL), SOURCE_MCP_OFFICIAL,
                "mcp-registry", IMPORT_RESOURCE_TYPE_MCP);
        assertTrue(findImportSource(mcpSources, SOURCE_SKILLS_SH).isMissingNode(),
                mcpSources.toString());
        assertEverySourceSupports(mcpSources, IMPORT_RESOURCE_TYPE_MCP);

        JsonNode skillSources = getJsonOk(ADMIN_IMPORT_SOURCES_PATH,
                importSourceQuery(IMPORT_RESOURCE_TYPE_SKILL)).get("data");
        assertImportSource(findImportSource(skillSources, SOURCE_SKILLS_SH), SOURCE_SKILLS_SH,
                "skills-sh", IMPORT_RESOURCE_TYPE_SKILL);
        assertTrue(findImportSource(skillSources, SOURCE_MCP_OFFICIAL).isMissingNode(),
                skillSources.toString());
        assertEverySourceSupports(skillSources, IMPORT_RESOURCE_TYPE_SKILL);

        JsonNode unsupportedSources = getJsonOk(ADMIN_IMPORT_SOURCES_PATH,
                importSourceQuery("unsupported-resource-type")).get("data");
        assertTrue(unsupportedSources.isArray(), unsupportedSources.toString());
        assertEquals(0, unsupportedSources.size(), unsupportedSources.toString());
    }

    @Test
    public void testSearchValidationAndSourceErrors() throws Exception {
        assertError(postRaw(ADMIN_IMPORT_SEARCH_PATH,
                queryFrom(importSearchForm(null, SOURCE_MCP_OFFICIAL, "nacos", 10, null))),
                400, ErrorCode.PARAMETER_MISSING, "resourceType");
        assertError(postRaw(ADMIN_IMPORT_SEARCH_PATH,
                queryFrom(importSearchForm(IMPORT_RESOURCE_TYPE_MCP, null, "nacos", 10, null))),
                400, ErrorCode.PARAMETER_MISSING, "sourceId");
        assertError(postRaw(ADMIN_IMPORT_SEARCH_PATH,
                queryFrom(importSearchForm(IMPORT_RESOURCE_TYPE_MCP, SOURCE_MCP_OFFICIAL,
                        "nacos", 10, "{"))), 400, ErrorCode.PARAMETER_VALIDATE_ERROR,
                "options");

        Map<String, String> unknownSource = importSearchForm(IMPORT_RESOURCE_TYPE_MCP,
                UNKNOWN_SOURCE, "nacos", 0, importOptionsJson());
        unknownSource.put("cursor", "next-page");
        assertError(postRaw(ADMIN_IMPORT_SEARCH_PATH, queryFrom(unknownSource)), 404,
                ErrorCode.RESOURCE_NOT_FOUND, "source not found");

        assertError(postRaw(ADMIN_IMPORT_SEARCH_PATH,
                queryFrom(importSearchForm(IMPORT_RESOURCE_TYPE_SKILL, SOURCE_MCP_OFFICIAL,
                        "skill", 1, importOptionsJson()))), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "does not support resource type");
    }

    @Test
    public void testValidateSelectedItemsBoundariesAndSourceErrors() throws Exception {
        String selectedItems = importSelectedItemsJson("external-skill", "openapi-skill",
                "1.0.0");
        assertError(postRaw(ADMIN_IMPORT_VALIDATE_PATH,
                queryFrom(importValidateForm(null, SOURCE_SKILLS_SH, selectedItems, null,
                        false))), 400, ErrorCode.PARAMETER_MISSING, "resourceType");
        assertError(postRaw(ADMIN_IMPORT_VALIDATE_PATH,
                queryFrom(importValidateForm(IMPORT_RESOURCE_TYPE_SKILL, null, selectedItems,
                        null, false))), 400, ErrorCode.PARAMETER_MISSING, "sourceId");
        assertError(postRaw(ADMIN_IMPORT_VALIDATE_PATH,
                queryFrom(importSourceForm(IMPORT_RESOURCE_TYPE_SKILL, SOURCE_SKILLS_SH))),
                400, ErrorCode.PARAMETER_MISSING, "selectedItems");
        assertError(postRaw(ADMIN_IMPORT_VALIDATE_PATH,
                queryFrom(importValidateForm(IMPORT_RESOURCE_TYPE_SKILL, SOURCE_SKILLS_SH, "{",
                        null, false))), 400, ErrorCode.PARAMETER_VALIDATE_ERROR,
                "selectedItems");
        assertError(postRaw(ADMIN_IMPORT_VALIDATE_PATH,
                queryFrom(importValidateForm(IMPORT_RESOURCE_TYPE_SKILL, SOURCE_SKILLS_SH,
                        selectedItems, "{", false))), 400, ErrorCode.PARAMETER_VALIDATE_ERROR,
                "options");
        assertError(postRaw(ADMIN_IMPORT_VALIDATE_PATH,
                queryFrom(importValidateForm(IMPORT_RESOURCE_TYPE_SKILL, SOURCE_SKILLS_SH, "[]",
                        null, true))), 400, ErrorCode.PARAMETER_VALIDATE_ERROR,
                "selected items must not be empty");
        assertError(postRaw(ADMIN_IMPORT_VALIDATE_PATH,
                queryFrom(importValidateForm(IMPORT_RESOURCE_TYPE_SKILL, UNKNOWN_SOURCE,
                        selectedItems, importOptionsJson(), true))), 404,
                ErrorCode.RESOURCE_NOT_FOUND, "source not found");
        assertError(postRaw(ADMIN_IMPORT_VALIDATE_PATH,
                queryFrom(importValidateForm(IMPORT_RESOURCE_TYPE_MCP, SOURCE_SKILLS_SH,
                        selectedItems, importOptionsJson(), false))), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "does not support resource type");
    }

    @Test
    public void testExecuteSelectedItemsBoundariesAndSourceErrors() throws Exception {
        String selectedItems = importSelectedItemsJson("external-mcp", "openapi-mcp", "1.0.0");
        assertError(postRaw(ADMIN_IMPORT_EXECUTE_PATH,
                queryFrom(importExecuteForm(null, SOURCE_MCP_OFFICIAL, selectedItems, null,
                        false, true))), 400, ErrorCode.PARAMETER_MISSING, "resourceType");
        assertError(postRaw(ADMIN_IMPORT_EXECUTE_PATH,
                queryFrom(importExecuteForm(IMPORT_RESOURCE_TYPE_MCP, null, selectedItems, null,
                        false, true))), 400, ErrorCode.PARAMETER_MISSING, "sourceId");
        assertError(postRaw(ADMIN_IMPORT_EXECUTE_PATH,
                queryFrom(importSourceForm(IMPORT_RESOURCE_TYPE_MCP, SOURCE_MCP_OFFICIAL))),
                400, ErrorCode.PARAMETER_MISSING, "selectedItems");
        assertError(postRaw(ADMIN_IMPORT_EXECUTE_PATH,
                queryFrom(importExecuteForm(IMPORT_RESOURCE_TYPE_MCP, SOURCE_MCP_OFFICIAL, "{",
                        null, false, true))), 400, ErrorCode.PARAMETER_VALIDATE_ERROR,
                "selectedItems");
        assertError(postRaw(ADMIN_IMPORT_EXECUTE_PATH,
                queryFrom(importExecuteForm(IMPORT_RESOURCE_TYPE_MCP, SOURCE_MCP_OFFICIAL,
                        selectedItems, "{", false, true))), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "options");
        assertError(postRaw(ADMIN_IMPORT_EXECUTE_PATH,
                queryFrom(importExecuteForm(IMPORT_RESOURCE_TYPE_MCP, SOURCE_MCP_OFFICIAL, "[]",
                        null, true, true))), 400, ErrorCode.PARAMETER_VALIDATE_ERROR,
                "selected items must not be empty");
        assertError(postRaw(ADMIN_IMPORT_EXECUTE_PATH,
                queryFrom(importExecuteForm(IMPORT_RESOURCE_TYPE_MCP, UNKNOWN_SOURCE,
                        selectedItems, importOptionsJson(), true, false))), 404,
                ErrorCode.RESOURCE_NOT_FOUND, "source not found");
        assertError(postRaw(ADMIN_IMPORT_EXECUTE_PATH,
                queryFrom(importExecuteForm(IMPORT_RESOURCE_TYPE_SKILL, SOURCE_MCP_OFFICIAL,
                        selectedItems, importOptionsJson(), false, true))), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "does not support resource type");
    }

    private void assertImportSource(JsonNode source, String sourceId, String pluginName,
            String resourceType) {
        assertFalse(source.isMissingNode(), sourceId);
        assertEquals(sourceId, source.get("sourceId").asText(), source.toString());
        assertEquals(pluginName, source.get("pluginName").asText(), source.toString());
        assertTrue(source.get("enabled").asBoolean(), source.toString());
        assertResourceTypesContain(source, resourceType);
        assertTrue(source.get("capabilities").isArray(), source.toString());
        assertTrue(source.get("capabilities").toString().contains("search"), source.toString());
        assertTrue(source.get("capabilities").toString().contains("validate"), source.toString());
        assertTrue(source.get("capabilities").toString().contains("execute"), source.toString());
    }

    private void assertEverySourceSupports(JsonNode sources, String resourceType) {
        for (JsonNode source : sources) {
            assertResourceTypesContain(source, resourceType);
        }
    }

    private void assertResourceTypesContain(JsonNode source, String resourceType) {
        boolean found = false;
        for (JsonNode each : source.get("resourceTypes")) {
            if (resourceType.equals(each.asText())) {
                found = true;
            }
        }
        assertTrue(found, source.toString());
    }

    private void assertSourcesDoNotExposeRuntimeFields(JsonNode sources) {
        for (JsonNode source : sources) {
            assertFalse(source.has("endpoint"), source.toString());
            assertFalse(source.has("authRef"), source.toString());
            assertFalse(source.has("properties"), source.toString());
        }
    }

    private JsonNode findImportSource(JsonNode sources, String sourceId) {
        for (JsonNode source : sources) {
            if (sourceId.equals(source.get("sourceId").asText())) {
                return source;
            }
        }
        return MissingNode.getInstance();
    }
}
