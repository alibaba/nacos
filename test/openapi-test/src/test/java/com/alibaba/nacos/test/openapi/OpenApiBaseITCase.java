/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.test.openapi;

import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.alibaba.nacos.common.http.client.request.DefaultHttpClientRequest;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shared standalone-server OpenAPI integration test infrastructure.
 *
 * @author xiweng.yy
 */
public abstract class OpenApiBaseITCase {
    
    protected static final String NACOS_HOST = System.getProperty("nacos.host", "127.0.0.1");
    
    protected static final String NACOS_PORT = System.getProperty("nacos.port", "8848");
    
    protected static final String BASE_URL = "http://" + NACOS_HOST + ":" + NACOS_PORT;
    
    protected CloseableHttpClient httpClient;
    
    protected NacosRestTemplate nacosRestTemplate;
    
    private final Deque<CleanupAction> cleanupActions = new ArrayDeque<>();
    
    private Logger logger;
    
    @BeforeEach
    public void setUpOpenApiBase() throws Exception {
        logger = LoggerFactory.getLogger(getClass());
        httpClient = HttpClientBuilder.create().disableAutomaticRetries().build();
        nacosRestTemplate = new NacosRestTemplate(logger,
                new DefaultHttpClientRequest(httpClient, RequestConfig.DEFAULT));
    }
    
    @AfterEach
    public void tearDownOpenApiBase() throws Exception {
        Exception failure = runCleanupActions();
        failure = closeRestTemplate(failure);
        failure = closeHttpClient(failure);
        if (null != failure) {
            throw failure;
        }
    }
    
    protected Logger logger() {
        return logger;
    }
    
    protected void addCleanup(CleanupAction cleanupAction) {
        cleanupActions.addLast(cleanupAction);
    }
    
    protected static String nacosPath(String apiPath) {
        return "/nacos" + apiPath;
    }
    
    protected static String url(String path) {
        return BASE_URL + path;
    }

    protected String baseUrl() {
        return BASE_URL;
    }

    protected String requestUrl(String path) {
        return baseUrl() + path;
    }
    
    protected HttpResponse getRaw(String pathAndQuery) throws Exception {
        return executeRaw(new HttpGet(requestUrl(pathAndQuery)));
    }
    
    protected HttpResponse getRaw(String path, Query query) throws Exception {
        return getRaw(path + "?" + query.toQueryUrl());
    }
    
    protected ByteResponse getRawBytes(String path, Query query) throws Exception {
        return executeRawBytes(new HttpGet(requestUrl(path + "?" + query.toQueryUrl())));
    }
    
    protected HttpResponse postRaw(String path, Query query) throws Exception {
        return executeRaw(new HttpPost(requestUrl(path + "?" + query.toQueryUrl())));
    }
    
    protected HttpResponse putRaw(String path, Query query) throws Exception {
        return executeRaw(new HttpPut(requestUrl(path + "?" + query.toQueryUrl())));
    }
    
    protected HttpResponse deleteRaw(String path, Query query) throws Exception {
        return executeRaw(new HttpDelete(requestUrl(path + "?" + query.toQueryUrl())));
    }
    
    protected JsonNode getJsonOk(String path, Query query) throws Exception {
        HttpRestResult<String> restResult = nacosRestTemplate.get(requestUrl(path), Header.EMPTY, query, String.class);
        assertTrue(restResult.ok(), "HTTP status should be 2xx, code=" + restResult.getCode() + ", body="
                + restResult.getData() + ", message=" + restResult.getMessage());
        JsonNode root = JacksonUtils.toObj(restResult.getData());
        assertSuccess(root);
        return root;
    }

    protected JsonNode postFormOk(String path, Map<String, String> form) throws Exception {
        return postFormOk(path, Header.EMPTY, form);
    }

