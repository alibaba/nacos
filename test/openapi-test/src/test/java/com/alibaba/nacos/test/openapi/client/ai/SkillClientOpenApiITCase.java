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
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for the AI skill client Open API {@code GET /nacos/v3/client/ai/skills}.
 *
 * <p>Scenario coverage:
 * <ul>
 *     <li>Expected capability: an online skill can be downloaded as a ZIP by latest, explicit version, and label; the
 *     ZIP contains {@code SKILL.md}, normalized version front matter, and resource files.</li>
 *     <li>Boundary/validation: omitted namespaceId uses public; explicit version has priority over label; missing
 *     name and malformed name are rejected with HTTP 400; unknown version and unknown label are not resolved.</li>
 *     <li>Exception/error handling: absent skill and unresolved version/label return controlled not-found JSON
 *     responses instead of HTTP 500, while successful responses keep the binary ZIP contract.</li>
 * </ul>
 *
 * <p>Draft, update-draft, force-publish, label, and delete calls to {@code /nacos/v3/admin/ai/skills} are helper calls
 * only; this class keeps its assertions focused on the runtime client download contract.
 *
 * @author xiweng.yy
 */
public class SkillClientOpenApiITCase extends AiOpenApiBaseITCase {
    
    private static final String SKILL_CLIENT_PATH = nacosPath(Constants.Skills.CLIENT_PATH);
    
    private static final String SKILL_ADMIN_PATH = nacosPath(Constants.Skills.ADMIN_PATH);
    
    @Test
    public void testDownloadSkillByLatestVersionAndLabel() throws Exception {
        String skillName = randomSkillName("skill");
        publishSkill(skillName, "1.0.0", null, "Use the v1 skill body.", "guide v1");
        addCleanup(() -> deleteSkill(skillName));
        publishSkill(skillName, "2.0.0", "1.0.0", "Use the v2 skill body.", "guide v2");
        updateLabels(skillName, "{\"stable\":\"1.0.0\"}");
        
        assertSkillZip(Query.newInstance().addParam("name", skillName), skillName, "2.0.0",
                "Use the v2 skill body.", "guide v2");
        assertSkillZip(Query.newInstance().addParam("name", skillName).addParam("version", "1.0.0"),
                skillName, "1.0.0", "Use the v1 skill body.", "guide v1");
        assertSkillZip(Query.newInstance().addParam("name", skillName).addParam("label", "stable"),
                skillName, "1.0.0", "Use the v1 skill body.", "guide v1");
        assertSkillZip(Query.newInstance().addParam("name", skillName).addParam("version", "2.0.0")
                .addParam("label", "stable"), skillName, "2.0.0", "Use the v2 skill body.", "guide v2");
    }
    
    @Test
    public void testDownloadSkillMissingNameReturnsBadRequest() throws Exception {
        assertError(getRaw(SKILL_CLIENT_PATH + "?namespaceId=" + DEFAULT_NAMESPACE), 400,
                ErrorCode.PARAMETER_MISSING, "Skill name is required");
    }
    
    @Test
    public void testDownloadSkillInvalidNameReturnsBadRequest() throws Exception {
        Query query = Query.newInstance().addParam("name", "invalid_name");
        assertError(getRaw(SKILL_CLIENT_PATH, query), 400, ErrorCode.PARAMETER_VALIDATE_ERROR,
                "Skill name may only contain lowercase letters, numbers, and hyphens");
    }
    
    @Test
    public void testDownloadSkillUnknownResourceReturnsNotFoundResultBody() throws Exception {
        Query query = Query.newInstance().addParam("name", randomSkillName("absent"));
        assertError(getRaw(SKILL_CLIENT_PATH, query), 404, ErrorCode.RESOURCE_NOT_FOUND, "Skill not found");
    }
    
