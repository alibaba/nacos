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

package com.alibaba.nacos.ai.service.search;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.model.search.AiResourceSearchDocument;
import com.alibaba.nacos.ai.service.agent.AgentPersistenceService;
import com.alibaba.nacos.ai.service.resource.AiResourceManager;
import com.alibaba.nacos.api.ai.model.agent.Agent;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionCatalog;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionDetail;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentAiResourceSearchTypeHandlerTest {
    
    private static final String NAMESPACE_ID = "public";
    
    private static final String AGENT_NAME = "agent-a";
    
    private static final String VERSION = "1.0.0";
    
    @Mock
    private AiResourceManager resourceManager;
    
    @Mock
    private AgentPersistenceService persistenceService;
    
    @Mock
    private AgentSearchIndexProjector projector;
    
    private AgentAiResourceSearchTypeHandler handler;
    
    @BeforeEach
    void setUp() {
        handler = new AgentAiResourceSearchTypeHandler(resourceManager, persistenceService,
            projector);
    }
    
    @Test
    void shouldDeclareAgentProjectionGeneration() {
        assertEquals(1, handler.projectionVersion());
        assertEquals(Collections.singletonList(Constants.Agent.RESOURCE_TYPE_AGENT),
            handler.resourceTypes());
        assertEquals(Collections.singletonList(Constants.Agent.RESOURCE_TYPE_AGENT),
            new AgentAiResourceSearchTypeHandler(resourceManager, persistenceService)
                .resourceTypes());
        assertEquals(0, new NoopHandler().projectionVersion());
    }
    
    @Test
    void shouldDeclareProductionInjectionConstructor() throws NoSuchMethodException {
        assertNotNull(AgentAiResourceSearchTypeHandler.class.getConstructor(
            AiResourceManager.class, AgentPersistenceService.class).getAnnotation(
                Autowired.class));
    }
    
    @Test
    void shouldProjectOnlyEnabledCommonLatestOnlineVersion() throws Exception {
        AiResource meta = meta("enable", AGENT_NAME);
        Agent agent = agent(VERSION);
        AgentVersionDetail latest = version("online");
        AiResourceIndexProjection expected = projection("digest");
        when(resourceManager.findMeta(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(meta);
        when(persistenceService.getAgent(NAMESPACE_ID, AGENT_NAME)).thenReturn(agent);
        when(persistenceService.getAgentVersion(NAMESPACE_ID, AGENT_NAME, VERSION))
            .thenReturn(latest);
        when(projector.project(agent, latest)).thenReturn(expected);
        
        assertNull(handler.project(NAMESPACE_ID, "skill", AGENT_NAME, null));
        assertNull(handler.project(NAMESPACE_ID, Constants.Agent.RESOURCE_TYPE_AGENT,
            AGENT_NAME, "2.0.0"));
        assertSame(expected, handler.project(NAMESPACE_ID,
            Constants.Agent.RESOURCE_TYPE_AGENT, AGENT_NAME, VERSION));
    }
    
    @Test
    void shouldSkipMissingDisabledLatestOrOfflineAgent() throws Exception {
        assertNull(handler.project(NAMESPACE_ID, Constants.Agent.RESOURCE_TYPE_AGENT,
            AGENT_NAME, null));
        AiResource disabled = meta("disable", AGENT_NAME);
        when(resourceManager.findMeta(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(disabled);
        assertNull(handler.project(NAMESPACE_ID, Constants.Agent.RESOURCE_TYPE_AGENT,
            AGENT_NAME, null));
        
        AiResource enabled = meta("enable", AGENT_NAME);
        Agent noCatalog = agent(null);
        when(resourceManager.findMeta(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(enabled);
        when(persistenceService.getAgent(NAMESPACE_ID, AGENT_NAME)).thenReturn(noCatalog);
        assertNull(handler.project(NAMESPACE_ID, Constants.Agent.RESOURCE_TYPE_AGENT,
            AGENT_NAME, null));
        noCatalog.setVersionCatalog(null);
        assertNull(handler.project(NAMESPACE_ID, Constants.Agent.RESOURCE_TYPE_AGENT,
            AGENT_NAME, null));
        
        Agent agent = agent(VERSION);
        when(persistenceService.getAgent(NAMESPACE_ID, AGENT_NAME)).thenReturn(agent);
        when(persistenceService.getAgentVersion(NAMESPACE_ID, AGENT_NAME, VERSION))
            .thenReturn(version("offline"));
        assertNull(handler.project(NAMESPACE_ID, Constants.Agent.RESOURCE_TYPE_AGENT,
            AGENT_NAME, null));
    }
    
    @Test
    void shouldScanBoundedPageAndIsolateProjectionFailures() throws Exception {
        AiResource good = meta("enable", "good");
        AiResource failed = meta("enable", "failed");
        Page<AiResource> page = new Page<>();
        page.setPageItems(Arrays.asList(null, good, failed));
        when(resourceManager.listMetaByType(NAMESPACE_ID,
            Constants.Agent.RESOURCE_TYPE_AGENT, null, null, 1, 3)).thenReturn(page);
        Agent goodAgent = agent(VERSION);
        AgentVersionDetail goodVersion = version("online");
        when(persistenceService.getAgent(NAMESPACE_ID, "good")).thenReturn(goodAgent);
        when(persistenceService.getAgentVersion(NAMESPACE_ID, "good", VERSION))
            .thenReturn(goodVersion);
        when(projector.project(goodAgent, goodVersion)).thenReturn(projection("good"));
        when(persistenceService.getAgent(NAMESPACE_ID, "failed"))
            .thenThrow(new NacosException(NacosException.SERVER_ERROR, "broken"));
        
        AiResourceIndexSourcePage result = handler.scan(NAMESPACE_ID,
            Constants.Agent.RESOURCE_TYPE_AGENT, 1, 3);
        
        assertTrue(result.hasMore());
        assertEquals(3, result.getItems().size());
        assertNull(result.getItems().get(0).getProjection());
        assertEquals("good", result.getItems().get(1).getResourceName());
        assertEquals("failed", result.getItems().get(2).getResourceName());
        assertTrue(result.getItems().get(2).getFailure() instanceof NacosException);
        assertFalse(handler.scan(NAMESPACE_ID, "skill", 1, 3).hasMore());
        when(resourceManager.listMetaByType(NAMESPACE_ID,
            Constants.Agent.RESOURCE_TYPE_AGENT, null, null, 2, 3)).thenReturn(null);
        assertTrue(handler.scan(NAMESPACE_ID, Constants.Agent.RESOURCE_TYPE_AGENT, 2, 3)
            .getItems().isEmpty());
        Page<AiResource> shortPage = new Page<>();
        shortPage.setPageItems(Collections.singletonList(null));
        when(resourceManager.listMetaByType(NAMESPACE_ID,
            Constants.Agent.RESOURCE_TYPE_AGENT, null, null, 3, 4)).thenReturn(shortPage);
        assertFalse(handler.scan(NAMESPACE_ID, Constants.Agent.RESOURCE_TYPE_AGENT, 3, 4)
            .hasMore());
    }
    
    @Test
    void shouldValidateCurrentReadableDigest() throws Exception {
        AiResource meta = meta("enable", AGENT_NAME);
        Agent agent = agent(VERSION);
        AgentVersionDetail latest = version("online");
        AiResourceSearchDocument document = projection("digest").getDocument();
        when(resourceManager.findMeta(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(meta);
        when(persistenceService.getAgent(NAMESPACE_ID, AGENT_NAME)).thenReturn(agent);
        when(persistenceService.getAgentVersion(NAMESPACE_ID, AGENT_NAME, VERSION))
            .thenReturn(latest);
        when(projector.project(agent, latest)).thenReturn(projection("digest"));
        
        assertTrue(handler.isCurrent(document));
        document.setSourceDigest("stale");
        assertFalse(handler.isCurrent(document));
        document.setSourceDigest("digest");
        document.setMetadata("corrupt");
        assertFalse(handler.isCurrent(document));
        verify(resourceManager, times(3)).ensureReadableOrNotFound(meta,
            "Agent not found: " + AGENT_NAME);
    }
    
    @Test
    void shouldRejectUnreadableOrInvalidCurrentDocument() throws Exception {
        assertFalse(handler.isCurrent(null));
        AiResourceSearchDocument wrongType = new AiResourceSearchDocument();
        wrongType.setResourceType("skill");
        assertFalse(handler.isCurrent(wrongType));
        AiResourceSearchDocument document = projection("digest").getDocument();
        AiResource disabled = meta("disable", AGENT_NAME);
        when(resourceManager.findMeta(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(disabled);
        assertFalse(handler.isCurrent(document));
        
        AiResource enabled = meta("enable", AGENT_NAME);
        when(resourceManager.findMeta(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(enabled);
        doThrow(new NacosException(NacosException.NOT_FOUND, "hidden"))
            .when(resourceManager).ensureReadableOrNotFound(enabled,
                "Agent not found: " + AGENT_NAME);
        assertFalse(handler.isCurrent(document));
        verify(persistenceService, never()).getAgent(NAMESPACE_ID, AGENT_NAME);
    }
    
    @Test
    void shouldCheckCanonicalExistenceByType() {
        when(resourceManager.findMeta(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(meta("enable", AGENT_NAME));
        
        assertTrue(handler.exists(NAMESPACE_ID, Constants.Agent.RESOURCE_TYPE_AGENT, AGENT_NAME));
        assertFalse(handler.exists(NAMESPACE_ID, "skill", AGENT_NAME));
    }
    
    private AiResource meta(String status, String name) {
        AiResource result = new AiResource();
        result.setNamespaceId(NAMESPACE_ID);
        result.setName(name);
        result.setType(Constants.Agent.RESOURCE_TYPE_AGENT);
        result.setStatus(status);
        return result;
    }
    
    private Agent agent(String latestVersion) {
        Agent result = new Agent();
        result.setNamespaceId(NAMESPACE_ID);
        result.setAgentName(AGENT_NAME);
        if (latestVersion != null) {
            AgentVersionCatalog catalog = new AgentVersionCatalog();
            catalog.setLatestVersion(latestVersion);
            result.setVersionCatalog(catalog);
        } else {
            result.setVersionCatalog(new AgentVersionCatalog());
        }
        return result;
    }
    
    private AgentVersionDetail version(String status) {
        AgentVersionDetail result = new AgentVersionDetail();
        result.setVersion(VERSION);
        result.setStatus(status);
        return result;
    }
    
    private AiResourceIndexProjection projection(String digest) {
        AiResourceSearchDocument document = new AiResourceSearchDocument();
        document.setNamespaceId(NAMESPACE_ID);
        document.setResourceType(Constants.Agent.RESOURCE_TYPE_AGENT);
        document.setResourceName(AGENT_NAME);
        document.setResourceVersion(VERSION);
        document.setSourceDigest(digest);
        return new AiResourceIndexProjection(document, null, null, null);
    }
    
    private static final class NoopHandler implements AiResourceSearchTypeHandler {
        
        @Override
        public List<String> resourceTypes() {
            return Collections.emptyList();
        }
        
        @Override
        public AiResourceIndexProjection project(String namespaceId, String resourceType,
            String resourceName, String version) {
            return null;
        }
        
        @Override
        public AiResourceIndexSourcePage scan(String namespaceId, String resourceType, int pageNo,
            int pageSize) {
            return null;
        }
        
        @Override
        public boolean isCurrent(AiResourceSearchDocument document) {
            return false;
        }
        
        @Override
        public boolean exists(String namespaceId, String resourceType, String resourceName) {
            return false;
        }
    }
}
