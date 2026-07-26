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

import com.fasterxml.jackson.annotation.JsonProperty;

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
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

/**
 * Importer for Skill well-known registry endpoints.
 *
 * @author xiweng.yy
 * @since 3.2.1
 */
public class SkillWellKnownImportService implements AiResourceImportService {
    
    public static final String RESOURCE_TYPE_SKILL = AiResourceImportConstants.RESOURCE_TYPE_SKILL;
    
    private static final String WELL_KNOWN_AGENT_SKILLS = "/.well-known/agent-skills";
    
    private static final String WELL_KNOWN_SKILLS = "/.well-known/skills";
    
    private static final String INDEX_JSON = "/index.json";
    
    private static final String INDEX_JSON_FILE = "index.json";
    
    private static final String SCHEMA_0_1 =
        "https://schemas.agentskills.io/discovery/0.1.0/schema.json";
    
    private static final String SCHEMA_0_2 =
        "https://schemas.agentskills.io/discovery/0.2.0/schema.json";
    
    private static final String MARKDOWN_FILE = "SKILL.md";
    
    private static final String METADATA_FILE_COUNT = "fileCount";
    
    private static final String METADATA_SOURCE = "source";
    
    private static final String METADATA_SCHEMA_VERSION = "schemaVersion";
    
    private static final String METADATA_DISTRIBUTION_TYPE = "distributionType";
    
    private static final String METADATA_ARTIFACT_URL = "artifactUrl";
    
    private static final String METADATA_DIGEST = "digest";
    
    private static final String TYPE_SKILL_MD = "skill-md";
    
    private static final String TYPE_ARCHIVE = "archive";
    
    private static final String DIGEST_SHA256_PREFIX = "sha256:";
    
    private static final String ZIP_SUFFIX = ".zip";
    
    private static final String TAR_SUFFIX = ".tar";
    
    private static final String TAR_GZ_SUFFIX = ".tar.gz";
    
    private static final String TGZ_SUFFIX = ".tgz";
    
    private static final int DEFAULT_LIMIT = 30;
    
    private static final int DEFAULT_READ_TIMEOUT_SECONDS = 20;
    
    private static final int DEFAULT_MAX_ARCHIVE_ENTRIES = 500;
    
    private static final long DEFAULT_MAX_ARCHIVE_UNCOMPRESSED_BYTES = 50L * 1024L * 1024L;
    
    private final DefaultImportHttpClient httpClient;
    
    public SkillWellKnownImportService() {
        this(new DefaultImportHttpClient());
    }
    
    SkillWellKnownImportService(HttpClient httpClient) {
        this(new DefaultImportHttpClient(httpClient));
    }
    
    SkillWellKnownImportService(DefaultImportHttpClient httpClient) {
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
            ResolvedWellKnownIndex resolvedIndex = fetchIndex(context);
            List<WellKnownSkillEntry> matched = filterSkills(resolvedIndex.getIndex().getSkills(),
                resolvedIndex.getVersion(), context.getQuery());
            int offset = parseCursor(context.getCursor());
            int limit = resolveLimit(context.getLimit());
            int toIndex = Math.min(offset + limit, matched.size());
            List<AiResourceImportCandidate> items = new ArrayList<>();
            for (int i = offset; i < toIndex; i++) {
                items.add(toCandidate(matched.get(i), resolvedIndex));
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
            ResolvedWellKnownIndex resolvedIndex = fetchIndex(context);
            WellKnownSkillEntry entry = findSkillEntry(resolvedIndex.getIndex().getSkills(),
                skillName);
            byte[] zipBytes = fetchSkillZip(context, resolvedIndex, entry);
            AiResourceImportArtifact result = new AiResourceImportArtifact();
            result.setResourceType(RESOURCE_TYPE_SKILL);
            result.setExternalId(skillName);
            result.setName(entry.getName());
            result.setVersion(entry.getVersion());
            result.setDescription(entry.getDescription());
            result.setPayloadKind(AiResourceImportPayloadKind.SKILL_ZIP);
            result.setPayload(zipBytes);
            result.setSourceMetadata(buildMetadata(entry, resolvedIndex));
            return result;
        } catch (NacosException e) {
            throw e;
        } catch (Exception e) {
            throw dataAccess("Fetch Skill well-known artifact failed: " + e.getMessage(), e);
        }
    }
    
