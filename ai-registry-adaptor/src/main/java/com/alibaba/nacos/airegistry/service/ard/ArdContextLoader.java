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

package com.alibaba.nacos.airegistry.service.ard;

import com.alibaba.nacos.airegistry.constant.ArdProtocolConstants;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.apicatalog.jsonld.JsonLdError;
import com.apicatalog.jsonld.JsonLdErrorCode;
import com.apicatalog.jsonld.document.Document;
import com.apicatalog.jsonld.document.JsonDocument;
import com.apicatalog.jsonld.loader.DocumentLoader;
import com.apicatalog.jsonld.loader.DocumentLoaderOptions;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeoutException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Deployment-controlled context loading without ambient request credentials.
 *
 * @author nacos
 */
final class ArdContextLoader {
    
    private static final String PREFIX = "nacos.ai.ard.context.";
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NEVER).build();
    private final Set<String> allowedUris;
    private final int maxBytes;
    private final int timeoutMillis;
    
    ArdContextLoader(Set<String> allowedUris, int maxBytes, int timeoutMillis) {
        if (maxBytes <= 0 || timeoutMillis <= 0) {
            throw new IllegalArgumentException("ARD context budgets must be positive");
        }
        this.allowedUris = Set.copyOf(allowedUris);
        this.maxBytes = maxBytes;
        this.timeoutMillis = timeoutMillis;
    }
    
    static ArdContextLoader fromEnvironment() {
        Set<String> allowed = Arrays.stream(property("allowed-uris", "").split(","))
            .map(String::trim).filter(value -> !value.isEmpty()).collect(Collectors.toSet());
        return new ArdContextLoader(allowed, Integer.parseInt(property("max-bytes", "1048576")),
            Integer.parseInt(property("timeout-ms", "3000")));
    }
    
    Session newSession(JsonObject baseDocument) {
        return new Session(baseDocument);
    }
    
    private static String property(String key, String defaultValue) {
        String value = System.getProperty(PREFIX + key);
        if (value != null) {
            return value;
        }
        return EnvUtil.getEnvironment() == null ? defaultValue
            : EnvUtil.getProperty(PREFIX + key, defaultValue);
    }
    
    final class Session implements DocumentLoader {
        
        private final JsonObject baseDocument;
        private final Map<String, Document> documents = new HashMap<>();
        private long remainingBytes = maxBytes;
        private long remainingNanos = TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        
        private Session(JsonObject baseDocument) {
            this.baseDocument = baseDocument;
        }
        
        void consume(int bytes) {
            remainingBytes -= bytes;
            if (remainingBytes < 0) {
                throw new IllegalArgumentException("ARD context byte budget exceeded");
            }
        }
        
        @Override
        public Document loadDocument(URI uri, DocumentLoaderOptions options) throws JsonLdError {
            String key = uri.toString();
            if (documents.containsKey(key)) {
                return documents.get(key);
            }
            try {
                JsonObject content;
                if (ArdProtocolConstants.BASE_CONTEXT.equals(key)) {
                    content = baseDocument;
                } else {
                    checkAllowed(uri);
                    try (JsonReader reader =
                        Json.createReader(new ByteArrayInputStream(download(uri)))) {
                        content = reader.readObject();
                    }
                    if (!content.containsKey("@context")) {
                        throw new IllegalArgumentException("Remote document has no @context");
                    }
                    content = Json.createObjectBuilder(content)
                        .add("@context", ArdContextResolver.preserveBase(content.get("@context")))
                        .build();
                }
                JsonDocument document = JsonDocument.of(content);
                document.setDocumentUrl(uri);
                documents.put(key, document);
                return document;
            } catch (IOException | RuntimeException e) {
                throw new JsonLdError(JsonLdErrorCode.LOADING_DOCUMENT_FAILED,
                    "Unable to load allowed ARD context within deployment policy", e);
            }
        }
        
        private void checkAllowed(URI uri) {
            if (!allowedUris.contains(uri.toString()) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getFragment() != null
                || !("https".equalsIgnoreCase(uri.getScheme())
                    || "http".equalsIgnoreCase(uri.getScheme()))) {
                throw new IllegalArgumentException(
                    "ARD context URI is not allowed by deployment policy");
            }
        }
        
        private byte[] download(URI uri) throws IOException {
            if (remainingNanos <= 0) {
                throw new IOException("ARD context remote-fetch time budget exceeded");
            }
            long start = System.nanoTime();
            HttpRequest request =
                HttpRequest.newBuilder(uri).timeout(Duration.ofNanos(remainingNanos))
                    .header("Accept", "application/ld+json, application/json")
                    .header("Accept-Encoding", "identity").GET().build();
            CompletableFuture<HttpResponse<byte[]>> response = HTTP_CLIENT.sendAsync(request,
                info -> new BoundedBodySubscriber(remainingBytes));
            try {
                HttpResponse<byte[]> result = response.get(remainingNanos, TimeUnit.NANOSECONDS);
                if (result.statusCode() != 200) {
                    throw new IOException("ARD context HTTP response is not 200");
                }
                consume(result.body().length);
                return result.body();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("ARD context loading interrupted", e);
            } catch (ExecutionException | TimeoutException e) {
                throw new IOException("ARD context loading failed or exceeded deployment budgets",
                    e);
            } finally {
                remainingNanos -= System.nanoTime() - start;
                response.cancel(true);
            }
        }
    }
    
    private static final class BoundedBodySubscriber
        implements HttpResponse.BodySubscriber<byte[]> {
        
        private final HttpResponse.BodySubscriber<byte[]> delegate =
            HttpResponse.BodySubscribers.ofByteArray();
        private final long limit;
        private long received;
        private Flow.Subscription subscription;
        
        private BoundedBodySubscriber(long limit) {
            this.limit = limit;
        }
        
        @Override
        public CompletionStage<byte[]> getBody() {
            return delegate.getBody();
        }
        
        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            delegate.onSubscribe(subscription);
        }
        
        @Override
        public void onNext(List<ByteBuffer> items) {
            for (ByteBuffer item : items) {
                received += item.remaining();
            }
            if (received > limit) {
                subscription.cancel();
                delegate.onError(new IOException("ARD context byte budget exceeded"));
                return;
            }
            delegate.onNext(items);
        }
        
        @Override
        public void onError(Throwable throwable) {
            delegate.onError(throwable);
        }
        
        @Override
        public void onComplete() {
            delegate.onComplete();
        }
    }
}
