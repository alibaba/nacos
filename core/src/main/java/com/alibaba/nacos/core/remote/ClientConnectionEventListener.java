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

/**
 * ClientConnectionEventListener.
 *
 * @author liuzunfei
 * @version $Id: ClientConnectionEventListener.java, v 0.1 2020年07月16日 3:06 PM liuzunfei Exp $
 */
public interface ClientConnectionEventListener {
    
    /**
     * notified when a client connected.
     *
     * @param connect connect.
     */
    public void clientConnected(Connection connect);
    
    /**
     * notified when a client disconnected.
     *
     * @param connect connect.
     */
    public void clientDisConnected(Connection connect);
    
}