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

import org.junit.jupiter.api.Test;

import java.util.Properties;

import static com.alibaba.nacos.common.remote.client.RpcConstants.NACOS_PEER_RPC;
import static com.alibaba.nacos.common.remote.client.RpcConstants.ServerSuffix.MUTUAL_AUTH;
import static com.alibaba.nacos.common.remote.client.RpcConstants.ServerSuffix.TLS_CERT_CHAIN_PATH;
import static com.alibaba.nacos.common.remote.client.RpcConstants.ServerSuffix.TLS_CERT_KEY;
import static com.alibaba.nacos.common.remote.client.RpcConstants.ServerSuffix.TLS_CIPHERS;
import static com.alibaba.nacos.common.remote.client.RpcConstants.ServerSuffix.TLS_ENABLE;
import static com.alibaba.nacos.common.remote.client.RpcConstants.ServerSuffix.TLS_PROTOCOLS;
import static com.alibaba.nacos.common.remote.client.RpcConstants.ServerSuffix.TLS_PROVIDER;
import static com.alibaba.nacos.common.remote.client.RpcConstants.ServerSuffix.TLS_TRUST_ALL;
import static com.alibaba.nacos.common.remote.client.RpcConstants.ServerSuffix.TLS_TRUST_COLLECTION_CHAIN_PATH;
import static com.alibaba.nacos.common.remote.client.RpcConstants.ServerSuffix.TLS_TRUST_PWD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RpcClusterClientTlsConfigTest {
    
    @Test
    void testEnableTls() {
        Properties properties = new Properties();
        properties.setProperty(NACOS_PEER_RPC + TLS_ENABLE, "true");
        RpcClientTlsConfig tlsConfig = RpcClientTlsConfigFactory.getInstance().createClusterConfig(properties);
        assertTrue(tlsConfig.getEnableTls());
    }
    
    @Test
    void testSslProvider() {
        Properties properties = new Properties();
        properties.setProperty(NACOS_PEER_RPC + TLS_ENABLE, "true");
        properties.setProperty(NACOS_PEER_RPC + TLS_PROVIDER, "provider");
        RpcClientTlsConfig tlsConfig = RpcClientTlsConfigFactory.getInstance().createClusterConfig(properties);
        assertEquals("provider", tlsConfig.getSslProvider());
    }
    
    @Test
    void testMutualAuthEnable() {
        Properties properties = new Properties();
        properties.setProperty(NACOS_PEER_RPC + TLS_ENABLE, "true");
        properties.setProperty(NACOS_PEER_RPC + MUTUAL_AUTH, "true");
        RpcClientTlsConfig tlsConfig = RpcClientTlsConfigFactory.getInstance().createClusterConfig(properties);
        assertTrue(tlsConfig.getMutualAuthEnable());
    }
    
    @Test
    void testProtocols() {
        Properties properties = new Properties();
        properties.setProperty(NACOS_PEER_RPC + TLS_ENABLE, "true");
        properties.setProperty(NACOS_PEER_RPC + TLS_PROTOCOLS, "protocols");
        RpcClientTlsConfig tlsConfig = RpcClientTlsConfigFactory.getInstance().createClusterConfig(properties);
        assertEquals("protocols", tlsConfig.getProtocols());
    }
    
    @Test
    void testCiphers() {
        Properties properties = new Properties();
        properties.setProperty(NACOS_PEER_RPC + TLS_ENABLE, "true");
        properties.setProperty(NACOS_PEER_RPC + TLS_CIPHERS, "ciphers");
        RpcClientTlsConfig tlsConfig = RpcClientTlsConfigFactory.getInstance().createClusterConfig(properties);
        assertEquals("ciphers", tlsConfig.getCiphers());
    }
    
    @Test
    void testTrustCollectionCertFile() {
        Properties properties = new Properties();
        properties.setProperty(NACOS_PEER_RPC + TLS_ENABLE, "true");
        properties.setProperty(NACOS_PEER_RPC + TLS_TRUST_COLLECTION_CHAIN_PATH, "trustCollectionCertFile");
        RpcClientTlsConfig tlsConfig = RpcClientTlsConfigFactory.getInstance().createClusterConfig(properties);
        assertEquals("trustCollectionCertFile", tlsConfig.getTrustCollectionCertFile());
    }
    
    @Test
    void testCertChainFile() {
        Properties properties = new Properties();
        properties.setProperty(NACOS_PEER_RPC + TLS_ENABLE, "true");
        properties.setProperty(NACOS_PEER_RPC + TLS_CERT_CHAIN_PATH, "certChainFile");
        RpcClientTlsConfig tlsConfig = RpcClientTlsConfigFactory.getInstance().createClusterConfig(properties);
        assertEquals("certChainFile", tlsConfig.getCertChainFile());
    }
    
    @Test
    void testCertPrivateKey() {
        Properties properties = new Properties();
        properties.setProperty(NACOS_PEER_RPC + TLS_ENABLE, "true");
        properties.setProperty(NACOS_PEER_RPC + TLS_CERT_KEY, "certPrivateKey");
        RpcClientTlsConfig tlsConfig = RpcClientTlsConfigFactory.getInstance().createClusterConfig(properties);
        assertEquals("certPrivateKey", tlsConfig.getCertPrivateKey());
    }
    
    @Test
    void testTrustAll() {
        Properties properties = new Properties();
        properties.setProperty(NACOS_PEER_RPC + TLS_ENABLE, "true");
        properties.setProperty(NACOS_PEER_RPC + TLS_TRUST_ALL, "true");
        RpcClientTlsConfig tlsConfig = RpcClientTlsConfigFactory.getInstance().createClusterConfig(properties);
        assertTrue(tlsConfig.getTrustAll());
    }
    
    @Test
    void testCertPrivateKeyPassword() {
        Properties properties = new Properties();
        properties.setProperty(NACOS_PEER_RPC + TLS_ENABLE, "true");
        properties.setProperty(NACOS_PEER_RPC + TLS_TRUST_PWD, "trustPwd");
        RpcClientTlsConfig tlsConfig = RpcClientTlsConfigFactory.getInstance().createClusterConfig(properties);
        assertEquals("trustPwd", tlsConfig.getCertPrivateKeyPassword());
    }
}

