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

