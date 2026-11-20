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

package com.alibaba.nacos.maintainer.client.ai;

import com.alibaba.nacos.api.ai.model.hub.HubAdjustShareRequest;
import com.alibaba.nacos.api.ai.model.hub.HubForceUnshareRequest;
import com.alibaba.nacos.api.ai.model.hub.HubResourceShareState;
import com.alibaba.nacos.api.ai.model.hub.HubShareResourceRequest;
import com.alibaba.nacos.api.ai.model.hub.HubShareResult;
import com.alibaba.nacos.api.ai.model.hub.HubUnshareRequest;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.ai.model.hub.HubShareOutcome;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.maintainer.client.model.HttpRequest;
import com.alibaba.nacos.maintainer.client.remote.ClientHttpProxy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Typed management transport, controlled failures and validation before network IO. */
class HubResourceMaintainerServiceTest {
    
    private final ClientHttpProxy proxy = mock(ClientHttpProxy.class);
    private final HubResourceMaintainerService service =
        new HubResourceMaintainerServiceImpl(new AiMaintainerHttpContext(proxy));
    
    @ParameterizedTest
    @ValueSource(strings = {"mcp", "agent", "prompt", "agentspec"})
    void shareUsesTypedManagementRouteAndPreservesLargeIds(String type) throws Exception {
        HubShareResult result = new HubShareResult();
        result.setHubEntryId(9007199254740993L);
        result.setMetaVersion(2L);
        result.setOutcome(HubShareOutcome.CREATED);
        respond(Result.success(result));
        HubShareResourceRequest request = shareRequest();
        request.setExpectedSourceResourceId(9007199254740995L);
        
        assertEquals(9007199254740993L, service.share(type, request).getHubEntryId());
        HttpRequest http = capture();
        assertEquals("/v3/admin/ai/hub/" + type + "/share", http.getPath());
        assertEquals("POST", http.getHttpMethod());
        assertEquals("source", http.getParamValues().get("sourceNamespaceId"));
        assertEquals("same-name", http.getParamValues().get("sourceName"));
        assertEquals("1.0.0", http.getParamValues().get("sourceVersion"));
        assertEquals("9007199254740995", http.getParamValues().get("expectedSourceResourceId"));
        assertEquals("source", request.getSourceNamespaceId());
    }
    
    @Test
    void publicResourceFacadesSelectTheirFixedHubRoute() throws Exception {
        AiMaintainerHttpContext context = new AiMaintainerHttpContext(proxy);
        HubShareResult result = new HubShareResult();
        result.setHubEntryId(501L);
        result.setMetaVersion(1L);
        result.setOutcome(HubShareOutcome.CREATED);
        for (String type : new String[] {"mcp", "agent", "prompt", "agentspec"}) {
            org.mockito.Mockito.clearInvocations(proxy);
            respond(Result.success(result));
            if ("mcp".equals(type)) {
                new McpMaintainerServiceImpl(context).shareHubMcp(
                    typedShareRequest(new com.alibaba.nacos.api.ai.model.hub.HubShareMcpRequest()));
            } else if ("agent".equals(type)) {
                new AgentMaintainerServiceImpl(context).shareHubAgent(typedShareRequest(
                    new com.alibaba.nacos.api.ai.model.hub.HubShareAgentRequest()));
            } else if ("prompt".equals(type)) {
                new PromptMaintainerServiceImpl(context).shareHubPrompt(typedShareRequest(
                    new com.alibaba.nacos.api.ai.model.hub.HubSharePromptRequest()));
            } else {
                new AgentSpecMaintainerServiceImpl(context).shareHubAgentSpec(typedShareRequest(
                    new com.alibaba.nacos.api.ai.model.hub.HubShareAgentSpecRequest()));
            }
            assertEquals("/v3/admin/ai/hub/" + type + "/share", capture().getPath());
        }
    }
    
    @Test
    void invalidRequestsFailBeforeAnyHttp() {
        for (String type : new String[] {"skill", "../skills", "MCP", "", "unknown"}) {
            assertThrows(NacosException.class, () -> service.share(type, shareRequest()));
        }
        assertThrows(NacosException.class, () -> service.share("prompt", null));
        HubShareResourceRequest request = shareRequest();
        request.setSourceNamespaceId("__nacos_ai_hub__");
        assertThrows(NacosException.class, () -> service.share("prompt", request));
        request.setSourceNamespaceId("source");
        request.setSourceVersion("latest");
        assertThrows(NacosException.class, () -> service.share("prompt", request));
        assertThrows(NacosException.class,
            () -> service.adjust("prompt", new HubAdjustShareRequest()));
        assertThrows(NacosException.class,
            () -> service.unshare("prompt", new HubUnshareRequest()));
        assertThrows(NacosException.class,
            () -> service.forceUnshare("prompt", new HubForceUnshareRequest()));
        assertThrows(NacosException.class, () -> service.governanceState("prompt", -1L));
        verifyNoInteractions(proxy);
    }
    
