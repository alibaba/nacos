/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.remote;

import com.alibaba.nacos.api.remote.connection.Connection;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.common.utils.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * DataChangeListenerNotifier.
 *
 * @author liuzunfei
 * @version $Id: DataChangeLisenerNotifier.java, v 0.1 2020年07月14日 10:44 AM liuzunfei Exp $
 */

@Service
public class DataChangeListenerNotifier {
    
    /**
     * connect manager.
     */
    @Autowired
    ConnectionManager connectionManager;
    
    /**
     * asyncListenContext.
     */
    @Autowired
    AsyncListenContext asyncListenContext;
    
    /**
     * adaptor to config module ,when server side congif change ,invoke this method.
     *
     * @param groupKey       groupKey
     * @param notifyResponse notifyResponse
     */
    public void configDataChanged(String groupKey, Response notifyResponse) {
        
        Set<String> listeners = asyncListenContext.getListeners(NacosRemoteConstants.LISTEN_CONTEXT_CONFIG, groupKey);
        if (!CollectionUtils.isEmpty(listeners)) {
            for (String connectionId : listeners) {
                Connection connection = connectionManager.getConnection(connectionId);
                if (connection != null) {
                    connection.sendResponse(notifyResponse);
                }
            }
        }
    }
    
    public void serviceIndoChanged(String serviceKey, Response notifyResponse) {
        //TODO
    }
}
