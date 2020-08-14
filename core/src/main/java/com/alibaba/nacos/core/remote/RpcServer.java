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

import com.alibaba.nacos.common.remote.ConnectionType;
import com.alibaba.nacos.core.utils.ApplicationUtils;
import com.alibaba.nacos.core.utils.Loggers;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.PostConstruct;

/**
 * abstrat rpc server .
 *
 * @author liuzunfei
 * @version $Id: RpcServer.java, v 0.1 2020年07月13日 3:41 PM liuzunfei Exp $
 */
public abstract class RpcServer {
    
    @Autowired
    private ConnectionManager connectionManager;
    
    /**
     * Start sever.
     */
    @PostConstruct
    public void start() throws Exception {
    
        Loggers.RPC.info("Nacos {} Rpc server starting at port {}", getConnectionType(),
                (ApplicationUtils.getPort() + rpcPortOffset()));
    
        startServer();
    
        Loggers.RPC.info("Nacos {} Rpc server started at port {}", getConnectionType(),
                (ApplicationUtils.getPort() + rpcPortOffset()));
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                Loggers.RPC.info("Nacos {} Rpc server stopping", getConnectionType());
                try {
                    RpcServer.this.stopServer();
                    Loggers.RPC.info("Nacos {} Rpc server stopped successfully...", getConnectionType());
                } catch (Exception e) {
                    Loggers.RPC.error("Nacos {} Rpc server stopped fail...", getConnectionType(), e);
                }
            }
        });
    }
    
    /**
     * get connection type.
     *
     * @return
     */
    public abstract ConnectionType getConnectionType();
    
    /**
     * Start sever.
     */
    public abstract void startServer() throws Exception;
    
    /**
     * the increase offset of nacos server port for rpc server port.
     *
     * @return
     */
    public abstract int rpcPortOffset();
    
    /**
     * Stop Server.
     */
    public void stopServer() throws Exception {
        Loggers.RPC.info("Nacos clear all rpc clients...");
        connectionManager.expelAll();
        try {
            //wait clients to switch  server.
            Thread.sleep(2000L);
        } catch (InterruptedException e) {
            //Do nothing.
        }
        shundownServer();
    }
    
    /**
     * the increase offset of nacos server port for rpc server port.
     *
     * @return
     */
    public abstract void shundownServer();
    
}
