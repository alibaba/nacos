/*
 *  Copyright 1999-2021 Alibaba Group Holding Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package com.alibaba.nacos.core.remote.grpc;

import com.alibaba.nacos.common.remote.ConnectionType;
import com.alibaba.nacos.core.remote.RpcServerTlsConfig;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.mock.env.MockEnvironment;

import java.util.concurrent.ThreadPoolExecutor;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link GrpcSdkServer} and {@link GrpcClusterServer} unit test.
 *
 * @author chenglu
 * @date 2021-06-30 14:32
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class GrpcServerTest {

    private final RpcServerTlsConfig grpcServerConfig = mock(RpcServerTlsConfig.class);

    @Before
    public void setUp() {
        EnvUtil.setEnvironment(new MockEnvironment());
    }

    @Test
    public void testGrpcSdkServer() throws Exception {
        BaseGrpcServer grpcSdkServer = new GrpcSdkServer();
        grpcSdkServer.setGrpcServerConfig(grpcServerConfig);
        when(grpcServerConfig.getEnableTls()).thenReturn(false);
        grpcSdkServer.start();
        Assert.assertEquals(grpcSdkServer.getConnectionType(), ConnectionType.GRPC);
        Assert.assertEquals(grpcSdkServer.rpcPortOffset(), 1000);
        grpcSdkServer.stopServer();
    }

    @Test
    public void testGrpcClusterServer() throws Exception {
        BaseGrpcServer grpcSdkServer = new GrpcClusterServer();
        grpcSdkServer.setGrpcServerConfig(grpcServerConfig);
        when(grpcServerConfig.getEnableTls()).thenReturn(false);
        grpcSdkServer.start();
        Assert.assertEquals(grpcSdkServer.getConnectionType(), ConnectionType.GRPC);
        Assert.assertEquals(grpcSdkServer.rpcPortOffset(), 1001);
        grpcSdkServer.stopServer();
    }

    @Test
    public void testGrpcEnableTls() throws Exception {
        final BaseGrpcServer grpcSdkServer = new BaseGrpcServer() {
            @Override
            public ThreadPoolExecutor getRpcExecutor() {
                return null;
            }

            @Override
            public int rpcPortOffset() {
                return 100;
            }
        };
        when(grpcServerConfig.getEnableTls()).thenReturn(true);
        when(grpcServerConfig.getCiphers()).thenReturn("ECDHE-RSA-AES128-GCM-SHA256,ECDHE-RSA-AES256-GCM-SHA384");
        when(grpcServerConfig.getProtocols()).thenReturn("TLSv1.2,TLSv1.3");

        when(grpcServerConfig.getCertPrivateKey()).thenReturn("test-server-key.pem");
        when(grpcServerConfig.getCertChainFile()).thenReturn("test-server-cert.pem");
        grpcSdkServer.setGrpcServerConfig(grpcServerConfig);
        grpcSdkServer.start();
        grpcSdkServer.shutdownServer();
    }

    @Test
    public void testGrpcEnableMutualAuthAndTrustAll() throws Exception {

        final BaseGrpcServer grpcSdkServer = new BaseGrpcServer() {
            @Override
            public ThreadPoolExecutor getRpcExecutor() {
                return null;
            }

            @Override
            public int rpcPortOffset() {
                return 100;
            }
        };

        when(grpcServerConfig.getEnableTls()).thenReturn(true);
        when(grpcServerConfig.getTrustAll()).thenReturn(true);
        when(grpcServerConfig.getCiphers()).thenReturn("ECDHE-RSA-AES128-GCM-SHA256,ECDHE-RSA-AES256-GCM-SHA384");
        when(grpcServerConfig.getProtocols()).thenReturn("TLSv1.2,TLSv1.3");
        when(grpcServerConfig.getCertPrivateKey()).thenReturn("test-server-key.pem");
        when(grpcServerConfig.getCertChainFile()).thenReturn("test-server-cert.pem");
        grpcSdkServer.setGrpcServerConfig(grpcServerConfig);
        grpcSdkServer.start();
        grpcSdkServer.shutdownServer();
    }

    @Test
    public void testGrpcEnableMutualAuthAndPart() throws Exception {
        final BaseGrpcServer grpcSdkServer = new BaseGrpcServer() {
            @Override
            public ThreadPoolExecutor getRpcExecutor() {
                return null;
            }

            @Override
            public int rpcPortOffset() {
                return 100;
            }
        };
        when(grpcServerConfig.getEnableTls()).thenReturn(true);
        when(grpcServerConfig.getMutualAuthEnable()).thenReturn(true);
        when(grpcServerConfig.getEnableTls()).thenReturn(true);
        when(grpcServerConfig.getCiphers()).thenReturn("ECDHE-RSA-AES128-GCM-SHA256,ECDHE-RSA-AES256-GCM-SHA384");
        when(grpcServerConfig.getProtocols()).thenReturn("TLSv1.2,TLSv1.3");

        when(grpcServerConfig.getCertPrivateKey()).thenReturn("test-server-key.pem");
        when(grpcServerConfig.getCertChainFile()).thenReturn("test-server-cert.pem");
        when(grpcServerConfig.getTrustCollectionCertFile()).thenReturn("test-ca-cert.pem");

        grpcSdkServer.setGrpcServerConfig(grpcServerConfig);

        grpcSdkServer.start();
        grpcSdkServer.shutdownServer();
    }
}
