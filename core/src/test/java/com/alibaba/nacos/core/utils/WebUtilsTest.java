/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.utils;

import com.alibaba.nacos.common.constant.HttpHeaderConsts;
import com.alibaba.nacos.common.model.RestResult;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * {@link WebUtils} unit tests.
 *
 * @author chenglu
 * @date 2021-06-10 13:33
 */
class WebUtilsTest {
    
    private static final String X_REAL_IP = "X-Real-IP";
    
    private static final String X_FORWARDED_FOR = "X-Forwarded-For";
    
    @Test
    void testRequired() {
        final String key = "key";
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        try {
            WebUtils.required(servletRequest, key);
        } catch (Exception e) {
            assertTrue(e instanceof IllegalArgumentException);
        }
        
        servletRequest.addParameter(key, "value");
        String val = WebUtils.required(servletRequest, key);
        assertEquals("value", val);
    }
    
    @Test
    void testOptional() {
        final String key = "key";
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        String val1 = WebUtils.optional(servletRequest, key, "value");
        assertEquals("value", val1);
        
        servletRequest.addParameter(key, "value1");
        assertEquals("value1", WebUtils.optional(servletRequest, key, "value"));
    }
    
    @Test
    void testGetUserAgent() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        String userAgent = WebUtils.getUserAgent(servletRequest);
        assertEquals("", userAgent);
        
        servletRequest.addHeader(HttpHeaderConsts.CLIENT_VERSION_HEADER, "0");
        assertEquals("0", WebUtils.getUserAgent(servletRequest));
        
