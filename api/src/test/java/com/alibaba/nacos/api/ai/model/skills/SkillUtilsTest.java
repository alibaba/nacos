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

package com.alibaba.nacos.api.ai.model.skills;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test for SkillUtils.
 *
 * @author nacos
 */
class SkillUtilsTest {
    
    @TempDir
    Path tempDir;
    
    @Test
    void testToMarkdownWithValidSkill() {
        // Given
        Skill skill = createValidSkill();
        
        // When
        String markdown = SkillUtils.toMarkdown(skill);
        
        // Then
        assertNotNull(markdown);
        assertTrue(markdown.contains("name: test-skill"));
        assertTrue(markdown.contains("description: Test description"));
        assertTrue(markdown.contains("Test instruction"));
    }
    
    @Test
    void testToMarkdownWithNullSkill() {
        // When
        String markdown = SkillUtils.toMarkdown(null);
        
        // Then
        assertEquals("", markdown);
    }
    
    @Test
    void testToMarkdownWithSpecialCharacters() {
        // Given
        Skill skill = createValidSkill();
        skill.setSkillMd("---\n"
                + "name: test-skill\n"
                + "description: \"Description with: colon and \\\"quotes\\\"\"\n"
                + "---\n\n"
                + "Test instruction");
        
        // When
        String markdown = SkillUtils.toMarkdown(skill);
        
        // Then
        assertNotNull(markdown);
        assertTrue(markdown.contains("\"Description with: colon"));
    }
    
    @Test
    void testSyncToLocalWithOverwriteStrategy() throws IOException {
        // Given
        Skill skill = createValidSkill();
        Path baseDir = tempDir.resolve("skills");
        Files.createDirectories(baseDir);
        
        // When
        SkillUtils.syncToLocal(skill, baseDir.toString());
        
        // Then
        Path skillDir = baseDir.resolve(skill.getName());
        assertTrue(Files.exists(skillDir));
        assertTrue(Files.exists(skillDir.resolve("SKILL.md")));
    }
    
    @Test
    void testSyncToLocalWithBackupStrategy() throws IOException {
        // Given
        Skill skill = createValidSkill();
        Path baseDir = tempDir.resolve("skills");
        Files.createDirectories(baseDir);
        Path skillDir = baseDir.resolve(skill.getName());
        Files.createDirectories(skillDir);
        
        // When
        SkillUtils.syncToLocal(skill, baseDir.toString(),
                SkillUtils.ExistingDirectoryStrategy.BACKUP);
        
        // Then
        assertTrue(Files.exists(skillDir));
        assertTrue(Files.exists(skillDir.resolve("SKILL.md")));
    }
    
    @Test
    void testSyncToLocalWithFailStrategy() throws IOException {
        // Given
        Skill skill = createValidSkill();
        Path baseDir = tempDir.resolve("skills");
        Files.createDirectories(baseDir);
        Path skillDir = baseDir.resolve(skill.getName());
        Files.createDirectories(skillDir);
        
        // When & Then
        assertThrows(java.nio.file.FileAlreadyExistsException.class,
                () -> SkillUtils.syncToLocal(skill, baseDir.toString(),
                        SkillUtils.ExistingDirectoryStrategy.FAIL));
    }
    
    @Test
    void testSyncToLocalWithResources() throws IOException {
        // Given
        Skill skill = createValidSkillWithResources();
        Path baseDir = tempDir.resolve("skills");
        Files.createDirectories(baseDir);
        
        // When
        SkillUtils.syncToLocal(skill, baseDir.toString());
        
        // Then
        Path skillDir = baseDir.resolve(skill.getName());
        assertTrue(Files.exists(skillDir));
        assertTrue(Files.exists(skillDir.resolve("SKILL.md")));
        assertTrue(Files.exists(skillDir.resolve("script").resolve("test.sh")));
    }
    
    @Test
    void testSyncToLocalWithCustomDirName() throws IOException {
        // Given
        Skill skill = createValidSkill();
        Path baseDir = tempDir.resolve("skills");
        Files.createDirectories(baseDir);
        
        // When
        SkillUtils.syncToLocal(skill, baseDir.toString(), "custom-dir");
        
        // Then
        Path skillDir = baseDir.resolve("custom-dir");
        assertTrue(Files.exists(skillDir));
        assertTrue(Files.exists(skillDir.resolve("SKILL.md")));
    }
    
    @Test
    void testSyncToLocalWithNullSkill() {
        // When & Then
        assertThrows(IllegalArgumentException.class,
                () -> SkillUtils.syncToLocal(null, tempDir.toString()));
    }
    
    @Test
    void testSyncToLocalWithBlankSkillName() {
        // Given
        Skill skill = new Skill();
        skill.setName("");
        
        // When & Then
        assertThrows(IllegalArgumentException.class,
                () -> SkillUtils.syncToLocal(skill, tempDir.toString()));
    }
    
    @Test
    void testSyncToLocalWithBlankBaseDir() {
        // Given
        Skill skill = createValidSkill();
        
        // When & Then
        assertThrows(IllegalArgumentException.class,
                () -> SkillUtils.syncToLocal(skill, ""));
    }
    
    /**
     * Create a valid skill for testing.
     */
    private Skill createValidSkill() {
        Skill skill = new Skill();
        skill.setName("test-skill");
        skill.setDescription("Test description");
        skill.setSkillMd("---\n"
                + "name: test-skill\n"
                + "description: Test description\n"
                + "---\n\n"
                + "Test instruction");
        return skill;
    }
    
    /**
     * Create a skill with resources for testing.
     */
    private Skill createValidSkillWithResources() {
        Skill skill = createValidSkill();
        Map<String, SkillResource> resources = new HashMap<>();
        SkillResource resource = new SkillResource();
        resource.setName("test.sh");
        resource.setType("script");
        resource.setContent("#!/bin/bash\necho 'test'");
        resources.put("test", resource);
        skill.setResource(resources);
        return skill;
    }
}
