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

package com.alibaba.nacos.ai.service.search;

import com.alibaba.nacos.ai.constant.AiResourceConstants;
import com.alibaba.nacos.ai.model.search.AiResourceSearchChunk;
import com.alibaba.nacos.ai.model.search.AiResourceSearchDocument;
import com.alibaba.nacos.api.ai.model.prompt.PromptUtils;
import com.alibaba.nacos.common.utils.JacksonUtils;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link AiResourceSearchChunkBuilder}.
 *
 * @author nacos
 */
class AiResourceSearchChunkBuilderTest {
    
    @Test
    void buildChunksShouldIncludeDescriptionTagsCapabilitiesAndMetadata() {
        AiResourceSearchDocument entry = new AiResourceSearchDocument();
        entry.setNamespaceId("public");
        entry.setResourceType(AiResourceConstants.RESOURCE_TYPE_SKILL);
        entry.setResourceName("api-helper");
        entry.setResourceVersion("1.0.0");
        entry.setDisplayName("api-helper");
        entry.setDescription("Generate API parameter tables");
        entry.setTags(JacksonUtils.toJson(List.of("api")));
        entry.setCapabilities(JacksonUtils.toJson(List.of("documentation")));
        entry.setRepresentativeQueries(JacksonUtils.toJson(List.of("api helper")));
        entry.setMetadata(JacksonUtils.toJson(Map.of("inputTypes", List.of("json"),
            "riskLevel", "low")));
        
        List<AiResourceSearchChunk> chunks = new AiResourceSearchChunkBuilder().buildChunks(entry);
        
        assertFalse(chunks.isEmpty());
        assertTrue(chunks.stream().anyMatch(
            chunk -> AiResourceSearchConstants.CHUNK_TYPE_DESCRIPTION
                .equals(chunk.getChunkType())));
        assertTrue(chunks.stream().anyMatch(
            chunk -> AiResourceSearchConstants.CHUNK_TYPE_METADATA_IO
                .equals(chunk.getChunkType())));
        assertTrue(chunks.stream().allMatch(chunk -> chunk.getChunkHash() != null));
    }
    
    @Test
    void buildEnhancementChunksShouldIncludeAiGeneratedSearchText() {
        AiResourceSearchDocument entry = entry();
        List<AiResourceIndexEnhancementChunk> enhancements =
            List.of(
                new AiResourceIndexEnhancementChunk(
                    AiResourceSearchConstants.CHUNK_TYPE_SEARCH_TERM,
                    "支付对账 payment reconciliation", "{\"source\":\"llm\"}"),
                new AiResourceIndexEnhancementChunk(
                    AiResourceSearchConstants.CHUNK_TYPE_SEARCH_INTENT,
                    "find a skill for payment reconciliation", "{\"source\":\"llm\"}"));
        
        List<AiResourceSearchChunk> chunks =
            new AiResourceSearchChunkBuilder().buildEnhancementChunks(entry,
                enhancements);
        
        assertTrue(chunks.stream().anyMatch(
            chunk -> AiResourceSearchConstants.CHUNK_TYPE_SEARCH_INTENT
                .equals(chunk.getChunkType())));
        assertTrue(chunks.stream().anyMatch(
            chunk -> AiResourceSearchConstants.CHUNK_TYPE_SEARCH_TERM
                .equals(chunk.getChunkType())));
        assertTrue(chunks.stream().anyMatch(
            chunk -> chunk.getChunkText().contains("find a skill for payment reconciliation")));
        assertTrue(chunks.stream().allMatch(chunk -> chunk.getChunkHash() != null));
    }
    
    @Test
    void buildSourceContentChunksShouldIncludeExtractedSkillMarkdownText() {
        AiResourceSearchDocument entry = entry();
        String markdown = "---\n"
            + "description: Create AI avatar and talking head videos.\n"
            + "---\n"
            + "## Triggers\n"
            + "- ai avatar\n"
            + "- talking head\n";
        
        List<AiResourceSearchChunk> chunks =
            new AiResourceSearchChunkBuilder().buildSourceContentChunks(entry,
                List.of(new AiResourceIndexEnhancementContent("SKILL.md", markdown)));
        
        assertTrue(chunks.stream().anyMatch(
            chunk -> AiResourceSearchConstants.CHUNK_TYPE_SKILL_CONTENT
                .equals(chunk.getChunkType())));
        assertTrue(chunks.stream().anyMatch(
            chunk -> chunk.getChunkText().contains("talking head")));
    }
    