    @Test
    void unsharedSourceHasNullableStateAndNoMutation() throws Exception {
        respond(Result.success(null));
        assertNull(service.sourceState("agent", "source", "same-name"));
        HttpRequest http = capture();
        assertEquals("GET", http.getHttpMethod());
        assertEquals("/v3/admin/ai/hub/agent/source-share", http.getPath());
        assertFalse(http.getParamValues().containsKey("hubEntryId"));
    }
    
    @Test
    void governanceCannotAcceptStateForAnotherIdentity() throws Exception {
        HubResourceShareState state = new HubResourceShareState();
        state.setHubEntryId(502L);
        state.setMetaVersion(3L);
        state.setSharedVersion("1.0.0");
        respond(Result.success(state));
        assertEquals(NacosException.SERVER_ERROR,
            assertThrows(NacosException.class, () -> service.governanceState("agentspec", 501L))
                .getErrCode());
        assertEquals("console/ai-hub", capture().getResource().getResource());
    }
    
    @Test
    void malformedMutationResponseNeverLooksSuccessful() throws Exception {
        HttpRestResult<String> response = new HttpRestResult<>();
        response.setData("not-json");
        when(proxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(response);
        assertEquals(NacosException.SERVER_ERROR,
            assertThrows(NacosException.class, () -> service.share("mcp", shareRequest()))
                .getErrCode());
    }
    
    @Test
    void adjustAndUnshareRetainOptimisticRevision() throws Exception {
        HubShareResult result = new HubShareResult();
        result.setHubEntryId(501L);
        result.setMetaVersion(9L);
        respond(Result.success(result));
        HubAdjustShareRequest request = new HubAdjustShareRequest();
        request.setHubEntryId(501L);
        request.setExpectedMetaVersion(8L);
        request.setTargetVersion("2.0.0");
        assertEquals(9L, service.adjust("prompt", request).getMetaVersion());
        HttpRequest http = capture();
        assertEquals("501", http.getParamValues().get("hubEntryId"));
        assertEquals("8", http.getParamValues().get("expectedMetaVersion"));
        assertEquals("2.0.0", http.getParamValues().get("targetVersion"));
    }
    
    private HubShareResourceRequest shareRequest() {
        HubShareResourceRequest request = new HubShareResourceRequest();
        request.setSourceNamespaceId("source");
        request.setSourceName("same-name");
        request.setSourceVersion("1.0.0");
        return request;
    }
    
    private void respond(Object body) throws Exception {
        HttpRestResult<String> response = new HttpRestResult<>();
        response.setData(JacksonUtils.toJson(body));
        when(proxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(response);
    }
    
    private HttpRequest capture() throws Exception {
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(proxy).executeSyncHttpRequest(captor.capture());
        return captor.getValue();
    }
    
    @Test
    void mcpFacadeImportsThePinnedEntryWithoutDirectSourceFields() throws Exception {
        com.alibaba.nacos.api.ai.model.hub.HubMcpImportRequest request =
            new com.alibaba.nacos.api.ai.model.hub.HubMcpImportRequest();
        request.setHubEntryId(501L);
        request.setTargetNamespaceId("target");
        request.setTargetName("imported");
        request.setTargetVersion("0.0.1");
        com.alibaba.nacos.api.ai.model.hub.HubResourceTransferResult created =
            new com.alibaba.nacos.api.ai.model.hub.HubResourceTransferResult();
        created.setResourceType("mcp");
        created.setHubEntryId(501L);
        created.setTargetResourceId(601L);
        created.setTargetNamespaceId("target");
        created.setTargetName("imported");
        created.setDraftVersion("0.0.1");
        created.setStatus("draft");
        created.setOrigin("HUB");
        respond(Result.success(created));
        McpMaintainerService mcp = new McpMaintainerServiceImpl(new AiMaintainerHttpContext(proxy));
        assertEquals(601L, mcp.importHubMcp(request).getTargetResourceId());
        HttpRequest http = capture();
        assertEquals("/v3/admin/ai/hub/mcp/import", http.getPath());
        assertFalse(http.getParamValues().containsKey("sourceNamespaceId"));
        request.setSourceName("source-alias");
        org.mockito.Mockito.clearInvocations(proxy);
        assertThrows(NacosException.class, () -> mcp.importHubMcp(request));
        verifyNoInteractions(proxy);
    }
    
    private <T extends HubShareResourceRequest> T typedShareRequest(T request) {
        request.setSourceNamespaceId("source");
        request.setSourceName("same-name");
        request.setSourceVersion("1.0.0");
        return request;
    }
}
