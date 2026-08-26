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

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.test.adminapi.ai.AiAdminApiBaseITCase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Integration tests for generic and resource-specific AI Resource Search Client Open APIs.
 *
 * <p>Scenario coverage:
 * <ul>
 *     <li>Expected capability: Admin lifecycle APIs publish one current online Agent, AgentSpec,
 *     Skill, Prompt, and MCP resource. Generic Search recalls all five through one global query,
 *     and every generic single-type result is cross-checked against its resource-specific Search
 *     facade. Generic Search exposes the canonical MCP name while the dedicated MCP DTO keeps
 *     its compatible ID field.</li>
 *     <li>Boundary/validation: omitted namespace uses public; blank query lists deterministically;
 *     exact-all tags, exact-any capabilities, MCP protocol filtering, and opaque cursor traversal
 *     work across page boundaries; unsupported types, malformed cursors, invalid limits,
 *     oversized queries, and invalid numbered pages are rejected.</li>
 *     <li>Exception/error handling: no-match Search returns a successful empty cursor page.
 *     Search remains available with the current snapshot during asynchronous projection, and the
 *     test then waits for durable index convergence before comparing complete results.</li>
 * </ul>
 *
 * @author xiweng.yy
 */
public class AiResourceSearchClientOpenApiITCase extends AiAdminApiBaseITCase {
    
    private static final String GENERIC_SEARCH_PATH =
            nacosPath(Constants.AI_RESOURCE_SEARCH_CLIENT_PATH);
    
    private static final String AGENT_SEARCH_PATH =
            nacosPath(Constants.Agent.CLIENT_PATH + "/search");
    
    private static final String AGENT_SPEC_SEARCH_PATH =
            nacosPath(Constants.AgentSpecs.CLIENT_PATH + "/search");
    
    private static final String SKILL_SEARCH_PATH =
            nacosPath(Constants.Skills.CLIENT_PATH + "/search");
    
    private static final String PROMPT_SEARCH_PATH =
            nacosPath(Constants.Prompt.CLIENT_PATH + "/search");
    
    private static final String MCP_SEARCH_PATH =
            nacosPath(Constants.MCP_CLIENT_PATH + "/search");
    
    private static final int SEARCH_MAX_RETRIES = 120;
    
    private static final long SEARCH_RETRY_INTERVAL_MILLIS = 250L;
    
    @Test
    public void testCrossTypeSearchSpecificFacadesFiltersAndCursor() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        SearchFixture fixture = publishFixture(suffix);
        Set<String> expectedKeys = fixture.expectedGenericKeys();
        
        JsonNode crossType = awaitGenericSearch(genericQuery(suffix), expectedKeys);
        assertEquals(expectedKeys, genericKeys(crossType), crossType.toString());
        
        for (String resourceType : expectedKeysByType(expectedKeys).keySet()) {
            Set<String> genericSingleType = genericKeys(awaitGenericSearch(
                    genericQuery(suffix).addParam("resourceTypes", resourceType),
                    expectedKeysByType(expectedKeys).get(resourceType)));
            Set<String> dedicated = dedicatedKeys(resourceType,
                    awaitDedicatedSearch(resourceType, suffix));
            assertEquals(genericSingleType, dedicated, resourceType);
        }
        
        JsonNode blankList = awaitGenericSearch(Query.newInstance()
                .addParam("resourceTypes", "agent,agentspec,skill,prompt,mcp")
                .addParam("limit", "100"), expectedKeys);
        assertTrue(genericKeys(blankList).containsAll(expectedKeys), blankList.toString());
        
        Set<String> expectedTagged = new LinkedHashSet<>(expectedKeys);
        expectedTagged.remove("mcp:" + fixture.mcpName);
        JsonNode tagged = awaitGenericSearch(genericQuery(suffix)
                .addParam("tagsAll", suffix), expectedTagged);
        assertEquals(expectedTagged, genericKeys(tagged), tagged.toString());
        
