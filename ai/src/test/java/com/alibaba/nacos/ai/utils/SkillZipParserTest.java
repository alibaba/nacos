/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.api.ai.model.skills.Skill;
import com.alibaba.nacos.api.ai.model.skills.SkillResource;
import com.alibaba.nacos.api.ai.model.skills.SkillUtils;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test for SkillZipParser.
 *
 * @author nacos
 */
class SkillZipParserTest {
    
    @Test
    void testParseSkillFromZipWithValidSkillMd() throws Exception {
        // Given
        byte[] zipBytes = createValidSkillZip();
        
        // When
        Skill skill = SkillZipParser.parseSkillFromZip(zipBytes, "test-namespace");
        
        // Then
        assertNotNull(skill);
        assertEquals("test-skill", skill.getName());
        assertEquals("Test skill description", skill.getDescription());
        assertTrue(skill.getSkillMd().contains("This is a test instruction"));
        assertEquals("test-namespace", skill.getNamespaceId());
    }
    
    @Test
    void testParseSkillFromZipWithSkillMdInSubdir() throws Exception {
        // Given
        byte[] zipBytes = createSkillZipWithSubdir();
        
        // When
        Skill skill = SkillZipParser.parseSkillFromZip(zipBytes, "test-namespace");
        
        // Then
        assertNotNull(skill);
        assertEquals("test-skill", skill.getName());
        assertEquals("Test skill description", skill.getDescription());
    }
    
    @Test
    void testParseSkillFromZipPrefersRootSkillMdOverNestedSkillMd() throws Exception {
        // Given: nested SKILL.md appears before root SKILL.md in the zip entry order.
        byte[] zipBytes = createZipWithRootAndNestedSkillMd();
        
        // When
        Skill skill = SkillZipParser.parseSkillFromZip(zipBytes, "test-namespace");
        
        // Then: root SKILL.md is the descriptor; nested SKILL.md remains a normal resource.
        assertNotNull(skill);
        assertEquals("root-skill", skill.getName());
        assertEquals("Root skill description", skill.getDescription());
        String nestedSkillMdKey = SkillUtils.generateResourceId("nested", "SKILL.md");
        assertTrue(skill.getResource().containsKey(nestedSkillMdKey));
        SkillResource nestedSkillMd = skill.getResource().get(nestedSkillMdKey);
        assertEquals("SKILL.md", nestedSkillMd.getName());
        assertEquals("nested", nestedSkillMd.getType());
        assertTrue(nestedSkillMd.getContent().contains("Nested instructions"));
    }
    
    @Test
    void testParseSkillFromZipWithResources() throws Exception {
        // Given
        byte[] zipBytes = createSkillZipWithResources();
        
        // When
        Skill skill = SkillZipParser.parseSkillFromZip(zipBytes, "test-namespace");
        
        // Then
        assertNotNull(skill);
        assertNotNull(skill.getResource());
        assertTrue(skill.getResource().size() > 0);
    }
    
    @Test
    void testParseSkillFromZipWithEscapedYamlValues() throws Exception {
        // Given
        byte[] zipBytes = createZipWithEscapedYamlValues();
        
        // When
        Skill skill = SkillZipParser.parseSkillFromZip(zipBytes, "test-namespace");
        
        // Then
        assertNotNull(skill);
        assertEquals("test\\skill\"name", skill.getName());
        assertEquals("desc\\folder\"quoted", skill.getDescription());
    }
    
