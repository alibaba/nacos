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

package com.alibaba.nacos.airegistry.service;

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
import com.alibaba.nacos.airegistry.model.skills.SkillsSearchItem;
import com.alibaba.nacos.airegistry.model.skills.SkillsSearchResponse;
import com.alibaba.nacos.airegistry.model.skills.WellKnownSkillEntry;
import com.alibaba.nacos.airegistry.model.skills.WellKnownSkillsIndex;
import com.alibaba.nacos.plugin.visibility.constant.VisibilityConstants;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Service for exposing Nacos skills through the well-known protocol expected by the skills CLI.
 *
 * @author nacos
 */
@Service
@ConditionalOnProperty(name = "nacos.ai.skill.registry.enabled", havingValue = "true")
public class NacosSkillsRegistryService {
    
    private static final String SCHEMA_0_2 =
        "https://schemas.agentskills.io/discovery/0.2.0/schema.json";
    
    private static final int LIST_PAGE_SIZE = 100;
    
    private static final Set<String> BINARY_EXTENSIONS = new HashSet<>();
    
    private static final String MARKDOWN_FILE = "SKILL.md";
    
    private static final String ARCHIVE_TYPE = "archive";
    
    private static final String SKILL_MD_TYPE = "skill-md";
    
    private static final String ZIP_SUFFIX = ".zip";
    
    private static final String DIGEST_SHA256_PREFIX = "sha256:";
    
    private static final long STABLE_ZIP_ENTRY_TIME = 0L;
    
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
    public WellKnownSkillsIndex buildAgentSkillsIndex(String namespaceId) throws NacosException {
        return buildIndex(namespaceId, WellKnownIndexVersion.V0_2_0);
    }
    
    /**
     * Build the legacy well-known skill index for a namespace.
     *
     * @param namespaceId namespace to query
     * @return legacy well-known index response
     * @throws NacosException if query fails
     */
    public WellKnownSkillsIndex buildLegacySkillsIndex(String namespaceId) throws NacosException {
        return buildIndex(namespaceId, WellKnownIndexVersion.V0_1_0);
    }
    
