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
import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;

/**
 * Skill zip parser utility. Supports both text and binary resources:
 * text files are stored as UTF-8; binary files (e.g. .ttf, .png) are stored as Base64 with metadata encoding=base64.
 *
 * @author nacos
 */
public class SkillZipParser {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(SkillZipParser.class);
    
    private static final String SKILL_MD_FILE = "SKILL.md";
    /** UTF-8 BOM character that some editors prepend to files. Must be stripped before parsing. */
    private static final char UTF8_BOM = '\uFEFF';
    /** macOS AppleDouble/resource fork metadata file prefix (e.g. ._LICENSE.txt). Should be excluded from skill zip. */
    private static final String MACOS_METADATA_PREFIX = "._";
    private static final String DOUBLE_QUOTE = "\"";
    private static final String SINGLE_QUOTE = "'";
    private static final String DOUBLE_SINGLE_QUOTE = "''";
    private static final String BACKSLASH = "\\";
    private static final String DOUBLE_BACKSLASH = "\\\\";
    private static final String ESCAPED_DOUBLE_QUOTE = "\\\"";
    private static final String SLASH = "/";
    /**
     * Metadata key for binary resources: value "base64" means content is Base64-encoded.
     * Kept as constants on this class for backward compatibility with existing callers
     * (e.g. {@code SkillOperationServiceImpl}); the canonical definition lives on
     * {@link ResourceContentEncoder}.
     */
    public static final String METADATA_ENCODING = ResourceContentEncoder.METADATA_ENCODING;
    
    public static final String METADATA_ENCODING_BASE64 =
        ResourceContentEncoder.METADATA_ENCODING_BASE64;
    
    /**
     * Default maximum compressed (upload) size in MB for a skill ZIP. Derived from the historical
     * {@link Constants.Skills#MAX_UPLOAD_ZIP_BYTES} so the public constant remains the single
     * source of truth; runtime callers should consult {@link #resolveMaxUploadBytes()} which
     * honors the {@value #CONFIG_MAX_UPLOAD_SIZE_MB} override.
     */
    static final int DEFAULT_MAX_UPLOAD_SIZE_MB =
        (int) (Constants.Skills.MAX_UPLOAD_ZIP_BYTES / 1024L / 1024L);
    
    /**
     * Default maximum number of entries allowed in a skill ZIP. Overridable via the
     * {@value #CONFIG_MAX_ZIP_ENTRIES} property when users legitimately upload larger skills.
     */
    static final int DEFAULT_MAX_ZIP_ENTRIES = 500;
    
    /**
     * Default maximum total decompressed size (in MB) for a skill ZIP. Prevents Zip Bomb attacks
     * while still permitting legitimate uploads. Overridable via the
     * {@value #CONFIG_MAX_UNCOMPRESSED_SIZE_MB} property.
     */
    static final int DEFAULT_MAX_UNCOMPRESSED_SIZE_MB = 50;
    
    /**
     * Property key for overriding {@link #DEFAULT_MAX_UPLOAD_SIZE_MB}. The value is in megabytes
     * and applies to the raw compressed skill ZIP before parsing. Non-positive values are ignored.
     */
    static final String CONFIG_MAX_UPLOAD_SIZE_MB = "nacos.ai.skill.zip.max-upload-size-mb";
    
    /**
     * Property key for overriding {@link #DEFAULT_MAX_ZIP_ENTRIES}. Non-positive values are ignored.
     */
    static final String CONFIG_MAX_ZIP_ENTRIES = "nacos.ai.skill.zip.max-entries";
    
    /**
     * Property key for overriding {@link #DEFAULT_MAX_UNCOMPRESSED_SIZE_MB}. The value is in megabytes.
     * Non-positive values are ignored.
     */
    static final String CONFIG_MAX_UNCOMPRESSED_SIZE_MB =
        "nacos.ai.skill.zip.max-uncompressed-size-mb";
    
    private static final Pattern YAML_FRONT_MATTER = Pattern.compile(
        "^---\\s*\\n(.*?)\\n---\\s*\\n(.*)$", Pattern.DOTALL);
    
