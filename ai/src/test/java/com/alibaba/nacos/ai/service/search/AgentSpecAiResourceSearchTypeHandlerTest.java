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
import com.alibaba.nacos.ai.model.AiResourceVersion;
import com.alibaba.nacos.ai.model.search.AiResourceSearchDocument;
import com.alibaba.nacos.ai.service.agentspecs.AgentSpecStorageReader;
import com.alibaba.nacos.ai.service.resource.AiResourceManager;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpec;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentSpecAiResourceSearchTypeHandlerTest {
    
    private static final String NAMESPACE_ID = "public";
    
    private static final String NAME = "demo-worker";
    
    private static final String VERSION = "1.0.0";
    
    @Mock
    private AiResourceManager resourceManager;
    
    @Mock
    private AgentSpecStorageReader storageReader;
    
    @Mock
    private AgentSpecSearchIndexProjector projector;
    
    private AgentSpecAiResourceSearchTypeHandler handler;
    
    @BeforeEach
    void setUp() {
        handler = new AgentSpecAiResourceSearchTypeHandler(resourceManager, storageReader,
            projector);
    }
    
    @Test
    void shouldDeclareAgentSpecProjectionGeneration() {
        assertEquals(1, handler.projectionVersion());
        assertEquals(Collections.singletonList(Constants.AgentSpecs.RESOURCE_TYPE_AGENTSPEC),
            handler.resourceTypes());
        assertEquals(Collections.singletonList(Constants.AgentSpecs.RESOURCE_TYPE_AGENTSPEC),
            new AgentSpecAiResourceSearchTypeHandler(resourceManager, storageReader)
                .resourceTypes());
    }
    
    @Test
    void shouldSelectRuntimeConstructorDuringSpringInstantiation() {
        try (AnnotationConfigApplicationContext context =
            new AnnotationConfigApplicationContext()) {
            context.registerBean(AiResourceManager.class, () -> resourceManager);
            context.registerBean(AgentSpecStorageReader.class, () -> storageReader);
            context.register(AgentSpecAiResourceSearchTypeHandler.class);
            context.refresh();
            
            AgentSpecAiResourceSearchTypeHandler bean = context.getBean(
                AgentSpecAiResourceSearchTypeHandler.class);
            assertEquals(Collections.singletonList(Constants.AgentSpecs.RESOURCE_TYPE_AGENTSPEC),
                bean.resourceTypes());
        }
    }
    
    @Test
    void shouldProjectOnlyEnabledLatestOnlineVersion() throws Exception {
        AiResource meta = meta("enable", NAME, VERSION);
        AiResourceVersion version = version("online");
        AgentSpec agentSpec = new AgentSpec();
        AiResourceIndexProjection expected = projection("digest");
        when(resourceManager.findMeta(NAMESPACE_ID, NAME,
            Constants.AgentSpecs.RESOURCE_TYPE_AGENTSPEC)).thenReturn(meta);
        when(resourceManager.findVersion(NAMESPACE_ID, NAME,
            Constants.AgentSpecs.RESOURCE_TYPE_AGENTSPEC, VERSION)).thenReturn(version);
        when(storageReader.readMeta(NAMESPACE_ID, NAME, VERSION, "storage"))
            .thenReturn(agentSpec);
        when(projector.project(meta, version, agentSpec)).thenReturn(expected);
        
        assertNull(handler.project(NAMESPACE_ID, "skill", NAME, null));
        assertNull(handler.project(NAMESPACE_ID,
            Constants.AgentSpecs.RESOURCE_TYPE_AGENTSPEC, NAME, "2.0.0"));
        assertSame(expected, handler.project(NAMESPACE_ID,
            Constants.AgentSpecs.RESOURCE_TYPE_AGENTSPEC, NAME, VERSION));
    }
    
    @Test
    void shouldSkipMissingDisabledBlankLatestAndOfflineAgentSpec() throws Exception {
        assertNull(handler.project(NAMESPACE_ID,
            Constants.AgentSpecs.RESOURCE_TYPE_AGENTSPEC, NAME, null));
        when(resourceManager.findMeta(NAMESPACE_ID, NAME,
            Constants.AgentSpecs.RESOURCE_TYPE_AGENTSPEC))
            .thenReturn(meta("disable", NAME, VERSION));
        assertNull(handler.project(NAMESPACE_ID,
            Constants.AgentSpecs.RESOURCE_TYPE_AGENTSPEC, NAME, null));
        
        AiResource noLatest = meta("enable", NAME, null);
        when(resourceManager.findMeta(NAMESPACE_ID, NAME,
            Constants.AgentSpecs.RESOURCE_TYPE_AGENTSPEC)).thenReturn(noLatest);
        assertNull(handler.project(NAMESPACE_ID,
            Constants.AgentSpecs.RESOURCE_TYPE_AGENTSPEC, NAME, null));
        
        AiResource enabled = meta("enable", NAME, VERSION);
        when(resourceManager.findMeta(NAMESPACE_ID, NAME,
            Constants.AgentSpecs.RESOURCE_TYPE_AGENTSPEC)).thenReturn(enabled);
        when(resourceManager.findVersion(NAMESPACE_ID, NAME,
            Constants.AgentSpecs.RESOURCE_TYPE_AGENTSPEC, VERSION))
            .thenReturn(version("offline"));
        assertNull(handler.project(NAMESPACE_ID,
            Constants.AgentSpecs.RESOURCE_TYPE_AGENTSPEC, NAME, null));
        assertNull(handler.project(NAMESPACE_ID,
            Constants.AgentSpecs.RESOURCE_TYPE_AGENTSPEC, " ", null));
    }
    
    @Test
    void shouldScanBoundedPageAndIsolateFailures() throws Exception {
        AiResource good = meta("enable", "good", VERSION);
        AiResource failed = meta("enable", "failed", VERSION);
        Page<AiResource> page = new Page<>();
        page.setPageItems(Arrays.asList(null, good, failed));
        when(resourceManager.listMetaByType(NAMESPACE_ID,
            Constants.AgentSpecs.RESOURCE_TYPE_AGENTSPEC, null, null, 1, 3)).thenReturn(page);
        AiResourceVersion version = version("online");
        when(resourceManager.findVersion(NAMESPACE_ID, "good",
            Constants.AgentSpecs.RESOURCE_TYPE_AGENTSPEC, VERSION)).thenReturn(version);
        when(resourceManager.findVersion(NAMESPACE_ID, "failed",
            Constants.AgentSpecs.RESOURCE_TYPE_AGENTSPEC, VERSION)).thenReturn(version);
        AgentSpec agentSpec = new AgentSpec();
        when(storageReader.readMeta(NAMESPACE_ID, "good", VERSION, "storage"))
            .thenReturn(agentSpec);
        when(storageReader.readMeta(NAMESPACE_ID, "failed", VERSION, "storage"))
            .thenThrow(new NacosException(NacosException.SERVER_ERROR, "broken"));
        when(projector.project(good, version, agentSpec)).thenReturn(projection("good"));
        
        AiResourceIndexSourcePage result = handler.scan(NAMESPACE_ID,
            Constants.AgentSpecs.RESOURCE_TYPE_AGENTSPEC, 1, 3);
        
        assertTrue(result.hasMore());
        assertEquals(3, result.getItems().size());
        assertNull(result.getItems().get(0).getProjection());
        assertEquals("good", result.getItems().get(1).getResourceName());
        assertTrue(result.getItems().get(2).getFailure() instanceof NacosException);
        assertFalse(handler.scan(NAMESPACE_ID, "skill", 1, 3).hasMore());
        when(resourceManager.listMetaByType(NAMESPACE_ID,
            Constants.AgentSpecs.RESOURCE_TYPE_AGENTSPEC, null, null, 2, 3)).thenReturn(null);
        assertTrue(handler.scan(NAMESPACE_ID,
            Constants.AgentSpecs.RESOURCE_TYPE_AGENTSPEC, 2, 3).getItems().isEmpty());
    }
    
    @Test
    void shouldValidateCurrentReadableProjection() throws Exception {
        AiResource meta = meta("enable", NAME, VERSION);
        AiResourceVersion version = version("online");
        AgentSpec agentSpec = new AgentSpec();
        AiResourceSearchDocument document = projection("digest").getDocument();
        when(resourceManager.findMeta(NAMESPACE_ID, NAME,
            Constants.AgentSpecs.RESOURCE_TYPE_AGENTSPEC)).thenReturn(meta);
        when(resourceManager.findVersion(NAMESPACE_ID, NAME,
            Constants.AgentSpecs.RESOURCE_TYPE_AGENTSPEC, VERSION)).thenReturn(version);
        when(storageReader.readMeta(NAMESPACE_ID, NAME, VERSION, "storage"))
            .thenReturn(agentSpec);
        when(projector.project(meta, version, agentSpec)).thenReturn(projection("digest"));
        
        assertTrue(handler.isCurrent(document));
        document.setSourceDigest("stale");
        assertFalse(handler.isCurrent(document));
        verify(resourceManager, times(2)).ensureReadableOrNotFound(meta,
            "AgentSpec not found: " + NAME);
    }
    
    @Test
    void shouldRejectInvalidUnreadableOrMissingCurrentDocument() throws Exception {
        assertFalse(handler.isCurrent(null));
        AiResourceSearchDocument wrongType = new AiResourceSearchDocument();
        wrongType.setResourceType("skill");
        assertFalse(handler.isCurrent(wrongType));
        AiResourceSearchDocument document = projection("digest").getDocument();
        when(resourceManager.findMeta(NAMESPACE_ID, NAME,
            Constants.AgentSpecs.RESOURCE_TYPE_AGENTSPEC))
            .thenReturn(meta("disable", NAME, VERSION));
        assertFalse(handler.isCurrent(document));
        
        AiResource enabled = meta("enable", NAME, VERSION);
        when(resourceManager.findMeta(NAMESPACE_ID, NAME,
            Constants.AgentSpecs.RESOURCE_TYPE_AGENTSPEC)).thenReturn(enabled);
        doThrow(new NacosException(NacosException.NOT_FOUND, "hidden"))
            .when(resourceManager).ensureReadableOrNotFound(enabled,
                "AgentSpec not found: " + NAME);
        assertFalse(handler.isCurrent(document));
        verify(storageReader, never()).readMeta(NAMESPACE_ID, NAME, VERSION, "storage");
    }
    
    @Test
    void shouldCheckCanonicalExistenceByType() {
        when(resourceManager.findMeta(NAMESPACE_ID, NAME,
            Constants.AgentSpecs.RESOURCE_TYPE_AGENTSPEC))
            .thenReturn(meta("enable", NAME, VERSION));
        
        assertTrue(handler.exists(NAMESPACE_ID,
            Constants.AgentSpecs.RESOURCE_TYPE_AGENTSPEC, NAME));
        assertFalse(handler.exists(NAMESPACE_ID, "skill", NAME));
    }
    
    private AiResource meta(String status, String name, String latestVersion) {
        AiResource result = new AiResource();
        result.setNamespaceId(NAMESPACE_ID);
        result.setName(name);
        result.setType(Constants.AgentSpecs.RESOURCE_TYPE_AGENTSPEC);
        result.setStatus(status);
        if (latestVersion != null) {
            result.setVersionInfo("{\"labels\":{\"latest\":\"" + latestVersion + "\"}}");
        }
        return result;
    }
    
    private AiResourceVersion version(String status) {
        AiResourceVersion result = new AiResourceVersion();
        result.setNamespaceId(NAMESPACE_ID);
        result.setName(NAME);
        result.setType(Constants.AgentSpecs.RESOURCE_TYPE_AGENTSPEC);
        result.setVersion(VERSION);
        result.setStatus(status);
        result.setStorage("storage");
        return result;
    }
    
    private AiResourceIndexProjection projection(String digest) {
        AiResourceSearchDocument document = new AiResourceSearchDocument();
        document.setNamespaceId(NAMESPACE_ID);
        document.setResourceType(Constants.AgentSpecs.RESOURCE_TYPE_AGENTSPEC);
        document.setResourceName(NAME);
        document.setResourceVersion(VERSION);
        document.setSourceDigest(digest);
        return new AiResourceIndexProjection(document, null, null, null);
    }
}
