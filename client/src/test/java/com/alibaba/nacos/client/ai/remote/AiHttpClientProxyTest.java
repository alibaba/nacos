/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.client.ai.remote;

import com.alibaba.nacos.api.ai.model.prompt.Prompt;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.naming.core.NamingServerListManager;
import com.alibaba.nacos.client.security.SecurityProxy;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.api.model.v2.Result;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiHttpClientProxyTest {
    
    @Mock
    private NacosRestTemplate nacosRestTemplate;
    
    @Mock
    private NamingServerListManager serverListManager;
    
    @Mock
    private SecurityProxy securityProxy;
    
    private AiHttpClientProxy httpClientProxy;
    
    @BeforeEach
    void setUp() throws Exception {
        httpClientProxy = createProxyWithMocks();
    }
    
    @AfterEach
    void tearDown() throws NacosException {
        if (httpClientProxy != null) {
            httpClientProxy.shutdown();
        }
    }
    
    @Test
    void queryPromptSuccess() throws Exception {
        Prompt expectedPrompt = new Prompt("test-key", "1.0.0", "Hello {{name}}");
        expectedPrompt.setMd5("abc123");
        Result<Prompt> result = Result.success(expectedPrompt);
        String responseBody = JacksonUtils.toJson(result);
        
        HttpRestResult<String> httpResult = new HttpRestResult<>();
        httpResult.setCode(200);
        httpResult.setData(responseBody);
        
        when(serverListManager.getServerList()).thenReturn(Arrays.asList("127.0.0.1:8848"));
        when(serverListManager.getContextPath()).thenReturn("/nacos");
        when(securityProxy.getIdentityContext(any())).thenReturn(new HashMap<>());
        doReturn(httpResult).when(nacosRestTemplate)
            .get(anyString(), any(Header.class), any(Query.class), eq(String.class));
        
        Prompt actual = httpClientProxy.queryPrompt("test-key", "1.0.0", null, null);
        
        assertNotNull(actual);
        assertEquals("test-key", actual.getPromptKey());
        assertEquals("1.0.0", actual.getVersion());
        assertEquals("Hello {{name}}", actual.getTemplate());
        assertEquals("abc123", actual.getMd5());
    }
    
    @Test
    void queryPromptNotModifiedShouldThrow() throws Exception {
        HttpRestResult<String> httpResult = new HttpRestResult<>();
        httpResult.setCode(HttpURLConnection.HTTP_NOT_MODIFIED);
        
        when(serverListManager.getServerList()).thenReturn(Arrays.asList("127.0.0.1:8848"));
        when(serverListManager.getContextPath()).thenReturn("/nacos");
        when(securityProxy.getIdentityContext(any())).thenReturn(new HashMap<>());
        doReturn(httpResult).when(nacosRestTemplate)
            .get(anyString(), any(Header.class), any(Query.class), eq(String.class));
        
        NacosException exception = assertThrows(NacosException.class,
            () -> httpClientProxy.queryPrompt("test-key", null, null, "md5-value"));
        assertEquals(NacosException.NOT_MODIFIED, exception.getErrCode());
    }
    
    @Test
    void queryPromptNoServerAvailable() {
        when(serverListManager.getServerList()).thenReturn(Collections.emptyList());
        
        NacosException exception = assertThrows(NacosException.class,
            () -> httpClientProxy.queryPrompt("test-key", null, null, null));
        assertEquals(NacosException.INVALID_PARAM, exception.getErrCode());
    }
    
    @Test
    void queryPromptServerError() throws Exception {
        HttpRestResult<String> httpResult = new HttpRestResult<>();
        httpResult.setCode(500);
        httpResult.setMessage("Internal Server Error");
        
        when(serverListManager.getServerList()).thenReturn(Arrays.asList("127.0.0.1:8848"));
        when(serverListManager.getContextPath()).thenReturn("/nacos");
        when(securityProxy.getIdentityContext(any())).thenReturn(new HashMap<>());
        doReturn(httpResult).when(nacosRestTemplate)
            .get(anyString(), any(Header.class), any(Query.class), eq(String.class));
        
        NacosException exception = assertThrows(NacosException.class,
            () -> httpClientProxy.queryPrompt("test-key", null, null, null));
        assertEquals(500, exception.getErrCode());
    }
    
    @Test
    void queryPromptByLabel() throws Exception {
        Prompt expectedPrompt = new Prompt("test-key", "2.0.0", "Label prompt");
        Result<Prompt> result = Result.success(expectedPrompt);
        String responseBody = JacksonUtils.toJson(result);
        
        HttpRestResult<String> httpResult = new HttpRestResult<>();
        httpResult.setCode(200);
        httpResult.setData(responseBody);
        
        when(serverListManager.getServerList()).thenReturn(Arrays.asList("127.0.0.1:8848"));
        when(serverListManager.getContextPath()).thenReturn("/nacos");
        when(securityProxy.getIdentityContext(any())).thenReturn(new HashMap<>());
        doReturn(httpResult).when(nacosRestTemplate)
            .get(anyString(), any(Header.class), any(Query.class), eq(String.class));
        
        Prompt actual = httpClientProxy.queryPrompt("test-key", null, "prod", null);
        
        assertNotNull(actual);
        assertEquals("test-key", actual.getPromptKey());
    }
    
    private AiHttpClientProxy createProxyWithMocks() throws Exception {
        AiHttpClientProxy proxy = new AiHttpClientProxy();
        injectField(proxy, "namespaceId", "public");
        injectField(proxy, "nacosRestTemplate", nacosRestTemplate);
        injectField(proxy, "serverListManager", serverListManager);
        injectField(proxy, "securityProxy", securityProxy);
        injectField(proxy, "executorService", new ScheduledThreadPoolExecutor(1));
        return proxy;
    }
    
    private void injectField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
