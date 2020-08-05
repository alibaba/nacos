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

import org.springframework.beans.factory.annotation.Autowired;

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
    public abstract void start() throws Exception;
    
    /**
     * the increase offset of nacos server port for rpc server port.
     *
     * @return
     */
    public abstract int rpcPortOffset();
    
    /**
     * Stop Server.
     */
    public abstract void stop() throws Exception;
    
    public void setMaxClientCount(int maxClient) {
        this.connectionManager.coordinateMaxClientsSmoth(maxClient);
    }
    
    public void reloadClient(int loadCount) {
        this.connectionManager.loadClientsSmoth(loadCount);
    }
    
    public int currentClients() {
        return this.connectionManager.currentClients();
    }
}