    @Test
    void testParseSkillFromZipWithoutSkillMd() throws IOException {
        // Given
        byte[] zipBytes = createZipWithoutSkillMd();
        
        // When & Then
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> SkillZipParser.parseSkillFromZip(zipBytes, "test-namespace"));
        assertTrue(exception.getMessage().contains("SKILL.md file not found"));
    }
    
    @Test
    void testParseSkillFromZipWithInvalidYaml() throws IOException {
        // Given
        byte[] zipBytes = createZipWithInvalidYaml();
        
        // When & Then
        assertThrows(NacosApiException.class,
            () -> SkillZipParser.parseSkillFromZip(zipBytes, "test-namespace"));
    }
    
    @Test
    void testParseSkillFromZipWithMissingName() throws IOException {
        // Given
        byte[] zipBytes = createZipWithMissingName();
        
        // When & Then
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> SkillZipParser.parseSkillFromZip(zipBytes, "test-namespace"));
        assertTrue(exception.getMessage().contains("name"));
    }
    
    @Test
    void testParseSkillFromZipWithMissingDescription() throws IOException {
        // Given
        byte[] zipBytes = createZipWithMissingDescription();
        
        // When & Then
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> SkillZipParser.parseSkillFromZip(zipBytes, "test-namespace"));
        assertTrue(exception.getMessage().contains("description"));
    }
    
    @Test
    void testParseSkillFromZipWithInstructionsHeader() throws Exception {
        // Given
        byte[] zipBytes = createZipWithInstructionsHeader();
        
        // When
        Skill skill = SkillZipParser.parseSkillFromZip(zipBytes, "test-namespace");
        
        // Then
        assertNotNull(skill);
        assertTrue(skill.getSkillMd().contains("instruction content"));
    }
    
    @Test
    void testParseSkillFromZipWithBinaryResource() throws Exception {
        // Given: zip with a .ttf (binary) and SKILL.md
        byte[] zipBytes = createSkillZipWithBinaryResource();
        
        // When
        Skill skill = SkillZipParser.parseSkillFromZip(zipBytes, "test-namespace");
        
        // Then: binary resource is Base64-encoded and has metadata encoding=base64 (key = generateResourceId("canvas-fonts", "font.ttf"))
        String fontKey = SkillUtils.generateResourceId("canvas-fonts", "font.ttf");
        assertNotNull(skill);
        assertNotNull(skill.getResource());
        assertTrue(skill.getResource().containsKey(fontKey));
        SkillResource font = skill.getResource().get(fontKey);
        assertEquals("font.ttf", font.getName());
        assertNotNull(font.getContent());
        assertTrue(font.getContent().length() > 0);
        byte[] decoded = Base64.getDecoder().decode(font.getContent());
        assertNotNull(decoded);
        assertEquals(4, decoded.length);
        assertEquals(0, decoded[0]);
        assertEquals(1, decoded[1]);
        Map<String, Object> meta = font.getMetadata();
        assertNotNull(meta);
        assertEquals("base64", meta.get("encoding"));
    }
    
    @Test
    void testResolveVersionFromZipUsesSiblingMetaJsonWhenSkillMdMissingVersion() throws Exception {
        byte[] zipBytes = createZipWithSkillMdAndMetaSibling("skills/test-skill", "1.1.3");
        
        String version = SkillZipParser.resolveVersionFromZip(zipBytes);
        
        assertEquals("1.1.3", version);
    }
    
    @Test
    void testResolveVersionFromZipPrefersRootSkillMdSiblingMetaJson() throws Exception {
        byte[] zipBytes = createZipWithRootAndNestedSkillMdAndMetaJson();
        
        String version = SkillZipParser.resolveVersionFromZip(zipBytes);
        
        assertEquals("1.0.0", version);
    }
    
    @Test
    void testParseYamlFrontMatterFromMarkdownSupportsMetadataVersion() {
        String markdown =
            "---\n" + "name: baidu-search\n" + "description: test\n" + "metadata:\n"
                + "  author: example-org\n"
                + "  version: \"1.0\"\n" + "---\n\n" + "body";
        
        Map<String, String> result = SkillZipParser.parseYamlFrontMatterFromMarkdown(markdown);
        
        assertEquals("1.0", result.get("metadata.version"));
    }
    
    @Test
    void testParseSkillFromZipExceedsSizeLimit() throws IOException {
        // Given: zip larger than MAX_UPLOAD_ZIP_BYTES (10MB)
        int overSize = (int) (Constants.Skills.MAX_UPLOAD_ZIP_BYTES + 1024);
        byte[] zipBytes = createValidSkillZip();
        byte[] largeZip = new byte[overSize];
        System.arraycopy(zipBytes, 0, largeZip, 0, zipBytes.length);
        
        // When & Then
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> SkillZipParser.parseSkillFromZip(largeZip, "test-namespace"));
        assertTrue(exception.getMessage().contains("must not exceed"));
        assertTrue(exception.getMessage().contains("10"));
    }
    
    @Test
    void testParseSkillFromZipIgnoresMacOsMetadataFiles() throws Exception {
        // Given: zip contains macOS AppleDouble file (._LICENSE.txt) and normal resource
        byte[] zipBytes = createSkillZipWithMacOsMetadataFiles();
        
        // When
        Skill skill = SkillZipParser.parseSkillFromZip(zipBytes, "test-namespace");
        
        // Then: skill parses OK and ._* files are not in resources (key = generateResourceId("references", "readme.md"))
        String readmeKey = SkillUtils.generateResourceId("references", "readme.md");
        assertNotNull(skill);
        assertNotNull(skill.getResource());
        assertEquals(1, skill.getResource().size());
        assertTrue(skill.getResource().containsKey(readmeKey));
        assertFalse(skill.getResource().containsKey("._LICENSE"));
        assertFalse(skill.getResource().keySet().stream().anyMatch(k -> k.startsWith("._")));
    }
    
    @Test
    void testParseSkillFromZipIncludesFilesUnderSkillRoot() throws Exception {
        // Given: zip with file directly under skill folder (e.g. algorithmic-art/LICENSE.txt)
        byte[] zipBytes = createSkillZipWithFileUnderSkillRoot();
        
        // When
        Skill skill = SkillZipParser.parseSkillFromZip(zipBytes, "test-namespace");
        
        // Then: LICENSE.txt is included as resource with empty type (key = generateResourceId("", "LICENSE.txt"))
        String licenseKey = SkillUtils.generateResourceId("", "LICENSE.txt");
        assertNotNull(skill);
        assertNotNull(skill.getResource());
        assertTrue(skill.getResource().containsKey(licenseKey));
        assertEquals("LICENSE.txt", skill.getResource().get(licenseKey).getName());
        assertEquals("", skill.getResource().get(licenseKey).getType() == null ? ""
            : skill.getResource().get(licenseKey).getType());
        assertTrue(skill.getResource().get(licenseKey).getContent().contains("MIT License"));
    }
    
    @Test
    void testParseMultipleSkillsFromZipWithSingleSkill() throws Exception {
        // Given: zip with single SKILL.md (should delegate to single-skill parsing)
        byte[] zipBytes = createValidSkillZip();
        
        // When
        SkillZipParser.MultiSkillParseResult result =
            SkillZipParser.parseMultipleSkillsFromZip(zipBytes, "test-namespace");
        
        // Then
        assertNotNull(result.getSkills());
        assertEquals(1, result.getSkills().size());
        assertEquals("test-skill", result.getSkills().get(0).getName());
        assertTrue(result.getFailures().isEmpty());
    }
    
    @Test
    void testParseMultipleSkillsFromZipWithRootSkillMdTreatsArchiveAsSingleSkill()
        throws Exception {
        byte[] zipBytes = createZipWithRootAndNestedSkillMd();
        
        SkillZipParser.MultiSkillParseResult result =
            SkillZipParser.parseMultipleSkillsFromZip(zipBytes, "test-namespace");
        
        assertNotNull(result.getSkills());
        assertEquals(1, result.getSkills().size());
        Skill skill = result.getSkills().get(0);
        assertEquals("root-skill", skill.getName());
        String nestedSkillMdKey = SkillUtils.generateResourceId("nested", "SKILL.md");
        assertTrue(skill.getResource().containsKey(nestedSkillMdKey));
        assertTrue(result.getFailures().isEmpty());
    }
    
    @Test
    void testParseMultipleSkillsFromZipWithMultipleSkills() throws Exception {
        // Given: zip with two skill subdirectories
        byte[] zipBytes = createMultiSkillZip();
        
        // When
        SkillZipParser.MultiSkillParseResult result =
            SkillZipParser.parseMultipleSkillsFromZip(zipBytes, "test-namespace");
        
        // Then
        List<Skill> skills = result.getSkills();
        assertNotNull(skills);
        assertEquals(2, skills.size());
        assertTrue(skills.stream().anyMatch(s -> "skill-alpha".equals(s.getName())));
        assertTrue(skills.stream().anyMatch(s -> "skill-beta".equals(s.getName())));
        // Verify namespaceId is set
        skills.forEach(s -> assertEquals("test-namespace", s.getNamespaceId()));
        assertTrue(result.getFailures().isEmpty());
    }
    
    @Test
    void testParseMultipleSkillsFromZipWithResources() throws Exception {
        // Given: multi-skill zip where each skill has its own resources
        byte[] zipBytes = createMultiSkillZipWithResources();
        
        // When
        SkillZipParser.MultiSkillParseResult result =
            SkillZipParser.parseMultipleSkillsFromZip(zipBytes, "test-namespace");
        
        // Then
        List<Skill> skills = result.getSkills();
        assertNotNull(skills);
        assertEquals(2, skills.size());
        Skill alpha =
            skills.stream().filter(s -> "skill-alpha".equals(s.getName())).findFirst().orElse(null);
        Skill beta =
            skills.stream().filter(s -> "skill-beta".equals(s.getName())).findFirst().orElse(null);
        assertNotNull(alpha);
        assertNotNull(beta);
        // alpha has a script resource
        assertNotNull(alpha.getResource());
        assertTrue(alpha.getResource().size() > 0);
        // beta has a config resource
        assertNotNull(beta.getResource());
        assertTrue(beta.getResource().size() > 0);
    }
    
    @Test
    void testParseMultipleSkillsFromZipWithNoSkillMd() throws IOException {
        // Given: zip with no SKILL.md at all
        byte[] zipBytes = createZipWithoutSkillMd();
        
        // When & Then
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> SkillZipParser.parseMultipleSkillsFromZip(zipBytes, "test-namespace"));
        assertTrue(exception.getMessage().contains("SKILL.md file not found"));
    }
    
    @Test
    void testParseMultipleSkillsFromZipWithEmptyBytes() {
        // When & Then
        assertThrows(NacosApiException.class,
            () -> SkillZipParser.parseMultipleSkillsFromZip(new byte[0], "test-namespace"));
    }
    
    @Test
    void testParseMultipleSkillsFromZipSkipsInvalidFolder() throws Exception {
        // Given: zip with one valid skill and one invalid skill (malformed YAML)
        byte[] zipBytes = createMultiSkillZipWithInvalidFolder();
        
        // When
        SkillZipParser.MultiSkillParseResult result =
            SkillZipParser.parseMultipleSkillsFromZip(zipBytes, "test-namespace");
        
        // Then: only the valid skill is returned, the invalid one is recorded as failure
        assertNotNull(result.getSkills());
        assertEquals(1, result.getSkills().size());
        assertEquals("skill-valid", result.getSkills().get(0).getName());
        
        assertEquals(1, result.getFailures().size());
        assertEquals("skill-broken", result.getFailures().get(0).getFolder());
        assertNotNull(result.getFailures().get(0).getReason());
    }
    
    @Test
    void testParseMultipleSkillsFromZipWarnsForFolderWithoutSkillMd() throws Exception {
        // Given: zip with two valid skills, one random folder without SKILL.md, and a .git folder
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry entry = new ZipEntry("skill-a/SKILL.md");
            zos.putNextEntry(entry);
            zos.write("---\nname: skill-a\ndescription: A\n---\n\nA".getBytes());
            zos.closeEntry();
            
            entry = new ZipEntry("skill-b/SKILL.md");
            zos.putNextEntry(entry);
            zos.write("---\nname: skill-b\ndescription: B\n---\n\nB".getBytes());
            zos.closeEntry();
            
            // Folder without SKILL.md - should produce a warning
            entry = new ZipEntry("my-utils/helper.py");
            zos.putNextEntry(entry);
            zos.write("print('hello')".getBytes());
            zos.closeEntry();
            
            // .git folder - should be silently ignored
            entry = new ZipEntry(".git/config");
            zos.putNextEntry(entry);
            zos.write("[core]".getBytes());
            zos.closeEntry();
        }
        byte[] zipBytes = baos.toByteArray();
        
        // When
        SkillZipParser.MultiSkillParseResult result =
            SkillZipParser.parseMultipleSkillsFromZip(zipBytes, "test-namespace");
        
        // Then: 2 skills parsed, 1 warning for my-utils/, no warning for .git/
        assertEquals(2, result.getSkills().size());
        assertEquals(1, result.getFailures().size());
        assertEquals("my-utils", result.getFailures().get(0).getFolder());
        assertTrue(result.getFailures().get(0).getReason().contains("SKILL.md not found"));
    }
    
    @Test
    void testParseMultipleSkillsFromZipWarnsCorrectPathForNestedStructure() throws Exception {
        // Given: zip with nested structure: parent/skill-a/SKILL.md, parent/skill-b/SKILL.md,
        //        parent/random-lib/util.js (no SKILL.md)
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry entry = new ZipEntry("parent/skill-a/SKILL.md");
            zos.putNextEntry(entry);
            zos.write("---\nname: skill-a\ndescription: A\n---\n\nA".getBytes());
            zos.closeEntry();
            
            entry = new ZipEntry("parent/skill-b/SKILL.md");
            zos.putNextEntry(entry);
            zos.write("---\nname: skill-b\ndescription: B\n---\n\nB".getBytes());
            zos.closeEntry();
            
            // Sibling folder without SKILL.md
            entry = new ZipEntry("parent/random-lib/util.js");
            zos.putNextEntry(entry);
            zos.write("export default {}".getBytes());
            zos.closeEntry();
        }
        byte[] zipBytes = baos.toByteArray();
        
        // When
        SkillZipParser.MultiSkillParseResult result =
            SkillZipParser.parseMultipleSkillsFromZip(zipBytes, "test-namespace");
        
        // Then: folder name "random-lib" is reported, not full path
        assertEquals(2, result.getSkills().size());
        assertEquals(1, result.getFailures().size());
        assertEquals("random-lib", result.getFailures().get(0).getFolder());
    }
    
    /**
     * Create a valid skill zip file.
     */
    private byte[] createValidSkillZip() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // Add SKILL.md
            ZipEntry entry = new ZipEntry("SKILL.md");
            zos.putNextEntry(entry);
            String skillMd =
                "---\n" + "name: test-skill\n" + "description: Test skill description\n" + "---\n\n"
                    + "This is a test instruction";
            zos.write(skillMd.getBytes());
            zos.closeEntry();
        }
        return baos.toByteArray();
    }
    
    private byte[] createZipWithSkillMdAndMetaSibling(String dir, String metaVersion)
        throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        String prefix = (dir == null || dir.isEmpty()) ? "" : (dir.endsWith("/") ? dir : dir + "/");
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry skillMd = new ZipEntry(prefix + "SKILL.md");
            zos.putNextEntry(skillMd);
            String skillMdContent =
                "---\n" + "name: test-skill\n" + "description: Test skill description\n" + "---\n\n"
                    + "This is a test instruction";
            zos.write(skillMdContent.getBytes());
            zos.closeEntry();
            
            ZipEntry meta = new ZipEntry(prefix + "_meta.json");
            zos.putNextEntry(meta);
            String metaJson =
                "{\n" + "  \"ownerId\": \"kn7akgt520t01vgs2tzx7yk6m180kt26\",\n"
                    + "  \"slug\": \"baidu-search\",\n"
                    + "  \"version\": \"" + metaVersion + "\",\n"
                    + "  \"publishedAt\": 1773828934466\n" + "}";
            zos.write(metaJson.getBytes());
            zos.closeEntry();
        }
        return baos.toByteArray();
    }
    
    private byte[] createZipWithRootAndNestedSkillMd() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry entry = new ZipEntry("nested/SKILL.md");
            zos.putNextEntry(entry);
            String nestedSkillMd =
                "---\n" + "name: nested-skill\n" + "description: Nested skill description\n"
                    + "---\n\n" + "Nested instructions";
            zos.write(nestedSkillMd.getBytes());
            zos.closeEntry();
            
            entry = new ZipEntry("SKILL.md");
            zos.putNextEntry(entry);
            String rootSkillMd =
                "---\n" + "name: root-skill\n" + "description: Root skill description\n"
                    + "---\n\n" + "Root instructions";
            zos.write(rootSkillMd.getBytes());
            zos.closeEntry();
        }
        return baos.toByteArray();
    }
    
    private byte[] createZipWithRootAndNestedSkillMdAndMetaJson() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry entry = new ZipEntry("nested/SKILL.md");
            zos.putNextEntry(entry);
            String nestedSkillMd =
                "---\n" + "name: nested-skill\n" + "description: Nested skill description\n"
                    + "version: 9.9.9\n" + "---\n\n" + "Nested instructions";
            zos.write(nestedSkillMd.getBytes());
            zos.closeEntry();
            
            entry = new ZipEntry("nested/_meta.json");
            zos.putNextEntry(entry);
            zos.write("{\"version\":\"9.9.9\"}".getBytes());
            zos.closeEntry();
            
            entry = new ZipEntry("SKILL.md");
            zos.putNextEntry(entry);
            String rootSkillMd =
                "---\n" + "name: root-skill\n" + "description: Root skill description\n"
                    + "---\n\n" + "Root instructions";
            zos.write(rootSkillMd.getBytes());
            zos.closeEntry();
            
            entry = new ZipEntry("_meta.json");
            zos.putNextEntry(entry);
            zos.write("{\"version\":\"1.0.0\"}".getBytes());
            zos.closeEntry();
        }
        return baos.toByteArray();
    }
    
    /**
     * Create a skill zip with SKILL.md in subdirectory.
     */
    private byte[] createSkillZipWithSubdir() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // Add SKILL.md in subdirectory
            ZipEntry entry = new ZipEntry("test-skill/SKILL.md");
            zos.putNextEntry(entry);
            String skillMd =
                "---\n" + "name: test-skill\n" + "description: Test skill description\n" + "---\n\n"
                    + "This is a test instruction";
            zos.write(skillMd.getBytes());
            zos.closeEntry();
        }
        return baos.toByteArray();
    }
    
    /**
     * Create a skill zip with resources.
     */
    private byte[] createSkillZipWithResources() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // Add SKILL.md
            ZipEntry entry = new ZipEntry("SKILL.md");
            zos.putNextEntry(entry);
            String skillMd =
                "---\n" + "name: test-skill\n" + "description: Test skill description\n" + "---\n\n"
                    + "This is a test instruction";
            zos.write(skillMd.getBytes());
            zos.closeEntry();
            
            // Add resource file
            entry = new ZipEntry("scripts/test.sh");
            zos.putNextEntry(entry);
            zos.write("#!/bin/bash\necho 'test'".getBytes());
            zos.closeEntry();
        }
        return baos.toByteArray();
    }
    
    /**
     * Create a zip without SKILL.md.
     */
    private byte[] createZipWithoutSkillMd() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry entry = new ZipEntry("other-file.txt");
            zos.putNextEntry(entry);
            zos.write("content".getBytes());
            zos.closeEntry();
        }
        return baos.toByteArray();
    }
    
    /**
     * Create a zip with invalid YAML.
     */
    private byte[] createZipWithInvalidYaml() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry entry = new ZipEntry("SKILL.md");
            zos.putNextEntry(entry);
            String skillMd = "Invalid content without YAML front matter";
            zos.write(skillMd.getBytes());
            zos.closeEntry();
        }
        return baos.toByteArray();
    }
    
    /**
     * Create a zip with missing name.
     */
    private byte[] createZipWithMissingName() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry entry = new ZipEntry("SKILL.md");
            zos.putNextEntry(entry);
            String skillMd =
                "---\n" + "description: Test skill description\n" + "---\n\n"
                    + "This is a test instruction";
            zos.write(skillMd.getBytes());
            zos.closeEntry();
        }
        return baos.toByteArray();
    }
    
    /**
     * Create a zip with missing description.
     */
    private byte[] createZipWithMissingDescription() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry entry = new ZipEntry("SKILL.md");
            zos.putNextEntry(entry);
            String skillMd =
                "---\n" + "name: test-skill\n" + "---\n\n" + "This is a test instruction";
            zos.write(skillMd.getBytes());
            zos.closeEntry();
        }
        return baos.toByteArray();
    }
    
    /**
     * Create a zip with escaped YAML values in front matter.
     */
    private byte[] createZipWithEscapedYamlValues() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry entry = new ZipEntry("SKILL.md");
            zos.putNextEntry(entry);
            String skillMd =
                "---\n" + "name: \"test\\\\skill\\\"name\"\n"
                    + "description: \"desc\\\\folder\\\"quoted\"\n"
                    + "---\n\n" + "This is a test instruction";
            zos.write(skillMd.getBytes());
            zos.closeEntry();
        }
        return baos.toByteArray();
    }
    
    /**
     * Create a skill zip with a file directly under skill root (skillName/LICENSE.txt). Parser should include it as
     * resource with empty type.
     */
    private byte[] createSkillZipWithFileUnderSkillRoot() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry entry = new ZipEntry("test-skill/SKILL.md");
            zos.putNextEntry(entry);
            String skillMd =
                "---\n" + "name: test-skill\n" + "description: Test skill description\n" + "---\n\n"
                    + "This is a test instruction";
            zos.write(skillMd.getBytes());
            zos.closeEntry();
            
            entry = new ZipEntry("test-skill/LICENSE.txt");
            zos.putNextEntry(entry);
            zos.write("MIT License\nCopyright (c) 2025".getBytes());
            zos.closeEntry();
        }
        return baos.toByteArray();
    }
    
    /**
     * Create a skill zip that contains macOS metadata files (._*) like ._LICENSE.txt. Parser should ignore them and
     * only include normal resources.
     */
    private byte[] createSkillZipWithMacOsMetadataFiles() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry entry = new ZipEntry("test-skill/SKILL.md");
            zos.putNextEntry(entry);
            String skillMd =
                "---\n" + "name: test-skill\n" + "description: Test skill description\n" + "---\n\n"
                    + "This is a test instruction";
            zos.write(skillMd.getBytes());
            zos.closeEntry();
            
            entry = new ZipEntry("test-skill/references/readme.md");
            zos.putNextEntry(entry);
            zos.write("# Readme".getBytes());
            zos.closeEntry();
            
            entry = new ZipEntry("test-skill/._LICENSE.txt");
            zos.putNextEntry(entry);
            zos.write(new byte[] {0, 5, 0, 0}); // binary AppleDouble-like content
            zos.closeEntry();
        }
        return baos.toByteArray();
    }
    
    /**
     * Create a skill zip that contains a binary file (.ttf). Parser should store it as Base64 with metadata
     * encoding=base64.
     */
    private byte[] createSkillZipWithBinaryResource() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry entry = new ZipEntry("test-skill/SKILL.md");
            zos.putNextEntry(entry);
            String skillMd =
                "---\n" + "name: test-skill\n" + "description: Test skill description\n" + "---\n\n"
                    + "This is a test instruction";
            zos.write(skillMd.getBytes());
            zos.closeEntry();
            
            entry = new ZipEntry("test-skill/canvas-fonts/font.ttf");
            zos.putNextEntry(entry);
            zos.write(new byte[] {0, 1, 2, 3}); // minimal binary content
            zos.closeEntry();
        }
        return baos.toByteArray();
    }
    
    /**
     * Create a zip with Instructions header.
     */
    private byte[] createZipWithInstructionsHeader() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry entry = new ZipEntry("SKILL.md");
            zos.putNextEntry(entry);
            String skillMd =
                "---\n" + "name: test-skill\n" + "description: Test skill description\n" + "---\n\n"
                    + "## Instructions\n" + "instruction content";
            zos.write(skillMd.getBytes());
            zos.closeEntry();
        }
        return baos.toByteArray();
    }
    
    @Test
    void testParseSkillFromZipWithUtf8Bom() throws Exception {
        // Given: SKILL.md content starts with UTF-8 BOM (EF BB BF)
        byte[] zipBytes = createSkillZipWithUtf8Bom();
        
        // When
        Skill skill = SkillZipParser.parseSkillFromZip(zipBytes, "test-namespace");
        
        // Then: BOM is stripped and skill parses correctly
        assertNotNull(skill);
        assertEquals("test-skill", skill.getName());
        assertEquals("Test skill description", skill.getDescription());
        assertEquals("---\n" + "name: test-skill\n" + "description: Test skill description\n"
            + "---\n" + "\n"
            + "This is a test instruction", skill.getSkillMd().trim());
    }
    
    @Test
    void testParseSkillFromZipWithUtf8BomInSubdir() throws Exception {
        // Given: SKILL.md in subdirectory with UTF-8 BOM
        byte[] zipBytes = createSkillZipWithUtf8BomInSubdir();
        
        // When
        Skill skill = SkillZipParser.parseSkillFromZip(zipBytes, "test-namespace");
        
        // Then: BOM is stripped and skill parses correctly
        assertNotNull(skill);
        assertEquals("test-skill", skill.getName());
        assertEquals("Test skill description", skill.getDescription());
    }
    
    /**
     * Create a skill zip where SKILL.md content starts with UTF-8 BOM (EF BB BF).
     */
    private byte[] createSkillZipWithUtf8Bom() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry entry = new ZipEntry("SKILL.md");
            zos.putNextEntry(entry);
            // UTF-8 BOM bytes followed by normal SKILL.md content
            String skillMd =
                "---\n" + "name: test-skill\n" + "description: Test skill description\n" + "---\n\n"
                    + "This is a test instruction";
            byte[] bom = new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
            byte[] content = skillMd.getBytes("UTF-8");
            byte[] withBom = new byte[bom.length + content.length];
            System.arraycopy(bom, 0, withBom, 0, bom.length);
            System.arraycopy(content, 0, withBom, bom.length, content.length);
            zos.write(withBom);
            zos.closeEntry();
        }
        return baos.toByteArray();
    }
    
    /**
     * Create a skill zip where SKILL.md in subdirectory has UTF-8 BOM.
     */
    private byte[] createSkillZipWithUtf8BomInSubdir() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry entry = new ZipEntry("test-skill/SKILL.md");
            zos.putNextEntry(entry);
            String skillMd =
                "---\n" + "name: test-skill\n" + "description: Test skill description\n" + "---\n\n"
                    + "This is a test instruction";
            byte[] bom = new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
            byte[] content = skillMd.getBytes("UTF-8");
            byte[] withBom = new byte[bom.length + content.length];
            System.arraycopy(bom, 0, withBom, 0, bom.length);
            System.arraycopy(content, 0, withBom, bom.length, content.length);
            zos.write(withBom);
            zos.closeEntry();
        }
        return baos.toByteArray();
    }
    
    /**
     * Create a multi-skill zip with two skill subdirectories, each having its own SKILL.md.
     */
    private byte[] createMultiSkillZip() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // Skill alpha
            ZipEntry entry = new ZipEntry("skill-alpha/SKILL.md");
            zos.putNextEntry(entry);
            String skillMdAlpha =
                "---\n" + "name: skill-alpha\n" + "description: Alpha skill\n" + "---\n\n"
                    + "Alpha instructions";
            zos.write(skillMdAlpha.getBytes());
            zos.closeEntry();
            
            // Skill beta
            entry = new ZipEntry("skill-beta/SKILL.md");
            zos.putNextEntry(entry);
            String skillMdBeta =
                "---\n" + "name: skill-beta\n" + "description: Beta skill\n" + "---\n\n"
                    + "Beta instructions";
            zos.write(skillMdBeta.getBytes());
            zos.closeEntry();
        }
        return baos.toByteArray();
    }
    
    /**
     * Create a multi-skill zip where each skill has its own resource files.
     */
    private byte[] createMultiSkillZipWithResources() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // Skill alpha with script resource
            ZipEntry entry = new ZipEntry("skill-alpha/SKILL.md");
            zos.putNextEntry(entry);
            String skillMdAlpha =
                "---\n" + "name: skill-alpha\n" + "description: Alpha skill\n" + "---\n\n"
                    + "Alpha instructions";
            zos.write(skillMdAlpha.getBytes());
            zos.closeEntry();
            
            entry = new ZipEntry("skill-alpha/scripts/run.sh");
            zos.putNextEntry(entry);
            zos.write("#!/bin/bash\necho 'alpha'".getBytes());
            zos.closeEntry();
            
            // Skill beta with config resource
            entry = new ZipEntry("skill-beta/SKILL.md");
            zos.putNextEntry(entry);
            String skillMdBeta =
                "---\n" + "name: skill-beta\n" + "description: Beta skill\n" + "---\n\n"
                    + "Beta instructions";
            zos.write(skillMdBeta.getBytes());
            zos.closeEntry();
            
            entry = new ZipEntry("skill-beta/configs/settings.yaml");
            zos.putNextEntry(entry);
            zos.write("key: value".getBytes());
            zos.closeEntry();
        }
        return baos.toByteArray();
    }
    
    /**
     * Create a multi-skill zip with one valid skill and one invalid skill (malformed YAML front-matter).
     */
    private byte[] createMultiSkillZipWithInvalidFolder() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // Valid skill
            ZipEntry entry = new ZipEntry("skill-valid/SKILL.md");
            zos.putNextEntry(entry);
            String validMd =
                "---\nname: skill-valid\ndescription: Valid skill\n---\n\nValid instructions";
            zos.write(validMd.getBytes());
            zos.closeEntry();
            
            // Invalid skill: malformed YAML front-matter (unclosed braces)
            entry = new ZipEntry("skill-broken/SKILL.md");
            zos.putNextEntry(entry);
            String invalidMd = "---\nname: {{{invalid yaml\n---\n\nBroken instructions";
            zos.write(invalidMd.getBytes());
            zos.closeEntry();
        }
        return baos.toByteArray();
    }
}