    /**
     * Parse YAML front matter map from full SKILL.md content.
     *
     * @param markdownContent full SKILL.md content
     * @return parsed front matter map, empty when no valid front matter exists
     */
    public static Map<String, String> parseYamlFrontMatterFromMarkdown(String markdownContent) {
        if (StringUtils.isBlank(markdownContent)) {
            return new HashMap<>(2);
        }
        Matcher matcher = YAML_FRONT_MATTER.matcher(markdownContent);
        if (!matcher.matches()) {
            return new HashMap<>(2);
        }
        String yamlContent = matcher.group(1);
        return parseYamlFrontMatter(yamlContent);
    }
    
    /**
     * Resolve version using SKILL.md sibling _meta.json as compensation.
     *
     * <p>Priority:
     * <ol>
     *   <li>frontmatter {@code version} in SKILL.md</li>
     *   <li>{@code _meta.json} in the same directory as SKILL.md, field {@code version}</li>
     * </ol>
     *
     * <p>Returns {@code null} when no version can be inferred.</p>
     */
    public static String resolveVersionFromZip(byte[] zipBytes) {
        if (zipBytes == null || zipBytes.length == 0) {
            return null;
        }
        try {
            List<ZipEntryData> entries = unzipToEntries(zipBytes);
            ZipEntryData skillMdEntry = findSkillMdEntry(entries);
            if (skillMdEntry == null) {
                return null;
            }
            String skillMdContent = new String(skillMdEntry.data, StandardCharsets.UTF_8);
            Map<String, String> yaml = parseYamlFrontMatterFromMarkdown(skillMdContent);
            String version = yaml.get("version");
            if (StringUtils.isNotBlank(version)) {
                return version.trim();
            }
            
            String metaJsonPath = buildSiblingMetaJsonPath(skillMdEntry.name);
            ZipEntryData metaEntry = findEntryByPath(entries, metaJsonPath);
            if (metaEntry == null) {
                return null;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> meta =
                JacksonUtils.toObj(new String(metaEntry.data, StandardCharsets.UTF_8), Map.class);
            if (meta == null) {
                return null;
            }
            Object metaVersion = meta.get("version");
            if (metaVersion == null) {
                return null;
            }
            String resolved = String.valueOf(metaVersion).trim();
            return StringUtils.isBlank(resolved) ? null : resolved;
        } catch (Exception e) {
            LOGGER.warn("Failed to resolve version from zip (fallback to default later): {}",
                e.getMessage());
            return null;
        }
    }
    
    /**
     * Parse skill from zip file bytes. Zip size must not exceed the limit returned by
     * {@link #resolveMaxUploadBytes()} (configurable via {@value #CONFIG_MAX_UPLOAD_SIZE_MB}).
     * Text files are decoded as UTF-8; binary files (by extension) are stored as Base64 with metadata encoding=base64.
     *
     * @param zipBytes zip file bytes
     * @param namespaceId namespace ID
     * @return parsed skill
     * @throws NacosApiException if parsing failed or zip exceeds size limit
     */
    public static Skill parseSkillFromZip(byte[] zipBytes, String namespaceId)
        throws NacosApiException {
        if (zipBytes == null || zipBytes.length == 0) {
            throw new NacosApiException(NacosApiException.INVALID_PARAM,
                ErrorCode.PARAMETER_VALIDATE_ERROR,
                "Skill zip file is empty");
        }
        long maxUploadBytes = resolveMaxUploadBytes();
        if (zipBytes.length > maxUploadBytes) {
            throw new NacosApiException(NacosApiException.INVALID_PARAM,
                ErrorCode.PARAMETER_VALIDATE_ERROR,
                "Skill zip size must not exceed "
                    + (maxUploadBytes / 1024 / 1024) + "MB, current: "
                    + (zipBytes.length / 1024 / 1024) + "MB");
        }
        try {
            List<ZipEntryData> entries = unzipToEntries(zipBytes);
            ZipEntryData skillMdEntry = findSkillMdEntry(entries);
            String skillMdContent =
                skillMdEntry == null ? null : stripBom(new String(skillMdEntry.data,
                    StandardCharsets.UTF_8));
            
            if (StringUtils.isBlank(skillMdContent)) {
                throw new NacosApiException(NacosApiException.INVALID_PARAM,
                    ErrorCode.PARAMETER_VALIDATE_ERROR,
                    "SKILL.md file not found in zip");
            }
            
            Skill skill = parseSkillMarkdown(skillMdContent, namespaceId);
            Map<String, SkillResource> resources =
                parseResources(entries, skill.getName(), skillMdEntry.name);
            skill.setResource(resources);
            
            return skill;
        } catch (NacosApiException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("Failed to parse skill zip file", e);
            throw new NacosApiException(NacosApiException.INVALID_PARAM,
                ErrorCode.PARSING_DATA_FAILED,
                "Failed to parse zip file: " + e.getMessage());
        }
    }
    
    /**
     * Parse multiple skills from a single zip archive. Supports zip files containing multiple skill subdirectories,
     * each with its own SKILL.md. If only one SKILL.md is found, returns a list with a single element.
     *
     * <p>Expected zip structure for multi-skill:
     * <pre>
     * skills.zip
     * ├── skill-a/
     * │   ├── SKILL.md
     * │   └── resource.txt
     * ├── skill-b/
     * │   ├── SKILL.md
     * │   └── template/prompt.md
     * </pre>
     *
     * @param zipBytes zip file bytes
     * @param namespaceId namespace ID
     * @return list of parsed skills (at least one element)
     * @throws NacosApiException if parsing failed, zip exceeds size limit, or no SKILL.md found
     */
    public static MultiSkillParseResult parseMultipleSkillsFromZip(byte[] zipBytes,
        String namespaceId) throws NacosApiException {
        if (zipBytes == null || zipBytes.length == 0) {
            throw new NacosApiException(NacosApiException.INVALID_PARAM,
                ErrorCode.PARAMETER_VALIDATE_ERROR,
                "Skill zip file is empty");
        }
        long maxUploadBytes = resolveMaxUploadBytes();
        if (zipBytes.length > maxUploadBytes) {
            throw new NacosApiException(NacosApiException.INVALID_PARAM,
                ErrorCode.PARAMETER_VALIDATE_ERROR,
                "Skill zip size must not exceed "
                    + (maxUploadBytes / 1024 / 1024) + "MB, current: "
                    + (zipBytes.length / 1024 / 1024) + "MB");
        }
        try {
            List<ZipEntryData> entries = unzipToEntries(zipBytes);
            
            // Find all SKILL.md entries and group by their parent directory
            List<ZipEntryData> skillMdEntries = new ArrayList<>();
            for (ZipEntryData entry : entries) {
                String name = entry.name;
                if (isMacOsMetadataFile(name)) {
                    continue;
                }
                boolean isSkillMdFile = SKILL_MD_FILE.equals(name);
                boolean isSkillMdInSubdir = name.endsWith(SLASH + SKILL_MD_FILE);
                if (isSkillMdFile || isSkillMdInSubdir) {
                    skillMdEntries.add(entry);
                }
            }
            
            if (skillMdEntries.isEmpty()) {
                throw new NacosApiException(NacosApiException.INVALID_PARAM,
                    ErrorCode.PARAMETER_VALIDATE_ERROR,
                    "SKILL.md file not found in zip");
            }
            
            // If root SKILL.md exists, the archive is a single skill package. Nested SKILL.md files
            // are regular resources referenced by the root descriptor.
            if (containsRootSkillMdEntry(skillMdEntries)) {
                MultiSkillParseResult result = new MultiSkillParseResult();
                result.addSkill(parseSkillFromZip(zipBytes, namespaceId));
                return result;
            }
            
            // Collect directories that have SKILL.md and determine their nesting depth
            Set<String> skillPrefixes = new HashSet<>();
            for (ZipEntryData skillMdEntry : skillMdEntries) {
                skillPrefixes.add(getSkillPrefix(skillMdEntry.name));
            }
            
            // Determine the depth of skill directories (number of '/' segments in prefix)
            int skillDepth = 0;
            for (String prefix : skillPrefixes) {
                if (!prefix.isEmpty()) {
                    skillDepth = prefix.split("/").length;
                    break;
                }
            }
            
            // Detect directories at the same depth that have files but no SKILL.md
            Set<String> nonSkillDirs = new HashSet<>();
            for (ZipEntryData entry : entries) {
                String name = entry.name;
                String peerDir = extractPrefixAtDepth(name, skillDepth);
                if (peerDir == null) {
                    continue;
                }
                if (skillPrefixes.contains(peerDir) || nonSkillDirs.contains(peerDir)) {
                    continue;
                }
                if (isIgnorableDirectory(peerDir)) {
                    continue;
                }
                nonSkillDirs.add(peerDir);
            }
            
            // Multiple SKILL.md files: parse each skill with its scoped entries
            MultiSkillParseResult parseResult = new MultiSkillParseResult();
            
            // Record warnings for directories without SKILL.md
            for (String dir : nonSkillDirs) {
                parseResult.addFailure(extractFolderName(dir),
                    "SKILL.md not found in this folder, skipped");
            }
            for (ZipEntryData skillMdEntry : skillMdEntries) {
                String skillMdPath = skillMdEntry.name;
                String prefix = getSkillPrefix(skillMdPath);
                
                try {
                    String skillMdContent =
                        stripBom(new String(skillMdEntry.data, StandardCharsets.UTF_8));
                    if (StringUtils.isBlank(skillMdContent)) {
                        parseResult.addFailure(extractFolderName(prefix),
                            "SKILL.md content is empty");
                        continue;
                    }
                    
                    Skill skill = parseSkillMarkdown(skillMdContent, namespaceId);
                    
                    // Filter entries belonging to this skill's directory
                    List<ZipEntryData> scopedEntries = filterEntriesByPrefix(entries, prefix);
                    Map<String, SkillResource> resources =
                        parseResources(scopedEntries, skill.getName(), SKILL_MD_FILE);
                    skill.setResource(resources);
                    parseResult.addSkill(skill);
                } catch (Exception e) {
                    LOGGER.warn("Skipping invalid skill folder [{}]: {}", prefix, e.getMessage());
                    parseResult.addFailure(extractFolderName(prefix), e.getMessage());
                }
            }
            
            if (parseResult.getSkills().isEmpty()) {
                throw new NacosApiException(NacosApiException.INVALID_PARAM,
                    ErrorCode.PARAMETER_VALIDATE_ERROR,
                    "No valid skills found in zip");
            }
            return parseResult;
        } catch (NacosApiException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("Failed to parse multi-skill zip file", e);
            throw new NacosApiException(NacosApiException.INVALID_PARAM,
                ErrorCode.PARSING_DATA_FAILED,
                "Failed to parse zip file: " + e.getMessage());
        }
    }
    
    /**
     * Get the directory prefix for a SKILL.md path. For "skill-a/SKILL.md" returns "skill-a/".
     * For root-level "SKILL.md" returns empty string.
     */
    private static String getSkillPrefix(String skillMdPath) {
        int lastSlash = skillMdPath.lastIndexOf('/');
        if (lastSlash < 0) {
            return "";
        }
        return skillMdPath.substring(0, lastSlash + 1);
    }
    
    /**
     * Filter entries by directory prefix and strip the prefix from entry names.
     */
    private static List<ZipEntryData> filterEntriesByPrefix(List<ZipEntryData> entries,
        String prefix) {
        if (prefix.isEmpty()) {
            return entries;
        }
        List<ZipEntryData> result = new ArrayList<>();
        for (ZipEntryData entry : entries) {
            if (entry.name.startsWith(prefix)) {
                // Strip prefix so parseResources sees paths relative to the skill directory
                String relativeName = entry.name.substring(prefix.length());
                if (!relativeName.isEmpty()) {
                    result.add(new ZipEntryData(relativeName, entry.data));
                }
            }
        }
        return result;
    }
    
    /**
     * Unzip to list of (name, raw bytes). Does not decode as text so binary files are preserved.
     * Uses Apache Commons Compress to support zip files with STORED entries that have data descriptor
     * (e.g. created on macOS or by some tools), which JDK ZipInputStream rejects.
     *
     * <p>Security hardening:
     * <ul>
     *   <li>Rejects entries with path traversal sequences (..) or absolute paths</li>
     *   <li>Enforces maximum total decompressed size (configurable via
     *       {@value #CONFIG_MAX_UNCOMPRESSED_SIZE_MB}, default
     *       {@link #DEFAULT_MAX_UNCOMPRESSED_SIZE_MB} MB)</li>
     *   <li>Enforces maximum number of entries (configurable via
     *       {@value #CONFIG_MAX_ZIP_ENTRIES}, default {@link #DEFAULT_MAX_ZIP_ENTRIES})</li>
     * </ul>
     *
     * <p>Security-limit violations are reported as {@link NacosRuntimeException} (not {@link IOException})
     * because they represent invalid user input rather than an underlying I/O failure. The caller
     * {@link #parseSkillFromZip(byte[], String)} translates them into a {@link NacosApiException}
     * for the HTTP layer.
     */
    private static List<ZipEntryData> unzipToEntries(byte[] zipBytes) throws IOException {
        final int maxEntries = resolveMaxZipEntries();
        final long maxUncompressedBytes = resolveMaxUncompressedBytes();
        List<ZipEntryData> result = new ArrayList<>();
        long totalSize = 0;
        try (ZipArchiveInputStream zis =
            new ZipArchiveInputStream(new ByteArrayInputStream(zipBytes),
                StandardCharsets.UTF_8.name(), true, true)) {
            ZipArchiveEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                // Security: reject path traversal and absolute paths
                SkillUtils.validatePathSafety(name);
                boolean isMacOsxEntry =
                    name != null && (name.contains("__MACOSX") || name.contains("/__MACOSX/"));
                if (isMacOsxEntry) {
                    continue;
                }
                if (result.size() >= maxEntries) {
                    throw new NacosRuntimeException(ErrorCode.PARAMETER_VALIDATE_ERROR.getCode(),
                        "ZIP file contains too many entries (max " + maxEntries + ")");
                }
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                int n;
                while ((n = zis.read(buffer)) != -1) {
                    totalSize += n;
                    if (totalSize > maxUncompressedBytes) {
                        throw new NacosRuntimeException(
                            ErrorCode.PARAMETER_VALIDATE_ERROR.getCode(),
                            "ZIP decompressed size exceeds limit ("
                                + (maxUncompressedBytes / 1024 / 1024) + "MB)");
                    }
                    out.write(buffer, 0, n);
                }
                result.add(new ZipEntryData(name, out.toByteArray()));
            }
        }
        return result;
    }
    
