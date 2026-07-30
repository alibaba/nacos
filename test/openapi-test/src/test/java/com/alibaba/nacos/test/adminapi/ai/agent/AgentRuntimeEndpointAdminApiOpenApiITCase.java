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

package com.alibaba.nacos.test.adminapi.ai.agent;

import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.test.adminapi.ai.AiAdminApiBaseITCase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for Agent Runtime Endpoint Snapshot Admin API.
 *
 * <p>Scenario coverage:
 * <ul>
 *     <li>Expected capability: runtime state can be inspected without an Agent definition and a
 *     missing Naming projection returns a successful, complete empty snapshot.</li>
 *     <li>Boundary/validation: omitted or blank namespace uses {@code public}; explicit namespace
 *     and optional exact Version are reflected in the snapshot; protocol and Version validation
 *     return HTTP 400.</li>
 *     <li>Exception/error handling: missing required identity/protocol fields produce controlled
 *     v3 {@code Result} errors rather than HTTP 500. The populated Publisher path is exercised
 *     across Client registration and Admin/Console reads by
 *     {@code AgentEndpointClientOpenApiITCase}; merge conflicts and capacity limits remain
 *     covered by Runtime Registry unit tests.</li>
 * </ul>
 *
 * @author xiweng.yy
 */
public class AgentRuntimeEndpointAdminApiOpenApiITCase extends AiAdminApiBaseITCase {

    @Test
    public void testMissingDefinitionReturnsDefaultNamespaceEmptySnapshot() throws Exception {
        String agentName = randomAiName("agent-runtime-empty");
        JsonNode snapshot = getJsonOk(ADMIN_AGENT_RUNTIME_ENDPOINTS_PATH,
                Query.newInstance().addParam("agentName", agentName)
                        .addParam("protocol", "a2a")).get("data");
        assertEmptySnapshot(snapshot, DEFAULT_NAMESPACE, agentName, "a2a", null);

        JsonNode blankNamespace = getJsonOk(ADMIN_AGENT_RUNTIME_ENDPOINTS_PATH,
                Query.newInstance().addParam("namespaceId", "").addParam("agentName", agentName)
                        .addParam("protocol", "a2a")).get("data");
        assertEmptySnapshot(blankNamespace, DEFAULT_NAMESPACE, agentName, "a2a", null);
    }

    @Test
    public void testExplicitNamespaceAndVersionReturnEmptySnapshot() throws Exception {
        String namespaceId = "oit_runtime_" + UUID.randomUUID().toString().substring(0, 8);
        String agentName = randomAiName("agent-runtime-version");
        JsonNode snapshot = getJsonOk(ADMIN_AGENT_RUNTIME_ENDPOINTS_PATH,
                Query.newInstance().addParam("namespaceId", namespaceId)
                        .addParam("agentName", agentName).addParam("protocol", "a2a")
                        .addParam("version", "1.2.3")).get("data");
        assertEmptySnapshot(snapshot, namespaceId, agentName, "a2a", "1.2.3");
    }

    @Test
    public void testRuntimeSnapshotValidationErrors() throws Exception {
        assertError(getRaw(ADMIN_AGENT_RUNTIME_ENDPOINTS_PATH,
                Query.newInstance().addParam("protocol", "a2a")), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "agentName");
        assertError(getRaw(ADMIN_AGENT_RUNTIME_ENDPOINTS_PATH,
                Query.newInstance().addParam("agentName", randomAiName("missing-protocol"))),
                400, ErrorCode.PARAMETER_VALIDATE_ERROR, "protocol");
        assertError(getRaw(ADMIN_AGENT_RUNTIME_ENDPOINTS_PATH,
                Query.newInstance().addParam("agentName", randomAiName("invalid-protocol"))
                        .addParam("protocol", "bad protocol")), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "protocol");
        assertError(getRaw(ADMIN_AGENT_RUNTIME_ENDPOINTS_PATH,
                Query.newInstance().addParam("agentName", randomAiName("invalid-version"))
                        .addParam("protocol", "a2a").addParam("version", "invalid")), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "version");
        assertError(getRaw(ADMIN_AGENT_RUNTIME_ENDPOINTS_PATH,
                Query.newInstance().addParam("namespaceId", "bad namespace")
                        .addParam("agentName", randomAiName("invalid-namespace"))
                        .addParam("protocol", "a2a")), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "namespace");
    }

    private void assertEmptySnapshot(JsonNode snapshot, String namespaceId, String agentName,
            String protocol, String version) {
        assertEquals(namespaceId, snapshot.get("namespaceId").asText(), snapshot.toString());
        assertEquals(agentName, snapshot.get("agentName").asText(), snapshot.toString());
        assertEquals(protocol, snapshot.get("protocol").asText(), snapshot.toString());
        if (null == version) {
            assertTrue(snapshot.get("version") == null || snapshot.get("version").isNull(),
                    snapshot.toString());
        } else {
            assertEquals(version, snapshot.get("version").asText(), snapshot.toString());
        }
        assertTrue(snapshot.get("items").isArray(), snapshot.toString());
        assertFalse(snapshot.get("items").elements().hasNext(), snapshot.toString());
    }
}
