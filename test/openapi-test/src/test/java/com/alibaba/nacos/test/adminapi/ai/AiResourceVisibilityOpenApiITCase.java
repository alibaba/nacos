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

package com.alibaba.nacos.test.adminapi.ai;

import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.http.param.Query;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Auth-enabled Agent/MCP default visibility and independent scope integration tests.
 *
 * <p>Scenario coverage: a non-owner with ordinary AI READ permission discovers a default-public
 * service without a visibility grant; private scope removes that access, explicit grants restore
 * it, and revocation removes it again. Public scope never grants WRITE or bypasses request auth.
 * Agent draft and namespace boundaries remain enforced. See ADMIN_API_TEST_SCENARIOS.md.</p>
 *
 * @author Nacos
 */
public class AiResourceVisibilityOpenApiITCase extends AiAdminApiBaseITCase {

    private static final String AGENT_CLIENT_PATH = nacosPath("/v3/client/ai/agents");

    private static final String MCP_CLIENT_PATH = nacosPath("/v3/client/ai/mcp");

    private static final String GRANT_PATH = nacosPath("/v3/auth/visibility");

    @Test
    public void testAgentScopeAcrossIdentitiesAndLifecycle() throws Exception {
        assumeTrue(AUTH_ENABLED, "Visibility isolation requires auth");
        String name = randomAiName("agent-visibility");
        postFormOk(ADMIN_AGENT_PATH + "/draft",
                agentForm(agentInitialDraftRequest(null, name, "1.0.0")));
        addCleanup(() -> deleteAgentDefinitionQuietly(DEFAULT_NAMESPACE, name));
        Query identity = agentIdentityQuery(null, name);
        JsonNode metadata = getJsonOk(ADMIN_AGENT_PATH, identity).get("data").get("agent");
        assertEquals("PUBLIC", metadata.get("scope").asText());
        assertNotEquals(identityUsername(AuthIdentity.CLIENT_READ_ONLY),
                metadata.get("owner").asText());
        assertEquals(404, getRaw(AGENT_CLIENT_PATH, identity,
                AuthIdentity.CLIENT_READ_ONLY).code());
        postFormOk(ADMIN_AGENT_PATH + "/force-publish",
                agentVersionIdentityQuery(null, name, "1.0.0"));
        JsonNode visible = getJsonOk(AGENT_CLIENT_PATH, identity,
                AuthIdentity.CLIENT_READ_ONLY).get("data");
        assertEquals(name, visible.get("agentName").asText());
        assertAgentSearchCount(name, 1);
        Query privateScope = scopeQuery("agentName", name, "PRIVATE");
        assertEquals(403, putRaw(ADMIN_AGENT_PATH + "/scope", privateScope,
                AuthIdentity.CLIENT_READ_ONLY).code());
        assertEquals(403, putRaw(ADMIN_AGENT_PATH + "/scope", privateScope,
                AuthIdentity.CLIENT_READ_WRITE).code());
        assertEquals(403, getRaw(AGENT_CLIENT_PATH, identity,
                AuthIdentity.CLIENT_NO_PERMISSION).code());
        assertError(getRaw(AGENT_CLIENT_PATH, agentIdentityQuery("other-namespace", name),
                AuthIdentity.CLIENT_READ_ONLY),
                404, ErrorCode.RESOURCE_NOT_FOUND, "not found");
        putFormOk(ADMIN_AGENT_PATH + "/scope", privateScope);
        assertError(getRaw(AGENT_CLIENT_PATH, identity,
                AuthIdentity.CLIENT_READ_ONLY),
                404, ErrorCode.RESOURCE_NOT_FOUND, "not found");
        assertAgentSearchCount(name, 0);
        assertEquals(name, getJsonOk(AGENT_CLIENT_PATH, identity, AuthIdentity.ADMIN)
                .get("data").get("agentName").asText());
        assertGrantAndRevoke("agent", name, AGENT_CLIENT_PATH, identity, AuthIdentity.ADMIN);
        putFormOk(ADMIN_AGENT_PATH + "/scope", scopeQuery("agentName", name, "PUBLIC"));
        assertEquals(visible.get("contentDigest"), getJsonOk(AGENT_CLIENT_PATH, identity,
                AuthIdentity.CLIENT_READ_ONLY).get("data").get("contentDigest"));
        assertAgentSearchCount(name, 1);
    }

