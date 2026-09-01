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
import com.alibaba.nacos.sys.env.EnvUtil;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.StandardEnvironment;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link OpenAiCompatibleResourceIndexEnhancementService}.
 *
 * @author nacos
 */
class OpenAiCompatibleResourceIndexEnhancementServiceTest {
    
    private ConfigurableEnvironment previousEnvironment;
    
    @BeforeEach
    void setUp() {
        previousEnvironment = EnvUtil.getEnvironment();
        EnvUtil.setEnvironment(new StandardEnvironment());
    }
    
    @AfterEach
    void tearDown() {
        System.clearProperty(OpenAiCompatibleResourceIndexEnhancementService.KEY_ENABLED);
        System.clearProperty(OpenAiCompatibleResourceIndexEnhancementService.KEY_ENDPOINT);
        System.clearProperty(OpenAiCompatibleResourceIndexEnhancementService.KEY_API_KEY);
        System.clearProperty(OpenAiCompatibleResourceIndexEnhancementService.KEY_MODEL);
        System.clearProperty(OpenAiCompatibleResourceIndexEnhancementService.KEY_TIMEOUT_MS);
        System.clearProperty(OpenAiCompatibleResourceIndexEnhancementService.KEY_MAX_ITEMS);
        EnvUtil.setEnvironment(previousEnvironment);
    }
    
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
    
    @Test
    void fingerprintShouldTrackOutputConfigurationButExcludeCredentials() {
        System.setProperty(OpenAiCompatibleResourceIndexEnhancementService.KEY_ENDPOINT,
            "https://example.com/v1/");
        System.setProperty(OpenAiCompatibleResourceIndexEnhancementService.KEY_MODEL, "model-v1");
        System.setProperty(OpenAiCompatibleResourceIndexEnhancementService.KEY_API_KEY, "key-one");
        OpenAiCompatibleResourceIndexEnhancementService service =
            new OpenAiCompatibleResourceIndexEnhancementService();
        
        String initial = service.fingerprint();
        System.setProperty(OpenAiCompatibleResourceIndexEnhancementService.KEY_ENDPOINT,
            "https://example.com/v1");
        assertEquals(initial, service.fingerprint());
        
        System.setProperty(OpenAiCompatibleResourceIndexEnhancementService.KEY_API_KEY, "key-two");
        assertEquals(initial, service.fingerprint());
        
        System.setProperty(OpenAiCompatibleResourceIndexEnhancementService.KEY_MODEL, "model-v2");
        assertFalse(initial.equals(service.fingerprint()));
    }
    
    @Test
    void resultShouldRecordConfigurationUsedByTheRequest() throws Exception {
        System.setProperty(OpenAiCompatibleResourceIndexEnhancementService.KEY_ENABLED, "true");
        System.setProperty(OpenAiCompatibleResourceIndexEnhancementService.KEY_ENDPOINT,
            "https://example.com/v1");
        System.setProperty(OpenAiCompatibleResourceIndexEnhancementService.KEY_MODEL, "model-v1");
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(
            "{\"choices\":[{\"message\":{\"content\":\"{\\\"summary\\\":\\\"summary\\\"}\"}}]}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenAnswer(invocation -> {
                System.setProperty(OpenAiCompatibleResourceIndexEnhancementService.KEY_MODEL,
                    "model-v2");
                return response;
            });
        OpenAiCompatibleResourceIndexEnhancementService service =
            new OpenAiCompatibleResourceIndexEnhancementService(httpClient);
        String requestedFingerprint = service.fingerprint();
        
        AiResourceIndexEnhancementResult result = service.enhanceWithResult(
            new AiResourceSearchDocument(), List.of(), List.of());
        
        assertEquals(requestedFingerprint, result.getFingerprint());
        assertFalse(requestedFingerprint.equals(service.fingerprint()));
    }
}
