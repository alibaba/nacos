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
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link ArdChunkBuilder}.
 *
 * @author nacos
 */
class ArdChunkBuilderTest {
    
    @Test
    void buildChunksShouldIncludeDescriptionTagsCapabilitiesAndMetadata() {
        ArdEntry entry = new ArdEntry();
        entry.setNamespaceId("public");
        entry.setIdentifier("urn:air:nacos:public:skill:api-helper");
        entry.setResourceType(Constants.Skills.RESOURCE_TYPE_SKILL);
        entry.setResourceName("api-helper");
        entry.setResourceVersion("1.0.0");
        entry.setDisplayName("api-helper");
        entry.setDescription("Generate API parameter tables");
        entry.setTags(JacksonUtils.toJson(List.of("api")));
        entry.setCapabilities(JacksonUtils.toJson(List.of("documentation")));
        entry.setRepresentativeQueries(JacksonUtils.toJson(List.of("api helper")));
        entry.setMetadata(JacksonUtils.toJson(Map.of("inputTypes", List.of("json"),
            "riskLevel", "low")));
        
        List<ArdChunk> chunks = new ArdChunkBuilder().buildChunks(entry);
        
        assertFalse(chunks.isEmpty());
        assertTrue(chunks.stream().anyMatch(
            chunk -> ArdIndexConstants.CHUNK_TYPE_DESCRIPTION.equals(chunk.getChunkType())));
        assertTrue(chunks.stream().anyMatch(
            chunk -> ArdIndexConstants.CHUNK_TYPE_METADATA_IO.equals(chunk.getChunkType())));
        assertTrue(chunks.stream().allMatch(chunk -> chunk.getChunkHash() != null));
    }
    
    @Test
    void buildEnhancementChunksShouldIncludeAiGeneratedSearchText() {
        ArdEntry entry = entry();
        List<ArdIndexEnhancementChunk> enhancements =
            List.of(new ArdIndexEnhancementChunk(ArdIndexConstants.CHUNK_TYPE_SEARCH_TERM,
                "支付对账 payment reconciliation", "{\"source\":\"llm\"}"),
                new ArdIndexEnhancementChunk(ArdIndexConstants.CHUNK_TYPE_SEARCH_INTENT,
                    "find a skill for payment reconciliation", "{\"source\":\"llm\"}"));
        
        List<ArdChunk> chunks = new ArdChunkBuilder().buildEnhancementChunks(entry,
            enhancements);
        
        assertTrue(chunks.stream().anyMatch(
            chunk -> ArdIndexConstants.CHUNK_TYPE_SEARCH_INTENT.equals(chunk.getChunkType())));
        assertTrue(chunks.stream().anyMatch(
            chunk -> ArdIndexConstants.CHUNK_TYPE_SEARCH_TERM.equals(chunk.getChunkType())));
        assertTrue(chunks.stream().anyMatch(
            chunk -> chunk.getChunkText().contains("find a skill for payment reconciliation")));
        assertTrue(chunks.stream().allMatch(chunk -> chunk.getChunkHash() != null));
    }
    
    @Test
    void buildSkillContentChunksShouldIncludeExtractedSkillMarkdownText() {
        ArdEntry entry = entry();
        String markdown = "---\n"
            + "description: Create AI avatar and talking head videos.\n"
            + "---\n"
            + "## Triggers\n"
            + "- ai avatar\n"
            + "- talking head\n";
        
        List<ArdChunk> chunks = new ArdChunkBuilder().buildSkillContentChunks(entry,
            List.of(new ArdIndexEnhancementContent("SKILL.md", markdown)));
        
        assertTrue(chunks.stream().anyMatch(
            chunk -> ArdIndexConstants.CHUNK_TYPE_SKILL_CONTENT.equals(chunk.getChunkType())));
        assertTrue(chunks.stream().anyMatch(
            chunk -> chunk.getChunkText().contains("talking head")));
    }
    
    @Test
    void buildSourceContentChunksShouldIncludePromptContentText() {
        ArdEntry entry = entry();
        entry.setResourceType(NacosConfigAiResourceStorage.RESOURCE_TYPE_PROMPT);
        
        List<ArdChunk> chunks = new ArdChunkBuilder().buildSourceContentChunks(entry,
            List.of(new ArdIndexEnhancementContent(PromptUtils.PROMPT_MAIN_DATA_ID,
                "# Prompt template\n生成头像视频脚本，适合数字人介绍产品")));
        
        assertTrue(chunks.stream().anyMatch(
            chunk -> ArdIndexConstants.CHUNK_TYPE_PROMPT_CONTENT.equals(chunk.getChunkType())));
        assertTrue(chunks.stream().anyMatch(
            chunk -> chunk.getChunkText().contains("头像视频脚本")));
    }
    
    @Test
    void buildSourceContentChunksShouldIncludeMcpToolText() {
        ArdEntry entry = entry();
        entry.setResourceType(ArdIndexConstants.RESOURCE_TYPE_MCP);
        
        List<ArdChunk> chunks = new ArdChunkBuilder().buildSourceContentChunks(entry,
            List.of(new ArdIndexEnhancementContent("mcp-tools.json",
                "# MCP tools\n## Tool avatar_video\nCreate avatar video from an image")));
        
        assertTrue(chunks.stream().anyMatch(
            chunk -> ArdIndexConstants.CHUNK_TYPE_MCP_CONTENT.equals(chunk.getChunkType())));
        assertTrue(chunks.stream().anyMatch(
            chunk -> chunk.getChunkText().contains("avatar_video")));
    }
    
    private ArdEntry entry() {
        ArdEntry entry = new ArdEntry();
        entry.setNamespaceId("public");
        entry.setIdentifier("urn:air:nacos:public:skill:api-helper");
        entry.setResourceType(Constants.Skills.RESOURCE_TYPE_SKILL);
        entry.setResourceName("api-helper");
        entry.setResourceVersion("1.0.0");
        entry.setDisplayName("api-helper");
        entry.setDescription("Generate API parameter tables");
        return entry;
    }
}
