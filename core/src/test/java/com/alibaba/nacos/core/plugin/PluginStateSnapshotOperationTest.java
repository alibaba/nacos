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
import com.alibaba.nacos.consistency.snapshot.LocalFileMeta;
import com.alibaba.nacos.consistency.snapshot.Reader;
import com.alibaba.nacos.consistency.snapshot.Writer;
import com.alibaba.nacos.core.plugin.model.PluginStateSnapshot;
import com.alibaba.nacos.core.plugin.storage.PluginStatePersistenceService;
import com.alibaba.nacos.sys.utils.DiskUtils;
import com.alipay.sofa.jraft.util.CRC64;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.zip.Checksum;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PluginStateSnapshotOperation} unit test.
 *
 * @author WangzJi
 */
@ExtendWith(MockitoExtension.class)
class PluginStateSnapshotOperationTest {

    @Mock
    private PluginStatePersistenceService persistence;

    @Mock
    private PluginManager pluginManager;

    @Mock
    private Writer writer;

    @Mock
    private Reader reader;

    @TempDir
    Path tempDir;

    private PluginStateSnapshotOperation snapshotOperation;

    private Serializer serializer;

    @BeforeEach
    void setUp() {
        snapshotOperation = new PluginStateSnapshotOperation(
                persistence, pluginManager, new ReentrantReadWriteLock());
        serializer = SerializeFactory.getDefault();
    }

    @AfterEach
    void tearDown() {
        // Clean up temp files if needed
    }

    @Test
    void onSnapshotSaveSuccessTest() throws Exception {
        // Prepare test data
        Map<String, Boolean> states = new HashMap<>();
        states.put("trace:otel", true);
        states.put("auth:nacos", false);

        Map<String, Map<String, String>> configs = new HashMap<>();
        Map<String, String> otelConfig = new HashMap<>();
        otelConfig.put("endpoint", "http://localhost:8080");
        configs.put("trace:otel", otelConfig);

        when(persistence.loadAllStates()).thenReturn(states);
        when(persistence.loadAllConfigs()).thenReturn(configs);
        when(writer.getPath()).thenReturn(tempDir.toString());
        when(writer.addFile(eq("plugin_state.zip"), any(LocalFileMeta.class))).thenReturn(true);

        AtomicBoolean callbackSuccess = new AtomicBoolean(false);
        AtomicReference<Throwable> callbackError = new AtomicReference<>();

        snapshotOperation.onSnapshotSave(writer, (success, error) -> {
            callbackSuccess.set(success);
            callbackError.set(error);
        });

        assertTrue(callbackSuccess.get());
        assertTrue(callbackError.get() == null);
        assertTrue(Files.exists(tempDir.resolve("plugin_state.zip")));
    }

    @Test
    void onSnapshotSaveEmptyDataTest() throws Exception {
        when(persistence.loadAllStates()).thenReturn(new HashMap<>());
        when(persistence.loadAllConfigs()).thenReturn(new HashMap<>());
        when(writer.getPath()).thenReturn(tempDir.toString());
        when(writer.addFile(eq("plugin_state.zip"), any(LocalFileMeta.class))).thenReturn(true);

        AtomicBoolean callbackSuccess = new AtomicBoolean(false);

        snapshotOperation.onSnapshotSave(writer, (success, error) -> {
            callbackSuccess.set(success);
        });

        assertTrue(callbackSuccess.get());
    }

    @Test
    void onSnapshotSaveFailureTest() throws Exception {
        when(persistence.loadAllStates()).thenReturn(new HashMap<>());
        when(persistence.loadAllConfigs()).thenReturn(new HashMap<>());
        when(writer.getPath()).thenReturn(tempDir.toString());
        when(writer.addFile(eq("plugin_state.zip"), any(LocalFileMeta.class))).thenReturn(false);

        AtomicBoolean callbackSuccess = new AtomicBoolean(true);

        snapshotOperation.onSnapshotSave(writer, (success, error) -> {
            callbackSuccess.set(success);
        });

        assertFalse(callbackSuccess.get());
    }