    protected JsonNode postFormOk(String path, Header header, Map<String, String> form) throws Exception {
        HttpRestResult<String> restResult = nacosRestTemplate.postForm(requestUrl(path), header, form, String.class);
        assertTrue(restResult.ok(), "HTTP status should be 2xx, code=" + restResult.getCode() + ", body="
                + restResult.getData() + ", message=" + restResult.getMessage());
        JsonNode root = JacksonUtils.toObj(restResult.getData());
        assertSuccess(root);
        return root;
    }

    protected JsonNode postFormOk(String path, Query query) throws Exception {
        HttpRestResult<String> restResult = nacosRestTemplate.postForm(requestUrl(path), Header.EMPTY, query,
                Collections.emptyMap(), String.class);
        assertTrue(restResult.ok(), "HTTP status should be 2xx, code=" + restResult.getCode() + ", body="
                + restResult.getData() + ", message=" + restResult.getMessage());
        JsonNode root = JacksonUtils.toObj(restResult.getData());
        assertSuccess(root);
        return root;
    }

    protected JsonNode postJsonOk(String path, Query query, String json) throws Exception {
        HttpResponse response = postJsonRaw(path, query, json);
        assertEquals(200, response.code(), response.body());
        JsonNode root = JacksonUtils.toObj(response.body());
        assertSuccess(root);
        return root;
    }

    protected HttpResponse postJsonRaw(String path, Query query, String json) throws Exception {
        HttpPost post = new HttpPost(requestUrl(path + "?" + query.toQueryUrl()));
        post.setEntity(new StringEntity(json, ContentType.APPLICATION_JSON));
        return executeRaw(post);
    }

    protected JsonNode putJsonOk(String path, Query query, String json) throws Exception {
        HttpResponse response = putJsonRaw(path, query, json);
        assertEquals(200, response.code(), response.body());
        JsonNode root = JacksonUtils.toObj(response.body());
        assertSuccess(root);
        return root;
    }

    protected HttpResponse putJsonRaw(String path, Query query, String json) throws Exception {
        HttpPut put = new HttpPut(requestUrl(path + "?" + query.toQueryUrl()));
        put.setEntity(new StringEntity(json, ContentType.APPLICATION_JSON));
        return executeRaw(put);
    }

