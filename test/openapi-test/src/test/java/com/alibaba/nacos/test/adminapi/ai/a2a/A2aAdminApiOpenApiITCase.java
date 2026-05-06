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
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.alibaba.nacos.common.http.client.request.DefaultHttpClientRequest;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for A2A admin APIs ({@code /nacos/v3/admin/ai/a2a}).
 *
 * <p>These tests run against an already started standalone server (default
 * {@code 127.0.0.1:8848}) and assume auth is disabled in the local environment.
 */
public class A2aAdminApiOpenApiITCase {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(A2aAdminApiOpenApiITCase.class);
    
    private static final String NACOS_HOST = System.getProperty("nacos.host", "127.0.0.1");
    
    private static final String NACOS_PORT = System.getProperty("nacos.port", "8848");
    
    private static final String BASE_URL = "http://" + NACOS_HOST + ":" + NACOS_PORT;
    
    private static final String A2A_ADMIN_PATH = "/nacos/v3/admin/ai/a2a";
    
    private static final String A2A_ADMIN_LIST_PATH = A2A_ADMIN_PATH + "/list";
    
    private static final String A2A_ADMIN_VERSION_LIST_PATH = A2A_ADMIN_PATH + "/version/list";
    
    private static final String DEFAULT_NAMESPACE = "public";
    
    private static final String REGISTRATION_TYPE_URL = "URL";
    
    private static final String SEARCH_BLUR = "blur";
    
    private CloseableHttpClient httpClient;
    
    private NacosRestTemplate nacosRestTemplate;
    
    @BeforeEach
    public void setUp() throws Exception {
        httpClient = HttpClientBuilder.create().build();
        nacosRestTemplate = new NacosRestTemplate(LOGGER, new DefaultHttpClientRequest(httpClient, RequestConfig.DEFAULT));
    }
    
