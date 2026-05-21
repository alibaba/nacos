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

package com.alibaba.nacos.ai.importer.skill;

import com.alibaba.nacos.ai.utils.SkillZipParser;
import com.alibaba.nacos.api.ai.model.skills.Skill;
import com.alibaba.nacos.api.ai.model.skills.SkillUtils;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.ai.importer.model.AiResourceImportArtifact;
import com.alibaba.nacos.plugin.ai.importer.model.AiResourceImportCandidate;
import com.alibaba.nacos.plugin.ai.importer.model.AiResourceImportCandidatePage;
import com.alibaba.nacos.plugin.ai.importer.model.AiResourceImportContext;
import com.alibaba.nacos.plugin.ai.importer.model.AiResourceImportItem;
import com.alibaba.nacos.plugin.ai.importer.model.AiResourceImportPayloadKind;
import com.alibaba.nacos.plugin.ai.importer.model.AiResourceImportSource;
import com.alibaba.nacos.plugin.ai.importer.spi.AiResourceImportService;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Importer for Skill well-known registry endpoints.
 *
 * @author xiweng.yy
 * @since 3.2.1
 */
public class SkillWellKnownImportService implements AiResourceImportService {
    
    public static final String RESOURCE_TYPE_SKILL = "skill";
    
    private static final String WELL_KNOWN_AGENT_SKILLS = "/.well-known/agent-skills";
    
    private static final String WELL_KNOWN_SKILLS = "/.well-known/skills";
    
    private static final String INDEX_JSON = "/index.json";
    
    private static final String MARKDOWN_FILE = "SKILL.md";
    
    private static final String METADATA_FILE_COUNT = "fileCount";
    
    private static final String METADATA_SOURCE = "source";
    
    private static final int DEFAULT_LIMIT = 30;
    
    private static final int DEFAULT_CONNECT_TIMEOUT_SECONDS = 10;
    
    private static final int DEFAULT_READ_TIMEOUT_SECONDS = 20;
    
    private final HttpClient httpClient;
    
