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

package com.alibaba.nacos.common.remote.client.grpc;

import com.alibaba.nacos.api.ability.constant.AbilityMode;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.common.remote.client.RpcClientTlsConfig;

import java.util.Map;
import java.util.Properties;

/**
 * gRPC client for sdk.
 *
 * @author liuzunfei
 * @version $Id: GrpcSdkClient.java, v 0.1 2020年09月07日 11:05 AM liuzunfei Exp $
 */
public class GrpcSdkClient extends GrpcClient {
    
    /**
     * Constructor.
     *
     * @param name name of client.
     */
    public GrpcSdkClient(String name) {
        super(name);
    }
    
    /**
     * Constructor.
     *
     * @param properties .
     */
    public GrpcSdkClient(Properties properties) {
        super(properties);
    }
    
    /**
     * Constructor.
     *
     * @param name               name of client.
     * @param threadPoolCoreSize .
     * @param threadPoolMaxSize  .
     * @param labels             .
     */
    public GrpcSdkClient(String name, Integer threadPoolCoreSize, Integer threadPoolMaxSize, Map<String, String> labels) {
        this(name, threadPoolCoreSize, threadPoolMaxSize, labels, null);
    }

    public GrpcSdkClient(String name, Integer threadPoolCoreSize, Integer threadPoolMaxSize, Map<String, String> labels,
                         RpcClientTlsConfig tlsConfig) {
        super(name, threadPoolCoreSize, threadPoolMaxSize, labels, tlsConfig);
    }

    @Override
    protected AbilityMode abilityMode() {
        return AbilityMode.SDK_CLIENT;
    }

    /**
     * constructor.
     *
     * @param config of GrpcClientConfig.
     */
    public GrpcSdkClient(GrpcClientConfig config) {
        super(config);
    }
    
    @Override
    public int rpcPortOffset() {
        return Integer.parseInt(System.getProperty(GrpcConstants.NACOS_SERVER_GRPC_PORT_OFFSET_KEY,
                String.valueOf(Constants.SDK_GRPC_PORT_DEFAULT_OFFSET)));
    }
    
}