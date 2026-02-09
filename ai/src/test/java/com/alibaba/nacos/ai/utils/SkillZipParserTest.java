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
        assertEquals("This is a test instruction", skill.getInstruction().trim());
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
        assertTrue(skill.getInstruction().contains("instruction content"));
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
        assertEquals("", skill.getResource().get(licenseKey).getType() == null ? "" : skill.getResource().get(licenseKey).getType());
        assertTrue(skill.getResource().get(licenseKey).getContent().contains("MIT License"));
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
            String skillMd = "---\n"
                    + "name: test-skill\n"
                    + "description: Test skill description\n"
                    + "---\n\n"
                    + "This is a test instruction";
            zos.write(skillMd.getBytes());
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
            String skillMd = "---\n"
                    + "name: test-skill\n"
                    + "description: Test skill description\n"
                    + "---\n\n"
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
            String skillMd = "---\n"
                    + "name: test-skill\n"
                    + "description: Test skill description\n"
                    + "---\n\n"
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
            String skillMd = "---\n"
                    + "description: Test skill description\n"
                    + "---\n\n"
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
            String skillMd = "---\n"
                    + "name: test-skill\n"
                    + "---\n\n"
                    + "This is a test instruction";
            zos.write(skillMd.getBytes());
            zos.closeEntry();
        }
        return baos.toByteArray();
    }
    
    /**
     * Create a skill zip with a file directly under skill root (skillName/LICENSE.txt).
     * Parser should include it as resource with empty type.
     */
    private byte[] createSkillZipWithFileUnderSkillRoot() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry entry = new ZipEntry("test-skill/SKILL.md");
            zos.putNextEntry(entry);
            String skillMd = "---\n"
                    + "name: test-skill\n"
                    + "description: Test skill description\n"
                    + "---\n\n"
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
     * Create a skill zip that contains macOS metadata files (._*) like ._LICENSE.txt.
     * Parser should ignore them and only include normal resources.
     */
    private byte[] createSkillZipWithMacOsMetadataFiles() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry entry = new ZipEntry("test-skill/SKILL.md");
            zos.putNextEntry(entry);
            String skillMd = "---\n"
                    + "name: test-skill\n"
                    + "description: Test skill description\n"
                    + "---\n\n"
                    + "This is a test instruction";
            zos.write(skillMd.getBytes());
            zos.closeEntry();

            entry = new ZipEntry("test-skill/references/readme.md");
            zos.putNextEntry(entry);
            zos.write("# Readme".getBytes());
            zos.closeEntry();

            entry = new ZipEntry("test-skill/._LICENSE.txt");
            zos.putNextEntry(entry);
            zos.write(new byte[] { 0, 5, 0, 0 }); // binary AppleDouble-like content
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    /**
     * Create a skill zip that contains a binary file (.ttf). Parser should store it as Base64 with metadata encoding=base64.
     */
    private byte[] createSkillZipWithBinaryResource() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry entry = new ZipEntry("test-skill/SKILL.md");
            zos.putNextEntry(entry);
            String skillMd = "---\n"
                    + "name: test-skill\n"
                    + "description: Test skill description\n"
                    + "---\n\n"
                    + "This is a test instruction";
            zos.write(skillMd.getBytes());
            zos.closeEntry();

            entry = new ZipEntry("test-skill/canvas-fonts/font.ttf");
            zos.putNextEntry(entry);
            zos.write(new byte[] { 0, 1, 2, 3 }); // minimal binary content
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
            String skillMd = "---\n"
                    + "name: test-skill\n"
                    + "description: Test skill description\n"
                    + "---\n\n"
                    + "## Instructions\n"
                    + "instruction content";
            zos.write(skillMd.getBytes());
            zos.closeEntry();
        }
        return baos.toByteArray();
    }
}
