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

import com.alibaba.nacos.api.ai.model.NacosAiConfigKeyCodec;
import com.alibaba.nacos.api.utils.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Utility class for Skill operations.
 *
 * @author nacos
 */
public class SkillUtils {
    
    private static final String EMPTY_STRING = "";
    
    private static final String METADATA_ENCODING = "encoding";
    
    private static final String METADATA_ENCODING_BASE64 = "base64";
    
    private static final String PATH_TRAVERSAL_SEQUENCE = "..";
    
    /**
     * ZIP local file header signature: PK\x03\x04.
     */
    private static final byte[] ZIP_MAGIC = {0x50, 0x4B, 0x03, 0x04};
    
    /**
     * Minimum valid ZIP size (local file header = 30 bytes).
     */
    private static final int ZIP_MIN_SIZE = 30;
    
    /**
     * Strategy for handling existing skill directories.
     */
    public enum ExistingDirectoryStrategy {
        /**
         * Overwrite existing directory (delete and recreate).
         */
        OVERWRITE,
        
        /**
         * Backup existing directory by renaming it with timestamp suffix.
         */
        BACKUP,
        
        /**
         * Throw exception if directory already exists.
         */
        FAIL
    }
    
    /**
     * Get full SKILL.md markdown content from skill.
     *
     * @param skill the Skill object to convert
     * @return SKILL.md markdown content
     */
    public static String toMarkdown(Skill skill) {
        if (skill == null) {
            return EMPTY_STRING;
        }
        
        return skill.getSkillMd() == null ? EMPTY_STRING : skill.getSkillMd();
    }
    
    /**
     * Convert Skill object to a ZIP byte array containing all skill files.
     *
     * <p>The ZIP structure mirrors the upload format:
     * {@code skillName/SKILL.md}, {@code skillName/type/resourceName}, etc.
     * Binary resources (marked with metadata encoding=base64) are decoded back to raw bytes.</p>
     *
     * @param skill the Skill object to convert
     * @return ZIP file as byte array
     * @throws IOException if ZIP creation fails
     * @throws IllegalArgumentException if skill is null or skill name is blank
     */
    public static byte[] toZipBytes(Skill skill) throws IOException {
        if (skill == null) {
            throw new IllegalArgumentException("Skill cannot be null");
        }
        if (StringUtils.isBlank(skill.getName())) {
            throw new IllegalArgumentException("Skill name cannot be blank");
        }
        
        String skillName = skill.getName();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // 1. SKILL.md
            zos.putNextEntry(new ZipEntry(skillName + "/SKILL.md"));
            zos.write(toMarkdown(skill).getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            
            // 2. Resource files
            if (skill.getResource() != null && !skill.getResource().isEmpty()) {
                for (SkillResource resource : skill.getResource().values()) {
                    if (resource == null || StringUtils.isBlank(resource.getName())) {
                        continue;
                    }
                    String entryPath = buildZipEntryPath(skillName, resource);
                    zos.putNextEntry(new ZipEntry(entryPath));
                    byte[] bytes = resolveResourceBytes(resource);
                    zos.write(bytes);
                    zos.closeEntry();
                }
            }
        }
        return baos.toByteArray();
    }
    
    /**
     * Build ZIP entry path for a skill resource.
     *
     * @param skillName skill name (root directory)
     * @param resource  skill resource
     * @return ZIP entry path, e.g. "skillName/type/resourceName" or "skillName/resourceName"
     */
    private static String buildZipEntryPath(String skillName, SkillResource resource) {
        String type = resource.getType();
        String entryPath;
        if (!StringUtils.isBlank(type)) {
            entryPath = skillName + "/" + type + "/" + resource.getName();
        } else {
            entryPath = skillName + "/" + resource.getName();
        }
        validatePathSafety(entryPath);
        return entryPath;
    }
    
    /**
     * Validate that a path does not contain path traversal sequences or absolute path indicators.
     *
     * @param path the path to validate
     * @throws SecurityException if path contains unsafe sequences
     */
    public static void validatePathSafety(String path) {
        if (path == null) {
            return;
        }
        if (path.contains(PATH_TRAVERSAL_SEQUENCE)) {
            throw new SecurityException("Path traversal detected: " + path);
        }
        if (path.startsWith("/") || path.startsWith("\\")) {
            throw new SecurityException("Absolute path not allowed: " + path);
        }
    }
    
