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

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
    
    @Test
    void queryPromptWithNullKeyUsesEmptyResource() throws Exception {
        Prompt p = new Prompt("k", "v", "tpl");
        Result<Prompt> r = Result.success(p);
        HttpRestResult<String> httpResult = new HttpRestResult<>();
        httpResult.setCode(200);
        httpResult.setData(JacksonUtils.toJson(r));
        when(serverListManager.getServerList()).thenReturn(Arrays.asList("127.0.0.1:8848"));
        when(serverListManager.getContextPath()).thenReturn("/nacos");
        when(securityProxy.getIdentityContext(any())).thenReturn(new HashMap<>());
        doReturn(httpResult).when(nacosRestTemplate).get(anyString(), any(Header.class),
            any(Query.class), eq(String.class));
        // Pass null promptKey to exercise StringUtils.EMPTY branch (line 130)
        Prompt actual = httpClientProxy.queryPrompt(null, null, null, null);
        assertNotNull(actual);
    }
    
    @Test
    void queryPromptForbiddenTriggersReLogin() throws Exception {
        HttpRestResult<String> httpResult = new HttpRestResult<>();
        httpResult.setCode(HttpURLConnection.HTTP_FORBIDDEN);
        httpResult.setMessage("forbidden");
        when(serverListManager.getServerList()).thenReturn(Arrays.asList("127.0.0.1:8848"));
        when(serverListManager.getContextPath()).thenReturn("/nacos");
        when(securityProxy.getIdentityContext(any())).thenReturn(new HashMap<>());
        doReturn(httpResult).when(nacosRestTemplate).get(anyString(), any(Header.class),
            any(Query.class), eq(String.class));
        assertThrows(NacosException.class,
            () -> httpClientProxy.queryPrompt("k", null, null, null));
        verify(securityProxy, times(3)).reLogin();
    }
    
    @Test
    void queryPromptNonNacosExceptionWrapped() throws Exception {
        when(serverListManager.getServerList()).thenReturn(Arrays.asList("127.0.0.1:8848"));
        when(serverListManager.getContextPath()).thenReturn("/nacos");
        when(securityProxy.getIdentityContext(any())).thenReturn(new HashMap<>());
        doThrow(new RuntimeException("boom")).when(nacosRestTemplate).get(anyString(),
            any(Header.class), any(Query.class), eq(String.class));
        NacosException ex = assertThrows(NacosException.class,
            () -> httpClientProxy.queryPrompt("k", null, null, null));
        assertEquals(NacosException.SERVER_ERROR, ex.getErrCode());
    }
    
    @Test
    void downloadSkillZipSuccess() throws Exception {
        byte[] zip = buildEmptyZip();
        HttpRestResult<byte[]> httpResult = new HttpRestResult<>();
        httpResult.setCode(200);
        httpResult.setData(zip);
        when(serverListManager.getServerList()).thenReturn(Arrays.asList("127.0.0.1:8848"));
        when(serverListManager.getContextPath()).thenReturn("/nacos");
        when(securityProxy.getIdentityContext(any())).thenReturn(new HashMap<>());
        doReturn(httpResult).when(nacosRestTemplate).get(anyString(), any(Header.class),
            any(Query.class), eq(byte[].class));
        byte[] result = httpClientProxy.downloadSkillZip("skill-a", "1.0", "stable");
        assertArrayEquals(zip, result);
    }
    
    @Test
    void downloadSkillZipNullNameUsesEmptyResource() throws Exception {
        byte[] zip = buildEmptyZip();
        HttpRestResult<byte[]> httpResult = new HttpRestResult<>();
        httpResult.setCode(200);
        httpResult.setData(zip);
        when(serverListManager.getServerList()).thenReturn(Arrays.asList("127.0.0.1:8848"));
        when(serverListManager.getContextPath()).thenReturn("/nacos");
        when(securityProxy.getIdentityContext(any())).thenReturn(new HashMap<>());
        doReturn(httpResult).when(nacosRestTemplate).get(anyString(), any(Header.class),
            any(Query.class), eq(byte[].class));
        byte[] result = httpClientProxy.downloadSkillZip(null, null, null);
        assertNotNull(result);
    }
    
    @Test
    void downloadSkillZipUnsafeEntryWrapped() throws Exception {
        // Create a zip with a path traversal entry to trigger SecurityException on validation
        byte[] zip = buildZipWithEntry("../escape.txt");
        HttpRestResult<byte[]> httpResult = new HttpRestResult<>();
        httpResult.setCode(200);
        httpResult.setData(zip);
        when(serverListManager.getServerList()).thenReturn(Arrays.asList("127.0.0.1:8848"));
        when(serverListManager.getContextPath()).thenReturn("/nacos");
        when(securityProxy.getIdentityContext(any())).thenReturn(new HashMap<>());
        doReturn(httpResult).when(nacosRestTemplate).get(anyString(), any(Header.class),
            any(Query.class), eq(byte[].class));
        NacosException ex = assertThrows(NacosException.class,
            () -> httpClientProxy.downloadSkillZip("s", null, null));
        assertEquals(NacosException.SERVER_ERROR, ex.getErrCode());
        assertTrue(ex.getErrMsg().contains("unsafe entry paths"));
    }
    
    @Test
    void downloadSkillZipNoServer() {
        when(serverListManager.getServerList()).thenReturn(Collections.emptyList());
        NacosException ex = assertThrows(NacosException.class,
            () -> httpClientProxy.downloadSkillZip("s", null, null));
        assertEquals(NacosException.INVALID_PARAM, ex.getErrCode());
    }
    
    @Test
    void downloadSkillZipServerError() throws Exception {
        HttpRestResult<byte[]> httpResult = new HttpRestResult<>();
        httpResult.setCode(500);
        httpResult.setMessage("Internal Server Error");
        when(serverListManager.getServerList()).thenReturn(Arrays.asList("127.0.0.1:8848"));
        when(serverListManager.getContextPath()).thenReturn("/nacos");
        when(securityProxy.getIdentityContext(any())).thenReturn(new HashMap<>());
        doReturn(httpResult).when(nacosRestTemplate).get(anyString(), any(Header.class),
            any(Query.class), eq(byte[].class));
        NacosException ex = assertThrows(NacosException.class,
            () -> httpClientProxy.downloadSkillZip("s", null, null));
        assertEquals(500, ex.getErrCode());
    }
    
    @Test
    void downloadSkillZipForbiddenTriggersReLogin() throws Exception {
        HttpRestResult<byte[]> httpResult = new HttpRestResult<>();
        httpResult.setCode(HttpURLConnection.HTTP_FORBIDDEN);
        httpResult.setMessage("forbidden");
        when(serverListManager.getServerList()).thenReturn(Arrays.asList("127.0.0.1:8848"));
        when(serverListManager.getContextPath()).thenReturn("/nacos");
        when(securityProxy.getIdentityContext(any())).thenReturn(new HashMap<>());
        doReturn(httpResult).when(nacosRestTemplate).get(anyString(), any(Header.class),
            any(Query.class), eq(byte[].class));
        assertThrows(NacosException.class,
            () -> httpClientProxy.downloadSkillZip("s", null, null));
        verify(securityProxy, times(3)).reLogin();
    }
    
    @Test
    void downloadSkillZipNonNacosExceptionWrapped() throws Exception {
        when(serverListManager.getServerList()).thenReturn(Arrays.asList("127.0.0.1:8848"));
        when(serverListManager.getContextPath()).thenReturn("/nacos");
        when(securityProxy.getIdentityContext(any())).thenReturn(new HashMap<>());
        doThrow(new RuntimeException("boom")).when(nacosRestTemplate).get(anyString(),
            any(Header.class), any(Query.class), eq(byte[].class));
        NacosException ex = assertThrows(NacosException.class,
            () -> httpClientProxy.downloadSkillZip("s", null, null));
        assertEquals(NacosException.SERVER_ERROR, ex.getErrCode());
    }
    
    @Test
    void buildUrlWithExplicitHttpPrefix() throws Exception {
        when(serverListManager.getContextPath()).thenReturn("/nacos");
        Method m = AiHttpClientProxy.class.getDeclaredMethod("buildUrl", String.class,
            String.class);
        m.setAccessible(true);
        String url = (String) m.invoke(httpClientProxy, "http://1.2.3.4:8848", "/v3/path");
        assertEquals("http://1.2.3.4:8848/nacos/v3/path", url);
    }
    
    @Test
    void buildUrlWithExplicitHttpsPrefix() throws Exception {
        when(serverListManager.getContextPath()).thenReturn("/nacos");
        Method m = AiHttpClientProxy.class.getDeclaredMethod("buildUrl", String.class,
            String.class);
        m.setAccessible(true);
        String url = (String) m.invoke(httpClientProxy, "https://1.2.3.4:8848", "/v3/path");
        assertEquals("https://1.2.3.4:8848/nacos/v3/path", url);
    }
    
    @Test
    void buildUrlWithoutPrefixAddsHttp() throws Exception {
        when(serverListManager.getContextPath()).thenReturn("/nacos");
        Method m = AiHttpClientProxy.class.getDeclaredMethod("buildUrl", String.class,
            String.class);
        m.setAccessible(true);
        String url = (String) m.invoke(httpClientProxy, "1.2.3.4:8848", "/v3/path");
        // ENABLE_HTTPS defaults to false; expect http:// prepended
        assertTrue(url.startsWith("http://1.2.3.4:8848"));
    }
    
    @Test
    void shutdownIsIdempotent() throws Exception {
        httpClientProxy.shutdown();
        // Calling shutdown a second time should also be safe
        httpClientProxy.shutdown();
    }
    
    private static byte[] buildEmptyZip() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry("safe.txt"));
            zos.write(new byte[] {1, 2, 3});
            zos.closeEntry();
        }
        return baos.toByteArray();
    }
    
    private static byte[] buildZipWithEntry(String entryName) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry(entryName));
            zos.write(new byte[] {1});
            zos.closeEntry();
        }
        return baos.toByteArray();
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
