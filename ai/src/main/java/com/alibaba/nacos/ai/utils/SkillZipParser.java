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
import com.alibaba.nacos.api.model.v2.ErrorCode;
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
    /** macOS AppleDouble/resource fork metadata file prefix (e.g. ._LICENSE.txt). Should be excluded from skill zip. */
    private static final String MACOS_METADATA_PREFIX = "._";
    private static final String INSTRUCTIONS_HEADER_WITH_SPACE = "## Instructions";
    private static final String INSTRUCTIONS_HEADER_NO_SPACE = "##Instructions";
    private static final String DOUBLE_QUOTE = "\"";
    private static final String SINGLE_QUOTE = "'";
    private static final String SLASH = "/";
    /** Metadata key for binary resources: value "base64" means content is Base64-encoded. */
    private static final String METADATA_ENCODING = "encoding";
    private static final String METADATA_ENCODING_BASE64 = "base64";
    
    /** File extensions treated as binary; content will be stored as Base64. */
    private static final Set<String> BINARY_EXTENSIONS = new HashSet<>();
    
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
                    skillMdContent = new String(entry.data, StandardCharsets.UTF_8);
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
     */
    private static List<ZipEntryData> unzipToEntries(byte[] zipBytes) throws IOException {
        List<ZipEntryData> result = new ArrayList<>();
        try (ZipArchiveInputStream zis = new ZipArchiveInputStream(new ByteArrayInputStream(zipBytes),
                StandardCharsets.UTF_8.name(), true, true)) {
            ZipArchiveEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                if (name != null && (name.contains("__MACOSX") || name.contains("/__MACOSX/"))) {
                    continue;
                }
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                int n;
                while ((n = zis.read(buffer)) != -1) {
                    out.write(buffer, 0, n);
                }
                result.add(new ZipEntryData(name, out.toByteArray()));
            }
        }
        return result;
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
            if (parts.length == 2 && parts[0].equals(skillName)) {
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
    
    private static boolean isBinaryResource(String fileName) {
        if (StringUtils.isBlank(fileName) || !fileName.contains(".")) {
            return false;
        }
        String ext = fileName.substring(fileName.lastIndexOf('.') + 1).trim().toLowerCase();
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
        String instructionContent = matcher.group(2);
        
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
        
        String instruction = extractInstruction(instructionContent);
        
        if (StringUtils.isBlank(instruction)) {
            throw new NacosApiException(NacosApiException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "Skill instruction is required");
        }
        
        Skill skill = new Skill();
        skill.setNamespaceId(namespaceId);
        skill.setName(name.trim());
        skill.setDescription(description.trim());
        skill.setInstruction(instruction.trim());
        
        return skill;
    }
    
    private static Map<String, String> parseYamlFrontMatter(String yamlContent) {
        Map<String, String> result = new HashMap<>(4);
        String[] lines = yamlContent.split("\\n");
        
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            
            int colonIndex = line.indexOf(':');
            if (colonIndex > 0) {
                String key = line.substring(0, colonIndex).trim();
                String value = line.substring(colonIndex + 1).trim();
                boolean hasDoubleQuotes = value.startsWith(DOUBLE_QUOTE) && value.endsWith(DOUBLE_QUOTE);
                boolean hasSingleQuotes = value.startsWith(SINGLE_QUOTE) && value.endsWith(SINGLE_QUOTE);
                if (hasDoubleQuotes || hasSingleQuotes) {
                    value = value.substring(1, value.length() - 1);
                }
                result.put(key, value);
            }
        }
        
        return result;
    }
    
    private static String extractInstruction(String markdownContent) {
        String content = markdownContent.trim();
        boolean hasHeaderWithSpace = content.startsWith(INSTRUCTIONS_HEADER_WITH_SPACE);
        boolean hasHeaderNoSpace = content.startsWith(INSTRUCTIONS_HEADER_NO_SPACE);
        if (hasHeaderWithSpace || hasHeaderNoSpace) {
            int headerEnd = content.indexOf('\n');
            if (headerEnd > 0) {
                content = content.substring(headerEnd).trim();
            } else {
                content = content.replaceFirst("##\\s*Instructions\\s*", "");
            }
        }
        return content.trim();
    }
    
    private static boolean isMacOsMetadataFile(String itemName) {
        if (StringUtils.isBlank(itemName)) {
            return false;
        }
        int lastSlash = itemName.lastIndexOf('/');
        String fileName = lastSlash >= 0 ? itemName.substring(lastSlash + 1) : itemName;
        return fileName.startsWith(MACOS_METADATA_PREFIX);
    }
}
