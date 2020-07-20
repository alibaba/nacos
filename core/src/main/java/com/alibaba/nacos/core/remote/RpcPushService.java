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
import com.alibaba.nacos.api.remote.response.ServerPushResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * push response  to clients.
 *
 * @author liuzunfei
 * @version $Id: PushService.java, v 0.1 2020年07月20日 1:12 PM liuzunfei Exp $
 */
@Service
public class RpcPushService {
    
    @Autowired
    private ConnectionManager connectionManager;
    
    /**
     * push response without callback.
     *
     * @param connectionId connectionId.
     * @param response     response.
     */
    public void push(String connectionId, ServerPushResponse response) {
        Connection connection = connectionManager.getConnection(connectionId);
        if (connection != null) {
            connection.sendResponse(response);
        }
    }
    
    /**
     * push response with callback. [not support yet]
     *
     * @param connectionId connectionId.
     * @param response     response.
     * @param pushCallBack pushCallBack.
     */
    public void push(String connectionId, ServerPushResponse response, PushCallBack pushCallBack) {
        push(connectionId, response);
    }
    
}