    public SkillWellKnownImportService() {
        this(HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(DEFAULT_CONNECT_TIMEOUT_SECONDS))
            .build());
    }
    
    SkillWellKnownImportService(HttpClient httpClient) {
        this.httpClient = httpClient;
    }
    
    @Override
    public String importerType() {
        return SkillWellKnownImportServiceBuilder.IMPORTER_TYPE;
    }
    
    @Override
    public Set<String> supportedResourceTypes() {
        return Collections.singleton(RESOURCE_TYPE_SKILL);
    }
    
    @Override
    public AiResourceImportCandidatePage search(AiResourceImportContext context)
        throws NacosException {
        try {
            List<WellKnownSkillEntry> matched = filterSkills(fetchIndex(context).getSkills(),
                context.getQuery());
            int offset = parseCursor(context.getCursor());
            int limit = resolveLimit(context.getLimit());
            int toIndex = Math.min(offset + limit, matched.size());
            List<AiResourceImportCandidate> items = new ArrayList<>();
            for (int i = offset; i < toIndex; i++) {
                items.add(toCandidate(matched.get(i)));
            }
            AiResourceImportCandidatePage result = new AiResourceImportCandidatePage();
            result.setItems(items);
            result.setHasMore(toIndex < matched.size());
            result.setNextCursor(result.isHasMore() ? String.valueOf(toIndex) : null);
            return result;
        } catch (NacosException e) {
            throw e;
        } catch (Exception e) {
            throw dataAccess("Search Skill well-known source failed: " + e.getMessage(), e);
        }
    }
    
    @Override
    public AiResourceImportArtifact fetch(AiResourceImportContext context,
        AiResourceImportItem item) throws NacosException {
        try {
            String skillName = resolveExternalId(item);
            WellKnownSkillEntry entry = findSkillEntry(fetchIndex(context).getSkills(), skillName);
            byte[] zipBytes = fetchSkillZip(context, entry);
            Skill skill = SkillZipParser.parseSkillFromZip(zipBytes, context.getNamespaceId());
            AiResourceImportArtifact result = new AiResourceImportArtifact();
            result.setResourceType(RESOURCE_TYPE_SKILL);
            result.setExternalId(skillName);
            result.setName(skill.getName());
            result.setVersion(SkillZipParser.resolveVersionFromZip(zipBytes));
            result.setDescription(skill.getDescription());
            result.setPayloadKind(AiResourceImportPayloadKind.SKILL_ZIP);
            result.setPayload(zipBytes);
            result.setSourceMetadata(buildMetadata(entry));
            return result;
        } catch (NacosException e) {
            throw e;
        } catch (Exception e) {
            throw dataAccess("Fetch Skill well-known artifact failed: " + e.getMessage(), e);
        }
    }
    
    private WellKnownSkillsIndex fetchIndex(AiResourceImportContext context) throws Exception {
        String body = fetchString(indexUrl(requireSource(context)));
        WellKnownSkillsIndex result = JacksonUtils.toObj(body, WellKnownSkillsIndex.class);
        if (result == null) {
            throw invalid("Skill well-known index cannot be parsed.");
        }
        return result;
    }
    
    private AiResourceImportSource requireSource(AiResourceImportContext context)
        throws NacosException {
        if (context == null || context.getSource() == null
            || StringUtils.isBlank(context.getSource().getEndpoint())) {
            throw invalid("Skill well-known import source endpoint must not be empty.");
        }
        return context.getSource();
    }
    
    private List<WellKnownSkillEntry> filterSkills(List<WellKnownSkillEntry> skills,
        String query) {
        if (CollectionUtils.isEmpty(skills)) {
            return Collections.emptyList();
        }
        String normalizedQuery =
            StringUtils.isBlank(query) ? null : query.toLowerCase(Locale.ENGLISH);
        List<WellKnownSkillEntry> result = new ArrayList<>(skills.size());
        for (WellKnownSkillEntry each : skills) {
            if (each == null || StringUtils.isBlank(each.getName())) {
                continue;
            }
            if (normalizedQuery == null || contains(each.getName(), normalizedQuery)
                || contains(each.getDescription(), normalizedQuery)) {
                result.add(each);
            }
        }
        return result;
    }
    
    private boolean contains(String value, String normalizedQuery) {
        return StringUtils.isNotBlank(value)
            && value.toLowerCase(Locale.ENGLISH).contains(normalizedQuery);
    }
    
    private int parseCursor(String cursor) {
        if (StringUtils.isBlank(cursor)) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(cursor));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
    
    private int resolveLimit(Integer limit) {
        return limit == null || limit <= 0 ? DEFAULT_LIMIT : limit;
    }
    
    private AiResourceImportCandidate toCandidate(WellKnownSkillEntry entry) {
        AiResourceImportCandidate result = new AiResourceImportCandidate();
        result.setResourceType(RESOURCE_TYPE_SKILL);
        result.setExternalId(entry.getName());
        result.setName(entry.getName());
        result.setDescription(entry.getDescription());
        result.setMetadata(buildMetadata(entry));
        return result;
    }
    
    private WellKnownSkillEntry findSkillEntry(List<WellKnownSkillEntry> skills, String skillName)
        throws NacosException {
        if (CollectionUtils.isNotEmpty(skills)) {
            for (WellKnownSkillEntry each : skills) {
                if (each != null && StringUtils.equals(skillName, each.getName())) {
                    return each;
                }
            }
        }
        throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
            "Skill not found in well-known index: " + skillName);
    }
    
    private byte[] fetchSkillZip(AiResourceImportContext context, WellKnownSkillEntry entry)
        throws Exception {
        String base = wellKnownBase(requireSource(context));
        List<String> files = normalizeFiles(entry.getFiles());
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            for (String each : files) {
                SkillUtils.validatePathSafety(each);
                byte[] bytes = fetchBytes(fileUrl(base, entry.getName(), each));
                zip.putNextEntry(new ZipEntry(entry.getName() + "/" + each));
                zip.write(bytes);
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }
    
    private List<String> normalizeFiles(List<String> files) {
        List<String> result = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(files)) {
            for (String each : files) {
                if (StringUtils.isNotBlank(each) && !result.contains(each)) {
                    result.add(each);
                }
            }
        }
        if (!result.contains(MARKDOWN_FILE)) {
            result.add(0, MARKDOWN_FILE);
        }
        return result;
    }
    
    private String resolveExternalId(AiResourceImportItem item) throws NacosException {
        if (item == null) {
            throw invalid("Skill well-known import item must not be null.");
        }
        String externalId = StringUtils.isNotBlank(item.getExternalId()) ? item.getExternalId()
            : item.getName();
        if (StringUtils.isBlank(externalId)) {
            throw invalid("Skill well-known import item external id must not be empty.");
        }
        return externalId;
    }
    
    private Map<String, String> buildMetadata(WellKnownSkillEntry entry) {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put(METADATA_FILE_COUNT, String.valueOf(normalizeFiles(entry.getFiles()).size()));
        metadata.put(METADATA_SOURCE, WELL_KNOWN_AGENT_SKILLS);
        return metadata;
    }
    
    private String indexUrl(AiResourceImportSource source) throws NacosException {
        return wellKnownBase(source) + INDEX_JSON;
    }
    
    private String wellKnownBase(AiResourceImportSource source) throws NacosException {
        String endpoint = trimTrailingSlash(source.getEndpoint());
        if (endpoint.endsWith(WELL_KNOWN_AGENT_SKILLS) || endpoint.endsWith(WELL_KNOWN_SKILLS)) {
            return endpoint;
        }
        return endpoint + WELL_KNOWN_AGENT_SKILLS;
    }
    
    private String fileUrl(String base, String skillName, String file) {
        return base + "/" + encodePathSegment(skillName) + "/" + encodePath(file);
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
    
    private String trimTrailingSlash(String value) throws NacosException {
        if (StringUtils.isBlank(value)) {
            throw invalid("Skill well-known import source endpoint must not be empty.");
        }
        String result = value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
    
    private String fetchString(String url) throws Exception {
        byte[] bytes = fetchBytes(url);
        return new String(bytes, StandardCharsets.UTF_8);
    }
    
    private byte[] fetchBytes(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(DEFAULT_READ_TIMEOUT_SECONDS))
            .header("Accept", "*/*")
            .GET()
            .build();
        HttpResponse<byte[]> response = httpClient.send(request,
            HttpResponse.BodyHandlers.ofByteArray());
        int code = response.statusCode();
        if (code < 200 || code >= 300) {
            throw new IllegalStateException("HTTP " + code + " when fetching " + url);
        }
        return response.body();
    }
    
    private NacosException invalid(String message) {
        return new NacosApiException(NacosException.INVALID_PARAM,
            ErrorCode.PARAMETER_VALIDATE_ERROR, message);
    }
    
    private NacosException dataAccess(String message, Throwable cause) {
        return new NacosApiException(NacosException.SERVER_ERROR, ErrorCode.DATA_ACCESS_ERROR,
            cause, message);
    }
    
    static class WellKnownSkillsIndex {
        
        private List<WellKnownSkillEntry> skills;
        
        public List<WellKnownSkillEntry> getSkills() {
            return skills;
        }
        
        public void setSkills(List<WellKnownSkillEntry> skills) {
            this.skills = skills;
        }
    }
    
    static class WellKnownSkillEntry {
        
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
