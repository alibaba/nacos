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

package com.alibaba.nacos.ai.service.agent.runtime;

import com.alibaba.nacos.ai.service.VisibilityHelper;
import com.alibaba.nacos.ai.service.runtime.AiHttpClientLifecycleService;
import com.alibaba.nacos.api.ai.model.agent.ClientLivenessInfo;
import com.alibaba.nacos.api.ai.model.rad.AgentEndpointRegistrationBatch;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.naming.core.v2.client.Client;
import com.alibaba.nacos.naming.core.v2.client.ClientAttributes;
import com.alibaba.nacos.naming.core.v2.client.impl.HttpConnectionBasedClient;
import com.alibaba.nacos.naming.core.v2.client.manager.impl.HttpConnectionBasedClientManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentHttpClientLifecycleServiceTest {
    
    private static final String EXTERNAL_CLIENT_ID = "client-1";
    
    private static final String INTERNAL_CLIENT_ID = "HTTP_CLIENT@@client-1";
    
    private HttpConnectionBasedClientManager clientManager;
    
    private AgentRuntimeRegistryService runtimeRegistryService;
    
    private AiHttpClientLifecycleService sharedService;
    
    private AgentHttpClientLifecycleService service;
    
    private MockedStatic<VisibilityHelper> visibilityHelper;
    
    @BeforeEach
    void setUp() {
        clientManager = mock(HttpConnectionBasedClientManager.class);
        runtimeRegistryService = mock(AgentRuntimeRegistryService.class);
        sharedService = new AiHttpClientLifecycleService(clientManager);
        service = new AgentHttpClientLifecycleService(sharedService, runtimeRegistryService);
        visibilityHelper = mockStatic(VisibilityHelper.class);
        visibilityHelper.when(VisibilityHelper::resolveCurrentIdentity).thenReturn("alice");
    }
    
    @AfterEach
    void tearDown() {
        visibilityHelper.close();
    }
    
    @Test
    void testQueryRenewsOnlyExistingBoundClient() throws Exception {
        HttpConnectionBasedClient client = client("alice", "team");
        when(clientManager.getClient(INTERNAL_CLIENT_ID)).thenReturn(client);
        
        service.renewForQuery(EXTERNAL_CLIENT_ID, "team");
        
        verify(clientManager).renewClient(INTERNAL_CLIENT_ID);
        verify(clientManager, never()).renewPublisher(any());
        
        service.renewForQuery(null, "team");
        when(clientManager.getClient("HTTP_CLIENT@@missing")).thenReturn(null);
        service.renewForQuery("missing", "team");
        
        Client otherType = mock(Client.class);
        when(clientManager.getClient("HTTP_CLIENT@@other")).thenReturn(otherType);
        service.renewForQuery("other", "team");
    }
    
    @Test
    void testQueryRejectsMismatchedBinding() {
        when(clientManager.getClient(INTERNAL_CLIENT_ID)).thenReturn(client("bob", "team"));
        assertDetailCode(ErrorCode.ACCESS_DENIED,
            assertThrows(NacosApiException.class,
                () -> service.renewForQuery(EXTERNAL_CLIENT_ID, "team")));
        
        when(clientManager.getClient(INTERNAL_CLIENT_ID)).thenReturn(client("alice", "other"));
        assertDetailCode(ErrorCode.ACCESS_DENIED,
            assertThrows(NacosApiException.class,
                () -> service.renewForQuery(EXTERNAL_CLIENT_ID, "team")));
    }
    
    @Test
    void testWatchValidatesHeadersAndRenewsExistingClient() throws Exception {
        when(clientManager.getClient(INTERNAL_CLIENT_ID)).thenReturn(null);
        service.renewForWatch(EXTERNAL_CLIENT_ID, "ai", "team");
        verify(clientManager, never()).renewClient(any());
        
        when(clientManager.getClient(INTERNAL_CLIENT_ID)).thenReturn(client("alice", "team"));
        service.renewForWatch(EXTERNAL_CLIENT_ID, "AI", "team");
        verify(clientManager).renewClient(INTERNAL_CLIENT_ID);
        
        assertInvalidWatchHeader(null, "AI", "team");
        assertInvalidWatchHeader(EXTERNAL_CLIENT_ID, "naming", "team");
        assertDetailCode(ErrorCode.ACCESS_DENIED,
            assertThrows(NacosApiException.class,
                () -> service.renewForWatch(EXTERNAL_CLIENT_ID, "AI", "other")));
    }
    
    @Test
    void testRegisterCreatesAndBindsHttpClient() throws Exception {
        HttpConnectionBasedClient client = client("alice", "team");
        when(clientManager.getClient(INTERNAL_CLIENT_ID)).thenReturn(null, client);
        when(clientManager.clientConnected(eq(INTERNAL_CLIENT_ID), any(ClientAttributes.class)))
            .thenReturn(true);
        when(clientManager.renewPublisher(INTERNAL_CLIENT_ID)).thenReturn(true);
        AgentEndpointRegistrationBatch batch = batch("team");
        
        ClientLivenessInfo actual = service.register(EXTERNAL_CLIENT_ID, "ai", batch);
        
        assertEquals(5000L, actual.getHeartbeatIntervalMillis());
        assertEquals(15000L, actual.getUnhealthyTimeoutMillis());
        assertEquals(30000L, actual.getExpireTimeoutMillis());
        verify(runtimeRegistryService).register(INTERNAL_CLIENT_ID, batch);
        ArgumentCaptor<ClientAttributes> attributes = ArgumentCaptor.forClass(
            ClientAttributes.class);
        verify(clientManager).clientConnected(eq(INTERNAL_CLIENT_ID), attributes.capture());
        assertEquals("alice", attributes.getValue().getClientAttribute(
            "httpClientIdentity"));
        assertEquals("team", attributes.getValue().getClientAttribute(
            "httpClientNamespace"));
    }
    
    @Test
    void testAgentAndMcpReuseSameBoundHttpClient() throws Exception {
        HttpConnectionBasedClient client = client("alice", "team");
        when(clientManager.getClient(INTERNAL_CLIENT_ID)).thenReturn(null, client, client);
        when(clientManager.clientConnected(eq(INTERNAL_CLIENT_ID), any(ClientAttributes.class)))
            .thenReturn(true);
        when(clientManager.renewPublisher(INTERNAL_CLIENT_ID)).thenReturn(true);
        AtomicReference<String> mcpPublisher = new AtomicReference<>();
        
        service.register(EXTERNAL_CLIENT_ID, "AI", batch("team"));
        sharedService.register(EXTERNAL_CLIENT_ID, "AI", "team", mcpPublisher::set);
        
        assertEquals(INTERNAL_CLIENT_ID, mcpPublisher.get());
        verify(clientManager).clientConnected(eq(INTERNAL_CLIENT_ID), any(ClientAttributes.class));
        verify(clientManager, org.mockito.Mockito.times(2)).renewPublisher(INTERNAL_CLIENT_ID);
    }
    
    @Test
    void testRegisterBindsLegacyEmptyAttributes() throws Exception {
        HttpConnectionBasedClient client =
            new HttpConnectionBasedClient(INTERNAL_CLIENT_ID, new ClientAttributes());
        when(clientManager.getClient(INTERNAL_CLIENT_ID)).thenReturn(client);
        when(clientManager.renewPublisher(INTERNAL_CLIENT_ID)).thenReturn(true);
        
        service.register(EXTERNAL_CLIENT_ID, "AI", batch("team"));
        
        assertEquals("alice", client.getClientAttributes().getClientAttribute(
            "httpClientIdentity"));
        assertEquals("team", client.getClientAttributes().getClientAttribute(
            "httpClientNamespace"));
    }
    
    @Test
    void testRegisterRejectsClientCreationFailures() {
        when(clientManager.getClient(INTERNAL_CLIENT_ID)).thenReturn(null);
        when(clientManager.clientConnected(eq(INTERNAL_CLIENT_ID), any(ClientAttributes.class)))
            .thenReturn(false);
        assertClientNotFound(assertThrows(NacosApiException.class,
            () -> service.register(EXTERNAL_CLIENT_ID, "AI", batch("team"))));
        
        when(clientManager.clientConnected(eq(INTERNAL_CLIENT_ID), any(ClientAttributes.class)))
            .thenReturn(true);
        assertClientNotFound(assertThrows(NacosApiException.class,
            () -> service.register(EXTERNAL_CLIENT_ID, "AI", batch("team"))));
    }
    
    @Test
    void testRegisterRejectsMissingAttributes() {
        HttpConnectionBasedClient client =
            new HttpConnectionBasedClient(INTERNAL_CLIENT_ID, null);
        when(clientManager.getClient(INTERNAL_CLIENT_ID)).thenReturn(client);
        
        assertClientNotFound(assertThrows(NacosApiException.class,
            () -> service.register(EXTERNAL_CLIENT_ID, "AI", batch("team"))));
    }
    
    @Test
    void testRegisterCleansEmptyClientAfterFailure() throws Exception {
        HttpConnectionBasedClient client = client("alice", "team");
        when(clientManager.getClient(INTERNAL_CLIENT_ID)).thenReturn(client);
        NacosException failure = new NacosException(500, "failed");
        doThrow(failure).when(runtimeRegistryService).register(eq(INTERNAL_CLIENT_ID),
            any(AgentEndpointRegistrationBatch.class));
        
        NacosException actual = assertThrows(NacosException.class,
            () -> service.register(EXTERNAL_CLIENT_ID, "AI", batch("team")));
        
        assertSame(failure, actual);
        verify(clientManager).disconnectIfEmpty(INTERNAL_CLIENT_ID);
    }
    
    @Test
    void testRegisterRejectsPublicationNotRetained() throws Exception {
        when(clientManager.getClient(INTERNAL_CLIENT_ID)).thenReturn(client("alice", "team"));
        when(clientManager.renewPublisher(INTERNAL_CLIENT_ID)).thenReturn(false);
        
        assertClientNotFound(assertThrows(NacosApiException.class,
            () -> service.register(EXTERNAL_CLIENT_ID, "AI", batch("team"))));
        verify(clientManager).disconnectIfEmpty(INTERNAL_CLIENT_ID);
    }
    
    @Test
    void testDeregisterIsIdempotentAndRemovesExistingPublication() throws Exception {
        when(clientManager.getClient(INTERNAL_CLIENT_ID)).thenReturn(null);
        service.deregister(EXTERNAL_CLIENT_ID, "AI", "team", "demo", "a2a");
        verify(runtimeRegistryService, never()).deregisterPublisher(any(), any(), any(), any());
        
        HttpConnectionBasedClient client = client("alice", "team");
        when(clientManager.getClient(INTERNAL_CLIENT_ID)).thenReturn(client);
        service.deregister(EXTERNAL_CLIENT_ID, "AI", "team", "demo", "a2a");
        
        verify(runtimeRegistryService).deregisterPublisher(INTERNAL_CLIENT_ID, "team", "demo",
            "a2a");
        verify(clientManager).disconnectIfEmpty(INTERNAL_CLIENT_ID);
    }
    
    @Test
    void testHeartbeatReturnsLivenessOrClientNotFound() throws Exception {
        when(clientManager.getClient(INTERNAL_CLIENT_ID)).thenReturn(null);
        assertClientNotFound(assertThrows(NacosApiException.class,
            () -> service.heartbeat(EXTERNAL_CLIENT_ID, "AI")));
        
        when(clientManager.getClient(INTERNAL_CLIENT_ID)).thenReturn(client("alice", "team"));
        when(clientManager.renewPublisher(INTERNAL_CLIENT_ID)).thenReturn(false, true);
        assertClientNotFound(assertThrows(NacosApiException.class,
            () -> service.heartbeat(EXTERNAL_CLIENT_ID, "AI")));
        
        assertEquals(5000L,
            service.heartbeat(EXTERNAL_CLIENT_ID, "AI").getHeartbeatIntervalMillis());
    }
    
    @Test
    void testStatefulHeaderValidation() {
        assertInvalidHeader(null, "AI");
        assertInvalidHeader("x".repeat(257), "AI");
        assertInvalidHeader("bad/id", "AI");
        assertInvalidHeader(EXTERNAL_CLIENT_ID, null);
        assertInvalidHeader(EXTERNAL_CLIENT_ID, "naming");
    }
    
    private void assertInvalidHeader(String clientId, String module) {
        assertDetailCode(ErrorCode.PARAMETER_VALIDATE_ERROR,
            assertThrows(NacosApiException.class,
                () -> service.heartbeat(clientId, module)));
    }
    
    private void assertInvalidWatchHeader(String clientId, String module, String namespaceId) {
        assertDetailCode(ErrorCode.PARAMETER_VALIDATE_ERROR,
            assertThrows(NacosApiException.class,
                () -> service.renewForWatch(clientId, module, namespaceId)));
    }
    
    private void assertDetailCode(ErrorCode errorCode, NacosApiException actual) {
        assertEquals(errorCode.getCode(), actual.getDetailErrCode());
    }
    
    private void assertClientNotFound(NacosApiException actual) {
        assertEquals(NacosException.NOT_FOUND, actual.getErrCode());
        assertDetailCode(ErrorCode.HTTP_CLIENT_NOT_FOUND, actual);
    }
    
    private HttpConnectionBasedClient client(String identity, String namespaceId) {
        ClientAttributes attributes = new ClientAttributes();
        attributes.addClientAttribute("httpClientIdentity",
            identity);
        attributes.addClientAttribute("httpClientNamespace",
            namespaceId);
        return new HttpConnectionBasedClient(INTERNAL_CLIENT_ID, attributes);
    }
    
    private AgentEndpointRegistrationBatch batch(String namespaceId) {
        AgentEndpointRegistrationBatch result = new AgentEndpointRegistrationBatch();
        result.setNamespaceId(namespaceId);
        return result;
    }
    
}
