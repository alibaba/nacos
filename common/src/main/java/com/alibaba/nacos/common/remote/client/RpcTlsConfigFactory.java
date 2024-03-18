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

import com.alibaba.nacos.common.remote.tls.RpcServerTlsConfig;

import java.util.Properties;

/**
 * TlsConfigFactory.
 *
 * @author stone-98
 */
public class RpcTlsConfigFactory {
    
    /**
     * Create SDK client TLS config.
     *
     * @param properties Properties containing TLS configuration
     * @return RpcClientTlsConfig object representing the TLS configuration
     */
    public static RpcClientTlsConfig createSdkClientTlsConfig(Properties properties) {
        return createClientTlsConfig(properties, RpcConstants.NACOS_CLIENT_RPC);
    }
    
    /**
     * Create cluster client TLS config.
     *
     * @param properties Properties containing TLS configuration
     * @return RpcClientTlsConfig object representing the TLS configuration
     */
    public static RpcClientTlsConfig createClusterClientTlsConfig(Properties properties) {
        return createClientTlsConfig(properties, RpcConstants.NACOS_CLUSTER_CLIENT_RPC);
    }
    
    /**
     * Create SDK client TLS config.
     *
     * @param properties Properties containing TLS configuration
     * @return RpcClientTlsConfig object representing the TLS configuration
     */
    public static RpcServerTlsConfig createSdkServerTlsConfig(Properties properties) {
        return createServerTlsConfig(properties, RpcConstants.NACOS_SERVER_RPC);
    }
    
    /**
     * Create cluster client TLS config.
     *
     * @param properties Properties containing TLS configuration
     * @return RpcClientTlsConfig object representing the TLS configuration
     */
    public static RpcServerTlsConfig createClusterServerTlsConfig(Properties properties) {
        return createServerTlsConfig(properties, RpcConstants.NACOS_CLUSTER_SERVER_RPC);
    }
    
    /**
     * Get boolean property from properties.
     *
     * @param properties   Properties containing configuration
     * @param key          Key of the property
     * @param defaultValue Default value to return if the property is not found or is invalid
     * @return Boolean value of the property, or the provided defaultValue if not found or invalid
     */
    private static Boolean getBooleanProperty(Properties properties, String key, Boolean defaultValue) {
        String value = properties.getProperty(key);
        if (value != null) {
            return Boolean.parseBoolean(value);
        }
        return defaultValue;
    }
    
    /**
     * Create client TLS config.
     *
     * @param properties Properties containing TLS configuration
     * @param prefix     Prefix for other configuration keys
     * @return RpcClientTlsConfig object representing the TLS configuration
     */
    public static RpcClientTlsConfig createClientTlsConfig(Properties properties, String prefix) {
        RpcClientTlsConfig tlsConfig = new RpcClientTlsConfig();
        tlsConfig.setEnableTls(getBooleanProperty(properties, prefix + RpcConstants.ClientSuffix.TLS_ENABLE, false));
        tlsConfig.setMutualAuthEnable(
                getBooleanProperty(properties, prefix + RpcConstants.ClientSuffix.MUTUAL_AUTH, false));
        tlsConfig.setProtocols(properties.getProperty(prefix + RpcConstants.ClientSuffix.TLS_PROTOCOLS));
        tlsConfig.setCiphers(properties.getProperty(prefix + RpcConstants.ClientSuffix.TLS_CIPHERS));
        tlsConfig.setTrustCollectionCertFile(
                properties.getProperty(prefix + RpcConstants.ClientSuffix.TLS_TRUST_COLLECTION_CHAIN_PATH));
        tlsConfig.setCertChainFile(properties.getProperty(prefix + RpcConstants.ClientSuffix.TLS_CERT_CHAIN_PATH));
        tlsConfig.setCertPrivateKey(properties.getProperty(prefix + RpcConstants.ClientSuffix.TLS_CERT_KEY));
        tlsConfig.setTrustAll(getBooleanProperty(properties, prefix + RpcConstants.ClientSuffix.TLS_TRUST_ALL, true));
        tlsConfig.setCertPrivateKeyPassword(properties.getProperty(prefix + RpcConstants.ClientSuffix.TLS_TRUST_PWD));
        tlsConfig.setSslProvider(properties.getProperty(prefix + RpcConstants.ClientSuffix.TLS_PROVIDER));
        return tlsConfig;
    }
    
    /**
     * create sdk server tls config.
     *
     * @param properties properties
     * @param prefix     prefix
     * @return
     */
    public static RpcServerTlsConfig createServerTlsConfig(Properties properties, String prefix) {
        RpcServerTlsConfig tlsConfig = new RpcServerTlsConfig();
        tlsConfig.setEnableTls(getBooleanProperty(properties, prefix + RpcConstants.ServerSuffix.TLS_ENABLE, false));
        tlsConfig.setMutualAuthEnable(
                getBooleanProperty(properties, prefix + RpcConstants.ServerSuffix.MUTUAL_AUTH, false));
        tlsConfig.setProtocols(properties.getProperty(prefix + RpcConstants.ServerSuffix.TLS_PROTOCOLS));
        tlsConfig.setCiphers(properties.getProperty(prefix + RpcConstants.ServerSuffix.TLS_CIPHERS));
        tlsConfig.setTrustCollectionCertFile(
                properties.getProperty(prefix + RpcConstants.ServerSuffix.TLS_TRUST_COLLECTION_CHAIN_PATH));
        tlsConfig.setCertChainFile(properties.getProperty(prefix + RpcConstants.ServerSuffix.TLS_CERT_CHAIN_PATH));
        tlsConfig.setCertPrivateKey(properties.getProperty(prefix + RpcConstants.ServerSuffix.TLS_CERT_KEY));
        tlsConfig.setTrustAll(getBooleanProperty(properties, prefix + RpcConstants.ServerSuffix.TLS_TRUST_ALL, true));
        tlsConfig.setCertPrivateKeyPassword(properties.getProperty(prefix + RpcConstants.ServerSuffix.TLS_TRUST_PWD));
        tlsConfig.setSslProvider(properties.getProperty(prefix + RpcConstants.ServerSuffix.TLS_PROVIDER));
        tlsConfig.setSslContextRefresher(
                properties.getProperty(prefix + RpcConstants.ServerSuffix.SSL_CONTEXT_REFRESHER));
        tlsConfig.setCompatibility(
                getBooleanProperty(properties, prefix + RpcConstants.ServerSuffix.COMPATIBILITY, true));
        return tlsConfig;
    }
}
