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

import com.alibaba.nacos.api.plugin.ConfigItemDefinition;
import com.alibaba.nacos.api.plugin.PluginType;
import com.alibaba.nacos.core.plugin.config.storage.PluginConfigStorage;
import com.alibaba.nacos.core.plugin.config.storage.PluginConfigStorageProvider;
import com.alibaba.nacos.core.plugin.model.PluginConfigSourceType;
import com.alibaba.nacos.core.plugin.model.PluginInfo;
import com.alibaba.nacos.core.plugin.storage.PluginPersistenceException;
import com.alibaba.nacos.core.plugin.storage.PluginStatePersistenceService;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.mock.env.MockEnvironment;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuntimePersistedPluginConfigSourceResolverTest {
    
    private static final String PLUGIN_ID = "trace:test";
    
    @Mock
    private PluginStatePersistenceService persistence;
    
    private ConfigurableEnvironment previousEnvironment;
    
    @BeforeEach
    void setUp() {
        previousEnvironment = EnvUtil.getEnvironment();
        EnvUtil.setEnvironment(new MockEnvironment());
    }
    
    @AfterEach
    void tearDown() {
        EnvUtil.setEnvironment(previousEnvironment);
    }
    
    @Test
    void testInitializeAndNormalizeLoadedConfig() {
        Map<String, String> loadedConfig = Collections.singletonMap(
            "nacos.plugin.trace.test.endpoint", "loaded");
        when(persistence.loadAllConfigs()).thenReturn(
            Collections.singletonMap(PLUGIN_ID, loadedConfig));
        RuntimePersistedPluginConfigSourceResolver resolver =
            new RuntimePersistedPluginConfigSourceResolver(persistence);
        PluginInfo pluginInfo = pluginInfo();
        
        resolver.initialize();
        resolver.initializeConfig(pluginInfo);
        
        assertEquals(Collections.singletonMap("endpoint", "loaded"),
            resolver.getConfig(pluginInfo));
        assertEquals(PluginConfigSourceType.RUNTIME_PERSISTED, resolver.getSourceType());
        verify(persistence).loadAllConfigs();
    }
    
    @Test
    void testUpdatePersistsBeforeReplacingSource() {
        RuntimePersistedPluginConfigSourceResolver resolver =
            new RuntimePersistedPluginConfigSourceResolver(persistence);
        Map<String, String> config = new HashMap<>();
        config.put("endpoint", "new");
        
        resolver.updateConfig(PLUGIN_ID, config);
        config.put("endpoint", "mutated");
        
        assertEquals("new", resolver.getConfig(pluginInfo()).get("endpoint"));
        verify(persistence).saveConfig(PLUGIN_ID,
            Collections.singletonMap("endpoint", "new"));
        
        resolver.updateConfig(PLUGIN_ID, null);
        assertTrue(resolver.getConfig(pluginInfo()).isEmpty());
        verify(persistence).saveConfig(PLUGIN_ID, Collections.emptyMap());
    }
    
    @Test
    void testPersistenceFailureKeepsCurrentSource() {
        RuntimePersistedPluginConfigSourceResolver resolver =
            new RuntimePersistedPluginConfigSourceResolver(persistence);
        resolver.updateConfig(PLUGIN_ID, Collections.singletonMap("endpoint", "current"));
        doThrow(new PluginPersistenceException("save failed")).when(persistence)
            .saveConfig(PLUGIN_ID, Collections.singletonMap("endpoint", "failed"));
        
        assertThrows(PluginPersistenceException.class, () -> resolver.updateConfig(PLUGIN_ID,
            Collections.singletonMap("endpoint", "failed")));
        
        assertEquals("current", resolver.getConfig(pluginInfo()).get("endpoint"));
    }
    
    @Test
    void testSnapshotIsDefensiveCopy() {
        RuntimePersistedPluginConfigSourceResolver resolver =
            new RuntimePersistedPluginConfigSourceResolver();
        resolver.initialize();
        resolver.updateConfig(PLUGIN_ID, Collections.singletonMap("endpoint", "value"));
        
        Map<String, Map<String, String>> snapshot = resolver.getAllConfigs();
        snapshot.get(PLUGIN_ID).put("endpoint", "mutated");
        snapshot.put("other:test", Collections.emptyMap());
        
        assertEquals("value", resolver.getConfig(pluginInfo()).get("endpoint"));
        assertEquals(1, resolver.getAllConfigs().size());
        
        resolver.restoreConfigs(Collections.singletonMap(PLUGIN_ID,
            Collections.singletonMap("endpoint", "restored")));
        assertEquals("restored", resolver.getConfig(pluginInfo()).get("endpoint"));
    }
    
    @Test
    void testRestoreReplacesCompleteSource() {
        RuntimePersistedPluginConfigSourceResolver resolver =
            new RuntimePersistedPluginConfigSourceResolver(persistence);
        resolver.updateConfig(PLUGIN_ID, Collections.singletonMap("old", "value"));
        Map<String, Map<String, String>> restored = new HashMap<>();
        Map<String, String> restoredPluginConfig = new HashMap<>();
        restoredPluginConfig.put("endpoint", "restored");
        restored.put(PLUGIN_ID, restoredPluginConfig);
        restored.put("unknown:test", null);
        
        resolver.restoreConfigs(restored);
        restored.get(PLUGIN_ID).put("endpoint", "mutated");
        
        assertEquals(Collections.singletonMap("endpoint", "restored"),
            resolver.getConfig(pluginInfo()));
        PluginInfo unknownPlugin = new PluginInfo();
        unknownPlugin.setPluginId("unknown:test");
        assertTrue(resolver.getConfig(unknownPlugin).isEmpty());
        verify(persistence).replaceAllConfigs(org.mockito.ArgumentMatchers
            .argThat(configs -> "restored".equals(configs.get(PLUGIN_ID).get("endpoint"))
                && configs.get("unknown:test").isEmpty()));
        
        resolver.restoreConfigs(null);
        assertTrue(resolver.getAllConfigs().isEmpty());
        verify(persistence).replaceAllConfigs(Collections.emptyMap());
    }
    
    @Test
    void testRestoreFailureKeepsCurrentSource() {
        RuntimePersistedPluginConfigSourceResolver resolver =
            new RuntimePersistedPluginConfigSourceResolver(persistence);
        resolver.updateConfig(PLUGIN_ID, Collections.singletonMap("endpoint", "current"));
        Map<String, Map<String, String>> restored = Collections.singletonMap(PLUGIN_ID,
            Collections.singletonMap("endpoint", "failed"));
        doThrow(new PluginPersistenceException("restore failed")).when(persistence)
            .replaceAllConfigs(restored);
        
        assertThrows(PluginPersistenceException.class,
            () -> resolver.restoreConfigs(restored));
        assertEquals("current", resolver.getConfig(pluginInfo()).get("endpoint"));
    }
    
    @Test
    void testStorageInitializationFailureIsIsolatedFromStartup() {
        PluginConfigStorage storage = mock(PluginConfigStorage.class);
        doThrow(new IllegalStateException("initialization failed")).when(storage).initialize();
        RuntimePersistedPluginConfigSourceResolver resolver =
            new RuntimePersistedPluginConfigSourceResolver(provider("remote", storage));
        
        resolver.initialize();
        
        assertFalse(resolver.isAvailable());
        assertTrue(resolver.getAllConfigs().isEmpty());
        assertThrows(PluginPersistenceException.class, () -> resolver.updateConfig(PLUGIN_ID,
            Collections.singletonMap("endpoint", "value")));
        verify(storage).shutdown();
        verify(storage, never()).loadAllConfigs();
    }
    
    @Test
    void testStorageReadFailureIsIsolatedFromStartup() {
        PluginConfigStorage storage = mock(PluginConfigStorage.class);
        when(storage.loadAllConfigs()).thenThrow(new IllegalStateException("read failed"));
        RuntimePersistedPluginConfigSourceResolver resolver =
            new RuntimePersistedPluginConfigSourceResolver(provider("remote", storage));
        
        resolver.initialize();
        
        assertFalse(resolver.isAvailable());
        verify(storage).initialize();
        verify(storage).shutdown();
    }
    
    @Test
    void testNullOrBrokenProviderLeavesSourceUnavailable() {
        RuntimePersistedPluginConfigSourceResolver missing =
            new RuntimePersistedPluginConfigSourceResolver(
                (PluginConfigStorageProvider) null);
        PluginConfigStorageProvider nullStorage = mock(PluginConfigStorageProvider.class);
        when(nullStorage.getName()).thenReturn("null-storage");
        when(nullStorage.createStorage()).thenReturn(null);
        RuntimePersistedPluginConfigSourceResolver nullStorageResolver =
            new RuntimePersistedPluginConfigSourceResolver(nullStorage);
        PluginConfigStorageProvider broken = mock(PluginConfigStorageProvider.class);
        when(broken.getName()).thenThrow(new LinkageError("name failed"));
        when(broken.createStorage()).thenThrow(new LinkageError("create failed"));
        RuntimePersistedPluginConfigSourceResolver brokenResolver =
            new RuntimePersistedPluginConfigSourceResolver(broken);
        
        missing.initialize();
        nullStorageResolver.initialize();
        brokenResolver.initialize();
        
        assertFalse(missing.isAvailable());
        assertFalse(nullStorageResolver.isAvailable());
        assertFalse(brokenResolver.isAvailable());
        assertThrows(PluginPersistenceException.class, () -> missing.updateConfig(PLUGIN_ID,
            Collections.singletonMap("endpoint", "value")));
    }
    
    @Test
    void testInitializeIsIdempotentAndShutdownFailureIsContained() {
        PluginConfigStorage storage = mock(PluginConfigStorage.class);
        when(storage.loadAllConfigs()).thenReturn(Collections.emptyMap());
        doThrow(new IllegalStateException("shutdown failed")).when(storage).shutdown();
        PluginConfigStorageProvider provider = provider("remote", storage);
        RuntimePersistedPluginConfigSourceResolver resolver =
            new RuntimePersistedPluginConfigSourceResolver(provider);
        
        resolver.initialize();
        resolver.initialize();
        resolver.shutdown();
        
        assertFalse(resolver.isAvailable());
        verify(provider, times(1)).createStorage();
        verify(storage, times(1)).initialize();
        verify(storage, times(1)).loadAllConfigs();
        verify(storage, times(1)).shutdown();
    }
    
    private PluginConfigStorageProvider provider(String name, PluginConfigStorage storage) {
        PluginConfigStorageProvider result = mock(PluginConfigStorageProvider.class);
        when(result.getName()).thenReturn(name);
        when(result.createStorage()).thenReturn(storage);
        return result;
    }
    
    private PluginInfo pluginInfo() {
        ConfigItemDefinition definition = new ConfigItemDefinition();
        definition.setKey("endpoint");
        PluginInfo result = new PluginInfo();
        result.setPluginId(PLUGIN_ID);
        result.setPluginType(PluginType.TRACE);
        result.setPluginName("test");
        result.setConfigDefinitions(Collections.singletonList(definition));
        return result;
    }
}