    /**
     * Resolve the maximum compressed (upload) size in bytes, honoring the
     * {@value #CONFIG_MAX_UPLOAD_SIZE_MB} override (interpreted in megabytes) when present and
     * positive. Returns {@link #DEFAULT_MAX_UPLOAD_SIZE_MB} MB otherwise. Keep this in sync with
     * the Spring multipart cap ({@code spring.servlet.multipart.max-file-size}); the multipart
     * filter rejects oversize uploads first, but operators raising the multipart cap also need
     * to raise this property for the change to take effect on the skill upload pipeline.
     */
    static long resolveMaxUploadBytes() {
        int mb = resolvePositiveIntProperty(CONFIG_MAX_UPLOAD_SIZE_MB, DEFAULT_MAX_UPLOAD_SIZE_MB);
        return (long) mb * 1024L * 1024L;
    }
    
    /**
     * Resolve the maximum number of ZIP entries allowed, honoring the
     * {@value #CONFIG_MAX_ZIP_ENTRIES} override when present and positive.
     * Returns {@link #DEFAULT_MAX_ZIP_ENTRIES} when no override is configured or when the
     * Nacos environment has not been initialized (e.g. in unit tests that bypass Spring boot-up).
     */
    static int resolveMaxZipEntries() {
        return resolvePositiveIntProperty(CONFIG_MAX_ZIP_ENTRIES, DEFAULT_MAX_ZIP_ENTRIES);
    }
    
