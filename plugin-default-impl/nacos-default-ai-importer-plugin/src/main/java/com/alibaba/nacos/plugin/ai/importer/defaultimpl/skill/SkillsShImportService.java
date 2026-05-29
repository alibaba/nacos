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

package com.alibaba.nacos.plugin.ai.importer.defaultimpl.skill;

import com.alibaba.nacos.api.ai.model.skills.SkillUtils;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.ai.importer.AiResourceImportConstants;
import com.alibaba.nacos.plugin.ai.importer.defaultimpl.http.DefaultImportHttpClient;
import com.alibaba.nacos.plugin.ai.importer.defaultimpl.http.ImportHttpResponse;
import com.alibaba.nacos.plugin.ai.importer.model.AiResourceImportArtifact;
import com.alibaba.nacos.plugin.ai.importer.model.AiResourceImportCandidate;
import com.alibaba.nacos.plugin.ai.importer.model.AiResourceImportCandidatePage;
import com.alibaba.nacos.plugin.ai.importer.model.AiResourceImportContext;
import com.alibaba.nacos.plugin.ai.importer.model.AiResourceImportItem;
import com.alibaba.nacos.plugin.ai.importer.model.AiResourceImportPayloadKind;
import com.alibaba.nacos.plugin.ai.importer.model.AiResourceImportSource;
import com.alibaba.nacos.plugin.ai.importer.spi.AiResourceImportService;

import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Importer for the skills.sh search and download APIs.
 *
 * @author xiweng.yy
 * @since 3.2.1
 */
public class SkillsShImportService implements AiResourceImportService {
    
    public static final String RESOURCE_TYPE_SKILL = AiResourceImportConstants.RESOURCE_TYPE_SKILL;
    
    private static final String API_SEARCH = "/api/search";
    
    private static final String API_DOWNLOAD = "/api/download";
    
    private static final String METADATA_SOURCE = "source";
    
    private static final String METADATA_ARTIFACT_URL = "artifactUrl";
    
    private static final String METADATA_REPOSITORY = "repository";
    
    private static final String METADATA_REPOSITORY_SOURCE = "repositorySource";
    
    private static final String METADATA_SKILL_ID = "skillId";
    
    private static final String METADATA_INSTALLS = "installs";
    
    private static final String METADATA_HASH = "hash";
    
    private static final String SKILL_MARKDOWN_FILE = "SKILL.md";
    
    private static final String DEFAULT_SEARCH_QUERY = "skill";
    
    private static final int MIN_SEARCH_QUERY_LENGTH = 2;
    
    private static final int DEFAULT_LIMIT = 30;
    
    private static final int DEFAULT_READ_TIMEOUT_SECONDS = 20;
    
    private static final int DEFAULT_MAX_FILE_COUNT = 500;
    
    private final DefaultImportHttpClient httpClient;
    
    public SkillsShImportService() {
        this(new DefaultImportHttpClient());
    }
    
    SkillsShImportService(HttpClient httpClient) {
        this(new DefaultImportHttpClient(httpClient));
    }
    
    SkillsShImportService(DefaultImportHttpClient httpClient) {
        this.httpClient = httpClient;
    }
    
    @Override
    public String importerType() {
        return SkillsShImportServiceBuilder.IMPORTER_TYPE;
    }
    
    @Override
    public Set<String> supportedResourceTypes() {
        return Collections.singleton(RESOURCE_TYPE_SKILL);
    }
    
    @Override
    public AiResourceImportCandidatePage search(AiResourceImportContext context)
        throws NacosException {
        try {
            AiResourceImportSource source = requireSource(context);
            String apiRoot = resolveApiRoot(source);
            int resultLimit = resolveLimit(context.getLimit());
            ImportHttpResponse response = fetchUrl(source, searchUrl(apiRoot,
                resolveQuery(context.getQuery()), resolveSearchFetchLimit(resultLimit)));
            if (!response.isSuccess()) {
                throw new IllegalStateException(
                    "HTTP " + response.getStatusCode() + " when fetching " + response.getUrl());
            }
            SkillsShSearchResponse searchResponse =
                JacksonUtils.toObj(response.getBody(), SkillsShSearchResponse.class);
            AiResourceImportCandidatePage result = new AiResourceImportCandidatePage();
            result.setItems(toCandidates(apiRoot, searchResponse, resultLimit));
            result.setHasMore(false);
            result.setNextCursor(null);
            return result;
        } catch (NacosException e) {
            throw e;
        } catch (Exception e) {
            throw dataAccess("Search skills.sh source failed: " + e.getMessage(), e);
        }
    }
    
