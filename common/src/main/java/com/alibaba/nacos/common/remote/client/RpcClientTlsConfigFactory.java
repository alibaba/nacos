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

import java.util.Properties;

import static com.alibaba.nacos.common.remote.client.RpcConstants.ClientSuffix.MUTUAL_AUTH;
import static com.alibaba.nacos.common.remote.client.RpcConstants.ClientSuffix.TLS_CERT_CHAIN_PATH;
import static com.alibaba.nacos.common.remote.client.RpcConstants.ClientSuffix.TLS_CERT_KEY;
import static com.alibaba.nacos.common.remote.client.RpcConstants.ClientSuffix.TLS_CIPHERS;
import static com.alibaba.nacos.common.remote.client.RpcConstants.ClientSuffix.TLS_ENABLE;
import static com.alibaba.nacos.common.remote.client.RpcConstants.ClientSuffix.TLS_PROTOCOLS;
import static com.alibaba.nacos.common.remote.client.RpcConstants.ClientSuffix.TLS_PROVIDER;
import static com.alibaba.nacos.common.remote.client.RpcConstants.ClientSuffix.TLS_TRUST_ALL;
import static com.alibaba.nacos.common.remote.client.RpcConstants.ClientSuffix.TLS_TRUST_COLLECTION_CHAIN_PATH;
import static com.alibaba.nacos.common.remote.client.RpcConstants.ClientSuffix.TLS_TRUST_PWD;
import static com.alibaba.nacos.common.remote.client.RpcConstants.NACOS_CLIENT_RPC;
import static com.alibaba.nacos.common.remote.client.RpcConstants.NACOS_PEER_RPC;

/**
 * TlsConfigFactory.
 *
 * @author stone-98
 */
public class RpcClientTlsConfigFactory implements RpcTlsConfigFactory {

    private static RpcClientTlsConfigFactory instance;

    private RpcClientTlsConfigFactory() {
    }

    public static synchronized RpcClientTlsConfigFactory getInstance() {
        if (instance == null) {
            instance = new RpcClientTlsConfigFactory();
        }
        return instance;
    }

    /**
     * Create SDK client TLS config.
     *
     * @param properties Properties containing TLS configuration
     * @return RpcClientTlsConfig object representing the TLS configuration
     */
    @Override
    public RpcClientTlsConfig createSdkConfig(Properties properties) {
        RpcClientTlsConfig tlsConfig = new RpcClientTlsConfig();
        tlsConfig.setEnableTls(getBooleanProperty(properties, NACOS_CLIENT_RPC + TLS_ENABLE, false));
        tlsConfig.setMutualAuthEnable(getBooleanProperty(properties, NACOS_CLIENT_RPC + MUTUAL_AUTH, false));
        tlsConfig.setProtocols(properties.getProperty(NACOS_CLIENT_RPC + TLS_PROTOCOLS));
        tlsConfig.setCiphers(properties.getProperty(NACOS_CLIENT_RPC + TLS_CIPHERS));
        tlsConfig.setTrustCollectionCertFile(properties.getProperty(NACOS_CLIENT_RPC + TLS_TRUST_COLLECTION_CHAIN_PATH));
        tlsConfig.setCertChainFile(properties.getProperty(NACOS_CLIENT_RPC + TLS_CERT_CHAIN_PATH));
        tlsConfig.setCertPrivateKey(properties.getProperty(NACOS_CLIENT_RPC + TLS_CERT_KEY));
        tlsConfig.setTrustAll(getBooleanProperty(properties, NACOS_CLIENT_RPC + TLS_TRUST_ALL, true));
        tlsConfig.setCertPrivateKeyPassword(properties.getProperty(NACOS_CLIENT_RPC + TLS_TRUST_PWD));
        tlsConfig.setSslProvider(properties.getProperty(NACOS_CLIENT_RPC + TLS_PROVIDER));
        return tlsConfig;
    }

    /**
     * Create cluster client TLS config.
     *
     * @param properties Properties containing TLS configuration
     * @return RpcClientTlsConfig object representing the TLS configuration
     */
    @Override
    public RpcClientTlsConfig createClusterConfig(Properties properties) {
        RpcClientTlsConfig tlsConfig = new RpcClientTlsConfig();
        tlsConfig.setEnableTls(getBooleanProperty(properties, NACOS_PEER_RPC + RpcConstants.ServerSuffix.TLS_ENABLE, false));
        tlsConfig.setMutualAuthEnable(getBooleanProperty(properties, NACOS_PEER_RPC + RpcConstants.ServerSuffix.MUTUAL_AUTH, false));
        tlsConfig.setProtocols(properties.getProperty(NACOS_PEER_RPC + RpcConstants.ServerSuffix.TLS_PROTOCOLS));
        tlsConfig.setCiphers(properties.getProperty(NACOS_PEER_RPC + RpcConstants.ServerSuffix.TLS_CIPHERS));
        tlsConfig.setTrustCollectionCertFile(properties.getProperty(NACOS_PEER_RPC + RpcConstants.ServerSuffix.TLS_TRUST_COLLECTION_CHAIN_PATH));
        tlsConfig.setCertChainFile(properties.getProperty(NACOS_PEER_RPC + RpcConstants.ServerSuffix.TLS_CERT_CHAIN_PATH));
        tlsConfig.setCertPrivateKey(properties.getProperty(NACOS_PEER_RPC + RpcConstants.ServerSuffix.TLS_CERT_KEY));
        tlsConfig.setTrustAll(getBooleanProperty(properties, NACOS_PEER_RPC + RpcConstants.ServerSuffix.TLS_TRUST_ALL, true));
        tlsConfig.setCertPrivateKeyPassword(properties.getProperty(NACOS_PEER_RPC + RpcConstants.ServerSuffix.TLS_TRUST_PWD));
        tlsConfig.setSslProvider(properties.getProperty(NACOS_PEER_RPC + RpcConstants.ServerSuffix.TLS_PROVIDER));
        return tlsConfig;
    }

}
