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

package com.alibaba.nacos.ai.service.a2a.migration;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.model.search.AiResourceSearchDocument;
import com.alibaba.nacos.ai.service.search.AgentSearchIndexProjector;
import com.alibaba.nacos.ai.service.search.AiResourceSearchReadinessService;
import com.alibaba.nacos.ai.service.search.AiResourceSearchRepository;
import com.alibaba.nacos.ai.service.search.AiResourceSearchTypeHandlerRegistry;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.StandardEnvironment;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class A2aMigrationSearchReadinessGateTest {
    
    private static final ConfigurableEnvironment CACHED_ENVIRONMENT = EnvUtil.getEnvironment();
    
    private A2aMigrationSearchReadinessGate gate;
    
    private AiResourceSearchReadinessService readinessService;
    
    private AiResourceSearchRepository repository;
    
    private AiResourceSearchTypeHandlerRegistry registry;
    
    @BeforeEach
    void setUp() {
        EnvUtil.setEnvironment(new StandardEnvironment());
        System.setProperty(Constants.AI_RESOURCE_SEARCH_ENABLED_KEY, "true");
        gate = new A2aMigrationSearchReadinessGate();
        readinessService = mock(AiResourceSearchReadinessService.class);
        repository = mock(AiResourceSearchRepository.class);
        registry = mock(AiResourceSearchTypeHandlerRegistry.class);
    }
    
    @AfterEach
    void tearDown() {
        System.clearProperty(Constants.AI_RESOURCE_SEARCH_ENABLED_KEY);
        EnvUtil.setEnvironment(CACHED_ENVIRONMENT);
    }
    
    @Test
    void shouldSkipDisabledOrEmptySearchGate() {
        System.setProperty(Constants.AI_RESOURCE_SEARCH_ENABLED_KEY, "false");
        assertTrue(gate.isReady(null));
        System.setProperty(Constants.AI_RESOURCE_SEARCH_ENABLED_KEY, "true");
        assertTrue(gate.isReady(Collections.singletonMap("public", Collections.emptySet())));
        assertFalse(gate.isReady(null));
    }
    
    @Test
    void shouldRequireAllCollaboratorsAndGlobalReadiness() {
        Map<String, Set<String>> source = source();
        assertFalse(gate.isReady(source));
        gate.setReadinessService(readinessService);
        assertFalse(gate.isReady(source));
        gate.setRepository(repository);
        assertFalse(gate.isReady(source));
        gate.setTypeHandlerRegistry(registry);
        assertFalse(gate.isReady(source));
        when(readinessService.isReady(Constants.Agent.RESOURCE_TYPE_AGENT,
            AgentSearchIndexProjector.PROJECTION_VERSION)).thenReturn(true);
        assertFalse(gate.isReady(source));
    }
    
    @Test
    void shouldValidateEveryCurrentPersistedDocument() throws Exception {
        gate.setReadinessService(readinessService);
        gate.setRepository(repository);
        gate.setTypeHandlerRegistry(registry);
        when(readinessService.isReady(Constants.Agent.RESOURCE_TYPE_AGENT,
            AgentSearchIndexProjector.PROJECTION_VERSION)).thenReturn(true);
        AiResourceSearchDocument document = new AiResourceSearchDocument();
        document.setResourceType(Constants.Agent.RESOURCE_TYPE_AGENT);
        when(repository.findEntry("public", Constants.Agent.RESOURCE_TYPE_AGENT, "agent"))
            .thenReturn(document);
        when(registry.isCurrent(document)).thenReturn(false, true);
        assertFalse(gate.isReady(source()));
        assertTrue(gate.isReady(source()));
        when(repository.findEntry("public", Constants.Agent.RESOURCE_TYPE_AGENT, "agent"))
            .thenThrow(new IllegalStateException("repository unavailable"));
        assertFalse(gate.isReady(source()));
    }
    
    private Map<String, Set<String>> source() {
        return Collections.singletonMap("public", Collections.singleton("agent"));
    }
}
