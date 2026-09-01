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
import com.alibaba.nacos.api.ai.model.mcp.McpEndpointSpec;
import com.alibaba.nacos.api.ai.model.mcp.McpResourceSpecification;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpToolSpecification;
import com.alibaba.nacos.api.ai.model.mcp.registry.ServerVersionDetail;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
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
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiHttpClientProxyMcpTest {
    
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
    void queryUsesExactVersionAndStableSharedClientIdentity() throws Exception {
        McpServerDetailInfo expected = new McpServerDetailInfo();
        expected.setName("mcp-a");
        doReturn(success(expected)).when(restTemplate)
            .get(anyString(), any(Header.class), eq(Query.EMPTY), eq(String.class));
        doReturn(success(new ClientLivenessInfo())).when(restTemplate)
            .put(anyString(), any(Header.class), eq(Query.EMPTY), eq(null), eq(String.class));
        
        McpServerDetailInfo result = proxy.queryMcpServer("mcp a", "1.0.0");
        proxy.heartbeatMcpServerEndpoints();
        
        assertEquals("mcp-a", result.getName());
        ArgumentCaptor<String> queryUrl = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Header> queryHeader = ArgumentCaptor.forClass(Header.class);
        verify(restTemplate).get(queryUrl.capture(), queryHeader.capture(), eq(Query.EMPTY),
            eq(String.class));
        assertTrue(queryUrl.getValue().contains("/v3/client/ai/mcp?namespaceId=public"));
        assertTrue(queryUrl.getValue().contains("mcpName=mcp+a"));
        assertTrue(queryUrl.getValue().contains("version=1.0.0"));
        ArgumentCaptor<Header> heartbeatHeader = ArgumentCaptor.forClass(Header.class);
        verify(restTemplate).put(anyString(), heartbeatHeader.capture(), eq(Query.EMPTY), eq(null),
            eq(String.class));
        assertEquals(queryHeader.getValue().getValue(CLIENT_ID_HEADER),
            heartbeatHeader.getValue().getValue(CLIENT_ID_HEADER));
    }
    
    @Test
    void releaseSerializesCompleteDraftFormAndIsNeverCrossServerReplayed() throws Exception {
        when(serverListManager.getServerList())
            .thenReturn(Arrays.asList("127.0.0.1:8848", "127.0.0.2:8848"));
        doReturn(success("mcp-id")).when(restTemplate)
            .postForm(anyString(), any(Header.class), any(Map.class), eq(String.class));
        McpServerBasicInfo server = server("mcp-a", "1.0.0");
        McpToolSpecification tools = new McpToolSpecification();
        tools.getExtensions().put("tool-extension", "value");
        McpResourceSpecification resources = new McpResourceSpecification();
        resources.getExtensions().put("resource-extension", "value");
        McpEndpointSpec endpoint = new McpEndpointSpec();
        endpoint.setType("DIRECT");
        endpoint.getData().put("address", "127.0.0.1");
        
        assertEquals("mcp-id",
            proxy.releaseMcpServer(server, tools, resources, endpoint, true));
        
        ArgumentCaptor<Header> header = ArgumentCaptor.forClass(Header.class);
        ArgumentCaptor<Map> form = ArgumentCaptor.forClass(Map.class);
        verify(restTemplate).postForm(anyString(), header.capture(), form.capture(),
            eq(String.class));
        assertEquals(Constants.AI.AI_MODULE,
            header.getValue().getValue(HttpHeaderConsts.REQUEST_MODULE));
        assertEquals("public", form.getValue().get("namespaceId"));
        assertEquals("mcp-a", form.getValue().get("mcpName"));
        assertTrue(String.valueOf(form.getValue().get("serverSpecification"))
            .contains("1.0.0"));
        assertTrue(String.valueOf(form.getValue().get("toolSpecification"))
            .contains("tool-extension"));
        assertTrue(String.valueOf(form.getValue().get("resourceSpecification"))
            .contains("resource-extension"));
        assertTrue(String.valueOf(form.getValue().get("endpointSpecification"))
            .contains("127.0.0.1"));
        assertEquals("true", form.getValue().get("createDraft"));
    }
    
    @Test
    void releaseFailureAttemptsOnlyOneServer() throws Exception {
        when(serverListManager.getServerList())
            .thenReturn(Arrays.asList("127.0.0.1:8848", "127.0.0.2:8848"));
        doReturn(error(500, Result.failure(ErrorCode.SERVER_ERROR.getCode(), "failed", null)))
            .when(restTemplate)
            .postForm(anyString(), any(Header.class), any(Map.class), eq(String.class));
        
        assertThrows(NacosException.class,
            () -> proxy.releaseMcpServer(server("mcp-a", "1.0.0"), null, null, null, false));
        verify(restTemplate).postForm(anyString(), any(Header.class), any(Map.class),
            eq(String.class));
    }
    
    @Test
    void endpointOperationsUseFormsAndOneStableClientIdentity() throws Exception {
        ClientLivenessInfo liveness = new ClientLivenessInfo();
        liveness.setHeartbeatIntervalMillis(1000);
        doReturn(success(liveness)).when(restTemplate)
            .postForm(anyString(), any(Header.class), any(Map.class), eq(String.class));
        doReturn(success(null)).when(restTemplate)
            .delete(anyString(), any(Header.class), any(Query.class), eq(String.class));
        doReturn(success(liveness)).when(restTemplate)
            .put(anyString(), any(Header.class), eq(Query.EMPTY), eq(null), eq(String.class));
        
        assertEquals(1000,
            proxy.registerMcpServerEndpoint("mcp-a", "127.0.0.1", 8080, "1.0.0")
                .getHeartbeatIntervalMillis());
        proxy.deregisterMcpServerEndpoint("mcp-a", "127.0.0.1", 8080);
        proxy.heartbeatMcpServerEndpoints();
        
        ArgumentCaptor<Header> registerHeader = ArgumentCaptor.forClass(Header.class);
        ArgumentCaptor<Map> registerForm = ArgumentCaptor.forClass(Map.class);
        verify(restTemplate).postForm(anyString(), registerHeader.capture(),
            registerForm.capture(), eq(String.class));
        assertEquals("mcp-a", registerForm.getValue().get("mcpName"));
        assertEquals("127.0.0.1", registerForm.getValue().get("address"));
        assertEquals("8080", registerForm.getValue().get("port"));
        assertEquals("1.0.0", registerForm.getValue().get("version"));
        ArgumentCaptor<Header> deleteHeader = ArgumentCaptor.forClass(Header.class);
        ArgumentCaptor<Query> deleteForm = ArgumentCaptor.forClass(Query.class);
        verify(restTemplate).delete(anyString(), deleteHeader.capture(), deleteForm.capture(),
            eq(String.class));
        assertEquals("mcp-a", deleteForm.getValue().getValue("mcpName"));
        ArgumentCaptor<Header> heartbeatHeader = ArgumentCaptor.forClass(Header.class);
        verify(restTemplate).put(anyString(), heartbeatHeader.capture(), eq(Query.EMPTY), eq(null),
            eq(String.class));
        assertEquals(registerHeader.getValue().getValue(CLIENT_ID_HEADER),
            deleteHeader.getValue().getValue(CLIENT_ID_HEADER));
        assertEquals(registerHeader.getValue().getValue(CLIENT_ID_HEADER),
            heartbeatHeader.getValue().getValue(CLIENT_ID_HEADER));
    }
    
    @Test
    void endpointOmitsNullVersionAndPreservesHttpClientNotFound() throws Exception {
        doReturn(success(new ClientLivenessInfo())).when(restTemplate)
            .postForm(anyString(), any(Header.class), any(Map.class), eq(String.class));
        proxy.registerMcpServerEndpoint("mcp-a", "127.0.0.1", 8080, null);
        ArgumentCaptor<Map> form = ArgumentCaptor.forClass(Map.class);
        verify(restTemplate).postForm(anyString(), any(Header.class), form.capture(),
            eq(String.class));
        assertFalse(form.getValue().containsKey("version"));
        
        doReturn(error(404,
            Result.failure(ErrorCode.HTTP_CLIENT_NOT_FOUND.getCode(), "missing", null)))
            .when(restTemplate)
            .put(anyString(), any(Header.class), eq(Query.EMPTY), eq(null), eq(String.class));
        NacosException exception = assertThrows(NacosException.class,
            proxy::heartbeatMcpServerEndpoints);
        assertEquals(ErrorCode.HTTP_CLIENT_NOT_FOUND.getCode(), exception.getErrCode());
        assertNotNull(exception.getMessage());
    }
    
    private McpServerBasicInfo server(String name, String version) {
        McpServerBasicInfo result = new McpServerBasicInfo();
        result.setName(name);
        ServerVersionDetail versionDetail = new ServerVersionDetail();
        versionDetail.setVersion(version);
        result.setVersionDetail(versionDetail);
        return result;
    }
    
    private HttpRestResult<String> success(Object data) {
        HttpRestResult<String> result = new HttpRestResult<String>();
        result.setCode(200);
        result.setData(JacksonUtils.toJson(Result.success(data)));
        return result;
    }
    
    private HttpRestResult<String> error(int status, Object body) {
        HttpRestResult<String> result = new HttpRestResult<String>();
        result.setCode(status);
        result.setMessage(JacksonUtils.toJson(body));
        return result;
    }
    
    private void injectField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
