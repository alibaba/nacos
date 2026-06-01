/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.airegistry.service;

import com.alibaba.nacos.ai.model.skills.SkillIndexManifest;
import com.alibaba.nacos.ai.service.skills.SkillIndexManifestService;
import com.alibaba.nacos.ai.service.skills.SkillOperationService;
import com.alibaba.nacos.api.ai.model.skills.Skill;
import com.alibaba.nacos.api.ai.model.skills.SkillResource;
import com.alibaba.nacos.api.ai.model.skills.SkillSummary;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.airegistry.model.skills.SkillsSearchResponse;
import com.alibaba.nacos.airegistry.model.skills.WellKnownSkillsIndex;
import com.alibaba.nacos.plugin.visibility.constant.VisibilityConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipInputStream;

import static com.alibaba.nacos.ai.constant.Constants.Skills.SEARCH_ACCURATE;
import static com.alibaba.nacos.ai.constant.Constants.Skills.SEARCH_BLUR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link NacosSkillsRegistryService}.
 *
 * @author nacos
 */
@ExtendWith(MockitoExtension.class)
class NacosSkillsRegistryServiceTest {
    
    @Mock
    private SkillOperationService skillOperationService;
    
    @Mock
    private SkillIndexManifestService skillIndexManifestService;
    
    private static final String SCHEMA_0_2 =
        "https://schemas.agentskills.io/discovery/0.2.0/schema.json";
    
    private NacosSkillsRegistryService service;
    
    @BeforeEach
    void setUp() {
        service = new NacosSkillsRegistryService(skillOperationService, skillIndexManifestService);
    }
    
    @Test
    void testBuildLegacyIndexFiltersBinarySkill() throws Exception {
        Page<SkillSummary> page = new Page<>();
        page.setPagesAvailable(1);
        page.setPageItems(
            List.of(buildSummary("text-skill", 5L), buildSummary("binary-skill", 10L)));
        when(skillOperationService.listSkills(eq("public"), eq((String) null), eq(SEARCH_BLUR),
            eq("download_count"),
            eq(1), eq(100))).thenReturn(page);
        when(skillIndexManifestService.query("public", "text-skill"))
            .thenReturn(buildManifest("v1"));
        when(skillIndexManifestService.query("public", "binary-skill"))
            .thenReturn(buildManifest("v1"));
        when(skillOperationService.getSkillVersionDetail("public", "text-skill", "v1")).thenReturn(
            buildTextSkill("text-skill"));
        when(skillOperationService.getSkillVersionDetail("public", "binary-skill", "v1"))
            .thenReturn(
                buildBinarySkill("binary-skill"));
        
        WellKnownSkillsIndex result = service.buildLegacySkillsIndex("public");
        
        assertNotNull(result);
        assertNull(result.getSchema());
        assertEquals(1, result.getSkills().size());
        assertEquals("text-skill", result.getSkills().get(0).getName());
        assertEquals(List.of("SKILL.md", "docs/guide.md"), result.getSkills().get(0).getFiles());
    }
    
    @Test
    void testBuildAgentSkillsIndexUsesVersion020Shape() throws Exception {
        Page<SkillSummary> page = new Page<>();
        page.setPagesAvailable(1);
        page.setPageItems(List.of(buildSummary("a-simple-skill", 3L),
            buildSummary("z-archive-skill", 5L)));
        when(skillOperationService.listSkills(eq("public"), eq((String) null), eq(SEARCH_BLUR),
            eq("download_count"), eq(1), eq(100))).thenReturn(page);
        when(skillIndexManifestService.query("public", "a-simple-skill"))
            .thenReturn(buildManifest("1.0.0"));
        when(skillIndexManifestService.query("public", "z-archive-skill"))
            .thenReturn(buildManifest("1.2.0"));
        when(skillOperationService.getSkillVersionDetail("public", "a-simple-skill", "1.0.0"))
            .thenReturn(buildSimpleSkill("a-simple-skill"));
        when(skillOperationService.getSkillVersionDetail("public", "z-archive-skill", "1.2.0"))
            .thenReturn(buildTextSkill("z-archive-skill"));
        
        WellKnownSkillsIndex result = service.buildAgentSkillsIndex("public");
        
        assertEquals(SCHEMA_0_2, result.getSchema());
        assertEquals(2, result.getSkills().size());
        assertEquals("a-simple-skill", result.getSkills().get(0).getName());
        assertEquals("skill-md", result.getSkills().get(0).getType());
        assertEquals("a-simple-skill/SKILL.md", result.getSkills().get(0).getUrl());
        assertEquals("1.0.0", result.getSkills().get(0).getVersion());
        assertEquals(sha256("a-simple-skill"), result.getSkills().get(0).getDigest());
        assertNull(result.getSkills().get(0).getFiles());
        assertEquals("z-archive-skill", result.getSkills().get(1).getName());
        assertEquals("archive", result.getSkills().get(1).getType());
        assertEquals("z-archive-skill.zip", result.getSkills().get(1).getUrl());
        assertEquals("1.2.0", result.getSkills().get(1).getVersion());
        assertTrue(result.getSkills().get(1).getDigest().startsWith("sha256:"));
        assertNull(result.getSkills().get(1).getFiles());
    }
    
