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

package com.alibaba.nacos.core.remote.grpc.negotiator.tls;

import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.core.remote.tls.RpcSdkServerTlsConfig;
import com.alibaba.nacos.core.remote.tls.RpcServerTlsConfig;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.mock.env.MockEnvironment;

import java.lang.reflect.Field;

public class SdkDefaultTlsContextBuilderTest {
    
    private ConfigurableEnvironment environment;
    
    @Before
    public void setUp() throws Exception {
        environment = new MockEnvironment();
        EnvUtil.setEnvironment(environment);
        RpcSdkServerTlsConfig.getInstance().setEnableTls(true);
    }
    
    @After
    public void tearDown() throws Exception {
        RpcSdkServerTlsConfig.getInstance().setEnableTls(false);
        RpcSdkServerTlsConfig.getInstance().setTrustAll(false);
        RpcSdkServerTlsConfig.getInstance().setMutualAuthEnable(false);
        RpcSdkServerTlsConfig.getInstance().setCertChainFile(null);
        RpcSdkServerTlsConfig.getInstance().setCertPrivateKey(null);
        RpcSdkServerTlsConfig.getInstance().setCiphers(null);
        RpcSdkServerTlsConfig.getInstance().setProtocols(null);
        RpcSdkServerTlsConfig.getInstance().setTrustCollectionCertFile(null);
        RpcSdkServerTlsConfig.getInstance().setSslProvider("");
        clearRpcServerTlsConfigInstance();
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testGetSslContextIllegal() {
        DefaultTlsContextBuilder.getSslContext(RpcSdkServerTlsConfig.getInstance());
    }
    
    @Test
    public void testGetSslContextWithoutMutual() {
        RpcServerTlsConfig grpcServerConfig = RpcSdkServerTlsConfig.getInstance();
        grpcServerConfig.setCiphers("ECDHE-RSA-AES128-GCM-SHA256,ECDHE-RSA-AES256-GCM-SHA384");
        grpcServerConfig.setProtocols("TLSv1.2,TLSv1.3");
        grpcServerConfig.setCertPrivateKey("test-server-key.pem");
        grpcServerConfig.setCertChainFile("test-server-cert.pem");
        DefaultTlsContextBuilder.getSslContext(RpcSdkServerTlsConfig.getInstance());
    }
    
    @Test
    public void testGetSslContextWithMutual() {
        RpcServerTlsConfig grpcServerConfig = RpcSdkServerTlsConfig.getInstance();
        grpcServerConfig.setTrustAll(true);
        grpcServerConfig.setMutualAuthEnable(true);
        grpcServerConfig.setCiphers("ECDHE-RSA-AES128-GCM-SHA256,ECDHE-RSA-AES256-GCM-SHA384");
        grpcServerConfig.setProtocols("TLSv1.2,TLSv1.3");
        grpcServerConfig.setCertPrivateKey("test-server-key.pem");
        grpcServerConfig.setCertChainFile("test-server-cert.pem");
        DefaultTlsContextBuilder.getSslContext(RpcSdkServerTlsConfig.getInstance());
    }
    
    @Test
    public void testGetSslContextWithMutualAndPart() {
        RpcServerTlsConfig grpcServerConfig = RpcSdkServerTlsConfig.getInstance();
        grpcServerConfig.setMutualAuthEnable(true);
        grpcServerConfig.setCiphers("ECDHE-RSA-AES128-GCM-SHA256,ECDHE-RSA-AES256-GCM-SHA384");
        grpcServerConfig.setProtocols("TLSv1.2,TLSv1.3");
        grpcServerConfig.setCertPrivateKey("test-server-key.pem");
        grpcServerConfig.setCertChainFile("test-server-cert.pem");
        grpcServerConfig.setTrustCollectionCertFile("test-ca-cert.pem");
        DefaultTlsContextBuilder.getSslContext(RpcSdkServerTlsConfig.getInstance());
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testGetSslContextWithMutualAndPartIllegal() {
        RpcServerTlsConfig grpcServerConfig = RpcSdkServerTlsConfig.getInstance();
        grpcServerConfig.setMutualAuthEnable(true);
        grpcServerConfig.setCiphers("ECDHE-RSA-AES128-GCM-SHA256,ECDHE-RSA-AES256-GCM-SHA384");
        grpcServerConfig.setProtocols("TLSv1.2,TLSv1.3");
        grpcServerConfig.setCertPrivateKey("test-server-key.pem");
        grpcServerConfig.setCertChainFile("test-server-cert.pem");
        DefaultTlsContextBuilder.getSslContext(RpcSdkServerTlsConfig.getInstance());
    }
    
    @Test(expected = NacosRuntimeException.class)
    public void testGetSslContextForNonExistFile() {
        RpcServerTlsConfig grpcServerConfig = RpcSdkServerTlsConfig.getInstance();
        grpcServerConfig.setCiphers("ECDHE-RSA-AES128-GCM-SHA256,ECDHE-RSA-AES256-GCM-SHA384");
        grpcServerConfig.setProtocols("TLSv1.2,TLSv1.3");
        grpcServerConfig.setCertPrivateKey("non-exist-server-key.pem");
        grpcServerConfig.setCertChainFile("non-exist-cert.pem");
        DefaultTlsContextBuilder.getSslContext(RpcSdkServerTlsConfig.getInstance());
    }
    
    private static void clearRpcServerTlsConfigInstance() throws Exception {
        Field instanceField = RpcSdkServerTlsConfig.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, null);
    }
}
