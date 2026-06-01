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
import org.junit.jupiter.api.Test;

/**
 * Integration tests for the AI AgentSpec client Open API {@code GET /nacos/v3/client/ai/agentspecs}.
 *
 * <p>Scenario coverage:
 * <ul>
 *     <li>Expected capability: an online AgentSpec can be queried by latest, explicit version, and label with manifest
 *     content and resource files preserved.</li>
 *     <li>Boundary/validation: omitted namespaceId uses public; AgentSpec version resolution gives label priority over
 *     explicit version; unknown label falls back to latest; missing name is rejected with HTTP 400.</li>
 *     <li>Exception/error handling: absent AgentSpec and unknown explicit version return controlled not-found
 *     responses instead of HTTP 500.</li>
 * </ul>
 *
 * <p>Draft, update-draft, force-publish, label, and delete calls to {@code /nacos/v3/admin/ai/agentspecs} are helper
 * calls only; this class keeps its assertions focused on the runtime client query contract.
 *
 * @author xiweng.yy
 */
public class AgentSpecClientOpenApiITCase extends AgentSpecOpenApiBaseITCase {
    
    @Test
    public void testGetAgentSpecByLatestVersionAndLabel() throws Exception {
        String name = randomAgentSpecName("agentspec");
        publishAgentSpec(name, "1.0.0", null, "AgentSpec v1", "scenario-v1", "soul v1");
        addCleanup(() -> deleteAgentSpec(name));
        publishAgentSpec(name, "2.0.0", "1.0.0", "AgentSpec v2", "scenario-v2", "soul v2");
        updateAgentSpecLabels(name, "{\"stable\":\"1.0.0\",\"latest\":\"2.0.0\"}");
        
        JsonNode latest = getAgentSpec(Query.newInstance().addParam("name", name));
        assertAgentSpec(latest.get("data"), name, "2.0.0", "AgentSpec v2", "scenario-v2", "soul v2");
        
        JsonNode byVersion = getAgentSpec(Query.newInstance().addParam("name", name)
                .addParam("version", "1.0.0"));
        assertAgentSpec(byVersion.get("data"), name, "1.0.0", "AgentSpec v1", "scenario-v1", "soul v1");
        
        JsonNode byLabel = getAgentSpec(Query.newInstance().addParam("name", name)
                .addParam("label", "stable"));
        assertAgentSpec(byLabel.get("data"), name, "1.0.0", "AgentSpec v1", "scenario-v1", "soul v1");
        
        JsonNode labelWins = getAgentSpec(Query.newInstance().addParam("name", name)
                .addParam("version", "2.0.0").addParam("label", "stable"));
        assertAgentSpec(labelWins.get("data"), name, "1.0.0", "AgentSpec v1", "scenario-v1", "soul v1");
    }
    
    @Test
    public void testGetAgentSpecMissingNameReturnsBadRequest() throws Exception {
        assertError(getRaw(AGENT_SPEC_CLIENT_PATH + "?namespaceId=" + DEFAULT_NAMESPACE), 400,
                ErrorCode.PARAMETER_MISSING, "AgentSpec name is required");
    }
    
    @Test
    public void testGetAgentSpecUnknownResourceReturnsNotFoundResultBody() throws Exception {
        Query query = Query.newInstance().addParam("name", randomAgentSpecName("absent"));
        assertError(getRaw(AGENT_SPEC_CLIENT_PATH, query), 404, ErrorCode.RESOURCE_NOT_FOUND,
                "AgentSpec not found");
    }
    
    @Test
    public void testGetAgentSpecUnknownVersionAndLabelFallback() throws Exception {
        String name = randomAgentSpecName("missing");
        publishAgentSpec(name, "1.0.0", null, "Only AgentSpec", "only-scenario", "only soul");
        addCleanup(() -> deleteAgentSpec(name));
        
        assertError(getRaw(AGENT_SPEC_CLIENT_PATH,
                Query.newInstance().addParam("name", name).addParam("version", "9.9.9")),
                404, ErrorCode.RESOURCE_NOT_FOUND, "AgentSpec version not online");
        JsonNode unknownLabel = getAgentSpec(Query.newInstance().addParam("name", name)
                .addParam("label", "missing"));
        assertAgentSpec(unknownLabel.get("data"), name, "1.0.0", "Only AgentSpec", "only-scenario",
                "only soul");
    }
    
    private JsonNode getAgentSpec(Query query) throws Exception {
        return getJsonOk(AGENT_SPEC_CLIENT_PATH, query);
    }
}
