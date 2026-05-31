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

package com.alibaba.nacos.naming.misc;

import com.alibaba.nacos.common.http.Callback;
import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.plugin.auth.constant.Constants;
import com.alibaba.nacos.sys.env.EnvUtil;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpClientTest {
    
    private static final String INVALID_URL = "nacos://invalid";
    
    @BeforeAll
    static void beforeAll() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty(Constants.Auth.NACOS_CORE_AUTH_ENABLED, "false");
        environment.setProperty(Constants.Auth.NACOS_CORE_AUTH_ADMIN_ENABLED, "false");
        EnvUtil.setEnvironment(environment);
    }
    
    @Test
    void testRequestWrappersReturnErrorForUnsupportedUrl() {
        Map<String, String> params = new HashMap<>();
        params.put("name", "nacos");
        
        assertServerError(HttpClient.httpGet(INVALID_URL, Arrays.asList("h1", "v1"), params));
        assertServerError(HttpClient.httpDelete(INVALID_URL, Arrays.asList("h2", "v2"), params));
        assertServerError(HttpClient.httpPost(INVALID_URL, Arrays.asList("h3", "v3"), params));
        assertServerError(HttpClient.request(INVALID_URL, Arrays.asList("h4", "v4"), params,
            "body", 1, 1, StandardCharsets.UTF_8.name(), "PATCH"));
    }
    
    @Test
    void testLargeBodyRequestsReturnErrorForUnsupportedUrl() {
        Map<String, String> headers = new HashMap<>();
        headers.put("header", "value");
        
        assertServerError(HttpClient.httpPutLarge(INVALID_URL, headers, "body".getBytes(
            StandardCharsets.UTF_8)));
        assertServerError(HttpClient.httpGetLarge(INVALID_URL, headers, "body"));
        assertServerError(HttpClient.httpPostLarge(INVALID_URL, headers, "body"));
    }
    
    @Test
    void testAsyncHttpRequestRejectsUnsupportedMethod() {
        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> HttpClient.asyncHttpRequest(INVALID_URL, Arrays.asList("h", "v"),
                Collections.singletonMap("key", "value"), new NoopCallback(), "PATCH"));
        
        assertEquals("not supported method:PATCH", exception.getMessage());
    }
    
    @Test
    void testAsyncRequestWrappersDelegateToRestTemplate() {
        NoopCallback callback = new NoopCallback();
        Map<String, String> headers = Collections.singletonMap("h", "v");
        byte[] body = "body".getBytes(StandardCharsets.UTF_8);
        
        assertDoesNotThrow(() -> HttpClient.asyncHttpGet(INVALID_URL, Arrays.asList("h1", "v1"),
            Collections.singletonMap("key", "value"), callback));
        assertDoesNotThrow(() -> HttpClient.asyncHttpPost(INVALID_URL, Arrays.asList("h2", "v2"),
            Collections.singletonMap("key", "value"), callback));
        assertDoesNotThrow(() -> HttpClient.asyncHttpDelete(INVALID_URL,
            Arrays.asList("h3", "v3"), Collections.singletonMap("key", "value"), callback));
        assertDoesNotThrow(() -> HttpClient.asyncHttpPostLarge(INVALID_URL,
            Arrays.asList("h4", "v4"), "body", callback));
        assertDoesNotThrow(() -> HttpClient.asyncHttpPostLarge(INVALID_URL,
            Arrays.asList("h5", "v5"), body, callback));
        assertDoesNotThrow(() -> HttpClient.asyncHttpDeleteLarge(INVALID_URL,
            Arrays.asList("h6", "v6"), "body", callback));
        assertDoesNotThrow(() -> HttpClient.asyncHttpPutLarge(INVALID_URL, headers, body,
            callback));
    }
    
    @Test
    void testTranslateParameterMapUsesFirstValue() {
        Map<String, String[]> parameterMap = new HashMap<>();
        parameterMap.put("name", new String[] {"nacos", "ignored"});
        
        Map<String, String> result = HttpClient.translateParameterMap(parameterMap);
        
        assertEquals("nacos", result.get("name"));
    }
    
    private void assertServerError(RestResult<String> result) {
        assertEquals(500, result.getCode());
        assertTrue(result.getMessage().contains("nacos"));
    }
    
    private static class NoopCallback implements Callback<String> {
        
        @Override
        public void onReceive(RestResult<String> result) {
        }
        
        @Override
        public void onError(Throwable throwable) {
        }
        
        @Override
        public void onCancel() {
        }
    }
}
