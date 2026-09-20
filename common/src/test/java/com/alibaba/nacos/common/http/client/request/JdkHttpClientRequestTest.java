/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.common.http.client.request;

import com.alibaba.nacos.common.http.HttpClientConfig;
import com.alibaba.nacos.common.http.HttpUtils;
import com.alibaba.nacos.common.http.client.response.HttpClientResponse;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.MediaType;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.model.RequestHttpEntity;
import com.alibaba.nacos.common.utils.IoUtils;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JdkHttpClientRequestTest {
    
    JdkHttpClientRequest httpClientRequest;
    
    @Mock
    private HttpURLConnection connection;
    
    @Mock
    private URI uri;
    
    @Mock
    private URL url;
    
    @Mock
    private OutputStream outputStream;
    
    private HttpClientConfig httpClientConfig;
    
    @TempDir
    Path tempDir;
    
    @BeforeEach
    void setUp() throws Exception {
        when(uri.toURL()).thenReturn(url);
        when(url.openConnection()).thenReturn(connection);
        when(connection.getOutputStream()).thenReturn(outputStream);
        httpClientConfig = HttpClientConfig.builder().build();
        httpClientRequest = new JdkHttpClientRequest(httpClientConfig);
    }
    
    @AfterEach
    void tearDown() throws Exception {
        httpClientRequest.close();
    }
    
    @Test
    void testExecuteNormal() throws Exception {
        Header header = Header.newInstance();
        HttpClientConfig config = HttpClientConfig.builder().build();
        RequestHttpEntity httpEntity =
            new RequestHttpEntity(config, header, Query.EMPTY, "a=bo&dy");
        HttpClientResponse response = httpClientRequest.execute(uri, "GET", httpEntity);
        byte[] writeBytes = "a=bo&dy".getBytes(StandardCharsets.UTF_8);
        verify(outputStream).write(writeBytes, 0, writeBytes.length);
        assertEquals(connection, getActualConnection(response));
    }
    
    @Test
    void testExecuteForm() throws Exception {
        Header header = Header.newInstance();
        header.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpClientConfig config = HttpClientConfig.builder().build();
        Map<String, String> body = new HashMap<>();
        body.put("a", "bo&dy");
        RequestHttpEntity httpEntity = new RequestHttpEntity(config, header, Query.EMPTY, body);
        HttpClientResponse response = httpClientRequest.execute(uri, "GET", httpEntity);
        byte[] writeBytes = HttpUtils.encodingParams(body, StandardCharsets.UTF_8.name())
            .getBytes(StandardCharsets.UTF_8);
        verify(outputStream).write(writeBytes, 0, writeBytes.length);
        assertEquals(connection, getActualConnection(response));
    }
    
    @Test
    void testExecuteEmptyBody() throws Exception {
        Header header = Header.newInstance();
        RequestHttpEntity httpEntity = new RequestHttpEntity(header, Query.EMPTY);
        HttpClientResponse response = httpClientRequest.execute(uri, "GET", httpEntity);
        verify(outputStream, never()).write(any(), eq(0), anyInt());
        assertEquals(connection, getActualConnection(response));
    }
    
    @Test
    void testExecuteDisconnectsOnOutputStreamException() throws Exception {
        when(connection.getOutputStream()).thenThrow(new IOException("output stream error"));
        Header header = Header.newInstance();
        HttpClientConfig config = HttpClientConfig.builder().build();
        RequestHttpEntity httpEntity = new RequestHttpEntity(config, header, Query.EMPTY, "body");
        assertThrows(IOException.class, () -> httpClientRequest.execute(uri, "GET", httpEntity));
        verify(connection).disconnect();
    }
    
    @Test
    void testExecuteDisconnectsOnConnectException() throws Exception {
        doThrow(new IOException("connect error")).when(connection).connect();
        Header header = Header.newInstance();
        RequestHttpEntity httpEntity = new RequestHttpEntity(header, Query.EMPTY);
        assertThrows(IOException.class, () -> httpClientRequest.execute(uri, "GET", httpEntity));
        verify(connection).disconnect();
    }
    
    @Test
    void testExecuteNoDisconnectOnSuccess() throws Exception {
        Header header = Header.newInstance();
        RequestHttpEntity httpEntity = new RequestHttpEntity(header, Query.EMPTY);
        httpClientRequest.execute(uri, "GET", httpEntity);
        verify(connection, never()).disconnect();
    }
    
    @Test
    @DisplayName("setSslContext should set SSL socket factory on HttpsURLConnection")
    void testSetSslContextShouldSetContext() throws Exception {
        javax.net.ssl.SSLContext sslContext = javax.net.ssl.SSLContext.getInstance("TLS");
        sslContext.init(null, null, new java.security.SecureRandom());
        httpClientRequest.setSslContext(sslContext);
    }
    
    @Test
    @DisplayName("setSslContext with null should not throw")
    void testSetSslContextWithNullShouldNotThrow() {
        httpClientRequest.setSslContext(null);
    }
    
    @Test
    @DisplayName("replaceSslHostnameVerifier should set hostname verifier on HttpsURLConnection")
    void testReplaceSslHostnameVerifierShouldReplace() {
        javax.net.ssl.HostnameVerifier verifier = (hostname, session) -> true;
        httpClientRequest.replaceSslHostnameVerifier(verifier);
    }
    
    @Test
    @DisplayName("replaceSslHostnameVerifier with null should not throw")
    void testReplaceSslHostnameVerifierWithNullShouldNotThrow() {
        httpClientRequest.replaceSslHostnameVerifier(null);
    }
    
    @Test
    @DisplayName("execute with File body should handle file upload")
    void testExecuteFileUpload() throws Exception {
        File testFile = tempDir.resolve("test-upload.txt").toFile();
        Files.write(testFile.toPath(), "test file content".getBytes(StandardCharsets.UTF_8));
        
        Header header = Header.newInstance();
        HttpClientConfig config = HttpClientConfig.builder().build();
        RequestHttpEntity httpEntity = new RequestHttpEntity(config, header, Query.EMPTY, testFile);
        
        HttpClientResponse response = httpClientRequest.execute(uri, "POST", httpEntity);
        
        verify(connection, atLeast(1)).setDoOutput(true);
        verify(outputStream, atLeast(1)).flush();
        assertNotNull(response);
    }
    
    @Test
    @DisplayName("execute with File body should not fall into else-if branch")
    void testExecuteFileBody() throws Exception {
        File tempFile = File.createTempFile("test", ".txt");
        tempFile.deleteOnExit();
        Files.write(tempFile.toPath(), "test content".getBytes(StandardCharsets.UTF_8));
        
        Header header = Header.newInstance();
        HttpClientConfig config = HttpClientConfig.builder().build();
        RequestHttpEntity httpEntity = new RequestHttpEntity(config, header, Query.EMPTY, tempFile);
        HttpClientResponse response = httpClientRequest.execute(uri, "POST", httpEntity);
        
        // handleFileUpload will set multipart Content-Type
        verify(connection).setRequestProperty(eq("Content-Type"),
            startsWith("multipart/form-data; boundary="));
        // should not fall into else-if branch, so Content-Length will not be set
        verify(connection, never()).setRequestProperty(eq("Content-Length"), anyString());
        assertEquals(connection, getActualConnection(response));
        tempFile.delete();
    }
    
    @Test
    @DisplayName("execute with empty string body should not write to output")
    void testExecuteEmptyStringBody() throws Exception {
        Header header = Header.newInstance();
        HttpClientConfig config = HttpClientConfig.builder().build();
        RequestHttpEntity httpEntity = new RequestHttpEntity(config, header, Query.EMPTY, "");
        
        HttpClientResponse response = httpClientRequest.execute(uri, "GET", httpEntity);
        
        verify(outputStream, never()).write(any(), eq(0), anyInt());
        assertNotNull(response);
    }
    
    @Test
    @DisplayName("execute with object body should serialize to JSON")
    void testExecuteObjectBody() throws Exception {
        Header header = Header.newInstance();
        HttpClientConfig config = HttpClientConfig.builder().build();
        
        Map<String, Object> bodyObject = new HashMap<>();
        bodyObject.put("key", "value");
        
        RequestHttpEntity httpEntity =
            new RequestHttpEntity(config, header, Query.EMPTY, bodyObject);
        
        HttpClientResponse response = httpClientRequest.execute(uri, "POST", httpEntity);
        
        verify(outputStream).write(any(byte[].class), eq(0), anyInt());
        assertNotNull(response);
    }
    
    @Test
    @DisplayName("replaceDefaultConfig should update httpClientConfig when config provided")
    void testReplaceDefaultConfig() throws Exception {
        HttpClientConfig newConfig = HttpClientConfig.builder()
            .setConTimeOutMillis(5000)
            .setReadTimeOutMillis(10000)
            .build();
        
        Header header = Header.newInstance();
        RequestHttpEntity httpEntity =
            new RequestHttpEntity(newConfig, header, Query.EMPTY, "body");
        
        httpClientRequest.execute(uri, "GET", httpEntity);
        
        verify(connection).setConnectTimeout(5000);
        verify(connection).setReadTimeout(10000);
    }
    
    @Test
    @DisplayName("execute with headers should set all header properties")
    void testExecuteWithHeaders() throws Exception {
        Header header = Header.newInstance();
        header.addParam("X-Custom-Header", "custom-value");
        header.addParam("Authorization", "Bearer token");
        
        RequestHttpEntity httpEntity = new RequestHttpEntity(header, Query.EMPTY);
        
        HttpClientResponse response = httpClientRequest.execute(uri, "GET", httpEntity);
        
        verify(connection).setRequestProperty("X-Custom-Header", "custom-value");
        verify(connection).setRequestProperty("Authorization", "Bearer token");
        assertNotNull(response);
    }
    
    private HttpURLConnection getActualConnection(HttpClientResponse actual)
        throws IllegalAccessException, NoSuchFieldException {
        Field field = actual.getClass().getDeclaredField("conn");
        field.setAccessible(true);
        return (HttpURLConnection) field.get(actual);
    }
    
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testResponseLossOnlyReplaysRepeatableBodies(boolean repeatable) throws Exception {
        AtomicInteger received = new AtomicInteger();
        HttpServer server = HttpServer.create(
            new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/publish", exchange -> {
            IoUtils.toString(exchange.getRequestBody(), "UTF-8");
            if (received.incrementAndGet() > 1) {
                byte[] response = "ok".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
            }
            exchange.close();
        });
        server.start();
        try (JdkHttpClientRequest request = new JdkHttpClientRequest(HttpClientConfig.builder()
            .setConTimeOutMillis(2000).setReadTimeOutMillis(2000).build())) {
            RequestHttpEntity entity = new RequestHttpEntity(null,
                Header.newInstance().setContentType(MediaType.APPLICATION_FORM_URLENCODED),
                null, Collections.singletonMap("name", "Agent & 中文"), repeatable);
            HttpClientResponse response = request.execute(URI.create("http://127.0.0.1:"
                + server.getAddress().getPort() + "/publish"), "POST", entity);
            try {
                if (repeatable) {
                    assertEquals(200, response.getStatusCode());
                    assertEquals(2, received.get());
                } else {
                    assertThrows(IOException.class, response::getStatusCode);
                    assertEquals(1, received.get());
                }
            } finally {
                response.close();
            }
        } finally {
            server.stop(0);
        }
    }
    
    @Test
    void testNonRepeatableFileLengthIncludesMultipartFramingAndUtf8Bytes() throws Exception {
        File file = tempDir.resolve("agent.txt").toFile();
        byte[] content = "Agent 中文".getBytes(StandardCharsets.UTF_8);
        Files.write(file.toPath(), content);
        httpClientRequest.execute(uri, "POST", new RequestHttpEntity(null, Header.newInstance(),
            Query.EMPTY, file, false));
        ArgumentCaptor<byte[]> parts =
            ArgumentCaptor.forClass(byte[].class);
        verify(outputStream, times(3)).write(parts.capture());
        long length = parts.getAllValues().stream().mapToLong(bytes -> bytes.length).sum();
        verify(connection).setFixedLengthStreamingMode(length);
        assertArrayEquals(content, parts.getAllValues().get(1));
        verify(outputStream).flush();
        verify(outputStream).close();
    }
    
    @Test
    void testNonRepeatableBodyUsesEncodedByteLengthWithoutChangingNextRequest() throws Exception {
        Header header = Header.newInstance().setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        Map<String, String> form = Collections.singletonMap("name", "中文 & agent");
        RequestHttpEntity entity = new RequestHttpEntity(null, header, null, form, false);
        httpClientRequest.execute(uri, "POST", entity);
        int length = HttpUtils.encodingParams(form, StandardCharsets.UTF_8.name())
            .getBytes(StandardCharsets.UTF_8).length;
        verify(connection).setFixedLengthStreamingMode(length);
        clearInvocations(connection);
        httpClientRequest.execute(uri, "POST", new RequestHttpEntity(header, form));
        verify(connection, never()).setFixedLengthStreamingMode(anyInt());
    }
    
}