    @Test
    void testSearchBuildsCliShape() throws Exception {
        Page<SkillSummary> page = new Page<>();
        page.setPagesAvailable(1);
        page.setPageItems(List.of(buildSummary("b-skill", 2L), buildSummary("a-skill", 9L)));
        when(skillOperationService.listSkills(eq("public"), eq("demo"), eq(SEARCH_BLUR),
            eq("download_count"), eq(1),
            eq(100))).thenReturn(page);
        when(skillIndexManifestService.query("public", "a-skill")).thenReturn(buildManifest("v1"));
        when(skillIndexManifestService.query("public", "b-skill")).thenReturn(buildManifest("v1"));
        when(skillOperationService.getSkillVersionDetail("public", "a-skill", "v1")).thenReturn(
            buildTextSkill("a-skill"));
        when(skillOperationService.getSkillVersionDetail("public", "b-skill", "v1")).thenReturn(
            buildTextSkill("b-skill"));
        
        SkillsSearchResponse result =
            service.search("public", "demo", 10, "http://localhost/registry/public");
        
        assertEquals(2, result.getSkills().size());
        assertEquals("a-skill", result.getSkills().get(0).getName());
        assertEquals(9L, result.getSkills().get(0).getInstalls());
        assertEquals("http://localhost/registry/public", result.getSkills().get(0).getSource());
    }
    
    @Test
    void testGetSkillFileContent() throws Exception {
        Page<SkillSummary> page = new Page<>();
        page.setPagesAvailable(1);
        page.setPageItems(List.of(buildSummary("demo-skill", 1L)));
        when(skillOperationService.listSkills(eq("public"), eq("demo-skill"), eq(SEARCH_ACCURATE),
            eq("download_count"),
            eq(1), eq(1))).thenReturn(page);
        when(skillIndexManifestService.query("public", "demo-skill"))
            .thenReturn(buildManifest("v1"));
        when(skillOperationService.getSkillVersionDetail("public", "demo-skill", "v1")).thenReturn(
            buildTextSkill("demo-skill"));
        
        String markdown = service.getSkillFileContent("public", "demo-skill", "SKILL.md");
        String file = service.getSkillFileContent("public", "demo-skill", "docs/guide.md");
        String missing = service.getSkillFileContent("public", "demo-skill", "docs/missing.md");
        
        assertTrue(markdown.contains("name: demo-skill"));
        assertEquals("guide", file);
        assertNull(missing);
    }
    
    @Test
    void testGetSkillArchiveContentUsesDownloadAndRootArchive() throws Exception {
        Page<SkillSummary> page = new Page<>();
        page.setPagesAvailable(1);
        page.setPageItems(List.of(buildSummary("demo-skill", 1L)));
        when(skillOperationService.listSkills(eq("public"), eq("demo-skill"), eq(SEARCH_ACCURATE),
            eq("download_count"), eq(1), eq(1))).thenReturn(page);
        when(skillIndexManifestService.query("public", "demo-skill"))
            .thenReturn(buildManifest("v1"));
        when(skillOperationService.downloadSkillVersion("public", "demo-skill", "v1"))
            .thenReturn(buildTextSkill("demo-skill"));
        
        byte[] zipBytes = service.getSkillArchiveContent("public", "demo-skill");
        
        assertZipEntryContains(zipBytes, "SKILL.md", "name: demo-skill");
        assertZipEntryContains(zipBytes, "docs/guide.md", "guide");
        verify(skillOperationService).downloadSkillVersion("public", "demo-skill", "v1");
    }
    
    private SkillSummary buildSummary(String name, Long downloadCount) {
        SkillSummary result = new SkillSummary();
        result.setNamespaceId("public");
        result.setName(name);
        result.setDescription(name + " description");
        result.setEnable(true);
        result.setScope(VisibilityConstants.SCOPE_PUBLIC);
        result.setOnlineCnt(1);
        result.setDownloadCount(downloadCount);
        return result;
    }
    
    private SkillIndexManifest buildManifest(String version) {
        SkillIndexManifest manifest = new SkillIndexManifest();
        manifest.setLabels(Map.of(SkillIndexManifest.LABEL_LATEST, version));
        manifest.setVersions(Map.of(version, List.of("ignored")));
        return manifest;
    }
    
    private Skill buildTextSkill(String name) {
        Skill result = new Skill();
        result.setNamespaceId("public");
        result.setName(name);
        result.setDescription(name + " description");
        result.setSkillMd(
            "---\nname: " + name + "\ndescription: " + name + " description\n---\n\n# " + name);
        SkillResource resource = new SkillResource();
        resource.setType("docs");
        resource.setName("guide.md");
        resource.setContent("guide");
        result.setResource(Map.of("docs::guide.md", resource));
        return result;
    }
    
    private Skill buildSimpleSkill(String name) {
        Skill result = new Skill();
        result.setNamespaceId("public");
        result.setName(name);
        result.setDescription(name + " description");
        result.setSkillMd(
            "---\nname: " + name + "\ndescription: " + name + " description\n---\n\n# " + name);
        return result;
    }
    
    private Skill buildBinarySkill(String name) {
        Skill result = buildTextSkill(name);
        SkillResource binary = new SkillResource();
        binary.setType("assets");
        binary.setName("logo.png");
        binary.setContent("AA==");
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("encoding", "base64");
        binary.setMetadata(metadata);
        result.setResource(Map.of("assets::logo.png", binary));
        return result;
    }
    
    private String sha256(String skillName) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(buildSimpleSkill(skillName).getSkillMd()
            .getBytes(StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder("sha256:");
        for (byte each : hash) {
            result.append(String.format("%02x", each & 0xff));
        }
        return result.toString();
    }
    
    private void assertZipEntryContains(byte[] zipBytes, String entryName, String expected)
        throws Exception {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes),
            StandardCharsets.UTF_8)) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entryName.equals(entry.getName())) {
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    zip.transferTo(output);
                    assertTrue(output.toString(StandardCharsets.UTF_8).contains(expected));
                    return;
                }
            }
        }
        throw new AssertionError("Zip entry not found: " + entryName);
    }
}
