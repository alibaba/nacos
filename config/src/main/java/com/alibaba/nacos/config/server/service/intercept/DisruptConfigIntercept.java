/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.service.intercept;

import com.alibaba.fastjson.JSON;
import com.alibaba.nacos.config.server.annoation.OpenXID;
import com.alibaba.nacos.config.server.configuration.DataSource4ClusterV2;
import com.alibaba.nacos.config.server.enums.ConfigOperationEnum;
import com.alibaba.nacos.config.server.model.ConfigHistoryInfo;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.ConfigInfo4Beta;
import com.alibaba.nacos.config.server.model.ConfigInfo4Tag;
import com.alibaba.nacos.config.server.model.ConfigInfoAggr;
import com.alibaba.nacos.config.server.model.log.ConfigHistoryRequest;
import com.alibaba.nacos.config.server.model.log.ConfigRequest;
import com.alibaba.nacos.config.server.model.log.ConfigTagRelationRequest;
import com.alibaba.nacos.config.server.service.ConfigDataChangeEvent;
import com.alibaba.nacos.config.server.service.consumer.ConfigBizProcessor;
import com.alibaba.nacos.config.server.utils.LogKeyUtils;
import com.alibaba.nacos.config.server.utils.event.EventDispatcher;
import com.alibaba.nacos.core.distributed.NDatum;
import com.alibaba.nacos.core.distributed.id.DistributeIDManager;
import com.alibaba.nacos.core.distributed.raft.jraft.JRaftProtocol;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@ConditionalOnProperty(value = "nacos.config.store.type", havingValue = "inner", matchIfMissing = true)
@Component
public class DisruptConfigIntercept implements Intercept {

    // Distributed ID resources for the configuration management module

    private static final String CONFIG_ID_RESOURCE = ConfigInfo.class.getCanonicalName();
    private static final String CONFIG_HISTORY_ID_RESOURCE = ConfigHistoryInfo.class.getCanonicalName();
    private static final String CONFIG_TAG_ID_RESOURCE = ConfigInfo4Tag.class.getCanonicalName();
    private static final String CONFIG_BETA_ID_RESOURCE = ConfigInfo4Beta.class.getCanonicalName();
    private static final String CONFIG_AGG_ID_RESOURCE = ConfigInfoAggr.class.getCanonicalName();

    private final DataSource4ClusterV2 connectionManager;

    private final JRaftProtocol protocol;

    private final ConfigBizProcessor bizProcessor;

    public DisruptConfigIntercept(final DataSource4ClusterV2 connectionManager,
                                  final JRaftProtocol protocol,
                                  final ConfigBizProcessor bizProcessor) {
        this.connectionManager = connectionManager;
        this.protocol = protocol;
        this.bizProcessor = bizProcessor;
    }

    @PostConstruct
    protected void init() {
        protocol.registerBizProcessor(bizProcessor);
        DistributeIDManager.register(CONFIG_ID_RESOURCE);
        DistributeIDManager.register(CONFIG_TAG_ID_RESOURCE);
        DistributeIDManager.register(CONFIG_BETA_ID_RESOURCE);
        DistributeIDManager.register(CONFIG_HISTORY_ID_RESOURCE);
        DistributeIDManager.register(CONFIG_AGG_ID_RESOURCE);
    }

    @OpenXID
    @Override
    public void configSave(String srcIp, String srcUser, ConfigInfo configInfo, Timestamp time,
                           Map<String, Object> configAdvanceInfo, boolean notify) {

        final String key = LogKeyUtils.build("CONFIG", configInfo.getTenant(), configInfo.getGroup(), configInfo.getDataId());

        final long configId = DistributeIDManager.nextId(CONFIG_ID_RESOURCE);

        // publish config

        final ConfigRequest request = ConfigRequest.builder()
                .id(configId)
                .srcIp(srcIp)
                .srcUser(srcUser)
                .configInfo(configInfo)
                .time(time)
                .configAdvanceInfo(configAdvanceInfo)
                .build();
        commit(key, request, ConfigOperationEnum.CONFIG_PUBLISH.getOperation());

        String configTags = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("config_tags");

        // publish config tag info

        final ConfigTagRelationRequest relationRequest = ConfigTagRelationRequest.builder()
                .configId(configId)
                .tenant(configInfo.getTenant())
                .group(configInfo.getGroup())
                .dataId(configInfo.getDataId())
                .configTags(configTags)
                .build();

        commit(key, relationRequest, ConfigOperationEnum.CONFIG_TAG_RELATION_PUBLISH.getOperation());

        // publish config history

        final ConfigHistoryRequest historyRequest = ConfigHistoryRequest.builder()
                .id(0)
                .configId(configId)
                .srcIp(srcIp)
                .srcUser(srcUser)
                .configInfo(configInfo)
                .ops("I")
                .build();

        commit(key, historyRequest, ConfigOperationEnum.CONFIG_HISTORY_PUBLISH.getOperation());

        if (notify) {
            EventDispatcher.fireEvent(
                    new ConfigDataChangeEvent(false, configInfo.getDataId(), configInfo.getGroup(),
                            configInfo.getTenant(), time.getTime()));
        }
    }

    @OpenXID
    @Override
    public void configTagSave(ConfigInfo configInfo, String tag, String srcIp, String srcUser,
                              Timestamp time, boolean notify) {

    }

    @OpenXID
    @Override
    public void configBetaSave(ConfigInfo configInfo, String betaIps, String srcIp, String srcUser,
                               Timestamp time, boolean notify) {

    }

    @Override
    public void configUpdate(String srcIp, String srcUser, ConfigInfo configInfo, Timestamp time, Map<String, Object> configAdvanceInfo, boolean notify) {

    }

    @Override
    public void configTagUpdate(ConfigInfo configInfo, String tag, String srcIp, String srcUser, Timestamp time, boolean notify) {

    }

    @Override
    public void configBetaUpdate(ConfigInfo configInfo, String betaIps, String srcIp, String srcUser, Timestamp time, boolean notify) {

    }

    @OpenXID
    @Override
    public void configRemove(String dataId, String group, String tenant, String srcIp,
                             String srcUser) {

    }

    @OpenXID
    @Override
    public void configTagRemove(String dataId, String group, String tenant, String tag,
                                String srcIp, String srcUser) {

    }

    @OpenXID
    @Override
    public void configBetaRemove(String dataId, String group, String tenant) {

    }

    private <T> void commit(String key, T data, String operation) {

        final Map<String, String> extendInfo = new HashMap<>(8);

        extendInfo.put("xid", connectionManager.currentXID());

        final NDatum datum = NDatum.builder()
                .operation(operation)
                .className(data.getClass().getCanonicalName())
                .key(key)
                .data(JSON.toJSONBytes(data))
                .extendInfo(extendInfo)
                .build();

        try {
            protocol.submit(datum);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
