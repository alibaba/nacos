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

import com.alibaba.nacos.ai.model.search.AiResourceSearchDocument;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link OpenAiCompatibleResourceIndexEnhancementService}.
 *
 * @author nacos
 */
class OpenAiCompatibleResourceIndexEnhancementServiceTest {
    
    @Test
    void systemPromptShouldFollowRetrievalEnrichmentContract() {
        String prompt = AiResourceIndexEnhancementPrompt.SYSTEM_PROMPT;
        
        assertTrue(prompt.contains("Generate compact bilingual retrieval-enrichment JSON"));
        assertTrue(prompt.contains("Use only the provided source content"));
        assertTrue(prompt.contains("must not infer"));
        assertTrue(prompt.contains("unsupported capabilities"));
        assertTrue(prompt.contains("humans or agents"));
        assertTrue(prompt.contains("Retrieval design"));
        assertTrue(prompt.contains("user jobs"));
        assertTrue(prompt.contains("searchIntents"));
        assertTrue(prompt.contains("searchTerms"));
        assertTrue(prompt.contains("Bilingual coverage"));
        assertTrue(prompt.contains("Do not require one-to-one translations"));
        assertTrue(prompt.contains("Avoid awkward literal translations"));
        assertTrue(prompt.contains("native-speaker"));
        assertTrue(prompt.contains("search-box check"));
        assertTrue(prompt.contains("\"searchIntents\""));
        assertTrue(prompt.contains("\"searchTerms\""));
        assertFalse(prompt.contains("\"searchPhrases\""));
        assertFalse(prompt.contains("\"bilingualAliases\""));
        assertFalse(prompt.contains("\"capabilitySynonyms\""));
        assertFalse(prompt.contains("\"exampleQueries\""));
        assertTrue(prompt.contains("Return strict JSON only"));
    }
    
    @Test
    void parseEnhancementContentShouldConvertJsonToChunks() {
        OpenAiCompatibleResourceIndexEnhancementService service =
            new OpenAiCompatibleResourceIndexEnhancementService();
        String content = "```json\n"
            + "{\"summary\":\"支付对账能力\","
            + "\"searchIntents\":[\"查找支付对账工具\","
            + "\"find a payment reconcile skill\"],"
            + "\"searchTerms\":[\"支付对账\",\"payment reconciliation\"]}"
            + "\n```";
        
        List<AiResourceIndexEnhancementChunk> chunks =
            service.parseEnhancementContent(content, "test-model");
        
        assertTrue(chunks.stream().anyMatch(
            chunk -> AiResourceSearchConstants.CHUNK_TYPE_AI_SUMMARY.equals(chunk.getChunkType())));
        assertTrue(chunks.stream().anyMatch(
            chunk -> AiResourceSearchConstants.CHUNK_TYPE_SEARCH_INTENT
                .equals(chunk.getChunkType())));
        assertTrue(chunks.stream().anyMatch(
            chunk -> AiResourceSearchConstants.CHUNK_TYPE_SEARCH_TERM
                .equals(chunk.getChunkType())));
    }
    
    @Test
    void resourcePayloadShouldIncludeSourceContentSnippets() {
        OpenAiCompatibleResourceIndexEnhancementService service =
            new OpenAiCompatibleResourceIndexEnhancementService();
        AiResourceSearchDocument entry = new AiResourceSearchDocument();
        entry.setResourceType("skill");
        entry.setResourceName("ai-video-avatar");
        String contentText = "Create AI avatar and talking head videos. " + "avatar ".repeat(100);
        
        Map<String, Object> payload = service.resourcePayload(entry, List.of(),
            List.of(new AiResourceIndexEnhancementContent("SKILL.md", contentText)));
        
        List<?> contents = (List<?>) payload.get("contents");
        assertFalse(contents.isEmpty());
        assertEquals(contentText, ((Map<?, ?>) contents.get(0)).get("text"));
        assertTrue(String.valueOf(contents).contains("talking head"));
    }
    
    @Test
    void errorMessageShouldIncludeCompactResponseBody() {
        OpenAiCompatibleResourceIndexEnhancementService service =
            new OpenAiCompatibleResourceIndexEnhancementService();
        String message = service.errorMessage(400, "bad\nrequest\r\n" + "x".repeat(1200));
        
        assertTrue(message.contains("status=400"));
        assertTrue(message.contains("body=bad request"));
        assertFalse(message.contains("\n"));
        assertFalse(message.contains("\r"));
        assertTrue(message.length() < 1100);
    }
    
    @Test
    void chatEndpointShouldAcceptOpenAiBaseUrl() {
        OpenAiCompatibleResourceIndexEnhancementService service =
            new OpenAiCompatibleResourceIndexEnhancementService();
        
        assertEquals("https://example.com/compatible-mode/v1/chat/completions",
            service.chatEndpoint("https://example.com/compatible-mode/v1"));
        assertEquals("https://example.com/compatible-mode/v1/chat/completions",
            service.chatEndpoint("https://example.com/compatible-mode/v1/"));
        assertEquals("https://example.com/compatible-mode/v1/chat/completions",
            service.chatEndpoint("https://example.com/compatible-mode/v1/chat/completions"));
        assertEquals("https://example.com/custom",
            service.chatEndpoint("https://example.com/custom"));
    }
}