    @Test
    void onSnapshotLoadSuccessTest() throws Exception {
        // Prepare snapshot data
        Map<String, Boolean> states = new HashMap<>();
        states.put("trace:otel", true);
        states.put("auth:nacos", false);

        Map<String, Map<String, String>> configs = new HashMap<>();
        Map<String, String> otelConfig = new HashMap<>();
        otelConfig.put("endpoint", "http://localhost:8080");
        configs.put("trace:otel", otelConfig);

        PluginStateSnapshot snapshot = new PluginStateSnapshot();
        snapshot.setStates(states);
        snapshot.setConfigs(configs);

        // Create snapshot file
        byte[] data = serializer.serialize(snapshot);
        String snapshotFile = tempDir.resolve("plugin_state.zip").toString();
        Checksum checksum = new CRC64();
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(data)) {
            DiskUtils.compressIntoZipFile("plugin", inputStream, snapshotFile, checksum);
        }

        // Setup mocks
        when(reader.getPath()).thenReturn(tempDir.toString());
        LocalFileMeta fileMeta = new LocalFileMeta();
        fileMeta.append("checksum", Long.toHexString(checksum.getValue()));
        when(reader.getFileMeta("plugin_state.zip")).thenReturn(fileMeta);

        doNothing().when(persistence).saveState(anyString(), any(Boolean.class));
        doNothing().when(persistence).saveConfig(anyString(), anyMap());
        doNothing().when(pluginManager).applyStateChange(anyString(), any(Boolean.class));
        doNothing().when(pluginManager).applyConfigChange(anyString(), anyMap());

        boolean result = snapshotOperation.onSnapshotLoad(reader);

        assertTrue(result);
        verify(persistence, times(2)).saveState(anyString(), any(Boolean.class));
        verify(persistence, times(1)).saveConfig(anyString(), anyMap());
        verify(pluginManager, times(2)).applyStateChange(anyString(), any(Boolean.class));
        verify(pluginManager, times(1)).applyConfigChange(anyString(), anyMap());
    }

    @Test
    void onSnapshotLoadNullStatesTest() throws Exception {
        // Prepare snapshot with null states
        PluginStateSnapshot snapshot = new PluginStateSnapshot();
        snapshot.setStates(null);
        snapshot.setConfigs(null);

        // Create snapshot file
        byte[] data = serializer.serialize(snapshot);
        String snapshotFile = tempDir.resolve("plugin_state.zip").toString();
        Checksum checksum = new CRC64();
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(data)) {
            DiskUtils.compressIntoZipFile("plugin", inputStream, snapshotFile, checksum);
        }

        // Setup mocks
        when(reader.getPath()).thenReturn(tempDir.toString());
        LocalFileMeta fileMeta = new LocalFileMeta();
        fileMeta.append("checksum", Long.toHexString(checksum.getValue()));
        when(reader.getFileMeta("plugin_state.zip")).thenReturn(fileMeta);

        boolean result = snapshotOperation.onSnapshotLoad(reader);

        assertTrue(result);
        verify(persistence, never()).saveState(anyString(), any(Boolean.class));
        verify(persistence, never()).saveConfig(anyString(), anyMap());
        verify(pluginManager, never()).applyStateChange(anyString(), any(Boolean.class));
        verify(pluginManager, never()).applyConfigChange(anyString(), anyMap());
    }

    @Test
    void onSnapshotLoadChecksumMismatchTest() throws Exception {
        // Prepare snapshot data
        PluginStateSnapshot snapshot = new PluginStateSnapshot();
        snapshot.setStates(new HashMap<>());
        snapshot.setConfigs(new HashMap<>());

        // Create snapshot file
        byte[] data = serializer.serialize(snapshot);
        String snapshotFile = tempDir.resolve("plugin_state.zip").toString();
        Checksum checksum = new CRC64();
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(data)) {
            DiskUtils.compressIntoZipFile("plugin", inputStream, snapshotFile, checksum);
        }

        // Setup mocks with wrong checksum
        when(reader.getPath()).thenReturn(tempDir.toString());
        LocalFileMeta fileMeta = new LocalFileMeta();
        fileMeta.append("checksum", "wrongchecksum");
        when(reader.getFileMeta("plugin_state.zip")).thenReturn(fileMeta);

        boolean result = snapshotOperation.onSnapshotLoad(reader);

        assertFalse(result);
        verify(persistence, never()).saveState(anyString(), any(Boolean.class));
        verify(pluginManager, never()).applyStateChange(anyString(), any(Boolean.class));
    }

    @Test
    void onSnapshotLoadFileNotFoundTest() {
        when(reader.getPath()).thenReturn(tempDir.resolve("nonexistent").toString());

        boolean result = snapshotOperation.onSnapshotLoad(reader);

        assertFalse(result);
    }
}
