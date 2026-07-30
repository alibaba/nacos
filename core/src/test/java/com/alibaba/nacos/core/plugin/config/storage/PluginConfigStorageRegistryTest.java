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

package com.alibaba.nacos.core.plugin.config.storage;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.ServiceConfigurationError;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class PluginConfigStorageRegistryTest {
    
    @Test
    void selectsBuiltInProviderByDefault() {
        TestProvider builtIn = new TestProvider("local-file", Integer.MAX_VALUE, true);
        PluginConfigStorageRegistry registry = registry(builtIn, Collections.emptyList(),
            (property, defaultValue) -> defaultValue);
        
        assertSame(builtIn, registry.getSelectedProvider());
    }
    
    @Test
    void selectsFirstEnabledProviderByOrder() {
        TestProvider builtIn = new TestProvider("local-file", Integer.MAX_VALUE, true);
        TestProvider first = new TestProvider("database", 10, false);
        TestProvider later = new TestProvider("remote", 20, false);
        PluginConfigStorageRegistry registry = registry(builtIn, Arrays.asList(later, first),
            (property, defaultValue) -> true);
        
        assertSame(first, registry.getSelectedProvider());
    }
    
    @Test
    void duplicateNameUsesFirstProviderAfterDeterministicSort() {
        TestProvider builtIn = new TestProvider("local-file", Integer.MAX_VALUE, true);
        TestProvider first = new TestProvider("database", 1, true);
        TestProvider duplicate = new TestProvider("database", 2, true);
        PluginConfigStorageRegistry registry = registry(builtIn,
            Arrays.asList(duplicate, first), (property, defaultValue) -> defaultValue);
        
        assertSame(first, registry.getSelectedProvider());
    }
    
    @Test
    void invalidProviderMetadataLeavesSourceUnavailable() {
        TestProvider builtIn = new TestProvider("local-file", Integer.MAX_VALUE, true);
        PluginConfigStorageProvider blank = new TestProvider(" ", 0, true);
        PluginConfigStorageProvider broken = new TestProvider("broken", 0, true) {
            
            @Override
            public int getOrder() {
                throw new IllegalStateException("metadata failed");
            }
        };
        PluginConfigStorageRegistry nullProvider = registry(builtIn,
            Collections.singletonList(null), (property, defaultValue) -> defaultValue);
        PluginConfigStorageRegistry blankProvider = registry(builtIn,
            Collections.singletonList(blank), (property, defaultValue) -> defaultValue);
        PluginConfigStorageRegistry brokenProvider = registry(builtIn,
            Collections.singletonList(broken), (property, defaultValue) -> defaultValue);
        PluginConfigStorageRegistry invalidBuiltIn = registry(null, Collections.emptyList(),
            (property, defaultValue) -> defaultValue);
        
        assertNull(nullProvider.getSelectedProvider());
        assertNull(blankProvider.getSelectedProvider());
        assertNull(brokenProvider.getSelectedProvider());
        assertNull(invalidBuiltIn.getSelectedProvider());
    }
    
    @Test
    void discoveryFailureLeavesSourceUnavailable() {
        TestProvider builtIn = new TestProvider("local-file", Integer.MAX_VALUE, true);
        PluginConfigStorageRegistry runtimeFailure = new PluginConfigStorageRegistry(builtIn,
            () -> {
                throw new IllegalStateException("discovery failed");
            }, (property, defaultValue) -> defaultValue);
        PluginConfigStorageRegistry serviceFailure = new PluginConfigStorageRegistry(builtIn,
            () -> {
                throw new ServiceConfigurationError("broken service");
            }, (property, defaultValue) -> defaultValue);
        PluginConfigStorageRegistry linkageFailure = new PluginConfigStorageRegistry(builtIn,
            () -> {
                throw new LinkageError("broken linkage");
            }, (property, defaultValue) -> defaultValue);
        
        assertNull(runtimeFailure.getSelectedProvider());
        assertNull(serviceFailure.getSelectedProvider());
        assertNull(linkageFailure.getSelectedProvider());
    }
    
    @Test
    void nullDiscoveryResultFallsBackToBuiltIn() {
        TestProvider builtIn = new TestProvider("local-file", Integer.MAX_VALUE, true);
        PluginConfigStorageRegistry registry = new PluginConfigStorageRegistry(builtIn,
            () -> null, (property, defaultValue) -> defaultValue);
        
        assertSame(builtIn, registry.getSelectedProvider());
    }
    
    @Test
    void propertyFailureLeavesSourceUnavailable() {
        TestProvider builtIn = new TestProvider("local-file", Integer.MAX_VALUE, true);
        PluginConfigStorageRegistry runtimeFailure = registry(builtIn, Collections.emptyList(),
            (property, defaultValue) -> {
                throw new IllegalStateException("environment unavailable");
            });
        PluginConfigStorageRegistry linkageFailure = registry(builtIn, Collections.emptyList(),
            (property, defaultValue) -> {
                throw new LinkageError("environment linkage unavailable");
            });
        
        assertNull(runtimeFailure.getSelectedProvider());
        assertNull(linkageFailure.getSelectedProvider());
    }
    
    @Test
    void allProvidersDisabledLeavesSourceUnavailable() {
        TestProvider builtIn = new TestProvider("local-file", Integer.MAX_VALUE, true);
        PluginConfigStorageRegistry registry = registry(builtIn, Collections.emptyList(),
            (property, defaultValue) -> false);
        
        assertNull(registry.getSelectedProvider());
    }
    
    private PluginConfigStorageRegistry registry(PluginConfigStorageProvider builtIn,
        Collection<PluginConfigStorageProvider> providers,
        java.util.function.BiFunction<String, Boolean, Boolean> enabledResolver) {
        return new PluginConfigStorageRegistry(builtIn, () -> providers, enabledResolver);
    }
    
    private static class TestProvider implements PluginConfigStorageProvider {
        
        private final String name;
        
        private final int order;
        
        private final boolean enabledByDefault;
        
        TestProvider(String name, int order, boolean enabledByDefault) {
            this.name = name;
            this.order = order;
            this.enabledByDefault = enabledByDefault;
        }
        
        @Override
        public String getName() {
            return name;
        }
        
        @Override
        public int getOrder() {
            return order;
        }
        
        @Override
        public boolean isEnabledByDefault() {
            return enabledByDefault;
        }
        
        @Override
        public PluginConfigStorage createStorage() {
            return null;
        }
    }
}
