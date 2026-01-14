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
import com.alibaba.nacos.consistency.snapshot.SnapshotOperation;
import com.alibaba.nacos.consistency.snapshot.Writer;
import com.alibaba.nacos.core.plugin.model.PluginStateSnapshot;
import com.alibaba.nacos.core.plugin.storage.PluginStatePersistenceService;
import com.alibaba.nacos.sys.utils.DiskUtils;
import com.alipay.sofa.jraft.util.CRC64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiConsumer;
import java.util.zip.Checksum;

/**
 * Plugin state snapshot operation for Raft recovery.
 * Handles snapshot save and load for plugin states and configurations.
 *
 * @author WangzJi
 * @since 3.2.0
 */
public class PluginStateSnapshotOperation implements SnapshotOperation {

    private static final Logger LOGGER = LoggerFactory.getLogger(PluginStateSnapshotOperation.class);

    private static final String SNAPSHOT_ARCHIVE = "plugin_state.zip";

    private static final String SNAPSHOT_CHILD_NAME = "plugin";

    private static final String CHECK_SUM_KEY = "checksum";

    private final PluginStatePersistenceService persistence;

    private final PluginManager pluginManager;

    private final Serializer serializer;

    private final ReentrantReadWriteLock lock;

    public PluginStateSnapshotOperation(PluginStatePersistenceService persistence, PluginManager pluginManager,
            ReentrantReadWriteLock lock) {
        this.persistence = persistence;
        this.pluginManager = pluginManager;
        this.serializer = SerializeFactory.getDefault();
        this.lock = lock;
    }

    @Override
    public void onSnapshotSave(Writer writer, BiConsumer<Boolean, Throwable> callFinally) {
        lock.writeLock().lock();
        try {
            // Load all states and configs
            Map<String, Boolean> states = persistence.loadAllStates();
            Map<String, Map<String, String>> configs = persistence.loadAllConfigs();

            // Create snapshot
            PluginStateSnapshot snapshot = new PluginStateSnapshot();
            snapshot.setStates(states);
            snapshot.setConfigs(configs);

            // Serialize
            byte[] data = serializer.serialize(snapshot);

            // Write to snapshot with compression and checksum
            final String writePath = writer.getPath();
            final String outputFile = Paths.get(writePath, SNAPSHOT_ARCHIVE).toString();
            final Checksum checksum = new CRC64();

            try (ByteArrayInputStream inputStream = new ByteArrayInputStream(data)) {
                DiskUtils.compressIntoZipFile(SNAPSHOT_CHILD_NAME, inputStream, outputFile, checksum);
            }

            final LocalFileMeta meta = new LocalFileMeta();
            meta.append(CHECK_SUM_KEY, Long.toHexString(checksum.getValue()));

            boolean success = writer.addFile(SNAPSHOT_ARCHIVE, meta);

            LOGGER.info("[PluginStateSnapshotOperation] Snapshot saved: {} states, {} configs, success={}",
                    states.size(), configs.size(), success);

            callFinally.accept(success, null);
        } catch (Exception e) {
            LOGGER.error("[PluginStateSnapshotOperation] Failed to save snapshot", e);
            callFinally.accept(false, e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public boolean onSnapshotLoad(Reader reader) {
        lock.writeLock().lock();
        try {
            // Read snapshot file
            final String readerPath = reader.getPath();
            final String sourceFile = Paths.get(readerPath, SNAPSHOT_ARCHIVE).toString();
            final Checksum checksum = new CRC64();

            byte[] snapshotBytes = DiskUtils.decompress(sourceFile, checksum);

            // Verify checksum
            LocalFileMeta fileMeta = reader.getFileMeta(SNAPSHOT_ARCHIVE);
            if (fileMeta.getFileMeta().containsKey(CHECK_SUM_KEY)) {
                if (!Objects.equals(Long.toHexString(checksum.getValue()), fileMeta.get(CHECK_SUM_KEY))) {
                    throw new IllegalArgumentException("Snapshot checksum failed");
                }
            } else {
                LOGGER.warn("[PluginStateSnapshotOperation] Snapshot has no checksum metadata, data integrity not verified");
            }

            // Deserialize
            PluginStateSnapshot snapshot = serializer.deserialize(snapshotBytes, PluginStateSnapshot.class);

            // Restore states
            Map<String, Boolean> states = snapshot.getStates();
            if (states != null) {
                for (Map.Entry<String, Boolean> entry : states.entrySet()) {
                    persistence.saveState(entry.getKey(), entry.getValue());
                    pluginManager.applyStateChange(entry.getKey(), entry.getValue());
                }
            }

            // Restore configs
            Map<String, Map<String, String>> configs = snapshot.getConfigs();
            if (configs != null) {
                for (Map.Entry<String, Map<String, String>> entry : configs.entrySet()) {
                    persistence.saveConfig(entry.getKey(), entry.getValue());
                    pluginManager.applyConfigChange(entry.getKey(), entry.getValue());
                }
            }

            LOGGER.info("[PluginStateSnapshotOperation] Snapshot loaded: {} states, {} configs",
                    states != null ? states.size() : 0, configs != null ? configs.size() : 0);

            return true;
        } catch (Exception e) {
            LOGGER.error("[PluginStateSnapshotOperation] Failed to load snapshot", e);
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }
}
