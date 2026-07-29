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

import com.alibaba.nacos.api.ai.model.agent.Agent;
import com.alibaba.nacos.api.ai.model.agent.AgentCallInterface;
import com.alibaba.nacos.api.ai.model.agent.AgentDraftCreateRequest;
import com.alibaba.nacos.api.ai.model.agent.AgentDraftUpdateRequest;
import com.alibaba.nacos.api.ai.model.agent.AgentLabelsUpdateRequest;
import com.alibaba.nacos.api.ai.model.agent.AgentOverview;
import com.alibaba.nacos.api.ai.model.agent.AgentProvider;
import com.alibaba.nacos.api.ai.model.agent.AgentSummary;
import com.alibaba.nacos.api.ai.model.agent.AgentUpdateRequest;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionCommand;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionDetail;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionSummary;
import com.alibaba.nacos.api.ai.model.agent.RuntimeEndpointSnapshot;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.api.utils.json.JsonUtils;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.utils.HttpMethod;
import com.alibaba.nacos.maintainer.client.constants.Constants;
import com.alibaba.nacos.maintainer.client.model.HttpRequest;
import com.alibaba.nacos.maintainer.client.remote.ClientHttpProxy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AgentMaintainerServiceImpl}.
 *
 * @author Nacos
 */
@ExtendWith(MockitoExtension.class)
class AgentMaintainerServiceImplTest {
    
    private static final String NAMESPACE_ID = "test-namespace";
    
    private static final String AGENT_NAME = "test-agent";
    
    private static final String VERSION = "1.0.0";
    
    @Mock
    private ClientHttpProxy clientHttpProxy;
    
    private AgentMaintainerService service;
    
    @BeforeEach
    void setUp() {
        service = new AgentMaintainerServiceImpl(new AiMaintainerHttpContext(clientHttpProxy));
    }
    