    @Test
    public void testDownloadSkillUnknownVersionAndLabelReturnNotFoundResultBody() throws Exception {
        String skillName = randomSkillName("missing");
        publishSkill(skillName, "1.0.0", null, "Only one online skill body.", "guide");
        addCleanup(() -> deleteSkill(skillName));
        
        assertError(getRaw(SKILL_CLIENT_PATH,
                Query.newInstance().addParam("name", skillName).addParam("version", "9.9.9")),
                404, ErrorCode.RESOURCE_NOT_FOUND, "Skill version not found");
        assertError(getRaw(SKILL_CLIENT_PATH,
                Query.newInstance().addParam("name", skillName).addParam("label", "missing")),
                404, ErrorCode.RESOURCE_NOT_FOUND, "Skill version not found");
    }
    
    private void publishSkill(String skillName, String version, String basedOnVersion, String body,
            String guideContent) throws Exception {
        if (null == basedOnVersion) {
            JsonNode draft = postFormOk(SKILL_ADMIN_PATH + "/draft",
                    buildSkillDraftForm(skillName, version, body, guideContent));
            assertEquals(version, draft.get("data").asText(), draft.toString());
        } else {
            JsonNode draft = postFormOk(SKILL_ADMIN_PATH + "/draft",
                    buildSkillForkForm(skillName, version, basedOnVersion));
            assertEquals(version, draft.get("data").asText(), draft.toString());
            JsonNode updated = putFormOk(SKILL_ADMIN_PATH + "/draft",
                    buildSkillUpdateForm(skillName, body, guideContent));
            assertEquals("ok", updated.get("data").asText(), updated.toString());
        }
        Map<String, String> form = new LinkedHashMap<>();
        form.put("skillName", skillName);
        form.put("version", version);
        JsonNode published = postFormOk(SKILL_ADMIN_PATH + "/force-publish", form);
        assertEquals("ok", published.get("data").asText(), published.toString());
    }
    
    private void updateLabels(String skillName, String labels) throws Exception {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("skillName", skillName);
        form.put("labels", labels);
        JsonNode root = putFormOk(SKILL_ADMIN_PATH + "/labels", form);
        assertEquals("ok", root.get("data").asText(), root.toString());
    }
    
    private void deleteSkill(String skillName) throws Exception {
        deleteQuietly(SKILL_ADMIN_PATH, Query.newInstance().addParam("skillName", skillName));
    }
    
    private Map<String, String> buildSkillDraftForm(String skillName, String version, String body,
            String guideContent) {
        Map<String, String> form = buildSkillUpdateForm(skillName, body, guideContent);
        form.put("targetVersion", version);
        return form;
    }
    
    private Map<String, String> buildSkillForkForm(String skillName, String version, String basedOnVersion) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("skillName", skillName);
        form.put("targetVersion", version);
        form.put("basedOnVersion", basedOnVersion);
        form.put("commitMsg", "openapi skill client it");
        return form;
    }
    
    private Map<String, String> buildSkillUpdateForm(String skillName, String body, String guideContent) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("skillCard", buildSkillCard(skillName, body, guideContent));
        form.put("commitMsg", "openapi skill client it");
        return form;
    }
    
    private String buildSkillCard(String skillName, String body, String guideContent) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("name", skillName);
        card.put("description", "skill client openapi integration test");
        card.put("skillMd", "---\nname: " + skillName
                + "\ndescription: skill client openapi integration test\n---\n\n" + body);
        Map<String, Object> guide = new LinkedHashMap<>();
        guide.put("name", "guide.md");
        guide.put("type", "references");
        guide.put("content", guideContent);
        Map<String, Object> resources = new LinkedHashMap<>();
        resources.put("references::guide.md", guide);
        card.put("resource", resources);
        return JacksonUtils.toJson(card);
    }
    
    private void assertSkillZip(Query query, String skillName, String version, String body, String guideContent)
            throws Exception {
        ByteResponse response = getRawBytes(SKILL_CLIENT_PATH, query);
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
    
    private Map<String, String> unzipTextEntries(byte[] body) throws Exception {
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
    
    private byte[] readEntry(ZipInputStream zis) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int len;
        while ((len = zis.read(buffer)) != -1) {
            output.write(buffer, 0, len);
        }
        return output.toByteArray();
    }
    
    private String randomSkillName(String scenario) {
        return "oit-" + scenario + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
