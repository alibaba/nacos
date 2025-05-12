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

/**
 * Nacos config listener statues service.
 *
 * @author xiweng.yy
 */
public interface ConfigListenerStateService {
    
    /**
     * Get config listener state by dataId, groupName, namespaceId.
     *
     * @param dataId        data id of config
     * @param groupName     group name of config
     * @param namespaceId   namespace id of config
     * @return              listener state, include listener ip and config md5
     */
    ConfigListenerInfo getListenerState(String dataId, String groupName, String namespaceId);
    
    /**
     * Get config listener state by listener ip.
     *
     * @param ip    listener ip
     * @return      listener config information, include dataId, groupName, namespaceId and config md5
     */
    ConfigListenerInfo getListenerStateByIp(String ip);
}
