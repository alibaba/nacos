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

package com.alibaba.nacos.mcpregistry.service;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.model.skills.SkillIndexManifest;
import com.alibaba.nacos.ai.service.skills.SkillIndexManifestService;
import com.alibaba.nacos.ai.service.skills.SkillOperationService;
import com.alibaba.nacos.api.ai.model.skills.Skill;
import com.alibaba.nacos.api.ai.model.skills.SkillResource;
import com.alibaba.nacos.api.ai.model.skills.SkillSummary;
import com.alibaba.nacos.api.ai.model.skills.SkillUtils;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.mcpregistry.model.skills.SkillsSearchItem;
import com.alibaba.nacos.mcpregistry.model.skills.SkillsSearchResponse;
import com.alibaba.nacos.mcpregistry.model.skills.WellKnownSkillEntry;
import com.alibaba.nacos.mcpregistry.model.skills.WellKnownSkillsIndex;
import com.alibaba.nacos.plugin.visibility.constant.VisibilityConstants;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Service for exposing Nacos skills through the well-known protocol expected by the skills CLI.
 *
 * @author nacos
 */
@Service
public class NacosSkillsRegistryService {

    private static final int LIST_PAGE_SIZE = 100;

    private static final Set<String> BINARY_EXTENSIONS = new HashSet<>();

    private static final String MARKDOWN_FILE = "SKILL.md";

    private static final String METADATA_ENCODING = "encoding";

    private static final String METADATA_ENCODING_BASE64 = "base64";

    static {
        Collections.addAll(BINARY_EXTENSIONS, "ttf", "otf", "woff", "woff2", "eot",
                "png", "jpg", "jpeg", "gif", "webp", "ico", "cur", "pdf", "bin");
    }

    private final SkillOperationService skillOperationService;

    private final SkillIndexManifestService skillIndexManifestService;

    public NacosSkillsRegistryService(SkillOperationService skillOperationService,
            SkillIndexManifestService skillIndexManifestService) {
        this.skillOperationService = skillOperationService;
        this.skillIndexManifestService = skillIndexManifestService;
    }

    /**
     * Build the well-known skill index for a namespace.
     *
     * @param namespaceId namespace to query
     * @return well-known index response
     * @throws NacosException if query fails
     */
    public WellKnownSkillsIndex buildIndex(String namespaceId) throws NacosException {
        List<ExportableSkill> skills = collectExportableSkills(namespaceId, null, Integer.MAX_VALUE);
        skills.sort(Comparator.comparing(each -> each.summary().getName()));
        List<WellKnownSkillEntry> entries = new ArrayList<>(skills.size());
        for (ExportableSkill each : skills) {
            entries.add(toWellKnownEntry(each));
        }
        WellKnownSkillsIndex result = new WellKnownSkillsIndex();
        result.setSkills(entries);
        return result;
    }

    /**
     * Search exportable skills for the CLI search endpoint.
     *
     * @param namespaceId namespace to query
     * @param query search keyword
     * @param limit max result count
     * @param sourceBaseUrl source URL reported to the CLI
     * @return CLI-compatible search response
     * @throws NacosException if query fails
     */
    public SkillsSearchResponse search(String namespaceId, String query, int limit, String sourceBaseUrl)
            throws NacosException {
        List<ExportableSkill> skills = collectExportableSkills(namespaceId, query, limit);
        skills.sort(Comparator.comparingLong((ExportableSkill each) -> safeDownloadCount(each.summary())).reversed()
                .thenComparing(each -> each.summary().getName()));
        List<SkillsSearchItem> items = new ArrayList<>(Math.min(limit, skills.size()));
        for (int i = 0; i < skills.size() && i < limit; i++) {
            SkillSummary summary = skills.get(i).summary();
            SkillsSearchItem item = new SkillsSearchItem();
            item.setId(summary.getName());
            item.setName(summary.getName());
            item.setInstalls(safeDownloadCount(summary));
            item.setSource(sourceBaseUrl);
            items.add(item);
        }
        SkillsSearchResponse result = new SkillsSearchResponse();
        result.setSkills(items);
        return result;
    }

    public String getSkillFileContent(String namespaceId, String skillName, String relativePath) throws NacosException {
        ExportableSkill skill = loadExportableSkill(namespaceId, skillName);
        if (skill == null) {
            return null;
        }
        if (MARKDOWN_FILE.equals(relativePath)) {
            return SkillUtils.toMarkdown(skill.skill());
        }
        if (skill.skill().getResource() == null) {
            return null;
        }
        for (SkillResource each : skill.skill().getResource().values()) {
            if (each == null) {
                continue;
            }
            if (relativePath.equals(buildRelativePath(each))) {
                return each.getContent();
            }
        }
        return null;
    }

