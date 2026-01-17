/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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

import com.alibaba.nacos.consistency.SerializeFactory;
import com.alibaba.nacos.consistency.entity.WriteRequest;
import com.alibaba.nacos.consistency.entity.Response;
import com.alibaba.nacos.core.plugin.model.PluginStateOperation;
import com.alibaba.nacos.core.plugin.storage.PluginStatePersistenceService;
import com.alibaba.nacos.core.plugin.sync.PluginStateSynchronizer;
import com.alibaba.nacos.core.distributed.ProtocolManager;
import com.alibaba.nacos.consistency.cp.CPProtocol;
import com.google.protobuf.ByteString;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Integration test for plugin cluster synchronization.
 * Verifies Raft-based state propagation and snapshot operations.
 *
 * @author WangzJi
 */
@ExtendWith(MockitoExtension.class)
class PluginClusterSyncIntegrationTest {

    @Mock
    private PluginStatePersistenceService persistence;

    @Mock
    private PluginStateSynchronizer synchronizer;

    @Mock
    private CPProtocol cpProtocol;

    @Mock
    private ProtocolManager protocolManager;

    private PluginManager pluginManager;

    private PluginStateProcessor stateProcessor;

    @BeforeEach
    void setUp() {
        lenient().when(persistence.loadAllStates()).thenReturn(new HashMap<>());
        lenient().when(persistence.loadAllConfigs()).thenReturn(new HashMap<>());

        lenient().when(protocolManager.getCpProtocol()).thenReturn(cpProtocol);
        lenient().doNothing().when(cpProtocol).addRequestProcessors(anyList());

        pluginManager = new PluginManager(persistence, synchronizer);

        stateProcessor = new PluginStateProcessor(pluginManager, persistence, protocolManager);

        registerTestPlugin("trace", "otel");
    }

    @Test
    void stateChangePropagationTest() throws Exception {
        pluginManager.setPluginEnabled("trace:otel", false);

        verify(synchronizer, times(1)).syncStateChange("trace:otel", false);
    }

    @Test
    void configUpdatePropagationTest() throws Exception {
        registerConfigurablePlugin("trace", "otel");

        Map<String, String> config = new HashMap<>();
        config.put("endpoint", "http://localhost:4317");
        config.put("timeout", "5000");

        pluginManager.updatePluginConfig("trace:otel", config);

        verify(synchronizer, times(1)).syncConfigChange(eq("trace:otel"), eq(config));
    }

    @Test
    void raftApplyStateChangeTest() throws Exception {
        assertTrue(pluginManager.isPluginEnabled("trace", "otel"));

        PluginStateOperation operation = PluginStateOperation.builder()
                .type(PluginStateOperation.OperationType.CHANGE_STATE)
                .pluginId("trace:otel")
                .enabled(false)
                .build();

        byte[] data = SerializeFactory.getDefault().serialize(operation);
        WriteRequest request = WriteRequest.newBuilder()
                .setData(ByteString.copyFrom(data))
                .build();

        Response response = stateProcessor.onApply(request);

        assertTrue(response.getSuccess());
        assertFalse(pluginManager.isPluginEnabled("trace", "otel"));
    }

    @Test
    void raftApplyConfigUpdateTest() throws Exception {
        Map<String, String> config = new HashMap<>();
        config.put("key1", "value1");
        config.put("key2", "value2");

        PluginStateOperation operation = PluginStateOperation.builder()
                .type(PluginStateOperation.OperationType.UPDATE_CONFIG)
                .pluginId("trace:otel")
                .config(config)
                .build();

        byte[] data = SerializeFactory.getDefault().serialize(operation);
        WriteRequest request = WriteRequest.newBuilder()
                .setData(ByteString.copyFrom(data))
                .build();

        Response response = stateProcessor.onApply(request);

        assertTrue(response.getSuccess());
    }

    @Test
    void endToEndStateSyncTest() throws Exception {
        assertTrue(pluginManager.isPluginEnabled("trace", "otel"));

        // Simulate state change through PluginManager
        pluginManager.setPluginEnabled("trace:otel", false);

        // Verify synchronizer was called
        verify(synchronizer, times(1)).syncStateChange("trace:otel", false);

        // Simulate Raft apply (what happens after Raft consensus)
        PluginStateOperation operation = PluginStateOperation.builder()
                .type(PluginStateOperation.OperationType.CHANGE_STATE)
                .pluginId("trace:otel")
                .enabled(false)
                .build();

        byte[] data = SerializeFactory.getDefault().serialize(operation);
        WriteRequest raftRequest = WriteRequest.newBuilder()
                .setData(ByteString.copyFrom(data))
                .build();

        Response raftResponse = stateProcessor.onApply(raftRequest);

        assertTrue(raftResponse.getSuccess());
        assertFalse(pluginManager.isPluginEnabled("trace", "otel"));
    }

    private void registerTestPlugin(String type, String name) {
        String pluginId = type + ":" + name;
        Map<String, com.alibaba.nacos.core.plugin.model.PluginInfo> registry = getPluginRegistry();

        com.alibaba.nacos.core.plugin.model.PluginInfo info = new com.alibaba.nacos.core.plugin.model.PluginInfo();
        info.setPluginId(pluginId);
        info.setPluginName(name);
        info.setPluginType(com.alibaba.nacos.api.plugin.PluginType.fromType(type));
        info.setClassName("TestPlugin");
        info.setCritical(false);
        info.setEnabled(true);
        info.setConfigurable(false);

        registry.put(pluginId, info);

        Map<String, Boolean> states = getPluginStates();
        states.put(pluginId, true);
    }

    private void registerConfigurablePlugin(String type, String name) {
        String pluginId = type + ":" + name;
        Map<String, com.alibaba.nacos.core.plugin.model.PluginInfo> registry = getPluginRegistry();

        com.alibaba.nacos.core.plugin.model.PluginInfo info = new com.alibaba.nacos.core.plugin.model.PluginInfo();
        info.setPluginId(pluginId);
        info.setPluginName(name);
        info.setPluginType(com.alibaba.nacos.api.plugin.PluginType.fromType(type));
        info.setClassName("TestConfigurablePlugin");
        info.setCritical(false);
        info.setEnabled(true);
        info.setConfigurable(true);

        registry.put(pluginId, info);

        Map<String, Boolean> states = getPluginStates();
        states.put(pluginId, true);
    }

    @SuppressWarnings("unchecked")
    private Map<String, com.alibaba.nacos.core.plugin.model.PluginInfo> getPluginRegistry() {
        return (Map<String, com.alibaba.nacos.core.plugin.model.PluginInfo>)
                ReflectionTestUtils.getField(pluginManager, "pluginRegistry");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Boolean> getPluginStates() {
        return (Map<String, Boolean>) ReflectionTestUtils.getField(pluginManager, "pluginStates");
    }
}
