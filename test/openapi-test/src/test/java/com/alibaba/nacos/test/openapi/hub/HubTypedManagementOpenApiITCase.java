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

package com.alibaba.nacos.test.openapi.hub;

import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.test.openapi.client.ai.AgentSpecOpenApiBaseITCase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Deployed typed management boundaries and AgentSpec fixed-version success flow.
 * Requires an auth-enabled, Hub-enabled standalone server containing these changes.
 * Other resource success fixtures and remote Console delegation are tracked separately.
 */
public class HubTypedManagementOpenApiITCase extends AgentSpecOpenApiBaseITCase {

    private static final String HUB = "__nacos_ai_hub__";
    private static final String MANAGEMENT = nacosPath("/v3/admin/ai/hub/");

    @BeforeEach
    void requireHubBaseline() {
        assumeTrue(AUTH_ENABLED, "Typed Hub management requires the auth-enabled baseline");
    }

    @Test
    void conflictingNamespacesNeverReachIdentityOnlySharedReads() throws Exception {
        for (String api : new String[] {"admin", "client"}) {
            for (String[] resource : new String[][] {{"mcp", "mcpName"}, {"agents", "agentName"},
                {"prompt", "promptKey"}, {"agentspecs", "agentSpecName"}}) {
                String key = "client".equals(api) && "agentspecs".equals(resource[0])
                    ? "name" : resource[1];
                String route = resource[0] + ("admin".equals(api) && "prompt".equals(resource[0])
                    ? "/governance" : "");
                for (String namespaces : new String[] {HUB + "&namespaceId=ordinary-source",
                    "ordinary-source&namespaceId=" + HUB}) {
                    assertError(getRaw(nacosPath("/v3/" + api + "/ai/" + route)
                        + "?namespaceId=" + namespaces + "&" + key + "=1",
                        AuthIdentity.CLIENT_NO_PERMISSION),
                        400, ErrorCode.PARAMETER_VALIDATE_ERROR, "");
                }
            }
        }
    }

    @Test
    void malformedTypedManagementAndDeniedGovernanceAreControlled() throws Exception {
        for (String type : new String[] {"mcp", "agent", "prompt", "agentspec"}) {
            for (String operation : new String[] {"share", "adjust", "unshare", "force-unshare"}) {
                assertError(postRaw(MANAGEMENT + type + "/" + operation, Query.newInstance()),
                    400, ErrorCode.PARAMETER_VALIDATE_ERROR, "");
            }
            assertError(postRaw(MANAGEMENT + type + "/share", Query.newInstance()
                    .addParam("sourceNamespaceId", HUB).addParam("sourceName", "unused")
                    .addParam("sourceVersion", "1.0.0")), 400, ErrorCode.ILLEGAL_NAMESPACE, "");
            assertEquals(403, getRaw(MANAGEMENT + type + "/governance-state",
                Query.newInstance().addParam("hubEntryId", "1"), AuthIdentity.CLIENT_NO_PERMISSION).code());
        }
    }

    @Test
    void agentSpecPublicationUsesOriginalClientRouteAndRevokesOnSourcePrivate() throws Exception {
        String name = randomAgentSpecName("typed-hub");
        addCleanup(() -> deleteAgentSpec(name));
        publishAgentSpec(name, "1.0.0", null, "Shared v1", "scenario-v1", "soul v1");
        putFormOk(AGENT_SPEC_ADMIN_PATH + "/scope", Map.of("agentSpecName", name, "scope", "PUBLIC"));
        String management = MANAGEMENT + "agentspec";
        Map<String, String> share = Map.of("sourceNamespaceId", DEFAULT_NAMESPACE,
            "sourceName", name, "sourceVersion", "1.0.0");
        JsonNode first = postFormOk(management + "/share", share).get("data");
        String id = first.get("hubEntryId").asText();
        addCleanup(() -> {
            JsonNode state = getJsonOk(management + "/governance-state",
                Query.newInstance().addParam("hubEntryId", id)).get("data");
            if (state.path("active").asBoolean()) {
                postFormOk(management + "/force-unshare", Map.of("hubEntryId", id,
                    "expectedMetaVersion", state.get("metaVersion").asText(), "reason", "typed Hub IT cleanup"));
            }
        });
        assertTrue(first.get("hubEntryId").isTextual());
        JsonNode retry = postFormOk(management + "/share", share).get("data");
        assertEquals("ALREADY_SHARED", retry.get("outcome").asText());
        assertEquals(first.get("metaVersion"), retry.get("metaVersion"));

        Query shared = Query.newInstance().addParam("namespaceId", HUB).addParam("name", id);
        JsonNode content = getJsonOk(AGENT_SPEC_CLIENT_PATH, shared, AuthIdentity.CLIENT_NO_PERMISSION).get("data");
        Query sharedMeta = Query.newInstance().addParam("namespaceId", HUB)
            .addParam("agentSpecName", id);
        JsonNode deniedSource = getJsonOk(AGENT_SPEC_ADMIN_PATH, sharedMeta,
            AuthIdentity.CLIENT_NO_PERMISSION).get("data").path("hub");
        assertFalse(deniedSource.path("sourceReadable").asBoolean());
        assertFalse(deniedSource.hasNonNull("sourceNamespaceId"));
        JsonNode sourceAllowed = getJsonOk(AGENT_SPEC_ADMIN_PATH, sharedMeta).get("data").path("hub");
        assertTrue(sourceAllowed.path("sourceReadable").asBoolean());
        assertEquals(DEFAULT_NAMESPACE, sourceAllowed.path("sourceNamespaceId").asText());
        assertEquals(name, sourceAllowed.path("sourceName").asText());
        assertEquals("1.0.0", sourceAllowed.path("version").asText());
        assertEquals(HUB, content.get("namespaceId").asText());
        assertEquals(name, content.get("name").asText());
        assertEquals("Shared v1", content.get("description").asText());
        assertTrue(sourceAllowed.path("updateTime").isIntegralNumber(),
            "Shared discovery metadata exposes the Hub entry modification time in milliseconds");
        assertEquals("soul v1", content.get("resource").get("config_SOUL__md").get("content").asText());
        assertError(getRaw(AGENT_SPEC_CLIENT_PATH, Query.newInstance().addParam("namespaceId", HUB)
                .addParam("name", id).addParam("version", "9.9.9"), AuthIdentity.CLIENT_NO_PERMISSION),
            404, ErrorCode.RESOURCE_NOT_FOUND, "");

        putFormOk(AGENT_SPEC_ADMIN_PATH + "/scope", Map.of("agentSpecName", name, "scope", "PRIVATE"));
        assertError(getRaw(AGENT_SPEC_CLIENT_PATH, shared, AuthIdentity.CLIENT_NO_PERMISSION),
            404, ErrorCode.RESOURCE_NOT_FOUND, "");
        JsonNode state = getJsonOk(management + "/source-share", Query.newInstance()
            .addParam("sourceNamespaceId", DEFAULT_NAMESPACE).addParam("sourceName", name)).get("data");
        assertFalse(state.get("active").asBoolean());
        JsonNode ordinary = getJsonOk(AGENT_SPEC_ADMIN_PATH,
            Query.newInstance().addParam("agentSpecName", name)).get("data");
        assertEquals("1.0.0", ordinary.get("labels").get("latest").asText());
    }
}

