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

import com.alibaba.nacos.ai.config.ConditionalOnAiResourceSearchEnabled;
import com.alibaba.nacos.ai.model.search.AiResourceSearchChunk;
import com.alibaba.nacos.ai.model.search.AiResourceSearchDocument;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI-compatible AI resource index enhancement provider.
 *
 * @author nacos
 */
@Service
@ConditionalOnAiResourceSearchEnabled
public class OpenAiCompatibleResourceIndexEnhancementService
    implements AiResourceIndexEnhancementService {
    
    static final String KEY_ENABLED = "nacos.ai.resource.search.index.enhancement.enabled";
    
    static final String KEY_ENDPOINT = "nacos.ai.resource.search.index.enhancement.endpoint";
    
    static final String KEY_API_KEY = "nacos.ai.resource.search.index.enhancement.api-key";
    
    static final String KEY_MODEL = "nacos.ai.resource.search.index.enhancement.model";
    
    static final String KEY_TIMEOUT_MS = "nacos.ai.resource.search.index.enhancement.timeout-ms";
    
    static final String KEY_MAX_ITEMS =
        "nacos.ai.resource.search.index.enhancement.max-items-per-field";
    
    private static final int DEFAULT_TIMEOUT_MS = 10000;
    
    private static final int DEFAULT_MAX_ITEMS = 10;
    
    private static final int MAX_PROMPT_CHUNKS = 20;
    
    private static final int MAX_PROMPT_CONTENTS = 8;
    
    private static final int MAX_TEXT_LENGTH = 500;
    
    private static final int MAX_ERROR_BODY_LENGTH = 1000;
    
    private static final String PROMPT_VERSION = "v1";
    
    private static final String OUTPUT_SCHEMA_VERSION = "v1";
    
    private static final TypeReference<Map<String, Object>> MAP_TYPE =
        new TypeReference<Map<String, Object>>() {
        };
    
    private final HttpClient httpClient;
    
    public OpenAiCompatibleResourceIndexEnhancementService() {
        this(HttpClient.newBuilder().build());
    }
    
    OpenAiCompatibleResourceIndexEnhancementService(HttpClient httpClient) {
        this.httpClient = httpClient;
    }
    
    @Override
    public boolean ready() {
        return configuration().ready();
    }
    
    @Override
    public boolean requested() {
        return Boolean.parseBoolean(property(KEY_ENABLED, "false"));
    }
    
    @Override
    public String fingerprint() {
        return configuration().fingerprint;
    }
    
    private String fingerprint(String endpoint, String model, int maxItems) {
        String identity = String.join("\n", "openai-compatible", endpoint, model,
            PROMPT_VERSION, OUTPUT_SCHEMA_VERSION, String.valueOf(maxItems));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(identity.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                result.append(String.format("%02x", value & 0xff));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
    
    @Override
    public List<AiResourceIndexEnhancementChunk> enhance(AiResourceSearchDocument entry,
        List<AiResourceSearchChunk> existingChunks) throws Exception {
        return enhance(entry, existingChunks, Collections.emptyList());
    }
    
    @Override
    public List<AiResourceIndexEnhancementChunk> enhance(AiResourceSearchDocument entry,
        List<AiResourceSearchChunk> existingChunks,
        List<AiResourceIndexEnhancementContent> contents)
        throws Exception {
        return enhanceWithResult(entry, existingChunks, contents).getChunks();
    }
    
    @Override
    public AiResourceIndexEnhancementResult enhanceWithResult(AiResourceSearchDocument entry,
        List<AiResourceSearchChunk> existingChunks,
        List<AiResourceIndexEnhancementContent> contents)
        throws Exception {
        EnhancementConfiguration configuration = configuration();
        if (!configuration.ready()) {
            throw new IllegalStateException("AI resource index enhancement is not configured");
        }
        String content = requestEnhancement(entry, existingChunks, contents, configuration);
        return new AiResourceIndexEnhancementResult(
            parseEnhancementContent(content, configuration.model, configuration.maxItems),
            configuration.fingerprint);
    }
    
    private String requestEnhancement(AiResourceSearchDocument entry,
        List<AiResourceSearchChunk> existingChunks,
        List<AiResourceIndexEnhancementContent> contents,
        EnhancementConfiguration configuration) throws Exception {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", configuration.model);
        request.put("temperature", 0.2D);
        request.put("messages", messages(entry, existingChunks, contents));
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(configuration.endpoint))
            .timeout(Duration.ofMillis(configuration.timeoutMs))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(JacksonUtils.toJson(request),
                StandardCharsets.UTF_8));
        String apiKey = configuration.apiKey;
        if (StringUtils.isNotBlank(apiKey)) {
            builder.header("Authorization", "Bearer " + apiKey);
        }
        HttpResponse<String> response =
            httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException(
                errorMessage(response.statusCode(), response.body()));
        }
        return responseContent(response.body());
    }
    
    private List<Map<String, String>> messages(AiResourceSearchDocument entry,
        List<AiResourceSearchChunk> existingChunks,
        List<AiResourceIndexEnhancementContent> contents) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(message("system", AiResourceIndexEnhancementPrompt.SYSTEM_PROMPT));
        messages.add(message("user",
            JacksonUtils.toJson(resourcePayload(entry, existingChunks, contents))));
        return messages;
    }
    
    Map<String, Object> resourcePayload(AiResourceSearchDocument entry,
        List<AiResourceSearchChunk> existingChunks,
        List<AiResourceIndexEnhancementContent> contents) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("resourceType", entry.getResourceType());
        payload.put("resourceName", entry.getResourceName());
        payload.put("displayName", entry.getDisplayName());
        payload.put("description", entry.getDescription());
        payload.put("tags", entry.getTags());
        payload.put("capabilities", entry.getCapabilities());
        payload.put("representativeQueries", entry.getRepresentativeQueries());
        payload.put("metadata", entry.getMetadata());
        payload.put("chunks", chunkPayload(existingChunks));
        payload.put("contents", contentPayload(contents));
        return payload;
    }
    
    private List<Map<String, String>> chunkPayload(List<AiResourceSearchChunk> existingChunks) {
        if (existingChunks == null || existingChunks.isEmpty()) {
            return Collections.emptyList();
        }
        List<Map<String, String>> result = new ArrayList<>();
        for (AiResourceSearchChunk chunk : existingChunks) {
            if (chunk == null || result.size() >= MAX_PROMPT_CHUNKS) {
                continue;
            }
            Map<String, String> item = new LinkedHashMap<>();
            item.put("type", chunk.getChunkType());
            item.put("text", limit(chunk.getChunkText()));
            result.add(item);
        }
        return result;
    }
    
    private List<Map<String, String>> contentPayload(
        List<AiResourceIndexEnhancementContent> contents) {
        if (contents == null || contents.isEmpty()) {
            return Collections.emptyList();
        }
        List<Map<String, String>> result = new ArrayList<>();
        for (AiResourceIndexEnhancementContent content : contents) {
            if (content == null || result.size() >= MAX_PROMPT_CONTENTS) {
                continue;
            }
            Map<String, String> item = new LinkedHashMap<>();
            item.put("path", content.getPath());
            item.put("text", content.getText());
            result.add(item);
        }
        return result;
    }
    
    private Map<String, String> message(String role, String content) {
        Map<String, String> message = new LinkedHashMap<>();
        message.put("role", role);
        message.put("content", content);
        return message;
    }
    
    List<AiResourceIndexEnhancementChunk> parseEnhancementContent(String content, String model) {
        return parseEnhancementContent(content, model, maxItems());
    }
    
    private List<AiResourceIndexEnhancementChunk> parseEnhancementContent(String content,
        String model, int maxItems) {
        if (StringUtils.isBlank(content)) {
            return Collections.emptyList();
        }
        Map<String, Object> parsed = JacksonUtils.toObj(extractJson(content), MAP_TYPE);
        if (parsed == null || parsed.isEmpty()) {
            return Collections.emptyList();
        }
        String metadata = metadata(model);
        List<AiResourceIndexEnhancementChunk> chunks = new ArrayList<>();
        addValue(chunks, AiResourceSearchConstants.CHUNK_TYPE_AI_SUMMARY, parsed.get("summary"),
            metadata, maxItems);
        addValue(chunks, AiResourceSearchConstants.CHUNK_TYPE_SEARCH_INTENT,
            parsed.get("searchIntents"), metadata, maxItems);
        addValue(chunks, AiResourceSearchConstants.CHUNK_TYPE_SEARCH_TERM,
            parsed.get("searchTerms"), metadata, maxItems);
        return chunks;
    }
    
    private void addValue(List<AiResourceIndexEnhancementChunk> chunks, String chunkType,
        Object value,
        String metadata, int maxItems) {
        for (String text : toStringList(value, maxItems)) {
            if (StringUtils.isNotBlank(text)) {
                chunks.add(new AiResourceIndexEnhancementChunk(chunkType, text.trim(), metadata));
            }
        }
    }
    
    private List<String> toStringList(Object value, int maxItems) {
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
            return limitItems(result, maxItems);
        }
        if (value instanceof String && StringUtils.isNotBlank((String) value)) {
            return Collections.singletonList((String) value);
        }
        return Collections.singletonList(String.valueOf(value));
    }
    
    private List<String> limitItems(List<String> values, int maxItems) {
        if (values.size() <= maxItems) {
            return values;
        }
        return new ArrayList<>(values.subList(0, maxItems));
    }
    
    private String extractJson(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            int firstLineEnd = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstLineEnd >= 0 && lastFence > firstLineEnd) {
                trimmed = trimmed.substring(firstLineEnd + 1, lastFence).trim();
            }
        }
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }
    
    private String responseContent(String body) {
        Map<String, Object> root = JacksonUtils.toObj(body, MAP_TYPE);
        if (root == null || root.isEmpty()) {
            return body;
        }
        Object choices = root.get("choices");
        if (choices instanceof List && !((List<?>) choices).isEmpty()) {
            Object message = mapValue(((List<?>) choices).get(0), "message");
            Object content = mapValue(message, "content");
            if (content instanceof String && StringUtils.isNotBlank((String) content)) {
                return (String) content;
            }
        }
        return body;
    }
    
    private Object mapValue(Object value, String key) {
        if (value instanceof Map) {
            return ((Map<?, ?>) value).get(key);
        }
        return null;
    }
    
    private String metadata(String model) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("source", "llm");
        metadata.put("provider", "openai-compatible");
        metadata.put("model", model);
        return JacksonUtils.toJson(metadata);
    }
    
    private String limit(String text) {
        if (text == null || text.length() <= MAX_TEXT_LENGTH) {
            return text;
        }
        return text.substring(0, MAX_TEXT_LENGTH);
    }
    
    String errorMessage(int statusCode, String body) {
        String errorBody = errorBody(body);
        String message = "AI resource index enhancement LLM request failed, status=" + statusCode;
        if (StringUtils.isBlank(errorBody)) {
            return message;
        }
        return message + ", body=" + errorBody;
    }
    
    private String errorBody(String body) {
        if (StringUtils.isBlank(body)) {
            return "";
        }
        String normalized = body.replace('\n', ' ').replace('\r', ' ').trim();
        if (normalized.length() <= MAX_ERROR_BODY_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, MAX_ERROR_BODY_LENGTH);
    }
    
    private String endpoint() {
        return property(KEY_ENDPOINT, "");
    }
    
    String chatEndpoint(String endpoint) {
        String value = endpoint == null ? "" : endpoint.trim();
        if (StringUtils.isBlank(value) || value.endsWith("/chat/completions")) {
            return value;
        }
        if (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        if (value.endsWith("/v1")) {
            return value + "/chat/completions";
        }
        return value;
    }
    
    private String apiKey() {
        return property(KEY_API_KEY, "");
    }
    
    private String model() {
        return property(KEY_MODEL, "");
    }
    
    private int timeoutMs() {
        return positiveInt(KEY_TIMEOUT_MS, DEFAULT_TIMEOUT_MS);
    }
    
    private int maxItems() {
        return positiveInt(KEY_MAX_ITEMS, DEFAULT_MAX_ITEMS);
    }
    
    private int positiveInt(String key, int defaultValue) {
        String value = property(key, String.valueOf(defaultValue));
        try {
            return Integer.max(1, Integer.parseInt(value));
        } catch (Exception ignored) {
            return defaultValue;
        }
    }
    
    private String property(String key, String defaultValue) {
        try {
            return EnvUtil.getProperty(key, defaultValue);
        } catch (Exception ignored) {
            String value = System.getProperty(key);
            return StringUtils.isBlank(value) ? defaultValue : value;
        }
    }
    
    private EnhancementConfiguration configuration() {
        String configuredEndpoint = chatEndpoint(endpoint());
        String configuredModel = model();
        int configuredMaxItems = maxItems();
        return new EnhancementConfiguration(requested(), configuredEndpoint, apiKey(),
            configuredModel, timeoutMs(), configuredMaxItems,
            fingerprint(configuredEndpoint, configuredModel, configuredMaxItems));
    }
    
    private static final class EnhancementConfiguration {
        
        private final boolean requested;
        
        private final String endpoint;
        
        private final String apiKey;
        
        private final String model;
        
        private final int timeoutMs;
        
        private final int maxItems;
        
        private final String fingerprint;
        
        private EnhancementConfiguration(boolean requested, String endpoint, String apiKey,
            String model, int timeoutMs, int maxItems, String fingerprint) {
            this.requested = requested;
            this.endpoint = endpoint;
            this.apiKey = apiKey;
            this.model = model;
            this.timeoutMs = timeoutMs;
            this.maxItems = maxItems;
            this.fingerprint = fingerprint;
        }
        
        private boolean ready() {
            return requested && StringUtils.isNotBlank(endpoint) && StringUtils.isNotBlank(model);
        }
    }
}
