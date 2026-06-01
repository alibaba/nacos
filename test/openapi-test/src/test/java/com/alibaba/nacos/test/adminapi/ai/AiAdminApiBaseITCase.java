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

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.test.openapi.OpenApiBaseITCase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shared helpers for AI admin OpenAPI integration tests.
 *
 * @author xiweng.yy
 */
public abstract class AiAdminApiBaseITCase extends OpenApiBaseITCase {

    protected static final String DEFAULT_NAMESPACE = "public";

    protected static final String ADMIN_A2A_PATH = nacosPath(Constants.A2A.ADMIN_PATH);

    protected static final String ADMIN_A2A_LIST_PATH = ADMIN_A2A_PATH + "/list";

    protected static final String ADMIN_A2A_VERSION_LIST_PATH = ADMIN_A2A_PATH + "/version/list";

    protected static final String ADMIN_MCP_PATH = nacosPath(Constants.MCP_ADMIN_PATH);

    protected static final String ADMIN_MCP_LIST_PATH = ADMIN_MCP_PATH + "/list";

    protected static final String ADMIN_PIPELINE_PATH = nacosPath(Constants.Pipeline.ADMIN_PATH);

    protected static final String ADMIN_PIPELINE_LIST_PATH = ADMIN_PIPELINE_PATH + Constants.Pipeline.LIST_SUBPATH;

    protected static final String ADMIN_PIPELINE_DETAIL_PATH =
            ADMIN_PIPELINE_PATH + Constants.Pipeline.DETAIL_SUBPATH;

    protected static final String ADMIN_PROMPT_PATH = nacosPath(Constants.Prompt.ADMIN_PATH);

    protected static final String ADMIN_PROMPT_LIST_PATH = ADMIN_PROMPT_PATH + "/list";

    protected static final String ADMIN_PROMPT_VERSIONS_PATH = ADMIN_PROMPT_PATH + "/versions";

    protected static final String ADMIN_PROMPT_GOVERNANCE_PATH = ADMIN_PROMPT_PATH + "/governance";

    protected static final String ADMIN_PROMPT_VERSION_PATH = ADMIN_PROMPT_PATH + "/version";

    protected static final String ADMIN_PROMPT_VERSION_DOWNLOAD_PATH =
            ADMIN_PROMPT_PATH + "/version/download";

    protected String randomAiName(String scenario) {
        return "oit-" + scenario + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    protected String randomPromptKey(String scenario) {
        return "oit_" + scenario + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    protected Query mcpIdentityQuery(String mcpName, String mcpId, String version) {
        Query query = Query.newInstance().addParam("namespaceId", DEFAULT_NAMESPACE);
        addIfNotBlank(query, "mcpName", mcpName);
        addIfNotBlank(query, "mcpId", mcpId);
        addIfNotBlank(query, "version", version);
        return query;
    }

    protected Map<String, String> mcpServerForm(String mcpName, String version, String description,
            String toolName, String resourceName) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("namespaceId", DEFAULT_NAMESPACE);
        form.put("mcpName", mcpName);
        form.put("serverSpecification", mcpServerSpecification(mcpName, version, description));
        form.put("toolSpecification", mcpToolSpecification(toolName));
        form.put("resourceSpecification", mcpResourceSpecification(resourceName));
        return form;
    }

    protected void deleteMcpServerQuietly(String mcpName, String mcpId) throws Exception {
        deleteQuietly(ADMIN_MCP_PATH, mcpIdentityQuery(mcpName, mcpId, null));
    }

    protected void assertMcpDetail(JsonNode data, String mcpName, String version, String description,
            String toolName, String resourceName) {
        assertEquals(DEFAULT_NAMESPACE, data.get("namespaceId").asText(), data.toString());
        assertEquals(mcpName, data.get("name").asText(), data.toString());
        assertEquals(version, data.get("version").asText(), data.toString());
        assertEquals(description, data.get("description").asText(), data.toString());
        assertEquals("stdio", data.get("protocol").asText(), data.toString());
        assertEquals(version, data.get("versionDetail").get("version").asText(), data.toString());
        assertEquals(toolName, data.get("toolSpec").get("tools").get(0).get("name").asText(),
                data.toString());
        assertEquals(resourceName, data.get("resourceSpec").get("resources").get(0).get("name").asText(),
                data.toString());
    }

    protected JsonNode findByName(JsonNode page, String fieldName, String expectedName) {
        for (JsonNode item : page.get("pageItems")) {
            if (expectedName.equals(item.get(fieldName).asText())) {
                return item;
            }
        }
        return MissingNode.getInstance();
    }

    protected void assertPageContains(JsonNode page, String fieldName, String expectedName) {
        assertFalse(findByName(page, fieldName, expectedName).isMissingNode(), page.toString());
    }

    protected void assertEmptyPageShape(JsonNode page) {
        assertTrue(page.get("pageNumber").asInt() >= 1, page.toString());
        assertTrue(page.get("pagesAvailable").asInt() >= 0, page.toString());
        assertTrue(page.get("totalCount").asInt() >= 0, page.toString());
        assertTrue(page.get("pageItems").isArray(), page.toString());
    }

    protected Query queryFrom(Map<String, String> params) {
        Query query = Query.newInstance();
        params.forEach(query::addParam);
        return query;
    }

    protected Map<String, String> buildAgentCardForm(String agentName, String version, String registrationType,
            String agentCard) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("agentName", agentName);
        form.put("version", version);
        form.put("namespaceId", DEFAULT_NAMESPACE);
        form.put("registrationType", registrationType);
        form.put("agentCard", agentCard);
        return form;
    }

