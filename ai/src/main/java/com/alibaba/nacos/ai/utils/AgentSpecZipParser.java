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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        try {
            List<ZipEntryData> entries = unzipToEntries(zipBytes);
            String manifestContent = null;
            for (ZipEntryData entry : entries) {
                String name = entry.name;
                if (isMacOsMetadataFile(name)) {
                    continue;
                }
                boolean isManifestFile = MANIFEST_JSON.equals(name);
                boolean isManifestInSubdir = name.endsWith(SLASH + MANIFEST_JSON);
                if (isManifestFile || isManifestInSubdir) {
                    manifestContent = new String(entry.data, StandardCharsets.UTF_8);
                    break;
                }
            }
            
            if (StringUtils.isBlank(manifestContent)) {
                throw new NacosApiException(NacosApiException.INVALID_PARAM,
                    ErrorCode.PARAMETER_VALIDATE_ERROR,
                    "manifest.json file not found in zip");
            }
            
            AgentSpec agentSpec = parseManifest(manifestContent, namespaceId);
            Map<String, AgentSpecResource> resources = parseResources(entries, agentSpec.getName());
            agentSpec.setResource(resources);
            
            return agentSpec;
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
     * {@link #parseAgentSpecFromZip(byte[], String)} translates them into a {@link NacosApiException}
     * for the HTTP layer.
     */
    private static List<ZipEntryData> unzipToEntries(byte[] zipBytes) throws IOException {
        final int maxEntries = resolveMaxZipEntries();
        final long maxUncompressedBytes = resolveMaxUncompressedBytes();
        List<ZipEntryData> result = new ArrayList<>();
        long totalSize = 0;
        try (ZipArchiveInputStream zis =
            new ZipArchiveInputStream(new ByteArrayInputStream(zipBytes),
                StandardCharsets.UTF_8.name(), true, true)) {
            ZipArchiveEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                // Security: reject path traversal (..) and absolute paths.
                SkillUtils.validatePathSafety(name);
                if (name != null && (name.startsWith("__MACOSX/") || name.contains("/__MACOSX/"))) {
                    continue;
                }
                if (result.size() >= maxEntries) {
                    throw new NacosRuntimeException(ErrorCode.PARAMETER_VALIDATE_ERROR.getCode(),
                        "ZIP file contains too many entries (max " + maxEntries + ")");
                }
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                int n;
                while ((n = zis.read(buffer)) != -1) {
                    totalSize += n;
                    if (totalSize > maxUncompressedBytes) {
                        throw new NacosRuntimeException(
                            ErrorCode.PARAMETER_VALIDATE_ERROR.getCode(),
                            "ZIP decompressed size exceeds limit ("
                                + (maxUncompressedBytes / 1024 / 1024) + "MB)");
                    }
                    out.write(buffer, 0, n);
                }
                result.add(new ZipEntryData(name, out.toByteArray()));
            }
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