    @AfterEach
    public void tearDown() throws Exception {
        nacosRestTemplate.close();
    }
    
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
            deleteAgent(agentName, version, REGISTRATION_TYPE_URL);
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
            deleteAgent(agentName, version, REGISTRATION_TYPE_URL);
        }
    }
    
    @Test
    public void testUpdateAgentCardAndListApisSuccess() throws Exception {
        String agentName = "openapi-update-" + UUID.randomUUID();
        String v1 = "3.0.0";
        String v2 = "3.1.0";
        try {
            JsonNode register = registerAgent(agentName, v1, REGISTRATION_TYPE_URL, buildV1AgentCard(agentName, v1, "1.0"));
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
            deleteAgent(agentName, v1, REGISTRATION_TYPE_URL);
            deleteAgent(agentName, v2, REGISTRATION_TYPE_URL);
        }
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
        String url = BASE_URL + A2A_ADMIN_PATH;
        Map<String, String> form = buildAgentCardForm(agentName, version, registrationType, agentCard);
        HttpRestResult<String> restResult = nacosRestTemplate.postForm(url, Header.EMPTY, form, String.class);
        assertTrue(restResult.ok(), "register HTTP status should be 2xx, body=" + restResult.getData());
        return JacksonUtils.toObj(restResult.getData());
    }
    
    private JsonNode updateAgentCard(String agentName, String version, String registrationType, String agentCard,
            boolean setAsLatest) throws Exception {
        String url = BASE_URL + A2A_ADMIN_PATH;
        Map<String, String> form = buildAgentCardForm(agentName, version, registrationType, agentCard);
        form.put("setAsLatest", String.valueOf(setAsLatest));
        HttpRestResult<String> restResult = nacosRestTemplate.putForm(url, Header.EMPTY, form, String.class);
        assertTrue(restResult.ok(), "update HTTP status should be 2xx, body=" + restResult.getData());
        return JacksonUtils.toObj(restResult.getData());
    }
    
    private JsonNode getAgentCard(String agentName, String version, String registrationType) throws Exception {
        String url = BASE_URL + A2A_ADMIN_PATH;
        Query query = Query.newInstance().addParam("agentName", agentName).addParam("namespaceId", DEFAULT_NAMESPACE)
                .addParam("registrationType", registrationType);
        if (null != version) {
            query.addParam("version", version);
        }
        HttpRestResult<String> restResult = nacosRestTemplate.get(url, Header.EMPTY, query, String.class);
        assertTrue(restResult.ok(), "get HTTP status should be 2xx, body=" + restResult.getData());
        return JacksonUtils.toObj(restResult.getData());
    }
    
    private JsonNode listAgentVersions(String agentName, String registrationType) throws Exception {
        String url = BASE_URL + A2A_ADMIN_VERSION_LIST_PATH;
        Query query = Query.newInstance().addParam("agentName", agentName).addParam("namespaceId", DEFAULT_NAMESPACE)
                .addParam("registrationType", registrationType);
        HttpRestResult<String> restResult = nacosRestTemplate.get(url, Header.EMPTY, query, String.class);
        assertTrue(restResult.ok(), "version list HTTP status should be 2xx, body=" + restResult.getData());
        return JacksonUtils.toObj(restResult.getData());
    }
    
    private JsonNode listAgents(String agentName, String search, int pageNo, int pageSize) throws Exception {
        String url = BASE_URL + A2A_ADMIN_LIST_PATH;
        Query query = Query.newInstance().addParam("namespaceId", DEFAULT_NAMESPACE).addParam("agentName", agentName)
                .addParam("search", search).addParam("pageNo", String.valueOf(pageNo))
                .addParam("pageSize", String.valueOf(pageSize));
        HttpRestResult<String> restResult = nacosRestTemplate.get(url, Header.EMPTY, query, String.class);
        assertTrue(restResult.ok(), "list HTTP status should be 2xx, body=" + restResult.getData());
        return JacksonUtils.toObj(restResult.getData());
    }
    
    private void deleteAgent(String agentName, String version, String registrationType) throws Exception {
        String url = BASE_URL + A2A_ADMIN_PATH;
        Query query = Query.newInstance().addParam("agentName", agentName).addParam("namespaceId", DEFAULT_NAMESPACE)
                .addParam("registrationType", registrationType);
        if (null != version) {
            query.addParam("version", version);
        }
        HttpRestResult<String> restResult = nacosRestTemplate.delete(url, Header.EMPTY, query, String.class);
        if (!restResult.ok()) {
            LOGGER.warn("delete agent non-OK: code={} body={}", restResult.getCode(), restResult.getData());
        }
    }
    
    private void assertBadRequestContains(Map<String, String> form, String expectedText) throws Exception {
        HttpRestResult<String> restResult = nacosRestTemplate.postForm(BASE_URL + A2A_ADMIN_PATH, Header.EMPTY, form,
                String.class);
        assertEquals(400, restResult.getCode(), "expect bad request, body=" + restResult.getData());
        String message = String.valueOf(restResult.getMessage());
        String body = String.valueOf(restResult.getData());
        assertTrue(message.contains(expectedText) || body.contains(expectedText),
                "expected message should contain `" + expectedText + "`, actual=" + body);
    }
    
    private Map<String, String> buildAgentCardForm(String agentName, String version, String registrationType,
            String agentCard) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("agentName", agentName);
        form.put("version", version);
        form.put("namespaceId", DEFAULT_NAMESPACE);
        form.put("registrationType", registrationType);
        form.put("agentCard", agentCard);
        return form;
    }
    
    private String buildLegacyAgentCard(String agentName, String version) {
        return String.format("{\"name\":\"%s\",\"version\":\"%s\",\"description\":\"legacy-%s\","
                        + "\"protocolVersion\":\"1.0\",\"preferredTransport\":\"JSONRPC\","
                        + "\"url\":\"https://example.com/%s/jsonrpc\","
                        + "\"additionalInterfaces\":[{\"url\":\"https://example.com/%s/grpc\",\"transport\":\"GRPC\"}],"
                        + "\"capabilities\":{\"streaming\":true,\"extendedAgentCard\":true}}",
                agentName, version, agentName, agentName, agentName);
    }
    
    private String buildV1AgentCard(String agentName, String version, String protocolVersion) {
        return String.format("{\"name\":\"%s\",\"version\":\"%s\",\"description\":\"v1-%s\","
                        + "\"supportedInterfaces\":["
                        + "{\"url\":\"https://example.com/%s/jsonrpc\",\"protocolBinding\":\"JSONRPC\",\"protocolVersion\":\"%s\"},"
                        + "{\"url\":\"https://example.com/%s/grpc\",\"protocolBinding\":\"GRPC\",\"protocolVersion\":\"%s\"}"
                        + "],\"capabilities\":{\"streaming\":true,\"extendedAgentCard\":true}}",
                agentName, version, agentName, agentName, protocolVersion, agentName, protocolVersion);
    }
}
