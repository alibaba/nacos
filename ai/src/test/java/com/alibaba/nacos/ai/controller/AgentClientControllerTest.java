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

package com.alibaba.nacos.ai.controller;

import com.alibaba.nacos.ai.form.agent.client.AgentDiscoveryForm;
import com.alibaba.nacos.ai.form.agent.client.AgentEndpointDeregistrationForm;
import com.alibaba.nacos.ai.form.agent.client.AgentEndpointRegistrationForm;
import com.alibaba.nacos.ai.form.agent.client.AgentPublishForm;
import com.alibaba.nacos.ai.form.agent.client.AgentSearchForm;
import com.alibaba.nacos.ai.form.agent.client.AgentWatchBatchForm;
import com.alibaba.nacos.ai.service.agent.AgentDiscoveryApplicationService;
import com.alibaba.nacos.ai.service.agent.AgentPublishApplicationService;
import com.alibaba.nacos.ai.service.agent.runtime.AgentHttpClientLifecycleService;
import com.alibaba.nacos.ai.service.agent.watch.AgentHttpWatchService;
import com.alibaba.nacos.api.ai.model.agent.ClientLivenessInfo;
import com.alibaba.nacos.api.ai.model.agent.AgentPublishRequest;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionDetail;
import com.alibaba.nacos.api.ai.model.rad.AgentCatalogEntry;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryRequest;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryResult;
import com.alibaba.nacos.api.ai.model.rad.AgentEndpointRegistrationBatch;
import com.alibaba.nacos.api.ai.model.rad.AgentSearchRequest;
import com.alibaba.nacos.api.ai.model.rad.AgentWatchBatchRequest;
import com.alibaba.nacos.api.ai.model.rad.AgentWatchBatchResponse;
import com.alibaba.nacos.api.common.ApiType;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.context.request.async.DeferredResult;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentClientControllerTest {
    
    private AgentDiscoveryApplicationService discoveryService;
    
    private AgentHttpClientLifecycleService lifecycleService;
    
    private AgentPublishApplicationService publishService;
    
    private AgentHttpWatchService watchService;
    
    private AgentClientController controller;
    
    @BeforeEach
    void setUp() {
        discoveryService = mock(AgentDiscoveryApplicationService.class);
        lifecycleService = mock(AgentHttpClientLifecycleService.class);
        publishService = mock(AgentPublishApplicationService.class);
        watchService = mock(AgentHttpWatchService.class);
        controller = new AgentClientController(discoveryService, lifecycleService,
            publishService, watchService);
    }
    
    @Test
    void testPublish() throws Exception {
        AgentPublishForm form = mock(AgentPublishForm.class);
        AgentPublishRequest request = new AgentPublishRequest();
        AgentVersionDetail detail = new AgentVersionDetail();
        when(form.getNamespaceId()).thenReturn("team");
        when(form.toRequest()).thenReturn(request);
        when(publishService.publish("team", request)).thenReturn(detail);
        assertSame(detail, controller.publish(form).getData());
    }
    
    @Test
    void testSearch() throws Exception {
        AgentSearchForm form = mock(AgentSearchForm.class);
        AgentSearchRequest request = new AgentSearchRequest();
        request.setNamespaceId("team");
        Page<AgentCatalogEntry> page = new Page<AgentCatalogEntry>();
        when(form.toRequest()).thenReturn(request);
        when(discoveryService.search(request)).thenReturn(page);
        
        assertSame(page, controller.search(form, "client").getData());
        verify(lifecycleService).renewForQuery("client", "team");
    }
    
    @Test
    void testDiscover() throws Exception {
        AgentDiscoveryForm form = mock(AgentDiscoveryForm.class);
        AgentDiscoveryRequest request = new AgentDiscoveryRequest();
        request.setNamespaceId("team");
        AgentDiscoveryResult discovery = new AgentDiscoveryResult();
        when(form.toRequest()).thenReturn(request);
        when(discoveryService.discover(request)).thenReturn(discovery);
        
        assertSame(discovery, controller.discover(form, "client").getData());
        verify(lifecycleService).renewForQuery("client", "team");
    }
    
    @Test
    void testRegisterEndpoints() throws Exception {
        AgentEndpointRegistrationForm form = mock(AgentEndpointRegistrationForm.class);
        AgentEndpointRegistrationBatch batch = new AgentEndpointRegistrationBatch();
        ClientLivenessInfo liveness = new ClientLivenessInfo();
        when(form.toRequest()).thenReturn(batch);
        when(lifecycleService.register("client", "AI", batch)).thenReturn(liveness);
        
        assertSame(liveness, controller.registerEndpoints(form, "client", "AI").getData());
    }
    
    @Test
    void testDeregisterEndpoints() throws Exception {
        AgentEndpointDeregistrationForm form = mock(AgentEndpointDeregistrationForm.class);
        when(form.getNamespaceId()).thenReturn("team");
        when(form.getAgentName()).thenReturn("demo");
        when(form.getProtocol()).thenReturn("a2a");
        
        assertNull(controller.deregisterEndpoints(form, "client", "AI").getData());
        verify(form).validate();
        verify(lifecycleService).deregister("client", "AI", "team", "demo", "a2a");
    }
    
    @Test
    void testHeartbeat() throws Exception {
        ClientLivenessInfo liveness = new ClientLivenessInfo();
        when(lifecycleService.heartbeat("client", "AI")).thenReturn(liveness);
        
        assertSame(liveness, controller.heartbeat("client", "AI").getData());
    }
    
    @Test
    void testWatch() throws Exception {
        AgentWatchBatchForm form = mock(AgentWatchBatchForm.class);
        AgentWatchBatchRequest request = new AgentWatchBatchRequest();
        DeferredResult<Result<AgentWatchBatchResponse>> deferred = new DeferredResult<>();
        when(form.toRequest()).thenReturn(request);
        when(form.getWatchPayloadBytes()).thenReturn(17);
        when(watchService.watch("client", "AI", request, 17)).thenReturn(deferred);
        
        assertSame(deferred, controller.watch(form, "client", "AI"));
    }
    
    @Test
    void testWatchSecurityMetadata() throws Exception {
        Method method = AgentClientController.class.getMethod("watch", AgentWatchBatchForm.class,
            String.class, String.class);
        PostMapping mapping = method.getAnnotation(PostMapping.class);
        Secured secured = method.getAnnotation(Secured.class);
        assertEquals(ActionTypes.READ, secured.action());
        assertEquals(SignType.AI, secured.signType());
        assertEquals(ApiType.OPEN_API, secured.apiType());
        org.junit.jupiter.api.Assertions.assertArrayEquals(new String[] {"/watch"},
            mapping.value());
    }
}
