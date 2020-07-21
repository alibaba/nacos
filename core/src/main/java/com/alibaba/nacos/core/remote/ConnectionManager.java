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
import com.alibaba.nacos.core.utils.Loggers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * connect manager.
 *
 * @author liuzunfei
 * @version $Id: ConnectionManager.java, v 0.1 2020年07月13日 7:07 PM liuzunfei Exp $
 */
@Service
public class ConnectionManager {
    
    Map<String, Connection> connetions = new HashMap<String, Connection>();
    
    @Autowired
    private ClientConnectionEventListenerRegistry clientConnectionEventListenerRegistry;
    
    /**
     * register a new connect.
     *
     * @param connectionId connectionId
     * @param connection   connection
     */
    public void register(String connectionId, Connection connection) {
        Connection connectionInner = connetions.putIfAbsent(connectionId, connection);
        if (connectionInner == null) {
            clientConnectionEventListenerRegistry.notifyClientConnected(connection);
            Loggers.GRPC.info("new connection registered successfully, connectionid = {} ", connectionId);
        }
    }
    
    /**
     * unregister a connection .
     * @param connectionId connectionId.
     */
    public void unregister(String connectionId) {
        Connection remove = this.connetions.remove(connectionId);
        if (remove != null) {
            remove.closeGrapcefully();
            Loggers.GRPC.info(" connection unregistered successfully,connectionid = {} ", connectionId);
            clientConnectionEventListenerRegistry.notifyClientConnected(remove);
        }
    }
    
    public Connection getConnection(String connectionId) {
        return connetions.get(connectionId);
    }
    
    /**
     * regresh connection active time.
     *
     * @param connnectionId connnectionId.
     */
    public void refreshActiveTime(String connnectionId) {
        Connection connection = connetions.get(connnectionId);
        if (connection != null) {
            connection.freshActiveTime();
        }
    }
    
}
