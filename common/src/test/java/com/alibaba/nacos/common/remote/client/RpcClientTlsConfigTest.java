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

import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RpcClientTlsConfigTest {
    
    @Test
    public void testEnableTls() {
        Properties properties = new Properties();
        properties.setProperty(RpcConstants.RPC_CLIENT_TLS_ENABLE, "true");
        RpcClientTlsConfig tlsConfig = RpcClientTlsConfig.properties(properties);
        assertTrue(tlsConfig.getEnableTls());
    }
    
    @Test
    public void testSslProvider() {
        Properties properties = new Properties();
        properties.setProperty(RpcConstants.RPC_CLIENT_TLS_PROVIDER, "provider");
        RpcClientTlsConfig tlsConfig = RpcClientTlsConfig.properties(properties);
        assertEquals("provider", tlsConfig.getSslProvider());
    }
    
    @Test
    public void testMutualAuthEnable() {
        Properties properties = new Properties();
        properties.setProperty(RpcConstants.RPC_CLIENT_MUTUAL_AUTH, "true");
        RpcClientTlsConfig tlsConfig = RpcClientTlsConfig.properties(properties);
        assertTrue(tlsConfig.getMutualAuthEnable());
    }
    
    @Test
    public void testProtocols() {
        Properties properties = new Properties();
        properties.setProperty(RpcConstants.RPC_CLIENT_TLS_PROTOCOLS, "protocols");
        RpcClientTlsConfig tlsConfig = RpcClientTlsConfig.properties(properties);
        assertEquals("protocols", tlsConfig.getProtocols());
    }
    
    @Test
    public void testCiphers() {
        Properties properties = new Properties();
        properties.setProperty(RpcConstants.RPC_CLIENT_TLS_CIPHERS, "ciphers");
        RpcClientTlsConfig tlsConfig = RpcClientTlsConfig.properties(properties);
        assertEquals("ciphers", tlsConfig.getCiphers());
    }
    
    @Test
    public void testTrustCollectionCertFile() {
        Properties properties = new Properties();
        properties.setProperty(RpcConstants.RPC_CLIENT_TLS_TRUST_COLLECTION_CHAIN_PATH, "trustCollectionCertFile");
        RpcClientTlsConfig tlsConfig = RpcClientTlsConfig.properties(properties);
        assertEquals("trustCollectionCertFile", tlsConfig.getTrustCollectionCertFile());
    }
    
    @Test
    public void testCertChainFile() {
        Properties properties = new Properties();
        properties.setProperty(RpcConstants.RPC_CLIENT_TLS_CERT_CHAIN_PATH, "certChainFile");
        RpcClientTlsConfig tlsConfig = RpcClientTlsConfig.properties(properties);
        assertEquals("certChainFile", tlsConfig.getCertChainFile());
    }
    
    @Test
    public void testCertPrivateKey() {
        Properties properties = new Properties();
        properties.setProperty(RpcConstants.RPC_CLIENT_TLS_CERT_KEY, "certPrivateKey");
        RpcClientTlsConfig tlsConfig = RpcClientTlsConfig.properties(properties);
        assertEquals("certPrivateKey", tlsConfig.getCertPrivateKey());
    }
    
    @Test
    public void testTrustAll() {
        Properties properties = new Properties();
        properties.setProperty(RpcConstants.RPC_CLIENT_TLS_TRUST_ALL, "true");
        RpcClientTlsConfig tlsConfig = RpcClientTlsConfig.properties(properties);
        assertTrue(tlsConfig.getTrustAll());
    }
    
    @Test
    public void testCertPrivateKeyPassword() {
        Properties properties = new Properties();
        properties.setProperty(RpcConstants.RPC_CLIENT_TLS_TRUST_PWD, "trustPwd");
        RpcClientTlsConfig tlsConfig = RpcClientTlsConfig.properties(properties);
        assertEquals("trustPwd", tlsConfig.getCertPrivateKeyPassword());
    }
}