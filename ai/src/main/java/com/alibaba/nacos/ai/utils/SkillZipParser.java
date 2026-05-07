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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
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
    private static final String DOT = ".";
    /** Metadata key for binary resources: value "base64" means content is Base64-encoded. */
    public static final String METADATA_ENCODING = "encoding";
    public static final String METADATA_ENCODING_BASE64 = "base64";
    
    /** File extensions treated as binary; content will be stored as Base64. */
    private static final Set<String> BINARY_EXTENSIONS = new HashSet<>();

    /**
     * Maximum total decompressed size allowed (50MB). Prevents Zip Bomb attacks.
     */
    private static final long MAX_TOTAL_UNCOMPRESSED_BYTES = 50L * 1024 * 1024;

    /**
     * Maximum number of entries allowed in a ZIP file.
     */
    private static final int MAX_ZIP_ENTRIES = 500;
    
    static {
        BINARY_EXTENSIONS.add("ttf");
        BINARY_EXTENSIONS.add("otf");
        BINARY_EXTENSIONS.add("woff");
        BINARY_EXTENSIONS.add("woff2");
        BINARY_EXTENSIONS.add("eot");
        BINARY_EXTENSIONS.add("png");
        BINARY_EXTENSIONS.add("jpg");
        BINARY_EXTENSIONS.add("jpeg");
        BINARY_EXTENSIONS.add("gif");
        BINARY_EXTENSIONS.add("webp");
        BINARY_EXTENSIONS.add("ico");
        BINARY_EXTENSIONS.add("cur");
        BINARY_EXTENSIONS.add("pdf");
        BINARY_EXTENSIONS.add("bin");
    }
    
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
            Map<String, Object> meta = JacksonUtils.toObj(new String(metaEntry.data, StandardCharsets.UTF_8), Map.class);
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
            LOGGER.warn("Failed to resolve version from zip (fallback to default later): {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Parse skill from zip file bytes. Zip size must not exceed {@link Constants.Skills#MAX_UPLOAD_ZIP_BYTES}.
     * Text files are decoded as UTF-8; binary files (by extension) are stored as Base64 with metadata encoding=base64.
     *
     * @param zipBytes zip file bytes
     * @param namespaceId namespace ID
     * @return parsed skill
     * @throws NacosApiException if parsing failed or zip exceeds size limit
     */
    public static Skill parseSkillFromZip(byte[] zipBytes, String namespaceId) throws NacosApiException {
        if (zipBytes == null || zipBytes.length == 0) {
            throw new NacosApiException(NacosApiException.INVALID_PARAM, ErrorCode.PARAMETER_VALIDATE_ERROR,
                    "Skill zip file is empty");
        }
        if (zipBytes.length > Constants.Skills.MAX_UPLOAD_ZIP_BYTES) {
            throw new NacosApiException(NacosApiException.INVALID_PARAM, ErrorCode.PARAMETER_VALIDATE_ERROR,
                    "Skill zip size must not exceed " + (Constants.Skills.MAX_UPLOAD_ZIP_BYTES / 1024 / 1024) + "MB, current: "
                            + (zipBytes.length / 1024 / 1024) + "MB");
        }
        try {
            List<ZipEntryData> entries = unzipToEntries(zipBytes);
            String skillMdContent = null;
            for (ZipEntryData entry : entries) {
                String name = entry.name;
                if (isMacOsMetadataFile(name)) {
                    continue;
                }
                boolean isSkillMdFile = SKILL_MD_FILE.equals(name);
                boolean isSkillMdInSubdir = name.endsWith(SLASH + SKILL_MD_FILE);
                boolean endsWithSkillMd = name.endsWith(SKILL_MD_FILE);
                boolean isSkillMd = isSkillMdFile || isSkillMdInSubdir;
                if (endsWithSkillMd && isSkillMd) {
                    skillMdContent = stripBom(new String(entry.data, StandardCharsets.UTF_8));
                    break;
                }
            }
            
            if (StringUtils.isBlank(skillMdContent)) {
                throw new NacosApiException(NacosApiException.INVALID_PARAM, ErrorCode.PARAMETER_VALIDATE_ERROR,
                        "SKILL.md file not found in zip");
            }
            
            Skill skill = parseSkillMarkdown(skillMdContent, namespaceId);
            Map<String, SkillResource> resources = parseResources(entries, skill.getName());
            skill.setResource(resources);
            
            return skill;
        } catch (NacosApiException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("Failed to parse skill zip file", e);
            throw new NacosApiException(NacosApiException.INVALID_PARAM, ErrorCode.PARSING_DATA_FAILED,
                    "Failed to parse zip file: " + e.getMessage());
        }
    }
    
    /**
     * Unzip to list of (name, raw bytes). Does not decode as text so binary files are preserved.
     * Uses Apache Commons Compress to support zip files with STORED entries that have data descriptor
     * (e.g. created on macOS or by some tools), which JDK ZipInputStream rejects.
     *
     * <p>Security hardening:
     * <ul>
     *   <li>Rejects entries with path traversal sequences (..) or absolute paths</li>
     *   <li>Enforces maximum total decompressed size ({@link #MAX_TOTAL_UNCOMPRESSED_BYTES})</li>
     *   <li>Enforces maximum number of entries ({@link #MAX_ZIP_ENTRIES})</li>
     * </ul>
     *
     * <p>Security-limit violations are reported as {@link NacosRuntimeException} (not {@link IOException})
     * because they represent invalid user input rather than an underlying I/O failure. The caller
     * {@link #parseSkillFromZip(byte[], String)} translates them into a {@link NacosApiException}
     * for the HTTP layer.
     */
    private static List<ZipEntryData> unzipToEntries(byte[] zipBytes) throws IOException {
        List<ZipEntryData> result = new ArrayList<>();
        long totalSize = 0;
        try (ZipArchiveInputStream zis = new ZipArchiveInputStream(new ByteArrayInputStream(zipBytes),
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
                boolean isMacOsxEntry = name != null && (name.contains("__MACOSX") || name.contains("/__MACOSX/"));
                if (isMacOsxEntry) {
                    continue;
                }
                if (result.size() >= MAX_ZIP_ENTRIES) {
                    throw new NacosRuntimeException(ErrorCode.PARAMETER_VALIDATE_ERROR.getCode(),
                            "ZIP file contains too many entries (max " + MAX_ZIP_ENTRIES + ")");
                }
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                int n;
                while ((n = zis.read(buffer)) != -1) {
                    totalSize += n;
                    if (totalSize > MAX_TOTAL_UNCOMPRESSED_BYTES) {
                        throw new NacosRuntimeException(ErrorCode.PARAMETER_VALIDATE_ERROR.getCode(),
                                "ZIP decompressed size exceeds limit ("
                                        + (MAX_TOTAL_UNCOMPRESSED_BYTES / 1024 / 1024) + "MB)");
                    }
                    out.write(buffer, 0, n);
                }
                result.add(new ZipEntryData(name, out.toByteArray()));
            }
        }
        return result;
    }

    private static ZipEntryData findSkillMdEntry(List<ZipEntryData> entries) {
        if (entries == null || entries.isEmpty()) {
            return null;
        }
        for (ZipEntryData entry : entries) {
            String name = entry.name;
            if (isMacOsMetadataFile(name)) {
                continue;
            }
            boolean isSkillMdFile = SKILL_MD_FILE.equals(name);
            boolean isSkillMdInSubdir = name.endsWith(SLASH + SKILL_MD_FILE);
            boolean endsWithSkillMd = name.endsWith(SKILL_MD_FILE);
            if (endsWithSkillMd && (isSkillMdFile || isSkillMdInSubdir)) {
                return entry;
            }
        }
        return null;
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
    private static Map<String, SkillResource> parseResources(List<ZipEntryData> entries, String skillName) {
        Map<String, SkillResource> resources = new HashMap<>(16);
        
        for (ZipEntryData entry : entries) {
            String itemName = entry.name;
            if (isMacOsMetadataFile(itemName)) {
                continue;
            }
            if (itemName.endsWith(SKILL_MD_FILE) || itemName.endsWith("/")) {
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
            
            boolean isBinary = isBinaryResource(resourceName);
            String content;
            Map<String, Object> metadata = new HashMap<>(4);
            if (isBinary) {
                content = Base64.getEncoder().encodeToString(entry.data);
                metadata.put(METADATA_ENCODING, METADATA_ENCODING_BASE64);
            } else {
                content = new String(entry.data, StandardCharsets.UTF_8);
            }
            
            SkillResource resource = new SkillResource();
            resource.setName(resourceName);
            resource.setType(type);
            resource.setContent(content);
            resource.setMetadata(metadata.isEmpty() ? null : metadata);
            // Use same key as getSkillDetail so resource map is consistent when skill is read back
            String key = SkillUtils.generateResourceId(type, resourceName);
            resources.put(key, resource);
        }
        
        return resources;
    }

    /**
     * check is binary
     * @param fileName file name
     * @return
     */
    public static boolean isBinaryResource(String fileName) {
        if (StringUtils.isBlank(fileName) || !fileName.contains(DOT)) {
            return false;
        }
        String ext = fileName.substring(fileName.lastIndexOf(DOT.charAt(0)) + 1).trim().toLowerCase();
        return BINARY_EXTENSIONS.contains(ext);
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
    private static Skill parseSkillMarkdown(String markdownContent, String namespaceId) throws NacosApiException {
        Matcher matcher = YAML_FRONT_MATTER.matcher(markdownContent);
        
        if (!matcher.matches()) {
            throw new NacosApiException(NacosApiException.INVALID_PARAM, ErrorCode.PARAMETER_VALIDATE_ERROR,
                    "SKILL.md must contain YAML front matter (---)");
        }
        
        String yamlContent = matcher.group(1);
        
        Map<String, String> yamlMap = parseYamlFrontMatter(yamlContent);
        
        String name = yamlMap.get("name");
        String description = yamlMap.get("description");
        
        if (StringUtils.isBlank(name)) {
            throw new NacosApiException(NacosApiException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "Skill name is required in YAML front matter");
        }
        
        if (StringUtils.isBlank(description)) {
            throw new NacosApiException(NacosApiException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "Skill description is required in YAML front matter");
        }
        
        if (!SkillRequestUtil.hasNonFrontmatterContent(markdownContent)) {
            throw new NacosApiException(NacosApiException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
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
        return value.replace(DOUBLE_BACKSLASH, BACKSLASH).replace(ESCAPED_DOUBLE_QUOTE, DOUBLE_QUOTE);
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
}