    private WellKnownSkillsIndex buildIndex(String namespaceId, WellKnownIndexVersion version)
        throws NacosException {
        List<ExportableSkill> skills =
            collectExportableSkills(namespaceId, null, Integer.MAX_VALUE);
        skills.sort(Comparator.comparing(each -> each.summary().getName()));
        List<WellKnownSkillEntry> entries = new ArrayList<>(skills.size());
        for (ExportableSkill each : skills) {
            entries.add(toWellKnownEntry(each, version));
        }
        WellKnownSkillsIndex result = new WellKnownSkillsIndex();
        if (version == WellKnownIndexVersion.V0_2_0) {
            result.setSchema(SCHEMA_0_2);
        }
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
    public SkillsSearchResponse search(String namespaceId, String query, int limit,
        String sourceBaseUrl)
        throws NacosException {
        List<ExportableSkill> skills = collectExportableSkills(namespaceId, query, limit);
        skills.sort(Comparator
            .comparingLong((ExportableSkill each) -> safeDownloadCount(each.summary())).reversed()
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
    
    public String getSkillFileContent(String namespaceId, String skillName, String relativePath)
        throws NacosException {
        ExportableSkill skill = loadExportableSkill(namespaceId, skillName, false);
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
    
    public byte[] getSkillArchiveContent(String namespaceId, String skillName)
        throws NacosException {
        ExportableSkill skill = loadExportableSkill(namespaceId, skillName, true);
        return skill == null ? null : toArchiveBytes(skill.skill());
    }
    
    private List<ExportableSkill> collectExportableSkills(String namespaceId, String query,
        int limit)
        throws NacosException {
        List<ExportableSkill> result = new ArrayList<>();
        int pageNo = 1;
        int pagesAvailable = 1;
        while (pageNo <= pagesAvailable) {
            Page<SkillSummary> page =
                skillOperationService.listSkills(namespaceId, query, Constants.Skills.SEARCH_BLUR,
                    "download_count", pageNo, LIST_PAGE_SIZE);
            if (page == null || CollectionUtils.isEmpty(page.getPageItems())) {
                break;
            }
            pagesAvailable = page.getPagesAvailable();
            for (SkillSummary each : page.getPageItems()) {
                if (!isEligibleSummary(each)) {
                    continue;
                }
                ExportableSkill exportable =
                    loadExportableSkill(namespaceId, each.getName(), each, false);
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
    
    private ExportableSkill loadExportableSkill(String namespaceId, String skillName,
        boolean download)
        throws NacosException {
        Page<SkillSummary> page = skillOperationService.listSkills(namespaceId, skillName,
            Constants.Skills.SEARCH_ACCURATE,
            "download_count", 1, 1);
        if (page == null || CollectionUtils.isEmpty(page.getPageItems())) {
            return null;
        }
        SkillSummary summary = page.getPageItems().get(0);
        if (!isEligibleSummary(summary)) {
            return null;
        }
        return loadExportableSkill(namespaceId, skillName, summary, download);
    }
    
    private ExportableSkill loadExportableSkill(String namespaceId, String skillName,
        SkillSummary summary, boolean download)
        throws NacosException {
        SkillIndexManifest manifest = skillIndexManifestService.query(namespaceId, skillName);
        String resolvedVersion = SkillIndexManifestService.resolveVersion(manifest, null,
            SkillIndexManifest.LABEL_LATEST);
        if (StringUtils.isBlank(resolvedVersion)) {
            return null;
        }
        Skill skill;
        try {
            skill = download ? skillOperationService.downloadSkillVersion(namespaceId, skillName,
                resolvedVersion)
                : skillOperationService.getSkillVersionDetail(namespaceId, skillName,
                    resolvedVersion);
        } catch (NacosException e) {
            if (e.getErrCode() == NacosException.NOT_FOUND
                || e.getErrCode() == NacosException.NO_RIGHT) {
                return null;
            }
            throw e;
        }
        if (skill == null || StringUtils.isBlank(skill.getName())
            || StringUtils.isBlank(skill.getDescription())) {
            return null;
        }
        List<String> files = buildFiles(skill);
        if (files == null) {
            return null;
        }
        return new ExportableSkill(summary, skill, resolvedVersion, files);
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
    
    private WellKnownSkillEntry toWellKnownEntry(ExportableSkill each,
        WellKnownIndexVersion version)
        throws NacosException {
        WellKnownSkillEntry entry = new WellKnownSkillEntry();
        entry.setName(each.skill().getName());
        entry.setDescription(each.skill().getDescription());
        if (version == WellKnownIndexVersion.V0_1_0) {
            entry.setFiles(each.files());
            return entry;
        }
        entry.setVersion(each.version());
        if (isSingleMarkdownSkill(each)) {
            entry.setType(SKILL_MD_TYPE);
            entry.setUrl(fileUrl(each.skill().getName(), MARKDOWN_FILE));
            entry.setDigest(
                sha256Digest(SkillUtils.toMarkdown(each.skill()).getBytes(StandardCharsets.UTF_8)));
            return entry;
        }
        entry.setType(ARCHIVE_TYPE);
        entry.setUrl(archiveUrl(each.skill().getName()));
        entry.setDigest(sha256Digest(toArchiveBytes(each.skill())));
        return entry;
    }
    
    private boolean isSingleMarkdownSkill(ExportableSkill skill) {
        return skill.files().size() == 1 && MARKDOWN_FILE.equals(skill.files().get(0));
    }
    
    private byte[] toArchiveBytes(Skill skill) throws NacosException {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
                addZipEntry(zip, MARKDOWN_FILE,
                    SkillUtils.toMarkdown(skill).getBytes(StandardCharsets.UTF_8));
                if (skill.getResource() != null && !skill.getResource().isEmpty()) {
                    List<SkillResource> resources = new ArrayList<>();
                    for (SkillResource each : skill.getResource().values()) {
                        if (each != null && StringUtils.isNotBlank(each.getName())) {
                            resources.add(each);
                        }
                    }
                    resources.sort(Comparator.comparing(this::buildRelativePath));
                    for (SkillResource each : resources) {
                        addZipEntry(zip, buildRelativePath(each),
                            each.getContent() == null ? new byte[0]
                                : each.getContent().getBytes(StandardCharsets.UTF_8));
                    }
                }
            }
            return output.toByteArray();
        } catch (Exception e) {
            throw new NacosException(NacosException.SERVER_ERROR,
                "Failed to create skill well-known archive: " + e.getMessage(), e);
        }
    }
    
    private void addZipEntry(ZipOutputStream zip, String path, byte[] bytes) throws Exception {
        SkillUtils.validatePathSafety(path);
        ZipEntry entry = new ZipEntry(path);
        entry.setTime(STABLE_ZIP_ENTRY_TIME);
        zip.putNextEntry(entry);
        zip.write(bytes);
        zip.closeEntry();
    }
    
    private String fileUrl(String skillName, String file) {
        return encodePathSegment(skillName) + "/" + encodePath(file);
    }
    
    private String archiveUrl(String skillName) {
        return encodePathSegment(skillName) + ZIP_SUFFIX;
    }
    
    private String encodePath(String path) {
        String[] segments = path.split("/");
        StringBuilder result = new StringBuilder();
        for (String each : segments) {
            if (result.length() > 0) {
                result.append('/');
            }
            result.append(encodePathSegment(each));
        }
        return result.toString();
    }
    
    private String encodePathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
    
    private String sha256Digest(byte[] bytes) throws NacosException {
        return DIGEST_SHA256_PREFIX + DigestUtils.sha256Hex(bytes);
    }
    
    private long safeDownloadCount(SkillSummary summary) {
        return summary.getDownloadCount() == null ? 0L : summary.getDownloadCount();
    }
    
    private enum WellKnownIndexVersion {
        
        V0_1_0,
        
        V0_2_0
    }
    
    private record ExportableSkill(SkillSummary summary, Skill skill, String version,
        List<String> files) {
    }
}
