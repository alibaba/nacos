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

package com.alibaba.nacos.common.remote.client;

import com.alibaba.nacos.common.remote.TlsConfig;
import com.alibaba.nacos.common.utils.StringUtils;

/**
 * RPC Cluster Client TLS Configuration for Nacos.
 * <p>
 * This class extends the {@link TlsConfig} class and provides a convenient way to create a configuration instance
 * specifically for the RPC (Remote Procedure Call) cluster client in Nacos.
 * </p>
 * <p>
 * To configure RPC cluster client TLS settings, you can use the following system properties:
 * </p>
 * <ul>
 *     <li>{@code nacos.remote.cluster.client.rpc.tls.enable}: Enable or disable TLS. Default is {@code false}.</li>
 *     <li>{@code nacos.remote.cluster.client.rpc.tls.Provider}: Specify the SSL provider.</li>
 *     <li>{@code nacos.remote.cluster.client.rpc.tls.mutualAuth}: Enable or disable mutual authentication. Default is {@code false}.</li>
 *     <li>{@code nacos.remote.cluster.client.rpc.tls.protocols}: Specify the TLS protocols.</li>
 *     <li>{@code nacos.remote.cluster.client.rpc.tls.ciphers}: Specify the TLS ciphers.</li>
 *     <li>{@code nacos.remote.cluster.client.rpc.tls.certChainFile}: Specify the path to the certificate chain file.</li>
 *     <li>{@code nacos.remote.cluster.client.rpc.tls.certPrivateKey}: Specify the path to the certificate private key file.</li>
 *     <li>{@code nacos.remote.cluster.client.rpc.tls.certPrivateKeyPassword}: Specify the password for the certificate private key.</li>
 *     <li>{@code nacos.remote.cluster.client.rpc.tls.trustCollectionCertFile}: Specify the path to the trust collection chain file.</li>
 *     <li>{@code nacos.remote.cluster.client.rpc.tls.trustAll}: Enable or disable trusting all certificates. Default is {@code false}.</li>
 * </ul>
 *
 * @author stone-98
 * @date 2023/12/20
 */
public class RpcClusterClientTlsConfig extends TlsConfig {

    /**
     * Creates a new instance of {@link RpcClusterClientTlsConfig} by loading TLS configuration from system properties.
     *
     * @return A new instance of {@link RpcClusterClientTlsConfig} with loaded TLS configuration.
     */
    public static RpcClusterClientTlsConfig createConfig() {
        RpcClusterClientTlsConfig tlsConfig = new RpcClusterClientTlsConfig();
        if (!Boolean.getBoolean(RpcConstants.RPC_CLUSTER_CLIENT_TLS_ENABLE)) {
            return tlsConfig;
        }
        tlsConfig.setEnableTls(Boolean.getBoolean(RpcConstants.RPC_CLUSTER_CLIENT_TLS_ENABLE));

        String sslProvider = System.getProperty(RpcConstants.RPC_CLUSTER_CLIENT_TLS_PROVIDER);
        if (StringUtils.isNotBlank(sslProvider)) {
            tlsConfig.setSslProvider(sslProvider);
        }

        String provider = System.getProperty(RpcConstants.RPC_CLUSTER_CLIENT_TLS_PROVIDER);
        if (StringUtils.isNotBlank(provider)) {
            tlsConfig.setProtocols(provider);
        }

        boolean mutualAuth = Boolean.getBoolean(RpcConstants.RPC_CLUSTER_CLIENT_MUTUAL_AUTH);
        tlsConfig.setMutualAuthEnable(mutualAuth);

        String protocols = System.getProperty(RpcConstants.RPC_CLUSTER_CLIENT_TLS_PROTOCOLS);
        if (StringUtils.isNotBlank(protocols)) {
            tlsConfig.setProtocols(protocols);
        }

        String ciphers = System.getProperty(RpcConstants.RPC_CLUSTER_CLIENT_TLS_CIPHERS);
        if (StringUtils.isNotBlank(ciphers)) {
            tlsConfig.setCiphers(ciphers);
        }

        String trustCollectionCertFile = System.getProperty(
                RpcConstants.RPC_CLUSTER_CLIENT_TLS_TRUST_COLLECTION_CHAIN_PATH);
        if (StringUtils.isNotBlank(trustCollectionCertFile)) {
            tlsConfig.setTrustCollectionCertFile(trustCollectionCertFile);
        }

        String certChain = System.getProperty(RpcConstants.RPC_CLUSTER_CLIENT_TLS_CERT_CHAIN_PATH);
        if (StringUtils.isNotBlank(ciphers)) {
            tlsConfig.setCertChainFile(certChain);
        }

        String certPrivateKey = System.getProperty(RpcConstants.RPC_CLUSTER_CLIENT_TLS_CERT_KEY);
        if (StringUtils.isNotBlank(certPrivateKey)) {
            tlsConfig.setCertPrivateKey(certPrivateKey);
        }

        boolean trustAll = Boolean.getBoolean(RpcConstants.RPC_CLUSTER_CLIENT_TLS_TRUST_ALL);
        tlsConfig.setTrustAll(trustAll);

        String certPrivateKeyPassword = System.getProperty(RpcConstants.RPC_CLUSTER_CLIENT_TLS_TRUST_PWD);
        if (StringUtils.isNotBlank(certPrivateKeyPassword)) {
            tlsConfig.setCertPrivateKeyPassword(certPrivateKeyPassword);
        }

        return tlsConfig;
    }
}
