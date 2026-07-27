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
import com.alibaba.nacos.plugin.ai.pipeline.spi.PublishPipelineService;

import java.util.Collection;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/**
 * Test support for the unified AI pipeline module and plugin state gates.
 *
 * @author Nacos
 */
public final class TestAiPipelineSupport {
    
    private TestAiPipelineSupport() {
    }
    
    /**
     * Create a manager with fixed module and implementation states.
     *
     * @param moduleEnabled whether the module entry is enabled
     * @param enabledPluginIds enabled implementation ids
     * @param services services to load
     * @return initialized manager
     */
    public static PublishPipelineManager newManager(boolean moduleEnabled,
        Collection<String> enabledPluginIds, Iterable<PublishPipelineService> services) {
        AiPipelineModuleConfig moduleConfig = mock(AiPipelineModuleConfig.class);
        lenient().when(moduleConfig.isEnabled()).thenReturn(moduleEnabled);
        PluginStateCheckerHolder.setInstance(
            (pluginType, pluginName) -> !PluginType.AI_PIPELINE.getType().equals(pluginType)
                || enabledPluginIds.contains(pluginName));
        PublishPipelineManager manager = new PublishPipelineManager(moduleConfig);
        manager.initWithServices(services);
        return manager;
    }
    
    /**
     * Clear the process-wide plugin state checker after a test.
     */
    public static void clearStateChecker() {
        PluginStateCheckerHolder.setInstance(null);
    }
}
