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

package com.alibaba.nacos.ai.service.ard;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.model.ard.ArdChunk;
import com.alibaba.nacos.ai.model.ard.ArdEntry;
import com.alibaba.nacos.ai.storage.NacosConfigAiResourceStorage;
import com.alibaba.nacos.api.ai.model.prompt.PromptUtils;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.MD5Utils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.fasterxml.jackson.core.type.TypeReference;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Builds searchable chunks from an ARD entry.
 *
 * @author nacos
 */
public class ArdChunkBuilder {
    
    private static final String SKILL_MD_RESOURCE_NAME = "SKILL.md";
    
    private static final String MCP_CONTENT_PATH_PREFIX = "mcp-";
    
    private static final TypeReference<List<String>> STRING_LIST_TYPE =
        new TypeReference<List<String>>() {
        };
    
    private static final TypeReference<Map<String, Object>> MAP_TYPE =
        new TypeReference<Map<String, Object>>() {
        };
    
    private final SkillMarkdownSearchTextExtractor skillMarkdownSearchTextExtractor =
        new SkillMarkdownSearchTextExtractor();
    
    /**
     * Build chunks used by keyword and vector retrieval.
     */
    public List<ArdChunk> buildChunks(ArdEntry entry) {
        List<ArdChunk> chunks = new ArrayList<>();
        addChunk(chunks, entry, ArdIndexConstants.CHUNK_TYPE_DESCRIPTION,
            firstNotBlank(entry.getDescription(), entry.getDisplayName()), null);
        addListChunks(chunks, entry, ArdIndexConstants.CHUNK_TYPE_CAPABILITY,
            parseStringList(entry.getCapabilities()));
        addListChunks(chunks, entry, ArdIndexConstants.CHUNK_TYPE_REPRESENTATIVE_QUERY,
            parseStringList(entry.getRepresentativeQueries()));
        addListChunks(chunks, entry, ArdIndexConstants.CHUNK_TYPE_TAG,
            parseStringList(entry.getTags()));
        Map<String, Object> metadata = parseMap(entry.getMetadata());
        addMetadataChunk(chunks, entry, metadata, ArdIndexConstants.CHUNK_TYPE_METADATA_IO,
            "inputTypes", "outputTypes");
        addMetadataChunk(chunks, entry, metadata, ArdIndexConstants.CHUNK_TYPE_METADATA_RISK,
            "sideEffects", "riskLevel");
        addMetadataChunk(chunks, entry, metadata, ArdIndexConstants.CHUNK_TYPE_NOT_FOR, "notFor");
        return chunks;
    }
    
    /**
     * Build deterministic search chunks from Skill markdown content.
     */
    public List<ArdChunk> buildSkillContentChunks(ArdEntry entry,
        List<ArdIndexEnhancementContent> contents) {
        return buildSourceContentChunks(entry, contents);
    }
    
    /**
     * Build deterministic search chunks from stored or supplied resource content.
     */
    public List<ArdChunk> buildSourceContentChunks(ArdEntry entry,
        List<ArdIndexEnhancementContent> contents) {
        if (contents == null || contents.isEmpty()) {
            return Collections.emptyList();
        }
        List<ArdChunk> chunks = new ArrayList<>();
        for (ArdIndexEnhancementContent content : contents) {
            String chunkType = sourceContentChunkType(entry, content);
            if (content == null || StringUtils.isBlank(chunkType)) {
                continue;
            }
            for (String text : skillMarkdownSearchTextExtractor.extract(content.getText())) {
                addChunk(chunks, entry, chunkType, text, sourceContentMetadata(content.getPath(),
                    sourceContentType(chunkType)));
            }
        }
        return dedupeByHash(chunks);
    }
    
    /**
     * Build chunks from optional AI-generated index enhancement text.
     */
    public List<ArdChunk> buildEnhancementChunks(ArdEntry entry,
        List<ArdIndexEnhancementChunk> enhancements) {
        if (enhancements == null || enhancements.isEmpty()) {
            return Collections.emptyList();
        }
        List<ArdChunk> chunks = new ArrayList<>();
        for (ArdIndexEnhancementChunk enhancement : enhancements) {
            if (enhancement == null || StringUtils.isBlank(enhancement.getChunkType())) {
                continue;
            }
            addChunk(chunks, entry, enhancement.getChunkType(), enhancement.getText(),
                enhancement.getMetadata());
        }
        return dedupeByHash(chunks);
    }
    
    private void addListChunks(List<ArdChunk> chunks, ArdEntry entry, String chunkType,
        List<String> values) {
        for (String value : values) {
            addChunk(chunks, entry, chunkType, value, null);
        }
    }
    
    private void addMetadataChunk(List<ArdChunk> chunks, ArdEntry entry,
        Map<String, Object> metadata, String chunkType, String... keys) {
        Map<String, Object> selected = new LinkedHashMap<>();
        List<String> textParts = new ArrayList<>();
        for (String key : keys) {
            Object value = metadata.get(key);
            if (value == null) {
                continue;
            }
            selected.put(key, value);
            for (String text : toStringList(value)) {
                textParts.add(key + ":" + text);
            }
        }
        if (!textParts.isEmpty()) {
            addChunk(chunks, entry, chunkType, StringUtils.join(textParts, " "),
                JacksonUtils.toJson(selected));
        }
    }
    
