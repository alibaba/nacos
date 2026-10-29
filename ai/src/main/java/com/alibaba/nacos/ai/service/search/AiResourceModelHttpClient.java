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

import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Common authenticated JSON transport for model/API adapters. Callers own lifecycle and budgets.
 * @author nacos
 */
public final class AiResourceModelHttpClient {
    
    private AiResourceModelHttpClient() {
    }
    
    /**
     * Post JSON without retries. The caller owns response validation and safe diagnostics.
     * @param client reusable HTTP client
     * @param endpoint configured API endpoint
     * @param apiKey optional bearer credential
     * @param payload provider request
     * @param timeout remaining call budget
     * @return HTTP response
     * @throws Exception on transport failure or interruption
     */
    public static HttpResponse<String> post(HttpClient client, String endpoint, String apiKey,
        Object payload, Duration timeout) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(endpoint))
            .timeout(timeout).header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(JacksonUtils.toJson(payload),
                StandardCharsets.UTF_8));
        if (StringUtils.isNotBlank(apiKey)) {
            request.header("Authorization", "Bearer " + apiKey);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
    /** Sanitized boundary failure suitable for production diagnostics. */
    public static class HttpFailure extends Exception {
        
        private static final long serialVersionUID = 1L;
        final int status;
        final String requestId;
        
        public HttpFailure(int status, String requestId) {
            super("Model HTTP status=" + status);
            this.status = status;
            String safe = requestId.replaceAll("[^A-Za-z0-9_.:-]", "_");
            this.requestId = safe.substring(0, Math.min(safe.length(), 128));
        }
    }
    
}
