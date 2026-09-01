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

import com.alibaba.nacos.ai.form.mcp.client.McpEndpointForm;
import com.alibaba.nacos.ai.form.mcp.client.McpQueryForm;
import com.alibaba.nacos.ai.form.mcp.client.McpReleaseForm;
import com.alibaba.nacos.ai.form.mcp.client.McpSearchForm;
import com.alibaba.nacos.ai.service.mcp.McpClientApplicationService;
import com.alibaba.nacos.ai.service.runtime.AiHttpClientLifecycleService;
import com.alibaba.nacos.ai.service.search.AiResourceSearchApplicationService;
import com.alibaba.nacos.api.ai.model.agent.ClientLivenessInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.remote.AiRemoteConstants;
import com.alibaba.nacos.api.ai.remote.request.McpServerEndpointRequest;
import com.alibaba.nacos.api.ai.remote.request.ReleaseMcpServerRequest;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.core.model.form.PageForm;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link McpClientController}.
 */
class McpClientControllerTest {
    
    @Test
    void queryRenewsOptionalSharedClientAndDelegates() throws Exception {
        AiResourceSearchApplicationService search =
            mock(AiResourceSearchApplicationService.class);
        McpClientApplicationService application = mock(McpClientApplicationService.class);
        AiHttpClientLifecycleService lifecycle = mock(AiHttpClientLifecycleService.class);
        McpClientController controller = new McpClientController(search, application, lifecycle);
        McpQueryForm form = new McpQueryForm();
        form.setMcpName("demo");
        form.setVersion("1.0.0");
        McpServerDetailInfo expected = new McpServerDetailInfo();
        expected.setName("demo");
        when(application.query("public", "demo", "1.0.0")).thenReturn(expected);
        
        Result<McpServerDetailInfo> result = controller.query(form, "client-1");
        
        assertSame(expected, result.getData());
        verify(lifecycle).renewForQuery("client-1", "public");
    }
    
    @Test
    void releasePreservesDefaultAndExplicitDraftFlags() throws Exception {
        McpClientApplicationService application = mock(McpClientApplicationService.class);
        McpClientController controller = new McpClientController(
            mock(AiResourceSearchApplicationService.class), application,
            mock(AiHttpClientLifecycleService.class));
        McpReleaseForm direct = releaseForm("1.0.0");
        McpReleaseForm draft = releaseForm("1.0.1");
        draft.setCreateDraft("true");
        when(application.release(any(ReleaseMcpServerRequest.class), anyString()))
            .thenReturn("id");
        
        assertEquals("id", controller.release(direct).getData());
        assertEquals("id", controller.release(draft).getData());
        
        ArgumentCaptor<ReleaseMcpServerRequest> requests =
            ArgumentCaptor.forClass(ReleaseMcpServerRequest.class);
        verify(application, org.mockito.Mockito.times(2)).release(requests.capture(),
            eq("HTTP Client"));
        assertFalse(requests.getAllValues().get(0).isCreateDraft());
        assertTrue(requests.getAllValues().get(1).isCreateDraft());
    }
    
    @Test
    void endpointWritesUseSharedLifecycleAndApplicationService() throws Exception {
        McpClientApplicationService application = mock(McpClientApplicationService.class);
        AiHttpClientLifecycleService lifecycle = mock(AiHttpClientLifecycleService.class);
        McpClientController controller = new McpClientController(
            mock(AiResourceSearchApplicationService.class), application, lifecycle);
        ClientLivenessInfo liveness = new ClientLivenessInfo();
        liveness.setHeartbeatIntervalMillis(5000L);
        doAnswer(invocation -> {
            AiHttpClientLifecycleService.StatefulOperation operation = invocation.getArgument(3);
            operation.execute("HTTP_CLIENT@@client-1");
            return liveness;
        }).when(lifecycle).register(eq("client-1"), eq("AI"), eq("public"), any());
        doAnswer(invocation -> {
            AiHttpClientLifecycleService.StatefulOperation operation = invocation.getArgument(3);
            operation.execute("HTTP_CLIENT@@client-1");
            return null;
        }).when(lifecycle).deregister(eq("client-1"), eq("AI"), eq("public"), any());
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        
        Result<ClientLivenessInfo> registered = controller.registerEndpoint(endpointForm(),
            "client-1", "AI", httpRequest);
        Result<Void> deregistered = controller.deregisterEndpoint(endpointForm(), "client-1",
            "AI", httpRequest);
        
        assertSame(liveness, registered.getData());
        assertEquals(0, deregistered.getCode());
        ArgumentCaptor<McpServerEndpointRequest> requests =
            ArgumentCaptor.forClass(McpServerEndpointRequest.class);
        verify(application, org.mockito.Mockito.times(2)).operateEndpoint(requests.capture(),
            eq("HTTP_CLIENT@@client-1"), any());
        assertEquals(AiRemoteConstants.REGISTER_ENDPOINT,
            requests.getAllValues().get(0).getType());
        assertEquals(AiRemoteConstants.DE_REGISTER_ENDPOINT,
            requests.getAllValues().get(1).getType());
    }
    
    @Test
    void heartbeatUsesSharedLifecycle() throws Exception {
        AiHttpClientLifecycleService lifecycle = mock(AiHttpClientLifecycleService.class);
        McpClientController controller = new McpClientController(
            mock(AiResourceSearchApplicationService.class),
            mock(McpClientApplicationService.class), lifecycle);
        ClientLivenessInfo expected = new ClientLivenessInfo();
        when(lifecycle.heartbeat("client-1", "AI")).thenReturn(expected);
        
        assertSame(expected, controller.heartbeat("client-1", "AI").getData());
    }
    
    @Test
    void searchShouldValidateDefaultsAndDelegate() throws NacosException {
        AiResourceSearchApplicationService service =
            mock(AiResourceSearchApplicationService.class);
        McpClientController controller = new McpClientController(service,
            mock(McpClientApplicationService.class),
            mock(AiHttpClientLifecycleService.class));
        McpSearchForm form = new McpSearchForm();
        PageForm pageForm = new PageForm();
        Page<McpServerBasicInfo> page = new Page<>();
        McpServerBasicInfo item = new McpServerBasicInfo();
        item.setName("research-mcp");
        page.setPageItems(Collections.singletonList(item));
        when(service.searchMcpServers(form, 1, 100)).thenReturn(page);
        
        Result<Page<McpServerBasicInfo>> result = controller.search(form, pageForm);
        
        assertEquals("public", form.getNamespaceId());
        assertEquals("research-mcp", result.getData().getPageItems().get(0).getName());
        verify(service).searchMcpServers(form, 1, 100);
    }
    
    private McpReleaseForm releaseForm(String version) {
        McpReleaseForm result = new McpReleaseForm();
        result.setServerSpecification("{\"name\":\"demo\",\"protocol\":\"stdio\","
            + "\"versionDetail\":{\"version\":\"" + version + "\"}}");
        return result;
    }
    
    private McpEndpointForm endpointForm() {
        McpEndpointForm result = new McpEndpointForm();
        result.setMcpName("demo");
        result.setAddress("127.0.0.1");
        result.setPort(8080);
        result.setVersion("1.0.0");
        return result;
    }
}
