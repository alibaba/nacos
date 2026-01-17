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
import com.alibaba.nacos.consistency.cp.RequestProcessor4CP;
import com.alibaba.nacos.consistency.entity.ReadRequest;
import com.alibaba.nacos.consistency.entity.Response;
import com.alibaba.nacos.consistency.entity.WriteRequest;
import com.alibaba.nacos.consistency.snapshot.SnapshotOperation;
import com.alibaba.nacos.core.distributed.ProtocolManager;
import com.alibaba.nacos.core.plugin.model.PluginStateOperation;
import com.alibaba.nacos.core.plugin.storage.PluginStatePersistenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Plugin state processor for Raft consensus.
 * Handles plugin state changes and config updates via Raft replication.
 *
 * @author WangzJi
 * @since 3.2.0
 */
@Component
public class PluginStateProcessor extends RequestProcessor4CP {

    private static final Logger LOGGER = LoggerFactory.getLogger(PluginStateProcessor.class);

    private static final String GROUP = "plugin_state";

    private final PluginManager pluginManager;

    private final PluginStatePersistenceService persistence;

    private final Serializer serializer;

    private final ReentrantReadWriteLock lock;

    private final ReentrantReadWriteLock.ReadLock readLock;

    public PluginStateProcessor(PluginManager pluginManager, PluginStatePersistenceService persistence,
            ProtocolManager protocolManager) {
        this.pluginManager = pluginManager;
        this.persistence = persistence;
        this.serializer = SerializeFactory.getDefault();
        this.lock = new ReentrantReadWriteLock();
        this.readLock = lock.readLock();

        // Register with Raft protocol
        protocolManager.getCpProtocol().addRequestProcessors(Collections.singletonList(this));
    }

    @Override
    public String group() {
        return GROUP;
    }

    @Override
    public Response onRequest(ReadRequest request) {
        // Read operations can go directly to PluginManager
        return Response.newBuilder().setSuccess(true).build();
    }

    @Override
    public Response onApply(WriteRequest request) {
        readLock.lock();
        PluginStateOperation operation = null;
        try {
            operation = serializer.deserialize(
                    request.getData().toByteArray(),
                    PluginStateOperation.class
            );

            switch (operation.getType()) {
                case CHANGE_STATE:
                    applyStateChange(operation);
                    break;
                case UPDATE_CONFIG:
                    applyConfigUpdate(operation);
                    break;
                default:
                    return Response.newBuilder()
                            .setSuccess(false)
                            .setErrMsg("Unknown operation type: " + operation.getType())
                            .build();
            }

            return Response.newBuilder().setSuccess(true).build();
        } catch (Exception e) {
            String context = buildErrorContext(operation);
            LOGGER.error("[PluginStateProcessor] Failed to apply plugin state change: {}", context, e);
            String errorMessage = String.format("[%s] %s", context,
                    e.getMessage() != null ? e.getMessage() : e.getClass().getName());
            return Response.newBuilder()
                    .setSuccess(false)
                    .setErrMsg(errorMessage)
                    .build();
        } finally {
            readLock.unlock();
        }
    }

    private String buildErrorContext(PluginStateOperation operation) {
        if (operation == null) {
            return "operation=null";
        }
        String pluginId = operation.getPluginId() != null ? operation.getPluginId() : "unknown";
        String opType = operation.getType() != null ? operation.getType().name() : "unknown";
        return "pluginId=" + pluginId + ", operation=" + opType;
    }

    private void applyStateChange(PluginStateOperation operation) {
        String pluginId = operation.getPluginId();
        Boolean enabled = operation.getEnabled();

        if (enabled == null) {
            throw new IllegalArgumentException(
                    "Enabled state cannot be null for CHANGE_STATE operation, pluginId=" + pluginId);
        }

        // Apply to in-memory state
        pluginManager.applyStateChange(pluginId, enabled);

        // Persist to local storage
        persistence.saveState(pluginId, enabled);

        LOGGER.info("[PluginStateProcessor] Applied state change: {}={}", pluginId, enabled);
    }

    private void applyConfigUpdate(PluginStateOperation operation) {
        String pluginId = operation.getPluginId();
        Map<String, String> config = operation.getConfig();

        // Apply to in-memory config
        pluginManager.applyConfigChange(pluginId, config);

        // Persist to local storage
        persistence.saveConfig(pluginId, config);

        LOGGER.info("[PluginStateProcessor] Applied config update: {}", pluginId);
    }

    @Override
    public List<SnapshotOperation> loadSnapshotOperate() {
        return Collections.singletonList(
                new PluginStateSnapshotOperation(persistence, pluginManager, lock)
        );
    }
}
