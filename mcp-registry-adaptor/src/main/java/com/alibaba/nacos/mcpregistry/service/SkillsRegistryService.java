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

package com.alibaba.nacos.mcpregistry.service;

import com.alibaba.nacos.ai.service.skills.SkillOperationService;
import com.alibaba.nacos.api.ai.model.skills.Skill;
import com.alibaba.nacos.api.ai.model.skills.SkillBasicInfo;
import com.alibaba.nacos.api.ai.model.skills.SkillResource;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.common.utils.StringUtils;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service that adapts Nacos Skills to the skills CLI protocol format.
 *
 * <p>Delegates to {@link SkillOperationService} for actual skill data retrieval and converts
 * the results to the format expected by the skills CLI search API and well-known protocol.
 *
 * @author nacos
 */
@Service
public class SkillsRegistryService {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(SkillsRegistryService.class);
    
    private static final int MAX_SEARCH_LIMIT = 100;
    
    private static final String SKILL_MD_FILE = "SKILL.md";
    
    private static final String DEFAULT_NAMESPACE = "public";
    
    private final SkillOperationService skillOperationService;
    
    public SkillsRegistryService(SkillOperationService skillOperationService) {
        this.skillOperationService = skillOperationService;
    }
    
    /**
     * Search skills and return results in the skills CLI search API format.
     *
     * @param query       search keyword
     * @param limit       maximum number of results
     * @param namespaceId optional namespace filter
     * @param sourceUrl   the base URL of this nacos instance, used as skill source
     * @return search result compatible with skills CLI
     */
    public SkillSearchResult searchSkills(String query, int limit, String namespaceId, String sourceUrl) {
        if (limit <= 0) {
            limit = 10;
        }
        if (limit > MAX_SEARCH_LIMIT) {
            limit = MAX_SEARCH_LIMIT;
        }
        String ns = resolveNamespace(namespaceId);
        
        try {
            Page<SkillBasicInfo> page = skillOperationService.searchSkillsPublic(ns, query, 1, limit);
            if (page == null || page.getPageItems() == null) {
                return new SkillSearchResult(Collections.emptyList());
            }
            List<SkillSearchItem> items = page.getPageItems().stream()
                    .map(info -> toSearchItem(info, sourceUrl))
                    .collect(Collectors.toList());
            return new SkillSearchResult(items);
        } catch (NacosException e) {
            LOGGER.warn("Failed to search skills with keyword: {}", query, e);
            return new SkillSearchResult(Collections.emptyList());
        }
    }
    
    /**
     * Build the well-known index.json response containing all available skills.
     *
     * @param namespaceId optional namespace filter
     * @return index compatible with the skills well-known protocol
     */
    public WellKnownIndex getSkillsIndex(String namespaceId) {
        String ns = resolveNamespace(namespaceId);
        try {
            Page<SkillBasicInfo> page = skillOperationService.searchSkillsPublic(ns, null, 1, MAX_SEARCH_LIMIT);
            if (page == null || page.getPageItems() == null) {
                return new WellKnownIndex(Collections.emptyList());
            }
            List<WellKnownSkillEntry> entries = page.getPageItems().stream()
                    .map(this::toWellKnownEntry)
                    .collect(Collectors.toList());
            return new WellKnownIndex(entries);
        } catch (NacosException e) {
            LOGGER.warn("Failed to list skills for well-known index", e);
            return new WellKnownIndex(Collections.emptyList());
        }
    }
    
    /**
     * Get the SKILL.md content for a specific skill.
     *
     * @param skillName   the skill name
     * @param namespaceId optional namespace filter
     * @return SKILL.md content string, or null if skill not found
     */
    public String getSkillMd(String skillName, String namespaceId) {
        String ns = resolveNamespace(namespaceId);
        try {
            Skill skill = skillOperationService.querySkillPublic(ns, skillName, null, null);
            if (skill == null) {
                return null;
            }
            return buildSkillMd(skill, skillName);
        } catch (NacosException e) {
            if (e.getErrCode() == NacosException.NOT_FOUND) {
                return null;
            }
            LOGGER.warn("Failed to query skill: {}", skillName, e);
            return null;
        }
    }
    
    private String resolveNamespace(String namespaceId) {
        return StringUtils.isEmpty(namespaceId) ? DEFAULT_NAMESPACE : namespaceId;
    }
    
    private SkillSearchItem toSearchItem(SkillBasicInfo info, String sourceUrl) {
        SkillSearchItem item = new SkillSearchItem();
        item.setId(info.getName());
        item.setName(info.getName());
        item.setSource(sourceUrl);
        item.setInstalls(0);
        return item;
    }
    
