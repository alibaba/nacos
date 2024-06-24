/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.remote.tls;

import com.alibaba.nacos.common.remote.client.RpcTlsConfigFactory;
import com.alibaba.nacos.common.remote.client.RpcConstants;

import java.util.Properties;

import static com.alibaba.nacos.common.remote.client.RpcConstants.NACOS_SERVER_RPC;
import static com.alibaba.nacos.common.remote.client.RpcConstants.ServerSuffix.COMPATIBILITY;
import static com.alibaba.nacos.common.remote.client.RpcConstants.ServerSuffix.MUTUAL_AUTH;
import static com.alibaba.nacos.common.remote.client.RpcConstants.ServerSuffix.SSL_CONTEXT_REFRESHER;
import static com.alibaba.nacos.common.remote.client.RpcConstants.ServerSuffix.TLS_CERT_CHAIN_PATH;
import static com.alibaba.nacos.common.remote.client.RpcConstants.ServerSuffix.TLS_CERT_KEY;
import static com.alibaba.nacos.common.remote.client.RpcConstants.ServerSuffix.TLS_CIPHERS;
import static com.alibaba.nacos.common.remote.client.RpcConstants.ServerSuffix.TLS_ENABLE;
import static com.alibaba.nacos.common.remote.client.RpcConstants.ServerSuffix.TLS_PROTOCOLS;
import static com.alibaba.nacos.common.remote.client.RpcConstants.ServerSuffix.TLS_PROVIDER;
import static com.alibaba.nacos.common.remote.client.RpcConstants.ServerSuffix.TLS_TRUST_ALL;
import static com.alibaba.nacos.common.remote.client.RpcConstants.ServerSuffix.TLS_TRUST_COLLECTION_CHAIN_PATH;
import static com.alibaba.nacos.common.remote.client.RpcConstants.ServerSuffix.TLS_TRUST_PWD;

/**
 * RpcServerTlsConfigFactory.
 *
 * @author stone-98
 * @date 2024/4/8
 */
public class RpcServerTlsConfigFactory implements RpcTlsConfigFactory {

    private static RpcServerTlsConfigFactory instance;

    private RpcServerTlsConfigFactory() {
    }

    public static synchronized RpcServerTlsConfigFactory getInstance() {
        if (instance == null) {
            instance = new RpcServerTlsConfigFactory();
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
    public RpcServerTlsConfig createSdkConfig(Properties properties) {
        return createServerTlsConfig(properties, NACOS_SERVER_RPC);
    }

    /**
     * Create cluster client TLS config.
     *
     * @param properties Properties containing TLS configuration
     * @return RpcClientTlsConfig object representing the TLS configuration
     */
    @Override
    public RpcServerTlsConfig createClusterConfig(Properties properties) {
        return createServerTlsConfig(properties, RpcConstants.NACOS_PEER_RPC);
    }

    /**
     * create sdk server tls config.
     *
     * @param properties properties
     * @param prefix     prefix
     * @return
     */
    public RpcServerTlsConfig createServerTlsConfig(Properties properties, String prefix) {
        RpcServerTlsConfig tlsConfig = new RpcServerTlsConfig();
        tlsConfig.setEnableTls(getBooleanProperty(properties, prefix + TLS_ENABLE, false));
        tlsConfig.setMutualAuthEnable(getBooleanProperty(properties, prefix + MUTUAL_AUTH, false));
        tlsConfig.setProtocols(properties.getProperty(prefix + TLS_PROTOCOLS));
        tlsConfig.setCiphers(properties.getProperty(prefix + TLS_CIPHERS));
        tlsConfig.setTrustCollectionCertFile(properties.getProperty(prefix + TLS_TRUST_COLLECTION_CHAIN_PATH));
        tlsConfig.setCertChainFile(properties.getProperty(prefix + TLS_CERT_CHAIN_PATH));
        tlsConfig.setCertPrivateKey(properties.getProperty(prefix + TLS_CERT_KEY));
        tlsConfig.setTrustAll(getBooleanProperty(properties, prefix + TLS_TRUST_ALL, true));
        tlsConfig.setCertPrivateKeyPassword(properties.getProperty(prefix + TLS_TRUST_PWD));
        tlsConfig.setSslProvider(properties.getProperty(prefix + TLS_PROVIDER));
        tlsConfig.setSslContextRefresher(properties.getProperty(prefix + SSL_CONTEXT_REFRESHER));
        tlsConfig.setCompatibility(getBooleanProperty(properties, prefix + COMPATIBILITY, true));
        return tlsConfig;
    }
}
