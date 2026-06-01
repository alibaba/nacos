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

import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.http.param.Query;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for the AI AgentSpec search Open API {@code GET /nacos/v3/client/ai/agentspecs/search}.
 *
 * <p>Scenario coverage:
 * <ul>
 *     <li>Expected capability: search returns enabled AgentSpecs with online versions, filters by keyword, and exposes
 *     the expected page fields and basic AgentSpec information.</li>
 *     <li>Boundary/validation: omitted namespaceId uses public; keyword is optional; pageNo and pageSize default when
 *     omitted and reject non-positive values.</li>
 *     <li>Exception/error handling: no-match search returns a successful empty page, while invalid pagination returns
 *     HTTP 400 with a wrapped validation error instead of HTTP 500.</li>
 * </ul>
 *
 * <p>Draft, update-draft, force-publish, and delete calls to {@code /nacos/v3/admin/ai/agentspecs} are helper calls
 * only; this class keeps its assertions focused on the runtime client search contract.
 *
 * @author xiweng.yy
 */
public class AgentSpecSearchClientOpenApiITCase extends AgentSpecOpenApiBaseITCase {
    
    private static final String AGENT_SPEC_SEARCH_PATH = AGENT_SPEC_CLIENT_PATH + "/search";
    
    @Test
    public void testSearchAgentSpecsByKeywordAndPagination() throws Exception {
        String suffix = randomAgentSpecSuffix();
        String firstName = "oit-search-a-" + suffix;
        String secondName = "oit-search-b-" + suffix;
        publishAgentSpec(firstName, "1.0.0", null, "Search AgentSpec A", "search-a", "soul a");
        addCleanup(() -> deleteAgentSpec(firstName));
        publishAgentSpec(secondName, "1.0.0", null, "Search AgentSpec B", "search-b", "soul b");
        addCleanup(() -> deleteAgentSpec(secondName));
        
        JsonNode both = search(Query.newInstance().addParam("keyword", suffix));
        JsonNode bothPage = both.get("data");
        assertEquals(1, bothPage.get("pageNumber").asInt(), bothPage.toString());
        assertEquals(2, bothPage.get("totalCount").asInt(), bothPage.toString());
        assertContainsAgentSpec(bothPage, firstName, "Search AgentSpec A");
        assertContainsAgentSpec(bothPage, secondName, "Search AgentSpec B");
        
        JsonNode paged = search(Query.newInstance().addParam("keyword", suffix)
                .addParam("pageNo", "1").addParam("pageSize", "1"));
        JsonNode pagedData = paged.get("data");
        assertEquals(1, pagedData.get("pageNumber").asInt(), pagedData.toString());
        assertEquals(2, pagedData.get("totalCount").asInt(), pagedData.toString());
        assertEquals(2, pagedData.get("pagesAvailable").asInt(), pagedData.toString());
        assertEquals(1, pagedData.get("pageItems").size(), pagedData.toString());
        
        JsonNode firstOnly = search(Query.newInstance().addParam("keyword", "search-a-" + suffix));
        JsonNode firstOnlyPage = firstOnly.get("data");
        assertEquals(1, firstOnlyPage.get("totalCount").asInt(), firstOnlyPage.toString());
        assertContainsAgentSpec(firstOnlyPage, firstName, "Search AgentSpec A");
        assertNotContainsAgentSpec(firstOnlyPage, secondName);
    }
    
    @Test
    public void testSearchAgentSpecsEmptyKeywordUsesPublicNamespace() throws Exception {
        String name = randomAgentSpecName("open-search");
        publishAgentSpec(name, "1.0.0", null, "Open Search AgentSpec", "open-search", "open soul");
        addCleanup(() -> deleteAgentSpec(name));
        
        JsonNode root = search(Query.newInstance().addParam("pageNo", "1").addParam("pageSize", "500"));
        JsonNode page = root.get("data");
        assertEquals(1, page.get("pageNumber").asInt(), page.toString());
        assertContainsAgentSpec(page, name, "Open Search AgentSpec");
    }
    
    @Test
    public void testSearchAgentSpecsNoMatchReturnsEmptyPage() throws Exception {
        JsonNode root = search(Query.newInstance().addParam("keyword", "no-match-" + randomAgentSpecSuffix()));
        JsonNode page = root.get("data");
        assertEquals(1, page.get("pageNumber").asInt(), page.toString());
        assertEquals(0, page.get("totalCount").asInt(), page.toString());
        assertEquals(0, page.get("pagesAvailable").asInt(), page.toString());
        assertEquals(0, page.get("pageItems").size(), page.toString());
    }
    
    @Test
    public void testSearchAgentSpecsInvalidPaginationReturnsBadRequest() throws Exception {
        assertError(getRaw(AGENT_SPEC_SEARCH_PATH + "?pageNo=0"), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "pageNo");
        assertError(getRaw(AGENT_SPEC_SEARCH_PATH + "?pageSize=0"), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "pageSize");
    }
    
    private JsonNode search(Query query) throws Exception {
        return getJsonOk(AGENT_SPEC_SEARCH_PATH, query);
    }
    
    private void assertContainsAgentSpec(JsonNode page, String name, String description) {
        JsonNode found = findAgentSpec(page, name);
        assertFalse(found.isMissingNode(), page.toString());
        assertEquals(description, found.get("description").asText(), found.toString());
    }
    
    private void assertNotContainsAgentSpec(JsonNode page, String name) {
        assertTrue(findAgentSpec(page, name).isMissingNode(), page.toString());
    }
    
    private JsonNode findAgentSpec(JsonNode page, String name) {
        for (JsonNode item : page.get("pageItems")) {
            if (name.equals(item.get("name").asText())) {
                return item;
            }
        }
        return MissingNode.getInstance();
    }
}