    protected HttpResponse postMultipartRaw(String path, Query query, String fieldName, String fileName,
            String contentType, byte[] fileBytes) throws Exception {
        String boundary = "----nacos-openapi-it-" + System.nanoTime();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(("Content-Disposition: form-data; name=\"" + fieldName + "\"; filename=\"" + fileName
                + "\"\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(("Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(fileBytes);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        HttpPost post = new HttpPost(requestUrl(path + "?" + query.toQueryUrl()));
        post.setEntity(new ByteArrayEntity(body.toByteArray(),
                ContentType.parse("multipart/form-data; boundary=" + boundary)));
        return executeRaw(post);
    }

    protected JsonNode putFormOk(String path, Map<String, String> form) throws Exception {
        HttpRestResult<String> restResult = nacosRestTemplate.putForm(requestUrl(path), Header.EMPTY, form,
                String.class);
        assertTrue(restResult.ok(), "HTTP status should be 2xx, code=" + restResult.getCode() + ", body="
                + restResult.getData() + ", message=" + restResult.getMessage());
        JsonNode root = JacksonUtils.toObj(restResult.getData());
        assertSuccess(root);
        return root;
    }

    protected JsonNode putFormOk(String path, Query query) throws Exception {
        HttpRestResult<String> restResult = nacosRestTemplate.putForm(requestUrl(path), Header.EMPTY, query,
                Collections.emptyMap(), String.class);
        assertTrue(restResult.ok(), "HTTP status should be 2xx, code=" + restResult.getCode() + ", body="
                + restResult.getData() + ", message=" + restResult.getMessage());
        JsonNode root = JacksonUtils.toObj(restResult.getData());
        assertSuccess(root);
        return root;
    }

    protected JsonNode deleteJsonOk(String path, Query query) throws Exception {
        HttpRestResult<String> restResult = nacosRestTemplate.delete(requestUrl(path), Header.EMPTY, query,
                String.class);
        assertTrue(restResult.ok(), "HTTP status should be 2xx, code=" + restResult.getCode() + ", body="
                + restResult.getData() + ", message=" + restResult.getMessage());
        JsonNode root = JacksonUtils.toObj(restResult.getData());
        assertSuccess(root);
        return root;
    }

    protected void deleteQuietly(String path, Query query) throws Exception {
        HttpRestResult<String> restResult = nacosRestTemplate.delete(requestUrl(path), Header.EMPTY, query,
                String.class);
        if (!restResult.ok()) {
            logger().warn("delete non-OK: path={} code={} body={}", path, restResult.getCode(), restResult.getData());
        }
    }

    protected void assertSuccess(JsonNode root) {
        assertNotNull(root);
        assertEquals(ErrorCode.SUCCESS.getCode(), root.get("code").asInt(), root.toString());
        assertEquals(ErrorCode.SUCCESS.getMsg(), root.get("message").asText(), root.toString());
    }

    protected void assertError(HttpResponse response, int expectedStatus, ErrorCode expectedCode, String expectedData)
            throws Exception {
        assertEquals(expectedStatus, response.code(), response.body());
        JsonNode root = JacksonUtils.toObj(response.body());
        assertNotNull(root, response.body());
        assertEquals(expectedCode.getCode(), root.get("code").asInt(), response.body());
        assertNotNull(root.get("message"), response.body());
        assertNotNull(root.get("data"), response.body());
        assertTrue(root.get("data").asText().contains(expectedData), response.body());
    }

    protected HttpResponse executeRaw(ClassicHttpRequest request) throws Exception {
        HttpClientResponseHandler<HttpResponse> responseHandler = response -> {
            String body = null == response.getEntity() ? "" : EntityUtils.toString(response.getEntity());
            return new HttpResponse(response.getCode(), body);
        };
        return httpClient.execute(request, responseHandler);
    }
    
    protected ByteResponse executeRawBytes(ClassicHttpRequest request) throws Exception {
        HttpClientResponseHandler<ByteResponse> responseHandler = response -> {
            byte[] body = null == response.getEntity() ? new byte[0] : EntityUtils.toByteArray(response.getEntity());
            String contentType = null == response.getEntity() || null == response.getEntity().getContentType()
                    ? null : response.getEntity().getContentType();
            String contentDisposition = null == response.getFirstHeader("Content-Disposition") ? null
                    : response.getFirstHeader("Content-Disposition").getValue();
            return new ByteResponse(response.getCode(), body, contentType, contentDisposition);
        };
        return httpClient.execute(request, responseHandler);
    }
    
    protected static void addIfNotBlank(Query query, String name, String value) {
        if (null != value && !value.isBlank()) {
            query.addParam(name, value);
        }
    }
    
    private Exception runCleanupActions() {
        Exception failure = null;
        while (!cleanupActions.isEmpty()) {
            try {
                cleanupActions.removeLast().run();
            } catch (Exception e) {
                failure = mergeFailure(failure, e);
            }
        }
        return failure;
    }
    
    private Exception closeRestTemplate(Exception failure) {
        if (null != nacosRestTemplate) {
            try {
                nacosRestTemplate.close();
            } catch (Exception e) {
                failure = mergeFailure(failure, e);
            }
        }
        return failure;
    }
    
    private Exception closeHttpClient(Exception failure) {
        if (null != httpClient) {
            try {
                httpClient.close();
            } catch (Exception e) {
                failure = mergeFailure(failure, e);
            }
        }
        return failure;
    }
    
    private Exception mergeFailure(Exception existing, Exception next) {
        if (null == existing) {
            return next;
        }
        existing.addSuppressed(next);
        return existing;
    }
    
    @FunctionalInterface
    protected interface CleanupAction {
        
        void run() throws Exception;
    }
    
    protected record HttpResponse(int code, String body) {
    }
    
    protected record ByteResponse(int code, byte[] body, String contentType, String contentDisposition) {
    }
}