    @Test
    void testCreateDraftAndGetAgent() throws NacosException {
        AgentOverview overview = new AgentOverview();
        overview.setAgent(agent());
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class)))
            .thenReturn(response(versionDetail()), response(overview));
        
        AgentDraftCreateRequest createRequest = new AgentDraftCreateRequest();
        createRequest.setAgentName(AGENT_NAME);
        createRequest.setVersion(VERSION);
        createRequest.setCallInterfaces(Collections.<AgentCallInterface>emptyList());
        AgentProvider provider = new AgentProvider();
        provider.setName("provider");
        createRequest.setProvider(provider);
        createRequest.setTags(Arrays.asList("east", "prod,blue"));
        createRequest.setExtensions(Collections.<String, Object>singletonMap("region", "east"));
        AgentVersionDetail created = service.createDraft(NAMESPACE_ID, createRequest);
        AgentOverview queried = service.getAgent(NAMESPACE_ID, AGENT_NAME);
        
        assertEquals(AGENT_NAME, created.getAgentName());
        assertEquals(AGENT_NAME, queried.getAgent().getAgentName());
        List<HttpRequest> requests = captureRequests(2);
        assertRequest(requests.get(0), HttpMethod.POST, rootPath() + "/draft");
        Map<String, String> createParams = requests.get(0).getParamValues();
        assertEquals(NAMESPACE_ID, createParams.get("namespaceId"));
        assertEquals("provider",
            JsonUtils.toObj(createParams.get("provider"), AgentProvider.class).getName());
        assertEquals(Arrays.asList("east", "prod,blue"),
            JsonUtils.toObj(createParams.get("tags"), List.class));
        assertEquals("east",
            JsonUtils.toObj(createParams.get("extensions"), Map.class).get("region"));
        assertEquals(VERSION, createParams.get("version"));
        assertRequest(requests.get(1), HttpMethod.GET, rootPath());
        assertEquals(NAMESPACE_ID, requests.get(1).getParamValues().get("namespaceId"));
    }
    
    @Test
    void testUpdateAndDeleteAgent() throws NacosException {
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class)))
            .thenReturn(response(agent()), response(null));
        AgentUpdateRequest updateRequest = new AgentUpdateRequest();
        updateRequest.setAgentName(AGENT_NAME);
        
        Agent updated = service.updateAgent(NAMESPACE_ID, updateRequest);
        service.deleteAgent(NAMESPACE_ID, AGENT_NAME);
        
        assertEquals(AGENT_NAME, updated.getAgentName());
        List<HttpRequest> requests = captureRequests(2);
        assertRequest(requests.get(0), HttpMethod.PUT, rootPath());
        assertEquals(AGENT_NAME, requests.get(0).getParamValues().get("agentName"));
        assertRequest(requests.get(1), HttpMethod.DELETE, rootPath());
        assertEquals(AGENT_NAME, requests.get(1).getParamValues().get("agentName"));
    }
    
    @Test
    void testListAgents() throws NacosException {
        Page<AgentSummary> expected = new Page<>();
        expected.setTotalCount(1);
        expected.setPageItems(Collections.singletonList(new AgentSummary()));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class)))
            .thenReturn(response(expected));
        
        Page<AgentSummary> actual = service.listAgents(NAMESPACE_ID, "agent", "east", "PUBLIC",
            "owner", "download_count", 2, 20);
        
        assertEquals(1, actual.getTotalCount());
        HttpRequest request = captureRequests(1).get(0);
        assertRequest(request, HttpMethod.GET, rootPath() + "/list");
        assertEquals("east", request.getParamValues().get("bizTag"));
        assertEquals("2", request.getParamValues().get("pageNo"));
        assertEquals("20", request.getParamValues().get("pageSize"));
        assertEquals("download_count", request.getParamValues().get("orderBy"));
    }
    
    @Test
    void testVersionAndRuntimeReads() throws NacosException {
        Page<AgentVersionSummary> versionPage = new Page<>();
        versionPage.setPageItems(Collections.singletonList(new AgentVersionSummary()));
        RuntimeEndpointSnapshot snapshot = new RuntimeEndpointSnapshot();
        snapshot.setItems(Collections.emptyList());
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class)))
            .thenReturn(response(versionPage), response(versionDetail()), response(snapshot));
        
        service.listAgentVersions(NAMESPACE_ID, AGENT_NAME, "draft", 1, 10);
        service.getAgentVersion(NAMESPACE_ID, AGENT_NAME, VERSION);
        RuntimeEndpointSnapshot actual =
            service.getRuntimeEndpoints(NAMESPACE_ID, AGENT_NAME, "A2A", VERSION);
        
        assertNotNull(actual.getItems());
        List<HttpRequest> requests = captureRequests(3);
        assertRequest(requests.get(0), HttpMethod.GET, rootPath() + "/versions");
        assertEquals("draft", requests.get(0).getParamValues().get("status"));
        assertRequest(requests.get(1), HttpMethod.GET, rootPath() + "/version");
        assertEquals(VERSION, requests.get(1).getParamValues().get("version"));
        assertRequest(requests.get(2), HttpMethod.GET, rootPath() + "/runtime-endpoints");
        assertEquals("A2A", requests.get(2).getParamValues().get("protocol"));
    }
    
    @Test
    void testDraftOperations() throws NacosException {
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class)))
            .thenReturn(response(versionDetail()), response(versionDetail()), response(null));
        AgentDraftCreateRequest createRequest = new AgentDraftCreateRequest();
        createRequest.setAgentName(AGENT_NAME);
        createRequest.setVersion(VERSION);
        createRequest.setCallInterfaces(Collections.<AgentCallInterface>emptyList());
        AgentDraftUpdateRequest updateRequest = new AgentDraftUpdateRequest();
        updateRequest.setAgentName(AGENT_NAME);
        updateRequest.setVersion(VERSION);
        updateRequest.setCallInterfaces(Collections.<AgentCallInterface>emptyList());
        
        service.createDraft(NAMESPACE_ID, createRequest);
        service.updateDraft(NAMESPACE_ID, updateRequest);
        service.deleteDraft(NAMESPACE_ID, AGENT_NAME, VERSION);
        
        List<HttpRequest> requests = captureRequests(3);
        assertRequest(requests.get(0), HttpMethod.POST, rootPath() + "/draft");
        assertEquals(Collections.emptyList(),
            JsonUtils.toObj(requests.get(0).getParamValues().get("callInterfaces"), List.class));
        assertRequest(requests.get(1), HttpMethod.PUT, rootPath() + "/draft");
        assertEquals(Collections.emptyList(),
            JsonUtils.toObj(requests.get(1).getParamValues().get("callInterfaces"), List.class));
        assertRequest(requests.get(2), HttpMethod.DELETE, rootPath() + "/draft");
        assertEquals(VERSION, requests.get(2).getParamValues().get("version"));
    }
    
    @Test
    void testLifecycleOperations() throws NacosException {
        AgentVersionSummary summary = new AgentVersionSummary();
        summary.setVersion(VERSION);
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class)))
            .thenReturn(response(summary), response(summary), response(summary), response(summary),
                response(summary), response(summary));
        final AgentVersionCommand command = versionCommand();
        
        service.submit(NAMESPACE_ID, command);
        service.publish(NAMESPACE_ID, command);
        service.forcePublish(NAMESPACE_ID, command);
        service.redraft(NAMESPACE_ID, command);
        service.online(NAMESPACE_ID, command);
        service.offline(NAMESPACE_ID, command);
        
        List<HttpRequest> requests = captureRequests(6);
        List<String> paths = Arrays.asList("/submit", "/publish", "/force-publish", "/redraft",
            "/online", "/offline");
        for (int i = 0; i < paths.size(); i++) {
            assertRequest(requests.get(i), HttpMethod.POST, rootPath() + paths.get(i));
            assertEquals(NAMESPACE_ID,
                requests.get(i).getParamValues().get("namespaceId"));
            assertEquals(VERSION, requests.get(i).getParamValues().get("version"));
        }
    }
    
    @Test
    void testUpdateLabels() throws NacosException {
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class)))
            .thenReturn(response(agent()));
        AgentLabelsUpdateRequest request = new AgentLabelsUpdateRequest();
        request.setAgentName(AGENT_NAME);
        request.setLabels(Collections.singletonMap("stable", VERSION));
        
        Agent result = service.updateLabels(NAMESPACE_ID, request);
        
        assertEquals(AGENT_NAME, result.getAgentName());
        HttpRequest httpRequest = captureRequests(1).get(0);
        assertRequest(httpRequest, HttpMethod.PUT, rootPath() + "/labels");
        assertEquals(NAMESPACE_ID, httpRequest.getParamValues().get("namespaceId"));
        assertEquals(VERSION,
            JsonUtils.toObj(httpRequest.getParamValues().get("labels"), Map.class).get("stable"));
    }
    
    @Test
    void testDefaultNamespaceOverloads() throws NacosException {
        AgentOverview overview = new AgentOverview();
        overview.setAgent(agent());
        AgentVersionSummary summary = new AgentVersionSummary();
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class)))
            .thenReturn(response(overview), response(summary), response(summary));
        
        service.getAgent(AGENT_NAME);
        service.submit(versionCommand());
        AgentVersionCommand explicitCommand = versionCommand();
        service.submit(NAMESPACE_ID, explicitCommand);
        
        List<HttpRequest> requests = captureRequests(3);
        assertEquals(com.alibaba.nacos.api.common.Constants.DEFAULT_NAMESPACE_ID,
            requests.get(0).getParamValues().get("namespaceId"));
        assertEquals(com.alibaba.nacos.api.common.Constants.DEFAULT_NAMESPACE_ID,
            requests.get(1).getParamValues().get("namespaceId"));
        assertEquals(NAMESPACE_ID, requests.get(2).getParamValues().get("namespaceId"));
    }
    
    @Test
    void testEveryConvenienceOverloadDefaultsMissingNamespaceToPublic() throws NacosException {
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class)))
            .thenReturn(response(null));
        AgentUpdateRequest updateRequest = new AgentUpdateRequest();
        updateRequest.setAgentName(AGENT_NAME);
        AgentDraftCreateRequest draftCreateRequest = new AgentDraftCreateRequest();
        draftCreateRequest.setAgentName(AGENT_NAME);
        draftCreateRequest.setVersion(VERSION);
        draftCreateRequest.setCallInterfaces(Collections.<AgentCallInterface>emptyList());
        AgentDraftUpdateRequest draftUpdateRequest = new AgentDraftUpdateRequest();
        draftUpdateRequest.setAgentName(AGENT_NAME);
        draftUpdateRequest.setVersion(VERSION);
        draftUpdateRequest.setCallInterfaces(Collections.<AgentCallInterface>emptyList());
        final AgentVersionCommand command = versionCommand();
        AgentLabelsUpdateRequest labelsRequest = new AgentLabelsUpdateRequest();
        labelsRequest.setAgentName(AGENT_NAME);
        
        service.getAgent(AGENT_NAME);
        service.updateAgent(updateRequest);
        service.deleteAgent(AGENT_NAME);
        service.listAgents(null, null, null, null, null, 1, 100);
        service.listAgentVersions(AGENT_NAME, null, 1, 100);
        service.getAgentVersion(AGENT_NAME, VERSION);
        service.getRuntimeEndpoints(AGENT_NAME, "A2A", null);
        service.createDraft(draftCreateRequest);
        service.updateDraft(draftUpdateRequest);
        service.deleteDraft(AGENT_NAME, VERSION);
        service.submit(command);
        service.publish(command);
        service.forcePublish(command);
        service.redraft(command);
        service.online(command);
        service.offline(command);
        service.updateLabels(labelsRequest);
        
        for (HttpRequest request : captureRequests(17)) {
            assertEquals(com.alibaba.nacos.api.common.Constants.DEFAULT_NAMESPACE_ID,
                request.getParamValues().get("namespaceId"));
        }
    }
    
    @Test
    void testOptionalQueryParametersAreOmitted() throws NacosException {
        Page<AgentSummary> page = new Page<>();
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class)))
            .thenReturn(response(page));
        
        service.listAgents(NAMESPACE_ID, null, null, null, null, null, 1, 100);
        
        HttpRequest request = captureRequests(1).get(0);
        assertFalse(request.getParamValues().containsKey("agentName"));
        assertFalse(request.getParamValues().containsKey("bizTag"));
        assertFalse(request.getParamValues().containsKey("orderBy"));
    }
    
    private List<HttpRequest> captureRequests(int count) throws NacosException {
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(clientHttpProxy, times(count)).executeSyncHttpRequest(captor.capture());
        return captor.getAllValues();
    }
    
    private void assertRequest(HttpRequest request, String method, String path) {
        assertEquals(method, request.getHttpMethod());
        assertEquals(path, request.getPath());
        assertNull(request.getBody());
    }
    
    private String rootPath() {
        return Constants.AdminApiPath.AI_AGENTS_ADMIN_PATH;
    }
    
    private Agent agent() {
        Agent result = new Agent();
        result.setAgentName(AGENT_NAME);
        return result;
    }
    
    private AgentVersionDetail versionDetail() {
        AgentVersionDetail result = new AgentVersionDetail();
        result.setAgentName(AGENT_NAME);
        result.setVersion(VERSION);
        return result;
    }
    
    private AgentVersionCommand versionCommand() {
        AgentVersionCommand result = new AgentVersionCommand();
        result.setAgentName(AGENT_NAME);
        result.setVersion(VERSION);
        return result;
    }
    
    private HttpRestResult<String> response(Object data) {
        HttpRestResult<String> result = new HttpRestResult<>();
        result.setData(JsonUtils.toJson(Result.success(data)));
        return result;
    }
}
