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
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JevRelevanceProviderTest {
    
    @Test
    void postsSystemOneContractAndRejectsMalformedAnswersWithoutRetry() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> answer =
            new AtomicReference<>("{\"answers\":{\"candidate_0\":{\"noul\":0.5}}}");
        AtomicReference<String> request = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicInteger status = new AtomicInteger(200);
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/v1/systemone", exchange -> {
            calls.incrementAndGet();
            request
                .set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = answer.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status.get(), body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            AiResourceRelevanceConfiguration configuration =
                AiResourceRelevanceFilterTest.configuration(
                    Map.of("endpoint",
                        "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/systemone"));
            JevRelevanceProvider provider = new JevRelevanceProvider();
            assertEquals(Map.of("candidate_0", 0.5), provider.evaluateBatch("query",
                AiResourceRelevanceFilterTest.candidates(1), configuration, deadline()));
            assertEquals("Bearer test-key", authorization.get());
            assertEquals("test-model", JacksonUtils.toObj(request.get()).path("model").asText());
            assertEquals("noul", JacksonUtils.toObj(request.get()).path("questions")
                .path("candidate_0").path("type").asText());
            assertTrue(JacksonUtils.toObj(request.get()).path("state").path("candidates")
                .has("candidate_0"));
            for (String invalid : new String[] {"{}", "{\"answers\":{}}",
                "{\"answers\":{\"candidate_0\":{\"noul\":1.1}}}",
                "{\"answers\":{\"candidate_0\":{\"noul\":\"0.5\"}}}",
                "{\"answers\":{\"unknown\":{\"noul\":0.5}}}",
                "{\"answers\":{\"candidate_0\":{\"noul\":0.5},\"candidate_0\":{\"noul\":0.8}}}"}) {
                answer.set(invalid);
                assertThrows(Exception.class, () -> provider.evaluateBatch("query",
                    AiResourceRelevanceFilterTest.candidates(1), configuration, deadline()));
            }
            status.set(429);
            int before = calls.get();
            assertThrows(Exception.class, () -> provider.evaluateBatch("query",
                AiResourceRelevanceFilterTest.candidates(1), configuration, deadline()));
            assertEquals(before + 1, calls.get());
        } finally {
            server.stop(0);
        }
    }
    
    private long deadline() {
        return System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
    }
}
