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

package com.alibaba.nacos.ai.plugin;

import com.alibaba.nacos.ai.pipeline.PublishPipelineManager;
import com.alibaba.nacos.api.plugin.PluginType;
import com.alibaba.nacos.plugin.ai.pipeline.spi.PublishPipelineService;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.context.ApplicationContext;

import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link AiPipelinePluginProvider}.
 *
 * @author Nacos
 */
class AiPipelinePluginProviderTest {
    
    @Test
    void shouldExposeAiPipelinePluginType() {
        assertEquals(PluginType.AI_PIPELINE, new AiPipelinePluginProvider().getPluginType());
    }
    
    @Test
    void shouldReturnManagerServiceInstances() {
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        PublishPipelineManager manager = mock(PublishPipelineManager.class);
        PublishPipelineService service = mock(PublishPipelineService.class);
        PublishPipelineService noIdService = mock(PublishPipelineService.class);
        when(service.pipelineId()).thenReturn("scanner");
        when(manager.getAllServices()).thenReturn(Arrays.asList(service, null, noIdService));
        try (MockedStatic<ApplicationUtils> applicationUtils =
            Mockito.mockStatic(ApplicationUtils.class)) {
            applicationUtils.when(ApplicationUtils::getApplicationContext)
                .thenReturn(applicationContext);
            applicationUtils.when(() -> ApplicationUtils.getBean(PublishPipelineManager.class))
                .thenReturn(manager);
            
            Map<String, PublishPipelineService> plugins =
                new AiPipelinePluginProvider().getAllPlugins();
            
            assertSame(service, plugins.get("scanner"));
        }
    }
    
    @Test
    void shouldReturnEmptyWhenApplicationContextIsUnavailable() {
        try (MockedStatic<ApplicationUtils> applicationUtils =
            Mockito.mockStatic(ApplicationUtils.class)) {
            assertTrue(new AiPipelinePluginProvider().getAllPlugins().isEmpty());
        }
    }
    
    @Test
    void shouldReturnEmptyWhenManagerLookupFails() {
        try (MockedStatic<ApplicationUtils> applicationUtils =
            Mockito.mockStatic(ApplicationUtils.class)) {
            applicationUtils.when(ApplicationUtils::getApplicationContext)
                .thenReturn(mock(ApplicationContext.class));
            applicationUtils.when(() -> ApplicationUtils.getBean(PublishPipelineManager.class))
                .thenThrow(new IllegalStateException("not ready"));
            
            assertTrue(new AiPipelinePluginProvider().getAllPlugins().isEmpty());
        }
    }
}
