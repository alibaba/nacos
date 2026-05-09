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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    
    // ========== toZipBytes Tests ==========
    
    @Test
    @DisplayName("test toZipBytes with null skill throws exception")
    void testToZipBytesWithNullSkillThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> SkillUtils.toZipBytes(null));
    }
    
    @Test
    @DisplayName("test toZipBytes with blank name throws exception")
    void testToZipBytesWithBlankNameThrowsException() {
        Skill skill = new Skill();
        skill.setName("");
        assertThrows(IllegalArgumentException.class, () -> SkillUtils.toZipBytes(skill));
    }
    
    @Test
    @DisplayName("test toZipBytes with valid skill")
    void testToZipBytesWithValidSkill() throws IOException {
        Skill skill = createValidSkillWithResources();
        byte[] zipBytes = SkillUtils.toZipBytes(skill);
        assertNotNull(zipBytes);
        assertTrue(zipBytes.length > 0);
        // Verify it's a valid ZIP by checking magic header
        SkillUtils.validateZipBytes(zipBytes);
    }
    
    @Test
    @DisplayName("test toZipBytes contains SKILL.md entry")
    void testToZipBytesContainsSkillMdEntry() throws IOException {
        Skill skill = createValidSkill();
        byte[] zipBytes = SkillUtils.toZipBytes(skill);
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry = zis.getNextEntry();
            assertNotNull(entry);
            assertTrue(entry.getName().endsWith("SKILL.md"));
        }
    }
    
    @Test
    @DisplayName("test toZipBytes with base64 encoded resource")
    void testToZipBytesWithBase64EncodedResource() throws IOException {
        Skill skill = createValidSkill();
        Map<String, SkillResource> resources = new HashMap<>();
        SkillResource resource = new SkillResource();
        resource.setName("binary.bin");
        resource.setType("data");
        String base64Content = Base64.getEncoder().encodeToString(new byte[] {0x01, 0x02, 0x03});
        resource.setContent(base64Content);
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("encoding", "base64");
        resource.setMetadata(metadata);
        resources.put("binary", resource);
        skill.setResource(resources);
        
        byte[] zipBytes = SkillUtils.toZipBytes(skill);
        assertNotNull(zipBytes);
        SkillUtils.validateZipBytes(zipBytes);
    }
    
    // ========== validatePathSafety Tests ==========
    
    @Test
    @DisplayName("test validatePathSafety with null path")
    void testValidatePathSafetyWithNullPath() {
        SkillUtils.validatePathSafety(null); // Should not throw
    }
    
    @Test
    @DisplayName("test validatePathSafety with safe path")
    void testValidatePathSafetyWithSafePath() {
        SkillUtils.validatePathSafety("skill/config.yaml"); // Should not throw
    }
    
    @Test
    @DisplayName("test validatePathSafety with path traversal throws exception")
    void testValidatePathSafetyWithPathTraversalThrowsException() {
        assertThrows(SecurityException.class, () -> SkillUtils.validatePathSafety("../etc/passwd"));
    }
    
    @Test
    @DisplayName("test validatePathSafety with absolute path throws exception")
    void testValidatePathSafetyWithAbsolutePathThrowsException() {
        assertThrows(SecurityException.class, () -> SkillUtils.validatePathSafety("/etc/passwd"));
    }
    
    @Test
    @DisplayName("test validatePathSafety with windows absolute path throws exception")
    void testValidatePathSafetyWithWindowsAbsolutePathThrowsException() {
        assertThrows(SecurityException.class,
            () -> SkillUtils.validatePathSafety("\\windows\\system"));
    }
    
    // ========== validateZipBytes Tests ==========
    
    @Test
    @DisplayName("test validateZipBytes with null data throws exception")
    void testValidateZipBytesWithNullDataThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> SkillUtils.validateZipBytes(null));
    }
    
    @Test
    @DisplayName("test validateZipBytes with too short data throws exception")
    void testValidateZipBytesWithTooShortDataThrowsException() {
        assertThrows(IllegalArgumentException.class,
            () -> SkillUtils.validateZipBytes(new byte[10]));
    }
    
    @Test
    @DisplayName("test validateZipBytes with invalid magic header throws exception")
    void testValidateZipBytesWithInvalidMagicHeaderThrowsException() {
        byte[] invalidZip = new byte[30];
        invalidZip[0] = 0x00;
        assertThrows(IllegalArgumentException.class, () -> SkillUtils.validateZipBytes(invalidZip));
    }
    
    @Test
    @DisplayName("test validateZipBytes with valid zip bytes")
    void testValidateZipBytesWithValidZipBytes() throws IOException {
        Skill skill = createValidSkill();
        byte[] zipBytes = SkillUtils.toZipBytes(skill);
        SkillUtils.validateZipBytes(zipBytes); // Should not throw
    }
    
    // ========== validateZipEntryPaths Tests ==========
    
    @Test
    @DisplayName("test validateZipEntryPaths with safe zip")
    void testValidateZipEntryPathsWithSafeZip() throws IOException {
        Skill skill = createValidSkill();
        byte[] zipBytes = SkillUtils.toZipBytes(skill);
        SkillUtils.validateZipEntryPaths(zipBytes); // Should not throw
    }
    
    // ========== buildSkillGroup Tests ==========
    
    @Test
    @DisplayName("test buildSkillGroup with valid name")
    void testBuildSkillGroupWithValidName() {
        String group = SkillUtils.buildSkillGroup("my-skill");
        assertTrue(group.startsWith("skill_"));
    }
    
    @Test
    @DisplayName("test buildSkillGroup with special characters")
    void testBuildSkillGroupWithSpecialCharacters() {
        String group = SkillUtils.buildSkillGroup("skill@test");
        assertTrue(group.startsWith("skill_"));
    }
    
    // ========== buildSkillVersionGroup Tests ==========
    
    @Test
    @DisplayName("test buildSkillVersionGroup with valid name and version")
    void testBuildSkillVersionGroupWithValidNameAndVersion() {
        String group = SkillUtils.buildSkillVersionGroup("my-skill", "v1");
        assertTrue(group.startsWith("skill_"));
        assertTrue(group.contains("__"));
    }
    
    // ========== decodeSkillGroupToNameAndVersion Tests ==========
    
    @Test
    @DisplayName("test decodeSkillGroupToNameAndVersion with manifest group")
    void testDecodeSkillGroupToNameAndVersionWithManifestGroup() {
        String group = SkillUtils.buildSkillGroup("my-skill");
        String[] result = SkillUtils.decodeSkillGroupToNameAndVersion(group);
        assertEquals(2, result.length);
        assertNotNull(result[0]);
        assertNull(result[1]);
    }
    
    @Test
    @DisplayName("test decodeSkillGroupToNameAndVersion with versioned group")
    void testDecodeSkillGroupToNameAndVersionWithVersionedGroup() {
        String group = SkillUtils.buildSkillVersionGroup("my-skill", "v1");
        String[] result = SkillUtils.decodeSkillGroupToNameAndVersion(group);
        assertEquals(2, result.length);
        assertNotNull(result[0]);
        assertNotNull(result[1]);
    }
    
    @Test
    @DisplayName("test decodeSkillGroupToNameAndVersion with invalid group throws exception")
    void testDecodeSkillGroupToNameAndVersionWithInvalidGroupThrowsException() {
        assertThrows(IllegalArgumentException.class,
            () -> SkillUtils.decodeSkillGroupToNameAndVersion("invalid"));
    }
    
    @Test
    @DisplayName("test decodeSkillGroupToNameAndVersion with blank group throws exception")
    void testDecodeSkillGroupToNameAndVersionWithBlankGroupThrowsException() {
        assertThrows(IllegalArgumentException.class,
            () -> SkillUtils.decodeSkillGroupToNameAndVersion(""));
    }
    
    // ========== generateResourceId Tests ==========
    
    @Test
    @DisplayName("test generateResourceId with null resourceName returns empty")
    void testGenerateResourceIdWithNullResourceNameReturnsEmpty() {
        String id = SkillUtils.generateResourceId("config", null);
        assertEquals("", id);
    }
    
    @Test
    @DisplayName("test generateResourceId with empty resourceName returns empty")
    void testGenerateResourceIdWithEmptyResourceNameReturnsEmpty() {
        String id = SkillUtils.generateResourceId("config", "");
        assertEquals("", id);
    }
    
    @Test
    @DisplayName("test generateResourceId with type and name")
    void testGenerateResourceIdWithTypeAndName() {
        String id = SkillUtils.generateResourceId("script", "test.py");
        assertTrue(id.contains("script"));
        assertTrue(id.contains("test"));
    }
    
    @Test
    @DisplayName("test generateResourceId with null type")
    void testGenerateResourceIdWithNullType() {
        String id = SkillUtils.generateResourceId(null, "test.yaml");
        assertNotNull(id);
        assertTrue(id.contains("test"));
    }
    
    @Test
    @DisplayName("test generateResourceId with file extension converts dot")
    void testGenerateResourceIdWithFileExtensionConvertsDot() {
        String id = SkillUtils.generateResourceId("config", "test.yaml");
        assertTrue(id.contains("__"));
    }
    
    @Test
    @DisplayName("test generateResourceId with slash in type converts to dot")
    void testGenerateResourceIdWithSlashInTypeConvertsToDot() {
        String id = SkillUtils.generateResourceId("path/to", "file.txt");
        assertTrue(id.contains("path.to"));
    }
    
    // ========== ExistingDirectoryStrategy Tests ==========
    
    @Test
    @DisplayName("test ExistingDirectoryStrategy OVERWRITE enum value")
    void testExistingDirectoryStrategyOverwriteEnumValue() {
        assertEquals("OVERWRITE", SkillUtils.ExistingDirectoryStrategy.OVERWRITE.name());
    }
    
    @Test
    @DisplayName("test ExistingDirectoryStrategy BACKUP enum value")
    void testExistingDirectoryStrategyBackupEnumValue() {
        assertEquals("BACKUP", SkillUtils.ExistingDirectoryStrategy.BACKUP.name());
    }
    
    @Test
    @DisplayName("test ExistingDirectoryStrategy FAIL enum value")
    void testExistingDirectoryStrategyFailEnumValue() {
        assertEquals("FAIL", SkillUtils.ExistingDirectoryStrategy.FAIL.name());
    }
    
    // ========== toMarkdown edge cases ==========
    
    @Test
    @DisplayName("test toMarkdown with skill having null skillMd")
    void testToMarkdownWithSkillHavingNullSkillMd() {
        Skill skill = new Skill();
        skill.setName("test");
        skill.setSkillMd(null);
        String markdown = SkillUtils.toMarkdown(skill);
        assertEquals("", markdown);
    }
    
    // ========== syncToLocal edge cases ==========
    
    @Test
    @DisplayName("test syncToLocal with null strategy defaults to OVERWRITE")
    void testSyncToLocalWithNullStrategyDefaultsToOverwrite() throws IOException {
        Skill skill = createValidSkill();
        Path baseDir = tempDir.resolve("skills2");
        Files.createDirectories(baseDir);
        SkillUtils.syncToLocal(skill, baseDir.toString(),
            (SkillUtils.ExistingDirectoryStrategy) null);
        Path skillDir = baseDir.resolve(skill.getName());
        assertTrue(Files.exists(skillDir));
    }
    
    @Test
    @DisplayName("test syncToLocal with custom dir name and null strategy")
    void testSyncToLocalWithCustomDirNameAndNullStrategy() throws IOException {
        Skill skill = createValidSkill();
        Path baseDir = tempDir.resolve("skills3");
        Files.createDirectories(baseDir);
        SkillUtils.syncToLocal(skill, baseDir.toString(), "custom-skill", null);
        Path skillDir = baseDir.resolve("custom-skill");
        assertTrue(Files.exists(skillDir));
    }
    
    @Test
    @DisplayName("test syncToLocal with custom dir name and fail strategy")
    void testSyncToLocalWithCustomDirNameAndFailStrategy() throws IOException {
        Skill skill = createValidSkill();
        Path baseDir = tempDir.resolve("skills4");
        Files.createDirectories(baseDir);
        Path skillDir = baseDir.resolve("custom-fail");
        Files.createDirectories(skillDir);
        assertThrows(java.nio.file.FileAlreadyExistsException.class,
            () -> SkillUtils.syncToLocal(skill, baseDir.toString(), "custom-fail",
                SkillUtils.ExistingDirectoryStrategy.FAIL));
    }
    
    // ========== Constant Tests ==========
    
    @Test
    @DisplayName("test SKILL_GROUP_PREFIX constant")
    void testSkillGroupPrefixConstant() {
        assertEquals("skill_", SkillUtils.SKILL_GROUP_PREFIX);
    }
    
    @Test
    @DisplayName("test RESOURCE_DATA_ID_PREFIX constant")
    void testResourceDataIdPrefixConstant() {
        assertEquals("resource_", SkillUtils.RESOURCE_DATA_ID_PREFIX);
    }
    
    @Test
    @DisplayName("test SKILL_INDEX_DATA_ID constant")
    void testSkillIndexDataIdConstant() {
        assertEquals("skill_index.json", SkillUtils.SKILL_INDEX_DATA_ID);
    }
}
