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

package com.alibaba.nacos.core.remote.core;

import com.alibaba.nacos.core.remote.ClientConnectionEventListener;
import com.alibaba.nacos.core.remote.Connection;
import com.alibaba.nacos.core.remote.RpcAckCallbackSynchronizer;
import org.springframework.stereotype.Component;

/**
 * RemoteConnectionEventListener.
 *
 * @author liuzunfei
 * @version $Id: RemoteConnectionEventListener.java, v 0.1 2020年08月10日 1:04 AM liuzunfei Exp $
 */
@Component
public class RpcAckCallbackInitorOrCleaner extends ClientConnectionEventListener {
    
    @Override
    public void clientConnected(Connection connect) {
        RpcAckCallbackSynchronizer.initContextIfNecessary(connect.getMetaInfo().getConnectionId());
    }
    
    @Override
    public void clientDisConnected(Connection connect) {
        RpcAckCallbackSynchronizer.clearContext(connect.getMetaInfo().getConnectionId());
    }
}
