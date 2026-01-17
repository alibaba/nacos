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
import com.alibaba.nacos.consistency.Serializer;
import com.alibaba.nacos.consistency.cp.CPProtocol;
import com.alibaba.nacos.consistency.entity.ReadRequest;
import com.alibaba.nacos.consistency.entity.Response;
import com.alibaba.nacos.consistency.entity.WriteRequest;
import com.alibaba.nacos.consistency.snapshot.SnapshotOperation;
import com.alibaba.nacos.core.distributed.ProtocolManager;
import com.alibaba.nacos.core.plugin.model.PluginStateOperation;
import com.alibaba.nacos.core.plugin.storage.PluginStatePersistenceService;
import com.google.protobuf.ByteString;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PluginStateProcessor} unit test.
 *
 * @author WangzJi
 */
@ExtendWith(MockitoExtension.class)
class PluginStateProcessorTest {

    @Mock
    private PluginManager pluginManager;

    @Mock
    private PluginStatePersistenceService persistence;

    @Mock
    private ProtocolManager protocolManager;

    @Mock
    private CPProtocol cpProtocol;

    private PluginStateProcessor processor;

    private Serializer serializer;

    @BeforeEach
    void setUp() {
        when(protocolManager.getCpProtocol()).thenReturn(cpProtocol);
        doNothing().when(cpProtocol).addRequestProcessors(anyList());

        processor = new PluginStateProcessor(pluginManager, persistence, protocolManager);
        serializer = SerializeFactory.getDefault();
    }

    @Test
    void groupTest() {
        assertEquals("plugin_state", processor.group());
    }

    @Test
    void onRequestTest() {
        ReadRequest request = ReadRequest.newBuilder().build();

        Response response = processor.onRequest(request);

        assertNotNull(response);
        assertTrue(response.getSuccess());
    }

    @Test
    void onApplyChangeStateTest() throws Exception {
        PluginStateOperation operation = PluginStateOperation.builder()
                .type(PluginStateOperation.OperationType.CHANGE_STATE)
                .pluginId("trace:otel")
                .enabled(false)
                .build();

        byte[] data = serializer.serialize(operation);
        WriteRequest request = WriteRequest.newBuilder()
                .setData(ByteString.copyFrom(data))
                .build();

        Response response = processor.onApply(request);

        assertNotNull(response);
        assertTrue(response.getSuccess());
        verify(pluginManager, times(1)).applyStateChange("trace:otel", false);
        verify(persistence, times(1)).saveState("trace:otel", false);
    }

    @Test
    void onApplyUpdateConfigTest() throws Exception {
        Map<String, String> config = new HashMap<>();
        config.put("endpoint", "http://localhost:8080");
        config.put("timeout", "5000");

        PluginStateOperation operation = PluginStateOperation.builder()
                .type(PluginStateOperation.OperationType.UPDATE_CONFIG)
                .pluginId("trace:otel")
                .config(config)
                .build();

        byte[] data = serializer.serialize(operation);
        WriteRequest request = WriteRequest.newBuilder()
                .setData(ByteString.copyFrom(data))
                .build();

        Response response = processor.onApply(request);

        assertNotNull(response);
        assertTrue(response.getSuccess());
        verify(pluginManager, times(1)).applyConfigChange("trace:otel", config);
        verify(persistence, times(1)).saveConfig("trace:otel", config);
    }

    @Test
    void onApplyInvalidDataTest() {
        WriteRequest request = WriteRequest.newBuilder()
                .setData(ByteString.copyFrom(new byte[]{0, 1, 2, 3}))
                .build();

        Response response = processor.onApply(request);

        assertNotNull(response);
        assertFalse(response.getSuccess());
        assertNotNull(response.getErrMsg());
    }

    @Test
    void loadSnapshotOperateTest() {
        List<SnapshotOperation> operations = processor.loadSnapshotOperate();

        assertNotNull(operations);
        assertEquals(1, operations.size());
        assertTrue(operations.get(0) instanceof PluginStateSnapshotOperation);
    }
}
