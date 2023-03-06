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
 * connection event listener of client side.
 * @author liuzunfei
 * @version $Id: ConnectionEventListener.java, v 0.1 2020年07月14日 10:59 AM liuzunfei Exp $
 */
public interface ConnectionEventListener {
    
    /**
     * notify when  connected to server.
     */
    public void onConnected();

    /**
     * notify when  disconnected to server.
     */
    public void onDisConnect();
}
