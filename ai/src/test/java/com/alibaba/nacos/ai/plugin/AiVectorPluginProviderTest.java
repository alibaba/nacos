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

import com.alibaba.nacos.api.plugin.PluginType;
import com.alibaba.nacos.plugin.ai.vector.AiResourceVectorIndexRegistry;
import com.alibaba.nacos.plugin.ai.vector.spi.AiResourceVectorIndex;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link AiVectorPluginProvider}.
 *
 * @author nacos
 */
class AiVectorPluginProviderTest {
    
    @Test
    void shouldExposeAllLoadedVectorIndexes() {
        AiResourceVectorIndexRegistry registry = mock(AiResourceVectorIndexRegistry.class);
        AiResourceVectorIndex index = mock(AiResourceVectorIndex.class);
        when(registry.getAllIndexes()).thenReturn(Map.of("postgresql", index));
        
        AiVectorPluginProvider provider = new AiVectorPluginProvider(registry);
        Map<String, AiResourceVectorIndex> plugins = provider.getAllPlugins();
        
        assertEquals(PluginType.AI_VECTOR, provider.getPluginType());
        assertSame(index, plugins.get("postgresql"));
    }
    
    @Test
    void shouldReturnEmptyWhenNoVectorIndexIsInstalled() {
        AiResourceVectorIndexRegistry registry = mock(AiResourceVectorIndexRegistry.class);
        when(registry.getAllIndexes()).thenReturn(Collections.emptyMap());
        
        assertEquals(Collections.emptyMap(),
            new AiVectorPluginProvider(registry).getAllPlugins());
    }
}
