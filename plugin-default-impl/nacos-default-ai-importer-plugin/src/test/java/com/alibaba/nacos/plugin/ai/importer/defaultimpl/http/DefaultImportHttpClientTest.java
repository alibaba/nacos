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

package com.alibaba.nacos.plugin.ai.importer.defaultimpl.http;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.plugin.ai.importer.model.AiResourceImportSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLParameters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Unit tests for {@link DefaultImportHttpClient}.
 *
 * @author xiweng.yy
 */
@ExtendWith(MockitoExtension.class)
class DefaultImportHttpClientTest {
    
    @Mock
    private HttpClient httpClient;
    
    @BeforeEach
    void setUp() throws Exception {
        lenient().when(httpClient.send(any(HttpRequest.class),
            anyBodyHandler())).thenReturn(response(200, "ok"));
    }
    
    @Test
    void testRejectsHttpByDefault() {
        DefaultImportHttpClient client = newClient("93.184.216.34");
        
        assertThrows(NacosException.class,
            () -> client.get(source(1024), "http://registry.example.com/index.json", "*/*"));
        verifyNoInteractions(httpClient);
    }
    
    @Test
    void testRejectsResolvedPrivateAddressByDefault() {
        DefaultImportHttpClient client = newClient("127.0.0.1");
        
        assertThrows(NacosException.class,
            () -> client.get(source(1024), "https://registry.example.com/index.json", "*/*"));
        verifyNoInteractions(httpClient);
    }
    
    @Test
    void testAllowsPrivateAddressWhenSourceOptIn() throws Exception {
        DefaultImportHttpClient client = newClient("127.0.0.1");
        AiResourceImportSource source = source(1024);
        source.setProperties(Collections.singletonMap(
            DefaultImportHttpClient.PROPERTY_ALLOW_PRIVATE_NETWORK, "true"));
        
        ImportHttpResponse response =
            client.get(source, "https://registry.example.com/index.json", "*/*");
        
        assertEquals(200, response.getStatusCode());
        assertEquals("ok", new String(response.getBody(), StandardCharsets.UTF_8));
    }
    
    @Test
    void testAllowsHttpWhenCamelPropertyOptIn() throws Exception {
        DefaultImportHttpClient client = newClient("93.184.216.34");
        AiResourceImportSource source = source(1024);
        source.setProperties(Collections.singletonMap(
            DefaultImportHttpClient.PROPERTY_ALLOW_HTTP_CAMEL, "true"));
        
        ImportHttpResponse response =
            client.get(source, "http://registry.example.com/index.json", 3, "application/json");
        
        assertEquals(200, response.getStatusCode());
        assertEquals("ok", new String(response.getBody(), StandardCharsets.UTF_8));
        org.mockito.Mockito.verify(httpClient).send(argThat(
            request -> request.timeout().orElseThrow().getSeconds() == 3
                && request.headers().firstValue("Accept").orElse("").equals("application/json")),
            anyBodyHandler());
    }
    
    @Test
    void testRejectsOversizedResponse() throws Exception {
        lenient().when(httpClient.send(any(HttpRequest.class),
            anyBodyHandler())).thenReturn(response(200, "toolong"));
        DefaultImportHttpClient client = newClient("93.184.216.34");
        
        assertThrows(NacosException.class,
            () -> client.get(source(2), "https://registry.example.com/index.json", "*/*"));
    }
    
    @Test
    void testRejectsBlankUrl() {
        DefaultImportHttpClient client = newClient("93.184.216.34");
        
        assertThrows(NacosException.class, () -> client.get(source(1024), " ", "*/*"));
        verifyNoInteractions(httpClient);
    }
    
    @Test
    void testRejectsRelativeUrl() {
        DefaultImportHttpClient client = newClient("93.184.216.34");
        
        assertThrows(NacosException.class, () -> client.get(source(1024), "/index.json", "*/*"));
        verifyNoInteractions(httpClient);
    }
    
    @Test
    void testRejectsMalformedUrl() {
        DefaultImportHttpClient client = newClient("93.184.216.34");
        
        assertThrows(NacosException.class,
            () -> client.get(source(1024), "https://[bad", "*/*"));
        verifyNoInteractions(httpClient);
    }
    
