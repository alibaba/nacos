/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.test.adminapi.ai.a2a;

import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.test.adminapi.ai.AiAdminApiBaseITCase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for A2A admin APIs ({@code /nacos/v3/admin/ai/a2a}).
 *
 * <p>Scenario coverage:
 * <ul>
 *     <li>Expected capability: legacy and v1 agent cards can be registered, normalized, queried by version/latest,
 *     updated to a new latest version, listed by blur search, and enumerated by version.</li>
 *     <li>Boundary/validation: namespace defaults to public; register defaults registrationType to URL; update allows
 *     omitted registrationType; invalid search, missing agentName, invalid registrationType, empty card, malformed
 *     JSON, and incomplete legacy/v1 endpoint definitions are rejected with HTTP 400.</li>
 *     <li>Exception/error handling: absent agents return a controlled not-found result and delete is tolerant of
 *     absent resources.</li>
 * </ul>
 *
 * @author xiweng.yy
 */
public class A2aAdminApiOpenApiITCase extends AiAdminApiBaseITCase {
    
    private static final String REGISTRATION_TYPE_URL = "URL";
    
    private static final String SEARCH_BLUR = "blur";
    
    @Test
    public void testRegisterLegacyAgentCardAndGetAgentCardSuccess() throws Exception {
        String agentName = "openapi-legacy-" + UUID.randomUUID();
        String version = "1.2.0";
        String legacyAgentCard = buildLegacyAgentCard(agentName, version);
        try {
            JsonNode register = registerAgent(agentName, version, REGISTRATION_TYPE_URL, legacyAgentCard);
            assertEquals(ErrorCode.SUCCESS.getCode(), register.get("code").asInt(), register.toString());
            assertEquals("ok", register.get("data").asText());
            
            JsonNode queryResult = getAgentCard(agentName, version, REGISTRATION_TYPE_URL);
            assertEquals(ErrorCode.SUCCESS.getCode(), queryResult.get("code").asInt(), queryResult.toString());
            JsonNode data = queryResult.get("data");
            assertNotNull(data);
            assertEquals(agentName, data.get("name").asText());
            assertEquals(version, data.get("version").asText());
            assertEquals("JSONRPC", data.get("preferredTransport").asText());
            assertEquals("1.0", data.get("protocolVersion").asText());
            JsonNode supportedInterfaces = data.get("supportedInterfaces");
            assertTrue(null != supportedInterfaces && supportedInterfaces.isArray() && supportedInterfaces.size() >= 1,
                    "supportedInterfaces should be generated for legacy card");
            assertEquals("https://example.com/" + agentName + "/jsonrpc", supportedInterfaces.get(0).get("url").asText());
            assertEquals("JSONRPC", supportedInterfaces.get(0).get("protocolBinding").asText());
            assertEquals("1.0", supportedInterfaces.get(0).get("protocolVersion").asText());
        } finally {
            deleteAgentQuietly(agentName, version, REGISTRATION_TYPE_URL);
        }
    }
    
    @Test
    public void testRegisterV1AgentCardAndGetAgentCardSuccess() throws Exception {
        String agentName = "openapi-v1-" + UUID.randomUUID();
        String version = "2.0.0";
        String v1AgentCard = buildV1AgentCard(agentName, version, "1.0");
        try {
            JsonNode register = registerAgent(agentName, version, REGISTRATION_TYPE_URL, v1AgentCard);
            assertEquals(ErrorCode.SUCCESS.getCode(), register.get("code").asInt(), register.toString());
            assertEquals("ok", register.get("data").asText());
            
            JsonNode queryResult = getAgentCard(agentName, version, REGISTRATION_TYPE_URL);
            assertEquals(ErrorCode.SUCCESS.getCode(), queryResult.get("code").asInt(), queryResult.toString());
            JsonNode data = queryResult.get("data");
            assertNotNull(data);
            assertEquals(agentName, data.get("name").asText());
            assertEquals(version, data.get("version").asText());
            JsonNode supportedInterfaces = data.get("supportedInterfaces");
            assertEquals(2, supportedInterfaces.size());
            assertEquals("1.0", supportedInterfaces.get(0).get("protocolVersion").asText());
            assertEquals("JSONRPC", supportedInterfaces.get(0).get("protocolBinding").asText());
            assertEquals("https://example.com/" + agentName + "/jsonrpc", supportedInterfaces.get(0).get("url").asText());
            // Legacy mirror fields should also be available for compatibility.
            assertEquals("1.0", data.get("protocolVersion").asText());
            assertEquals("JSONRPC", data.get("preferredTransport").asText());
            assertEquals("https://example.com/" + agentName + "/jsonrpc", data.get("url").asText());
            JsonNode additionalInterfaces = data.get("additionalInterfaces");
            assertTrue(null != additionalInterfaces && additionalInterfaces.isArray() && additionalInterfaces.size() >= 1,
                    "additionalInterfaces should be generated for v1 card");
            assertEquals("GRPC", additionalInterfaces.get(0).get("transport").asText());
        } finally {
            deleteAgentQuietly(agentName, version, REGISTRATION_TYPE_URL);
        }
    }
    
