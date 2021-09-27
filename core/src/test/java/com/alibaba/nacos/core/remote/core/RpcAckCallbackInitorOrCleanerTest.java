/*
 *  Copyright 1999-2021 Alibaba Group Holding Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package com.alibaba.nacos.core.remote.core;

import com.alibaba.nacos.core.remote.Connection;
import com.alibaba.nacos.core.remote.ConnectionMeta;
import com.alibaba.nacos.core.remote.grpc.GrpcConnection;
import org.junit.Test;

import java.util.HashMap;

/**
 * {@link RpcAckCallbackInitorOrCleaner} unit test.
 *
 * @author chenglu
 * @date 2021-07-01 13:39
 */
public class RpcAckCallbackInitorOrCleanerTest {
   
    @Test
    public void testInitAndCleaner() {
        String connectId = "11";
        ConnectionMeta meta = new ConnectionMeta(connectId, "", "", 80, 80, "GRPC", "", "", new HashMap<>());
        Connection connection = new GrpcConnection(meta, null, null);
        
        RpcAckCallbackInitorOrCleaner initorOrCleaner = new RpcAckCallbackInitorOrCleaner();
        initorOrCleaner.clientConnected(connection);
        
        initorOrCleaner.clientDisConnected(connection);
    }
}