    /**
     * Resolve the maximum total decompressed size in bytes, honoring the
     * {@value #CONFIG_MAX_UNCOMPRESSED_SIZE_MB} override (interpreted in megabytes) when present
     * and positive. Returns {@link #DEFAULT_MAX_UNCOMPRESSED_SIZE_MB} MB otherwise.
     */
    static long resolveMaxUncompressedBytes() {
        int mb = resolvePositiveIntProperty(
            CONFIG_MAX_UNCOMPRESSED_SIZE_MB, DEFAULT_MAX_UNCOMPRESSED_SIZE_MB);
        return (long) mb * 1024L * 1024L;
    }
    
    /**
     * Read an int-valued property from {@link EnvUtil}, returning {@code defaultValue} whenever
     * the override is missing, non-positive, or the environment has not yet been initialized.
     * Non-positive overrides are deliberately rejected so misconfiguration cannot silently
     * disable the underlying security guards.
     */
    private static int resolvePositiveIntProperty(String key, int defaultValue) {
        if (EnvUtil.getEnvironment() == null) {
            return defaultValue;
        }
        Integer configured = EnvUtil.getProperty(key, Integer.class);
        return configured != null && configured > 0 ? configured : defaultValue;
    }
    
    private static ZipEntryData findSkillMdEntry(List<ZipEntryData> entries) {
        if (entries == null || entries.isEmpty()) {
            return null;
        }
        ZipEntryData firstNestedSkillMdEntry = null;
        for (ZipEntryData entry : entries) {
            String name = entry.name;
            if (isMacOsMetadataFile(name)) {
                continue;
            }
            if (SKILL_MD_FILE.equals(name)) {
                return entry;
            }
            if (firstNestedSkillMdEntry == null && name.endsWith(SLASH + SKILL_MD_FILE)) {
                firstNestedSkillMdEntry = entry;
            }
        }
        return firstNestedSkillMdEntry;
    }
    
