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
 * Integration tests for RAD Search and Discover under {@code /nacos/v3/client/ai/agents}.
 *
 * <p>Scenario coverage:
 * <ul>
 *     <li>Expected capability: Search returns an enabled Agent's online Version catalog, while
 *     Discover resolves latest and returns the native protocol descriptor plus its authoritative
 *     Endpoint sets.</li>
 *     <li>Boundary/validation: omitted namespace uses {@code public}; Search filters by name,
 *     tags, and protocol; Discover supports protocol filtering and rejects mutually exclusive
 *     Version and label references.</li>
 *     <li>Exception/error handling: no-match Search returns an empty page, invalid pagination
 *     returns HTTP 400, and an absent Discover target returns a controlled not-found envelope.</li>
 * </ul>
 *
 * @author xiweng.yy
 */
public class AgentDiscoveryClientOpenApiITCase extends AgentClientOpenApiBaseITCase {
    
    @Test
    public void testSearchAndDiscoverOnlineAgent() throws Exception {
        String agentName = randomAiName("agent-client-discovery");
        String version = "1.0.0";
        publishAgent(agentName, version);
        
        JsonNode search = getJsonOk(AGENT_SEARCH_PATH,
                Query.newInstance().addParam("agentNameContains", agentName)
                        .addParam("tagsAll", "openapi-it")
                        .addParam("protocolsAny", "a2a")
                        .addParam("pageNo", "1").addParam("pageSize", "10"));
        JsonNode page = search.get("data");
        assertEmptyPageShape(page);
        assertEquals(1, page.get("totalCount").asInt(), page.toString());
        JsonNode catalog = findCatalog(page, agentName);
        assertFalse(catalog.isMissingNode(), page.toString());
        assertEquals(version, catalog.get("latestVersion").asText(), catalog.toString());
        assertEquals(version, catalog.get("versions").get(0).get("version").asText(),
                catalog.toString());
        assertEquals("a2a", catalog.get("versions").get(0).get("protocols").get(0).asText(),
                catalog.toString());
        
        JsonNode discovered = getJsonOk(AGENT_CLIENT_PATH,
                Query.newInstance().addParam("agentName", agentName)).get("data");
        assertEquals(DEFAULT_NAMESPACE, discovered.get("namespaceId").asText(),
                discovered.toString());
        assertEquals(agentName, discovered.get("agentName").asText(), discovered.toString());
        assertEquals(version, discovered.get("version").asText(), discovered.toString());
        assertTrue(discovered.get("contentDigest").asText().startsWith("sha256:"),
                discovered.toString());
        JsonNode callInterface = discovered.get("callInterfaces").get(0);
        assertEquals("a2a", callInterface.get("protocol").asText(), callInterface.toString());
        assertEquals(agentName, callInterface.get("nativeDescriptor").get("name").asText(),
                callInterface.toString());
        assertEquals("RUNTIME", callInterface.get("endpointSets").get(0).get("source").asText(),
                callInterface.toString());
        assertEquals(0, callInterface.get("endpointSets").get(0).get("endpoints").size(),
                callInterface.toString());
        
        JsonNode filtered = getJsonOk(AGENT_CLIENT_PATH,
                Query.newInstance().addParam("agentName", agentName)
                        .addParam("protocol", "jsonrpc")).get("data");
        assertEquals(0, filtered.get("callInterfaces").size(), filtered.toString());
    }
    
    @Test
    public void testSearchAndDiscoverValidationAndNotFound() throws Exception {
        String absentName = randomAiName("agent-client-absent");
        JsonNode empty = getJsonOk(AGENT_SEARCH_PATH,
                Query.newInstance().addParam("agentNameContains", absentName)).get("data");
        assertEquals(0, empty.get("totalCount").asInt(), empty.toString());
        assertEquals(0, empty.get("pageItems").size(), empty.toString());
        
        assertError(getRaw(AGENT_SEARCH_PATH + "?pageNo=0"), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "pageNo");
        assertError(getRaw(AGENT_SEARCH_PATH + "?pageSize=101"), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "pageSize");
        assertError(getRaw(AGENT_CLIENT_PATH, Query.newInstance()), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "agentName");
        assertError(getRaw(AGENT_CLIENT_PATH,
                Query.newInstance().addParam("agentName", absentName)), 404,
                ErrorCode.RESOURCE_NOT_FOUND, "agent not found");
        assertError(getRaw(AGENT_CLIENT_PATH,
                Query.newInstance().addParam("agentName", absentName)
                        .addParam("version", "1.0.0").addParam("label", "stable")), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "mutually exclusive");
    }
    
    private JsonNode findCatalog(JsonNode page, String agentName) {
        for (JsonNode item : page.get("pageItems")) {
            if (agentName.equals(item.get("agentName").asText())) {
                return item;
            }
        }
        return MissingNode.getInstance();
    }
}
