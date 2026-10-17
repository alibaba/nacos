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

package com.alibaba.nacos.test.upgrade;

import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.test.adminapi.ai.AiAdminApiBaseITCase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Standalone Derby upgrade fixture executed against external server processes.
 *
 * <p>The runner manages server lifecycle and data backup. The seed, verify-upgrade, mutate and
 * verify-restart phases retain their resources and share an expected-state manifest. Connection-owned
 * Agent runtime registrations are excluded from persistent recovery assertions.
 *
 * @author Nacos
 */
@EnabledIf("upgradeFixtureRequested")
class DerbyUpgradeOpenApiITCase extends AiAdminApiBaseITCase {

    private static final String CONFIG_PATH = nacosPath("/v3/admin/cs/config");

    private static final String HISTORY_PATH = nacosPath("/v3/admin/cs/history");

    private static final String NAMESPACE_PATH = nacosPath("/v3/admin/core/namespace");

    private static final String SERVICE_PATH = nacosPath("/v3/admin/ns/service");

    private static final String INSTANCE_PATH = nacosPath("/v3/admin/ns/instance");

    private static final String GROUP = "DERBY_UPGRADE";

    private static final String SKILL = "derby-upgrade-skill";

    private static final String PROMPT = "derby_upgrade_prompt";

    private static final String AGENT = "derby-upgrade-agent";

    private static final List<String> INITIAL_VERSIONS = List.of("1.0.0", "2.0.0", "3.0.0");

    private ObjectNode expected;

    private ObjectNode observed;

    static boolean upgradeFixtureRequested() {
        if (System.getenv("NACOS_DERBY_UPGRADE_PHASE") != null) {
            return true;
        }
        String simpleName = DerbyUpgradeOpenApiITCase.class.getSimpleName();
        String fullName = DerbyUpgradeOpenApiITCase.class.getName();
        for (String selection : System.getProperty("it.test", "").split(",")) {
            String selector = selection.trim().split("#", 2)[0];
            if (selector.startsWith("!")) {
                continue;
            }
            selector = selector.replace('/', '.').replace('\\', '.');
            if (selector.endsWith(".java")) {
                selector = selector.substring(0, selector.length() - 5);
            }
            // Require explicit selection for this stateful upgrade fixture.
            if (!selector.contains("Derby") && !selector.contains(".upgrade.")) {
                continue;
            }
            StringBuilder pattern = new StringBuilder();
            for (char character : selector.toCharArray()) {
                if (character == '*') {
                    pattern.append(".*");
                } else if (character == '?') {
                    pattern.append('.');
                } else {
                    pattern.append(java.util.regex.Pattern.quote(String.valueOf(character)));
                }
            }
            if (simpleName.matches(pattern.toString()) || fullName.matches(pattern.toString())) {
                return true;
            }
        }
        return false;
    }

    @Test
    void testDerbyUpgradePhase() throws Exception {
        String phase = System.getenv("NACOS_DERBY_UPGRADE_PHASE");
        assertTrue(phase != null
                        && Set.of("seed", "verify-upgrade", "mutate", "verify-restart").contains(phase),
                "NACOS_DERBY_UPGRADE_PHASE is required and must be seed, verify-upgrade, mutate or "
                        + "verify-restart; actual: " + phase);
        String stateDirectory = System.getenv("NACOS_DERBY_UPGRADE_STATE_DIR");
        assertTrue(stateDirectory != null && !stateDirectory.isBlank(),
                "NACOS_DERBY_UPGRADE_STATE_DIR is required");
        assertTrue(AUTH_ENABLED, "The upgrade fixture requires authentication enabled");
        Path directory = Path.of(stateDirectory);
        Files.createDirectories(directory);
        Path state = directory.resolve("expected.json");
        if ("seed".equals(phase)) {
            assertFalse(Files.exists(state), "Refusing to overwrite existing upgrade state");
            expected = objectNode();
            expected.put("formatVersion", 1);
            expected.put("runId", UUID.randomUUID().toString().substring(0, 8));
            expected.put("mutated", false);
            expected.put("username", "derby_upgrade_" + expected.path("runId").asText());
            seed();
            verify(true);
            save(state);
            return;
        }
        assertTrue(Files.isRegularFile(state), "Missing seed state: " + state);
        expected = (ObjectNode) JacksonUtils.toObj(Files.readString(state));
        assertEquals(1, expected.path("formatVersion").asInt(), "Unsupported state format");
        assertTrue(expected.path("runId").asText().matches("[0-9a-f]{8}"), "Invalid fixture run ID");
        assertEquals("derby_upgrade_" + expected.path("runId").asText(),
                expected.path("username").asText(), "Invalid fixture username");
        assertTrue(expected.path("durableFields").isObject(), "Missing durable seed evidence");
        if ("mutate".equals(phase)) {
            assertFalse(expected.path("mutated").asBoolean(), "Mutation already recorded");
            verify(false);
            mutate();
            expected.put("mutated", true);
            verify(true);
            save(state);
        } else {
            assertEquals("verify-restart".equals(phase), expected.path("mutated").asBoolean(),
                    "Phase does not match persisted fixture state");
            verify(false);
        }
    }

