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

package com.alibaba.nacos.mcpregistry.controller;

import com.alibaba.nacos.mcpregistry.service.SkillsRegistryService;
import com.alibaba.nacos.mcpregistry.service.SkillsRegistryService.SkillSearchResult;
import com.alibaba.nacos.mcpregistry.service.SkillsRegistryService.WellKnownIndex;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller that implements the skills CLI protocol for Nacos.
 *
 * <p>This controller exposes two integration paths compatible with the
 * <a href="https://github.com/vercel-labs/skills">skills CLI</a>:
 * <ul>
 *   <li><b>Search API</b> ({@code GET /api/search}) - used by {@code skills find}</li>
 *   <li><b>Well-Known Protocol</b> ({@code GET /.well-known/skills/*}) - used by {@code skills add}</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>
 * # Search for skills
 * SKILLS_API_URL=http://nacos-host:9080 npx skills find &lt;query&gt;
 *
 * # Install skills
 * npx skills add http://nacos-host:9080
 * </pre>
 *
 * <p>No authentication is required for these endpoints.
 *
 * @author nacos
 */
@RestController
public class SkillsRegistryController {
    
    private final SkillsRegistryService skillsRegistryService;
    
    public SkillsRegistryController(SkillsRegistryService skillsRegistryService) {
        this.skillsRegistryService = skillsRegistryService;
    }
    
    /**
     * Search API endpoint for {@code skills find} command.
     *
     * <p>Returns skills matching the query in the skills search response format.
     * The CLI sends requests like: {@code GET /api/search?q=typescript&limit=10}
     *
     * @param q     search query keyword
     * @param limit maximum number of results to return (default 10)
     * @param namespaceId optional namespace filter
     * @param request HTTP request used to resolve the source URL
     * @return search results in skills-compatible format
     */
    @GetMapping(value = "/api/search", produces = MediaType.APPLICATION_JSON_VALUE)
    public SkillSearchResult searchSkills(
            @RequestParam(value = "q", required = false, defaultValue = "") String q,
            @RequestParam(value = "limit", required = false, defaultValue = "10") int limit,
            @RequestParam(value = "namespaceId", required = false) String namespaceId,
            HttpServletRequest request) {
        String sourceUrl = resolveSourceUrl(request);
        return skillsRegistryService.searchSkills(q, limit, namespaceId, sourceUrl);
    }
    
    /**
     * Well-Known index endpoint for {@code skills add} command.
     *
     * <p>Returns the index of all available skills.
     * The CLI requests: {@code GET /.well-known/skills/index.json}
     *
     * @param namespaceId optional namespace filter
     * @return skills index in well-known format
     */
    @GetMapping(value = "/.well-known/skills/index.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public WellKnownIndex getSkillsIndex(
            @RequestParam(value = "namespaceId", required = false) String namespaceId) {
        return skillsRegistryService.getSkillsIndex(namespaceId);
    }
    
    /**
     * Well-Known SKILL.md endpoint for {@code skills add} command.
     *
     * <p>Returns the SKILL.md content for a specific skill.
     * The CLI requests: {@code GET /.well-known/skills/{skillName}/SKILL.md}
     *
     * @param skillName the skill name
     * @param namespaceId optional namespace filter
     * @return SKILL.md content or 404 if not found
     */
    @GetMapping(value = "/.well-known/skills/{skillName}/SKILL.md", produces = "text/markdown")
    public ResponseEntity<String> getSkillMd(
            @PathVariable String skillName,
            @RequestParam(value = "namespaceId", required = false) String namespaceId) {
        String skillMd = skillsRegistryService.getSkillMd(skillName, namespaceId);
        if (skillMd == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(skillMd);
    }
    
    /**
     * Well-Known resource file endpoint for {@code skills add} command.
     *
     * <p>Returns the content of a resource file for a specific skill.
     * The CLI requests: {@code GET /.well-known/skills/{skillName}/{filePath}}
     * where filePath can include subdirectories (e.g., "resources/foo.json")
     *
     * @param skillName the skill name
     * @param namespaceId optional namespace filter
     * @param request HTTP request used to extract the file path
     * @return resource file content or 404 if not found
     */
    @GetMapping(value = "/.well-known/skills/{skillName}/**")
    public ResponseEntity<String> getSkillResource(
            @PathVariable String skillName,
            @RequestParam(value = "namespaceId", required = false) String namespaceId,
            HttpServletRequest request) {
        // Extract the file path after /.well-known/skills/{skillName}/
        String prefix = "/.well-known/skills/" + skillName + "/";
        String requestUri = request.getRequestURI();
        String filePath = requestUri.substring(requestUri.indexOf(prefix) + prefix.length());
        
        String content = skillsRegistryService.getSkillResource(skillName, filePath, namespaceId);
        if (content == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(content);
    }
    
    /**
     * Resolve the source URL from the incoming request.
     * This URL is used as the {@code source} field in search results,
     * so that {@code skills add {source}} can find the well-known endpoints.
     */
    private String resolveSourceUrl(HttpServletRequest request) {
        // Prefer X-Forwarded headers for reverse proxy scenarios
        String scheme = request.getHeader("X-Forwarded-Proto");
        if (scheme == null) {
            scheme = request.getScheme();
        }
        String host = request.getHeader("X-Forwarded-Host");
        if (host == null) {
            host = request.getHeader("Host");
        }
        if (host == null) {
            host = request.getServerName() + ":" + request.getServerPort();
        }
        return scheme + "://" + host;
    }
}