    private ResolvedWellKnownIndex fetchIndex(AiResourceImportContext context) throws Exception {
        AiResourceImportSource source = requireSource(context);
        List<String> indexUrls = indexUrls(source);
        Exception lastFailure = null;
        for (String each : indexUrls) {
            ImportHttpResponse response = fetchUrl(source, each);
            if (!response.isSuccess()) {
                lastFailure = new IllegalStateException(
                    "HTTP " + response.getStatusCode() + " when fetching " + each);
                continue;
            }
            return parseIndex(each, response.getBody());
        }
        throw dataAccess("Fetch Skill well-known index failed: "
            + (lastFailure == null ? "No index URL available." : lastFailure.getMessage()),
            lastFailure);
    }
    
    private ResolvedWellKnownIndex parseIndex(String indexUrl, byte[] body) throws NacosException {
        String content = new String(body, StandardCharsets.UTF_8);
        WellKnownSkillsIndex result = JacksonUtils.toObj(content, WellKnownSkillsIndex.class);
        if (result == null) {
            throw invalid("Skill well-known index cannot be parsed.");
        }
        WellKnownIndexVersion version = resolveVersion(result);
        return new ResolvedWellKnownIndex(result, version, indexUrl, wellKnownBase(indexUrl));
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
        WellKnownIndexVersion version, String query) {
        if (CollectionUtils.isEmpty(skills)) {
            return Collections.emptyList();
        }
        String normalizedQuery =
            StringUtils.isBlank(query) ? null : query.toLowerCase(Locale.ENGLISH);
        List<WellKnownSkillEntry> result = new ArrayList<>(skills.size());
        for (WellKnownSkillEntry each : skills) {
            if (!isSupportedEntry(each, version)) {
                continue;
            }
            if (normalizedQuery == null || contains(each.getName(), normalizedQuery)
                || contains(each.getDescription(), normalizedQuery)) {
                result.add(each);
            }
        }
        return result;
    }
    
    private boolean isSupportedEntry(WellKnownSkillEntry entry, WellKnownIndexVersion version) {
        if (entry == null || StringUtils.isBlank(entry.getName())) {
            return false;
        }
        if (version == WellKnownIndexVersion.V0_1_0) {
            return true;
        }
        String type = normalizeType(entry.getType());
        return TYPE_SKILL_MD.equals(type) || TYPE_ARCHIVE.equals(type);
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
    
    private AiResourceImportCandidate toCandidate(WellKnownSkillEntry entry,
        ResolvedWellKnownIndex resolvedIndex) {
        AiResourceImportCandidate result = new AiResourceImportCandidate();
        result.setResourceType(RESOURCE_TYPE_SKILL);
        result.setExternalId(entry.getName());
        result.setName(entry.getName());
        result.setDescription(entry.getDescription());
        result.setMetadata(buildMetadata(entry, resolvedIndex));
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
    
    private byte[] fetchSkillZip(AiResourceImportContext context,
        ResolvedWellKnownIndex resolvedIndex, WellKnownSkillEntry entry) throws Exception {
        if (resolvedIndex.getVersion() == WellKnownIndexVersion.V0_2_0) {
            return fetchVersion020SkillZip(context, resolvedIndex, entry);
        }
        return fetchVersion010SkillZip(context, resolvedIndex, entry);
    }
    
    private byte[] fetchVersion010SkillZip(AiResourceImportContext context,
        ResolvedWellKnownIndex resolvedIndex, WellKnownSkillEntry entry) throws Exception {
        String base = resolvedIndex.getWellKnownBase();
        AiResourceImportSource source = requireSource(context);
        List<String> files = normalizeFiles(entry.getFiles());
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            for (String each : files) {
                SkillUtils.validatePathSafety(each);
                byte[] bytes = fetchBytes(source, fileUrl(base, entry.getName(), each));
                checkDownloadedSize(source, bytes);
                zip.putNextEntry(new ZipEntry(entry.getName() + "/" + each));
                zip.write(bytes);
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }
    
    private byte[] fetchVersion020SkillZip(AiResourceImportContext context,
        ResolvedWellKnownIndex resolvedIndex, WellKnownSkillEntry entry) throws Exception {
        String type = normalizeType(entry.getType());
        ImportHttpResponse artifact = fetchVersion020Artifact(context, resolvedIndex, entry);
        if (TYPE_SKILL_MD.equals(type)) {
            return toSingleMarkdownSkillZip(entry.getName(), artifact.getBody());
        }
        if (TYPE_ARCHIVE.equals(type)) {
            return toSkillZipFromArchive(artifact);
        }
        throw invalid("Unsupported Skill well-known distribution type: " + entry.getType());
    }
    
    private ImportHttpResponse fetchVersion020Artifact(AiResourceImportContext context,
        ResolvedWellKnownIndex resolvedIndex, WellKnownSkillEntry entry) throws Exception {
        if (StringUtils.isBlank(entry.getUrl())) {
            throw invalid("Skill well-known 0.2.0 entry url must not be empty.");
        }
        AiResourceImportSource source = requireSource(context);
        ImportHttpResponse result = fetchUrl(source,
            resolveArtifactUrl(resolvedIndex.getIndexUrl(), entry.getUrl()));
        if (!result.isSuccess()) {
            throw new IllegalStateException(
                "HTTP " + result.getStatusCode() + " when fetching " + result.getUrl());
        }
        checkDownloadedSize(source, result.getBody());
        verifySha256Digest(entry.getDigest(), result.getBody());
        return result;
    }
    
    private byte[] toSingleMarkdownSkillZip(String skillName, byte[] markdown) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry(skillName + "/" + MARKDOWN_FILE));
            zip.write(markdown);
            zip.closeEntry();
        }
        return output.toByteArray();
    }
    
