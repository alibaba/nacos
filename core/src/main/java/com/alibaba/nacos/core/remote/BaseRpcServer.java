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

import com.alibaba.nacos.api.remote.PayloadRegistry;
import com.alibaba.nacos.common.remote.ConnectionType;
import com.alibaba.nacos.core.utils.Loggers;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.PostConstruct;

/**
 * abstrat rpc server .
 *
 * @author liuzunfei
 * @version $Id: BaseRpcServer.java, v 0.1 2020年07月13日 3:41 PM liuzunfei Exp $
 */
public abstract class BaseRpcServer {
    
    @Autowired
    private ConnectionManager connectionManager;
    
    static {
        PayloadRegistry.init();
    }
    
    /**
     * Start sever.
     */
    @PostConstruct
    public void start() throws Exception {
    
        Loggers.REMOTE.info("Nacos {} Rpc server starting at port {}", getClass().getSimpleName(),
                (ApplicationUtils.getPort() + rpcPortOffset()));
    
        startServer();
    
        Loggers.REMOTE.info("Nacos {} Rpc server started at port {}", getClass().getSimpleName(),
                (ApplicationUtils.getPort() + rpcPortOffset()));
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                Loggers.REMOTE.info("Nacos {} Rpc server stopping", getClass().getSimpleName());
                try {
                    BaseRpcServer.this.stopServer();
                    Loggers.REMOTE.info("Nacos {} Rpc server stopped successfully...",
                            BaseRpcServer.this.getClass().getSimpleName());
                } catch (Exception e) {
                    Loggers.REMOTE
                            .error("Nacos {} Rpc server stopped fail...", BaseRpcServer.this.getClass().getSimpleName(),
                                    e);
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
     *
     * @throws Exception excetpion throw if start server fail.
     */
    public abstract void startServer() throws Exception;
    
    /**
     * the increase offset of nacos server port for rpc server port.
     *
     * @return
     */
    public abstract int rpcPortOffset();
    
    /**
     * get service port.
     *
     * @return
     */
    public int getServicePort() {
        return ApplicationUtils.getPort() + rpcPortOffset();
    }
    
    /**
     * Stop Server.
     *
     * @throws excetpion throw if stop server fail.
     */
    public final void stopServer() throws Exception {
        shundownServer();
    }
    
    /**
     * the increase offset of nacos server port for rpc server port.
     *
     * @return
     */
    public abstract void shundownServer();
    
}
