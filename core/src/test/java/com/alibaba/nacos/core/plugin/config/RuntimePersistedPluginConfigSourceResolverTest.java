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
import com.alibaba.nacos.core.plugin.model.PluginConfigSourceType;
import com.alibaba.nacos.core.plugin.model.PluginInfo;
import com.alibaba.nacos.core.plugin.storage.PluginPersistenceException;
import com.alibaba.nacos.core.plugin.storage.PluginStatePersistenceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuntimePersistedPluginConfigSourceResolverTest {
    
    private static final String PLUGIN_ID = "trace:test";
    
    @Mock
    private PluginStatePersistenceService persistence;
    
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
