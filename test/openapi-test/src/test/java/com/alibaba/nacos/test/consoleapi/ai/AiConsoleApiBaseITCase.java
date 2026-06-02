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

package com.alibaba.nacos.test.consoleapi.ai;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.copilot.constant.CopilotConstants;
import com.alibaba.nacos.test.adminapi.ai.AiAdminApiBaseITCase;
import com.fasterxml.jackson.databind.JsonNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shared helpers for AI console OpenAPI integration tests.
 *
 * @author xiweng.yy
 */
public abstract class AiConsoleApiBaseITCase extends AiAdminApiBaseITCase {

    protected static final String NACOS_CONSOLE_PORT = System.getProperty("nacos.console.port", "8080");

    protected static final String CONSOLE_BASE_URL = "http://" + NACOS_HOST + ":" + NACOS_CONSOLE_PORT;

    protected static final String CONSOLE_A2A_PATH = Constants.A2A.CONSOLE_PATH;

    protected static final String CONSOLE_A2A_LIST_PATH = CONSOLE_A2A_PATH + "/list";

    protected static final String CONSOLE_A2A_VERSION_LIST_PATH = CONSOLE_A2A_PATH + "/version/list";

    protected static final String CONSOLE_MCP_PATH = Constants.MCP_CONSOLE_PATH;

    protected static final String CONSOLE_MCP_LIST_PATH = CONSOLE_MCP_PATH + "/list";

    protected static final String CONSOLE_MCP_IMPORT_TOOLS_PATH = CONSOLE_MCP_PATH + "/importToolsFromMcp";

    protected static final String CONSOLE_MCP_IMPORT_VALIDATE_PATH = CONSOLE_MCP_PATH + "/import/validate";

    protected static final String CONSOLE_MCP_IMPORT_EXECUTE_PATH = CONSOLE_MCP_PATH + "/import/execute";

    protected static final String CONSOLE_PIPELINE_PATH = Constants.Pipeline.CONSOLE_PATH;

    protected static final String CONSOLE_PIPELINE_LIST_PATH =
            CONSOLE_PIPELINE_PATH + Constants.Pipeline.LIST_SUBPATH;

    protected static final String CONSOLE_PIPELINE_DETAIL_PATH =
            CONSOLE_PIPELINE_PATH + Constants.Pipeline.DETAIL_SUBPATH;

    protected static final String CONSOLE_PROMPT_PATH = Constants.Prompt.CONSOLE_PATH;

    protected static final String CONSOLE_PROMPT_LIST_PATH = CONSOLE_PROMPT_PATH + "/list";

    protected static final String CONSOLE_PROMPT_VERSIONS_PATH = CONSOLE_PROMPT_PATH + "/versions";

    protected static final String CONSOLE_PROMPT_GOVERNANCE_PATH = CONSOLE_PROMPT_PATH + "/governance";

    protected static final String CONSOLE_PROMPT_VERSION_PATH = CONSOLE_PROMPT_PATH + "/version";

    protected static final String CONSOLE_PROMPT_VERSION_DOWNLOAD_PATH =
            CONSOLE_PROMPT_PATH + "/version/download";

    protected static final String CONSOLE_SKILL_PATH = Constants.Skills.CONSOLE_PATH;

    protected static final String CONSOLE_SKILL_LIST_PATH = CONSOLE_SKILL_PATH + "/list";

    protected static final String CONSOLE_SKILL_VERSION_PATH = CONSOLE_SKILL_PATH + "/version";

    protected static final String CONSOLE_SKILL_VERSION_DOWNLOAD_PATH =
            CONSOLE_SKILL_PATH + "/version/download";

    protected static final String CONSOLE_AGENT_SPEC_PATH = Constants.AgentSpecs.CONSOLE_PATH;

    protected static final String CONSOLE_AGENT_SPEC_LIST_PATH = CONSOLE_AGENT_SPEC_PATH + "/list";

    protected static final String CONSOLE_AGENT_SPEC_VERSION_PATH =
            CONSOLE_AGENT_SPEC_PATH + "/version";

    protected static final String CONSOLE_IMPORT_PATH = Constants.AI_RESOURCE_IMPORT_CONSOLE_PATH;

    protected static final String CONSOLE_IMPORT_SOURCES_PATH = CONSOLE_IMPORT_PATH + "/sources";

    protected static final String CONSOLE_IMPORT_SEARCH_PATH = CONSOLE_IMPORT_PATH + "/search";

    protected static final String CONSOLE_IMPORT_VALIDATE_PATH = CONSOLE_IMPORT_PATH + "/validate";

    protected static final String CONSOLE_IMPORT_EXECUTE_PATH = CONSOLE_IMPORT_PATH + "/execute";

    protected static final String CONSOLE_COPILOT_PATH = CopilotConstants.COPILOT_CONSOLE_PATH;

    protected static final String CONSOLE_COPILOT_CONFIG_PATH = CONSOLE_COPILOT_PATH + "/config";

    @Override
    protected String baseUrl() {
        return CONSOLE_BASE_URL;
    }

    @Override
    protected void deleteMcpServerQuietly(String mcpName, String mcpId) throws Exception {
        deleteQuietly(CONSOLE_MCP_PATH, mcpIdentityQuery(mcpName, mcpId, null));
    }

    @Override
    protected void assertMcpDetail(JsonNode data, String mcpName, String version, String description,
            String toolName, String resourceName) {
        assertEquals(DEFAULT_NAMESPACE, data.get("namespaceId").asText(), data.toString());
        assertEquals(mcpName, data.get("name").asText(), data.toString());
        assertEquals(version, data.get("version").asText(), data.toString());
        assertEquals(description, data.get("description").asText(), data.toString());
        assertEquals("stdio", data.get("protocol").asText(), data.toString());
        assertEquals(version, data.get("versionDetail").get("version").asText(), data.toString());
        assertEquals(toolName, data.get("toolSpec").get("tools").get(0).get("name").asText(),
                data.toString());
        assertTrue(data.path("resourceSpec").isNull() || data.path("resourceSpec").isMissingNode(),
                "Console MCP API currently accepts but does not persist resourceSpecification: "
                        + resourceName + ", actual=" + data);
    }

    @Override
    protected void deleteAgentQuietly(String agentName, String version, String registrationType)
            throws Exception {
        Query query = Query.newInstance().addParam("agentName", agentName)
                .addParam("namespaceId", DEFAULT_NAMESPACE);
        addIfNotBlank(query, "version", version);
        addIfNotBlank(query, "registrationType", registrationType);
        deleteQuietly(CONSOLE_A2A_PATH, query);
    }

    @Override
    protected void deletePromptQuietly(String promptKey) throws Exception {
        deleteQuietly(CONSOLE_PROMPT_PATH, promptQuery(promptKey));
    }

    @Override
    protected void deleteSkillQuietly(String skillName) throws Exception {
        deleteQuietly(CONSOLE_SKILL_PATH, skillQuery(skillName));
    }

    @Override
    protected void deleteAgentSpecQuietly(String agentSpecName) throws Exception {
        deleteQuietly(CONSOLE_AGENT_SPEC_PATH, agentSpecQuery(agentSpecName));
    }
}