    private static boolean containsRootSkillMdEntry(List<ZipEntryData> entries) {
        if (entries == null || entries.isEmpty()) {
            return false;
        }
        for (ZipEntryData entry : entries) {
            if (SKILL_MD_FILE.equals(entry.name)) {
                return true;
            }
        }
        return false;
    }
    
    private static String buildSiblingMetaJsonPath(String skillMdPath) {
        if (StringUtils.isBlank(skillMdPath)) {
            return "_meta.json";
        }
        int idx = skillMdPath.lastIndexOf(SLASH);
        if (idx < 0) {
            return "_meta.json";
        }
        return skillMdPath.substring(0, idx + 1) + "_meta.json";
    }
    
    private static ZipEntryData findEntryByPath(List<ZipEntryData> entries, String targetPath) {
        if (entries == null || entries.isEmpty() || StringUtils.isBlank(targetPath)) {
            return null;
        }
        for (ZipEntryData entry : entries) {
            if (targetPath.equals(entry.name)) {
                return entry;
            }
        }
        return null;
    }
    
    /**
     * Parse resources from zip entries. Text files use UTF-8 content; binary (by extension) use Base64 content and metadata encoding=base64.
     */
    private static Map<String, SkillResource> parseResources(List<ZipEntryData> entries,
        String skillName, String descriptorPath) {
        Map<String, SkillResource> resources = new HashMap<>(16);
        
        for (ZipEntryData entry : entries) {
            String itemName = entry.name;
            if (isMacOsMetadataFile(itemName)) {
                continue;
            }
            if (itemName.equals(descriptorPath) || itemName.endsWith("/")) {
                continue;
            }
            
            String[] parts = itemName.split("/");
            String type;
            String resourceName;
            if (parts.length == 1) {
                // Root-level file (no subdirectory), e.g. "CONTRIBUTING.md"
                type = "";
                resourceName = parts[0];
            } else if (parts.length == 2 && parts[0].equals(skillName)) {
                type = "";
                resourceName = parts[1];
            } else if (parts.length >= 3 && parts[0].equals(skillName)) {
                // Preserve full path as type so multi-level folders (e.g. folder1/folder2) are kept
                StringBuilder typeSb = new StringBuilder();
                for (int i = 1; i < parts.length - 1; i++) {
                    if (typeSb.length() > 0) {
                        typeSb.append('/');
                    }
                    typeSb.append(parts[i]);
                }
                type = typeSb.toString();
                resourceName = parts[parts.length - 1];
            } else if (parts.length >= 2) {
                StringBuilder typeSb = new StringBuilder();
                for (int i = 0; i < parts.length - 1; i++) {
                    if (typeSb.length() > 0) {
                        typeSb.append('/');
                    }
                    typeSb.append(parts[i]);
                }
                type = typeSb.toString();
                resourceName = parts[parts.length - 1];
            } else {
                continue;
            }
            
            ResourceContentEncoder.EncodedContent encoded =
                ResourceContentEncoder.encode(entry.data, resourceName);
            
            SkillResource resource = new SkillResource();
            resource.setName(resourceName);
            resource.setType(type);
            resource.setContent(encoded.getContent());
            resource.setMetadata(encoded.getMetadata());
            // Use same key as getSkillDetail so resource map is consistent when skill is read back
            String key = SkillUtils.generateResourceId(type, resourceName);
            resources.put(key, resource);
        }
        
        return resources;
    }
    
