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

package com.alibaba.nacos.core.plugin.config;

import com.alibaba.nacos.core.plugin.model.PluginConfigSourceType;
import com.alibaba.nacos.core.plugin.model.PluginInfo;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginConfigSourceRegistryTest {
    
    @Test
    void testDefaultRegistryUsesFixedSourceOrder() {
        PluginConfigSourceRegistry registry = new PluginConfigSourceRegistry();
        
        List<PluginConfigSourceResolver> resolvers = registry.getSourceResolvers();
        
        assertEquals(Arrays.asList(PluginConfigSourceType.LOCAL_ONLY,
            PluginConfigSourceType.RUNTIME_PERSISTED, PluginConfigSourceType.STATIC,
            PluginConfigSourceType.DEFAULT),
            resolvers.stream()
                .map(PluginConfigSourceResolver::getSourceType).toList());
        assertThrows(UnsupportedOperationException.class,
            () -> resolvers.add(new TestSourceResolver(PluginConfigSourceType.DEFAULT)));
    }
    
    @Test
    void testRegistryDelegatesSourceLifecycle() {
        TestSourceResolver staticResolver = new TestSourceResolver(PluginConfigSourceType.STATIC);
        TestPersistedSourceResolver persistedResolver = new TestPersistedSourceResolver();
        PluginConfigSourceRegistry registry = registryWithResolvers(staticResolver,
            persistedResolver);
        PluginInfo pluginInfo = new PluginInfo();
        Map<String, Map<String, String>> restored = Collections.singletonMap("trace:test",
            Collections.singletonMap("key", "value"));
        
        registry.initializeConfig(PluginConfigSourceType.STATIC, pluginInfo);
        registry.refreshConfig(PluginConfigSourceType.STATIC, pluginInfo);
        registry.initializePersistedConfigs();
        registry.restorePersistedConfigs(restored);
        
        assertSame(staticResolver,
            registry.getSourceResolver(PluginConfigSourceType.STATIC));
        assertTrue(staticResolver.initialized);
        assertTrue(staticResolver.refreshed);
        assertTrue(persistedResolver.persistedInitialized);
        assertEquals(restored, registry.getAllPersistedConfigs());
    }
    
    @Test
    void testRegistryRejectsDuplicateAndMissingSources() {
        List<PluginConfigSourceResolver> duplicateSources = defaultSources();
        duplicateSources.add(new TestSourceResolver(PluginConfigSourceType.DEFAULT));
        
        IllegalArgumentException duplicateException = assertThrows(IllegalArgumentException.class,
            () -> new PluginConfigSourceRegistry(duplicateSources));
        IllegalArgumentException missingException = assertThrows(IllegalArgumentException.class,
            () -> new PluginConfigSourceRegistry(Collections.emptyList()));
        IllegalArgumentException unknownException = assertThrows(IllegalArgumentException.class,
            () -> new PluginConfigSourceRegistry().getSourceResolver(null));
        List<PluginConfigSourceResolver> unsupportedPersistedSource = defaultSources();
        unsupportedPersistedSource.set(1,
            new TestSourceResolver(PluginConfigSourceType.RUNTIME_PERSISTED));
        IllegalArgumentException unsupportedException = assertThrows(
            IllegalArgumentException.class,
            () -> new PluginConfigSourceRegistry(unsupportedPersistedSource));
        
        assertTrue(duplicateException.getMessage().contains("Duplicate"));
        assertTrue(missingException.getMessage().contains("Required"));
        assertTrue(unknownException.getMessage().contains("not found"));
        assertTrue(unsupportedException.getMessage().contains("persistence lifecycle"));
    }
    
    private PluginConfigSourceRegistry registryWithResolvers(
        PluginConfigSourceResolver staticResolver,
        PersistedPluginConfigSourceResolver persistedResolver) {
        List<PluginConfigSourceResolver> sources = defaultSources();
        sources.set(1, persistedResolver);
        sources.set(2, staticResolver);
        return new PluginConfigSourceRegistry(sources);
    }
    
    private List<PluginConfigSourceResolver> defaultSources() {
        return new java.util.ArrayList<>(Arrays.asList(
            new TestSourceResolver(PluginConfigSourceType.LOCAL_ONLY),
            new TestPersistedSourceResolver(),
            new TestSourceResolver(PluginConfigSourceType.STATIC),
            new TestSourceResolver(PluginConfigSourceType.DEFAULT)));
    }
    
    private static class TestPersistedSourceResolver extends TestSourceResolver
        implements PersistedPluginConfigSourceResolver {
        
        private boolean persistedInitialized;
        
        private Map<String, Map<String, String>> configs = Collections.emptyMap();
        
        TestPersistedSourceResolver() {
            super(PluginConfigSourceType.RUNTIME_PERSISTED);
        }
        
        @Override
        public void initialize() {
            persistedInitialized = true;
        }
        
        @Override
        public boolean isAvailable() {
            return true;
        }
        
        @Override
        public Map<String, Map<String, String>> getAllConfigs() {
            return configs;
        }
        
        @Override
        public void restoreConfigs(Map<String, Map<String, String>> configs) {
            this.configs = configs;
        }
        
        @Override
        public void shutdown() {
        }
    }
    
    private static class TestSourceResolver implements PluginConfigSourceResolver {
        
        private final PluginConfigSourceType sourceType;
        
        private boolean initialized;
        
        private boolean refreshed;
        
        TestSourceResolver(PluginConfigSourceType sourceType) {
            this.sourceType = sourceType;
        }
        
        @Override
        public void initializeConfig(PluginInfo pluginInfo) {
            initialized = true;
        }
        
        @Override
        public void refreshConfig(PluginInfo pluginInfo) {
            refreshed = true;
        }
        
        @Override
        public Map<String, String> getConfig(PluginInfo pluginInfo) {
            return Collections.emptyMap();
        }
        
        @Override
        public PluginConfigSourceType getSourceType() {
            return sourceType;
        }
    }
}
