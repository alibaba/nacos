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
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpec;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpecResource;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpecUtils;
import com.alibaba.nacos.api.ai.model.skills.SkillUtils;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * AgentSpec zip parser utility. Mirrors {@link SkillZipParser} for HiClaw Worker packages.
 * Parses zip files containing manifest.json as the main metadata and additional resource files.
 * Text files are stored as UTF-8; binary files (e.g. .ttf, .png) are stored as Base64 with metadata encoding=base64.
 *
 * @author nacos
 */
public class AgentSpecZipParser {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(AgentSpecZipParser.class);
    
    private static final String MANIFEST_JSON = "manifest.json";
    
    /** macOS AppleDouble/resource fork metadata file prefix (e.g. ._LICENSE.txt). */
    private static final String MACOS_METADATA_PREFIX = "._";
    
    private static final String DS_STORE = ".DS_Store";
    
    private static final String SLASH = "/";
    
    /**
     * Default maximum compressed (upload) size in MB for an AgentSpec ZIP. Derived from the
     * historical {@link Constants.AgentSpecs#MAX_UPLOAD_ZIP_BYTES} so the public constant remains
     * the single source of truth; runtime callers should consult {@link #resolveMaxUploadBytes()}
     * which honors the {@value #CONFIG_MAX_UPLOAD_SIZE_MB} override.
     */
    static final int DEFAULT_MAX_UPLOAD_SIZE_MB =
        (int) (Constants.AgentSpecs.MAX_UPLOAD_ZIP_BYTES / 1024L / 1024L);
    
    /**
     * Default maximum number of entries allowed in an AgentSpec ZIP. Overridable via the
     * {@value #CONFIG_MAX_ZIP_ENTRIES} property when users legitimately upload larger specs.
     */
    static final int DEFAULT_MAX_ZIP_ENTRIES = 500;
    
    /**
     * Default maximum total decompressed size (in MB) for an AgentSpec ZIP. Prevents Zip Bomb
     * attacks while still permitting legitimate uploads. Overridable via the
     * {@value #CONFIG_MAX_UNCOMPRESSED_SIZE_MB} property.
     */
    static final int DEFAULT_MAX_UNCOMPRESSED_SIZE_MB = 50;
    
    static final int DEFAULT_MAX_SEED_ARCHIVE_ENTRIES = 2048;
    
    static final long DEFAULT_MAX_SEED_UNCOMPRESSED_BYTES = 50L * 1024L * 1024L;
    
    /**
     * Property key for overriding {@link #DEFAULT_MAX_UPLOAD_SIZE_MB}. The value is in megabytes
     * and applies to the raw compressed AgentSpec ZIP before parsing. Non-positive values are
     * ignored.
     */
    static final String CONFIG_MAX_UPLOAD_SIZE_MB =
        "nacos.ai.agentspec.zip.max-upload-size-mb";
    
    /**
     * Property key for overriding {@link #DEFAULT_MAX_ZIP_ENTRIES}. Non-positive values are ignored.
     */
    static final String CONFIG_MAX_ZIP_ENTRIES = "nacos.ai.agentspec.zip.max-entries";
    
    /**
     * Property key for overriding {@link #DEFAULT_MAX_UNCOMPRESSED_SIZE_MB}. The value is in
     * megabytes. Non-positive values are ignored.
     */
    static final String CONFIG_MAX_UNCOMPRESSED_SIZE_MB =
        "nacos.ai.agentspec.zip.max-uncompressed-size-mb";
    
    /**
     * Parse AgentSpec from zip file bytes. Zip size must not exceed the limit returned by
     * {@link #resolveMaxUploadBytes()} (configurable via {@value #CONFIG_MAX_UPLOAD_SIZE_MB}).
     * Looks for manifest.json as the main metadata, extracts worker.suggested_name as the AgentSpec name.
     * Other entries become AgentSpecResource instances. Binary files are Base64 encoded.
     * macOS metadata files (__MACOSX/*, .DS_Store, ._*) are filtered out.
     *
     * @param zipBytes    zip file bytes
     * @param namespaceId namespace ID
     * @return parsed AgentSpec
     * @throws NacosApiException if parsing failed, zip exceeds size limit, manifest.json missing, or suggested_name empty
     */
    public static AgentSpec parseAgentSpecFromZip(byte[] zipBytes, String namespaceId)
        throws NacosApiException {
        validateZipBytes(zipBytes);
        try {
            List<ZipEntryData> entries = unzipToEntries(zipBytes);
            ZipEntryData manifestEntry = findFirstManifestEntry(entries);
            if (manifestEntry == null) {
                throw manifestNotFoundException();
            }
            return parseAgentSpec(entries, manifestEntry, namespaceId);
        } catch (NacosApiException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("Failed to parse AgentSpec zip file", e);
            throw new NacosApiException(NacosApiException.INVALID_PARAM,
                ErrorCode.PARSING_DATA_FAILED,
                "Failed to parse zip file: " + e.getMessage());
        }
    }
    