    /**
     * Check whether a resource should be persisted as Base64-encoded binary content.
     * Backward-compatible facade over {@link ResourceContentEncoder#isBinary(String)}.
     *
     * @param fileName resource file name (with extension)
     * @return {@code true} when the file is not in the text whitelist
     */
    public static boolean isBinaryResource(String fileName) {
        return ResourceContentEncoder.isBinary(fileName);
    }
    
    private static final class ZipEntryData {
        
        final String name;
        final byte[] data;
        
        ZipEntryData(String name, byte[] data) {
            this.name = name;
            this.data = data;
        }
    }
    
    /**
     * Parse skill from SKILL.md markdown content.
     */
    private static Skill parseSkillMarkdown(String markdownContent, String namespaceId)
        throws NacosApiException {
        Matcher matcher = YAML_FRONT_MATTER.matcher(markdownContent);
        
        if (!matcher.matches()) {
            throw new NacosApiException(NacosApiException.INVALID_PARAM,
                ErrorCode.PARAMETER_VALIDATE_ERROR,
                "SKILL.md must contain YAML front matter (---)");
        }
        
        String yamlContent = matcher.group(1);
        
        Map<String, String> yamlMap = parseYamlFrontMatter(yamlContent);
        
        String name = yamlMap.get("name");
        String description = yamlMap.get("description");
        
        if (StringUtils.isBlank(name)) {
            throw new NacosApiException(NacosApiException.INVALID_PARAM,
                ErrorCode.PARAMETER_MISSING,
                "Skill name is required in YAML front matter");
        }
        
        if (StringUtils.isBlank(description)) {
            throw new NacosApiException(NacosApiException.INVALID_PARAM,
                ErrorCode.PARAMETER_MISSING,
                "Skill description is required in YAML front matter");
        }
        
        if (!SkillRequestUtil.hasNonFrontmatterContent(markdownContent)) {
            throw new NacosApiException(NacosApiException.INVALID_PARAM,
                ErrorCode.PARAMETER_MISSING,
                "Skill markdown body is required");
        }
        
        Skill skill = new Skill();
        skill.setNamespaceId(namespaceId);
        skill.setName(name.trim());
        skill.setDescription(description.trim());
        skill.setSkillMd(markdownContent);
        
        return skill;
    }
    