    @Test
    public void testUpdateAgentCardAndListApisSuccess() throws Exception {
        String agentName = "openapi-update-" + UUID.randomUUID();
        String v1 = "3.0.0";
        String v2 = "3.1.0";
        try {
            JsonNode register = registerAgent(agentName, v1, null, buildV1AgentCard(agentName, v1, "1.0"));
            assertEquals(ErrorCode.SUCCESS.getCode(), register.get("code").asInt(), register.toString());
            
            JsonNode updateResult = updateAgentCard(agentName, v2, REGISTRATION_TYPE_URL,
                    buildV1AgentCard(agentName, v2, "1.0"), true);
            assertEquals(ErrorCode.SUCCESS.getCode(), updateResult.get("code").asInt(), updateResult.toString());
            assertEquals("ok", updateResult.get("data").asText());
            
            JsonNode latest = getAgentCard(agentName, null, REGISTRATION_TYPE_URL);
            assertEquals(ErrorCode.SUCCESS.getCode(), latest.get("code").asInt(), latest.toString());
            assertEquals(v2, latest.get("data").get("version").asText());
            
            JsonNode versionList = listAgentVersions(agentName, REGISTRATION_TYPE_URL);
            assertEquals(ErrorCode.SUCCESS.getCode(), versionList.get("code").asInt(), versionList.toString());
            assertTrue(versionList.get("data").isArray() && versionList.get("data").size() >= 2);
            
            JsonNode listResult = listAgents(agentName, SEARCH_BLUR, 1, 10);
            assertEquals(ErrorCode.SUCCESS.getCode(), listResult.get("code").asInt(), listResult.toString());
            JsonNode pageItems = listResult.get("data").get("pageItems");
            assertTrue(pageItems.isArray() && pageItems.size() >= 1);
            assertEquals(agentName, pageItems.get(0).get("name").asText());
        } finally {
            deleteAgentQuietly(agentName, v1, REGISTRATION_TYPE_URL);
            deleteAgentQuietly(agentName, v2, REGISTRATION_TYPE_URL);
        }
    }
    
    @Test
    public void testGetListAndVersionListValidationAndNotFoundErrors() throws Exception {
        assertError(getRaw(ADMIN_A2A_PATH, Query.newInstance().addParam("namespaceId", DEFAULT_NAMESPACE)),
                400, ErrorCode.PARAMETER_MISSING, "name");
        assertError(getRaw(ADMIN_A2A_LIST_PATH, Query.newInstance().addParam("search", "invalid")
                .addParam("pageNo", "1").addParam("pageSize", "10")), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "search");
        assertError(getRaw(ADMIN_A2A_VERSION_LIST_PATH, Query.newInstance()
                .addParam("namespaceId", DEFAULT_NAMESPACE)), 400, ErrorCode.PARAMETER_MISSING,
                "name");

        String absentAgent = "openapi-absent-" + UUID.randomUUID();
        assertError(getRaw(ADMIN_A2A_PATH, Query.newInstance().addParam("agentName", absentAgent)
                .addParam("namespaceId", DEFAULT_NAMESPACE)), 404, ErrorCode.AGENT_NOT_FOUND,
                "Agent not found");
    }

    @Test
    public void testRegisterInvalidAgentCardReturnsBadRequest() throws Exception {
        String agentName = "openapi-invalid-" + UUID.randomUUID();
        String version = "1.0.0";
        Map<String, String> form = buildAgentCardForm(agentName, version, REGISTRATION_TYPE_URL,
                "{\"name\":\"" + agentName + "\",\"version\":\"" + version + "\"}");
        assertBadRequestContains(form, "agentCard.supportedInterfaces");
    }
    
