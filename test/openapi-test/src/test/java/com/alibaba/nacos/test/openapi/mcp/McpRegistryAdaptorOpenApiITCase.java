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

package com.alibaba.nacos.test.openapi.mcp;

import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.test.adminapi.ai.AiAdminApiBaseITCase;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Live standalone-server conformance tests for the MCP Registry compatible {@code /v0} endpoints
 * exposed by the AI registry adaptor web context.
 *
 * <p>Regression coverage for the {@code buildUrl} null protocol/path handling and the
 * {@code _meta.official.status} population in {@code /v0} responses: a {@code DIRECT} endpoint
 * registered without {@code protocol} or {@code path} must render an {@code http} URL instead of a
 * {@code null} scheme and a {@code null} path fragment.</p>
 *
 * @author xiweng.yy
 */
public class McpRegistryAdaptorOpenApiITCase extends AiAdminApiBaseITCase {
    
    private static final String MCP_REGISTRY_PORT =
            System.getProperty("nacos.ai.registry.port", "9080");
    
    private static final String MCP_REGISTRY_BASE_URL =
            "http://" + NACOS_HOST + ':' + MCP_REGISTRY_PORT;
    
    private static final String V0_SERVERS_PATH = "/v0/servers";
    
    @Test
    public void testDirectEndpointWithoutProtocolOrPathRendersHttpUrlAndOfficialStatus() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String mcpName = "oit-mcpreg-mcp-" + suffix;
        String version = "1.0.0";
        
        JsonNode created = postFormOk(ADMIN_MCP_PATH, directEndpointMcpForm(mcpName, version, suffix));
        String mcpId = created.get("data").asText();
        assertFalse(mcpId.isBlank(), created.toString());
        addCleanup(() -> deleteMcpServerQuietly(mcpName, mcpId));
        grantAnonymousReadVisibility("mcp", mcpName);
        
        AuthIdentity identity = AUTH_ENABLED ? AuthIdentity.ADMIN : AuthIdentity.ANONYMOUS;
        String expectedUrl = "http://127.0.0.1:18080";
        
        // The server list exposes the DIRECT endpoint as an http URL with no null fragment.
        JsonNode listed = findListedServer(mcpName, identity);
        JsonNode remotes = listed.path("server").path("remotes");
        assertTrue(remotes.isArray() && remotes.size() > 0, listed.toString());
        assertEquals(expectedUrl, remotes.get(0).path("url").asText(), listed.toString());
        assertOfficialStatusPresent(listed);
        
        // The version detail keeps the same contract.
        HttpResponse detail = executeRaw(
                new HttpGet(MCP_REGISTRY_BASE_URL + V0_SERVERS_PATH + '/' + mcpName
                        + "/versions/" + version), identity);
        assertEquals(200, detail.code(), detail.body());
        JsonNode detailDocument = JacksonUtils.toObj(detail.body());
        assertEquals(expectedUrl, detailDocument.path("server").path("remotes").get(0)
                .path("url").asText(), detail.body());
        assertOfficialStatusPresent(detailDocument);
    }
    
    private JsonNode findListedServer(String mcpName, AuthIdentity identity) throws Exception {
        HttpResponse lastResponse = null;
        for (int retry = 0; retry < 60; retry++) {
            lastResponse = executeRaw(new HttpGet(MCP_REGISTRY_BASE_URL + V0_SERVERS_PATH
                    + "?limit=100"), identity);
            assertEquals(200, lastResponse.code(), lastResponse.body());
            JsonNode servers = JacksonUtils.toObj(lastResponse.body()).path("servers");
            if (servers.isArray()) {
                for (JsonNode server : servers) {
                    if (mcpName.equals(server.path("server").path("name").asText())) {
                        return server;
                    }
                }
            }
            Thread.sleep(500L);
        }
        assertNotNull(lastResponse);
        fail("MCP server " + mcpName + " not listed by " + V0_SERVERS_PATH + ": " + lastResponse.body());
        return null;
    }
    
    private void assertOfficialStatusPresent(JsonNode serverResponse) {
        JsonNode official = serverResponse.path("_meta")
                .path("io.modelcontextprotocol.registry/official");
        assertTrue(official.isObject(), serverResponse.toString());
        assertFalse(official.path("status").asText().isBlank(), serverResponse.toString());
    }
    
    private Map<String, String> directEndpointMcpForm(String mcpName, String version, String suffix) {
        Map<String, Object> versionDetail = new LinkedHashMap<>();
        versionDetail.put("version", version);
        Map<String, Object> server = new LinkedHashMap<>();
        server.put("name", mcpName);
        server.put("protocol", "http");
        server.put("frontProtocol", "mcp-sse");
        server.put("description", "MCP Registry IT " + suffix);
        server.put("versionDetail", versionDetail);
        server.put("enabled", true);
        
        Map<String, String> endpointData = new LinkedHashMap<>();
        endpointData.put("address", "127.0.0.1");
        endpointData.put("port", "18080");
        Map<String, Object> endpointSpecification = new LinkedHashMap<>();
        endpointSpecification.put("type", "DIRECT");
        endpointSpecification.put("data", endpointData);
        
        Map<String, String> form = new LinkedHashMap<>();
        form.put("namespaceId", DEFAULT_NAMESPACE);
        form.put("mcpName", mcpName);
        form.put("serverSpecification", JacksonUtils.toJson(server));
        form.put("endpointSpecification", JacksonUtils.toJson(endpointSpecification));
        return form;
    }
}