    private WellKnownSkillEntry toWellKnownEntry(SkillBasicInfo info) {
        WellKnownSkillEntry entry = new WellKnownSkillEntry();
        entry.setName(info.getName());
        entry.setDescription(info.getDescription() != null ? info.getDescription() : info.getName());
        List<String> files = new ArrayList<>();
        files.add(SKILL_MD_FILE);
        entry.setFiles(files);
        return entry;
    }
    
    /**
     * Build SKILL.md content from the Skill model.
     * Uses instruction as main body, prepends YAML frontmatter with name and description.
     *
     * @param skill the skill object
     * @param skillName fallback name if skill.getName() is null
     */
    private String buildSkillMd(Skill skill, String skillName) {
        String name = skill.getName() != null ? skill.getName() : skillName;
        String description = skill.getDescription() != null ? skill.getDescription() : name;
        
        // If the skill already has instruction content, use it directly with frontmatter
        String instruction = skill.getInstruction();
        if (StringUtils.isNotBlank(instruction)) {
            StringBuilder sb = new StringBuilder();
            sb.append("---\n");
            sb.append("name: ").append(name).append("\n");
            sb.append("description: ").append(escapeYaml(description)).append("\n");
            // Append resource references if any
            Map<String, SkillResource> resources = skill.getResource();
            if (resources != null && !resources.isEmpty()) {
                sb.append("resources:\n");
                for (Map.Entry<String, SkillResource> entry : resources.entrySet()) {
                    sb.append("  - ").append(entry.getKey()).append("\n");
                }
            }
            sb.append("---\n\n");
            sb.append(instruction);
            return sb.toString();
        }
        // Fallback: generate minimal SKILL.md from name + description
        StringBuilder sb = new StringBuilder();
        sb.append("---\n");
        sb.append("name: ").append(name).append("\n");
        sb.append("description: ").append(escapeYaml(description)).append("\n");
        sb.append("---\n\n");
        sb.append("# ").append(name).append("\n\n");
        sb.append(description).append("\n");
        return sb.toString();
    }
    
    private String escapeYaml(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(":") || value.contains("#") || value.contains("'")
                || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        }
        return value;
    }
    
    // ----- Response DTOs -----
    
    /**
     * Search API response: {@code { "skills": [...] }}.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SkillSearchResult {
        
        private List<SkillSearchItem> skills;
        
        public SkillSearchResult() {
        }
        
        public SkillSearchResult(List<SkillSearchItem> skills) {
            this.skills = skills;
        }
        
        public List<SkillSearchItem> getSkills() {
            return skills;
        }
        
        public void setSkills(List<SkillSearchItem> skills) {
            this.skills = skills;
        }
    }
    
    /**
     * A single skill item in the search result.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SkillSearchItem {
        
        private String id;
        
        private String name;
        
        private String source;
        
        private int installs;
        
        public String getId() {
            return id;
        }
        
        public void setId(String id) {
            this.id = id;
        }
        
        public String getName() {
            return name;
        }
        
        public void setName(String name) {
            this.name = name;
        }
        
        public String getSource() {
            return source;
        }
        
        public void setSource(String source) {
            this.source = source;
        }
        
        public int getInstalls() {
            return installs;
        }
        
        public void setInstalls(int installs) {
            this.installs = installs;
        }
    }
    
    /**
     * Well-Known index.json response: {@code { "skills": [...] }}.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class WellKnownIndex {
        
        private List<WellKnownSkillEntry> skills;
        
        public WellKnownIndex() {
        }
        
        public WellKnownIndex(List<WellKnownSkillEntry> skills) {
            this.skills = skills;
        }
        
        public List<WellKnownSkillEntry> getSkills() {
            return skills;
        }
        
        public void setSkills(List<WellKnownSkillEntry> skills) {
            this.skills = skills;
        }
    }
    
    /**
     * A single skill entry in the well-known index.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class WellKnownSkillEntry {
        
        private String name;
        
        private String description;
        
        private List<String> files;
        
        public String getName() {
            return name;
        }
        
        public void setName(String name) {
            this.name = name;
        }
        
        public String getDescription() {
            return description;
        }
        
        public void setDescription(String description) {
            this.description = description;
        }
        
        public List<String> getFiles() {
            return files;
        }
        
        public void setFiles(List<String> files) {
            this.files = files;
        }
    }
}