    private void seed() throws Exception {
        for (String namespace : namespaces()) {
            postFormOk(NAMESPACE_PATH, Map.of("namespaceId", namespace,
                    "namespaceName", namespace, "namespaceDesc", "Derby upgrade 中文"));
            publishConfig(namespace, "plain", "original " + namespace, "text");
            publishConfig(namespace, "plain", "updated " + namespace, "text");
            publishConfig(namespace, "json", "{\"namespace\":\"" + namespace + "\"}", "json");
            publishConfig(namespace, "unicode", "中文配置\n第二行 " + namespace + "\n", "text");
            postFormOk(SERVICE_PATH, service(namespace).addParam("ephemeral", "false")
                    .addParam("metadata", JacksonUtils.toJson(Map.of("namespace", namespace)))
                    .addParam("protectThreshold", "0.25"));
            for (int index = 1; index <= 2; index++) {
                postFormOk(INSTANCE_PATH, service(namespace).addParam("ip", "192.0.2." + index)
                        .addParam("port", Integer.toString(9000 + index))
                        .addParam("clusterName", "DEFAULT").addParam("ephemeral", "false")
                        .addParam("enabled", Boolean.toString(index == 1))
                        .addParam("weight", Double.toString(index + 0.5))
                        .addParam("metadata", JacksonUtils.toJson(Map.of("namespace", namespace,
                                "instance", Integer.toString(index)))));
            }
            for (String version : INITIAL_VERSIONS) {
                createSkill(namespace, version);
                createPrompt(namespace, version);
                createAgent(namespace, version);
                if (!"3.0.0".equals(version)) {
                    publishAi(ADMIN_SKILL_PATH, identity(namespace, "skillName", SKILL, version));
                    publishAi(ADMIN_PROMPT_PATH, identity(namespace, "promptKey", PROMPT, version));
                    postFormOk(ADMIN_AGENT_PATH + "/force-publish",
                            agentForm(agentVersionCommand(namespace, AGENT, version)));
                }
            }
            putFormOk(ADMIN_SKILL_PATH + "/labels", identity(namespace, "skillName", SKILL, null)
                    .addParam("labels", "{\"stable\":\"1.0.0\"}"));
            putFormOk(ADMIN_PROMPT_PATH + "/labels", identity(namespace, "promptKey", PROMPT, null)
                    .addParam("labels", "{\"stable\":\"1.0.0\"}"));
            putFormOk(ADMIN_AGENT_PATH + "/labels", agentForm(agentLabelsUpdateRequest(namespace,
                    AGENT, Map.of("stable", "1.0.0"))));
        }
        String username = expected.path("username").asText();
        postFormOk(nacosPath("/v3/auth/user"), Map.of("username", username,
                "password", ordinaryPassword()));
        postFormOk(nacosPath("/v3/auth/role"), Map.of("username", username, "role", username));
        postFormOk(nacosPath("/v3/auth/permission"), Map.of("role", username,
                "resource", namespaces().get(0) + ":" + GROUP + ":config/plain", "action", "r"));
    }

    private void mutate() throws Exception {
        for (String namespace : namespaces()) {
            publishConfig(namespace, "plain", "mutated " + namespace, "text");
            publishConfig(namespace, "new", "new after upgrade " + namespace, "text");
            // Publish the existing draft before creating the next Prompt version.
            postFormOk(ADMIN_PROMPT_PATH + "/force-publish",
                    identity(namespace, "promptKey", PROMPT, "3.0.0"));
            createPrompt(namespace, "4.0.0");
            publishAi(ADMIN_PROMPT_PATH, identity(namespace, "promptKey", PROMPT, "4.0.0"));
        }
    }

