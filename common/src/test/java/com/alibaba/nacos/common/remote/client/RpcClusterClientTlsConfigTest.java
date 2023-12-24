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

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RpcClusterClientTlsConfigTest {

    @Test
    public void testEnableTls() {
        System.setProperty(RpcConstants.RPC_CLUSTER_CLIENT_TLS_ENABLE, "true");
        RpcClusterClientTlsConfig tlsConfig = RpcClusterClientTlsConfig.createConfig();
        assertTrue(tlsConfig.getEnableTls());
    }

    @Test
    public void testSslProvider() {
        System.setProperty(RpcConstants.RPC_CLUSTER_CLIENT_TLS_ENABLE, "true");
        System.setProperty(RpcConstants.RPC_CLUSTER_CLIENT_TLS_PROVIDER, "provider");
        RpcClusterClientTlsConfig tlsConfig = RpcClusterClientTlsConfig.createConfig();
        assertEquals("provider", tlsConfig.getSslProvider());
    }

    @Test
    public void testMutualAuthEnable() {
        System.setProperty(RpcConstants.RPC_CLUSTER_CLIENT_TLS_ENABLE, "true");
        System.setProperty(RpcConstants.RPC_CLUSTER_CLIENT_MUTUAL_AUTH, "true");
        RpcClusterClientTlsConfig tlsConfig = RpcClusterClientTlsConfig.createConfig();
        assertTrue(tlsConfig.getMutualAuthEnable());
    }

    @Test
    public void testProtocols() {
        System.setProperty(RpcConstants.RPC_CLUSTER_CLIENT_TLS_ENABLE, "true");
        System.setProperty(RpcConstants.RPC_CLUSTER_CLIENT_TLS_PROTOCOLS, "protocols");
        RpcClusterClientTlsConfig tlsConfig = RpcClusterClientTlsConfig.createConfig();
        assertEquals("protocols", tlsConfig.getProtocols());
    }

    @Test
    public void testCiphers() {
        System.setProperty(RpcConstants.RPC_CLUSTER_CLIENT_TLS_ENABLE, "true");
        System.setProperty(RpcConstants.RPC_CLUSTER_CLIENT_TLS_CIPHERS, "ciphers");
        RpcClusterClientTlsConfig tlsConfig = RpcClusterClientTlsConfig.createConfig();
        assertEquals("ciphers", tlsConfig.getCiphers());
    }

    @Test
    public void testTrustCollectionCertFile() {
        System.setProperty(RpcConstants.RPC_CLUSTER_CLIENT_TLS_ENABLE, "true");
        System.setProperty(RpcConstants.RPC_CLUSTER_CLIENT_TLS_TRUST_COLLECTION_CHAIN_PATH, "trustCollectionCertFile");
        RpcClusterClientTlsConfig tlsConfig = RpcClusterClientTlsConfig.createConfig();
        assertEquals("trustCollectionCertFile", tlsConfig.getTrustCollectionCertFile());
    }

    @Test
    public void testCertChainFile() {
        System.setProperty(RpcConstants.RPC_CLUSTER_CLIENT_TLS_ENABLE, "true");
        System.setProperty(RpcConstants.RPC_CLUSTER_CLIENT_TLS_CERT_CHAIN_PATH, "certChainFile");
        RpcClusterClientTlsConfig tlsConfig = RpcClusterClientTlsConfig.createConfig();
        assertEquals("certChainFile", tlsConfig.getCertChainFile());
    }

    @Test
    public void testCertPrivateKey() {
        System.setProperty(RpcConstants.RPC_CLUSTER_CLIENT_TLS_ENABLE, "true");
        System.setProperty(RpcConstants.RPC_CLUSTER_CLIENT_TLS_CERT_KEY, "certPrivateKey");
        RpcClusterClientTlsConfig tlsConfig = RpcClusterClientTlsConfig.createConfig();
        assertEquals("certPrivateKey", tlsConfig.getCertPrivateKey());
    }

    @Test
    public void testTrustAll() {
        System.setProperty(RpcConstants.RPC_CLUSTER_CLIENT_TLS_ENABLE, "true");
        System.setProperty(RpcConstants.RPC_CLUSTER_CLIENT_TLS_TRUST_ALL, "true");
        RpcClusterClientTlsConfig tlsConfig = RpcClusterClientTlsConfig.createConfig();
        assertTrue(tlsConfig.getTrustAll());
    }

    @Test
    public void testCertPrivateKeyPassword() {
        System.setProperty(RpcConstants.RPC_CLUSTER_CLIENT_TLS_ENABLE, "true");
        System.setProperty(RpcConstants.RPC_CLUSTER_CLIENT_TLS_TRUST_PWD, "trustPwd");
        RpcClusterClientTlsConfig tlsConfig = RpcClusterClientTlsConfig.createConfig();
        assertEquals("trustPwd", tlsConfig.getCertPrivateKeyPassword());
    }
}