    @Override
    public AiResourceImportArtifact fetch(AiResourceImportContext context,
        AiResourceImportItem item) throws NacosException {
        try {
            AiResourceImportSource source = requireSource(context);
            String apiRoot = resolveApiRoot(source);
            SkillsShSkillRef skillRef = resolveSkillRef(item);
            ImportHttpResponse response = fetchUrl(source, downloadUrl(apiRoot, skillRef));
            if (!response.isSuccess()) {
                throw new IllegalStateException(
                    "HTTP " + response.getStatusCode() + " when fetching " + response.getUrl());
            }
            SkillsShDownloadResponse downloadResponse =
                JacksonUtils.toObj(response.getBody(), SkillsShDownloadResponse.class);
            byte[] zipBytes = toSkillZip(source, skillRef, downloadResponse);
            AiResourceImportArtifact result = new AiResourceImportArtifact();
            result.setResourceType(RESOURCE_TYPE_SKILL);
            result.setExternalId(skillRef.getExternalId());
            result.setName(skillRef.getName());
            result.setPayloadKind(AiResourceImportPayloadKind.SKILL_ZIP);
            result.setPayload(zipBytes);
            result.setSourceMetadata(buildArtifactMetadata(apiRoot, skillRef, downloadResponse));
            return result;
        } catch (NacosException e) {
            throw e;
        } catch (Exception e) {
            throw dataAccess("Fetch skills.sh artifact failed: " + e.getMessage(), e);
        }
    }
    
    private AiResourceImportSource requireSource(AiResourceImportContext context)
        throws NacosException {
        if (context == null || context.getSource() == null
            || StringUtils.isBlank(context.getSource().getEndpoint())) {
            throw invalid("skills.sh import source endpoint must not be empty.");
        }
        return context.getSource();
    }
    
    private int resolveLimit(int limit) {
        return limit <= 0 ? DEFAULT_LIMIT : limit;
    }
    
    private int resolveSearchFetchLimit(int resultLimit) {
        return Math.max(resultLimit, DEFAULT_LIMIT);
    }
    
    private String resolveQuery(String query) throws NacosException {
        if (StringUtils.isBlank(query)) {
            return DEFAULT_SEARCH_QUERY;
        }
        String result = query.trim();
        if (result.length() < MIN_SEARCH_QUERY_LENGTH) {
            throw invalid("skills.sh search query must be at least 2 characters.");
        }
        return result;
    }
    
    private List<AiResourceImportCandidate> toCandidates(String apiRoot,
        SkillsShSearchResponse searchResponse, int limit) throws NacosException {
        if (searchResponse == null || CollectionUtils.isEmpty(searchResponse.getSkills())) {
            return Collections.emptyList();
        }
        List<AiResourceImportCandidate> result =
            new ArrayList<>(searchResponse.getSkills().size());
        for (SkillsShSearchItem each : searchResponse.getSkills()) {
            if (each == null || StringUtils.isBlank(each.getId())) {
                continue;
            }
            if (!isSupportedRepositorySource(each.getSource())) {
                continue;
            }
            result.add(toCandidate(apiRoot, each));
            if (result.size() >= limit) {
                break;
            }
        }
        return result;
    }
    
    private boolean isSupportedRepositorySource(String repositorySource) {
        if (StringUtils.isBlank(repositorySource)) {
            return true;
        }
        String[] segments = repositorySource.split("/");
        return segments.length == 2 && StringUtils.isNotBlank(segments[0])
            && StringUtils.isNotBlank(segments[1]);
    }
    
