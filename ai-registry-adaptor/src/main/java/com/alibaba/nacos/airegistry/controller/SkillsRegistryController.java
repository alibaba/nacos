/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.airegistry.controller;

import com.alibaba.nacos.api.annotation.Since;
import com.alibaba.nacos.api.annotation.NacosApi;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.airegistry.form.SkillsFileQueryForm;
import com.alibaba.nacos.airegistry.form.SkillsSearchForm;
import com.alibaba.nacos.airegistry.model.skills.SkillsSearchResponse;
import com.alibaba.nacos.airegistry.model.skills.WellKnownSkillsIndex;
import com.alibaba.nacos.airegistry.service.NacosSkillsRegistryService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Skills CLI compatible registry endpoints hosted by the MCP registry adaptor.
 *
 * @author nacos
 */
@NacosApi
@RestController
@ConditionalOnProperty(name = "nacos.ai.skill.registry.enabled", havingValue = "true")
public class SkillsRegistryController {
    
    private static final String BASE_PATH = "/registry";
    
    private static final String WELL_KNOWN_AGENT_SKILLS =
        BASE_PATH + "/{namespaceId}/.well-known/agent-skills";
    
    private static final String WELL_KNOWN_SKILLS = BASE_PATH + "/{namespaceId}/.well-known/skills";
    
    private static final String APPLICATION_ZIP_VALUE = "application/zip";
    
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();
    
    private final NacosSkillsRegistryService nacosSkillsRegistryService;
    
    public SkillsRegistryController(NacosSkillsRegistryService nacosSkillsRegistryService) {
        this.nacosSkillsRegistryService = nacosSkillsRegistryService;
    }
    
    /**
     * Expose well-known index.json for the skills CLI.
     *
     * @param namespaceId namespace to query
     * @return well-known index payload
     * @throws NacosException if query fails
     */
    @Since("3.2.2")
    @GetMapping(value = WELL_KNOWN_AGENT_SKILLS + "/index.json",
        produces = MediaType.APPLICATION_JSON_VALUE)
    public WellKnownSkillsIndex getAgentSkillsIndex(@PathVariable String namespaceId)
        throws NacosException {
        return nacosSkillsRegistryService.buildAgentSkillsIndex(namespaceId);
    }
    
    /**
     * Expose legacy well-known index.json for v0.1-compatible clients.
     *
     * @param namespaceId namespace to query
     * @return legacy well-known index payload
     * @throws NacosException if query fails
     */
    @Since("3.2.2")
    @GetMapping(value = WELL_KNOWN_SKILLS + "/index.json",
        produces = MediaType.APPLICATION_JSON_VALUE)
    public WellKnownSkillsIndex getLegacySkillsIndex(@PathVariable String namespaceId)
        throws NacosException {
        return nacosSkillsRegistryService.buildLegacySkillsIndex(namespaceId);
    }
    
    /**
     * Expose CLI-compatible search results under the adaptor endpoint.
     *
     * @param namespaceId namespace to query
     * @param form search query form
     * @param request current HTTP request
     * @return search response with CLI-compatible shape
     * @throws NacosException if query fails
     */
    @Since("3.2.1")
    @GetMapping(value = BASE_PATH + "/{namespaceId}/api/search",
        produces = MediaType.APPLICATION_JSON_VALUE)
    public SkillsSearchResponse search(@PathVariable String namespaceId, SkillsSearchForm form,
        HttpServletRequest request)
        throws NacosException {
        form.setNamespaceId(namespaceId);
        form.validate();
        return nacosSkillsRegistryService.search(namespaceId, form.getQ(), form.getLimit(),
            buildSourceBaseUrl(request,
                namespaceId));
    }
    
    /**
     * Return the exported SKILL.md for a namespace skill.
     *
     * @param namespaceId namespace to query
     * @param skillName skill name
     * @return skill markdown when the skill is exportable, otherwise 404
     * @throws NacosException if query fails
     */
    @Since("3.2.1")
    @GetMapping(value = {
        WELL_KNOWN_AGENT_SKILLS + "/{skillName}/SKILL.md",
        WELL_KNOWN_SKILLS + "/{skillName}/SKILL.md"
    }, produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> getSkillMarkdown(@PathVariable String namespaceId,
        @PathVariable String skillName)
        throws NacosException {
        SkillsFileQueryForm form = new SkillsFileQueryForm();
        form.setNamespaceId(namespaceId);
        form.setSkillName(skillName);
        form.setFilePath("SKILL.md");
        form.validate();
        String content = nacosSkillsRegistryService.getSkillFileContent(namespaceId, skillName,
            form.getFilePath());
        return content == null ? ResponseEntity.notFound().build()
            : ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(content);
    }
    
    /**
     * Return an exported skill archive for v0.2 well-known discovery.
     *
     * @param namespaceId namespace to query
     * @param skillName skill name
     * @return skill ZIP archive when exportable, otherwise 404
     * @throws NacosException if query fails
     */
    @Since("3.2.2")
    @GetMapping(value = {
        WELL_KNOWN_AGENT_SKILLS + "/{skillName}.zip",
        WELL_KNOWN_SKILLS + "/{skillName}.zip"
    }, produces = APPLICATION_ZIP_VALUE)
    public ResponseEntity<byte[]> getSkillArchive(@PathVariable String namespaceId,
        @PathVariable String skillName)
        throws NacosException {
        byte[] content = nacosSkillsRegistryService.getSkillArchiveContent(namespaceId,
            skillName);
        return content == null ? ResponseEntity.notFound().build()
            : ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment;filename=" + skillName + ".zip")
                .contentType(MediaType.parseMediaType(APPLICATION_ZIP_VALUE))
                .body(content);
    }
    
    /**
     * Return an exported text resource for a namespace skill.
     *
     * @param namespaceId namespace to query
     * @param skillName skill name
     * @param request current HTTP request
     * @return file content when the skill and file are exportable, otherwise 404
     * @throws NacosException if query fails
     */
    @Since("3.2.1")
    @GetMapping(value = {
        WELL_KNOWN_AGENT_SKILLS + "/{skillName}/**",
        WELL_KNOWN_SKILLS + "/{skillName}/**"
    }, produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> getSkillFile(@PathVariable String namespaceId,
        @PathVariable String skillName,
        HttpServletRequest request) throws NacosException {
        String filePath = extractFilePath(request);
        SkillsFileQueryForm form = new SkillsFileQueryForm();
        form.setNamespaceId(namespaceId);
        form.setSkillName(skillName);
        form.setFilePath(filePath);
        form.validate();
        String content = nacosSkillsRegistryService.getSkillFileContent(namespaceId, skillName,
            form.getFilePath());
        return content == null ? ResponseEntity.notFound().build()
            : ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(content);
    }
    
    private String buildSourceBaseUrl(HttpServletRequest request, String namespaceId) {
        return ServletUriComponentsBuilder.fromRequestUri(request)
            .replacePath(BASE_PATH + "/{namespaceId}")
            .replaceQuery(null)
            .buildAndExpand(namespaceId)
            .toUriString();
    }
    
    private String extractFilePath(HttpServletRequest request) {
        String pathWithinMapping =
            (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        String bestMatchingPattern =
            (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String extracted =
            PATH_MATCHER.extractPathWithinPattern(bestMatchingPattern, pathWithinMapping);
        if (StringUtils.isBlank(extracted)) {
            return extracted;
        }
        return extracted.replaceFirst("^/+", "");
    }
}