    protected String buildLegacyAgentCard(String agentName, String version) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("name", agentName);
        card.put("version", version);
        card.put("description", "legacy-" + agentName);
        card.put("protocolVersion", "1.0");
        card.put("preferredTransport", "JSONRPC");
        card.put("url", "https://example.com/" + agentName + "/jsonrpc");
        card.put("additionalInterfaces", Collections.singletonList(agentInterface(agentName, "GRPC", "1.0")));
        card.put("capabilities", agentCapabilities());
        return JacksonUtils.toJson(card);
    }

    protected String buildV1AgentCard(String agentName, String version, String protocolVersion) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("name", agentName);
        card.put("version", version);
        card.put("description", "v1-" + agentName);
        card.put("supportedInterfaces", List.of(agentInterface(agentName, "JSONRPC", protocolVersion),
                agentInterface(agentName, "GRPC", protocolVersion)));
        card.put("capabilities", agentCapabilities());
        return JacksonUtils.toJson(card);
    }

    protected void deleteAgentQuietly(String agentName, String version, String registrationType) throws Exception {
        Query query = Query.newInstance().addParam("agentName", agentName)
                .addParam("namespaceId", DEFAULT_NAMESPACE);
        addIfNotBlank(query, "version", version);
        addIfNotBlank(query, "registrationType", registrationType);
        deleteQuietly(ADMIN_A2A_PATH, query);
    }

    protected Query promptQuery(String promptKey) {
        return Query.newInstance().addParam("namespaceId", DEFAULT_NAMESPACE)
                .addParam("promptKey", promptKey);
    }

    protected Query promptVersionQuery(String promptKey, String version) {
        Query query = promptQuery(promptKey);
        addIfNotBlank(query, "version", version);
        return query;
    }

    protected Map<String, String> promptDraftForm(String promptKey, String targetVersion,
            String template, String description, String bizTags) {
        Map<String, String> form = promptQueryForm(promptKey);
        form.put("targetVersion", targetVersion);
        form.put("template", template);
        form.put("variables", promptVariables());
        form.put("commitMsg", "openapi prompt admin it");
        form.put("description", description);
        form.put("bizTags", bizTags);
        return form;
    }

    protected Map<String, String> promptUpdateDraftForm(String promptKey, String template) {
        Map<String, String> form = promptQueryForm(promptKey);
        form.put("template", template);
        form.put("variables", promptVariables());
        form.put("commitMsg", "openapi prompt admin update");
        return form;
    }

    protected Map<String, String> promptPublishForm(String promptKey, String version) {
        Map<String, String> form = promptQueryForm(promptKey);
        form.put("version", version);
        form.put("updateLatestLabel", "true");
        return form;
    }

    protected Map<String, String> promptLabelsForm(String promptKey, String labels) {
        Map<String, String> form = promptQueryForm(promptKey);
        form.put("labels", labels);
        return form;
    }

    protected Map<String, String> promptDescriptionForm(String promptKey, String description) {
        Map<String, String> form = promptQueryForm(promptKey);
        form.put("description", description);
        return form;
    }

    protected Map<String, String> promptBizTagsForm(String promptKey, String bizTags) {
        Map<String, String> form = promptQueryForm(promptKey);
        form.put("bizTags", bizTags);
        return form;
    }

    protected void deletePromptQuietly(String promptKey) throws Exception {
        deleteQuietly(ADMIN_PROMPT_PATH, promptQuery(promptKey));
    }

    protected void assertPromptVersion(JsonNode data, String promptKey, String version, String status,
            String template) {
        assertEquals(promptKey, data.get("promptKey").asText(), data.toString());
        assertEquals(version, data.get("version").asText(), data.toString());
        assertEquals(status, data.get("status").asText(), data.toString());
        assertEquals(template, data.get("template").asText(), data.toString());
        assertEquals("name", data.get("variables").get(0).get("name").asText(), data.toString());
        assertEquals("Nacos", data.get("variables").get(0).get("defaultValue").asText(), data.toString());
    }

    private String mcpServerSpecification(String mcpName, String version, String description) {
        Map<String, Object> versionDetail = new LinkedHashMap<>();
        versionDetail.put("version", version);
        Map<String, Object> localServerConfig = new LinkedHashMap<>();
        localServerConfig.put("command", "echo");
        localServerConfig.put("args", Collections.singletonList("openapi-it"));
        Map<String, Object> server = new LinkedHashMap<>();
        server.put("name", mcpName);
        server.put("protocol", "stdio");
        server.put("description", description);
        server.put("versionDetail", versionDetail);
        server.put("localServerConfig", localServerConfig);
        server.put("enabled", true);
        return JacksonUtils.toJson(server);
    }

    private String mcpToolSpecification(String toolName) {
        Map<String, Object> textProperty = new LinkedHashMap<>();
        textProperty.put("type", "string");
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("text", textProperty);
        Map<String, Object> inputSchema = new LinkedHashMap<>();
        inputSchema.put("type", "object");
        inputSchema.put("properties", properties);
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("name", toolName);
        tool.put("description", "Echo text for OpenAPI IT");
        tool.put("inputSchema", inputSchema);
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("tools", Collections.singletonList(tool));
        return JacksonUtils.toJson(spec);
    }

    private String mcpResourceSpecification(String resourceName) {
        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("name", resourceName);
        resource.put("uri", "file:///tmp/" + resourceName + ".txt");
        resource.put("description", "OpenAPI IT resource");
        resource.put("mimeType", "text/plain");
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("resources", Collections.singletonList(resource));
        return JacksonUtils.toJson(spec);
    }

    private Map<String, Object> agentInterface(String agentName, String protocolBinding, String protocolVersion) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("url", "https://example.com/" + agentName + "/" + protocolBinding.toLowerCase());
        result.put("protocolBinding", protocolBinding);
        result.put("protocolVersion", protocolVersion);
        result.put("transport", protocolBinding);
        return result;
    }

    private Map<String, Object> agentCapabilities() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("streaming", true);
        result.put("extendedAgentCard", true);
        return result;
    }

    protected Map<String, String> promptQueryForm(String promptKey) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("namespaceId", DEFAULT_NAMESPACE);
        form.put("promptKey", promptKey);
        return form;
    }

    private String promptVariables() {
        Map<String, String> variable = new LinkedHashMap<>();
        variable.put("name", "name");
        variable.put("defaultValue", "Nacos");
        variable.put("description", "target name");
        return JacksonUtils.toJson(Collections.singletonList(variable));
    }
}
