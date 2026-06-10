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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

    protected static final String ADMIN_SKILL_PATH = nacosPath(Constants.Skills.ADMIN_PATH);

    protected static final String ADMIN_SKILL_LIST_PATH = ADMIN_SKILL_PATH + "/list";

    protected static final String ADMIN_SKILL_VERSION_PATH = ADMIN_SKILL_PATH + "/version";

    protected static final String ADMIN_SKILL_VERSION_DOWNLOAD_PATH =
            ADMIN_SKILL_PATH + "/version/download";

    protected static final String ADMIN_AGENT_SPEC_PATH = nacosPath(Constants.AgentSpecs.ADMIN_PATH);

    protected static final String ADMIN_AGENT_SPEC_LIST_PATH = ADMIN_AGENT_SPEC_PATH + "/list";

    protected static final String ADMIN_AGENT_SPEC_VERSION_PATH =
            ADMIN_AGENT_SPEC_PATH + "/version";

    protected static final String ADMIN_AGENT_SPEC_VERSION_META_PATH =
            ADMIN_AGENT_SPEC_PATH + "/version/meta";

    protected static final String ADMIN_IMPORT_PATH =
            nacosPath(Constants.AI_RESOURCE_IMPORT_ADMIN_PATH);

    protected static final String ADMIN_IMPORT_SOURCES_PATH = ADMIN_IMPORT_PATH + "/sources";

    protected static final String ADMIN_IMPORT_SEARCH_PATH = ADMIN_IMPORT_PATH + "/search";

    protected static final String ADMIN_IMPORT_VALIDATE_PATH = ADMIN_IMPORT_PATH + "/validate";

    protected static final String ADMIN_IMPORT_EXECUTE_PATH = ADMIN_IMPORT_PATH + "/execute";

    protected static final String IMPORT_RESOURCE_TYPE_MCP = "mcp";

    protected static final String IMPORT_RESOURCE_TYPE_SKILL = "skill";

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
        return form;
    }

    protected Map<String, String> withUpdateLatestLabel(Map<String, String> form) {
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

    protected Query skillQuery(String skillName) {
        return Query.newInstance().addParam("namespaceId", DEFAULT_NAMESPACE)
                .addParam("skillName", skillName);
    }

    protected Query skillVersionQuery(String skillName, String version) {
        Query query = skillQuery(skillName);
        addIfNotBlank(query, "version", version);
        return query;
    }

    protected Map<String, String> skillDraftForm(String skillName, String version, String body,
            String guideContent) {
        Map<String, String> form = skillUpdateForm(skillName, body, guideContent);
        form.put("namespaceId", DEFAULT_NAMESPACE);
        form.put("targetVersion", version);
        return form;
    }

    protected Map<String, String> skillForkForm(String skillName, String targetVersion,
            String basedOnVersion) {
        Map<String, String> form = skillQueryForm(skillName);
        form.put("targetVersion", targetVersion);
        form.put("basedOnVersion", basedOnVersion);
        form.put("commitMsg", "openapi skill admin fork");
        return form;
    }

    protected Map<String, String> skillUpdateForm(String skillName, String body,
            String guideContent) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("skillName", skillName);
        form.put("skillCard", buildSkillCard(skillName, body, guideContent));
        form.put("commitMsg", "openapi skill admin it");
        return form;
    }

    protected Map<String, String> skillPublishForm(String skillName, String version) {
        Map<String, String> form = skillQueryForm(skillName);
        form.put("version", version);
        return form;
    }

    protected Map<String, String> skillLabelsForm(String skillName, String labels) {
        Map<String, String> form = skillQueryForm(skillName);
        form.put("labels", labels);
        return form;
    }

    protected Map<String, String> skillBizTagsForm(String skillName, String bizTags) {
        Map<String, String> form = skillQueryForm(skillName);
        form.put("bizTags", bizTags);
        return form;
    }

    protected Map<String, String> skillScopeForm(String skillName, String scope) {
        Map<String, String> form = skillQueryForm(skillName);
        form.put("scope", scope);
        return form;
    }

    protected Map<String, String> skillOnlineForm(String skillName, String version, String scope) {
        Map<String, String> form = skillQueryForm(skillName);
        addIfNotBlank(form, "version", version);
        addIfNotBlank(form, "scope", scope);
        return form;
    }

    protected void deleteSkillQuietly(String skillName) throws Exception {
        deleteQuietly(ADMIN_SKILL_PATH, skillQuery(skillName));
    }

    protected void assertSkillContent(JsonNode data, String skillName, String version, String body,
            String guideContent) {
        assertEquals(DEFAULT_NAMESPACE, data.get("namespaceId").asText(), data.toString());
        assertEquals(skillName, data.get("name").asText(), data.toString());
        assertEquals("skill admin openapi integration test", data.get("description").asText(),
                data.toString());
        String skillMd = data.get("skillMd").asText();
        assertTrue(skillMd.contains("name: " + skillName), skillMd);
        assertTrue(skillMd.contains("version: " + version), skillMd);
        assertTrue(skillMd.contains(body), skillMd);
        JsonNode resource = findSkillResource(data.get("resource"), "references", "guide.md");
        assertNotNull(resource, data.toString());
        assertEquals("guide.md", resource.get("name").asText(), data.toString());
        assertEquals("references", resource.get("type").asText(), data.toString());
        assertEquals(guideContent, resource.get("content").asText(), data.toString());
    }

    protected JsonNode findSkillResource(JsonNode resources, String type, String name) {
        if (null == resources || !resources.isObject()) {
            return MissingNode.getInstance();
        }
        for (JsonNode resource : resources) {
            if (name.equals(resource.get("name").asText())
                    && type.equals(resource.get("type").asText())) {
                return resource;
            }
        }
        return MissingNode.getInstance();
    }

    protected JsonNode findSkillVersionSummary(JsonNode skillMeta, String version) {
        for (JsonNode item : skillMeta.get("versions")) {
            if (version.equals(item.get("version").asText())) {
                return item;
            }
        }
        return MissingNode.getInstance();
    }

    protected void assertSkillZip(ByteResponse response, String skillName, String version, String body,
            String guideContent) throws Exception {
        assertEquals(200, response.code(), new String(response.body(), StandardCharsets.UTF_8));
        assertNotNull(response.contentDisposition());
        assertTrue(response.contentDisposition().contains(skillName + ".zip"),
                response.contentDisposition());
        Map<String, String> entries = unzipTextEntries(response.body());
        String skillMd = entries.get(skillName + "/SKILL.md");
        assertNotNull(skillMd, entries.keySet().toString());
        assertTrue(skillMd.contains("name: " + skillName), skillMd);
        assertTrue(skillMd.contains("version: " + version), skillMd);
        assertTrue(skillMd.contains(body), skillMd);
        assertEquals(guideContent, entries.get(skillName + "/references/guide.md"));
    }

    protected byte[] buildSkillZip(String skillName, String version, String body,
            String guideContent) throws Exception {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("SKILL.md", skillMarkdown(skillName, version, body));
        entries.put("references/guide.md", guideContent);
        return zipEntries(entries);
    }

    protected byte[] buildMultiSkillZip(Map<String, String> skillNameToBody) throws Exception {
        Map<String, String> entries = new LinkedHashMap<>();
        skillNameToBody.forEach((skillName, body) -> {
            entries.put(skillName + "/SKILL.md", skillMarkdown(skillName, "1.0.0", body));
            entries.put(skillName + "/references/guide.md", "guide for " + skillName);
        });
        return zipEntries(entries);
    }

    protected byte[] buildPartiallyInvalidMultiSkillZip(String validSkillName, String validBody)
            throws Exception {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put(validSkillName + "/SKILL.md", skillMarkdown(validSkillName, "1.0.0", validBody));
        entries.put("invalid-skill/SKILL.md", "---\nname: {{{invalid yaml\n---\n\nBroken instructions");
        return zipEntries(entries);
    }

    protected Map<String, String> unzipTextEntries(byte[] body) throws Exception {
        Map<String, String> result = new LinkedHashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(body))) {
            ZipEntry entry;
            while (null != (entry = zis.getNextEntry())) {
                if (!entry.isDirectory()) {
                    result.put(entry.getName(), new String(readEntry(zis), StandardCharsets.UTF_8));
                }
            }
        }
        return result;
    }

    protected Query agentSpecQuery(String agentSpecName) {
        return Query.newInstance().addParam("namespaceId", DEFAULT_NAMESPACE)
                .addParam("agentSpecName", agentSpecName);
    }

    protected Query agentSpecVersionQuery(String agentSpecName, String version) {
        Query query = agentSpecQuery(agentSpecName);
        addIfNotBlank(query, "version", version);
        return query;
    }

    protected Map<String, String> agentSpecDraftForm(String agentSpecName, String targetVersion) {
        Map<String, String> form = agentSpecQueryForm(agentSpecName);
        form.put("targetVersion", targetVersion);
        return form;
    }

    protected Map<String, String> agentSpecForkForm(String agentSpecName, String targetVersion,
            String basedOnVersion) {
        Map<String, String> form = agentSpecDraftForm(agentSpecName, targetVersion);
        form.put("basedOnVersion", basedOnVersion);
        return form;
    }

    protected Map<String, String> agentSpecUpdateForm(String agentSpecName, String version,
            String description, String scenario, String soulContent) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("agentSpecCard", buildAgentSpecCard(agentSpecName, version, description,
                scenario, soulContent));
        return form;
    }

    protected Map<String, String> agentSpecPublishForm(String agentSpecName, String version) {
        Map<String, String> form = agentSpecQueryForm(agentSpecName);
        form.put("version", version);
        return form;
    }

    protected Map<String, String> agentSpecLabelsForm(String agentSpecName, String labels) {
        Map<String, String> form = agentSpecQueryForm(agentSpecName);
        form.put("labels", labels);
        return form;
    }

    protected Map<String, String> agentSpecBizTagsForm(String agentSpecName, String bizTags) {
        Map<String, String> form = agentSpecQueryForm(agentSpecName);
        form.put("bizTags", bizTags);
        return form;
    }

    protected Map<String, String> agentSpecScopeForm(String agentSpecName, String scope) {
        Map<String, String> form = agentSpecQueryForm(agentSpecName);
        form.put("scope", scope);
        return form;
    }

    protected Map<String, String> agentSpecOnlineForm(String agentSpecName, String version,
            String scope) {
        Map<String, String> form = agentSpecQueryForm(agentSpecName);
        addIfNotBlank(form, "version", version);
        addIfNotBlank(form, "scope", scope);
        return form;
    }

    protected void deleteAgentSpecQuietly(String agentSpecName) throws Exception {
        deleteQuietly(ADMIN_AGENT_SPEC_PATH, agentSpecQuery(agentSpecName));
    }

    protected void assertAgentSpecContent(JsonNode data, String agentSpecName, String version,
            String description, String scenario, String soulContent) {
        assertEquals(DEFAULT_NAMESPACE, data.get("namespaceId").asText(), data.toString());
        assertEquals(agentSpecName, data.get("name").asText(), data.toString());
        assertEquals(description, data.get("description").asText(), data.toString());
        JsonNode manifest = JacksonUtils.toObj(data.get("content").asText());
        assertEquals(agentSpecName, manifest.get("worker").get("suggested_name").asText(),
                data.toString());
        assertEquals(version, manifest.get("version").asText(), data.toString());
        assertEquals(scenario, manifest.get("scenario").asText(), data.toString());
        JsonNode resource = findSkillResource(data.get("resource"), "config", "SOUL.md");
        assertNotNull(resource, data.toString());
        assertEquals(soulContent, resource.get("content").asText(), data.toString());
    }

    protected void assertAgentSpecMetaContent(JsonNode data, String agentSpecName, String version,
            String description, String scenario) {
        assertEquals(DEFAULT_NAMESPACE, data.get("namespaceId").asText(), data.toString());
        assertEquals(agentSpecName, data.get("name").asText(), data.toString());
        assertEquals(description, data.get("description").asText(), data.toString());
        JsonNode manifest = JacksonUtils.toObj(data.get("content").asText());
        assertEquals(version, manifest.get("version").asText(), data.toString());
        assertEquals(scenario, manifest.get("scenario").asText(), data.toString());
        JsonNode resource = findSkillResource(data.get("resource"), "config", "SOUL.md");
        assertNotNull(resource, data.toString());
        assertTrue(resource.path("content").isNull() || resource.path("content").isMissingNode(),
                data.toString());
    }

    protected JsonNode findAgentSpecVersionSummary(JsonNode agentSpecMeta, String version) {
        for (JsonNode item : agentSpecMeta.get("versions")) {
            if (version.equals(item.get("version").asText())) {
                return item;
            }
        }
        return MissingNode.getInstance();
    }

    protected Query importSourceQuery(String resourceType) {
        Query query = Query.newInstance();
        addIfNotBlank(query, "resourceType", resourceType);
        return query;
    }

    protected Map<String, String> importSearchForm(String resourceType, String sourceId,
            String query, Integer limit, String options) {
        Map<String, String> form = importSourceForm(resourceType, sourceId);
        addIfNotBlank(form, "query", query);
        if (null != limit) {
            form.put("limit", String.valueOf(limit));
        }
        addIfNotBlank(form, "options", options);
        return form;
    }

    protected Map<String, String> importValidateForm(String resourceType, String sourceId,
            String selectedItems, String options, boolean overwriteExisting) {
        Map<String, String> form = importSourceForm(resourceType, sourceId);
        addIfNotBlank(form, "selectedItems", selectedItems);
        addIfNotBlank(form, "options", options);
        form.put("overwriteExisting", String.valueOf(overwriteExisting));
        return form;
    }

    protected Map<String, String> importExecuteForm(String resourceType, String sourceId,
            String selectedItems, String options, boolean overwriteExisting,
            boolean skipInvalid) {
        Map<String, String> form = importValidateForm(resourceType, sourceId, selectedItems,
                options, overwriteExisting);
        form.put("skipInvalid", String.valueOf(skipInvalid));
        form.put("validationToken", "openapi-it-token");
        return form;
    }

    protected String importSelectedItemsJson(String externalId, String name, String version) {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("source", "openapi-it");
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("externalId", externalId);
        item.put("name", name);
        item.put("version", version);
        item.put("metadata", metadata);
        return JacksonUtils.toJson(Collections.singletonList(item));
    }

    protected String importOptionsJson() {
        Map<String, String> options = new LinkedHashMap<>();
        options.put("scenario", "openapi-it");
        return JacksonUtils.toJson(options);
    }

    protected byte[] buildAgentSpecZip(String agentSpecName, String version, String description,
            String scenario, String soulContent) throws Exception {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("manifest.json", buildAgentSpecManifest(agentSpecName, version, description,
                scenario));
        entries.put("config/SOUL.md", soulContent);
        return zipEntries(entries);
    }

    protected byte[] buildAgentSpecSeedArchive(Map<String, String> agentSpecNameToScenario)
            throws Exception {
        Map<String, String> entries = new LinkedHashMap<>();
        agentSpecNameToScenario.forEach((agentSpecName, scenario) -> {
            String root = "openapi/" + agentSpecName;
            entries.put(root + "/manifest.json", buildAgentSpecManifest(agentSpecName, "0.0.1",
                    "uploaded seed " + agentSpecName, scenario));
            entries.put(root + "/config/SOUL.md", "seed soul for " + agentSpecName);
        });
        return zipEntries(entries);
    }

    protected byte[] buildZipWithoutAgentSpecManifest() throws Exception {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("config/SOUL.md", "soul without manifest");
        return zipEntries(entries);
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

    protected Map<String, String> skillQueryForm(String skillName) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("namespaceId", DEFAULT_NAMESPACE);
        form.put("skillName", skillName);
        return form;
    }

    protected Map<String, String> agentSpecQueryForm(String agentSpecName) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("namespaceId", DEFAULT_NAMESPACE);
        form.put("agentSpecName", agentSpecName);
        return form;
    }

    protected Map<String, String> importSourceForm(String resourceType, String sourceId) {
        Map<String, String> form = new LinkedHashMap<>();
        addIfNotBlank(form, "namespaceId", DEFAULT_NAMESPACE);
        addIfNotBlank(form, "resourceType", resourceType);
        addIfNotBlank(form, "sourceId", sourceId);
        return form;
    }

    private String promptVariables() {
        Map<String, String> variable = new LinkedHashMap<>();
        variable.put("name", "name");
        variable.put("defaultValue", "Nacos");
        variable.put("description", "target name");
        return JacksonUtils.toJson(Collections.singletonList(variable));
    }

    private String buildSkillCard(String skillName, String body, String guideContent) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("name", skillName);
        card.put("description", "skill admin openapi integration test");
        card.put("skillMd", skillMarkdown(skillName, null, body));
        Map<String, Object> guide = new LinkedHashMap<>();
        guide.put("name", "guide.md");
        guide.put("type", "references");
        guide.put("content", guideContent);
        Map<String, Object> resources = new LinkedHashMap<>();
        resources.put("references::guide.md", guide);
        card.put("resource", resources);
        return JacksonUtils.toJson(card);
    }

    private String buildAgentSpecCard(String agentSpecName, String version, String description,
            String scenario, String soulContent) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("name", agentSpecName);
        card.put("description", description);
        card.put("bizTags", "[\"openapi-it\"]");
        card.put("content", buildAgentSpecManifest(agentSpecName, version, description, scenario));
        Map<String, Object> soul = new LinkedHashMap<>();
        soul.put("name", "SOUL.md");
        soul.put("type", "config");
        soul.put("content", soulContent);
        Map<String, Object> resources = new LinkedHashMap<>();
        resources.put("config_SOUL__md", soul);
        card.put("resource", resources);
        return JacksonUtils.toJson(card);
    }

    private String buildAgentSpecManifest(String agentSpecName, String version, String description,
            String scenario) {
        Map<String, Object> worker = new LinkedHashMap<>();
        worker.put("suggested_name", agentSpecName);
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("version", version);
        manifest.put("description", description);
        manifest.put("scenario", scenario);
        manifest.put("tags", Collections.singletonList("openapi-it"));
        manifest.put("worker", worker);
        return JacksonUtils.toJson(manifest);
    }

    private String skillMarkdown(String skillName, String version, String body) {
        StringBuilder result = new StringBuilder();
        result.append("---\nname: ").append(skillName)
                .append("\ndescription: skill admin openapi integration test\n");
        if (null != version) {
            result.append("version: ").append(version).append('\n');
        }
        result.append("---\n\n").append(body);
        return result.toString();
    }

    private byte[] zipEntries(Map<String, String> entries) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(output)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                zos.putNextEntry(new ZipEntry(entry.getKey()));
                zos.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        return output.toByteArray();
    }

    private byte[] readEntry(ZipInputStream zis) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int len;
        while ((len = zis.read(buffer)) != -1) {
            output.write(buffer, 0, len);
        }
        return output.toByteArray();
    }

    private void addIfNotBlank(Map<String, String> form, String name, String value) {
        if (null != value && !value.isBlank()) {
            form.put(name, value);
        }
    }
}
