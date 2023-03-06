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
import com.alibaba.nacos.core.remote.BaseRpcServer;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.mock.env.MockEnvironment;

/**
 * {@link GrpcSdkServer} and {@link GrpcClusterServer} unit test.
 *
 * @author chenglu
 * @date 2021-06-30 14:32
 */
@RunWith(MockitoJUnitRunner.class)
public class GrpcServerTest {
    
    @Before
    public void setUp() {
        EnvUtil.setEnvironment(new MockEnvironment());
    }
    
    @Test
    public void testGrpcSdkServer() throws Exception {
        BaseGrpcServer grpcSdkServer = new GrpcSdkServer();
        grpcSdkServer.start();
        
        Assert.assertEquals(grpcSdkServer.getConnectionType(), ConnectionType.GRPC);
        
        Assert.assertEquals(grpcSdkServer.rpcPortOffset(), 1000);
        
        grpcSdkServer.stopServer();
    }
    
    @Test
    public void testGrpcClusterServer() throws Exception {
        BaseRpcServer grpcSdkServer = new GrpcClusterServer();
        grpcSdkServer.start();
        
        Assert.assertEquals(grpcSdkServer.getConnectionType(), ConnectionType.GRPC);
    
        Assert.assertEquals(grpcSdkServer.rpcPortOffset(), 1001);
    
        grpcSdkServer.stopServer();
    }
}