    /**
     * Validate that a resolved path stays within the expected base directory.
     *
     * @param baseDir the base directory that must contain the target
     * @param target  the resolved target path
     * @throws SecurityException if target escapes baseDir
     */
    public static void validatePathContainment(Path baseDir, Path target) {
        if (!target.normalize().startsWith(baseDir.normalize())) {
            throw new SecurityException(
                "Path escapes target directory: " + target + " is outside " + baseDir);
        }
    }
    
    /**
     * Validate that byte array is a valid ZIP file by checking the magic number header.
     *
     * @param data the byte array to validate
     * @throws IllegalArgumentException if data is null, too short, or does not have ZIP magic header
     */
    public static void validateZipBytes(byte[] data) {
        if (data == null || data.length < ZIP_MIN_SIZE) {
            throw new IllegalArgumentException(
                "Invalid ZIP data: too short (" + (data == null ? 0 : data.length) + " bytes)");
        }
        for (int i = 0; i < ZIP_MAGIC.length; i++) {
            if (data[i] != ZIP_MAGIC[i]) {
                throw new IllegalArgumentException(
                    "Invalid ZIP data: missing ZIP magic header (PK\\x03\\x04)");
            }
        }
    }
    
    /**
     * Validate all ZIP entry paths for path traversal and absolute paths.
     *
     * <p>Scans entry names only without decompressing content, so it is lightweight
     * and suitable for validating downloaded ZIP bytes on the client side.</p>
     *
     * @param data the ZIP byte array to validate
     * @throws SecurityException if any entry contains path traversal or absolute path
     * @throws IOException if ZIP cannot be read
     */
    public static void validateZipEntryPaths(byte[] data) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(data))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                validatePathSafety(entry.getName());
            }
        }
    }
    
    /**
     * Resolve resource content to raw bytes.
     * Base64-encoded binary resources (marked with metadata encoding=base64) are decoded;
     * text resources are returned as UTF-8 bytes.
     *
     * @param resource the skill resource
     * @return raw bytes of the resource content
     */
    private static byte[] resolveResourceBytes(SkillResource resource) {
        String content = resource.getContent();
        if (content == null) {
            return new byte[0];
        }
        if (isBase64Encoded(resource)) {
            return Base64.getDecoder().decode(content);
        }
        return content.getBytes(StandardCharsets.UTF_8);
    }
    
    /**
     * Check if a resource is Base64-encoded binary content.
     *
     * @param resource the skill resource
     * @return true if metadata contains encoding=base64
     */
    private static boolean isBase64Encoded(SkillResource resource) {
        Map<String, Object> metadata = resource.getMetadata();
        return metadata != null && METADATA_ENCODING_BASE64.equals(metadata.get(METADATA_ENCODING));
    }
    
    /**
     * Sync Skill object to local directory.
     * Creates the skill directory structure, SKILL.md file, and resource files.
     * Uses OVERWRITE strategy by default.
     *
     * @param skill the Skill object to sync
     * @param baseDir the base directory path where the skill directory will be created
     * @throws IOException if file operations fail
     * @throws IllegalArgumentException if skill is null or skill name is blank
     */
    public static void syncToLocal(Skill skill, String baseDir) throws IOException {
        syncToLocal(skill, baseDir, ExistingDirectoryStrategy.OVERWRITE);
    }
    
    /**
     * Sync Skill object to local directory with strategy.
     * Creates the skill directory structure, SKILL.md file, and resource files.
     * Uses atomic operation: creates temporary directory first, writes all files,
     * then renames to final directory to ensure integrity.
     *
     * @param skill the Skill object to sync
     * @param baseDir the base directory path where the skill directory will be created
     * @param strategy the strategy for handling existing directories
     * @throws IOException if file operations fail
     * @throws IllegalArgumentException if skill is null or skill name is blank
     * @throws FileAlreadyExistsException if directory exists and strategy is FAIL
     */
    public static void syncToLocal(Skill skill, String baseDir, ExistingDirectoryStrategy strategy)
        throws IOException {
        if (skill == null) {
            throw new IllegalArgumentException("Skill cannot be null");
        }
        
        if (StringUtils.isBlank(skill.getName())) {
            throw new IllegalArgumentException("Skill name cannot be blank");
        }
        
        if (StringUtils.isBlank(baseDir)) {
            throw new IllegalArgumentException("Base directory cannot be blank");
        }
        
        if (strategy == null) {
            strategy = ExistingDirectoryStrategy.OVERWRITE;
        }
        
        // Create skill directory path: {baseDir}/{skillName}
        Path basePath = Paths.get(baseDir);
        Path skillDir = basePath.resolve(skill.getName());
        
        // Delegate to core implementation
        syncToLocalCore(skill, skillDir, basePath, strategy);
    }
    
    /**
     * Sync Skill object to local directory with custom skill directory name.
     * Creates the skill directory structure, SKILL.md file, and resource files.
     * Uses OVERWRITE strategy by default.
     *
     * @param skill the Skill object to sync
     * @param baseDir the base directory path where the skill directory will be created
     * @param skillDirName the custom directory name for the skill (if null, uses skill name)
     * @throws IOException if file operations fail
     * @throws IllegalArgumentException if skill is null or baseDir is blank
     */
    public static void syncToLocal(Skill skill, String baseDir, String skillDirName)
        throws IOException {
        syncToLocal(skill, baseDir, skillDirName, ExistingDirectoryStrategy.OVERWRITE);
    }
    
    /**
     * Sync Skill object to local directory with custom skill directory name and strategy.
     * Creates the skill directory structure, SKILL.md file, and resource files.
     * Uses atomic operation: creates temporary directory first, writes all files,
     * then renames to final directory to ensure integrity.
     *
     * @param skill the Skill object to sync
     * @param baseDir the base directory path where the skill directory will be created
     * @param skillDirName the custom directory name for the skill (if null, uses skill name)
     * @param strategy the strategy for handling existing directories
     * @throws IOException if file operations fail
     * @throws IllegalArgumentException if skill is null or baseDir is blank
     * @throws FileAlreadyExistsException if directory exists and strategy is FAIL
     */
    public static void syncToLocal(Skill skill, String baseDir, String skillDirName,
        ExistingDirectoryStrategy strategy) throws IOException {
        if (skill == null) {
            throw new IllegalArgumentException("Skill cannot be null");
        }
        
        if (StringUtils.isBlank(baseDir)) {
            throw new IllegalArgumentException("Base directory cannot be blank");
        }
        
        if (strategy == null) {
            strategy = ExistingDirectoryStrategy.OVERWRITE;
        }
        
        // Use custom directory name or fall back to skill name
        String dirName = !StringUtils.isBlank(skillDirName) ? skillDirName : skill.getName();
        if (StringUtils.isBlank(dirName)) {
            throw new IllegalArgumentException("Skill directory name cannot be blank");
        }
        
        // Create skill directory path: {baseDir}/{skillDirName}
        Path basePath = Paths.get(baseDir);
        Path skillDir = basePath.resolve(dirName);
        
        // Delegate to core implementation
        syncToLocalCore(skill, skillDir, basePath, strategy);
    }
    
    /**
     * Core implementation for syncing Skill to local directory.
     * This method contains the common logic for all syncToLocal variants.
     *
     * @param skill the Skill object to sync
     * @param skillDir the target skill directory path
     * @param basePath the base directory path
     * @param strategy the strategy for handling existing directories
     * @throws IOException if file operations fail
     * @throws FileAlreadyExistsException if directory exists and strategy is FAIL
     */
    private static void syncToLocalCore(Skill skill, Path skillDir, Path basePath,
        ExistingDirectoryStrategy strategy) throws IOException {
        // Step 1: If strategy is FAIL, check if directory exists and throw exception immediately
        if (strategy == ExistingDirectoryStrategy.FAIL) {
            if (Files.exists(skillDir) && Files.isDirectory(skillDir)) {
                throw new FileAlreadyExistsException("Skill directory already exists: " + skillDir);
            }
        }
        
        // Step 2: Create temporary directory and write all files
        String dirName = skillDir.getFileName().toString();
        Path tempSkillDir = basePath.resolve(dirName + ".tmp." + System.currentTimeMillis());
        
        try {
            // Create temporary skill directory
            Files.createDirectories(tempSkillDir);
            
            // Write SKILL.md file
            String markdownContent = toMarkdown(skill);
            Path skillMdPath = tempSkillDir.resolve("SKILL.md");
            Files.write(skillMdPath, markdownContent.getBytes(StandardCharsets.UTF_8));
            
            // Write resource files
            if (skill.getResource() != null && !skill.getResource().isEmpty()) {
                for (Map.Entry<String, SkillResource> entry : skill.getResource().entrySet()) {
                    SkillResource resource = entry.getValue();
                    if (resource == null) {
                        continue;
                    }
                    
                    String resourceName = resource.getName();
                    if (StringUtils.isBlank(resourceName)) {
                        // Use key as resource name if name is blank
                        resourceName = entry.getKey();
                    }
                    
                    String resourceType = resource.getType();
                    String resourceContent = resource.getContent();
                    
                    // Determine resource file path
                    Path resourcePath;
                    if (!StringUtils.isBlank(resourceType)) {
                        // Resources with type: {tempSkillDir}/{type}/{resourceName}
                        Path typeDir = tempSkillDir.resolve(resourceType);
                        Files.createDirectories(typeDir);
                        resourcePath = typeDir.resolve(resourceName);
                    } else {
                        // Resources without type: {tempSkillDir}/{resourceName}
                        resourcePath = tempSkillDir.resolve(resourceName);
                    }
                    // Security: ensure resolved path does not escape the skill directory
                    validatePathContainment(tempSkillDir, resourcePath);
                    
                    // Write resource content (use empty string if content is null)
                    String content = resourceContent != null ? resourceContent : "";
                    Files.write(resourcePath, content.getBytes(StandardCharsets.UTF_8));
                }
            }
            
            // Step 3: All files written successfully, now handle final directory
            boolean oldDirExists = Files.exists(skillDir) && Files.isDirectory(skillDir);
            
            if (!oldDirExists) {
                // Old directory doesn't exist, directly rename temp directory to final directory
                Files.move(tempSkillDir, skillDir, StandardCopyOption.ATOMIC_MOVE);
            } else {
                // Old directory exists, need to backup first
                // Step 3.1: Rename old directory to backup directory
                Path backupDir = createBackupDirectoryPath(skillDir);
                Files.move(skillDir, backupDir, StandardCopyOption.ATOMIC_MOVE);
                
                // Step 3.2: Rename temp directory to final directory
                Files.move(tempSkillDir, skillDir, StandardCopyOption.ATOMIC_MOVE);
                
                // Step 3.3: Handle backup directory based on strategy
                if (strategy == ExistingDirectoryStrategy.OVERWRITE) {
                    // Delete backup directory
                    deleteDirectory(backupDir);
                }
                // If strategy is BACKUP, keep the backup directory (do nothing)
            }
            
        } catch (Exception e) {
            // Clean up temporary directory on failure
            if (Files.exists(tempSkillDir)) {
                try {
                    deleteDirectory(tempSkillDir);
                } catch (IOException cleanupException) {
                    // Log but don't throw - original exception is more important
                }
            }
            throw e;
        }
    }
    
    /**
     * Create backup directory path with timestamp suffix.
     * If backup directory already exists, append counter to ensure uniqueness.
     *
     * @param skillDir the skill directory path
     * @return backup directory path
     */
    private static Path createBackupDirectoryPath(Path skillDir) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");
        String timestamp = dateFormat.format(new Date());
        Path backupDir = skillDir.getParent()
            .resolve(skillDir.getFileName().toString() + ".backup." + timestamp);
        
        // If backup directory already exists, append counter
        int counter = 1;
        Path finalBackupDir = backupDir;
        while (Files.exists(finalBackupDir)) {
            finalBackupDir = skillDir.getParent().resolve(
                skillDir.getFileName().toString() + ".backup." + timestamp + "." + counter);
            counter++;
        }
        
        return finalBackupDir;
    }
    
    /**
     * Recursively delete a directory and all its contents.
     *
     * @param directory the directory to delete
     * @throws IOException if deletion fails
     */
    private static void deleteDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        
        // Delete files before directories
        Files.walk(directory)
            .sorted((a, b) -> b.compareTo(a))
            .forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to delete: " + path, e);
                }
            });
    }
    
    /**
     * Main config dataId for skill.
     *
     * @deprecated No longer used. Replaced by {@link #SKILL_INDEX_DATA_ID} for the manifest
     *             and versioned resource files for content.
     */
    @Deprecated
    public static final String SKILL_MAIN_DATA_ID = "skill.json";
    
    /**
     * Resource config dataId prefix.
     */
    public static final String RESOURCE_DATA_ID_PREFIX = "resource_";
    
    /**
     * Resource config dataId suffix.
     */
    public static final String RESOURCE_DATA_ID_SUFFIX = ".json";
    
    /**
     * Skill group prefix.
     */
    public static final String SKILL_GROUP_PREFIX = "skill_";
    
    /**
     * Skill index config dataId for client-side config caching.
     * Server writes a manifest config with this dataId at group {@code skill_{name}}
     * containing the current online version and file list.
     */
    public static final String SKILL_INDEX_DATA_ID = "skill_index.json";
    
    private static final String DOUBLE_UNDERSCORE = "__";
    
    /**
     * Build the Nacos Config group for a skill (no version suffix).
     *
     * @param skillName name of skill
     * @return config group string, e.g. "skill_myskill"
     */
    public static String buildSkillGroup(String skillName) {
        return SKILL_GROUP_PREFIX + NacosAiConfigKeyCodec.encodeManifestGroupNameSegment(skillName);
    }
    
    /**
     * Build the Nacos Config group for a specific skill version.
     *
     * @param skillName name of skill
     * @param version   version string, e.g. "v1"
     * @return config group string, e.g. "skill_myskill__v1"
     */
    public static String buildSkillVersionGroup(String skillName, String version) {
        return SKILL_GROUP_PREFIX + NacosAiConfigKeyCodec.encodeVersionedGroupSegment(skillName)
            + DOUBLE_UNDERSCORE
            + NacosAiConfigKeyCodec.encodeVersionedGroupSegment(version);
    }
    
    /**
     * Decode a Skill Nacos Config {@code group} (as stored) into logical skill name and optional version.
     *
     * @param group physical group, e.g. {@code skill_myagent} or {@code skill_name__v1}
     * @return array of length 2: {@code [skillName, version]}; {@code version} is {@code null} for manifest group
     */
    public static String[] decodeSkillGroupToNameAndVersion(String group) {
        if (StringUtils.isBlank(group) || !group.startsWith(SKILL_GROUP_PREFIX)) {
            throw new IllegalArgumentException("Not a Skill config group: " + group);
        }
        String rest = group.substring(SKILL_GROUP_PREFIX.length());
        int idx = rest.lastIndexOf(DOUBLE_UNDERSCORE);
        if (idx < 0) {
            return new String[] {NacosAiConfigKeyCodec.decodeSegment(rest), null};
        }
        return new String[] {NacosAiConfigKeyCodec.decodeSegment(rest.substring(0, idx)),
            NacosAiConfigKeyCodec.decodeSegment(rest.substring(idx + DOUBLE_UNDERSCORE.length()))};
    }
    
    /**
     * Sanitize a resource name for use in Nacos Config group names.
     *
     * @param name the raw resource name (e.g. skill name or agentspec name)
     * @return value safe for use in Nacos config parameters
     * @deprecated use {@link NacosAiConfigKeyCodec#encodeSegment(String)} for reversible encoding
     */
    @Deprecated
    public static String sanitizeNameForGroup(String name) {
        return NacosAiConfigKeyCodec.encodeManifestGroupNameSegment(name);
    }
    
    private static final String FILE_EXTENSION_PATTERN = ".*\\.[a-zA-Z0-9]+$";
    
    /**
     * Generate resource ID from resource type and name.
     * Format: {type}_{resourcename}
     * If resourcename ends with .xx, convert the last . to __
     * Slashes in type are encoded as dots so that dataId (resource_{resourceId}.json) is valid in Nacos.
     *
     * @param type resource type (can be null or empty; may contain / for multi-level paths)
     * @param resourceName resource name
     * @return resource ID (safe for use in config dataId)
     */
    public static String generateResourceId(String type, String resourceName) {
        if (resourceName == null || resourceName.trim().isEmpty()) {
            return "";
        }
        
        // If resourcename ends with .xx, convert the last . to __
        String processedName = resourceName;
        if (resourceName.matches(FILE_EXTENSION_PATTERN)) {
            // Replace only the last dot before the extension
            int lastDotIndex = resourceName.lastIndexOf('.');
            if (lastDotIndex > 0) {
                processedName = resourceName.substring(0, lastDotIndex) + DOUBLE_UNDERSCORE
                    + resourceName.substring(lastDotIndex + 1);
            }
        }
        
        if (type != null && !type.trim().isEmpty()) {
            // Encode / as . so dataId has no slash (Nacos config key compatibility)
            String safeType = type.trim().replace("/", ".");
            return safeType + "_" + processedName;
        } else {
            return processedName;
        }
    }
}