        JsonNode capable = awaitGenericSearch(genericQuery(suffix)
                .addParam("capabilitiesAny", "tool"),
                Set.of("mcp:" + fixture.mcpName));
        assertEquals(Set.of("mcp:" + fixture.mcpName), genericKeys(capable), capable.toString());
        
        JsonNode mcpByProtocol = awaitDedicatedSearch("mcp", suffix,
                Query.newInstance().addParam("protocolsAny", "stdio"));
        assertEquals(Set.of("mcp:" + fixture.mcpId), dedicatedKeys("mcp", mcpByProtocol),
                mcpByProtocol.toString());
        JsonNode absentProtocol = getJsonOk(MCP_SEARCH_PATH, Query.newInstance()
                .addParam("query", suffix).addParam("protocolsAny", "sse")
                .addParam("pageNo", "1").addParam("pageSize", "100"));
        assertEquals(0, absentProtocol.get("data").get("totalCount").asInt(),
                absentProtocol.toString());
        
        Set<String> traversed = traverseCursor(suffix);
        assertEquals(expectedKeys, traversed);
    }
    
    @Test
    public void testSearchEmptyAndValidationContracts() throws Exception {
        JsonNode empty = getJsonOk(GENERIC_SEARCH_PATH, Query.newInstance()
                .addParam("query", "absent-" + UUID.randomUUID()).addParam("limit", "10"));
        assertEquals(0, empty.get("data").get("items").size(), empty.toString());
        assertTrue(empty.get("data").path("nextCursor").isMissingNode()
                || empty.get("data").path("nextCursor").isNull(), empty.toString());
        
        assertError(getRaw(GENERIC_SEARCH_PATH,
                Query.newInstance().addParam("resourceTypes", "unknown")), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "Unsupported AI resource type");
        assertError(getRaw(GENERIC_SEARCH_PATH,
                Query.newInstance().addParam("cursor", "not-a-cursor")), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "Invalid discovery cursor");
        assertError(getRaw(GENERIC_SEARCH_PATH + "?limit=0"), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "limit");
        assertError(getRaw(GENERIC_SEARCH_PATH + "?limit=101"), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "limit");
        assertError(getRaw(GENERIC_SEARCH_PATH, Query.newInstance()
                .addParam("query", "x".repeat(1025))), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "query");
        assertError(getRaw(SKILL_SEARCH_PATH + "?pageNo=0"), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "pageNo");
        assertError(getRaw(PROMPT_SEARCH_PATH + "?pageSize=0"), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "pageSize");
        assertError(getRaw(MCP_SEARCH_PATH + "?pageNo=0"), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "pageNo");
    }
    
    private SearchFixture publishFixture(String suffix) throws Exception {
        SearchFixture fixture = new SearchFixture();
        fixture.agentName = "oit-search-agent-" + suffix;
        postFormOk(ADMIN_AGENT_PATH + "/draft",
                agentForm(agentInitialDraftRequest(null, fixture.agentName, "1.0.0")));
        addCleanup(() -> deleteAgentDefinitionQuietly(DEFAULT_NAMESPACE, fixture.agentName));
        postFormOk(ADMIN_AGENT_PATH + "/force-publish",
                agentForm(agentVersionCommand(null, fixture.agentName, "1.0.0")));
        putFormOk(ADMIN_AGENT_PATH,
                agentForm(agentUpdateRequest(null, fixture.agentName, suffix)));
        
        fixture.agentSpecName = "oit-search-agentspec-" + suffix;
        postFormOk(ADMIN_AGENT_SPEC_PATH + "/draft",
                agentSpecDraftForm(fixture.agentSpecName, "1.0.0"));
        putFormOk(ADMIN_AGENT_SPEC_PATH + "/draft", agentSpecUpdateForm(
                fixture.agentSpecName, "1.0.0", "AgentSpec " + suffix,
                "search " + suffix, "soul " + suffix));
        addCleanup(() -> deleteAgentSpecQuietly(fixture.agentSpecName));
        postFormOk(ADMIN_AGENT_SPEC_PATH + "/force-publish",
                agentSpecPublishForm(fixture.agentSpecName, "1.0.0"));
        putFormOk(ADMIN_AGENT_SPEC_PATH + "/biz-tags",
                agentSpecBizTagsForm(fixture.agentSpecName,
                        "[\"openapi-it\",\"" + suffix + "\"]"));
        
        fixture.skillName = "oit-search-skill-" + suffix;
        postFormOk(ADMIN_SKILL_PATH + "/draft", skillDraftForm(fixture.skillName,
                "1.0.0", "Skill body " + suffix, "Skill guide " + suffix));
        addCleanup(() -> deleteSkillQuietly(fixture.skillName));
        postFormOk(ADMIN_SKILL_PATH + "/force-publish",
                skillPublishForm(fixture.skillName, "1.0.0"));
        putFormOk(ADMIN_SKILL_PATH + "/biz-tags",
                skillBizTagsForm(fixture.skillName, "openapi-it," + suffix));
        
        fixture.promptKey = "oit_search_prompt_" + suffix;
        postFormOk(ADMIN_PROMPT_PATH + "/draft", promptDraftForm(fixture.promptKey,
                "1.0.0", "Prompt template " + suffix, "Prompt " + suffix,
                "openapi-it," + suffix));
        addCleanup(() -> deletePromptQuietly(fixture.promptKey));
        postFormOk(ADMIN_PROMPT_PATH + "/force-publish",
                promptPublishForm(fixture.promptKey, "1.0.0"));
        
        fixture.mcpName = "oit-search-mcp-" + suffix;
        JsonNode created = postFormOk(ADMIN_MCP_PATH, mcpServerForm(fixture.mcpName,
                "1.0.0", "MCP " + suffix, "tool_" + suffix,
                "resource_" + suffix));
        fixture.mcpId = created.get("data").asText();
        assertFalse(fixture.mcpId.isBlank(), created.toString());
        addCleanup(() -> deleteMcpServerQuietly(fixture.mcpName, fixture.mcpId));
        JsonNode publishedMcp = putFormOk(ADMIN_MCP_PATH, mcpServerForm(fixture.mcpName,
                "1.0.0", "MCP " + suffix, "tool_" + suffix,
                "resource_" + suffix));
        assertEquals("ok", publishedMcp.get("data").asText(), publishedMcp.toString());
        return fixture;
    }
    
    private Query genericQuery(String suffix) {
        return Query.newInstance().addParam("query", suffix).addParam("limit", "100");
    }
    
    private JsonNode awaitGenericSearch(Query query, Set<String> expectedKeys) throws Exception {
        JsonNode last = null;
        for (int retry = 0; retry <= SEARCH_MAX_RETRIES; retry++) {
            last = getJsonOk(GENERIC_SEARCH_PATH, query);
            if (genericKeys(last).containsAll(expectedKeys)) {
                return last;
            }
            if (retry < SEARCH_MAX_RETRIES) {
                Thread.sleep(SEARCH_RETRY_INTERVAL_MILLIS);
            }
        }
        fail("AI Resource Search projection did not converge: " + last);
        return last;
    }
    
    private JsonNode awaitDedicatedSearch(String resourceType, String suffix) throws Exception {
        return awaitDedicatedSearch(resourceType, suffix, Query.newInstance());
    }
    
    private JsonNode awaitDedicatedSearch(String resourceType, String suffix, Query extra)
            throws Exception {
        Query query = dedicatedQuery(resourceType, suffix, extra);
        JsonNode last = null;
        for (int retry = 0; retry <= SEARCH_MAX_RETRIES; retry++) {
            last = getJsonOk(dedicatedPath(resourceType), query);
            if (last.get("data").get("totalCount").asInt() > 0) {
                return last;
            }
            if (retry < SEARCH_MAX_RETRIES) {
                Thread.sleep(SEARCH_RETRY_INTERVAL_MILLIS);
            }
        }
        fail("Dedicated " + resourceType + " Search did not converge: " + last);
        return last;
    }
    
    private Query dedicatedQuery(String resourceType, String suffix, Query extra) {
        Query result = Query.newInstance();
        String textField = "agent".equals(resourceType) ? "agentNameContains"
                : "agentspec".equals(resourceType) ? "keyword" : "query";
        result.addParam(textField, suffix).addParam("pageNo", "1")
                .addParam("pageSize", "100");
        if (extra.getValue("protocolsAny") != null) {
            result.addParam("protocolsAny", extra.getValue("protocolsAny"));
        }
        return result;
    }
    
    private String dedicatedPath(String resourceType) {
        return switch (resourceType) {
            case "agent" -> AGENT_SEARCH_PATH;
            case "agentspec" -> AGENT_SPEC_SEARCH_PATH;
            case "skill" -> SKILL_SEARCH_PATH;
            case "prompt" -> PROMPT_SEARCH_PATH;
            case "mcp" -> MCP_SEARCH_PATH;
            default -> throw new IllegalArgumentException(resourceType);
        };
    }
    
    private Set<String> dedicatedKeys(String resourceType, JsonNode root) {
        String identityField = switch (resourceType) {
            case "agent" -> "agentName";
            case "agentspec", "skill" -> "name";
            case "prompt" -> "promptKey";
            case "mcp" -> "id";
            default -> throw new IllegalArgumentException(resourceType);
        };
        Set<String> result = new LinkedHashSet<>();
        for (JsonNode item : root.get("data").get("pageItems")) {
            result.add(resourceType + ':' + item.get(identityField).asText());
        }
        return result;
    }
    
    private Set<String> genericKeys(JsonNode root) {
        Set<String> result = new LinkedHashSet<>();
        JsonNode data = root == null ? null : root.get("data");
        JsonNode items = data == null ? null : data.get("items");
        if (items == null) {
            return result;
        }
        for (JsonNode item : items) {
            result.add(item.get("resourceType").asText() + ':'
                    + item.get("resourceName").asText());
        }
        return result;
    }
    
    private Map<String, Set<String>> expectedKeysByType(Set<String> expectedKeys) {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        for (String expectedKey : expectedKeys) {
            String resourceType = expectedKey.substring(0, expectedKey.indexOf(':'));
            result.computeIfAbsent(resourceType, ignored -> new LinkedHashSet<>())
                    .add(expectedKey);
        }
        return result;
    }
    
    private Set<String> traverseCursor(String suffix) throws Exception {
        Set<String> result = new LinkedHashSet<>();
        String cursor = null;
        do {
            Query query = Query.newInstance().addParam("query", suffix).addParam("limit", "2");
            if (cursor != null) {
                query.addParam("cursor", cursor);
            }
            JsonNode root = getJsonOk(GENERIC_SEARCH_PATH, query);
            for (String key : genericKeys(root)) {
                assertTrue(result.add(key), "Duplicate cursor result: " + key);
            }
            JsonNode nextCursor = root.get("data").path("nextCursor");
            cursor = nextCursor.isMissingNode() || nextCursor.isNull()
                    ? null : nextCursor.asText();
        } while (cursor != null);
        return result;
    }
    
    private static final class SearchFixture {
        
        private String agentName;
        
        private String agentSpecName;
        
        private String skillName;
        
        private String promptKey;
        
        private String mcpName;
        
        private String mcpId;
        
        private Set<String> expectedGenericKeys() {
            assertNotNull(mcpName);
            return new LinkedHashSet<>(Set.of("agent:" + agentName,
                    "agentspec:" + agentSpecName, "skill:" + skillName,
                    "prompt:" + promptKey, "mcp:" + mcpName));
        }
    }
}
