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

package com.alibaba.nacos.core.remote.tls;

import com.alibaba.nacos.core.remote.BaseRpcServer;

/**
 * {@link RpcServerSslContextRefresher} uint test.
 *
 * @author stone-98
 * @date 2024-06-21 21:43
 */
public class RpcClusterServerSslContextRefresherTest implements RpcServerSslContextRefresher {
    
    public static final String NAME = "cluster-refresher-test";
    
    @Override
    public SslContextChangeAware refresh(BaseRpcServer baseRpcServer) {
        return new SslContextChangeAware() {
            @Override
            public void init(BaseRpcServer baseRpcServer) {
            
            }
            
            @Override
            public void onSslContextChange() {
            
            }
            
            @Override
            public void shutdown() {
            
            }
        };
    }
    
    @Override
    public String getName() {
        return NAME;
    }
}