        servletRequest.addHeader(HttpHeaderConsts.USER_AGENT_HEADER, "1");
        assertEquals("1", WebUtils.getUserAgent(servletRequest));
    }
    
    @Test
    void testGetAcceptEncoding() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        assertEquals(StandardCharsets.UTF_8.name(), WebUtils.getAcceptEncoding(servletRequest));
        
        servletRequest.addHeader(HttpHeaderConsts.ACCEPT_ENCODING, "gzip, deflate, br");
        assertEquals("gzip", WebUtils.getAcceptEncoding(servletRequest));
    }
    
    @Test
    void testGetRemoteIp() {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        
        Mockito.when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        assertEquals("127.0.0.1", WebUtils.getRemoteIp(request));
        
        Mockito.when(request.getHeader(eq(X_REAL_IP))).thenReturn("127.0.0.2");
        assertEquals("127.0.0.2", WebUtils.getRemoteIp(request));
        
        Mockito.when(request.getHeader(eq(X_FORWARDED_FOR))).thenReturn("127.0.0.3");
        assertEquals("127.0.0.3", WebUtils.getRemoteIp(request));
        
        Mockito.when(request.getHeader(eq(X_FORWARDED_FOR))).thenReturn("127.0.0.3, 127.0.0.4");
        assertEquals("127.0.0.3", WebUtils.getRemoteIp(request));
        
        Mockito.when(request.getHeader(eq(X_FORWARDED_FOR))).thenReturn("");
        assertEquals("127.0.0.2", WebUtils.getRemoteIp(request));
        
        Mockito.when(request.getHeader(eq(X_REAL_IP))).thenReturn("");
        assertEquals("127.0.0.1", WebUtils.getRemoteIp(request));
    }
    
    @Test
    void testGetAcceptEncodingWithSemicolon() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.addHeader(HttpHeaderConsts.ACCEPT_ENCODING, "gzip;q=1.0");
        assertEquals("gzip", WebUtils.getAcceptEncoding(servletRequest));
    }
    
    @Test
    void testOptionalWithEncoding() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addParameter("key", "value");
        req.addParameter("encoding", "UTF-8");
        assertEquals("value", WebUtils.optional(req, "key", "default"));
    }
    
    @Test
    void testOptionalWithInvalidEncodingIgnoresException() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addParameter("key", "value");
        req.addParameter("encoding", "InvalidEncodingNameXYZ");
        String result = WebUtils.optional(req, "key", "default");
        assertEquals("value", result.trim());
    }
    
    @Test
    void testResponse() throws IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();
        WebUtils.response(response, "{\"code\":0}", 200);
        assertEquals(200, response.getStatus());
        assertEquals("{\"code\":0}", response.getContentAsString());
    }
    
    @Test
    void testOnFileUploadNullFile() {
        DeferredResult<RestResult<String>> result = new DeferredResult<>();
        WebUtils.onFileUpload(null, f -> {
        }, result);
        assertNotNull(result.getResult());
        assertFalse(((RestResult<?>) result.getResult()).ok());
        assertEquals("File is empty", ((RestResult<?>) result.getResult()).getMessage());
    }
    
    @Test
    void testOnFileUploadEmptyFile() {
        MultipartFile emptyFile = Mockito.mock(MultipartFile.class);
        when(emptyFile.isEmpty()).thenReturn(true);
        DeferredResult<RestResult<String>> result = new DeferredResult<>();
        WebUtils.onFileUpload(emptyFile, f -> {
        }, result);
        assertNotNull(result.getResult());
        assertFalse(((RestResult<?>) result.getResult()).ok());
        assertEquals("File is empty", ((RestResult<?>) result.getResult()).getMessage());
    }
    
    @Test
    void testOnFileUploadExceptionInTransferTo() throws IOException {
        MultipartFile failingFile = Mockito.mock(MultipartFile.class);
        when(failingFile.isEmpty()).thenReturn(false);
        when(failingFile.getName()).thenReturn("f.txt");
        Mockito.doThrow(new IOException("transfer failed")).when(failingFile)
            .transferTo(Mockito.any(File.class));
        DeferredResult<RestResult<String>> result = new DeferredResult<>();
        WebUtils.onFileUpload(failingFile, f -> {
        }, result);
        assertNotNull(result.getResult());
        assertFalse(((RestResult<?>) result.getResult()).ok());
        assertEquals("transfer failed", ((RestResult<?>) result.getResult()).getMessage());
    }
    
    @Test
    void testProcessWithErrorHandler() {
        DeferredResult<String> deferred = new DeferredResult<>();
        CompletableFuture<String> future =
            CompletableFuture.failedFuture(new RuntimeException("test"));
        Function<Throwable, String> errorHandler = ex -> "error:" + ex.getMessage();
        WebUtils.process(deferred, future, errorHandler);
        assertEquals("error:test", deferred.getResult());
    }
    
    @Test
    void testProcessWithSuccessAndErrorHandler() {
        DeferredResult<String> deferred = new DeferredResult<>();
        CompletableFuture<String> future = CompletableFuture.completedFuture("ok");
        AtomicReference<String> run = new AtomicReference<>();
        WebUtils.process(deferred, future, () -> run.set("run"), ex -> "error");
        assertEquals("ok", deferred.getResult());
        assertEquals("run", run.get());
    }
    
    @Test
    void testProcessSuccessPathWithoutSuccessRunnable() {
        DeferredResult<String> deferred = new DeferredResult<>();
        CompletableFuture<String> future = CompletableFuture.completedFuture("done");
        WebUtils.process(deferred, future, ex -> "error");
        assertEquals("done", deferred.getResult());
    }
    
    @Test
    void testProcessWithSuccessRunnableAndErrorPath() {
        DeferredResult<String> deferred = new DeferredResult<>();
        CompletableFuture<String> future =
            CompletableFuture.failedFuture(new RuntimeException("fail"));
        WebUtils.process(deferred, future, () -> {
        }, ex -> "err:" + ex.getMessage());
        assertEquals("err:fail", deferred.getResult());
    }
    
    @Test
    void testOnFileUploadSuccessWithMockFile() {
        MultipartFile mockFile = new org.springframework.mock.web.MockMultipartFile("f", "f.txt",
            "text/plain", "data".getBytes(StandardCharsets.UTF_8));
        DeferredResult<RestResult<String>> result = new DeferredResult<>();
        AtomicReference<File> capturedFile = new AtomicReference<>();
        WebUtils.onFileUpload(mockFile, capturedFile::set, result);
        assertNotNull(capturedFile.get());
    }
}