    @Test
    void testRejectsUnsupportedScheme() {
        DefaultImportHttpClient client = newClient("93.184.216.34");
        
        assertThrows(NacosException.class,
            () -> client.get(source(1024), "ftp://registry.example.com/index.json", "*/*"));
        verifyNoInteractions(httpClient);
    }
    
    @Test
    void testRejectsHttpWhenSourceIsNull() {
        DefaultImportHttpClient client = newClient("93.184.216.34");
        
        assertThrows(NacosException.class,
            () -> client.get(null, "http://registry.example.com/index.json", "*/*"));
        verifyNoInteractions(httpClient);
    }
    
    @Test
    void testRejectsMissingHost() {
        DefaultImportHttpClient client = newClient("93.184.216.34");
        
        assertThrows(NacosException.class,
            () -> client.get(source(1024), "https:///index.json", "*/*"));
        verifyNoInteractions(httpClient);
    }
    
    @Test
    void testRejectsLocalhostWithoutResolvingDns() {
        DefaultImportHttpClient client = new DefaultImportHttpClient(httpClient, host -> {
            throw new AssertionError("localhost should be rejected before DNS lookup");
        });
        
        assertThrows(NacosException.class,
            () -> client.get(source(1024), "https://api.localhost/index.json", "*/*"));
        verifyNoInteractions(httpClient);
    }
    
    @Test
    void testRejectsUnknownHost() {
        DefaultImportHttpClient client = new DefaultImportHttpClient(httpClient, host -> {
            throw new UnknownHostException(host);
        });
        
        assertThrows(NacosException.class,
            () -> client.get(source(1024), "https://registry.example.com/index.json", "*/*"));
        verifyNoInteractions(httpClient);
    }
    
    @Test
    void testRejectsEmptyDnsResult() {
        DefaultImportHttpClient client =
            new DefaultImportHttpClient(httpClient, host -> new InetAddress[0]);
        
        assertThrows(NacosException.class,
            () -> client.get(source(1024), "https://registry.example.com/index.json", "*/*"));
        verifyNoInteractions(httpClient);
    }
    
    @Test
    void testUsesDefaultMaxResponseBytesWhenSourceNull() throws Exception {
        DefaultImportHttpClient client = newClient("93.184.216.34");
        
        ImportHttpResponse response =
            client.get(null, "https://registry.example.com/index.json", null);
        
        assertEquals(200, response.getStatusCode());
        assertEquals("ok", new String(response.getBody(), StandardCharsets.UTF_8));
    }
    
    @Test
    void testLimitedBodyHandlerReadsChunks() throws Exception {
        DefaultImportHttpClient client =
            new DefaultImportHttpClient(new BodyHandlerHttpClient("ok", false),
                host -> new InetAddress[] {InetAddress.getByName("93.184.216.34")});
        
        ImportHttpResponse response =
            client.get(source(1024), "https://registry.example.com/index.json", "*/*");
        
        assertEquals(200, response.getStatusCode());
        assertEquals("ok", new String(response.getBody(), StandardCharsets.UTF_8));
    }
    
    @Test
    void testLimitedBodyHandlerRejectsOversizedChunks() {
        DefaultImportHttpClient client =
            new DefaultImportHttpClient(new BodyHandlerHttpClient("toolong", false),
                host -> new InetAddress[] {InetAddress.getByName("93.184.216.34")});
        
        assertThrows(Exception.class,
            () -> client.get(source(2), "https://registry.example.com/index.json", "*/*"));
    }
    
    @Test
    void testLimitedBodyHandlerIgnoresChunksAfterError() {
        DefaultImportHttpClient client =
            new DefaultImportHttpClient(new BodyHandlerHttpClient("ok", true),
                host -> new InetAddress[] {InetAddress.getByName("93.184.216.34")});
        
        assertThrows(Exception.class,
            () -> client.get(source(1024), "https://registry.example.com/index.json", "*/*"));
    }
    
    private DefaultImportHttpClient newClient(String address) {
        return new DefaultImportHttpClient(httpClient,
            host -> new InetAddress[] {InetAddress.getByName(address)});
    }
    