    /**
     * Parse one or more AgentSpecs from a ZIP archive. A regular AgentSpec ZIP produces one
     * result; an archive containing multiple directories with {@code manifest.json} produces one
     * result per directory.
     *
     * @param zipBytes zip file bytes
     * @param namespaceId namespace ID
     * @return parsed AgentSpecs
     * @throws NacosApiException if parsing failed or the archive violates a security limit
     */
    public static List<AgentSpec> parseMultipleAgentSpecsFromZip(byte[] zipBytes,
        String namespaceId) throws NacosApiException {
        validateZipBytes(zipBytes);
        try {
            List<ZipEntryData> entries = unzipToEntries(zipBytes);
            List<ZipEntryData> manifestEntries = findManifestEntries(entries);
            if (manifestEntries.isEmpty()) {
                throw manifestNotFoundException();
            }
            manifestEntries.sort((left, right) -> left.name.compareTo(right.name));
            Set<String> seenNames = new HashSet<>();
            List<AgentSpec> result = new ArrayList<>(manifestEntries.size());
            for (ZipEntryData manifestEntry : manifestEntries) {
                AgentSpec agentSpec = parseAgentSpec(entries, manifestEntry, namespaceId);
                if (!seenNames.add(agentSpec.getName())) {
                    LOGGER.warn("Skip duplicate agentspec name `{}` from archive path `{}`",
                        agentSpec.getName(), getManifestPrefix(manifestEntry.name));
                    continue;
                }
                result.add(agentSpec);
            }
            return result;
        } catch (NacosApiException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("Failed to parse multi-AgentSpec zip file", e);
            throw new NacosApiException(NacosApiException.INVALID_PARAM,
                ErrorCode.PARSING_DATA_FAILED,
                "Failed to parse zip file: " + e.getMessage());
        }
    }
    
    /**
     * Parse the bundled seed archive into standalone AgentSpec ZIP packages.
     *
     * @param inputStream bundled archive input stream
     * @return standalone AgentSpec packages
     * @throws IOException if the archive cannot be read or violates a security limit
     */
    public static List<AgentSpecPackage> parseAgentSpecPackagesFromZip(InputStream inputStream)
        throws IOException {
        int maxEntries = Math.max(DEFAULT_MAX_SEED_ARCHIVE_ENTRIES, resolveMaxZipEntries());
        long maxUncompressedBytes = Math.max(DEFAULT_MAX_SEED_UNCOMPRESSED_BYTES,
            resolveMaxUncompressedBytes());
        return parseAgentSpecPackagesFromZip(inputStream, maxEntries, maxUncompressedBytes);
    }
    
    static List<AgentSpecPackage> parseAgentSpecPackagesFromZip(InputStream inputStream,
        int maxEntries, long maxUncompressedBytes) throws IOException {
        Map<String, byte[]> entries =
            readSeedArchiveEntries(inputStream, maxEntries, maxUncompressedBytes);
        if (entries.isEmpty()) {
            return Collections.emptyList();
        }
        
        Set<String> roots = detectAgentSpecRoots(entries.keySet());
        if (roots.isEmpty()) {
            return Collections.emptyList();
        }
        
        Set<String> seenNames = new HashSet<>();
        List<AgentSpecPackage> result = new ArrayList<>(roots.size());
        for (String root : roots) {
            String manifestPath = buildRootPath(root, MANIFEST_JSON);
            byte[] manifestBytes = entries.get(manifestPath);
            if (manifestBytes == null) {
                continue;
            }
            String agentSpecName = extractSeedSuggestedName(manifestBytes);
            if (StringUtils.isBlank(agentSpecName)) {
                throw new IOException("Missing worker.suggested_name in " + manifestPath);
            }
            if (!seenNames.add(agentSpecName)) {
                LOGGER.warn("Skip duplicate built-in agentspec name `{}` from archive path `{}`",
                    agentSpecName, root);
                continue;
            }
            result.add(new AgentSpecPackage(agentSpecName, extractSeedFrom(root), root,
                buildStandaloneAgentSpecZip(entries, root)));
        }
        return result;
    }
    