    @Test
    public void testMcpClientDefaultPublicAndPrivateGrantRoundTrip() throws Exception {
        assumeTrue(AUTH_ENABLED, "Visibility isolation requires auth");
        String name = randomAiName("mcp-visibility");
        String id = postFormOk(MCP_CLIENT_PATH,
                mcpServerForm(name, "1.0.0", "visibility", "echo", "resource"))
                .get("data").asText();
        addCleanup(() -> deleteMcpServerQuietly(name, id));
        Query identity = Query.newInstance().addParam("mcpName", name);
        JsonNode metadata = getJsonOk(ADMIN_MCP_PATH + "/version",
                mcpLifecycleVersionQuery(name, "1.0.0")).get("data");
        assertEquals("PUBLIC", metadata.get("scope").asText());
        assertEquals(name, getJsonOk(MCP_CLIENT_PATH, identity, AuthIdentity.CLIENT_READ_ONLY)
                .get("data").get("name").asText());
        Query privateScope = scopeQuery("mcpName", name, "PRIVATE");
        assertEquals(403, putRaw(ADMIN_MCP_PATH + "/scope", privateScope,
                AuthIdentity.CLIENT_READ_ONLY).code());
        assertEquals(403, getRaw(MCP_CLIENT_PATH, identity,
                AuthIdentity.CLIENT_NO_PERMISSION).code());
        putFormOk(ADMIN_MCP_PATH + "/scope", privateScope);
        assertEquals(404, getRaw(MCP_CLIENT_PATH, identity, AuthIdentity.CLIENT_READ_ONLY).code());
        assertGrantAndRevoke("mcp", name, MCP_CLIENT_PATH, identity,
                AuthIdentity.CLIENT_READ_WRITE);
        putFormOk(ADMIN_MCP_PATH + "/scope", scopeQuery("mcpName", name, "PUBLIC"));
        assertEquals(name, getJsonOk(MCP_CLIENT_PATH, identity, AuthIdentity.CLIENT_READ_ONLY)
                .get("data").get("name").asText());
    }

    private void assertAgentSearchCount(String name, int expected) throws Exception {
        Query query = Query.newInstance().addParam("agentNameContains", name)
                .addParam("pageNo", "1").addParam("pageSize", "10");
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
        int actual;
        do {
            actual = getJsonOk(AGENT_CLIENT_PATH + "/search", query,
                    AuthIdentity.CLIENT_READ_ONLY).get("data").get("totalCount").asInt();
            if (actual == expected) {
                return;
            }
            Thread.sleep(100);
        } while (System.nanoTime() < deadline);
        assertEquals(expected, actual, "Search must converge after scope change");
    }

    private void assertGrantAndRevoke(String type, String name, String readPath, Query identity,
            AuthIdentity owner) throws Exception {
        Query grant = Query.newInstance().addParam("namespaceId", DEFAULT_NAMESPACE)
                .addParam("resourceType", type).addParam("resourceName", name)
                .addParam("username", identityUsername(AuthIdentity.CLIENT_READ_ONLY))
                .addParam("action", "r");
        assertEquals(200, postRaw(GRANT_PATH, grant, owner).code());
        addCleanup(() -> deleteRaw(GRANT_PATH, grant, owner));
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
        while (getRaw(readPath, identity, AuthIdentity.CLIENT_READ_ONLY).code() != 200
                && System.nanoTime() < deadline) {
            Thread.sleep(100);
        }
        getJsonOk(readPath, identity, AuthIdentity.CLIENT_READ_ONLY);
        assertEquals(200, deleteRaw(GRANT_PATH, grant, owner).code());
        deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
        while (getRaw(readPath, identity, AuthIdentity.CLIENT_READ_ONLY).code() != 404
                && System.nanoTime() < deadline) {
            Thread.sleep(100);
        }
        assertEquals(404, getRaw(readPath, identity, AuthIdentity.CLIENT_READ_ONLY).code());
    }

    private Query scopeQuery(String nameKey, String name, String scope) {
        return Query.newInstance().addParam(nameKey, name).addParam("scope", scope);
    }
}