    private AiResourceImportSource source(long maxResponseSize) {
        AiResourceImportSource source = new AiResourceImportSource();
        source.setMaxArtifactSize(maxResponseSize);
        return source;
    }
    
    private HttpResponse.BodyHandler<byte[]> anyBodyHandler() {
        return any();
    }
    
    private HttpResponse<byte[]> response(int status, String body) {
        Map<String, java.util.List<String>> headers = new HashMap<>(1);
        headers.put("Content-Type", Collections.singletonList("application/json"));
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        return response(status, bytes, headers);
    }
    
    private HttpResponse<byte[]> response(int status, byte[] bytes,
        Map<String, java.util.List<String>> headers) {
        return new HttpResponse<>() {
            
            @Override
            public int statusCode() {
                return status;
            }
            
            @Override
            public HttpRequest request() {
                return null;
            }
            
            @Override
            public Optional<HttpResponse<byte[]>> previousResponse() {
                return Optional.empty();
            }
            
            @Override
            public HttpHeaders headers() {
                return HttpHeaders.of(headers, (key, value) -> true);
            }
            
            @Override
            public byte[] body() {
                return bytes;
            }
            
            @Override
            public Optional<SSLSession> sslSession() {
                return Optional.empty();
            }
            
            @Override
            public URI uri() {
                return null;
            }
            
            @Override
            public HttpClient.Version version() {
                return HttpClient.Version.HTTP_1_1;
            }
        };
    }
    
    private class BodyHandlerHttpClient extends HttpClient {
        
        private final String body;
        
        private final boolean completeWithError;
        
        BodyHandlerHttpClient(String body, boolean completeWithError) {
            this.body = body;
            this.completeWithError = completeWithError;
        }
        
        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }
        
        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.empty();
        }
        
        @Override
        public Redirect followRedirects() {
            return Redirect.NEVER;
        }
        
        @Override
        public Optional<ProxySelector> proxy() {
            return Optional.empty();
        }
        
        @Override
        public SSLContext sslContext() {
            return null;
        }
        
        @Override
        public SSLParameters sslParameters() {
            return null;
        }
        
        @Override
        public Optional<Authenticator> authenticator() {
            return Optional.empty();
        }
        
        @Override
        public Version version() {
            return Version.HTTP_1_1;
        }
        
        @Override
        public Optional<Executor> executor() {
            return Optional.empty();
        }
        
        @Override
        @SuppressWarnings("unchecked")
        public <T> HttpResponse<T> send(HttpRequest request,
            HttpResponse.BodyHandler<T> responseBodyHandler) throws java.io.IOException,
            InterruptedException {
            HttpResponse.BodySubscriber<T> subscriber = responseBodyHandler.apply(
                new HttpResponse.ResponseInfo() {
                    
                    @Override
                    public int statusCode() {
                        return 200;
                    }
                    
                    @Override
                    public HttpHeaders headers() {
                        return HttpHeaders.of(Collections.emptyMap(), (key, value) -> true);
                    }
                    
                    @Override
                    public Version version() {
                        return Version.HTTP_1_1;
                    }
                });
            subscriber.onSubscribe(new Flow.Subscription() {
                
                @Override
                public void request(long n) {
                }
                
                @Override
                public void cancel() {
                }
            });
            if (completeWithError) {
                subscriber.onError(new IllegalStateException("failed"));
                subscriber.onNext(Collections.singletonList(ByteBuffer.wrap(new byte[] {'x'})));
            } else {
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                List<ByteBuffer> chunks = Arrays.asList(ByteBuffer.wrap(bytes, 0, 1),
                    ByteBuffer.wrap(bytes, 1, bytes.length - 1));
                subscriber.onNext(chunks);
                subscriber.onComplete();
            }
            T responseBody = subscriber.getBody().toCompletableFuture().join();
            Map<String, java.util.List<String>> headers = new HashMap<>(1);
            headers.put("Content-Type", Collections.singletonList("application/json"));
            return (HttpResponse<T>) response(200, (byte[]) responseBody, headers);
        }
        
        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
            HttpResponse.BodyHandler<T> responseBodyHandler) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException());
        }
        
        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
            HttpResponse.BodyHandler<T> responseBodyHandler,
            HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException());
        }
    }
}