    private static Map<String, byte[]> readSeedArchiveEntries(InputStream inputStream,
        int maxEntries, long maxUncompressedBytes) throws IOException {
        Map<String, byte[]> result = new LinkedHashMap<>();
        Set<String> seenEntryNames = new HashSet<>();
        int entryCount = 0;
        long totalSize = 0;
        try (ZipArchiveInputStream zis =
            new ZipArchiveInputStream(inputStream, StandardCharsets.UTF_8.name(), true, true)) {
            ZipArchiveEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zis.getNextEntry()) != null) {
                entryCount++;
                if (entryCount > maxEntries) {
                    throw new IOException(
                        "ZIP file contains too many entries (max " + maxEntries + ")");
                }
                String entryName = normalizeEntryName(entry.getName());
                if (StringUtils.isNotBlank(entryName)) {
                    SkillUtils.validatePathSafety(entryName);
                    if (!seenEntryNames.add(entryName)) {
                        throw new IOException(
                            "ZIP file contains duplicate entry path: " + entryName);
                    }
                }
                boolean shouldStore = !entry.isDirectory() && StringUtils.isNotBlank(entryName);
                ByteArrayOutputStream out = shouldStore ? new ByteArrayOutputStream() : null;
                int bytesRead;
                while ((bytesRead = zis.read(buffer)) != -1) {
                    totalSize += bytesRead;
                    if (totalSize > maxUncompressedBytes) {
                        throw new IOException("ZIP decompressed size exceeds limit ("
                            + (maxUncompressedBytes / 1024 / 1024) + "MB)");
                    }
                    if (out != null) {
                        out.write(buffer, 0, bytesRead);
                    }
                }
                if (out != null) {
                    result.put(entryName, out.toByteArray());
                }
            }
        }
        return result;
    }
    
    private static Set<String> detectAgentSpecRoots(Set<String> entryNames) {
        Set<String> result = new TreeSet<>();
        for (String entryName : entryNames) {
            if (MANIFEST_JSON.equals(entryName)) {
                result.add("");
                continue;
            }
            if (entryName.endsWith(SLASH + MANIFEST_JSON)) {
                result.add(entryName.substring(0, entryName.length() - MANIFEST_JSON.length() - 1));
            }
        }
        return result;
    }
    
    @SuppressWarnings("unchecked")
    private static String extractSeedSuggestedName(byte[] manifestBytes) {
        String manifestContent = new String(manifestBytes, StandardCharsets.UTF_8);
        Map<String, Object> root;
        try {
            root = JacksonUtils.toObj(manifestContent, Map.class);
        } catch (Exception e) {
            return null;
        }
        Object workerObj = root.get("worker");
        if (!(workerObj instanceof Map)) {
            return null;
        }
        Map<String, Object> workerMap = (Map<String, Object>) workerObj;
        Object nameObj = workerMap.get("suggested_name");
        if (nameObj == null) {
            return null;
        }
        String suggestedName = nameObj.toString();
        return StringUtils.isBlank(suggestedName) ? null : suggestedName.trim();
    }
    
    private static byte[] buildStandaloneAgentSpecZip(Map<String, byte[]> entries, String root)
        throws IOException {
        List<String> paths = new ArrayList<>();
        for (String entryName : entries.keySet()) {
            if (isInSeedRoot(entryName, root)) {
                paths.add(entryName);
            }
        }
        Collections.sort(paths);
        
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            for (String path : paths) {
                String relativePath = root.isEmpty() ? path : path.substring(root.length() + 1);
                if (StringUtils.isBlank(relativePath)) {
                    continue;
                }
                ZipEntry zipEntry = new ZipEntry(relativePath);
                zos.putNextEntry(zipEntry);
                zos.write(entries.get(path));
                zos.closeEntry();
            }
        }
        return out.toByteArray();
    }
    
    private static boolean isInSeedRoot(String entryName, String root) {
        return root.isEmpty() || entryName.startsWith(root + SLASH);
    }
    
    private static String buildRootPath(String root, String fileName) {
        return root.isEmpty() ? fileName : root + SLASH + fileName;
    }
    
    private static String extractSeedFrom(String sourcePath) {
        if (StringUtils.isBlank(sourcePath)) {
            return null;
        }
        String normalized = sourcePath;
        while (normalized.endsWith(SLASH)) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        int idx = normalized.lastIndexOf('/');
        if (idx <= 0) {
            return null;
        }
        String from = normalized.substring(0, idx).trim();
        return StringUtils.isBlank(from) ? null : from;
    }
    
    private static void validateZipBytes(byte[] zipBytes) throws NacosApiException {
        if (zipBytes == null || zipBytes.length == 0) {
            throw new NacosApiException(NacosApiException.INVALID_PARAM,
                ErrorCode.PARAMETER_VALIDATE_ERROR,
                "AgentSpec zip file is empty");
        }
        long maxUploadBytes = resolveMaxUploadBytes();
        if (zipBytes.length > maxUploadBytes) {
            throw new NacosApiException(NacosApiException.INVALID_PARAM,
                ErrorCode.PARAMETER_VALIDATE_ERROR,
                "AgentSpec zip size must not exceed "
                    + (maxUploadBytes / 1024 / 1024)
                    + "MB, current: " + (zipBytes.length / 1024 / 1024) + "MB");
        }
    }
    
    private static ZipEntryData findFirstManifestEntry(List<ZipEntryData> entries) {
        for (ZipEntryData entry : entries) {
            if (isManifestEntry(entry.name)) {
                return entry;
            }
        }
        return null;
    }
    
    private static List<ZipEntryData> findManifestEntries(List<ZipEntryData> entries) {
        List<ZipEntryData> result = new ArrayList<>();
        for (ZipEntryData entry : entries) {
            if (isManifestEntry(entry.name)) {
                result.add(entry);
            }
        }
        return result;
    }
    
    private static boolean isManifestEntry(String name) {
        return MANIFEST_JSON.equals(name)
            || (name != null && name.endsWith(SLASH + MANIFEST_JSON));
    }
    
    private static AgentSpec parseAgentSpec(List<ZipEntryData> entries,
        ZipEntryData manifestEntry, String namespaceId) throws NacosApiException {
        String manifestContent = new String(manifestEntry.data, StandardCharsets.UTF_8);
        if (StringUtils.isBlank(manifestContent)) {
            throw manifestNotFoundException();
        }
        AgentSpec agentSpec = parseManifest(manifestContent, namespaceId);
        List<ZipEntryData> resourceEntries =
            filterEntriesByPrefix(entries, getManifestPrefix(manifestEntry.name));
        Map<String, AgentSpecResource> resources =
            parseResources(resourceEntries, agentSpec.getName());
        agentSpec.setResource(resources);
        return agentSpec;
    }
    
    private static NacosApiException manifestNotFoundException() {
        return new NacosApiException(NacosApiException.INVALID_PARAM,
            ErrorCode.PARAMETER_VALIDATE_ERROR, "manifest.json file not found in zip");
    }
    
    /**
     * Unzip to list of (name, raw bytes). Does not decode as text so binary files are preserved.
     * Uses Apache Commons Compress to support zip files with STORED entries that have data descriptor.
     *
     * <p>Security hardening (mirrors {@link SkillZipParser#unzipToEntries(byte[])}):
     * <ul>
     *   <li>Rejects entries with path traversal sequences (..) or absolute paths via
     *       {@link SkillUtils#validatePathSafety(String)}</li>
     *   <li>Enforces maximum total decompressed size (configurable via
     *       {@value #CONFIG_MAX_UNCOMPRESSED_SIZE_MB}, default
     *       {@link #DEFAULT_MAX_UNCOMPRESSED_SIZE_MB} MB) to prevent Zip Bomb attacks</li>
     *   <li>Enforces maximum number of entries (configurable via
     *       {@value #CONFIG_MAX_ZIP_ENTRIES}, default {@link #DEFAULT_MAX_ZIP_ENTRIES})
     *       to prevent entry-count flooding attacks</li>
     * </ul>
     *
     * <p>Security-limit violations are reported as {@link NacosRuntimeException} (not {@link IOException})
     * because they represent invalid user input rather than an underlying I/O failure. The caller
     * {@link #parseAgentSpecFromZip(byte[], String)} and
     * {@link #parseMultipleAgentSpecsFromZip(byte[], String)} translate them into a
     * {@link NacosApiException} for the HTTP layer.
     */
    private static List<ZipEntryData> unzipToEntries(byte[] zipBytes) throws IOException {
        final int maxEntries = resolveMaxZipEntries();
        final long maxUncompressedBytes = resolveMaxUncompressedBytes();
        List<ZipEntryData> result = new ArrayList<>();
        Set<String> seenEntryNames = new HashSet<>();
        int entryCount = 0;
        long totalSize = 0;
        try (ZipArchiveInputStream zis =
            new ZipArchiveInputStream(new ByteArrayInputStream(zipBytes),
                StandardCharsets.UTF_8.name(), true, true)) {
            ZipArchiveEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zis.getNextEntry()) != null) {
                entryCount++;
                if (entryCount > maxEntries) {
                    throw new NacosRuntimeException(ErrorCode.PARAMETER_VALIDATE_ERROR.getCode(),
                        "ZIP file contains too many entries (max " + maxEntries + ")");
                }
                String normalizedName = normalizeEntryName(entry.getName());
                SkillUtils.validatePathSafety(normalizedName);
                if (StringUtils.isNotBlank(normalizedName)
                    && !seenEntryNames.add(normalizedName)) {
                    throw new NacosRuntimeException(ErrorCode.PARAMETER_VALIDATE_ERROR.getCode(),
                        "ZIP file contains duplicate entry path: " + normalizedName);
                }
                boolean shouldStore = !entry.isDirectory()
                    && StringUtils.isNotBlank(normalizedName)
                    && !isMacOsMetadataFile(normalizedName);
                ByteArrayOutputStream out = shouldStore ? new ByteArrayOutputStream() : null;
                int n;
                while ((n = zis.read(buffer)) != -1) {
                    totalSize += n;
                    if (totalSize > maxUncompressedBytes) {
                        throw new NacosRuntimeException(
                            ErrorCode.PARAMETER_VALIDATE_ERROR.getCode(),
                            "ZIP decompressed size exceeds limit ("
                                + (maxUncompressedBytes / 1024 / 1024) + "MB)");
                    }
                    if (out != null) {
                        out.write(buffer, 0, n);
                    }
                }
                if (out != null) {
                    result.add(new ZipEntryData(normalizedName, out.toByteArray()));
                }
            }
        }
        return result;
    }
    
    private static String normalizeEntryName(String entryName) {
        if (entryName == null) {
            return null;
        }
        String result = entryName.replace('\\', '/');
        while (result.startsWith("./")) {
            result = result.substring(2);
        }
        return result;
    }
    
    /**
     * Resolve the maximum compressed (upload) size in bytes, honoring the
     * {@value #CONFIG_MAX_UPLOAD_SIZE_MB} override (interpreted in megabytes) when present and
     * positive. Returns {@link #DEFAULT_MAX_UPLOAD_SIZE_MB} MB otherwise. Keep this in sync with
     * the Spring multipart cap; the multipart filter rejects oversize uploads first.
     */
    static long resolveMaxUploadBytes() {
        int mb = resolvePositiveIntProperty(CONFIG_MAX_UPLOAD_SIZE_MB, DEFAULT_MAX_UPLOAD_SIZE_MB);
        return (long) mb * 1024L * 1024L;
    }
    
    /**
     * Resolve the maximum number of ZIP entries allowed, honoring the
     * {@value #CONFIG_MAX_ZIP_ENTRIES} override when present and positive.
     * Returns {@link #DEFAULT_MAX_ZIP_ENTRIES} when no override is configured or when the
     * Nacos environment has not been initialized (e.g. in unit tests that bypass Spring boot-up).
     */
    static int resolveMaxZipEntries() {
        return resolvePositiveIntProperty(CONFIG_MAX_ZIP_ENTRIES, DEFAULT_MAX_ZIP_ENTRIES);
    }
    
    /**
     * Resolve the maximum total decompressed size in bytes, honoring the
     * {@value #CONFIG_MAX_UNCOMPRESSED_SIZE_MB} override (interpreted in megabytes) when present
     * and positive. Returns {@link #DEFAULT_MAX_UNCOMPRESSED_SIZE_MB} MB otherwise.
     */
    static long resolveMaxUncompressedBytes() {
        int mb = resolvePositiveIntProperty(
            CONFIG_MAX_UNCOMPRESSED_SIZE_MB, DEFAULT_MAX_UNCOMPRESSED_SIZE_MB);
        return (long) mb * 1024L * 1024L;
    }
    
    /**
     * Read an int-valued property from {@link EnvUtil}, returning {@code defaultValue} whenever
     * the override is missing, non-positive, or the environment has not yet been initialized.
     * Non-positive overrides are deliberately rejected so misconfiguration cannot silently
     * disable the underlying security guards.
     */
    private static int resolvePositiveIntProperty(String key, int defaultValue) {
        if (EnvUtil.getEnvironment() == null) {
            return defaultValue;
        }
        Integer configured = EnvUtil.getProperty(key, Integer.class);
        return configured != null && configured > 0 ? configured : defaultValue;
    }
    
    /**
     * Parse manifest.json content to extract AgentSpec metadata.
     * Extracts worker.suggested_name as the AgentSpec name.
     */
    @SuppressWarnings("unchecked")
    private static AgentSpec parseManifest(String manifestContent, String namespaceId)
        throws NacosApiException {
        Map<String, Object> root;
        try {
            root = JacksonUtils.toObj(manifestContent, Map.class);
        } catch (Exception e) {
            throw new NacosApiException(NacosApiException.INVALID_PARAM,
                ErrorCode.PARAMETER_VALIDATE_ERROR,
                "manifest.json is not valid JSON: " + e.getMessage());
        }
        
        String suggestedName = null;
        Object workerObj = root.get("worker");
        if (workerObj instanceof Map) {
            Map<String, Object> workerMap = (Map<String, Object>) workerObj;
            Object nameObj = workerMap.get("suggested_name");
            if (nameObj != null) {
                suggestedName = nameObj.toString();
            }
        }
        
        if (StringUtils.isBlank(suggestedName)) {
            throw new NacosApiException(NacosApiException.INVALID_PARAM,
                ErrorCode.PARAMETER_MISSING,
                "worker.suggested_name is required in manifest.json");
        }
        
        AgentSpec agentSpec = new AgentSpec();
        agentSpec.setNamespaceId(namespaceId);
        agentSpec.setName(suggestedName.trim());
        Object descriptionObj = root.get("description");
        if (descriptionObj != null && StringUtils.isNotBlank(descriptionObj.toString())) {
            agentSpec.setDescription(descriptionObj.toString().trim());
        }
        String bizTags = parseBizTags(root.get("tags"));
        if (StringUtils.isBlank(bizTags)) {
            bizTags = parseBizTags(root.get("bizTags"));
        }
        if (StringUtils.isNotBlank(bizTags)) {
            agentSpec.setBizTags(bizTags);
        }
        agentSpec.setContent(manifestContent);
        
        return agentSpec;
    }
    
    private static String parseBizTags(Object bizTagsObj) {
        if (bizTagsObj instanceof List) {
            List<?> bizTags = (List<?>) bizTagsObj;
            List<String> normalized = new ArrayList<>(bizTags.size());
            for (Object each : bizTags) {
                if (each == null) {
                    continue;
                }
                String tag = each.toString().trim();
                if (StringUtils.isNotBlank(tag)) {
                    normalized.add(tag);
                }
            }
            return normalized.isEmpty() ? null : JacksonUtils.toJson(normalized);
        }
        if (bizTagsObj instanceof String) {
            String bizTags = bizTagsObj.toString().trim();
            return StringUtils.isBlank(bizTags) ? null : bizTags;
        }
        return null;
    }
    
    private static String getManifestPrefix(String manifestPath) {
        int lastSlash = manifestPath.lastIndexOf(SLASH);
        return lastSlash < 0 ? "" : manifestPath.substring(0, lastSlash + 1);
    }
    
    private static List<ZipEntryData> filterEntriesByPrefix(List<ZipEntryData> entries,
        String prefix) {
        if (prefix.isEmpty()) {
            return entries;
        }
        List<ZipEntryData> result = new ArrayList<>();
        for (ZipEntryData entry : entries) {
            if (entry.name.startsWith(prefix)) {
                String relativeName = entry.name.substring(prefix.length());
                if (StringUtils.isNotBlank(relativeName)) {
                    result.add(new ZipEntryData(relativeName, entry.data));
                }
            }
        }
        return result;
    }
    
    /**
     * Parse resources from zip entries. Text files use UTF-8 content; binary (by extension) use Base64 content
     * and metadata encoding=base64. manifest.json is excluded from resources.
     */
    private static Map<String, AgentSpecResource> parseResources(List<ZipEntryData> entries,
        String agentSpecName) {
        Map<String, AgentSpecResource> resources = new HashMap<>(16);
        
        for (ZipEntryData entry : entries) {
            String itemName = entry.name;
            if (isMacOsMetadataFile(itemName)) {
                continue;
            }
            // Skip manifest.json and directories
            if (itemName.endsWith(MANIFEST_JSON) || itemName.endsWith("/")) {
                continue;
            }
            
            String[] parts = itemName.split("/");
            String type;
            String resourceName;
            if (parts.length == 1) {
                // Top-level file (e.g. Dockerfile)
                type = determineResourceType(itemName);
                resourceName = parts[0];
            } else {
                // Files in subdirectories
                StringBuilder typeSb = new StringBuilder();
                for (int i = 0; i < parts.length - 1; i++) {
                    if (typeSb.length() > 0) {
                        typeSb.append('/');
                    }
                    typeSb.append(parts[i]);
                }
                type = typeSb.toString();
                resourceName = parts[parts.length - 1];
            }
            
            ResourceContentEncoder.EncodedContent encoded =
                ResourceContentEncoder.encode(entry.data, resourceName);
            
            AgentSpecResource resource = new AgentSpecResource();
            resource.setName(resourceName);
            resource.setType(type);
            resource.setContent(encoded.getContent());
            resource.setMetadata(encoded.getMetadata());
            String key = AgentSpecUtils.generateResourceId(type, resourceName);
            resources.put(key, resource);
        }
        
        return resources;
    }
    
    /**
     * Determine resource type for top-level files based on filename.
     */
    private static String determineResourceType(String fileName) {
        if ("Dockerfile".equals(fileName)) {
            return "dockerfile";
        }
        if ("tool-analysis.json".equals(fileName)) {
            return "tool-analysis";
        }
        return "";
    }
    
    /**
     * Check if a file is macOS metadata that should be filtered out.
     * Filters: __MACOSX/* entries, .DS_Store files, and ._ prefixed files.
     */
    private static boolean isMacOsMetadataFile(String itemName) {
        if (StringUtils.isBlank(itemName)) {
            return false;
        }
        // __MACOSX directory entries
        if (itemName.startsWith("__MACOSX/") || itemName.contains("/__MACOSX/")) {
            return true;
        }
        int lastSlash = itemName.lastIndexOf('/');
        String fileName = lastSlash >= 0 ? itemName.substring(lastSlash + 1) : itemName;
        // .DS_Store files
        if (DS_STORE.equals(fileName)) {
            return true;
        }
        // ._ prefixed files (AppleDouble resource fork metadata)
        return fileName.startsWith(MACOS_METADATA_PREFIX);
    }
    
    /**
     * Standalone AgentSpec package built from a bundled seed archive.
     */
    public static final class AgentSpecPackage {
        
        private final String agentSpecName;
        
        private final String from;
        
        private final String sourcePath;
        
        private final byte[] zipBytes;
        
        public AgentSpecPackage(String agentSpecName, String from, String sourcePath,
            byte[] zipBytes) {
            this.agentSpecName = agentSpecName;
            this.from = from;
            this.sourcePath = sourcePath;
            this.zipBytes = zipBytes == null ? new byte[0] : zipBytes;
        }
        
        public String getAgentSpecName() {
            return agentSpecName;
        }
        
        public String getFrom() {
            return from;
        }
        
        public String getSourcePath() {
            return sourcePath;
        }
        
        public byte[] getZipBytes() {
            return zipBytes;
        }
    }
    
    /**
     * Internal data holder for zip entry name and raw bytes.
     */
    private static final class ZipEntryData {
        
        final String name;
        
        final byte[] data;
        
        ZipEntryData(String name, byte[] data) {
            this.name = name;
            this.data = data;
        }
    }
}
