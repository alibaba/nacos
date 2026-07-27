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

package com.alibaba.nacos.ai.pipeline;

import com.alibaba.nacos.ai.config.AiPipelineModuleConfig;
import com.alibaba.nacos.api.plugin.PluginStateCheckerHolder;
import com.alibaba.nacos.api.plugin.PluginType;
import com.alibaba.nacos.plugin.ai.pipeline.model.PublishPipelineContext;
import com.alibaba.nacos.plugin.ai.pipeline.model.PublishPipelineResourceType;
import com.alibaba.nacos.plugin.ai.pipeline.model.PublishPipelineResult;
import com.alibaba.nacos.plugin.ai.pipeline.spi.PublishPipelineService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link PublishPipelineManager}.
 *
 * @author Nacos
 */
class PublishPipelineManagerTest {
    
    @AfterEach
    void tearDown() {
        PluginStateCheckerHolder.setInstance(null);
    }
    
    @Test
    void initShouldLoadAvailableSpiServices() {
        PublishPipelineManager manager = new PublishPipelineManager(moduleConfig(true));
        
        manager.init();
        manager.init();
        
        assertNotNull(manager.getAllServices());
    }
    
    @Test
    void constructorShouldRejectNullModuleConfig() {
        assertThrows(NullPointerException.class, () -> new PublishPipelineManager(null));
    }
    
    @Test
    void initShouldIgnoreInvalidDuplicateAndFailingServices() {
        PublishPipelineManager manager = new PublishPipelineManager(moduleConfig(true));
        PublishPipelineService first = service("same", 1,
            PublishPipelineResourceType.values());
        PublishPipelineService duplicate = service("same", 2,
            PublishPipelineResourceType.values());
        PublishPipelineService noId = service(null, 3, PublishPipelineResourceType.values());
        PublishPipelineService failing = new TestPublishPipelineService() {
            
            @Override
            public String pipelineId() {
                throw new IllegalStateException("broken id");
            }
            
            @Override
            public PublishPipelineResult execute(PublishPipelineContext context) {
                return null;
            }
            
            @Override
            public int getPreferOrder() {
                return 4;
            }
            
            @Override
            public PublishPipelineResourceType[] pipelineResourceTypes() {
                return PublishPipelineResourceType.values();
            }
        };
        
        manager.initWithServices(Arrays.asList(first, duplicate, null, noId, failing));
        
        Collection<PublishPipelineService> services = manager.getAllServices();
        assertEquals(1, services.size());
        assertSame(first, services.iterator().next());
    }
    
    @Test
    void getPipelineServicesShouldFilterAndSortAppliedServices() {
        PublishPipelineManager manager = new PublishPipelineManager(moduleConfig(true));
        PublishPipelineService last = service("last", 50,
            PublishPipelineResourceType.SKILL);
        PublishPipelineService middle = service("middle", 10,
            PublishPipelineResourceType.SKILL);
        PublishPipelineService first = service("first", 1,
            PublishPipelineResourceType.SKILL, PublishPipelineResourceType.AGENTSPEC);
        PublishPipelineService unsupported = service("unsupported", 0,
            PublishPipelineResourceType.PROMPT);
        PublishPipelineService withoutTypes = service("without-types", 0,
            (PublishPipelineResourceType[]) null);
        manager.initWithServices(Arrays.asList(last, middle, first,
            unsupported, withoutTypes));
        PluginStateCheckerHolder.setInstance((pluginType, pluginName) -> true);
        
        List<PublishPipelineService> result = manager.getPipelineServices(
            PublishPipelineResourceType.SKILL);
        
        assertEquals(Arrays.asList("first", "middle", "last"),
            Arrays.asList(result.get(0).pipelineId(), result.get(1).pipelineId(),
                result.get(2).pipelineId()));
    }
    
    @Test
    void unifiedStateShouldSelectServices() {
        PublishPipelineManager manager = new PublishPipelineManager(moduleConfig(true));
        manager.initWithServices(Arrays.asList(
            service("disabled", 1, PublishPipelineResourceType.SKILL),
            service("enabled", 2, PublishPipelineResourceType.SKILL)));
        PluginStateCheckerHolder.setInstance(
            (pluginType, pluginName) -> PluginType.AI_PIPELINE.getType().equals(pluginType)
                && "enabled".equals(pluginName));
        
        List<PublishPipelineService> result = manager.getPipelineServices(
            PublishPipelineResourceType.SKILL);
        
        assertEquals(1, result.size());
        assertEquals("enabled", result.get(0).pipelineId());
    }
    
    @Test
    void moduleSwitchShouldBeAppliedByManager() {
        AiPipelineModuleConfig moduleConfig = moduleConfig(false);
        PublishPipelineManager manager = new PublishPipelineManager(moduleConfig);
        manager.initWithServices(Collections.singletonList(
            service("switchable", 1, PublishPipelineResourceType.SKILL)));
        PluginStateCheckerHolder.setInstance((pluginType, pluginName) -> true);
        
        assertTrue(manager.getPipelineServices(PublishPipelineResourceType.SKILL).isEmpty());
        
        when(moduleConfig.isEnabled()).thenReturn(true);
        
        assertEquals(1,
            manager.getPipelineServices(PublishPipelineResourceType.SKILL).size());
    }
    
    @Test
    void missingUnifiedStateShouldDisablePipeline() {
        PublishPipelineManager manager = new PublishPipelineManager(moduleConfig(true));
        manager.initWithServices(Collections.singletonList(
            service("pipeline", 1, PublishPipelineResourceType.SKILL)));
        
        assertTrue(manager.getPipelineServices(PublishPipelineResourceType.SKILL).isEmpty());
    }
    
    private static AiPipelineModuleConfig moduleConfig(boolean enabled) {
        AiPipelineModuleConfig result = mock(AiPipelineModuleConfig.class);
        when(result.isEnabled()).thenReturn(enabled);
        return result;
    }
    
    private static PublishPipelineService service(String pipelineId, int order,
        PublishPipelineResourceType... resourceTypes) {
        return new TestPublishPipelineService() {
            
            @Override
            public String pipelineId() {
                return pipelineId;
            }
            
            @Override
            public PublishPipelineResult execute(PublishPipelineContext context) {
                return PublishPipelineResult.pass("passed");
            }
            
            @Override
            public int getPreferOrder() {
                return order;
            }
            
            @Override
            public PublishPipelineResourceType[] pipelineResourceTypes() {
                return resourceTypes;
            }
        };
    }
}