    private AiResourceImportCandidate toCandidate(String apiRoot, SkillsShSearchItem item)
        throws NacosException {
        SkillsShSkillRef skillRef = resolveSkillRef(item);
        AiResourceImportCandidate result = new AiResourceImportCandidate();
        result.setResourceType(RESOURCE_TYPE_SKILL);
        result.setExternalId(skillRef.getExternalId());
        result.setName(skillRef.getName());
        result.setMetadata(buildCandidateMetadata(apiRoot, skillRef, item));
        return result;
    }
    
    private SkillsShSkillRef resolveSkillRef(SkillsShSearchItem item) throws NacosException {
        String externalId = item.getId();
        String repositorySource = item.getSource();
        String skillId = item.getSkillId();
        SkillsShSkillRef result = resolveSkillRef(externalId, repositorySource, skillId);
        result.setName(StringUtils.isBlank(item.getName()) ? result.getSkillId() : item.getName());
        return result;
    }
    
    private SkillsShSkillRef resolveSkillRef(AiResourceImportItem item) throws NacosException {
        if (item == null) {
            throw invalid("skills.sh import item must not be null.");
        }
        Map<String, String> metadata = item.getMetadata();
        String repositorySource =
            metadata == null ? null : metadata.get(METADATA_REPOSITORY_SOURCE);
        String skillId = metadata == null ? null : metadata.get(METADATA_SKILL_ID);
        String externalId = StringUtils.isNotBlank(item.getExternalId()) ? item.getExternalId()
            : item.getName();
        SkillsShSkillRef result = resolveSkillRef(externalId, repositorySource, skillId);
        result.setName(StringUtils.isBlank(item.getName()) ? result.getSkillId() : item.getName());
        return result;
    }
    
    private SkillsShSkillRef resolveSkillRef(String externalId, String repositorySource,
        String skillId) throws NacosException {
        if (StringUtils.isBlank(repositorySource) || StringUtils.isBlank(skillId)) {
            String[] segments =
                StringUtils.isBlank(externalId) ? new String[0] : externalId.split("/");
            if (segments.length >= 3) {
                repositorySource = segments[0] + "/" + segments[1];
                skillId = joinSegments(segments, 2);
            }
        }
        if (StringUtils.isBlank(repositorySource) || StringUtils.isBlank(skillId)) {
            throw invalid("skills.sh import item must include repository source and skill id.");
        }
        validateRepositorySource(repositorySource);
        String normalizedSkillId = normalizeSkillId(skillId);
        return new SkillsShSkillRef(repositorySource, normalizedSkillId);
    }
    
    private void validateRepositorySource(String repositorySource) throws NacosException {
        if (!isSupportedRepositorySource(repositorySource)) {
            throw invalid("skills.sh repository source must use owner/repo format.");
        }
    }
    
    private String normalizeSkillId(String skillId) throws NacosException {
        String result = skillId.trim();
        SkillUtils.validatePathSafety(result);
        if (result.startsWith(".") || result.endsWith("/") || result.contains("\\")) {
            throw invalid("skills.sh skill id is invalid: " + skillId);
        }
        return result;
    }
    
    private String joinSegments(String[] segments, int startIndex) {
        StringBuilder result = new StringBuilder();
        for (int i = startIndex; i < segments.length; i++) {
            if (result.length() > 0) {
                result.append('/');
            }
            result.append(segments[i]);
        }
        return result.toString();
    }
    
