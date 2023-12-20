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

import com.alibaba.nacos.core.remote.tls.RpcSdkServerTlsConfig;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.mock.env.MockEnvironment;

import java.lang.reflect.Field;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class SdkDefaultTlsProtocolNegotiatorBuilderTest {
    
    private ConfigurableEnvironment environment;
    
    private SdkDefaultTlsProtocolNegotiatorBuilder builder;
    
    @Before
    public void setUp() throws Exception {
        environment = new MockEnvironment();
        EnvUtil.setEnvironment(environment);
        builder = new SdkDefaultTlsProtocolNegotiatorBuilder();
        setStaticField(RpcSdkServerTlsConfig.class, null, "instance");
    }
    
    @After
    public void tearDown() throws Exception {
        RpcSdkServerTlsConfig.getInstance().setEnableTls(false);
        RpcSdkServerTlsConfig.getInstance().setCertChainFile(null);
        RpcSdkServerTlsConfig.getInstance().setCertPrivateKey(null);
        setStaticField(RpcSdkServerTlsConfig.class, null, "instance");
    }
    
    @Test
    public void testBuildDisabled() {
        assertNull(builder.build());
    }
    
    @Test
    public void testBuildEnabled() {
        RpcSdkServerTlsConfig.getInstance().setEnableTls(true);
        RpcSdkServerTlsConfig.getInstance().setCertPrivateKey("test-server-key.pem");
        RpcSdkServerTlsConfig.getInstance().setCertChainFile("test-server-cert.pem");
        assertNotNull(builder.build());
    }
    
    private void setStaticField(Class<?> target, Object obj, String fieldName) {
        try {
            Field instanceField = target.getDeclaredField(fieldName);
            instanceField.setAccessible(true);
            instanceField.set(null, obj);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }
    }
}
