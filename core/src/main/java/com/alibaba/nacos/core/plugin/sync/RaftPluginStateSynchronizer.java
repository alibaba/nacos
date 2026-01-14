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

package com.alibaba.nacos.core.plugin.sync;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.consistency.DataOperation;
import com.alibaba.nacos.consistency.SerializeFactory;
import com.alibaba.nacos.consistency.Serializer;
import com.alibaba.nacos.consistency.cp.CPProtocol;
import com.alibaba.nacos.consistency.entity.Response;
import com.alibaba.nacos.consistency.entity.WriteRequest;
import com.alibaba.nacos.core.distributed.ProtocolManager;
import com.alibaba.nacos.core.plugin.condition.ConditionOnClusterMode;
import com.alibaba.nacos.core.plugin.model.PluginStateOperation;
import com.google.protobuf.ByteString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Raft-based plugin state synchronizer.
 * Uses CPProtocol (Raft) to synchronize plugin states across cluster nodes.
 * Only activated in cluster mode (nacos.standalone=false).
 *
 * @author WangzJi
 * @since 3.2.0
 */
@Component
@Conditional(ConditionOnClusterMode.class)
public class RaftPluginStateSynchronizer implements PluginStateSynchronizer {

    private static final Logger LOGGER = LoggerFactory.getLogger(RaftPluginStateSynchronizer.class);

    private static final String PLUGIN_STATE_GROUP = "plugin_state";

    private final CPProtocol cpProtocol;

    private final Serializer serializer;

    public RaftPluginStateSynchronizer(ProtocolManager protocolManager) {
        this.cpProtocol = protocolManager.getCpProtocol();
        this.serializer = SerializeFactory.getDefault();
        LOGGER.info("[RaftPluginStateSynchronizer] Initialized with Raft protocol");
    }

    @Override
    public void syncStateChange(String pluginId, boolean enabled) throws NacosApiException {
        PluginStateOperation operation = PluginStateOperation.builder()
                .type(PluginStateOperation.OperationType.CHANGE_STATE)
                .pluginId(pluginId)
                .enabled(enabled)
                .build();
        submitToRaft(operation);
    }

    @Override
    public void syncConfigChange(String pluginId, Map<String, String> config) throws NacosApiException {
        PluginStateOperation operation = PluginStateOperation.builder()
                .type(PluginStateOperation.OperationType.UPDATE_CONFIG)
                .pluginId(pluginId)
                .config(config)
                .build();
        submitToRaft(operation);
    }

    private void submitToRaft(PluginStateOperation operation) throws NacosApiException {
        try {
            byte[] data = serializer.serialize(operation);

            WriteRequest request = WriteRequest.newBuilder()
                    .setGroup(PLUGIN_STATE_GROUP)
                    .setData(ByteString.copyFrom(data))
                    .setOperation(DataOperation.CHANGE.name())
                    .build();

            Response response = cpProtocol.write(request);
            if (!response.getSuccess()) {
                throw new NacosApiException(NacosException.SERVER_ERROR, ErrorCode.SERVER_ERROR,
                        "Failed to submit plugin state to Raft: " + response.getErrMsg());
            }
        } catch (Exception e) {
            if (e instanceof NacosApiException) {
                throw (NacosApiException) e;
            }
            throw new NacosApiException(NacosException.SERVER_ERROR, ErrorCode.SERVER_ERROR, e,
                    "Failed to submit plugin state to Raft");
        }
    }
}
