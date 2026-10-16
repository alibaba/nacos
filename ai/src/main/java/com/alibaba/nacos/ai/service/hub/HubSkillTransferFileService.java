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

package com.alibaba.nacos.ai.service.hub;

import com.alibaba.nacos.ai.constant.AiResourceConstants;
import com.alibaba.nacos.ai.model.AiResourceVersion;
import com.alibaba.nacos.ai.storage.NacosConfigAiResourceStorage;
import com.alibaba.nacos.ai.utils.SkillRequestUtil;
import com.alibaba.nacos.api.ai.model.skills.Skill;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.plugin.ai.storage.AiResourceStorageRouter;
import com.alibaba.nacos.plugin.ai.storage.model.StorageKey;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads the exact published version storage descriptor and copies real bytes
 * to a different namespace/name/version key. A Hub version descriptor is NEVER
 * passed here; only a freshly validated source AiResourceVersion is accepted.
 */
@Service
public class HubSkillTransferFileService {
    
    private static final int MAX_FILES = 128;
    private static final int MAX_FILE_BYTES = 2 * 1024 * 1024;
    private static final long MAX_TOTAL_BYTES = 16L * 1024 * 1024;
    
    public static final class Files {
        
        private final Map<String, byte[]> contents;
        private final String description;
        private final String provider;
        
        private Files(Map<String, byte[]> contents, String description, String provider) {
            this.contents = copyContents(contents);
            this.description = description;
            this.provider = provider;
        }
        
        public Map<String, byte[]> getContents() {
            return copyContents(contents);
        }
        
        public String getDescription() {
            return description;
        }
        
        public String getProvider() {
            return provider;
        }
        
        private static Map<String, byte[]> copyContents(Map<String, byte[]> source) {
            Map<String, byte[]> result = new LinkedHashMap<>();
            source.forEach((path, bytes) -> result.put(path, Arrays.copyOf(bytes, bytes.length)));
            return Collections.unmodifiableMap(result);
        }
        
        public Files forProvider(String targetProvider) {
            return new Files(new LinkedHashMap<>(contents), description, targetProvider);
        }
    }
    