    @Test
    public void testRegisterLegacyAgentCardMissingProtocolVersionReturnsBadRequest() throws Exception {
        String agentName = "openapi-legacy-missing-" + UUID.randomUUID();
        String version = "1.0.1";
        String invalidLegacy = String.format("{\"name\":\"%s\",\"version\":\"%s\","
                        + "\"preferredTransport\":\"JSONRPC\",\"url\":\"https://example.com/%s/jsonrpc\"}",
                agentName, version, agentName);
        Map<String, String> form = buildAgentCardForm(agentName, version, REGISTRATION_TYPE_URL, invalidLegacy);
        assertBadRequestContains(form, "agentCard.supportedInterfaces");
    }
    
    @Test
    public void testRegisterV1AgentCardMissingProtocolVersionReturnsBadRequest() throws Exception {
        String agentName = "openapi-v1-missing-pv-" + UUID.randomUUID();
        String version = "1.0.2";
        String invalidV1 = String.format("{\"name\":\"%s\",\"version\":\"%s\","
                        + "\"supportedInterfaces\":[{\"url\":\"https://example.com/%s/jsonrpc\","
                        + "\"protocolBinding\":\"JSONRPC\"}]}", agentName, version, agentName);
        Map<String, String> form = buildAgentCardForm(agentName, version, REGISTRATION_TYPE_URL, invalidV1);
        assertBadRequestContains(form, "agentCard.supportedInterfaces");
    }
    
    @Test
    public void testRegisterInvalidRegistrationTypeReturnsBadRequest() throws Exception {
        String agentName = "openapi-invalid-reg-" + UUID.randomUUID();
        String version = "1.0.3";
        Map<String, String> form = buildAgentCardForm(agentName, version, "INVALID",
                buildV1AgentCard(agentName, version, "1.0"));
        assertBadRequestContains(form, "registrationType");
    }
    
    @Test
    public void testRegisterMalformedAgentCardJsonReturnsBadRequest() throws Exception {
        String agentName = "openapi-invalid-json-" + UUID.randomUUID();
        String version = "1.0.4";
        Map<String, String> form = buildAgentCardForm(agentName, version, REGISTRATION_TYPE_URL, "{");
        assertBadRequestContains(form, "Can't be parsed");
    }
    
    @Test
    public void testRegisterEmptyAgentCardReturnsBadRequest() throws Exception {
        String agentName = "openapi-empty-card-" + UUID.randomUUID();
        String version = "1.0.5";
        Map<String, String> form = buildAgentCardForm(agentName, version, REGISTRATION_TYPE_URL, "");
        assertBadRequestContains(form, "agentCard");
    }
    
    private JsonNode registerAgent(String agentName, String version, String registrationType, String agentCard)
            throws Exception {
        Map<String, String> form = buildAgentCardForm(agentName, version, registrationType, agentCard);
        if (null == registrationType) {
            form.remove("registrationType");
        }
        return postFormOk(ADMIN_A2A_PATH, form);
    }
    
    private JsonNode updateAgentCard(String agentName, String version, String registrationType, String agentCard,
            boolean setAsLatest) throws Exception {
        Map<String, String> form = buildAgentCardForm(agentName, version, registrationType, agentCard);
        form.put("setAsLatest", String.valueOf(setAsLatest));
        return putFormOk(ADMIN_A2A_PATH, form);
    }
    
    private JsonNode getAgentCard(String agentName, String version, String registrationType) throws Exception {
        Query query = Query.newInstance().addParam("agentName", agentName).addParam("namespaceId", DEFAULT_NAMESPACE)
                .addParam("registrationType", registrationType);
        if (null != version) {
            query.addParam("version", version);
        }
        return getJsonOk(ADMIN_A2A_PATH, query);
    }
    
    private JsonNode listAgentVersions(String agentName, String registrationType) throws Exception {
        Query query = Query.newInstance().addParam("agentName", agentName).addParam("namespaceId", DEFAULT_NAMESPACE)
                .addParam("registrationType", registrationType);
        return getJsonOk(ADMIN_A2A_VERSION_LIST_PATH, query);
    }
    
    private JsonNode listAgents(String agentName, String search, int pageNo, int pageSize) throws Exception {
        Query query = Query.newInstance().addParam("namespaceId", DEFAULT_NAMESPACE).addParam("agentName", agentName)
                .addParam("search", search).addParam("pageNo", String.valueOf(pageNo))
                .addParam("pageSize", String.valueOf(pageSize));
        return getJsonOk(ADMIN_A2A_LIST_PATH, query);
    }
    
    private void assertBadRequestContains(Map<String, String> form, String expectedText) throws Exception {
        HttpResponse response = postRaw(ADMIN_A2A_PATH, queryFrom(form));
        assertEquals(400, response.code(), response.body());
        assertTrue(response.body().contains(expectedText),
                "expected message should contain `" + expectedText + "`, actual=" + response.body());
    }
}
