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

import com.alibaba.nacos.api.ai.utils.AgentDiscoveryCanonicalizer;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * External migration admission contract; select with nacos.agent.migration.gate=blocked.
 *
 * @author Nacos
 */
@EnabledIfSystemProperty(named = "nacos.agent.migration.gate", matches = "blocked")
public class AgentMigrationClientOpenApiITCase extends AgentClientOpenApiBaseITCase {

    @Test
    public void testUniformGatePreservesManagementAndDoesNotCreateHttpOwner() throws Exception {
        String managed = randomAiName("migration-independent");
        publishAgent(managed, "1.0.0");
        assertEquals(managed, getJsonOk(ADMIN_AGENT_PATH,
                agentIdentityQuery(DEFAULT_NAMESPACE, managed)).at("/data/agent/agentName").asText());
        String unprojected = randomAiName("migration-unprojected");
        for (String agentName : new String[] {managed, unprojected}) {
            String clientId = randomHttpClientId();
            assertMigration(getWithClientId(AGENT_SEARCH_PATH,
                    Query.newInstance().addParam("agentNameContains", agentName), clientId));
            assertMigration(getWithClientId(AGENT_CLIENT_PATH,
                    Query.newInstance().addParam("agentName", agentName), clientId));
            Map<String, String> publication = agentForm(
                    agentInitialDraftRequest(null, agentName, "2.0.0"));
            assertMigration(postFormRaw(AGENT_CLIENT_PATH, publication));
            Map<String, String> registration = new LinkedHashMap<>();
            registration.put("agentName", agentName);
            registration.put("protocol", "a2a");
            registration.put("runtimeVersion", "1.0.0");
            registration.put("endpoints", "[{\"uri\":\"http://127.0.0.1:18080/a2a\","
                    + "\"transport\":\"HTTP+JSON\"}]");
            assertMigration(postEndpointForm(clientId, "AI", registration));
            Map<String, Object> watch = new LinkedHashMap<>();
            watch.put("clientWatchId", "watch");
            watch.put("discoveryRequest", Collections.singletonMap("reference",
                    Collections.singletonMap("agentName", agentName)));
            watch.put("materializedFingerprint", AgentDiscoveryCanonicalizer.ALGORITHM_ID
                    + ":" + "0".repeat(64));
            Map<String, String> form = new LinkedHashMap<>();
            form.put("generation", "1");
            form.put("timeoutMillis", "1000");
            form.put("watches", JacksonUtils.toJson(Collections.singletonList(watch)));
            assertMigration(postWatchForm(clientId, "AI", form));
            assertError(heartbeat(clientId, "AI"), 404, ErrorCode.HTTP_CLIENT_NOT_FOUND,
                    "HTTP Client");
            assertEquals(200, deleteEndpointForm(clientId, "AI", Query.newInstance()
                    .addParam("agentName", agentName).addParam("protocol", "a2a")).code());
        }
        JsonNode versions = getJsonOk(ADMIN_AGENT_PATH + "/versions",
                agentIdentityQuery(DEFAULT_NAMESPACE, managed));
        assertEquals(1, versions.at("/data/totalCount").asInt(), versions.toString());
        assertEquals(404, getRaw(ADMIN_AGENT_PATH,
                agentIdentityQuery(DEFAULT_NAMESPACE, unprojected)).code());
    }

    private void assertMigration(HttpResponse response) throws Exception {
        assertError(response, 409, ErrorCode.AGENT_MIGRATION_IN_PROGRESS, "historical A2A");
    }
}