    private byte[] toSkillZip(AiResourceImportSource source, SkillsShSkillRef skillRef,
        SkillsShDownloadResponse downloadResponse) throws Exception {
        if (downloadResponse == null || CollectionUtils.isEmpty(downloadResponse.getFiles())) {
            throw invalid("skills.sh download response does not contain skill files.");
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Set<String> entryNames = new HashSet<>();
        boolean hasSkillMarkdown = false;
        long totalSize = 0;
        int fileCount = 0;
        try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            for (SkillsShFileSnapshot each : downloadResponse.getFiles()) {
                String path = normalizeFilePath(each == null ? null : each.getPath());
                if (StringUtils.isBlank(path)) {
                    continue;
                }
                if (++fileCount > resolveMaxFileCount(source)) {
                    throw invalid("skills.sh download response contains too many files.");
                }
                SkillUtils.validatePathSafety(path);
                byte[] bytes =
                    nullToEmpty(each.getContents()).getBytes(StandardCharsets.UTF_8);
                checkDownloadedSize(source, totalSize + bytes.length);
                totalSize += bytes.length;
                String entryName = skillRef.getSkillId() + "/" + path;
                SkillUtils.validatePathSafety(entryName);
                if (!entryNames.add(entryName)) {
                    continue;
                }
                hasSkillMarkdown = hasSkillMarkdown
                    || SKILL_MARKDOWN_FILE.equalsIgnoreCase(path);
                zip.putNextEntry(new ZipEntry(entryName));
                zip.write(bytes);
                zip.closeEntry();
            }
        }
        if (!hasSkillMarkdown) {
            throw invalid("skills.sh download response must contain SKILL.md.");
        }
        return output.toByteArray();
    }
    
    private int resolveMaxFileCount(AiResourceImportSource source) {
        return source.getMaxItemCount() > 0 ? source.getMaxItemCount() : DEFAULT_MAX_FILE_COUNT;
    }
    
    private String normalizeFilePath(String path) {
        if (StringUtils.isBlank(path)) {
            return null;
        }
        String result = path.trim().replace('\\', '/');
        while (result.startsWith("./")) {
            result = result.substring(2);
        }
        return result;
    }
    
    private void checkDownloadedSize(AiResourceImportSource source, long totalSize)
        throws NacosException {
        if (source.getMaxArtifactSize() > 0 && totalSize > source.getMaxArtifactSize()) {
            throw invalid("skills.sh artifact size exceeds source limit.");
        }
    }
    
    private Map<String, String> buildCandidateMetadata(String apiRoot, SkillsShSkillRef skillRef,
        SkillsShSearchItem item) {
        Map<String, String> metadata = baseMetadata(apiRoot, skillRef);
        if (item.getInstalls() != null) {
            metadata.put(METADATA_INSTALLS, String.valueOf(item.getInstalls()));
        }
        return metadata;
    }
    
    private Map<String, String> buildArtifactMetadata(String apiRoot, SkillsShSkillRef skillRef,
        SkillsShDownloadResponse downloadResponse) {
        Map<String, String> metadata = baseMetadata(apiRoot, skillRef);
        if (StringUtils.isNotBlank(downloadResponse.getHash())) {
            metadata.put(METADATA_HASH, downloadResponse.getHash());
        }
        return metadata;
    }
    
    private Map<String, String> baseMetadata(String apiRoot, SkillsShSkillRef skillRef) {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put(METADATA_SOURCE, sourcePageUrl(apiRoot, skillRef.getExternalId()));
        metadata.put(METADATA_ARTIFACT_URL, sourcePageUrl(apiRoot, skillRef.getExternalId()));
        metadata.put(METADATA_REPOSITORY, repositoryUrl(skillRef.getRepositorySource()));
        metadata.put(METADATA_REPOSITORY_SOURCE, skillRef.getRepositorySource());
        metadata.put(METADATA_SKILL_ID, skillRef.getSkillId());
        return metadata;
    }
    
    private String resolveApiRoot(AiResourceImportSource source) throws NacosException {
        String endpoint = trimTrailingSlash(source.getEndpoint());
        if (endpoint.endsWith(API_SEARCH)) {
            return endpoint.substring(0, endpoint.length() - API_SEARCH.length());
        }
        if (endpoint.endsWith(API_DOWNLOAD)) {
            return endpoint.substring(0, endpoint.length() - API_DOWNLOAD.length());
        }
        return endpoint;
    }
    
    private String searchUrl(String apiRoot, String query, int limit) {
        return apiRoot + API_SEARCH + "?q=" + encodeQueryValue(nullToEmpty(query)) + "&limit="
            + limit;
    }
    
