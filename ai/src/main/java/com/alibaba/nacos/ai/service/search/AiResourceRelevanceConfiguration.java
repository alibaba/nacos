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
import com.alibaba.nacos.sys.env.EnvUtil;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.function.Function;

/**
 * Startup configuration for online filtering, independent of offline index enrichment.
 * @author nacos
 */
@Component
@ConditionalOnAiResourceSearchEnabled
public class AiResourceRelevanceConfiguration {
    
    static final String PREFIX = "nacos.ai.resource.search.relevance.";
    final boolean enabled;
    final String provider;
    final String endpoint;
    final String apiKey;
    final String model;
    final double threshold;
    final int timeoutMs;
    final int maxCandidates;
    final int batchSize;
    final int batchConcurrency;
    final int maxConcurrency;
    
    public AiResourceRelevanceConfiguration() {
        this(key -> EnvUtil.getProperty(key));
    }
    
    AiResourceRelevanceConfiguration(Function<String, String> properties) {
        enabled = Boolean.parseBoolean(properties.apply("nacos.ai.ard.search.enhancement.enabled"));
        provider = property(properties, "provider", "jev");
        endpoint = property(properties, "endpoint", "");
        apiKey = property(properties, "api-key", "");
        model = property(properties, "model", "");
        threshold =
            enabled ? Double.parseDouble(property(properties, "threshold", "0.50")) : Double.NaN;
        timeoutMs = positive(properties, "timeout-ms", 3000);
        maxCandidates = positive(properties, "max-candidates", 60);
        batchSize = positive(properties, "batch-size", 60);
        batchConcurrency = positive(properties, "batch-concurrency", 1);
        maxConcurrency = positive(properties, "max-concurrency", 8);
        if (batchConcurrency > maxConcurrency) {
            throw new IllegalArgumentException(
                "Relevance batch-concurrency exceeds max-concurrency");
        }
        if (enabled) {
            URI uri = URI.create(endpoint);
            if (apiKey.isBlank() || model.isBlank() || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                || !("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))
                || !Double.isFinite(threshold) || threshold < 0 || threshold > 1) {
                throw new IllegalArgumentException(
                    "Incomplete or invalid relevance provider configuration");
            }
        }
    }
    
    private static String property(Function<String, String> properties, String name,
        String fallback) {
        String value = properties.apply(PREFIX + name);
        return value == null ? fallback : value;
    }
    
    private static int positive(Function<String, String> properties, String name, int fallback) {
        int value = Integer.parseInt(property(properties, name, Integer.toString(fallback)));
        if (value <= 0) {
            throw new IllegalArgumentException("Relevance " + name + " must be positive");
        }
        return value;
    }
    
    public String getEndpoint() {
        return endpoint;
    }
    
    public String getApiKey() {
        return apiKey;
    }
    
    public String getModel() {
        return model;
    }
    
    String fingerprint(String contract) {
        return digest(String.join("\n", provider, endpoint, model, contract,
            Double.toString(threshold), Integer.toString(maxCandidates),
            Integer.toString(batchSize),
            Integer.toString(batchConcurrency), Integer.toString(timeoutMs)));
    }
    
    static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
