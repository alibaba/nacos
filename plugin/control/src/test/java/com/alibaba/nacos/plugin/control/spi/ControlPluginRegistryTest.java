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

package com.alibaba.nacos.plugin.control.spi;

import com.alibaba.nacos.api.plugin.PluginType;
import com.alibaba.nacos.plugin.control.ControlManagerBuilderTest;
import com.alibaba.nacos.plugin.control.ControlPluginAdapter;
import com.alibaba.nacos.plugin.control.configs.ControlConfigs;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ControlPluginRegistryTest {
    
    @AfterEach
    void tearDown() {
        ControlConfigs.getInstance().setControlManagerType(null);
    }
    
    @Test
    void testRegisterBuildersAndExposeImmutableAdapters() {
        NamedBuilder first = new NamedBuilder("first");
        NamedBuilder second = new NamedBuilder("second");
        
        ControlPluginRegistry registry =
            new ControlPluginRegistry(Arrays.asList(first, second));
        Map<String, ControlPluginAdapter> plugins = registry.getPlugins();
        
        assertEquals(2, plugins.size());
        assertEquals("first", plugins.get("first").getPluginName());
        assertEquals("second", plugins.get("second").getPluginName());
        assertThrows(UnsupportedOperationException.class,
            () -> plugins.put("third", plugins.get("first")));
    }
    
    @Test
    void testNullBuilderCollectionCreatesEmptyRegistry() {
        assertTrue(new ControlPluginRegistry(null).getPlugins().isEmpty());
    }
    
    @Test
    void testRejectInvalidBuilders() {
        assertThrows(IllegalStateException.class,
            () -> new ControlPluginRegistry(Collections.singletonList(null)));
        assertThrows(IllegalStateException.class,
            () -> new ControlPluginRegistry(
                Collections.singletonList(new NamedBuilder(" "))));
        assertThrows(IllegalStateException.class,
            () -> new ControlPluginRegistry(
                Arrays.asList(new NamedBuilder("same"), new NamedBuilder("SAME"))));
    }
    
    @Test
    void testSingletonLoadsSpiBuilders() {
        ControlPluginRegistry registry = ControlPluginRegistry.getInstance();
        
        assertNotNull(registry);
        assertTrue(registry.getPlugins().containsKey("test"));
        assertTrue(registry.getPlugins().containsKey("throw"));
        assertSame(registry, ControlPluginRegistry.getInstance());
    }
    
    @Test
    void testProviderLoadsRegistryLazily() {
        ControlPluginRegistry registry =
            new ControlPluginRegistry(Collections.singletonList(new NamedBuilder("selected")));
        AtomicInteger calls = new AtomicInteger();
        ControlPluginProvider provider = new ControlPluginProvider(() -> {
            calls.incrementAndGet();
            return registry;
        });
        assertEquals(0, calls.get());
        assertEquals(PluginType.CONTROL, provider.getPluginType());
        
        ControlConfigs.getInstance().setControlManagerType("SELECTED");
        assertSame(registry.getPlugins(), provider.getAllPlugins());
        assertEquals(1, calls.get());
        
        ControlConfigs.getInstance().setControlManagerType("missing");
        assertSame(registry.getPlugins(), provider.getAllPlugins());
        assertEquals(2, calls.get());
        
        ControlConfigs.getInstance().setControlManagerType("");
        assertFalse(provider.getAllPlugins().isEmpty());
        assertEquals(3, calls.get());
    }
    
    private static final class NamedBuilder extends ControlManagerBuilderTest {
        
        private final String name;
        
        private NamedBuilder(String name) {
            this.name = name;
        }
        
        @Override
        public String getName() {
            return name;
        }
    }
}