    private void createSkill(String namespace, String version) throws Exception {
        Map<String, String> form = skillDraftForm(SKILL, version, payload(namespace, version),
                "guide 中文\n" + namespace + " " + version + "\n");
        form.put("namespaceId", namespace);
        if ("1.0.0".equals(version)) {
            postFormOk(ADMIN_SKILL_PATH + "/draft", form);
        } else {
            Map<String, String> fork = skillForkForm(SKILL, version, "1.0.0");
            fork.put("namespaceId", namespace);
            postFormOk(ADMIN_SKILL_PATH + "/draft", fork);
            form.remove("targetVersion");
            putFormOk(ADMIN_SKILL_PATH + "/draft", form);
        }
    }

    private void createPrompt(String namespace, String version) throws Exception {
        Map<String, String> form = promptDraftForm(PROMPT, version, payload(namespace, version),
                "upgrade prompt " + namespace, "[\"derby-upgrade\"]");
        form.put("namespaceId", namespace);
        if (!"1.0.0".equals(version)) {
            form.remove("description");
            form.remove("bizTags");
        }
        postFormOk(ADMIN_PROMPT_PATH + "/draft", form);
    }

    private void createAgent(String namespace, String version) throws Exception {
        Map<String, Object> request = "1.0.0".equals(version)
                ? agentInitialDraftRequest(namespace, AGENT, version)
                : agentDraftCreateRequest(namespace, AGENT, version, null);
        Map<String, Object> callInterface = new LinkedHashMap<>();
        callInterface.put("protocol", "a2a");
        callInterface.put("protocolVersion", "1.0");
        callInterface.put("descriptorMediaType", "application/json");
        callInterface.put("nativeDescriptor", Map.of("name", AGENT, "version", version,
                "content", payload(namespace, version)));
        callInterface.put("endpointSourceOrder", List.of("DECLARED", "RUNTIME"));
        Map<String, Object> endpoint = Map.of("uri",
                "https://example.com:443/" + namespace + "/" + version, "transport", "JSONRPC",
                "priority", 1, "weight", 2.5, "metadata", Map.of("namespace", namespace));
        callInterface.put("endpointSets",
                List.of(Map.of("source", "DECLARED", "endpoints", List.of(endpoint))));
        request.put("callInterfaces", List.of(callInterface));
        postFormOk(ADMIN_AGENT_PATH + "/draft", agentForm(request));
    }

    private void publishAi(String path, Query query) throws Exception {
        postFormOk(path + "/force-publish", query.addParam("updateLatestLabel", "true"));
    }

    private void verify(boolean capture) throws Exception {
        observed = objectNode();
        for (String namespace : namespaces()) {
            JsonNode detail = data(NAMESPACE_PATH,
                    Query.newInstance().addParam("namespaceId", namespace));
            assertEquals(namespace, detail.path("namespace").asText());
            assertEquals("Derby upgrade 中文", detail.path("namespaceDesc").asText());
            verifyConfigs(namespace, capture);
            verifyNaming(namespace);
            verifyAi(namespace, capture);
        }
        verifyOrdinaryUser();
        if (capture) {
            verifyUnchangedFields();
            if (!expected.has("seedDurableFields")) {
                expected.set("seedDurableFields", observed.deepCopy());
            }
            expected.set("durableFields", observed);
        } else {
            assertEquals(expected.path("durableFields"), observed,
                    "Persisted IDs, hashes, content or lifecycle metadata changed");
        }
    }

