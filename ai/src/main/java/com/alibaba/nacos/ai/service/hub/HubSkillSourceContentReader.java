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
import com.alibaba.nacos.ai.service.resource.AiResourceManager;
import com.alibaba.nacos.ai.storage.NacosConfigAiResourceStorage;
import com.alibaba.nacos.ai.utils.ResourceContentEncoder;
import com.alibaba.nacos.api.ai.model.hub.HubSkillContent;
import com.alibaba.nacos.api.ai.model.hub.HubSkillDownload;
import com.alibaba.nacos.api.ai.model.hub.HubSkillResource;
import com.alibaba.nacos.api.ai.model.skills.SkillUtils;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.plugin.ai.storage.AiResourceStorageRouter;
import com.alibaba.nacos.plugin.ai.storage.model.StorageKey;

import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Server-only loader for the already-authorized, pinned source version.
 * Never interprets the Hub reference storage descriptor as Skill content.
 * This loader must only be invoked between the two Hub read authorization fences.
 */
@Service
public class HubSkillSourceContentReader {
    
    private static final int MAX_FILES = 128;
    private static final int MAX_FILE_BYTES = 2 * 1024 * 1024;
    private static final long MAX_TOTAL_BYTES = 16L * 1024 * 1024;
    
    private final AiResourceManager manager;
    
    private final HubSkillReadAccessService access;
    
    public HubSkillSourceContentReader(AiResourceManager manager,
        HubSkillReadAccessService access) {
        this.manager = manager;
        this.access = access;
    }
    
    /** Read files from the source, not from the Hub namespace. */
    public HubSkillContent load(ResolvedHubSkillReference publication) throws NacosException {
        if (publication == null) {
            throw unavailable();
        }
        access.requireReadable(publication);
        try {
            return loadSource(publication);
        } catch (RuntimeException failure) {
            throw unavailable();
        }
    }
    
    private HubSkillContent loadSource(ResolvedHubSkillReference publication)
        throws NacosException {
        AiResourceVersion version = requireVersion(publication);
        StorageDescriptor descriptor = requireDescriptor(version.getStorage());
        Map<String, HubSkillResource> contents = new LinkedHashMap<>();
        String skillMd = null;
        long totalBytes = 0L;
        try {
            for (String file : descriptor.files) {
                StorageKey key = NacosConfigAiResourceStorage.buildStorageKey(descriptor.provider,
                    publication.getSourceNamespaceId(), publication.getSourceName(),
                    publication.getSourceVersion(), file);
                byte[] bytes = AiResourceStorageRouter.getInstance().route(key).get(key);
                if (bytes == null || bytes.length > MAX_FILE_BYTES) {
                    throw unavailable();
                }
                totalBytes += bytes.length;
                if (totalBytes > MAX_TOTAL_BYTES) {
                    throw unavailable();
                }
                String content = new String(bytes, StandardCharsets.UTF_8);
                if ("SKILL.md".equals(file)) {
                    skillMd = content;
                } else if (!file.endsWith("/_meta.json") && !"_meta.json".equals(file)) {
                    HubSkillResource resource = new HubSkillResource();
                    int slash = file.lastIndexOf('/');
                    resource.setName(slash >= 0 ? file.substring(slash + 1) : file);
                    resource.setType(slash >= 0 ? file.substring(0, slash) : "");
                    resource.setContent(content);
                    contents.put(file, resource);
                }
            }
        } catch (NacosException exception) {
            throw unavailable();
        } catch (RuntimeException exception) {
            throw unavailable();
        }
        if (skillMd == null || skillMd.isBlank()) {
            throw unavailable();
        }
        HubSkillContent result = new HubSkillContent();
        result.setHubEntryId(publication.getHubEntryId());
        result.setDisplayName(publication.getSourceName());
        result.setSharedVersion(publication.getSourceVersion());
        result.setSkillMd(skillMd);
        result.setResource(contents);
        access.requireStillReadable(publication);
        return result;
    }
    
    private AiResourceVersion requireVersion(ResolvedHubSkillReference publication)
        throws NacosException {
        AiResourceVersion version = manager.findVersion(publication.getSourceNamespaceId(),
            publication.getSourceName(), AiResourceConstants.RESOURCE_TYPE_SKILL,
            publication.getSourceVersion());
        if (version == null || !Objects.equals(version.getId(), publication.getSourceVersionId())
            || !AiResourceConstants.RESOURCE_TYPE_SKILL.equals(version.getType())
            || !Objects.equals(version.getNamespaceId(), publication.getSourceNamespaceId())
            || !Objects.equals(version.getName(), publication.getSourceName())
            || !Objects.equals(version.getVersion(), publication.getSourceVersion())
            || !AiResourceConstants.VERSION_STATUS_ONLINE.equals(version.getStatus())) {
            throw unavailable();
        }
        return version;
    }
    
