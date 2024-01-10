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

import com.alibaba.nacos.common.remote.TlsConfig;

import java.util.Properties;

/**
 * gRPC config for sdk.
 *
 * @author githubcheng2978
 */
public class RpcSdkClientTlsConfig extends TlsConfig {
    
    /**
     * get tls config from properties.
     *
     * @param properties Properties.
     * @return tls of config.
     */
    public static RpcSdkClientTlsConfig properties(Properties properties) {
        RpcSdkClientTlsConfig tlsConfig = new RpcSdkClientTlsConfig();
        if (properties.containsKey(RpcConstants.RPC_CLIENT_TLS_ENABLE)) {
            tlsConfig.setEnableTls(
                    Boolean.parseBoolean(properties.getProperty(RpcConstants.RPC_CLIENT_TLS_ENABLE)));
        }
        
        if (properties.containsKey(RpcConstants.RPC_CLIENT_MUTUAL_AUTH)) {
            tlsConfig.setMutualAuthEnable(
                    Boolean.parseBoolean(properties.getProperty(RpcConstants.RPC_CLIENT_MUTUAL_AUTH)));
        }
        
        if (properties.containsKey(RpcConstants.RPC_CLIENT_TLS_PROTOCOLS)) {
            tlsConfig.setProtocols(properties.getProperty(RpcConstants.RPC_CLIENT_TLS_PROTOCOLS));
        }
        
        if (properties.containsKey(RpcConstants.RPC_CLIENT_TLS_CIPHERS)) {
            tlsConfig.setCiphers(properties.getProperty(RpcConstants.RPC_CLIENT_TLS_CIPHERS));
        }
        
        if (properties.containsKey(RpcConstants.RPC_CLIENT_TLS_TRUST_COLLECTION_CHAIN_PATH)) {
            tlsConfig.setTrustCollectionCertFile(
                    properties.getProperty(RpcConstants.RPC_CLIENT_TLS_TRUST_COLLECTION_CHAIN_PATH));
        }
        
        if (properties.containsKey(RpcConstants.RPC_CLIENT_TLS_CERT_CHAIN_PATH)) {
            tlsConfig.setCertChainFile(properties.getProperty(RpcConstants.RPC_CLIENT_TLS_CERT_CHAIN_PATH));
        }
        
        if (properties.containsKey(RpcConstants.RPC_CLIENT_TLS_CERT_KEY)) {
            tlsConfig.setCertPrivateKey(properties.getProperty(RpcConstants.RPC_CLIENT_TLS_CERT_KEY));
        }
        
        if (properties.containsKey(RpcConstants.RPC_CLIENT_TLS_TRUST_ALL)) {
            tlsConfig.setTrustAll(
                    Boolean.parseBoolean(properties.getProperty(RpcConstants.RPC_CLIENT_TLS_TRUST_ALL)));
        }
        
        if (properties.containsKey(RpcConstants.RPC_CLIENT_TLS_TRUST_PWD)) {
            tlsConfig.setCertPrivateKeyPassword(properties.getProperty(RpcConstants.RPC_CLIENT_TLS_TRUST_PWD));
        }
        
        if (properties.containsKey(RpcConstants.RPC_CLIENT_TLS_PROVIDER)) {
            tlsConfig.setSslProvider(properties.getProperty(RpcConstants.RPC_CLIENT_TLS_PROVIDER));
        }
        return tlsConfig;
    }
    
}
