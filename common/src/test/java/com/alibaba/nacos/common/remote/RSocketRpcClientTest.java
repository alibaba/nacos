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

package com.alibaba.nacos.common.remote;

import com.alibaba.nacos.common.remote.client.RpcClient;
import com.alibaba.nacos.common.remote.client.rsocket.RsocketRpcClient;
import io.rsocket.RSocket;
import org.junit.Test;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

/**
 * junit test from rsocket rpc client.
 *
 * @author liuzunfei
 * @version $Id: RSocketRpcClientTest.java, v 0.1 2020年08月07日 1:15 PM liuzunfei Exp $
 */
public class RSocketRpcClientTest {
    
    @Test
    public void testConectToServer() {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        System.out.println(compiler.getSourceVersions());
        RsocketRpcClient rsocketRpcClient = new RsocketRpcClient();
        RpcClient.ServerInfo serverInfo = new RpcClient.ServerInfo();
        serverInfo.setServerIp("127.0.0.1");
        serverInfo.setServerPort(9948);
        RSocket rSocket = rsocketRpcClient.connectToServer("123456", serverInfo);
        System.out.println("Client :" + rSocket);
    }
    
}
