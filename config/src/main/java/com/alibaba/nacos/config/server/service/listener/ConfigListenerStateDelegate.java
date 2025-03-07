/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.service.listener;

import com.alibaba.nacos.api.config.model.ConfigListenerInfo;
import org.springframework.stereotype.Service;

/**
 * Delegate for Config Listener State Service.
 *
 * @author xiweng.yy
 */
@Service
public class ConfigListenerStateDelegate {
    
    private final LocalConfigListenerStateServiceImpl localService;
    
    private final RemoteConfigListenerStateServiceImpl remoteService;
    
    public ConfigListenerStateDelegate(LocalConfigListenerStateServiceImpl localService,
            RemoteConfigListenerStateServiceImpl remoteService) {
        this.localService = localService;
        this.remoteService = remoteService;
    }
    
    public ConfigListenerInfo getListenerState(String dataId, String groupName, String namespaceId,
            boolean aggregation) {
        ConfigListenerInfo result = localService.getListenerState(dataId, groupName, namespaceId);
        if (aggregation) {
            result.getListenersStatus()
                    .putAll(remoteService.getListenerState(dataId, groupName, namespaceId).getListenersStatus());
        }
        return result;
    }
    
    public ConfigListenerInfo getListenerStateByIp(String ip, boolean aggregation) {
        ConfigListenerInfo result = localService.getListenerStateByIp(ip);
        if (aggregation) {
            result.getListenersStatus().putAll(remoteService.getListenerStateByIp(ip).getListenersStatus());
        }
        return result;
    }
    
}