    private static Map<String, String> parseYamlFrontMatter(String yamlContent) {
        Map<String, String> result = new HashMap<>(4);
        String[] lines = yamlContent.split("\\n");
        String currentKey = null;
        StringBuilder currentValue = null;
        
        for (String line : lines) {
            if (line.trim().isEmpty()) {
                continue;
            }
            if (Character.isWhitespace(line.charAt(0)) && currentKey != null) {
                String nestedLine = line.trim();
                int nestedColonIndex = nestedLine.indexOf(':');
                // Support one-level nested keys like:
                // metadata:
                //   version: 1.0.0
                if (nestedColonIndex > 0) {
                    String nestedKey = nestedLine.substring(0, nestedColonIndex).trim();
                    String nestedValue = nestedLine.substring(nestedColonIndex + 1).trim();
                    result.put(currentKey + "." + nestedKey, parseYamlScalarValue(nestedValue));
                }
                if (currentValue.length() > 0) {
                    currentValue.append(' ');
                }
                currentValue.append(nestedLine);
                result.put(currentKey, currentValue.toString());
                continue;
            }
            
            String trimmedLine = line.trim();
            if (trimmedLine.startsWith("#")) {
                continue;
            }
            
            int colonIndex = trimmedLine.indexOf(':');
            if (colonIndex > 0) {
                String key = trimmedLine.substring(0, colonIndex).trim();
                String value = trimmedLine.substring(colonIndex + 1).trim();
                value = parseYamlScalarValue(value);
                currentKey = key;
                currentValue = new StringBuilder(value);
                result.put(key, value);
                continue;
            }
            currentKey = null;
            currentValue = null;
        }
        
        return result;
    }
    
    private static String parseYamlScalarValue(String value) {
        if (value == null) {
            return null;
        }
        String result = value;
        boolean hasDoubleQuotes = result.startsWith(DOUBLE_QUOTE) && result.endsWith(DOUBLE_QUOTE);
        boolean hasSingleQuotes = result.startsWith(SINGLE_QUOTE) && result.endsWith(SINGLE_QUOTE);
        if (hasDoubleQuotes) {
            result = result.substring(1, result.length() - 1);
            result = unescapeDoubleQuotedYamlValue(result);
        } else if (hasSingleQuotes) {
            result = result.substring(1, result.length() - 1);
            result = result.replace(DOUBLE_SINGLE_QUOTE, SINGLE_QUOTE);
        }
        return result;
    }
    
