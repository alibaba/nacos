/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.remote;

import com.alibaba.nacos.api.config.remote.request.ConfigFuzzyWatchChangeNotifyRequest;
import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.notify.listener.Subscriber;
import com.alibaba.nacos.config.server.configuration.ConfigCommonConfig;
import com.alibaba.nacos.config.server.model.event.LocalDataChangeEvent;
import com.alibaba.nacos.config.server.service.ConfigCacheService;
import com.alibaba.nacos.config.server.service.ConfigFuzzyWatchContextService;
import com.alibaba.nacos.core.remote.Connection;
import com.alibaba.nacos.core.remote.ConnectionManager;
import com.alibaba.nacos.core.remote.RpcPushService;
import com.alibaba.nacos.core.utils.Loggers;
import org.springframework.stereotype.Component;

import static com.alibaba.nacos.api.common.Constants.ConfigChangedType.CONFIG_CHANGED;
import static com.alibaba.nacos.api.common.Constants.ConfigChangedType.DELETE_CONFIG;

/**
 * Notify remote clients about fuzzy listen configuration changes. Use subscriber mode to monitor local data changes,
 * and push notifications to remote clients accordingly.
 *
 * @author stone-98
 * @date 2024/3/18
 */
@Component
public class ConfigFuzzyWatchChangeNotifier extends Subscriber<LocalDataChangeEvent> {
    
    private final ConnectionManager connectionManager;
    
    private RpcPushService rpcPushService;
    
    private final ConfigFuzzyWatchContextService configFuzzyWatchContextService;
    
    /**
     * Constructs RpcFuzzyListenConfigChangeNotifier with the specified dependencies.
     *
     * @param connectionManager The manager for connections.
     * @param rpcPushService    The service for RPC push.
     */
    public ConfigFuzzyWatchChangeNotifier(ConnectionManager connectionManager, RpcPushService rpcPushService,
            ConfigFuzzyWatchContextService configFuzzyWatchContextService) {
        this.rpcPushService = rpcPushService;
        this.connectionManager = connectionManager;
        this.configFuzzyWatchContextService = configFuzzyWatchContextService;
        NotifyCenter.registerSubscriber(this);
    }
    
    @Override
    public void onEvent(LocalDataChangeEvent event) {
        
        boolean exists = ConfigCacheService.getContentCache(event.groupKey) != null;
        //can not recognize add or update,  set config_changed here
        String changedType = exists ? CONFIG_CHANGED : DELETE_CONFIG;
        boolean needNotify = configFuzzyWatchContextService.syncGroupKeyContext(event.groupKey, changedType);
        if (needNotify) {
            for (String clientId : configFuzzyWatchContextService.getMatchedClients(event.groupKey)) {
                Connection connection = connectionManager.getConnection(clientId);
                if (null == connection) {
                    Loggers.REMOTE_PUSH.warn(
                            "clientId not found, Config change notification not sent. clientId={},keyGroupPattern={}",
                            clientId, event.groupKey);
                    continue;
                }
                
                ConfigFuzzyWatchChangeNotifyRequest request = new ConfigFuzzyWatchChangeNotifyRequest(event.groupKey,
                        changedType);
                int maxPushRetryTimes = ConfigCommonConfig.getInstance().getMaxPushRetryTimes();
                FuzzyWatchChangeNotifyTask fuzzyWatchChangeNotifyTask = new FuzzyWatchChangeNotifyTask(
                        connectionManager, rpcPushService, request, maxPushRetryTimes, clientId);
                fuzzyWatchChangeNotifyTask.scheduleSelf();
            }
        }
        
    }
    
    @Override
    public Class<? extends Event> subscribeType() {
        return LocalDataChangeEvent.class;
    }
    
}
