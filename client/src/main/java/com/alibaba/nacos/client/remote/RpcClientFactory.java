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

package com.alibaba.nacos.client.remote;

import com.alibaba.nacos.client.remote.grpc.GrpcClient;

import java.util.HashMap;
import java.util.Map;

/**
 * RpcClientFactory.to support muti client for diffrent modules of usage.
 *
 * @author liuzunfei
 * @version $Id: RpcClientFactory.java, v 0.1 2020年07月14日 3:41 PM liuzunfei Exp $
 */
public class RpcClientFactory {
    
    private static RpcClient sharedClient;
    
    static Map<String, RpcClient> clientMap = new HashMap<String, RpcClient>();
    
    public static RpcClient getClient(String module) {
        synchronized (clientMap) {
            if (clientMap.get(module) == null) {
                RpcClient moduleClient = new GrpcClient();
                clientMap.putIfAbsent(module, moduleClient);
            }
        
            return clientMap.get(module);
        
        }
        
    }
    
}