    private List<ExportableSkill> collectExportableSkills(String namespaceId, String query, int limit)
            throws NacosException {
        List<ExportableSkill> result = new ArrayList<>();
        int pageNo = 1;
        int pagesAvailable = 1;
        while (pageNo <= pagesAvailable) {
            Page<SkillSummary> page = skillOperationService.listSkills(namespaceId, query, Constants.Skills.SEARCH_BLUR,
                    "download_count", pageNo, LIST_PAGE_SIZE);
            if (page == null || CollectionUtils.isEmpty(page.getPageItems())) {
                break;
            }
            pagesAvailable = page.getPagesAvailable();
            for (SkillSummary each : page.getPageItems()) {
                if (!isEligibleSummary(each)) {
                    continue;
                }
                ExportableSkill exportable = loadExportableSkill(namespaceId, each.getName(), each);
                if (exportable == null) {
                    continue;
                }
                result.add(exportable);
                if (limit != Integer.MAX_VALUE && result.size() >= limit) {
                    return result;
                }
            }
            pageNo++;
        }
        return result;
    }

    private ExportableSkill loadExportableSkill(String namespaceId, String skillName) throws NacosException {
        Page<SkillSummary> page = skillOperationService.listSkills(namespaceId, skillName, Constants.Skills.SEARCH_ACCURATE,
                "download_count", 1, 1);
        if (page == null || CollectionUtils.isEmpty(page.getPageItems())) {
            return null;
        }
        SkillSummary summary = page.getPageItems().get(0);
        if (!isEligibleSummary(summary)) {
            return null;
        }
        return loadExportableSkill(namespaceId, skillName, summary);
    }

    private ExportableSkill loadExportableSkill(String namespaceId, String skillName, SkillSummary summary)
            throws NacosException {
        SkillIndexManifest manifest = skillIndexManifestService.query(namespaceId, skillName);
        String resolvedVersion = SkillIndexManifestService.resolveVersion(manifest, null, SkillIndexManifest.LABEL_LATEST);
        if (StringUtils.isBlank(resolvedVersion)) {
            return null;
        }
        Skill skill;
        try {
            skill = skillOperationService.getSkillVersionDetail(namespaceId, skillName, resolvedVersion);
        } catch (NacosException e) {
            if (e.getErrCode() == NacosException.NOT_FOUND || e.getErrCode() == NacosException.NO_RIGHT) {
                return null;
            }
            throw e;
        }
        if (skill == null || StringUtils.isBlank(skill.getName()) || StringUtils.isBlank(skill.getDescription())) {
            return null;
        }
        List<String> files = buildFiles(skill);
        if (files == null) {
            return null;
        }
        return new ExportableSkill(summary, skill, files);
    }

    private boolean isEligibleSummary(SkillSummary summary) {
        return summary != null
                && summary.isEnable()
                && VisibilityConstants.SCOPE_PUBLIC.equalsIgnoreCase(summary.getScope())
                && summary.getOnlineCnt() != null
                && summary.getOnlineCnt() > 0
                && StringUtils.isNotBlank(summary.getName())
                && StringUtils.isNotBlank(summary.getDescription());
    }

    private List<String> buildFiles(Skill skill) {
        List<String> result = new ArrayList<>();
        result.add(MARKDOWN_FILE);
        if (skill.getResource() == null || skill.getResource().isEmpty()) {
            return result;
        }
        List<String> resourcePaths = new ArrayList<>(skill.getResource().size());
        for (SkillResource each : skill.getResource().values()) {
            if (each == null || StringUtils.isBlank(each.getName())) {
                continue;
            }
            if (isBinaryResource(each)) {
                return null;
            }
            resourcePaths.add(buildRelativePath(each));
        }
        resourcePaths.sort(String::compareTo);
        result.addAll(resourcePaths);
        return result;
    }

    private boolean isBinaryResource(SkillResource resource) {
        Map<String, Object> metadata = resource.getMetadata();
        if (metadata != null && METADATA_ENCODING_BASE64.equals(metadata.get(METADATA_ENCODING))) {
            return true;
        }
        String name = resource.getName();
        if (StringUtils.isBlank(name)) {
            return false;
        }
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return false;
        }
        String ext = name.substring(dot + 1).toLowerCase(Locale.ENGLISH);
        return BINARY_EXTENSIONS.contains(ext);
    }

    private String buildRelativePath(SkillResource resource) {
        if (StringUtils.isBlank(resource.getType())) {
            return resource.getName();
        }
        return resource.getType() + "/" + resource.getName();
    }

    private WellKnownSkillEntry toWellKnownEntry(ExportableSkill each) {
        WellKnownSkillEntry entry = new WellKnownSkillEntry();
        entry.setName(each.skill().getName());
        entry.setDescription(each.skill().getDescription());
        entry.setFiles(each.files());
        return entry;
    }

    private long safeDownloadCount(SkillSummary summary) {
        return summary.getDownloadCount() == null ? 0L : summary.getDownloadCount();
    }

    private record ExportableSkill(SkillSummary summary, Skill skill, List<String> files) {
    }
}