    @Test
    void buildSourceContentChunksShouldIncludePromptContentText() {
        AiResourceSearchDocument entry = entry();
        entry.setResourceType(AiResourceConstants.RESOURCE_TYPE_PROMPT);
        
        List<AiResourceSearchChunk> chunks =
            new AiResourceSearchChunkBuilder().buildSourceContentChunks(entry,
                List.of(new AiResourceIndexEnhancementContent(PromptUtils.PROMPT_MAIN_DATA_ID,
                    "# Prompt template\n生成头像视频脚本，适合数字人介绍产品")));
        
        assertTrue(chunks.stream().anyMatch(
            chunk -> AiResourceSearchConstants.CHUNK_TYPE_PROMPT_CONTENT
                .equals(chunk.getChunkType())));
        assertTrue(chunks.stream().anyMatch(
            chunk -> chunk.getChunkText().contains("头像视频脚本")));
    }
    
    @Test
    void buildSourceContentChunksShouldIncludeMcpToolText() {
        AiResourceSearchDocument entry = entry();
        entry.setResourceType(AiResourceConstants.RESOURCE_TYPE_MCP);
        
        List<AiResourceSearchChunk> chunks =
            new AiResourceSearchChunkBuilder().buildSourceContentChunks(entry,
                List.of(new AiResourceIndexEnhancementContent("mcp-tools.json",
                    "# MCP tools\n## Tool avatar_video\nCreate avatar video from an image")));
        
        assertTrue(chunks.stream().anyMatch(
            chunk -> AiResourceSearchConstants.CHUNK_TYPE_MCP_CONTENT
                .equals(chunk.getChunkType())));
        assertTrue(chunks.stream().anyMatch(
            chunk -> chunk.getChunkText().contains("avatar_video")));
    }
    
    @Test
    void buildersShouldHandleEmptyMalformedAndDuplicateInputs() {
        AiResourceSearchChunkBuilder builder = new AiResourceSearchChunkBuilder();
        AiResourceSearchDocument entry = entry();
        assertTrue(builder.buildSourceContentChunks(entry, null).isEmpty());
        assertTrue(builder.buildSourceContentChunks(entry, Collections.emptyList()).isEmpty());
        assertTrue(builder.buildSourceContentChunks(entry, List.of(
            new AiResourceIndexEnhancementContent("unknown", "ignored"))).isEmpty());
        assertTrue(builder.buildSourceContentChunks(entry, Arrays.asList(null,
            new AiResourceIndexEnhancementContent("unknown", "ignored"))).isEmpty());
        assertTrue(builder.buildSourceContentChunks(null, List.of(
            new AiResourceIndexEnhancementContent("SKILL.md", "ignored"))).isEmpty());
        assertTrue(builder.buildSourceContentChunks(entry, List.of(
            new AiResourceIndexEnhancementContent("SKILL.md", "text")), "").isEmpty());
        List<AiResourceSearchChunk> customSource = builder.buildSourceContentChunks(entry,
            Arrays.asList(null,
                new AiResourceIndexEnhancementContent("custom.txt", "custom content")),
            "custom");
        assertEquals(1, customSource.size());
        assertTrue(customSource.get(0).getMetadata().contains("content"));
        
        assertTrue(builder.buildEnhancementChunks(entry, null).isEmpty());
        assertTrue(builder.buildEnhancementChunks(entry, Collections.emptyList()).isEmpty());
        List<AiResourceSearchChunk> enhancements = builder.buildEnhancementChunks(entry,
            Arrays.asList(null, new AiResourceIndexEnhancementChunk("", "ignored", null),
                new AiResourceIndexEnhancementChunk("custom", " ", null),
                new AiResourceIndexEnhancementChunk("custom", "same", null),
                new AiResourceIndexEnhancementChunk("custom", "same", null)));
        assertEquals(1, enhancements.size());
        
        entry.setTags("null");
        entry.setCapabilities("not-json");
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("inputTypes", null);
        metadata.put("riskLevel", 7);
        entry.setMetadata(JacksonUtils.toJson(metadata));
        assertFalse(builder.buildChunks(entry).isEmpty());
        entry.setMetadata("null");
        assertFalse(builder.buildChunks(entry).isEmpty());
        entry.setMetadata("");
        assertFalse(builder.buildChunks(entry).isEmpty());
        entry.setMetadata("not-json");
        assertFalse(builder.buildChunks(entry).isEmpty());
    }
    
    private AiResourceSearchDocument entry() {
        AiResourceSearchDocument entry = new AiResourceSearchDocument();
        entry.setNamespaceId("public");
        entry.setResourceType(AiResourceConstants.RESOURCE_TYPE_SKILL);
        entry.setResourceName("api-helper");
        entry.setResourceVersion("1.0.0");
        entry.setDisplayName("api-helper");
        entry.setDescription("Generate API parameter tables");
        return entry;
    }
}
