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

import com.alibaba.nacos.api.remote.request.ServerPushRequest;
import com.alibaba.nacos.api.remote.response.PushCallBack;
import com.alibaba.nacos.common.remote.exception.ConnectionAlreadyClosedException;
import com.alibaba.nacos.core.utils.Loggers;
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
     * push response without callback. if the client of the specific connectionId is already close,will return true.
     *
     * @param connectionId connectionId.
     * @param request     request.
     */
    public boolean push(String connectionId, ServerPushRequest request, long timeoutMills) {
    
        Connection connection = connectionManager.getConnection(connectionId);
        if (connection != null) {
            try {
                return connection.sendRequest(request, timeoutMills);
            } catch (ConnectionAlreadyClosedException e) {
                connectionManager.unregister(connectionId);
                return true;
            } catch (Exception e) {
                Loggers.RPC_DIGEST
                        .error("error to send push response to connectionId ={},push response={}", connectionId,
                        request, e);
                return false;
            }
        } else {
            return true;
        }
    }
    
    /**
     * push response without callback. if the client of the specific connectionId is already close,will return true.
     *
     * @param connectionId connectionId.
     * @param request     request.
     */
    public PushFuture pushWithFuture(String connectionId, ServerPushRequest request) {
        
        Connection connection = connectionManager.getConnection(connectionId);
        if (connection != null) {
            try {
                return connection.sendRequestWithFuture(request);
            } catch (ConnectionAlreadyClosedException e) {
                connectionManager.unregister(connectionId);
            } catch (Exception e) {
                Loggers.RPC_DIGEST
                        .error("error to send push response to connectionId ={},push response={}", connectionId,
                        request, e);
            }
        }
        return null;
    }
    
    /**
     * push response with no ack.
     *
     * @param connectionId connectionId.
     * @param request     request.
     * @param pushCallBack pushCallBack.
     */
    public void pushWithCallback(String connectionId, ServerPushRequest request, PushCallBack pushCallBack) {
        Connection connection = connectionManager.getConnection(connectionId);
        if (connection != null) {
            try {
                connection.sendRequestWithCallBack(request, pushCallBack);
            } catch (ConnectionAlreadyClosedException e) {
                connectionManager.unregister(connectionId);
            } catch (Exception e) {
                Loggers.RPC_DIGEST
                        .error("error to send push response to connectionId ={},push response={}", connectionId,
                        request, e);
            }
        }
    }
    
    /**
     * push response with no ack.
     *
     * @param connectionId connectionId.
     * @param request     request.
     */
    public void pushWithoutAck(String connectionId, ServerPushRequest request) {
        Connection connection = connectionManager.getConnection(connectionId);
        if (connection != null) {
            try {
                connection.sendRequestNoAck(request);
            } catch (ConnectionAlreadyClosedException e) {
                connectionManager.unregister(connectionId);
            } catch (Exception e) {
                Loggers.RPC_DIGEST
                        .error("error to send push response to connectionId ={},push response={}", connectionId,
                        request, e);
            }
        }
    }
    
}