    /** Read without resolving a mutable label; validate all paths and file lengths. */
    @SuppressWarnings("unchecked")
    public Files load(HubSkillTransferSourceSnapshot source, AiResourceVersion version,
        String targetName, String targetVersion) throws NacosException {
        if (source == null || version == null || !source.getVersion().equals(version.getVersion())
            || version.getId() == null || source.getVersionId() != version.getId()
            || !Objects.equals(source.getNamespaceId(), version.getNamespaceId())
            || !Objects.equals(source.getName(), version.getName())
            || !AiResourceConstants.RESOURCE_TYPE_SKILL.equals(version.getType())
            || !AiResourceConstants.VERSION_STATUS_ONLINE.equals(version.getStatus())
            || !Objects.equals(source.getStorageDescriptor(), version.getStorage())) {
            throw invalid("Source version identity changed");
        }
        Map<String, Object> descriptor;
        try {
            descriptor = JacksonUtils.toObj(version.getStorage(), Map.class);
        } catch (RuntimeException bad) {
            throw invalid("Source version storage descriptor is malformed");
        }
        if (descriptor == null || !(descriptor.get("files") instanceof List)) {
            throw invalid("Source version has no files");
        }
        List<?> files = (List<?>) descriptor.get("files");
        if (files.isEmpty() || files.size() > MAX_FILES) {
            throw invalid("Source Skill file count exceeds import limits");
        }
        Object providerValue = descriptor.get("provider");
        if (providerValue != null && !(providerValue instanceof String)) {
            throw invalid("Source storage provider is invalid");
        }
        String provider = providerValue == null ? "nacos_config" : (String) providerValue;
        if (!provider.matches("[a-zA-Z0-9_-]{1,64}")) {
            throw invalid("Source storage provider is invalid");
        }
        Map<String, byte[]> content = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();
        for (Object item : files) {
            if (!(item instanceof String)) {
                throw invalid("Source Skill file path is invalid");
            }
            String path = (String) item;
            requireSafePath(path);
            if (!seen.add(path)) {
                throw invalid("Source Skill contains duplicate files");
            }
        }
        if (!seen.contains("SKILL.md")) {
            throw invalid("Source Skill lacks SKILL.md");
        }
        long total = 0L;
        for (Object item : files) {
            String path = (String) item;
            StorageKey key = NacosConfigAiResourceStorage.buildStorageKey(provider,
                source.getNamespaceId(), source.getName(), source.getVersion(), path);
            byte[] bytes;
            try {
                bytes = AiResourceStorageRouter.getInstance().route(key).get(key);
            } catch (NacosException | RuntimeException failure) {
                throw unavailable();
            }
            if (bytes == null || bytes.length > MAX_FILE_BYTES) {
                throw unavailable();
            }
            total += bytes.length;
            if (total > MAX_TOTAL_BYTES) {
                throw invalid("Source Skill exceeds import size limits");
            }
            // The source _meta.json can carry an old source version/name: do not copy it.
            if (!"_meta.json".equals(path) && !path.endsWith("/_meta.json")) {
                content.put(path, bytes);
            }
        }
        byte[] markdown = content.get("SKILL.md");
        if (markdown == null || markdown.length == 0) {
            throw invalid("Source Skill lacks SKILL.md");
        }
        Skill normalized = new Skill();
        normalized.setName(targetName);
        try {
            normalized.setSkillMd(StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(markdown)).toString());
        } catch (CharacterCodingException malformed) {
            throw invalid("Source SKILL.md is not valid UTF-8");
        }
        // isFirstCreate=false forces the *target* name, not the source frontmatter name.
        SkillRequestUtil.normalizeSkillFrontmatter(normalized, targetName, targetVersion, false);
        SkillRequestUtil.validateSkillMarkdownBody("skillMd", normalized.getSkillMd());
        String description = normalized.getDescription();
        if (description == null || description.isBlank()) {
            throw invalid("Source SKILL.md requires a description");
        }
        byte[] normalizedMarkdown = normalized.getSkillMd().getBytes(StandardCharsets.UTF_8);
        if (normalizedMarkdown.length > MAX_FILE_BYTES
            || total - markdown.length + normalizedMarkdown.length > MAX_TOTAL_BYTES) {
            throw invalid("Target Skill exceeds import size limits");
        }
        content.put("SKILL.md", normalizedMarkdown);
        return new Files(content, description, provider);
    }
    
    /** Save sequentially so rollback knows every key that may have been touched. */
    public void save(String namespaceId, String name, String version, Files files,
        List<String> attempted) throws NacosException {
        for (Map.Entry<String, byte[]> entry : files.getContents().entrySet()) {
            String path = entry.getKey();
            StorageKey key = NacosConfigAiResourceStorage.buildStorageKey(files.getProvider(),
                namespaceId, name, version, path);
            attempted.add(path); // include the failing key: provider may write-then-throw.
            AiResourceStorageRouter.getInstance().route(key).save(key, entry.getValue());
        }
    }
    
    /** Best effort: all failures are aggregated and surfaced, never silently ignored. */
    public void delete(String namespaceId, String name, String version, String provider,
        List<String> attempted) throws NacosException {
        NacosException first = null;
        for (String path : attempted) {
            try {
                StorageKey key = NacosConfigAiResourceStorage.buildStorageKey(provider,
                    namespaceId, name, version, path);
                AiResourceStorageRouter.getInstance().route(key).delete(key);
            } catch (NacosException | RuntimeException error) {
                NacosException failure = new NacosException(NacosException.SERVER_ERROR,
                    "Failed to clean imported Skill file", error);
                if (first == null) {
                    first = failure;
                } else {
                    first.addSuppressed(failure);
                }
            }
        }
        if (first != null) {
            throw first;
        }
    }
    
    /** Reject unsafe relative file paths before accessing storage. */
    public static void requireSafePath(String path) throws NacosException {
        if (path == null || path.isBlank() || path.length() > 512 || path.startsWith("/")
            || path.contains("..") || path.contains("//") || path.contains("\\")
            || path.endsWith("/") || path.equals(".") || path.startsWith("./")
            || path.contains("/./") || path.endsWith("/.") || path.contains(":")
            || path.chars().anyMatch(ch -> ch < 32)) {
            throw invalid("Unsafe source Skill file path");
        }
    }
    
    private static NacosApiException invalid(String message) {
        return new NacosApiException(NacosException.INVALID_PARAM,
            ErrorCode.PARAMETER_VALIDATE_ERROR, message);
    }
    
    private static NacosApiException unavailable() {
        return new NacosApiException(NacosException.NOT_FOUND,
            ErrorCode.RESOURCE_NOT_FOUND, "Source Skill content is unavailable");
    }
}
