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

import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpec;
import com.alibaba.nacos.api.ability.constant.AbilityStatus;
import com.alibaba.nacos.common.http.HttpClientConfig;
import com.alibaba.nacos.plugin.auth.api.RequestResource;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.net.SocketTimeoutException;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.never;
import com.alibaba.nacos.api.ai.model.prompt.Prompt;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.client.naming.core.NamingServerListManager;
import com.alibaba.nacos.client.security.SecurityProxy;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.utils.JacksonUtils;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
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
    void queryAgentSpecSuccess() throws Exception {
        AgentSpec expectedAgentSpec = new AgentSpec();
        expectedAgentSpec.setName("agent-a");
        expectedAgentSpec.setNamespaceId("public");
        expectedAgentSpec.setDescription("test agent");
        Result<AgentSpec> result = Result.success(expectedAgentSpec);
        
        HttpRestResult<String> httpResult = new HttpRestResult<>();
        httpResult.setCode(200);
        httpResult.setData(JacksonUtils.toJson(result));
        httpResult.setHeader(Header.newInstance().addParam("X-Nacos-AgentSpec-Md5", "md5-value")
            .addParam("X-Nacos-AgentSpec-Resolved-Version", "1.0.0"));
        
        when(serverListManager.getServerList()).thenReturn(Arrays.asList("127.0.0.1:8848"));
        when(serverListManager.getContextPath()).thenReturn("/nacos");
        when(securityProxy.getIdentityContext(any())).thenReturn(new HashMap<>());
        doReturn(httpResult).when(nacosRestTemplate).get(anyString(), any(Header.class),
            any(Query.class), eq(String.class));
        
        AgentSpecQueryResponse actual =
            httpClientProxy.queryAgentSpec("agent-a", "1.0.0", null, null);
        
        assertNotNull(actual);
        assertEquals("agent-a", actual.getAgentSpec().getName());
        assertEquals("public", actual.getAgentSpec().getNamespaceId());
        assertEquals("test agent", actual.getAgentSpec().getDescription());
        assertEquals("md5-value", actual.getMd5());
        assertEquals("1.0.0", actual.getResolvedVersion());
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
    
    @Test
    void shouldReadCapabilitiesWithoutLifecycleHeadersOrResourceScope() throws Exception {
        prepareCapabilityResponse(200, "{\"code\":0,\"data\":{\"schemaVersion\":1,"
            + "\"capabilities\":{\"radV1\":true}}}");
        assertEquals(AbilityStatus.SUPPORTED,
            httpClientProxy.getCapabilities("127.0.0.1:8848").get("radV1"));
        httpClientProxy.getCapabilities("127.0.0.1:8848");
        ArgumentCaptor<Header> header = ArgumentCaptor.forClass(Header.class);
        verify(nacosRestTemplate).get(eq("http://127.0.0.1:8848/nacos/v3/client/ai/capabilities"),
            any(HttpClientConfig.class), header.capture(), eq(Query.EMPTY), eq(String.class));
        assertEquals("token", header.getValue().getValue("Authorization"));
        assertNull(header.getValue().getValue("X-Nacos-Client-Id"));
        ArgumentCaptor<RequestResource> resource = ArgumentCaptor.forClass(RequestResource.class);
        verify(securityProxy, times(2)).getIdentityContext(resource.capture());
        assertEquals("", resource.getValue().getNamespace());
        assertEquals("", resource.getValue().getGroup());
        assertEquals("", resource.getValue().getResource());
        verify(serverListManager, never()).getServerList();
        httpClientProxy.invalidateCapabilities();
        httpClientProxy.getCapabilities("127.0.0.1:8848");
        verify(nacosRestTemplate, times(2)).get(anyString(), any(HttpClientConfig.class),
            any(Header.class), any(Query.class), eq(String.class));
    }
    
    @ParameterizedTest
    @ValueSource(ints = {404, 405})
    void shouldTreatMissingCapabilityRouteAsUnknown(int status) throws Exception {
        prepareCapabilityResponse(status, "not found");
        assertEquals(AbilityStatus.UNKNOWN,
            httpClientProxy.getCapabilities("127.0.0.1:8848").get("radV1"));
        verify(nacosRestTemplate).get(anyString(), any(HttpClientConfig.class),
            any(Header.class), any(Query.class), eq(String.class));
    }
    
    @ParameterizedTest
    @ValueSource(ints = {401, 403, 500, 503})
    void shouldPreserveErrorsWithoutTryingAnotherServerOrCachingFailure(int status)
        throws Exception {
        prepareCapabilityResponse(status, "rejected");
        for (int i = 0; i < 2; i++) {
            assertEquals(status, assertThrows(NacosException.class,
                () -> httpClientProxy.getCapabilities("127.0.0.1:8848")).getErrCode());
        }
        verify(nacosRestTemplate, times(2)).get(anyString(), any(HttpClientConfig.class),
            any(Header.class), any(Query.class), eq(String.class));
        verify(serverListManager, never()).getServerList();
    }
    
    @Test
    void shouldReportUnreachableHttpWithoutCapabilityDowngrade() throws Exception {
        when(serverListManager.getContextPath()).thenReturn("/nacos");
        when(securityProxy.getIdentityContext(any())).thenReturn(Collections.emptyMap());
        doThrow(new SocketTimeoutException("timeout")).when(nacosRestTemplate).get(anyString(),
            any(HttpClientConfig.class), any(Header.class), any(Query.class), eq(String.class));
        NacosException error = assertThrows(NacosException.class,
            () -> httpClientProxy.getCapabilities("127.0.0.1:8848"));
        assertEquals(NacosException.SERVER_ERROR, error.getErrCode());
        assertTrue(error.getMessage().contains("Cannot reach AI HTTP capability endpoint"));
        verify(nacosRestTemplate).get(anyString(), any(HttpClientConfig.class),
            any(Header.class), any(Query.class), eq(String.class));
    }
    
    @Test
    void capabilityProbeRetriesConnectionFailureOnNextTarget() throws Exception {
        prepareCapabilityResponse(200, "{\"code\":0,\"data\":{\"schemaVersion\":1,"
            + "\"capabilities\":{\"radV1\":true}}}");
        when(serverListManager.getServerList())
            .thenReturn(Arrays.asList("first:8848", "second:8848"));
        doThrow(new java.net.ConnectException("refused")).when(nacosRestTemplate)
            .get(eq("http://first:8848/nacos/v3/client/ai/capabilities"),
                any(HttpClientConfig.class),
                any(Header.class), any(Query.class), eq(String.class));
        assertEquals(AbilityStatus.SUPPORTED, httpClientProxy.getCapabilities().get("radV1"));
        verify(nacosRestTemplate).get(eq("http://second:8848/nacos/v3/client/ai/capabilities"),
            any(HttpClientConfig.class), any(Header.class), any(Query.class), eq(String.class));
    }
    
    @ParameterizedTest
    @ValueSource(ints = {401, 403, 500})
    void capabilityBusinessErrorDoesNotTryAnotherTarget(int code) throws Exception {
        prepareCapabilityResponse(code, "rejected");
        when(serverListManager.getServerList())
            .thenReturn(Arrays.asList("first:8848", "second:8848"));
        assertEquals(code, assertThrows(NacosException.class,
            () -> httpClientProxy.getCapabilities()).getErrCode());
        verify(nacosRestTemplate, times(1)).get(anyString(), any(HttpClientConfig.class),
            any(Header.class), any(Query.class), eq(String.class));
    }
    
    @Test
    void capabilityTlsFailureAndNoTargetsAreExplicit() throws Exception {
        when(serverListManager.getServerList()).thenReturn(Collections.emptyList());
        assertEquals(NacosException.CLIENT_DISCONNECT, assertThrows(NacosException.class,
            () -> httpClientProxy.getCapabilities()).getErrCode());
        when(serverListManager.getServerList())
            .thenReturn(Arrays.asList("first:8848", "second:8848"));
        when(serverListManager.getContextPath()).thenReturn("/nacos");
        javax.net.ssl.SSLHandshakeException tls =
            new javax.net.ssl.SSLHandshakeException("certificate");
        doThrow(tls).when(nacosRestTemplate).get(anyString(), any(HttpClientConfig.class),
            any(Header.class), any(Query.class), eq(String.class));
        assertSame(tls, assertThrows(NacosException.class,
            () -> httpClientProxy.getCapabilities()).getCause());
        verify(nacosRestTemplate, times(1)).get(anyString(), any(HttpClientConfig.class),
            any(Header.class), any(Query.class), eq(String.class));
    }
    
    @Test
    void legacyEvidenceRequiresSoleMatchingMainAddress() {
        when(serverListManager.getServerList())
            .thenReturn(Collections.singletonList("https://one:8848"));
        assertTrue(httpClientProxy.isOnlyServer("one:8848"));
        assertFalse(httpClientProxy.isOnlyServer(null));
        assertFalse(httpClientProxy.isOnlyServer("two:8848"));
        when(serverListManager.getServerList()).thenReturn(Arrays.asList("one:8848", "two:8848"));
        assertFalse(httpClientProxy.isOnlyServer("one:8848"));
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"one:8848", "http://one:8848", "https://one:8848"})
    void matchingSoleTargetAcceptsConfiguredScheme(String target) {
        when(serverListManager.getServerList()).thenReturn(Collections.singletonList(target));
        assertTrue(httpClientProxy.isOnlyServer("one:8848"));
        assertFalse(httpClientProxy.isOnlyServer("other:8848"));
    }
    
    @Test
    void allCapabilityTargetsUnreachablePreservesLastFailureAndCanRecover() throws Exception {
        when(serverListManager.getContextPath()).thenReturn("/nacos");
        when(serverListManager.getServerList()).thenReturn(Arrays.asList("one:8848", "two:8848"));
        when(securityProxy.getIdentityContext(any())).thenReturn(Collections.emptyMap());
        java.net.ConnectException first = new java.net.ConnectException("one refused");
        java.net.ConnectException last = new java.net.ConnectException("two refused");
        doThrow(first).doThrow(last).when(nacosRestTemplate).get(anyString(),
            any(HttpClientConfig.class),
            any(Header.class), any(Query.class), eq(String.class));
        assertSame(last,
            assertThrows(NacosException.class, httpClientProxy::getCapabilities).getCause());
        verify(nacosRestTemplate, times(2)).get(anyString(), any(HttpClientConfig.class),
            any(Header.class), any(Query.class), eq(String.class));
        prepareCapabilityResponse(200, "{\"code\":0,\"data\":{\"schemaVersion\":1,"
            + "\"capabilities\":{\"radV1\":true}}}");
        assertEquals(AbilityStatus.SUPPORTED, httpClientProxy.getCapabilities().get("radV1"));
    }
    
    private void prepareCapabilityResponse(int status, String body) throws Exception {
        when(serverListManager.getContextPath()).thenReturn("/nacos");
        when(securityProxy.getIdentityContext(any()))
            .thenReturn(Collections.singletonMap("Authorization", "token"));
        HttpRestResult<String> result = new HttpRestResult<>();
        result.setCode(status);
        result.setData(body);
        result.setMessage(body);
        doReturn(result).when(nacosRestTemplate).get(anyString(), any(HttpClientConfig.class),
            any(Header.class), any(Query.class), eq(String.class));
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
