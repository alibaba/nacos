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

package com.alibaba.nacos.common.remote.client;

/**
 * Status of rpc client.
 *
 * @author liuzunfei
 * @version $Id: RpcClientStatus.java, v 0.1 2020年07月14日 3:49 PM liuzunfei Exp $
 */
public enum RpcClientStatus {
    
    /**
     * Wait to init.
     */
    WAIT_INIT(0, "Wait to init server list factory..."),
    
    /**
     * Already init.
     */
    INITED(1, "Server list factory is ready, wait to starting..."),
    
    /**
     * In starting.
     */
    STARTING(2, "Client already staring, wait to connect with server..."),
    
    /**
     * Unhealthy.
     */
    UNHEALTHY(3, "Client unhealthy, may closed by server, in reconnecting"),
    
    /**
     * In running.
     */
    RUNNING(4, "Client is running"),
    
    /**
     * Shutdown.
     */
    SHUTDOWN(5, "Client is shutdown");
    
    int status;
    
    String desc;
    
    RpcClientStatus(int status, String desc) {
        this.status = status;
        this.desc = desc;
    }
}