    /**
     * Package original bytes from the immutable source version without text decoding.
     *
     * @param publication already authorized Hub publication
     * @return source ZIP with no internal reference descriptor
     * @throws NacosException if storage or any revocation check fails
     */
    public HubSkillDownload download(ResolvedHubSkillReference publication) throws NacosException {
        if (publication == null) {
            throw unavailable();
        }
        access.requireReadable(publication);
        try {
            AiResourceVersion version = requireVersion(publication);
            StorageDescriptor descriptor = requireDescriptor(version.getStorage());
            SkillUtils.validatePathSafety(publication.getSourceName());
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            long total = 0L;
            try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
                for (String file : descriptor.files) {
                    if ("_meta.json".equals(file) || file.endsWith("/_meta.json")) {
                        continue;
                    }
                    StorageKey key = NacosConfigAiResourceStorage.buildStorageKey(
                        descriptor.provider, publication.getSourceNamespaceId(),
                        publication.getSourceName(), publication.getSourceVersion(), file);
                    byte[] original = AiResourceStorageRouter.getInstance().route(key).get(key);
                    if (original == null || original.length > MAX_FILE_BYTES
                        || ("SKILL.md".equals(file) && original.length == 0)) {
                        throw unavailable();
                    }
                    total += original.length;
                    if (total > MAX_TOTAL_BYTES) {
                        throw unavailable();
                    }
                    byte[] decoded = ResourceContentEncoder.isBinary(file)
                        ? Base64.getDecoder().decode(original) : original;
                    String entryName = publication.getSourceName() + "/" + file;
                    SkillUtils.validatePathSafety(entryName);
                    zip.putNextEntry(new ZipEntry(entryName));
                    zip.write(decoded);
                    zip.closeEntry();
                }
            }
            HubSkillDownload result = new HubSkillDownload();
            result.setHubEntryId(publication.getHubEntryId());
            result.setDisplayName(publication.getSourceName());
            result.setSharedVersion(publication.getSourceVersion());
            result.setZipBytes(bytes.toByteArray());
            access.requireStillReadable(publication);
            return result;
        } catch (NacosException | IOException | RuntimeException failure) {
            throw unavailable();
        }
    }
    
    @SuppressWarnings("unchecked")
    private StorageDescriptor requireDescriptor(String storage) throws NacosException {
        try {
            if (storage == null || storage.isBlank()) {
                throw unavailable();
            }
            Map<String, Object> parsed = JacksonUtils.toObj(storage, Map.class);
            if (parsed == null || !(parsed.get("files") instanceof List)) {
                throw unavailable();
            }
            List<?> rawFiles = (List<?>) parsed.get("files");
            if (rawFiles.isEmpty() || rawFiles.size() > MAX_FILES) {
                throw unavailable();
            }
            Object providerValue = parsed.get("provider");
            if (providerValue != null && !(providerValue instanceof String)) {
                throw unavailable();
            }
            String provider =
                providerValue == null ? "nacos_config" : ((String) providerValue).trim();
            if (provider.isBlank() || provider.length() > 64
                || !provider.matches("[a-zA-Z0-9_\\-]+")) {
                throw unavailable();
            }
            Set<String> seen = new HashSet<>();
            java.util.ArrayList<String> files = new java.util.ArrayList<>(rawFiles.size());
            for (Object value : rawFiles) {
                if (!(value instanceof String)) {
                    throw unavailable();
                }
                String file = (String) value;
                if (file.isBlank() || file.length() > 512 || file.startsWith("/")
                    || file.endsWith("/")
                    || file.equals(".") || file.startsWith("./") || file.contains("/./")
                    || file.endsWith("/.")
                    || file.contains("..") || file.contains("//") || file.contains("\\")
                    || file.contains(":") || file.chars().anyMatch(ch -> ch < 32)
                    || !seen.add(file)) {
                    throw unavailable();
                }
                files.add(file);
            }
            if (!seen.contains("SKILL.md")) {
                throw unavailable();
            }
            return new StorageDescriptor(provider, files);
        } catch (NacosException exception) {
            throw unavailable();
        } catch (RuntimeException exception) {
            throw unavailable();
        }
    }
    
    private NacosApiException unavailable() {
        return new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
            "Hub Skill not found or unavailable");
    }
    
    private static final class StorageDescriptor {
        
        private final String provider;
        private final List<String> files;
        
        private StorageDescriptor(String provider, List<String> files) {
            this.provider = provider;
            this.files = files;
        }
    }
}
