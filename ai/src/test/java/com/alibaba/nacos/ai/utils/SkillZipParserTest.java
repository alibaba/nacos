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

import com.alibaba.nacos.api.ai.model.skills.Skill;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
