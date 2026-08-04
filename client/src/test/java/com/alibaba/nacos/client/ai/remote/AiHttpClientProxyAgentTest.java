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

package com.alibaba.nacos.client.ai.remote;

import com.alibaba.nacos.api.ai.model.agent.ClientLivenessInfo;
import com.alibaba.nacos.api.ai.model.agent.Endpoint;
import com.alibaba.nacos.api.ai.model.agent.AgentCallInterface;
import com.alibaba.nacos.api.ai.model.agent.AgentProvider;
import com.alibaba.nacos.api.ai.model.agent.AgentPublishRequest;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionDetail;
import com.alibaba.nacos.api.ai.model.agent.EndpointSource;
import com.alibaba.nacos.api.ai.model.rad.AgentCatalogEntry;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryFilter;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryRequest;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryResult;
import com.alibaba.nacos.api.ai.model.rad.AgentEndpointRegistrationBatch;
import com.alibaba.nacos.api.ai.model.rad.AgentReference;
import com.alibaba.nacos.api.ai.model.rad.AgentSearchRequest;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.client.naming.core.NamingServerListManager;
import com.alibaba.nacos.client.security.SecurityProxy;
import com.alibaba.nacos.common.constant.HttpHeaderConsts;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.utils.JacksonUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiHttpClientProxyAgentTest {
    
    private static final String CLIENT_ID_HEADER = "X-Nacos-Client-Id";
    
    @Mock
    private NacosRestTemplate restTemplate;
    
    @Mock
    private NamingServerListManager serverListManager;
    
    @Mock
    private SecurityProxy securityProxy;
    
    private AiHttpClientProxy proxy;
    
    @BeforeEach
    void setUp() throws Exception {
        proxy = new AiHttpClientProxy();
        injectField(proxy, "namespaceId", "public");
        injectField(proxy, "nacosRestTemplate", restTemplate);
        injectField(proxy, "serverListManager", serverListManager);
        injectField(proxy, "securityProxy", securityProxy);
        injectField(proxy, "executorService", new ScheduledThreadPoolExecutor(1));
        lenient().when(serverListManager.getServerList())
            .thenReturn(Collections.singletonList("127.0.0.1:8848"));
        lenient().when(serverListManager.getContextPath()).thenReturn("/nacos");
        lenient().when(securityProxy.getIdentityContext(any()))
            .thenReturn(Collections.<String, String>emptyMap());
    }
    
    @AfterEach
    void tearDown() throws NacosException {
        proxy.shutdown();
    }
    
    @Test
    void searchPreservesRepeatedParametersAndClientIdentity() throws Exception {
        AgentCatalogEntry entry = new AgentCatalogEntry();
        entry.setAgentName("agent-a");
        Page<AgentCatalogEntry> page = new Page<AgentCatalogEntry>();
        page.setPageItems(Collections.singletonList(entry));
        doReturn(success(page)).when(restTemplate)
            .get(anyString(), any(Header.class), eq(Query.EMPTY), eq(String.class));
        AgentSearchRequest request = new AgentSearchRequest();
        request.setNamespaceId("public");
        request.setAgentNameContains("hello world");
        request.setTagsAll(Arrays.asList("one", "two"));
        request.setProtocolsAny(Arrays.asList("a2a", "mcp"));
        request.setPageNo(2);
        request.setPageSize(10);
        
        Page<AgentCatalogEntry> result = proxy.searchAgents(request);
        
        assertEquals("agent-a", result.getPageItems().get(0).getAgentName());
        ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Header> header = ArgumentCaptor.forClass(Header.class);
        verify(restTemplate).get(url.capture(), header.capture(), eq(Query.EMPTY),
            eq(String.class));
        assertTrue(url.getValue().contains("/v3/client/ai/agents/search?namespaceId=public"));
        assertTrue(url.getValue().contains("agentNameContains=hello+world"));
        assertEquals(2, occurrences(url.getValue(), "tagsAll="));
        assertEquals(2, occurrences(url.getValue(), "protocolsAny="));
        assertTrue(url.getValue().contains("pageNo=2"));
        assertTrue(url.getValue().contains("pageSize=10"));
        assertNotNull(header.getValue().getValue(CLIENT_ID_HEADER));
        assertNull(header.getValue().getValue(HttpHeaderConsts.REQUEST_MODULE));
    }
    
    @Test
    void publishSerializesCompleteFormAndReturnsDetail() throws Exception {
        AgentVersionDetail expected = new AgentVersionDetail();
        expected.setAgentName("agent-a");
        expected.setVersion("1.0.0");
        doReturn(success(expected)).when(restTemplate)
            .postForm(anyString(), any(Header.class), any(Map.class), eq(String.class));
        AgentPublishRequest request = new AgentPublishRequest();
        request.setAgentName("agent-a");
        request.setDisplayName("Agent A");
        request.setDescription("description");
        request.setIconUrl("https://example.com/icon.png");
        AgentProvider provider = new AgentProvider();
        provider.setName("Nacos");
        request.setProvider(provider);
        request.setTags(Collections.singletonList("assistant"));
        request.setExtensions(Collections.<String, Object>singletonMap("region", "east"));
        request.setVersion("1.0.0");
        request.setCallInterfaces(Collections.singletonList(new AgentCallInterface()));
        request.setAuthor("alice");
        request.setChangeDescription("initial");
        request.setAutoSubmit(true);
        
        AgentVersionDetail actual = proxy.publishAgent(request);
        assertEquals("agent-a", actual.getAgentName());
        ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Header> header = ArgumentCaptor.forClass(Header.class);
        ArgumentCaptor<Map> form = ArgumentCaptor.forClass(Map.class);
        verify(restTemplate).postForm(url.capture(), header.capture(), form.capture(),
            eq(String.class));
        assertTrue(url.getValue().endsWith("/nacos/v3/client/ai/agents"));
        assertEquals(Constants.AI.AI_MODULE,
            header.getValue().getValue(HttpHeaderConsts.REQUEST_MODULE));
        assertEquals("public", form.getValue().get("namespaceId"));
        assertEquals("agent-a", form.getValue().get("agentName"));
        assertEquals("Agent A", form.getValue().get("displayName"));
        assertTrue(String.valueOf(form.getValue().get("provider")).contains("Nacos"));
        assertTrue(String.valueOf(form.getValue().get("tags")).contains("assistant"));
        assertTrue(String.valueOf(form.getValue().get("extensions")).contains("east"));
        assertTrue(String.valueOf(form.getValue().get("callInterfaces")).startsWith("["));
        assertEquals("true", form.getValue().get("autoSubmit"));
        assertFalse(form.getValue().containsKey("basedOnVersion"));
    }
    
    @Test
    void discoverSerializesEveryFilterAndUsesSameStableClientIdentity() throws Exception {
        AgentDiscoveryResult expected = new AgentDiscoveryResult();
        expected.setAgentName("agent-a");
        doReturn(success(expected)).when(restTemplate)
            .get(anyString(), any(Header.class), eq(Query.EMPTY), eq(String.class));
        AgentDiscoveryRequest request = discoveryRequest();
        
        AgentDiscoveryResult result = proxy.discoverAgent(request);
        proxy.discoverAgent(request);
        
        assertEquals("agent-a", result.getAgentName());
        ArgumentCaptor<String> urls = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Header> headers = ArgumentCaptor.forClass(Header.class);
        verify(restTemplate, times(2)).get(urls.capture(), headers.capture(), eq(Query.EMPTY),
            eq(String.class));
        String url = urls.getAllValues().get(0);
        assertTrue(url.contains("/v3/client/ai/agents?namespaceId=public"));
        assertTrue(url.contains("agentName=agent-a"));
        assertTrue(url.contains("version=1.0.0"));
        assertTrue(url.contains("label=stable"));
        assertEquals(2, occurrences(url, "protocol="));
        assertEquals(2, occurrences(url, "transport="));
        assertEquals(2, occurrences(url, "endpointSource="));
        assertTrue(url.contains("protocolVersion=1.0"));
        assertTrue(url.contains("metadataSelector="));
        assertEquals(headers.getAllValues().get(0).getValue(CLIENT_ID_HEADER),
            headers.getAllValues().get(1).getValue(CLIENT_ID_HEADER));
    }
    
    @Test
    void minimalSearchAndDiscoverOmitEveryOptionalParameter() throws Exception {
        doReturn(success(new Page<AgentCatalogEntry>())).when(restTemplate)
            .get(anyString(), any(Header.class), eq(Query.EMPTY), eq(String.class));
        AgentSearchRequest search = new AgentSearchRequest();
        search.setNamespaceId("public");
        search.setPageNo(1);
        search.setPageSize(20);
        proxy.searchAgents(search);
        
        doReturn(success(new AgentDiscoveryResult())).when(restTemplate)
            .get(anyString(), any(Header.class), eq(Query.EMPTY), eq(String.class));
        AgentDiscoveryRequest discovery = new AgentDiscoveryRequest();
        discovery.setNamespaceId("public");
        AgentReference reference = new AgentReference();
        reference.setAgentName("agent-a");
        discovery.setReference(reference);
        proxy.discoverAgent(discovery);
        
        ArgumentCaptor<String> urls = ArgumentCaptor.forClass(String.class);
        verify(restTemplate, times(2)).get(urls.capture(), any(Header.class), eq(Query.EMPTY),
            eq(String.class));
        assertFalse(urls.getAllValues().get(0).contains("tagsAll="));
        assertFalse(urls.getAllValues().get(0).contains("protocolsAny="));
        assertFalse(urls.getAllValues().get(1).contains("version="));
        assertFalse(urls.getAllValues().get(1).contains("protocol="));
    }
    
    @Test
    void registerUsesFormHeadersAndReturnsServerLiveness() throws Exception {
        ClientLivenessInfo expected = new ClientLivenessInfo();
        expected.setHeartbeatIntervalMillis(1234);
        doReturn(success(expected)).when(restTemplate)
            .postForm(anyString(), any(Header.class), any(Map.class), eq(String.class));
        AgentEndpointRegistrationBatch batch = registrationBatch(">=1.0");
        
        ClientLivenessInfo result = proxy.registerAgentEndpoints(batch);
        
        assertEquals(1234, result.getHeartbeatIntervalMillis());
        ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Header> header = ArgumentCaptor.forClass(Header.class);
        ArgumentCaptor<Map> form = ArgumentCaptor.forClass(Map.class);
        verify(restTemplate).postForm(url.capture(), header.capture(), form.capture(),
            eq(String.class));
        assertTrue(url.getValue().endsWith("/nacos/v3/client/ai/agents/endpoints"));
        assertEquals(Constants.AI.AI_MODULE,
            header.getValue().getValue(HttpHeaderConsts.REQUEST_MODULE));
        assertNotNull(header.getValue().getValue(CLIENT_ID_HEADER));
        assertEquals("public", form.getValue().get("namespaceId"));
        assertEquals("agent-a", form.getValue().get("agentName"));
        assertEquals("runtime-1", form.getValue().get("runtimeVersion"));
        assertEquals(">=1.0", form.getValue().get("versionRange"));
        assertEquals("a2a", form.getValue().get("protocol"));
        assertTrue(String.valueOf(form.getValue().get("endpoints")).contains("http://host/a"));
    }
    
    @Test
    void registerOmitsNullVersionRange() throws Exception {
        doReturn(success(new ClientLivenessInfo())).when(restTemplate)
            .postForm(anyString(), any(Header.class), any(Map.class), eq(String.class));
        
        proxy.registerAgentEndpoints(registrationBatch(null));
        
        ArgumentCaptor<Map> form = ArgumentCaptor.forClass(Map.class);
        verify(restTemplate).postForm(anyString(), any(Header.class), form.capture(),
            eq(String.class));
        assertFalse(form.getValue().containsKey("versionRange"));
    }
    
    @Test
    void deregisterAndHeartbeatUseExpectedMethodsAndStableClientIdentity() throws Exception {
        doReturn(success(null)).when(restTemplate)
            .delete(anyString(), any(Header.class), any(Query.class), eq(String.class));
        ClientLivenessInfo expected = new ClientLivenessInfo();
        expected.setExpireTimeoutMillis(30000);
        doReturn(success(expected)).when(restTemplate)
            .put(anyString(), any(Header.class), eq(Query.EMPTY), eq(null), eq(String.class));
        
        proxy.deregisterAgentEndpoints("public", "agent-a", "a2a");
        ClientLivenessInfo result = proxy.heartbeatAgentEndpoints();
        
        assertEquals(30000, result.getExpireTimeoutMillis());
        ArgumentCaptor<Header> deleteHeader = ArgumentCaptor.forClass(Header.class);
        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(restTemplate).delete(anyString(), deleteHeader.capture(), query.capture(),
            eq(String.class));
        assertEquals("public", query.getValue().getValue("namespaceId"));
        assertEquals("agent-a", query.getValue().getValue("agentName"));
        assertEquals("a2a", query.getValue().getValue("protocol"));
        ArgumentCaptor<String> putUrl = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Header> putHeader = ArgumentCaptor.forClass(Header.class);
        verify(restTemplate).put(putUrl.capture(), putHeader.capture(), eq(Query.EMPTY), eq(null),
            eq(String.class));
        assertTrue(putUrl.getValue().endsWith("/v3/client/ai/agents/endpoints/heartbeat"));
        assertEquals(Constants.AI.AI_MODULE,
            putHeader.getValue().getValue(HttpHeaderConsts.REQUEST_MODULE));
        assertEquals(deleteHeader.getValue().getValue(CLIENT_ID_HEADER),
            putHeader.getValue().getValue(CLIENT_ID_HEADER));
    }
    
    @Test
    void noServerAndTransportExceptionsRetainUsefulCategories() throws Exception {
        when(serverListManager.getServerList()).thenReturn(Collections.<String>emptyList());
        AgentSearchRequest search = new AgentSearchRequest();
        assertEquals(NacosException.INVALID_PARAM,
            assertThrows(NacosException.class, () -> proxy.searchAgents(search)).getErrCode());
        
        when(serverListManager.getServerList())
            .thenReturn(Collections.singletonList("127.0.0.1:8848"));
        doThrow(new NacosException(NacosException.CLIENT_ERROR, "direct")).when(restTemplate)
            .postForm(anyString(), any(Header.class), any(Map.class), eq(String.class));
        NacosException direct = assertThrows(NacosException.class,
            () -> proxy.registerAgentEndpoints(registrationBatch(null)));
        assertEquals(NacosException.CLIENT_ERROR, direct.getErrCode());
        
        doThrow(new IllegalStateException("boom")).when(restTemplate)
            .postForm(anyString(), any(Header.class), any(Map.class), eq(String.class));
        NacosException wrapped = assertThrows(NacosException.class,
            () -> proxy.registerAgentEndpoints(registrationBatch(null)));
        assertEquals(NacosException.SERVER_ERROR, wrapped.getErrCode());
    }
    
    @Test
    void responseErrorsMapApplicationAndTransportCodes() throws Exception {
        HttpRestResult<String> missing = error(404,
            Result.failure(ErrorCode.HTTP_CLIENT_NOT_FOUND.getCode(), "missing", null));
        doReturn(missing).when(restTemplate)
            .put(anyString(), any(Header.class), eq(Query.EMPTY), eq(null), eq(String.class));
        assertEquals(ErrorCode.HTTP_CLIENT_NOT_FOUND.getCode(),
            assertThrows(NacosException.class, proxy::heartbeatAgentEndpoints).getErrCode());
        
        HttpRestResult<String> conflict = error(409,
            Result.failure(ErrorCode.AGENT_VERSION_EXIST.getCode(), "conflict", "detail"));
        doReturn(conflict).when(restTemplate)
            .postForm(anyString(), any(Header.class), any(Map.class), eq(String.class));
        NacosException conflictException = assertThrows(NacosException.class,
            () -> proxy.registerAgentEndpoints(registrationBatch(null)));
        assertEquals(409, conflictException.getErrCode());
        assertTrue(conflictException.getMessage().contains("detail"));
        
        doReturn(error(422, "not-json")).when(restTemplate)
            .delete(anyString(), any(Header.class), any(Query.class), eq(String.class));
        assertEquals(422, assertThrows(NacosException.class,
            () -> proxy.deregisterAgentEndpoints("public", "agent-a", "a2a")).getErrCode());
        
        doReturn(error(418, Result.success(null))).when(restTemplate)
            .delete(anyString(), any(Header.class), any(Query.class), eq(String.class));
        assertEquals(418, assertThrows(NacosException.class,
            () -> proxy.deregisterAgentEndpoints("public", "agent-a", "a2a")).getErrCode());
    }
    
    @Test
    void forbiddenReloginsAndInvalidSuccessPayloadsAreRejected() throws Exception {
        doReturn(error(403, "forbidden")).when(restTemplate)
            .get(anyString(), any(Header.class), eq(Query.EMPTY), eq(String.class));
        assertThrows(NacosException.class, () -> proxy.searchAgents(new AgentSearchRequest()));
        verify(securityProxy, times(3)).reLogin();
        
        doReturn(ok("null")).when(restTemplate)
            .get(anyString(), any(Header.class), eq(Query.EMPTY), eq(String.class));
        assertEquals(NacosException.SERVER_ERROR, assertThrows(NacosException.class,
            () -> proxy.searchAgents(new AgentSearchRequest())).getErrCode());
        
        doReturn(ok("{\"code\":null,\"message\":\"invalid\",\"data\":null}"))
            .when(restTemplate)
            .get(anyString(), any(Header.class), eq(Query.EMPTY), eq(String.class));
        assertEquals(NacosException.SERVER_ERROR, assertThrows(NacosException.class,
            () -> proxy.searchAgents(new AgentSearchRequest())).getErrCode());
        
        doReturn(ok(JacksonUtils.toJson(
            Result.failure(ErrorCode.AGENT_NOT_FOUND.getCode(), "absent", null))))
            .when(restTemplate)
            .get(anyString(), any(Header.class), eq(Query.EMPTY), eq(String.class));
        assertEquals(ErrorCode.AGENT_NOT_FOUND.getCode(), assertThrows(NacosException.class,
            () -> proxy.searchAgents(new AgentSearchRequest())).getErrCode());
    }
    
    @Test
    void queryEncodingFailureIsMappedToClientError() {
        AgentSearchRequest request = new AgentSearchRequest();
        request.setNamespaceId("public");
        try (MockedStatic<URLEncoder> encoder = Mockito.mockStatic(URLEncoder.class)) {
            encoder.when(() -> URLEncoder.encode("namespaceId", Constants.ENCODE))
                .thenThrow(new java.io.UnsupportedEncodingException("unsupported"));
            
            assertEquals(NacosException.CLIENT_ERROR,
                assertThrows(NacosException.class, () -> proxy.searchAgents(request))
                    .getErrCode());
        }
    }
    
    private AgentDiscoveryRequest discoveryRequest() {
        AgentReference reference = new AgentReference();
        reference.setAgentName("agent-a");
        reference.setVersion("1.0.0");
        reference.setLabel("stable");
        AgentDiscoveryFilter filter = new AgentDiscoveryFilter();
        filter.setProtocols(Arrays.asList("a2a", "mcp"));
        filter.setProtocolVersion("1.0");
        filter.setTransports(Arrays.asList("jsonrpc", "grpc"));
        filter.setEndpointSources(Arrays.asList(EndpointSource.RUNTIME, EndpointSource.DECLARED));
        Map<String, String> selector = new HashMap<String, String>();
        selector.put("zone", "cn east");
        filter.setMetadataSelector(selector);
        AgentDiscoveryRequest result = new AgentDiscoveryRequest();
        result.setNamespaceId("public");
        result.setReference(reference);
        result.setFilter(filter);
        return result;
    }
    
    private AgentEndpointRegistrationBatch registrationBatch(String versionRange) {
        Endpoint endpoint = new Endpoint();
        endpoint.setUri("http://host/a");
        endpoint.setTransport("jsonrpc");
        AgentEndpointRegistrationBatch result = new AgentEndpointRegistrationBatch();
        result.setNamespaceId("public");
        result.setAgentName("agent-a");
        result.setRuntimeVersion("runtime-1");
        result.setVersionRange(versionRange);
        result.setProtocol("a2a");
        result.setEndpoints(Collections.singletonList(endpoint));
        return result;
    }
    
    private HttpRestResult<String> success(Object data) {
        return ok(JacksonUtils.toJson(Result.success(data)));
    }
    
    private HttpRestResult<String> ok(String body) {
        HttpRestResult<String> result = new HttpRestResult<String>();
        result.setCode(200);
        result.setData(body);
        return result;
    }
    
    private HttpRestResult<String> error(int status, Object body) {
        return error(status, JacksonUtils.toJson(body));
    }
    
    private HttpRestResult<String> error(int status, String body) {
        HttpRestResult<String> result = new HttpRestResult<String>();
        result.setCode(status);
        result.setMessage(body);
        return result;
    }
    
    private int occurrences(String value, String token) {
        int result = 0;
        int index = 0;
        while ((index = value.indexOf(token, index)) >= 0) {
            result++;
            index += token.length();
        }
        return result;
    }
    
    private void injectField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
