/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain the License at
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

import com.alibaba.nacos.api.ai.model.skills.SkillUtils;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Read a bundled agentspec archive and convert each agentspec directory (with manifest.json) into a standalone zip.
 *
 * @author nacos
 */
public final class AgentSpecSeedArchiveReader {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(AgentSpecSeedArchiveReader.class);
    
    private static final String MANIFEST_JSON = "manifest.json";
    
    static final int DEFAULT_MAX_SEED_ARCHIVE_ENTRIES = 10000;
    
    static final long DEFAULT_MAX_SEED_UNCOMPRESSED_BYTES = 256L * 1024L * 1024L;
    
    private AgentSpecSeedArchiveReader() {
    }
    
    /**
     * Read bundled seed archive and return standalone agentspec packages.
     *
     * @param inputStream archive input stream
     * @return agentspec packages
     * @throws IOException if reading failed
     */
    public static List<AgentSpecPackage> read(InputStream inputStream) throws IOException {
        int maxEntries = Math.max(DEFAULT_MAX_SEED_ARCHIVE_ENTRIES,
            AgentSpecZipParser.resolveMaxZipEntries());
        long maxUncompressedBytes = Math.max(DEFAULT_MAX_SEED_UNCOMPRESSED_BYTES,
            AgentSpecZipParser.resolveMaxUncompressedBytes());
        return read(inputStream, maxEntries, maxUncompressedBytes);
    }
    
    static List<AgentSpecPackage> read(InputStream inputStream, int maxEntries,
        long maxUncompressedBytes) throws IOException {
        Map<String, byte[]> entries =
            readArchiveEntries(inputStream, maxEntries, maxUncompressedBytes);
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
            String manifestPath = rootPath(root, MANIFEST_JSON);
            byte[] manifestBytes = entries.get(manifestPath);
            if (manifestBytes == null) {
                continue;
            }
            String agentSpecName = extractSuggestedName(manifestBytes);
            if (StringUtils.isBlank(agentSpecName)) {
                throw new IOException("Missing worker.suggested_name in " + manifestPath);
            }
            if (!seenNames.add(agentSpecName)) {
                LOGGER.warn("Skip duplicate built-in agentspec name `{}` from archive path `{}`",
                    agentSpecName, root);
                continue;
            }
            result.add(new AgentSpecPackage(agentSpecName, extractFrom(root), root,
                buildAgentSpecZip(entries, root)));
        }
        return result;
    }
    
    private static Map<String, byte[]> readArchiveEntries(InputStream inputStream, int maxEntries,
        long maxUncompressedBytes) throws IOException {
        Map<String, byte[]> result = new LinkedHashMap<>();
        Set<String> seenEntryNames = new HashSet<>();
        int entryCount = 0;
        long totalSize = 0;
        try (ZipArchiveInputStream zis =
            new ZipArchiveInputStream(inputStream, StandardCharsets.UTF_8.name(), true,
                true)) {
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
            if (entryName.endsWith("/" + MANIFEST_JSON)) {
                result.add(entryName.substring(0, entryName.length() - MANIFEST_JSON.length() - 1));
            }
        }
        return result;
    }
    
    @SuppressWarnings("unchecked")
    private static String extractSuggestedName(byte[] manifestBytes) {
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
    
    private static byte[] buildAgentSpecZip(Map<String, byte[]> entries, String root)
        throws IOException {
        List<String> paths = new ArrayList<>();
        for (String entryName : entries.keySet()) {
            if (isInRoot(entryName, root)) {
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
    
    private static boolean isInRoot(String entryName, String root) {
        return root.isEmpty() || entryName.startsWith(root + "/");
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
    
    private static String rootPath(String root, String fileName) {
        return root.isEmpty() ? fileName : root + "/" + fileName;
    }
    
    private static String extractFrom(String sourcePath) {
        if (StringUtils.isBlank(sourcePath)) {
            return null;
        }
        String normalized = sourcePath;
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        int idx = normalized.lastIndexOf('/');
        if (idx <= 0) {
            return null;
        }
        String from = normalized.substring(0, idx).trim();
        return StringUtils.isBlank(from) ? null : from;
    }
    
    /**
     * Standalone agentspec package built from the seed archive.
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
}