    private void verifyConfigs(String namespace, boolean capture) throws Exception {
        boolean mutated = expected.path("mutated").asBoolean();
        Map<String, String> contents = new LinkedHashMap<>();
        contents.put("plain", (mutated ? "mutated " : "updated ") + namespace);
        contents.put("json", "{\"namespace\":\"" + namespace + "\"}");
        contents.put("unicode", "中文配置\n第二行 " + namespace + "\n");
        if (mutated) {
            contents.put("new", "new after upgrade " + namespace);
        }
        for (Map.Entry<String, String> entry : contents.entrySet()) {
            JsonNode config = data(CONFIG_PATH, config(namespace, entry.getKey()));
            assertEquals(entry.getValue(), config.path("content").asText(), config.toString());
            assertEquals(namespace, config.path("namespaceId").asText());
            assertEquals(entry.getKey(), config.path("dataId").asText());
            assertEquals(GROUP, config.path("groupName").asText());
            assertEquals("json".equals(entry.getKey()) ? "json" : "text",
                    config.path("type").asText());
            assertFalse(config.path("id").asText().isBlank(), config.toString());
            record(namespace + "/config/" + entry.getKey(), config,
                    "id", "md5", "createTime", "content", "type");
            if (capture && expected.has("durableFields") && !"new".equals(entry.getKey())) {
                assertEquals(expected.path("durableFields")
                                .path(namespace + "/config/" + entry.getKey()).path("id"),
                        config.path("id"), "Updating a configuration must retain its ID");
            }
        }
        JsonNode history = data(HISTORY_PATH + "/list", config(namespace, "plain")
                .addParam("pageNo", "1").addParam("pageSize", "100"));
        assertEquals(mutated ? 3 : 2, history.path("totalCount").asInt(), history.toString());
        ObjectNode rows = objectNode();
        Set<String> oldContents = new LinkedHashSet<>();
        for (JsonNode row : history.path("pageItems")) {
            String id = row.path("id").asText();
            assertFalse(id.isBlank(), row.toString());
            JsonNode old = data(HISTORY_PATH, config(namespace, "plain").addParam("nid", id));
            ObjectNode fields = objectNode();
            fields.set("content", old.path("content"));
            fields.set("md5", old.path("md5"));
            fields.set("opType", row.path("opType"));
            rows.set(id, fields);
            oldContents.add(old.path("content").asText());
        }
        assertTrue(oldContents.contains("original " + namespace), rows.toString());
        if (mutated) {
            assertTrue(oldContents.contains("updated " + namespace), rows.toString());
        }
        observed.set(namespace + "/history", rows);
        JsonNode configPage = data(CONFIG_PATH + "/list", Query.newInstance()
                .addParam("namespaceId", namespace).addParam("groupName", GROUP)
                .addParam("search", "accurate").addParam("pageNo", "1").addParam("pageSize", "100"));
        Set<String> configNames = new LinkedHashSet<>();
        for (JsonNode item : configPage.path("pageItems")) {
            configNames.add(item.path("dataId").asText());
        }
        assertEquals(contents.keySet(), configNames, configPage.toString());
    }

    private void verifyNaming(String namespace) throws Exception {
        JsonNode service = data(SERVICE_PATH, service(namespace));
        assertEquals(namespace, service.path("metadata").path("namespace").asText());
        assertFalse(service.path("ephemeral").asBoolean(true));
        assertEquals(0.25, service.path("protectThreshold").asDouble(), 0.0001);
        JsonNode instances = null;
        for (int attempt = 0; attempt < 60; attempt++) {
            instances = data(INSTANCE_PATH + "/list",
                    service(namespace).addParam("healthyOnly", "false"));
            if (instances.size() == 2) {
                break;
            }
            Thread.sleep(250);
        }
        assertEquals(2, instances.size(), instances.toString());
        Set<String> addresses = new LinkedHashSet<>();
        for (JsonNode instance : instances) {
            int index = instance.path("port").asInt() - 9000;
            assertTrue(index == 1 || index == 2, instance.toString());
            assertEquals("192.0.2." + index, instance.path("ip").asText());
            assertEquals(index == 1, instance.path("enabled").asBoolean());
            assertEquals(index + 0.5, instance.path("weight").asDouble(), 0.0001);
            assertFalse(instance.path("ephemeral").asBoolean(true));
            assertEquals(namespace, instance.path("metadata").path("namespace").asText());
            assertEquals(Integer.toString(index), instance.path("metadata").path("instance").asText());
            addresses.add(instance.path("ip").asText() + ":" + instance.path("port").asInt());
            record(namespace + "/instance/" + index, instance, "instanceId", "ip", "port",
                    "weight", "enabled", "ephemeral", "metadata");
        }
        assertEquals(Set.of("192.0.2.1:9001", "192.0.2.2:9002"), addresses);
    }