    /**
     * Minimal unescape for double-quoted YAML scalar values.
     * Only revert the escape sequences that are emitted by SKILL.md exporters:
     * - \\\\ -> \
     * - \\\" -> "
     */
    private static String unescapeDoubleQuotedYamlValue(String value) {
        if (StringUtils.isBlank(value)) {
            return value;
        }
        return value.replace(DOUBLE_BACKSLASH, BACKSLASH).replace(ESCAPED_DOUBLE_QUOTE,
            DOUBLE_QUOTE);
    }
    
    private static boolean isMacOsMetadataFile(String itemName) {
        if (StringUtils.isBlank(itemName)) {
            return false;
        }
        int lastSlash = itemName.lastIndexOf('/');
        String fileName = lastSlash >= 0 ? itemName.substring(lastSlash + 1) : itemName;
        return fileName.startsWith(MACOS_METADATA_PREFIX);
    }
    
    /**
     * Check if a top-level directory is a well-known non-skill directory that should be silently
     * ignored without producing a warning.
     */
    private static boolean isIgnorableDirectory(String dirName) {
        String name = dirName.endsWith("/") ? dirName.substring(0, dirName.length() - 1) : dirName;
        // Check the last segment for dot-prefixed or known non-skill directories
        int lastSlash = name.lastIndexOf('/');
        String leaf = lastSlash >= 0 ? name.substring(lastSlash + 1) : name;
        return leaf.startsWith(".") || "__MACOSX".equals(leaf) || "node_modules".equals(leaf);
    }
    
    /**
     * Extract the first {@code depth} directory segments from a path as a prefix ending with '/'.
     * Returns null if the path does not have enough segments.
     *
     * <p>Example: extractPrefixAtDepth("a/b/c/file.txt", 2) -> "a/b/"</p>
     */
    private static String extractPrefixAtDepth(String path, int depth) {
        if (depth <= 0 || path == null) {
            return null;
        }
        int slashCount = 0;
        for (int i = 0; i < path.length(); i++) {
            if (path.charAt(i) == '/') {
                slashCount++;
                if (slashCount == depth) {
                    return path.substring(0, i + 1);
                }
            }
        }
        return null;
    }
    
    /**
     * Extract the last directory name from a prefix path.
     * For example: "parent/random-lib/" -> "random-lib", "skill-a/" -> "skill-a".
     */
    private static String extractFolderName(String prefix) {
        if (StringUtils.isBlank(prefix)) {
            return "unknown";
        }
        String trimmed = prefix.endsWith("/") ? prefix.substring(0, prefix.length() - 1) : prefix;
        int lastSlash = trimmed.lastIndexOf('/');
        return lastSlash >= 0 ? trimmed.substring(lastSlash + 1) : trimmed;
    }
    
    /**
     * Strip UTF-8 BOM character from the beginning of a string if present.
     *
     * @param content the string to strip BOM from
     * @return the string without leading BOM
     */
    private static String stripBom(String content) {
        if (content != null && !content.isEmpty() && content.charAt(0) == UTF8_BOM) {
            return content.substring(1);
        }
        return content;
    }
    
    /**
     * Result of parsing a multi-skill zip archive. Contains both successfully parsed skills and
     * failures (folder name + error message) for folders that could not be parsed.
     */
    public static class MultiSkillParseResult {
        
        private final List<Skill> skills;
        
        private final List<ParseFailure> failures;
        
        public MultiSkillParseResult() {
            this.skills = new ArrayList<>();
            this.failures = new ArrayList<>();
        }
        
        public List<Skill> getSkills() {
            return skills;
        }
        
        public List<ParseFailure> getFailures() {
            return failures;
        }
        
        public void addSkill(Skill skill) {
            this.skills.add(skill);
        }
        
        public void addFailure(String folder, String reason) {
            this.failures.add(new ParseFailure(folder, reason));
        }
    }
    
    /**
     * Represents a skill folder that failed to parse.
     */
    public static class ParseFailure {
        
        private final String folder;
        
        private final String reason;
        
        public ParseFailure(String folder, String reason) {
            this.folder = folder;
            this.reason = reason;
        }
        
        public String getFolder() {
            return folder;
        }
        
        public String getReason() {
            return reason;
        }
    }
}
