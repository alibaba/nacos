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
import com.alibaba.nacos.api.ai.model.skills.SkillResource;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.utils.ZipUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Skill zip parser utility.
 *
 * @author nacos
 */
public class SkillZipParser {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(SkillZipParser.class);
    
    private static final String SKILL_MD_FILE = "SKILL.md";
    
    private static final Pattern YAML_FRONT_MATTER = Pattern.compile(
            "^---\\s*\\n(.*?)\\n---\\s*\\n(.*)$", Pattern.DOTALL);
    
    /**
     * Parse skill from zip file bytes.
     *
     * @param zipBytes zip file bytes
     * @param namespaceId namespace ID
     * @return parsed skill
     * @throws NacosApiException if parsing failed
     */
    public static Skill parseSkillFromZip(byte[] zipBytes, String namespaceId) throws NacosApiException {
        try {
            ZipUtils.UnZipResult unzipResult = ZipUtils.unzip(zipBytes);
            List<ZipUtils.ZipItem> zipItems = unzipResult.getZipItemList();
            
            // Find SKILL.md file
            String skillMdContent = null;
            for (ZipUtils.ZipItem item : zipItems) {
                String itemName = item.getItemName();
                // Handle both "SKILL.md" and "{skillName}/SKILL.md" formats
                if (itemName.endsWith(SKILL_MD_FILE) && 
                    (itemName.equals(SKILL_MD_FILE) || itemName.endsWith("/" + SKILL_MD_FILE))) {
                    skillMdContent = item.getItemData();
                    break;
                }
            }
            
            if (StringUtils.isBlank(skillMdContent)) {
                throw new NacosApiException(NacosApiException.INVALID_PARAM, ErrorCode.PARAMETER_VALIDATE_ERROR,
                        "SKILL.md file not found in zip");
            }
            
            // Parse SKILL.md
            Skill skill = parseSkillMarkdown(skillMdContent, namespaceId);
            
            // Parse resources
            Map<String, SkillResource> resources = parseResources(zipItems, skill.getName());
            skill.setResource(resources);
            
            return skill;
        } catch (Exception e) {
            LOGGER.error("Failed to parse skill zip file", e);
            throw new NacosApiException(NacosApiException.INVALID_PARAM, ErrorCode.PARSING_DATA_FAILED,
                    "Failed to parse zip file: " + e.getMessage());
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
        
        // Parse YAML front matter
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
        
        // Extract instruction from markdown (remove "## Instructions" header if present)
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
    
    /**
     * Parse YAML front matter.
     */
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
                // Remove quotes if present
                if ((value.startsWith("\"") && value.endsWith("\"")) ||
                    (value.startsWith("'") && value.endsWith("'"))) {
                    value = value.substring(1, value.length() - 1);
                }
                result.put(key, value);
            }
        }
        
        return result;
    }
    
    /**
     * Extract instruction from markdown content.
     */
    private static String extractInstruction(String markdownContent) {
        // Remove "## Instructions" header if present
        String content = markdownContent.trim();
        if (content.startsWith("## Instructions") || content.startsWith("##Instructions")) {
            int headerEnd = content.indexOf('\n');
            if (headerEnd > 0) {
                content = content.substring(headerEnd).trim();
            } else {
                content = content.replaceFirst("##\\s*Instructions\\s*", "");
            }
        }
        
        // Remove leading/trailing newlines
        return content.trim();
    }
    
    /**
     * Parse resources from zip items.
     */
    private static Map<String, SkillResource> parseResources(List<ZipUtils.ZipItem> zipItems, String skillName) {
        Map<String, SkillResource> resources = new HashMap<>(16);
        
        for (ZipUtils.ZipItem item : zipItems) {
            String itemName = item.getItemName();
            
            // Skip SKILL.md and directories
            if (itemName.endsWith(SKILL_MD_FILE) || itemName.endsWith("/")) {
                continue;
            }
            
            String[] parts = itemName.split("/");
            
            // Resources should be in type folders like: {skillName}/{type}/{resourceName}
            // or just {type}/{resourceName} if skill folder is not included
            String type;
            String resourceName;
            
            if (parts.length >= 3 && parts[0].equals(skillName)) {
                // Format: skill-creator/references/workflows.md
                type = parts[1];
                resourceName = parts[parts.length - 1];
            } else if (parts.length >= 2) {
                // Format: references/workflows.md (no skill folder prefix)
                type = parts[parts.length - 2];
                resourceName = parts[parts.length - 1];
            } else {
                // Skip files in root (like LICENSE.txt)
                continue;
            }
            
            // Skip if it's the skill folder itself (e.g., skill-creator/LICENSE.txt)
            if (parts.length == 2 && parts[0].equals(skillName)) {
                continue;
            }
            
            SkillResource resource = new SkillResource();
            resource.setName(resourceName);
            resource.setType(type);
            resource.setContent(item.getItemData());
            
            // Use resource name as key (without extension for uniqueness)
            String key = resourceName;
            if (key.contains(".")) {
                key = key.substring(0, key.lastIndexOf('.'));
            }
            resources.put(key, resource);
        }
        
        return resources;
    }
}
