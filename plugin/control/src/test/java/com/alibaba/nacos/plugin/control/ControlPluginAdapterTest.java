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

package com.alibaba.nacos.plugin.control;

import com.alibaba.nacos.api.plugin.ConfigItemDefinition;
import com.alibaba.nacos.api.plugin.ConfigItemEffectMode;
import com.alibaba.nacos.api.plugin.ConfigItemType;
import com.alibaba.nacos.plugin.control.connection.ConnectionControlManager;
import com.alibaba.nacos.plugin.control.spi.ControlManagerBuilder;
import com.alibaba.nacos.plugin.control.tps.TpsControlManager;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ControlPluginAdapterTest {
    
    @Test
    void testApplyConfigBeforeIdempotentInitialization() {
        ConfigItemDefinition definition =
            new ConfigItemDefinition("endpoint", "Endpoint", ConfigItemType.STRING);
        RecordingBuilder builder =
            new RecordingBuilder(Collections.singletonList(definition));
        ControlManagerCenter managerCenter = new ControlManagerCenter();
        ControlPluginAdapter adapter = new ControlPluginAdapter(builder, managerCenter);
        Map<String, String> input = new LinkedHashMap<>();
        input.put("endpoint", "first");
        
        adapter.applyConfig(input);
        input.put("endpoint", "changed");
        Map<String, String> returnedConfig = adapter.getCurrentConfig();
        returnedConfig.put("endpoint", "returned-change");
        adapter.initialize();
        adapter.initialize();
        
        assertEquals("test", adapter.getPluginName());
        assertEquals(Collections.singletonList(definition), adapter.getConfigDefinitions());
        assertThrows(UnsupportedOperationException.class,
            () -> adapter.getConfigDefinitions().add(definition));
        assertEquals("first", adapter.getCurrentConfig().get("endpoint"));
        assertNotSame(returnedConfig, adapter.getCurrentConfig());
        assertEquals(Collections.singletonMap("endpoint", "first"), builder.getReceivedConfig());
        assertEquals(1, builder.getConnectionBuildCount());
        assertEquals(1, builder.getTpsBuildCount());
        assertEquals("testConnection",
            managerCenter.getConnectionControlManager().getName());
        assertEquals("testTps", managerCenter.getTpsControlManager().getName());
    }
    
    @Test
    void testNullConfigAndDefinitions() {
        RecordingBuilder builder = new RecordingBuilder(null);
        ControlPluginAdapter adapter =
            new ControlPluginAdapter(builder, new ControlManagerCenter());
        
        adapter.applyConfig(null);
        
        assertTrue(adapter.getConfigDefinitions().isEmpty());
        assertTrue(adapter.getCurrentConfig().isEmpty());
    }
    
    @Test
    void testNullConnectionAndThrowingTpsFallbackIndependently() {
        FailureBuilder builder = new FailureBuilder(false, true);
        ControlManagerCenter managerCenter = new ControlManagerCenter();
        ControlPluginAdapter adapter = new ControlPluginAdapter(builder, managerCenter);
        
        adapter.initialize();
        
        assertEquals("noLimit", managerCenter.getConnectionControlManager().getName());
        assertEquals("noLimit", managerCenter.getTpsControlManager().getName());
    }
    
    @Test
    void testThrowingConnectionAndNullTpsFallbackIndependently() {
        FailureBuilder builder = new FailureBuilder(true, false);
        ControlManagerCenter managerCenter = new ControlManagerCenter();
        ControlPluginAdapter adapter = new ControlPluginAdapter(builder, managerCenter);
        
        adapter.initialize();
        
        assertEquals("noLimit", managerCenter.getConnectionControlManager().getName());
        assertEquals("noLimit", managerCenter.getTpsControlManager().getName());
    }
    
    @Test
    void testRejectInvalidDependenciesAndIgnoreInvalidDefinitions() {
        RecordingBuilder validBuilder =
            new RecordingBuilder(Collections.emptyList());
        assertThrows(NullPointerException.class, () -> new ControlPluginAdapter(null));
        assertThrows(NullPointerException.class,
            () -> new ControlPluginAdapter(validBuilder, null));
        
        List<ConfigItemDefinition> nullItemDefinitions = new ArrayList<>();
        nullItemDefinitions.add(null);
        ControlPluginAdapter nullItemAdapter = new ControlPluginAdapter(
            new RecordingBuilder(nullItemDefinitions), new ControlManagerCenter());
        
        ConfigItemDefinition runtimeDefinition =
            new ConfigItemDefinition("runtime", "Runtime", ConfigItemType.STRING);
        runtimeDefinition.setEffectMode(ConfigItemEffectMode.RUNTIME);
        ControlPluginAdapter runtimeAdapter = new ControlPluginAdapter(
            new RecordingBuilder(Collections.singletonList(runtimeDefinition)),
            new ControlManagerCenter());
        
        assertTrue(nullItemAdapter.getConfigDefinitions().isEmpty());
        assertTrue(runtimeAdapter.getConfigDefinitions().isEmpty());
    }
    
    @Test
    void testControlManagerBundleRejectsNullManager() {
        ControlManagerBuilderTest builder = new ControlManagerBuilderTest();
        ConnectionControlManager connectionManager =
            builder.buildConnectionControlManager();
        TpsControlManager tpsManager = builder.buildTpsControlManager();
        
        assertThrows(NullPointerException.class,
            () -> new ControlManagerBundle(null, tpsManager));
        assertThrows(NullPointerException.class,
            () -> new ControlManagerBundle(connectionManager, null));
    }
    
    private static class RecordingBuilder extends ControlManagerBuilderTest {
        
        private final List<ConfigItemDefinition> definitions;
        
        private Map<String, String> receivedConfig;
        
        private int connectionBuildCount;
        
        private int tpsBuildCount;
        
        private RecordingBuilder(List<ConfigItemDefinition> definitions) {
            this.definitions = definitions;
        }
        
        @Override
        public List<ConfigItemDefinition> getConfigDefinitions() {
            return definitions;
        }
        
        @Override
        public ConnectionControlManager buildConnectionControlManager(
            Map<String, String> config) {
            receivedConfig = new LinkedHashMap<>(config);
            connectionBuildCount++;
            return super.buildConnectionControlManager();
        }
        
        @Override
        public TpsControlManager buildTpsControlManager(Map<String, String> config) {
            receivedConfig = new LinkedHashMap<>(config);
            tpsBuildCount++;
            return super.buildTpsControlManager();
        }
        
        private Map<String, String> getReceivedConfig() {
            return receivedConfig;
        }
        
        private int getConnectionBuildCount() {
            return connectionBuildCount;
        }
        
        private int getTpsBuildCount() {
            return tpsBuildCount;
        }
    }
    
    private static class FailureBuilder implements ControlManagerBuilder {
        
        private final boolean connectionThrows;
        
        private final boolean tpsThrows;
        
        private FailureBuilder(boolean connectionThrows, boolean tpsThrows) {
            this.connectionThrows = connectionThrows;
            this.tpsThrows = tpsThrows;
        }
        
        @Override
        public String getName() {
            return "failure";
        }
        
        @Override
        public ConnectionControlManager buildConnectionControlManager() {
            if (connectionThrows) {
                throw new IllegalStateException("connection failed");
            }
            return null;
        }
        
        @Override
        public TpsControlManager buildTpsControlManager() {
            if (tpsThrows) {
                throw new IllegalStateException("tps failed");
            }
            return null;
        }
    }
}
