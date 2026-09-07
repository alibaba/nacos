/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.test.sdk.ai;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.ai.AiService;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerVersionDetail;
import com.alibaba.nacos.api.ai.model.mcp.McpTool;
import com.alibaba.nacos.api.ai.model.mcp.McpToolSpecification;
import com.alibaba.nacos.api.ai.model.mcp.registry.ServerVersionDetail;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.maintainer.client.ai.AiMaintainerFactory;
import com.alibaba.nacos.maintainer.client.ai.McpMaintainerService;
import com.alibaba.nacos.test.sdk.JavaSdkBaseITCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.Collections;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Directed Java SDK integration test for the temporary historical MCP migration.
 *
 * <p>The migration workflow invokes the two phases against the same durable standalone
 * fixture. The normal Java SDK suite does not enable either phase.</p>
 *
 * @author xiweng.yy
 */
class McpUpgradeMigrationJavaSdkITCase extends JavaSdkBaseITCase {

    private static final String MIGRATION_PHASE_PROPERTY = "nacos.mcp.migration.phase";

    private static final String SYNCING_PHASE = "syncing";

    private static final String MANAGED_PHASE = "managed";

    private static final String MCP_NAME = "mcp-java-migration-it";

    private static final String GATED_DRAFT_NAME = "mcp-java-migration-gate-it";

    private static final String VERSION_ONE = "1.0.0";

    private static final String VERSION_TWO = "1.1.0";

    @Test
    @EnabledIfSystemProperty(named = MIGRATION_PHASE_PROPERTY, matches = SYNCING_PHASE)
    void shouldKeepHistoricalClientAuthorityDuringSyncing() throws Exception {
        AiService service = createAiService();
        String mcpId = service.releaseMcpServer(server(MCP_NAME, VERSION_ONE),
                toolSpecification(MCP_NAME), false);
        assertNotNull(mcpId);
        McpServerDetailInfo historical = service.getMcpServer(MCP_NAME, VERSION_ONE);
        assertEquals(mcpId, historical.getId(), historical.toString());
        assertEquals(VERSION_ONE, historical.getVersionDetail().getVersion(),
                historical.toString());

        NacosException gated = assertThrows(NacosException.class,
                () -> service.releaseMcpServer(server(GATED_DRAFT_NAME, VERSION_ONE),
                        toolSpecification(GATED_DRAFT_NAME), true));
        assertEquals(NacosException.CONFLICT, gated.getErrCode(), gated.toString());
        assertTrue(gated.getMessage().contains("LIFECYCLE_MANAGED cutover"), gated.toString());
    }

    @Test
    @EnabledIfSystemProperty(named = MIGRATION_PHASE_PROPERTY, matches = MANAGED_PHASE)
    void shouldExposeReconciledResourceAndManagedDraftAfterCutover() throws Exception {
        AiService service = createAiService();
        McpMaintainerService maintainer = createMcpMaintainerService();
        McpServerDetailInfo serving = service.getMcpServer(MCP_NAME, VERSION_ONE);
        assertEquals(MCP_NAME, serving.getName(), serving.toString());
        assertEquals(VERSION_ONE, serving.getVersionDetail().getVersion(), serving.toString());

        McpServerVersionDetail migrated = maintainer.getMcpServerVersion(MCP_NAME, VERSION_ONE);
        assertEquals(MCP_NAME, migrated.getMcpName(), migrated.toString());
        assertEquals(VERSION_ONE, migrated.getVersion(), migrated.toString());
        assertEquals("online", migrated.getStatus(), migrated.toString());

        String draftId = service.releaseMcpServer(server(MCP_NAME, VERSION_TWO),
                toolSpecification(MCP_NAME), true);
        assertEquals(serving.getId(), draftId);
        McpServerVersionDetail draft = maintainer.getMcpServerVersion(MCP_NAME, VERSION_TWO);
        assertEquals("draft", draft.getStatus(), draft.toString());
        addCleanup(() -> maintainer.deleteMcpServer(Constants.DEFAULT_NAMESPACE_ID, MCP_NAME,
                null, null));
        assertTrue(maintainer.deleteMcpServer(MCP_NAME));
    }

    private McpMaintainerService createMcpMaintainerService() throws NacosException {
        Properties properties = maintainerProperties();
        properties.setProperty(PropertyKeyConst.CONTEXT_PATH, "/nacos");
        return AiMaintainerFactory.createAiMaintainerService(properties).mcp();
    }

    private McpServerBasicInfo server(String mcpName, String version) {
        McpServerBasicInfo result = new McpServerBasicInfo();
        result.setName(mcpName);
        result.setDescription("historical MCP Java migration fixture");
        result.setProtocol(AiConstants.Mcp.MCP_PROTOCOL_STDIO);
        result.setVersion(version);
        ServerVersionDetail versionDetail = new ServerVersionDetail();
        versionDetail.setVersion(version);
        result.setVersionDetail(versionDetail);
        return result;
    }

    private McpToolSpecification toolSpecification(String mcpName) {
        McpTool tool = new McpTool();
        tool.setName("tool_" + mcpName.replace('-', '_'));
        tool.setDescription("MCP Java migration tool");
        tool.setInputSchema(Collections.singletonMap("type", "object"));
        McpToolSpecification result = new McpToolSpecification();
        result.setTools(Collections.singletonList(tool));
        return result;
    }
}
