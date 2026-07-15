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

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Shared helpers for AgentSpec client OpenAPI integration tests.
 *
 * @author xiweng.yy
 */
public abstract class AgentSpecOpenApiBaseITCase extends AiOpenApiBaseITCase {
    
    protected static final String AGENT_SPEC_CLIENT_PATH = nacosPath(Constants.AgentSpecs.CLIENT_PATH);
    
    protected static final String AGENT_SPEC_ADMIN_PATH = nacosPath(Constants.AgentSpecs.ADMIN_PATH);
    
    protected void publishAgentSpec(String name, String version, String basedOnVersion, String description,
            String scenario, String soulContent) throws Exception {
        JsonNode draft = postFormOk(AGENT_SPEC_ADMIN_PATH + "/draft",
                buildAgentSpecDraftForm(name, version, basedOnVersion));
        assertEquals(version, draft.get("data").asText(), draft.toString());
        JsonNode updated = putFormOk(AGENT_SPEC_ADMIN_PATH + "/draft",
                buildAgentSpecUpdateForm(name, version, description, scenario, soulContent));
        assertEquals("ok", updated.get("data").asText(), updated.toString());
        Map<String, String> form = new LinkedHashMap<>();
        form.put("agentSpecName", name);
        form.put("version", version);
        JsonNode published = postFormOk(AGENT_SPEC_ADMIN_PATH + "/force-publish", form);
        assertEquals("ok", published.get("data").asText(), published.toString());
    }
    
    protected void updateAgentSpecLabels(String name, String labels) throws Exception {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("agentSpecName", name);
        form.put("labels", labels);
        JsonNode root = putFormOk(AGENT_SPEC_ADMIN_PATH + "/labels", form);
        assertEquals("ok", root.get("data").asText(), root.toString());
    }
    
    protected void deleteAgentSpec(String name) throws Exception {
        deleteQuietly(AGENT_SPEC_ADMIN_PATH, Query.newInstance().addParam("agentSpecName", name));
    }
    
    protected String randomAgentSpecName(String scenario) {
        return "oit-" + scenario + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
    
    protected String randomAgentSpecSuffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
    
    protected void assertAgentSpec(JsonNode data, String name, String version, String description,
            String scenario, String soulContent) throws Exception {
        assertNotNull(data);
        assertEquals(DEFAULT_NAMESPACE, data.get("namespaceId").asText());
        assertEquals(name, data.get("name").asText());
        assertEquals(description, data.get("description").asText());
        JsonNode content = JacksonUtils.toObj(data.get("content").asText());
        assertEquals(name, content.get("worker").get("suggested_name").asText());
        assertEquals(version, content.get("version").asText());
        assertEquals(scenario, content.get("scenario").asText());
        JsonNode resource = data.get("resource").get("config_SOUL__md");
        assertNotNull(resource, data.toString());
        assertEquals("SOUL.md", resource.get("name").asText());
        assertEquals("config", resource.get("type").asText());
        assertEquals(soulContent, resource.get("content").asText());
    }
    
    private Map<String, String> buildAgentSpecDraftForm(String name, String version, String basedOnVersion) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("agentSpecName", name);
        form.put("targetVersion", version);
        if (null != basedOnVersion) {
            form.put("basedOnVersion", basedOnVersion);
        }
        return form;
    }
    
    private Map<String, String> buildAgentSpecUpdateForm(String name, String version, String description,
            String scenario, String soulContent) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("agentSpecCard", buildAgentSpecCard(name, version, description, scenario, soulContent));
        return form;
    }
    
    private String buildAgentSpecCard(String name, String version, String description, String scenario,
            String soulContent) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("name", name);
        card.put("description", description);
        card.put("bizTags", "[\"openapi-it\"]");
        card.put("content", buildManifestContent(name, version, scenario));
        Map<String, Object> soul = new LinkedHashMap<>();
        soul.put("name", "SOUL.md");
        soul.put("type", "config");
        soul.put("content", soulContent);
        Map<String, Object> resources = new LinkedHashMap<>();
        resources.put("config_SOUL__md", soul);
        card.put("resource", resources);
        return JacksonUtils.toJson(card);
    }
    
    private String buildManifestContent(String name, String version, String scenario) {
        Map<String, Object> worker = new LinkedHashMap<>();
        worker.put("suggested_name", name);
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("worker", worker);
        manifest.put("version", version);
        manifest.put("scenario", scenario);
        return JacksonUtils.toJson(manifest);
    }
}
