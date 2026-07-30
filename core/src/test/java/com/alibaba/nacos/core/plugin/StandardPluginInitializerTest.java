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

package com.alibaba.nacos.core.plugin;

import com.alibaba.nacos.api.plugin.PluginInitializationPhase;
import com.alibaba.nacos.core.plugin.sync.PluginStateSynchronizer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StandardPluginInitializerTest {
    
    @Test
    void testInitializeAndReadyFallbackUseSameFlow() {
        PluginManager pluginManager = mock(PluginManager.class);
        StandardPluginInitializer initializer = new StandardPluginInitializer(pluginManager);
        
        initializer.initialize();
        initializer.onApplicationEvent(mock(ApplicationReadyEvent.class));
        
        assertEquals(PluginInitializationPhase.STANDARD,
            initializer.getInitializationPhase());
        verify(pluginManager, times(2)).initialize();
    }
    
    @Test
    void testSynchronizerStartsAfterLocalPluginInitialization() {
        PluginManager pluginManager = mock(PluginManager.class);
        PluginStateSynchronizer synchronizer = mock(PluginStateSynchronizer.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<PluginStateSynchronizer> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(synchronizer);
        StandardPluginInitializer initializer =
            new StandardPluginInitializer(pluginManager, provider);
        
        initializer.initialize();
        
        org.mockito.InOrder order = inOrder(pluginManager, synchronizer);
        order.verify(pluginManager).initialize();
        order.verify(synchronizer).initialize();
    }
    
    @Test
    void testMissingSynchronizerKeepsStandaloneFlow() {
        PluginManager pluginManager = mock(PluginManager.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<PluginStateSynchronizer> provider = mock(ObjectProvider.class);
        StandardPluginInitializer initializer =
            new StandardPluginInitializer(pluginManager, provider);
        
        initializer.initialize();
        
        verify(pluginManager).initialize();
    }
}
