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

package com.alibaba.nacos.test.adminapi.ai.mcp;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.test.adminapi.ai.AiAdminApiBaseITCase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Directed integration test for the temporary historical MCP management migration.
 *
 * <p>The migration workflow invokes the two phases against the same durable standalone
 * fixture. The normal OpenAPI suite does not enable either phase.</p>
 *
 * @author xiweng.yy
 */
public class McpMigrationAdminApiOpenApiITCase extends AiAdminApiBaseITCase {

    private static final String MIGRATION_PHASE_PROPERTY = "nacos.mcp.migration.phase";

    private static final String SYNCING_PHASE = "syncing";

    private static final String MANAGED_PHASE = "managed";

    private static final String MCP_NAME = "mcp-openapi-migration-it";

    private static final String VERSION_ONE = "1.0.0";

    private static final String VERSION_TWO = "1.1.0";

    private static final String DESCRIPTION = "historical MCP migration fixture";

    private static final String TOOL_NAME = "mcp_openapi_migration_tool";

    private static final String RESOURCE_NAME = "mcp_openapi_migration_resource";

    private static final String CLIENT_MCP_PATH = nacosPath(Constants.MCP_CLIENT_PATH);

    @Test
    @EnabledIfSystemProperty(named = MIGRATION_PHASE_PROPERTY, matches = SYNCING_PHASE)
    public void testHistoricalAuthorityAndLifecycleGateDuringSyncing() throws Exception {
        JsonNode created = postFormOk(ADMIN_MCP_PATH,
                mcpServerForm(MCP_NAME, VERSION_ONE, DESCRIPTION, TOOL_NAME, RESOURCE_NAME));
        assertTrue(created.path("data").isTextual(), created.toString());

        JsonNode historical = getJsonOk(ADMIN_MCP_PATH,
                mcpIdentityQuery(MCP_NAME, null, VERSION_ONE)).get("data");
        assertMcpDetail(historical, MCP_NAME, VERSION_ONE, DESCRIPTION, TOOL_NAME, RESOURCE_NAME);
        assertMcpLifecycleCutoverGate(ADMIN_MCP_PATH, MCP_NAME, VERSION_ONE);

        Map<String, String> draft = mcpServerForm("mcp-client-migration-gate-it", VERSION_ONE,
                "pre-cutover draft", "mcp_client_migration_tool",
                "mcp_client_migration_resource");
        draft.put("createDraft", "true");
        assertError(postRaw(CLIENT_MCP_PATH, queryFrom(draft)), 409,
                ErrorCode.RESOURCE_CONFLICT, "LIFECYCLE_MANAGED cutover");
    }

    @Test
    @EnabledIfSystemProperty(named = MIGRATION_PHASE_PROPERTY, matches = MANAGED_PHASE)
    public void testHistoricalResourceIsManagedAfterCutover() throws Exception {
        JsonNode migrated = getJsonOk(ADMIN_MCP_PATH + "/version",
                mcpLifecycleVersionQuery(MCP_NAME, VERSION_ONE)).get("data");
        assertEquals(MCP_NAME, migrated.path("mcpName").asText(), migrated.toString());
        assertEquals(VERSION_ONE, migrated.path("version").asText(), migrated.toString());
        assertEquals("online", migrated.path("status").asText(), migrated.toString());
        assertEquals(DESCRIPTION,
                migrated.path("serverSpecification").path("description").asText(),
                migrated.toString());

        JsonNode historical = getJsonOk(ADMIN_MCP_PATH,
                mcpIdentityQuery(MCP_NAME, null, VERSION_ONE)).get("data");
        assertMcpDetail(historical, MCP_NAME, VERSION_ONE, DESCRIPTION, TOOL_NAME, RESOURCE_NAME);
        String mcpId = historical.path("id").asText();
        addCleanup(() -> deleteMcpServerQuietly(MCP_NAME, mcpId));

        Map<String, String> draft = mcpServerForm(MCP_NAME, VERSION_TWO,
                "post-cutover draft", "mcp_openapi_migration_tool_v2",
                "mcp_openapi_migration_resource_v2");
        draft.put("createDraft", "true");
        JsonNode created = postFormOk(CLIENT_MCP_PATH, draft);
        assertEquals(mcpId, created.path("data").asText(), created.toString());
        JsonNode createdDraft = getJsonOk(ADMIN_MCP_PATH + "/version",
                mcpLifecycleVersionQuery(MCP_NAME, VERSION_TWO)).get("data");
        assertEquals("draft", createdDraft.path("status").asText(), createdDraft.toString());

        deleteJsonOk(ADMIN_MCP_PATH, mcpIdentityQuery(MCP_NAME, null, null));
        assertMcpServerAbsentEventually(ADMIN_MCP_PATH, mcpId);
    }
}
