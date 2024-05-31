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
import com.alibaba.nacos.core.remote.tls.RpcServerTlsConfig;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * {@link DefaultTlsContextBuilder} unit test.
 *
 * @author stone-98
 * @date 2024-03-11 17:11
 */
@ExtendWith(MockitoExtension.class)
class SdkDefaultTlsContextBuilderTest {
    
    private ConfigurableEnvironment environment;
    
    @Mock
    private RpcServerTlsConfig rpcServerTlsConfig;
    
    @BeforeEach
    void setUp() throws Exception {
        environment = new MockEnvironment();
        EnvUtil.setEnvironment(environment);
    }
    
    @AfterEach
    void tearDown() throws Exception {
    }
    
    @Test
    void testGetSslContextIllegal() {
        assertThrows(IllegalArgumentException.class, () -> {
            DefaultTlsContextBuilder.getSslContext(rpcServerTlsConfig);
        });
    }
    
    @Test
    void testGetSslContextWithoutMutual() {
        when(rpcServerTlsConfig.getCiphers()).thenReturn("ECDHE-RSA-AES128-GCM-SHA256,ECDHE-RSA-AES256-GCM-SHA384");
        when(rpcServerTlsConfig.getProtocols()).thenReturn("TLSv1.2,TLSv1.3");
        when(rpcServerTlsConfig.getCertPrivateKey()).thenReturn("test-server-key.pem");
        when(rpcServerTlsConfig.getCertChainFile()).thenReturn("test-server-cert.pem");
        DefaultTlsContextBuilder.getSslContext(rpcServerTlsConfig);
    }
    
    @Test
    void testGetSslContextWithMutual() {
        when(rpcServerTlsConfig.getTrustAll()).thenReturn(true);
        when(rpcServerTlsConfig.getMutualAuthEnable()).thenReturn(true);
        when(rpcServerTlsConfig.getCiphers()).thenReturn("ECDHE-RSA-AES128-GCM-SHA256,ECDHE-RSA-AES256-GCM-SHA384");
        when(rpcServerTlsConfig.getProtocols()).thenReturn("TLSv1.2,TLSv1.3");
        when(rpcServerTlsConfig.getCertPrivateKey()).thenReturn("test-server-key.pem");
        when(rpcServerTlsConfig.getCertChainFile()).thenReturn("test-server-cert.pem");
        DefaultTlsContextBuilder.getSslContext(rpcServerTlsConfig);
    }
    
    @Test
    void testGetSslContextWithMutualAndPart() {
        when(rpcServerTlsConfig.getMutualAuthEnable()).thenReturn(true);
        when(rpcServerTlsConfig.getCiphers()).thenReturn("ECDHE-RSA-AES128-GCM-SHA256,ECDHE-RSA-AES256-GCM-SHA384");
        when(rpcServerTlsConfig.getProtocols()).thenReturn("TLSv1.2,TLSv1.3");
        when(rpcServerTlsConfig.getCertPrivateKey()).thenReturn("test-server-key.pem");
        when(rpcServerTlsConfig.getCertChainFile()).thenReturn("test-server-cert.pem");
        when(rpcServerTlsConfig.getTrustCollectionCertFile()).thenReturn("test-ca-cert.pem");
        DefaultTlsContextBuilder.getSslContext(rpcServerTlsConfig);
    }
    
    @Test
    void testGetSslContextWithMutualAndPartIllegal() {
        assertThrows(IllegalArgumentException.class, () -> {
            when(rpcServerTlsConfig.getMutualAuthEnable()).thenReturn(true);
            when(rpcServerTlsConfig.getCiphers()).thenReturn("ECDHE-RSA-AES128-GCM-SHA256,ECDHE-RSA-AES256-GCM-SHA384");
            when(rpcServerTlsConfig.getProtocols()).thenReturn("TLSv1.2,TLSv1.3");
            when(rpcServerTlsConfig.getCertPrivateKey()).thenReturn("test-server-key.pem");
            when(rpcServerTlsConfig.getCertChainFile()).thenReturn("test-server-cert.pem");
            DefaultTlsContextBuilder.getSslContext(rpcServerTlsConfig);
        });
    }
    
    @Test
    void testGetSslContextForNonExistFile() {
        assertThrows(NacosRuntimeException.class, () -> {
            when(rpcServerTlsConfig.getCertPrivateKey()).thenReturn("non-exist-server-key.pem");
            when(rpcServerTlsConfig.getCertChainFile()).thenReturn("non-exist-cert.pem");
            DefaultTlsContextBuilder.getSslContext(rpcServerTlsConfig);
        });
    }
    
}
