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
 * statsu of rpc client.
 *
 * @author liuzunfei
 * @version $Id: RpcClientStatus.java, v 0.1 2020年07月14日 3:49 PM liuzunfei Exp $
 */
public enum RpcClientStatus {
    
    /**
     * wait to init.
     */
    WAIT_INIT(0, "wait to  init serverlist factory... "),
    
    /**
     * inited.
     */
    INITED(1, "server list factory is ready,wait to start"),
    
    /**
     * is in starting.
     */
    STARTING(2, "server list factory is ready,wait to start"),
    
    /**
     * running.
     */
    RUNNING(4, "client is running..."),
    
    /**
     * is in starting.
     */
    UNHEALTHY(3, "client unhealthy,may closed by server,in rereconnecting"),
    
    /**
     * running.
     */
    SHUTDOWN(5, "client is shutdown...");
    
    int status;
    
    String desc;
    
    RpcClientStatus(int status, String desc) {
        this.status = status;
        this.desc = desc;
    }
}