    private void verifyAi(String namespace, boolean capture) throws Exception {
        boolean mutated = expected.path("mutated").asBoolean();
        JsonNode skill = data(ADMIN_SKILL_PATH, identity(namespace, "skillName", SKILL, null));
        assertGovernance(skill, "2.0.0", 2);
        assertTrue(skill.path("enable").asBoolean(), skill.toString());
        assertEquals("3.0.0", skill.path("editingVersion").asText());
        assertVersions(skill.path("versions"), Set.copyOf(INITIAL_VERSIONS));
        record(namespace + "/skill", skill, "namespaceId", "name", "labels", "editingVersion",
                "onlineCnt", "scope", "owner");
        JsonNode prompt = data(ADMIN_PROMPT_GOVERNANCE_PATH,
                identity(namespace, "promptKey", PROMPT, null));
        assertGovernance(prompt, mutated ? "4.0.0" : "2.0.0", mutated ? 4 : 2);
        assertEquals(mutated ? "" : "3.0.0", prompt.path("editingVersion").asText(""));
        Set<String> promptVersions = new LinkedHashSet<>(INITIAL_VERSIONS);
        if (mutated) {
            promptVersions.add("4.0.0");
        }
        assertVersions(prompt.path("versions"), promptVersions);
        record(namespace + "/prompt", prompt, "namespaceId", "promptKey", "labels",
                "editingVersion", "onlineCnt", "scope", "owner");
        JsonNode agent = data(ADMIN_AGENT_PATH,
                agentIdentityQuery(namespace, AGENT));
        assertEquals(namespace, agent.path("agent").path("namespaceId").asText());
        assertEquals(AGENT, agent.path("agent").path("agentName").asText());
        assertEquals("enable", agent.path("agent").path("status").asText());
        assertEquals("3.0.0", agent.path("agent").path("versionInfo")
                .path("editingVersion").asText());
        assertGovernance(agent.path("agent").path("versionInfo"), "2.0.0", 2);
        assertVersions(agent.path("agent").path("versionInfo").path("onlineVersions"),
                Set.of("1.0.0", "2.0.0"));
        assertVersions(agent.path("versionPage").path("pageItems"), Set.copyOf(INITIAL_VERSIONS));
        record(namespace + "/agent", agent.path("agent"), "namespaceId", "agentName", "displayName",
                "description", "provider", "extensions", "scope", "owner", "versionInfo");
        for (String version : INITIAL_VERSIONS) {
            JsonNode skillVersion = data(ADMIN_SKILL_VERSION_PATH,
                    identity(namespace, "skillName", SKILL, version));
            JsonNode skillSummary = findSkillVersionSummary(skill, version);
            assertEquals("3.0.0".equals(version) ? "draft" : "online",
                    skillSummary.path("status").asText());
            assertEquals(version, skillSummary.path("version").asText());
            assertEquals(namespace, skillVersion.path("namespaceId").asText());
            assertEquals(SKILL, skillVersion.path("name").asText());
            assertTrue(skillVersion.path("skillMd").asText().contains("version: " + version));
            assertTrue(skillVersion.path("skillMd").asText().endsWith(payload(namespace, version)),
                    skillVersion.toString());
            JsonNode guide = findSkillResource(skillVersion.path("resource"), "references", "guide.md");
            assertEquals("guide 中文\n" + namespace + " " + version + "\n",
                    guide.path("content").asText());
            record(namespace + "/skill/" + version, skillVersion, "namespaceId", "name",
                    "description", "skillMd", "resource");
            record(namespace + "/skillSummary/" + version, skillSummary, "version", "status",
                    "author", "createTime", "commitMsg");
            verifySkillZip(namespace, version, skillVersion, guide, capture);
            verifyPromptVersion(namespace, version,
                    "3.0.0".equals(version) && !mutated ? "draft" : "online");
            JsonNode agentVersion = data(ADMIN_AGENT_VERSION_PATH,
                    agentVersionIdentityQuery(namespace, AGENT, version));
            assertEquals("3.0.0".equals(version) ? "draft" : "online",
                    agentVersion.path("status").asText());
            assertEquals(version, agentVersion.path("version").asText());
            JsonNode call = agentVersion.path("callInterfaces").path(0);
            assertEquals(payload(namespace, version), call.path("nativeDescriptor")
                    .path("content").asText());
            assertEquals("DECLARED", call.path("endpointSourceOrder").path(0).asText());
            assertEquals("RUNTIME", call.path("endpointSourceOrder").path(1).asText());
            JsonNode endpoints = call.path("endpointSets");
            assertEquals(1, endpoints.size(), endpoints.toString());
            assertEquals("DECLARED", endpoints.path(0).path("source").asText());
            assertEquals("https://example.com:443/" + namespace + "/" + version,
                    endpoints.path(0).path("endpoints").path(0).path("uri").asText());
            record(namespace + "/agent/" + version, agentVersion, "version", "status",
                    "contentDigest", "callInterfaces", "author", "createTime", "changeDescription");
        }
        if (mutated) {
            verifyPromptVersion(namespace, "4.0.0", "online");
        }
    }