    private void addChunk(List<ArdChunk> chunks, ArdEntry entry, String chunkType, String text,
        String metadata) {
        if (StringUtils.isBlank(text)) {
            return;
        }
        ArdChunk chunk = new ArdChunk();
        chunk.setNamespaceId(entry.getNamespaceId());
        chunk.setIdentifier(entry.getIdentifier());
        chunk.setResourceType(entry.getResourceType());
        chunk.setResourceName(entry.getResourceName());
        chunk.setResourceVersion(entry.getResourceVersion());
        chunk.setChunkType(chunkType);
        chunk.setChunkText(text.trim());
        chunk.setCanonicalText(canonicalText(entry, chunkType, text));
        chunk.setLanguage("und");
        chunk.setMetadata(metadata);
        chunk.setStatus(ArdIndexConstants.STATUS_ENABLED);
        chunk.setChunkHash(
            md5(entry.getIdentifier() + ":" + chunkType + ":" + chunk.getCanonicalText()));
        chunks.add(chunk);
    }
    
    private String canonicalText(ArdEntry entry, String chunkType, String text) {
        List<String> parts = new ArrayList<>();
        parts.add(entry.getResourceType());
        parts.add(entry.getDisplayName());
        parts.add(chunkType);
        parts.add(text);
        return StringUtils.join(parts, " ").toLowerCase(Locale.ROOT);
    }
    
    private List<String> parseStringList(String value) {
        if (StringUtils.isBlank(value)) {
            return Collections.emptyList();
        }
        try {
            List<String> parsed = JacksonUtils.toObj(value, STRING_LIST_TYPE);
            return parsed == null ? Collections.emptyList() : parsed;
        } catch (Exception ignored) {
            return Collections.singletonList(value);
        }
    }
    
    private Map<String, Object> parseMap(String value) {
        if (StringUtils.isBlank(value)) {
            return Collections.emptyMap();
        }
        try {
            Map<String, Object> parsed = JacksonUtils.toObj(value, MAP_TYPE);
            return parsed == null ? Collections.emptyMap() : parsed;
        } catch (Exception ignored) {
            return Collections.emptyMap();
        }
    }
    
    private List<String> toStringList(Object value) {
        if (value == null) {
            return Collections.emptyList();
        }
        if (value instanceof Collection) {
            List<String> result = new ArrayList<>();
            for (Object each : (Collection<?>) value) {
                if (each != null && StringUtils.isNotBlank(String.valueOf(each))) {
                    result.add(String.valueOf(each));
                }
            }
            return result;
        }
        if (value instanceof String && StringUtils.isNotBlank((String) value)) {
            return Collections.singletonList((String) value);
        }
        return Collections.singletonList(String.valueOf(value));
    }
    
    private List<ArdChunk> dedupeByHash(List<ArdChunk> chunks) {
        if (chunks.isEmpty()) {
            return chunks;
        }
        List<ArdChunk> result = new ArrayList<>();
        for (ArdChunk chunk : chunks) {
            if (!containsHash(result, chunk.getChunkHash())) {
                result.add(chunk);
            }
        }
        return result;
    }
    
    private String sourceContentChunkType(ArdEntry entry, ArdIndexEnhancementContent content) {
        if (entry == null || content == null) {
            return null;
        }
        if (Constants.Skills.RESOURCE_TYPE_SKILL.equals(entry.getResourceType())
            && SKILL_MD_RESOURCE_NAME.equals(content.getPath())) {
            return ArdIndexConstants.CHUNK_TYPE_SKILL_CONTENT;
        }
        if (NacosConfigAiResourceStorage.RESOURCE_TYPE_PROMPT.equals(entry.getResourceType())
            && PromptUtils.PROMPT_MAIN_DATA_ID.equals(content.getPath())) {
            return ArdIndexConstants.CHUNK_TYPE_PROMPT_CONTENT;
        }
        if (ArdIndexConstants.RESOURCE_TYPE_MCP.equals(entry.getResourceType())
            && StringUtils.isNotBlank(content.getPath())
            && content.getPath().startsWith(MCP_CONTENT_PATH_PREFIX)) {
            return ArdIndexConstants.CHUNK_TYPE_MCP_CONTENT;
        }
        return null;
    }
    
    private String sourceContentType(String chunkType) {
        if (ArdIndexConstants.CHUNK_TYPE_SKILL_CONTENT.equals(chunkType)) {
            return "skill_md";
        }
        if (ArdIndexConstants.CHUNK_TYPE_PROMPT_CONTENT.equals(chunkType)) {
            return "prompt_content";
        }
        if (ArdIndexConstants.CHUNK_TYPE_MCP_CONTENT.equals(chunkType)) {
            return "mcp_spec";
        }
        return "content";
    }
    
    private String sourceContentMetadata(String path, String source) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("source", source);
        metadata.put("path", path);
        metadata.put("extractor", "rule");
        return JacksonUtils.toJson(metadata);
    }
    
    private boolean containsHash(List<ArdChunk> chunks, String chunkHash) {
        for (ArdChunk chunk : chunks) {
            if (chunkHash.equals(chunk.getChunkHash())) {
                return true;
            }
        }
        return false;
    }
    
    private String firstNotBlank(String first, String second) {
        return StringUtils.isNotBlank(first) ? first : second;
    }
    
    private String md5(String value) {
        return MD5Utils.md5Hex(value, StandardCharsets.UTF_8.name());
    }
}