    private byte[] toSkillZipFromArchive(ImportHttpResponse artifact) throws Exception {
        ArchiveFormat format = resolveArchiveFormat(artifact);
        if (format == ArchiveFormat.ZIP) {
            return artifact.getBody();
        }
        return convertTarToZip(artifact.getBody(), format == ArchiveFormat.TAR_GZ);
    }
    
    private byte[] convertTarToZip(byte[] bytes, boolean gzip) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Set<String> entryNames = new HashSet<>();
        try (TarArchiveInputStream tar = new TarArchiveInputStream(
            gzip ? new GzipCompressorInputStream(new ByteArrayInputStream(bytes))
                : new ByteArrayInputStream(bytes));
            ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            TarArchiveEntry entry;
            byte[] buffer = new byte[8192];
            int entryCount = 0;
            long totalSize = 0;
            while ((entry = tar.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = normalizeArchiveEntryName(entry.getName());
                if (StringUtils.isBlank(name)) {
                    continue;
                }
                SkillUtils.validatePathSafety(name);
                if (!entryNames.add(name)) {
                    continue;
                }
                if (++entryCount > DEFAULT_MAX_ARCHIVE_ENTRIES) {
                    throw invalid("Skill well-known archive contains too many entries.");
                }
                zip.putNextEntry(new ZipEntry(name));
                int n;
                while ((n = tar.read(buffer)) != -1) {
                    totalSize += n;
                    if (totalSize > DEFAULT_MAX_ARCHIVE_UNCOMPRESSED_BYTES) {
                        throw invalid("Skill well-known archive decompressed size exceeds limit.");
                    }
                    zip.write(buffer, 0, n);
                }
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
    
    private Map<String, String> buildMetadata(WellKnownSkillEntry entry,
        ResolvedWellKnownIndex resolvedIndex) {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put(METADATA_SCHEMA_VERSION, resolvedIndex.getVersion().getVersion());
        metadata.put(METADATA_SOURCE, resolvedIndex.getWellKnownBase());
        if (resolvedIndex.getVersion() == WellKnownIndexVersion.V0_1_0) {
            metadata.put(METADATA_FILE_COUNT,
                String.valueOf(normalizeFiles(entry.getFiles()).size()));
            return metadata;
        }
        metadata.put(METADATA_DISTRIBUTION_TYPE, normalizeType(entry.getType()));
        if (StringUtils.isNotBlank(entry.getUrl())) {
            metadata.put(METADATA_ARTIFACT_URL,
                resolveArtifactUrl(resolvedIndex.getIndexUrl(), entry.getUrl()));
        }
        if (StringUtils.isNotBlank(entry.getDigest())) {
            metadata.put(METADATA_DIGEST, entry.getDigest());
        }
        return metadata;
    }
    
    private List<String> indexUrls(AiResourceImportSource source) throws NacosException {
        String endpoint = trimTrailingSlash(source.getEndpoint());
        if (endpoint.endsWith(INDEX_JSON)) {
            return Collections.singletonList(endpoint);
        }
        if (isWellKnownBase(endpoint)) {
            return Collections.singletonList(endpoint + INDEX_JSON);
        }
        List<String> result = new ArrayList<>(2);
        result.add(endpoint + WELL_KNOWN_AGENT_SKILLS + INDEX_JSON);
        result.add(endpoint + WELL_KNOWN_SKILLS + INDEX_JSON);
        return result;
    }
    
    private boolean isWellKnownBase(String endpoint) {
        return endpoint.endsWith(WELL_KNOWN_AGENT_SKILLS)
            || endpoint.endsWith(WELL_KNOWN_SKILLS);
    }
    
    private String wellKnownBase(String indexUrl) {
        String normalized = trimTrailingSlashUnchecked(indexUrl);
        if (normalized.endsWith(INDEX_JSON)) {
            return normalized.substring(0, normalized.length() - INDEX_JSON.length());
        }
        if (normalized.endsWith(INDEX_JSON_FILE)) {
            return normalized.substring(0, normalized.length() - INDEX_JSON_FILE.length() - 1);
        }
        return normalized;
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
        return trimTrailingSlashUnchecked(value);
    }
    
    private String trimTrailingSlashUnchecked(String value) {
        String result = value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
    
    private byte[] fetchBytes(AiResourceImportSource source, String url) throws Exception {
        ImportHttpResponse result = fetchUrl(source, url);
        if (!result.isSuccess()) {
            throw new IllegalStateException(
                "HTTP " + result.getStatusCode() + " when fetching " + url);
        }
        return result.getBody();
    }
    
    private ImportHttpResponse fetchUrl(AiResourceImportSource source, String url)
        throws Exception {
        return httpClient.get(source, url, DEFAULT_READ_TIMEOUT_SECONDS, "*/*");
    }
    
    private WellKnownIndexVersion resolveVersion(WellKnownSkillsIndex index)
        throws NacosException {
        String schema = index.getSchema();
        if (StringUtils.isBlank(schema) || StringUtils.equals(schema, SCHEMA_0_1)
            || schema.contains("/0.1.0/")) {
            return WellKnownIndexVersion.V0_1_0;
        }
        if (StringUtils.equals(schema, SCHEMA_0_2) || schema.contains("/0.2.0/")) {
            return WellKnownIndexVersion.V0_2_0;
        }
        throw invalid("Unsupported Skill well-known schema: " + schema);
    }
    
    private String normalizeType(String type) {
        return StringUtils.isBlank(type) ? "" : type.trim().toLowerCase(Locale.ENGLISH);
    }
    
    private String resolveArtifactUrl(String indexUrl, String artifactUrl) {
        return URI.create(indexUrl).resolve(artifactUrl).toString();
    }
    
    private void checkDownloadedSize(AiResourceImportSource source, byte[] bytes)
        throws NacosException {
        if (source.getMaxArtifactSize() > 0 && bytes != null
            && bytes.length > source.getMaxArtifactSize()) {
            throw invalid("Skill well-known artifact size exceeds source limit.");
        }
    }
    
    private void verifySha256Digest(String digest, byte[] bytes) throws Exception {
        if (StringUtils.isBlank(digest)) {
            throw invalid("Skill well-known 0.2.0 entry digest must not be empty.");
        }
        String normalizedDigest = digest.trim().toLowerCase(Locale.ENGLISH);
        if (!normalizedDigest.startsWith(DIGEST_SHA256_PREFIX)) {
            throw invalid("Skill well-known digest must use sha256.");
        }
        String expected = normalizedDigest.substring(DIGEST_SHA256_PREFIX.length());
        String actual = sha256Hex(bytes);
        if (!StringUtils.equals(expected, actual)) {
            throw invalid("Skill well-known artifact digest mismatch.");
        }
    }
    
    private String sha256Hex(byte[] bytes) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(bytes);
        StringBuilder result = new StringBuilder(hash.length * 2);
        for (byte each : hash) {
            result.append(String.format("%02x", each & 0xff));
        }
        return result.toString();
    }
    
    private ArchiveFormat resolveArchiveFormat(ImportHttpResponse artifact) throws NacosException {
        String contentType = artifact.getContentType().toLowerCase(Locale.ENGLISH);
        String url = artifact.getUrl().toLowerCase(Locale.ENGLISH);
        if (contentType.contains("gzip") || url.endsWith(TAR_GZ_SUFFIX)
            || url.endsWith(TGZ_SUFFIX)) {
            return ArchiveFormat.TAR_GZ;
        }
        if (contentType.contains("zip") || url.endsWith(ZIP_SUFFIX)) {
            return ArchiveFormat.ZIP;
        }
        if (contentType.contains("tar") || url.endsWith(TAR_SUFFIX)) {
            return ArchiveFormat.TAR;
        }
        throw invalid("Unsupported Skill well-known archive content type: "
            + artifact.getContentType());
    }
    
    private String normalizeArchiveEntryName(String name) {
        if (StringUtils.isBlank(name)) {
            return null;
        }
        String result = name.trim().replace('\\', '/');
        while (result.startsWith("./")) {
            result = result.substring(2);
        }
        return result;
    }
    
    private NacosException invalid(String message) {
        return new NacosApiException(NacosException.INVALID_PARAM,
            ErrorCode.PARAMETER_VALIDATE_ERROR, message);
    }
    
    private NacosException dataAccess(String message, Throwable cause) {
        return new NacosApiException(NacosException.SERVER_ERROR, ErrorCode.DATA_ACCESS_ERROR,
            cause, message);
    }
    
    private enum WellKnownIndexVersion {
        
        V0_1_0("0.1.0"),
        
        V0_2_0("0.2.0");
        
        private final String version;
        
        WellKnownIndexVersion(String version) {
            this.version = version;
        }
        
        public String getVersion() {
            return version;
        }
    }
    
    private enum ArchiveFormat {
        
        ZIP,
        
        TAR,
        
        TAR_GZ
    }
    
    private static class ResolvedWellKnownIndex {
        
        private final WellKnownSkillsIndex index;
        
        private final WellKnownIndexVersion version;
        
        private final String indexUrl;
        
        private final String wellKnownBase;
        
        ResolvedWellKnownIndex(WellKnownSkillsIndex index, WellKnownIndexVersion version,
            String indexUrl, String wellKnownBase) {
            this.index = index;
            this.version = version;
            this.indexUrl = indexUrl;
            this.wellKnownBase = wellKnownBase;
        }
        
        public WellKnownSkillsIndex getIndex() {
            return index;
        }
        
        public WellKnownIndexVersion getVersion() {
            return version;
        }
        
        public String getIndexUrl() {
            return indexUrl;
        }
        
        public String getWellKnownBase() {
            return wellKnownBase;
        }
    }
    
    static class WellKnownSkillsIndex {
        
        @JsonProperty("$schema")
        private String schema;
        
        private List<WellKnownSkillEntry> skills;
        
        public String getSchema() {
            return schema;
        }
        
        public void setSchema(String schema) {
            this.schema = schema;
        }
        
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
        
        private String type;
        
        private String url;
        
        private String digest;
        
        private String version;
        
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
        
        public String getType() {
            return type;
        }
        
        public void setType(String type) {
            this.type = type;
        }
        
        public String getUrl() {
            return url;
        }
        
        public void setUrl(String url) {
            this.url = url;
        }
        
        public String getDigest() {
            return digest;
        }
        
        public void setDigest(String digest) {
            this.digest = digest;
        }
        
        public String getVersion() {
            return version;
        }
        
        public void setVersion(String version) {
            this.version = version;
        }
    }
}