    private void verifyPromptVersion(String namespace, String version, String status) throws Exception {
        JsonNode detail = data(ADMIN_PROMPT_VERSION_PATH,
                identity(namespace, "promptKey", PROMPT, version));
        assertPromptVersion(detail, PROMPT, version, status, payload(namespace, version));
        record(namespace + "/prompt/" + version, detail, "version", "status", "template",
                "variables", "md5", "srcUser", "commitMsg");
    }

    private void verifySkillZip(String namespace, String version, JsonNode skill, JsonNode guide,
            boolean capture) throws Exception {
        ByteResponse response = getRawBytes(ADMIN_SKILL_VERSION_DOWNLOAD_PATH,
                identity(namespace, "skillName", SKILL, version));
        assertEquals(200, response.code());
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(response.body()))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    assertFalse(entries.containsKey(entry.getName()), "Duplicate ZIP entry");
                    entries.put(entry.getName(), zip.readAllBytes());
                }
            }
        }
        assertEquals(Set.of(SKILL + "/SKILL.md", SKILL + "/references/guide.md"), entries.keySet());
        assertArrayEquals(skill.path("skillMd").asText().getBytes(StandardCharsets.UTF_8),
                entries.get(SKILL + "/SKILL.md"));
        assertArrayEquals(guide.path("content").asText().getBytes(StandardCharsets.UTF_8),
                entries.get(SKILL + "/references/guide.md"));
        ObjectNode files = objectNode();
        entries.forEach((name, bytes) -> files.put(name,
                java.util.Base64.getEncoder().encodeToString(bytes)));
        String key = namespace + "/skillZip/" + version;
        if (capture && expected.has("durableFields")) {
            assertEquals(expected.path("durableFields").path(key), files,
                    "Mutation must preserve pre-upgrade Skill archive contents");
        }
        observed.set(key, files);
    }

    private void verifyOrdinaryUser() throws Exception {
        Header noAuth = Header.newInstance();
        String token = "";
        for (int attempt = 0; attempt < 120; attempt++) {
            var login = nacosRestTemplate.<String>postForm(
                    requestUrl(nacosPath("/v3/auth/user/login")), noAuth,
                    Map.of("username", expected.path("username").asText(),
                            "password", ordinaryPassword()), String.class);
            if (login.ok() && login.getData() != null) {
                token = JacksonUtils.toObj(login.getData()).path("accessToken").asText();
                if (!token.isBlank()) {
                    break;
                }
            }
            Thread.sleep(250);
        }
        assertFalse(token.isBlank(), "Ordinary user login must return an access token");
        Header auth = Header.newInstance().addParam("Authorization", "Bearer " + token);
        String clientConfigPath = nacosPath("/v3/client/cs/config");
        for (int attempt = 0; attempt < 60; attempt++) {
            var allowed = nacosRestTemplate.<String>get(requestUrl(clientConfigPath), auth,
                    config(namespaces().get(0), "plain"), String.class);
            var denied = nacosRestTemplate.get(requestUrl(clientConfigPath), auth,
                    config(namespaces().get(0), "json"), String.class);
            var otherNamespace = nacosRestTemplate.get(requestUrl(clientConfigPath), auth,
                    config(namespaces().get(1), "plain"), String.class);
            if (allowed.getCode() == 200 && denied.getCode() == 403
                    && otherNamespace.getCode() == 403) {
                JsonNode result = JacksonUtils.toObj(allowed.getData());
                String content = (expected.path("mutated").asBoolean() ? "mutated " : "updated ")
                        + namespaces().get(0);
                if (result.path("code").asInt(-1) == 0
                        && content.equals(result.path("data").path("content").asText())) {
                    return;
                }
            }
            Thread.sleep(250);
        }
        throw new AssertionError("Ordinary user's exact config read grant/denials were not restored");
    }

    private void assertGovernance(JsonNode governance, String latest, int onlineCount) {
        assertEquals(latest, governance.path("labels").path("latest").asText(), governance.toString());
        assertEquals("1.0.0", governance.path("labels").path("stable").asText());
        assertEquals(2, governance.path("labels").size(), governance.toString());
        int actualOnlineCount = governance.has("onlineVersions")
                ? governance.path("onlineVersions").size() : governance.path("onlineCnt").asInt();
        assertEquals(onlineCount, actualOnlineCount, governance.toString());
    }

    private void assertVersions(JsonNode versions, Set<String> expectedVersions) {
        assertTrue(versions.isArray(), versions.toString());
        Set<String> actual = new LinkedHashSet<>();
        for (JsonNode version : versions) {
            assertTrue(actual.add(version.isTextual() ? version.asText()
                    : version.path("version").asText()), "Duplicate version");
        }
        assertEquals(expectedVersions, actual, versions.toString());
    }

    private void record(String key, JsonNode source, String... fields) {
        ObjectNode selected = objectNode();
        for (String field : fields) {
            if (source.has(field)) {
                selected.set(field, source.get(field));
            }
        }
        observed.set(key, selected);
    }

    private void verifyUnchangedFields() {
        if (!expected.has("durableFields")) {
            return;
        }
        expected.path("durableFields").fields().forEachRemaining(entry -> {
            String key = entry.getKey();
            if (key.endsWith("/config/plain") || key.endsWith("/history")
                    || key.endsWith("/prompt") || key.endsWith("/prompt/3.0.0")) {
                if (key.endsWith("/history")) {
                    entry.getValue().fields().forEachRemaining(history ->
                            assertEquals(history.getValue(), observed.path(key).path(history.getKey()),
                                    "Mutation changed existing history row " + history.getKey()));
                }
                if (key.endsWith("/config/plain")) {
                    for (String field : List.of("id", "createTime")) {
                        if (entry.getValue().has(field)) {
                            assertEquals(entry.getValue().path(field), observed.path(key).path(field),
                                    "Mutation changed configuration " + field);
                        }
                    }
                }
                if (key.endsWith("/prompt/3.0.0")) {
                    ObjectNode before = ((ObjectNode) entry.getValue()).deepCopy();
                    ObjectNode after = ((ObjectNode) observed.path(key)).deepCopy();
                    before.remove("status");
                    after.remove("status");
                    assertEquals(before, after, key + " content and IDs must survive publication");
                }
                return;
            }
            assertEquals(entry.getValue(), observed.path(key), "Mutation changed " + key);
        });
    }

    private JsonNode data(String path, Query query) throws Exception {
        // Decode original JSON response bytes as UTF-8 when Content-Type omits charset.
        ByteResponse response = getRawBytes(path, query);
        assertEquals(200, response.code());
        JsonNode result = JacksonUtils.toObj(new String(response.body(), StandardCharsets.UTF_8));
        assertSuccess(result);
        return result.path("data");
    }

    private void publishConfig(String namespace, String dataId, String content, String type)
            throws Exception {
        assertTrue(postFormOk(CONFIG_PATH, config(namespace, dataId).addParam("content", content)
                .addParam("type", type)).path("data").asBoolean());
    }

    private Query config(String namespace, String dataId) {
        return Query.newInstance().addParam("namespaceId", namespace).addParam("dataId", dataId)
                .addParam("groupName", GROUP);
    }

    private Query service(String namespace) {
        return Query.newInstance().addParam("namespaceId", namespace)
                .addParam("serviceName", "derby-upgrade-service").addParam("groupName", GROUP);
    }

    private Query identity(String namespace, String key, String name, String version) {
        Query query = Query.newInstance().addParam("namespaceId", namespace).addParam(key, name);
        if (version != null) {
            query.addParam("version", version);
        }
        return query;
    }

    private List<String> namespaces() {
        String prefix = "derby-upgrade-" + expected.path("runId").asText();
        return List.of(prefix + "-a", prefix + "-b");
    }

    private String payload(String namespace, String version) {
        return "升级测试 " + namespace + " " + version + "\nHello {{name}}\n第二行\n";
    }

    private String ordinaryPassword() {
        String password = System.getenv("NACOS_TEST_AUTH_CLIENT_PASSWORD");
        assertTrue(password != null && !password.isBlank(),
                "NACOS_TEST_AUTH_CLIENT_PASSWORD is required, and is never saved in expected.json");
        return password;
    }

    private ObjectNode objectNode() {
        return (ObjectNode) JacksonUtils.toObj("{}");
    }

    private void save(Path state) throws Exception {
        Path temporary = state.resolveSibling("expected.json.tmp");
        Files.writeString(temporary, JacksonUtils.toJson(expected), StandardCharsets.UTF_8);
        Files.move(temporary, state, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }
}