    private String downloadUrl(String apiRoot, SkillsShSkillRef skillRef) {
        String[] repositorySegments = skillRef.getRepositorySource().split("/");
        return apiRoot + API_DOWNLOAD + "/" + encodePathSegment(repositorySegments[0]) + "/"
            + encodePathSegment(repositorySegments[1]) + "/"
            + encodePath(skillRef.getSkillId());
    }
    
    private String sourcePageUrl(String apiRoot, String externalId) {
        return apiRoot + "/" + encodePath(externalId);
    }
    
    private String repositoryUrl(String repositorySource) {
        return "https://github.com/" + repositorySource;
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
    
    private String encodeQueryValue(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
    
    private String trimTrailingSlash(String value) throws NacosException {
        if (StringUtils.isBlank(value)) {
            throw invalid("skills.sh import source endpoint must not be empty.");
        }
        String result = value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
    
    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
    
    private ImportHttpResponse fetchUrl(AiResourceImportSource source, String url)
        throws Exception {
        return httpClient.get(source, url, DEFAULT_READ_TIMEOUT_SECONDS, "application/json");
    }
    
    private NacosException invalid(String message) {
        return new NacosApiException(NacosException.INVALID_PARAM,
            ErrorCode.PARAMETER_VALIDATE_ERROR, message);
    }
    
    private NacosException dataAccess(String message, Throwable cause) {
        return new NacosApiException(NacosException.SERVER_ERROR, ErrorCode.DATA_ACCESS_ERROR,
            cause, message);
    }
    
    static class SkillsShSearchResponse {
        
        private List<SkillsShSearchItem> skills;
        
        public List<SkillsShSearchItem> getSkills() {
            return skills;
        }
        
        public void setSkills(List<SkillsShSearchItem> skills) {
            this.skills = skills;
        }
    }
    
    static class SkillsShSearchItem {
        
        private String id;
        
        private String skillId;
        
        private String name;
        
        private Integer installs;
        
        private String source;
        
        public String getId() {
            return id;
        }
        
        public void setId(String id) {
            this.id = id;
        }
        
        public String getSkillId() {
            return skillId;
        }
        
        public void setSkillId(String skillId) {
            this.skillId = skillId;
        }
        
        public String getName() {
            return name;
        }
        
        public void setName(String name) {
            this.name = name;
        }
        
        public Integer getInstalls() {
            return installs;
        }
        
        public void setInstalls(Integer installs) {
            this.installs = installs;
        }
        
        public String getSource() {
            return source;
        }
        
        public void setSource(String source) {
            this.source = source;
        }
    }
    
    static class SkillsShDownloadResponse {
        
        private List<SkillsShFileSnapshot> files;
        
        private String hash;
        
        public List<SkillsShFileSnapshot> getFiles() {
            return files;
        }
        
        public void setFiles(List<SkillsShFileSnapshot> files) {
            this.files = files;
        }
        
        public String getHash() {
            return hash;
        }
        
        public void setHash(String hash) {
            this.hash = hash;
        }
    }
    
    static class SkillsShFileSnapshot {
        
        private String path;
        
        private String contents;
        
        public String getPath() {
            return path;
        }
        
        public void setPath(String path) {
            this.path = path;
        }
        
        public String getContents() {
            return contents;
        }
        
        public void setContents(String contents) {
            this.contents = contents;
        }
    }
    
    static class SkillsShSkillRef {
        
        private final String repositorySource;
        
        private final String skillId;
        
        private String name;
        
        SkillsShSkillRef(String repositorySource, String skillId) {
            this.repositorySource = repositorySource.toLowerCase(Locale.ENGLISH);
            this.skillId = skillId;
            this.name = skillId;
        }
        
        public String getRepositorySource() {
            return repositorySource;
        }
        
        public String getSkillId() {
            return skillId;
        }
        
        public String getName() {
            return name;
        }
        
        public void setName(String name) {
            this.name = name;
        }
        
        public String getExternalId() {
            return repositorySource + "/" + skillId;
        }
    }
}
