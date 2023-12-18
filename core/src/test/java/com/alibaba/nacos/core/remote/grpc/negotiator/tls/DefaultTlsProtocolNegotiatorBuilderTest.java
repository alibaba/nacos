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

import com.alibaba.nacos.core.remote.tls.RpcServerTlsConfig;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.mock.env.MockEnvironment;

import java.lang.reflect.Field;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class DefaultTlsProtocolNegotiatorBuilderTest {
    
    private ConfigurableEnvironment environment;
    
    private DefaultTlsProtocolNegotiatorBuilder builder;
    
    @Before
    public void setUp() throws Exception {
        environment = new MockEnvironment();
        EnvUtil.setEnvironment(environment);
        builder = new DefaultTlsProtocolNegotiatorBuilder();
    }
    
    @After
    public void tearDown() throws Exception {
        RpcServerTlsConfig.getInstance().setEnableTls(false);
        RpcServerTlsConfig.getInstance().setCertChainFile(null);
        RpcServerTlsConfig.getInstance().setCertPrivateKey(null);
        clearRpcServerTlsConfigInstance();
    }
    
    @Test
    public void testBuildDisabled() {
        assertNull(builder.build());
    }
    
    @Test
    public void testBuildEnabled() {
        RpcServerTlsConfig.getInstance().setEnableTls(true);
        RpcServerTlsConfig.getInstance().setCertPrivateKey("test-server-key.pem");
        RpcServerTlsConfig.getInstance().setCertChainFile("test-server-cert.pem");
        assertNotNull(builder.build());
    }
    
    private static void clearRpcServerTlsConfigInstance() throws Exception {
        Field instanceField = RpcServerTlsConfig.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, null);
    }
}