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

package com.alibaba.nacos.ai.utils;

import com.alibaba.nacos.api.ai.model.skills.Skill;
import com.alibaba.nacos.api.ai.model.skills.SkillResource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.springframework.core.io.ClassPathResource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashSet;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillSeedArchiveReaderTest {
    
    @Test
    void shouldBuildStandaloneSkillZipWithTopLevelResources() throws Exception {
        byte[] archive = buildArchive(
            new ArchiveEntry("vendor/demo-skill/SKILL.md", buildSkillMarkdown("demo-skill")),
            new ArchiveEntry("vendor/demo-skill/LICENSE.txt", "license"),
            new ArchiveEntry("vendor/demo-skill/references/guide.md", "guide"));
        
        List<SkillSeedArchiveReader.SkillPackage> actual =
            SkillSeedArchiveReader.read(new ByteArrayInputStream(archive));
        
        assertEquals(1, actual.size());
        assertEquals("demo-skill", actual.get(0).getSkillName());
        assertEquals("vendor", actual.get(0).getFrom());
        
        Skill skill = SkillZipParser.parseSkillFromZip(actual.get(0).getZipBytes(), "public");
        assertEquals("demo-skill", skill.getName());
        assertNotNull(findResource(skill, "", "LICENSE.txt"));
        assertNotNull(findResource(skill, "references", "guide.md"));
    }
    
    @Test
    void shouldSkipDuplicateSkillNames() throws Exception {
        byte[] archive = buildArchive(
            new ArchiveEntry("source-a/demo/SKILL.md", buildSkillMarkdown("same-skill")),
            new ArchiveEntry("source-b/demo/SKILL.md", buildSkillMarkdown("same-skill")));
        
        List<SkillSeedArchiveReader.SkillPackage> actual =
            SkillSeedArchiveReader.read(new ByteArrayInputStream(archive));
        
        assertEquals(1, actual.size());
        assertEquals("same-skill", actual.get(0).getSkillName());
    }
    
    @Test
    void shouldParseNestedFromPath() throws Exception {
        byte[] archive = buildArchive(new ArchiveEntry("github.com/nacos/find-skills/SKILL.md",
            buildSkillMarkdown("find-skills")));
        List<SkillSeedArchiveReader.SkillPackage> actual =
            SkillSeedArchiveReader.read(new ByteArrayInputStream(archive));
        assertEquals(1, actual.size());
        assertEquals("github.com/nacos", actual.get(0).getFrom());
    }
    
    @Test
    void shouldParseAllBundledSkillsFromArchive() throws Exception {
        ClassPathResource resource = new ClassPathResource("bootstrap/skills-data.zip");
        Assumptions.assumeTrue(resource.exists(),
            "bootstrap/skills-data.zip is not bundled in this test runtime");
        try (InputStream inputStream = resource.getInputStream()) {
            List<SkillSeedArchiveReader.SkillPackage> actual =
                SkillSeedArchiveReader.read(inputStream);
            
            assertEquals(139, actual.size());
            Set<String> skillNames = new HashSet<>();
            for (SkillSeedArchiveReader.SkillPackage each : actual) {
                skillNames.add(each.getSkillName());
                Skill skill = SkillZipParser.parseSkillFromZip(each.getZipBytes(), "public");
                assertNotNull(skill);
                assertEquals(each.getSkillName(), skill.getName());
            }
            assertTrue(skillNames.contains("algorithmic-art"));
            assertTrue(skillNames.contains("mcp-builder"));
            assertTrue(skillNames.contains("openai-docs"));
            assertTrue(skillNames.contains("playwright"));
            assertTrue(skillNames.contains("skill-installer"));
            assertTrue(skillNames.contains("brainstorming"));
            assertTrue(skillNames.contains("systematic-debugging"));
            assertTrue(skillNames.contains("using-git-worktrees"));
            assertTrue(skillNames.contains("ab-test-setup"));
            assertTrue(skillNames.contains("ai-seo"));
            assertTrue(skillNames.contains("adapt"));
            assertTrue(skillNames.contains("audit"));
            assertTrue(skillNames.contains("optimize"));
            assertTrue(skillNames.contains("social-content"));
            assertTrue(skillNames.contains("teach-impeccable"));
            assertTrue(skillNames.contains("typeset"));
            assertTrue(skillNames.contains("nacos-skill-registry"));
            assertFalse(skillNames.contains("find-skills"));
        }
    }
    
    private static SkillResource findResource(Skill skill, String type, String name) {
        if (skill.getResource() == null) {
            return null;
        }
        for (SkillResource resource : skill.getResource().values()) {
            if (resource == null) {
                continue;
            }
            String actualType = resource.getType() == null ? "" : resource.getType();
            if (actualType.equals(type) && name.equals(resource.getName())) {
                return resource;
            }
        }
        return null;
    }
    
    private static byte[] buildArchive(ArchiveEntry... entries) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            for (ArchiveEntry entry : entries) {
                zos.putNextEntry(new ZipEntry(entry.path));
                zos.write(entry.content.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        return out.toByteArray();
    }
    
    private static String buildSkillMarkdown(String name) {
        return "---\n"
            + "name: " + name + "\n"
            + "description: demo\n"
            + "---\n\n"
            + "# Demo\n\n"
            + "## Instructions\n\n"
            + "demo";
    }
    
    private static final class ArchiveEntry {
        
        private final String path;
        
        private final String content;
        
        private ArchiveEntry(String path, String content) {
            this.path = path;
            this.content = content;
        }
    }
}
