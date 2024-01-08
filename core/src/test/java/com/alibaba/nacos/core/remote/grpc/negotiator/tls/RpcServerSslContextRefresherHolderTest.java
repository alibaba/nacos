/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

import com.alibaba.nacos.core.remote.tls.RpcClusterServerTlsConfig;
import com.alibaba.nacos.core.remote.tls.RpcSdkServerTlsConfig;
import com.alibaba.nacos.core.remote.tls.RpcServerSslContextRefresherHolder;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.core.env.ConfigurableEnvironment;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.when;

/**
 * Test RpcServerSslContextRefresherHolder.
 *
 * @author stone-98
 */
@RunWith(MockitoJUnitRunner.class)
public class RpcServerSslContextRefresherHolderTest {
    
    @Mock
    private ConfigurableEnvironment environment;
    
    @Mock
    private RpcSdkServerTlsConfig sdkRpcConfig;
    
    @Mock
    private RpcClusterServerTlsConfig clusterConfig;
    
    @Before
    public void setUp() {
        EnvUtil.setEnvironment(environment);
    }
    
    @After
    public void tearDown() {
    }
    
    @Test
    public void testInit() {
        when(sdkRpcConfig.getSslContextRefresher()).thenReturn("sdk-refresher-test");
        when(clusterConfig.getSslContextRefresher()).thenReturn("cluster-refresher-test");
        setStaticField(RpcSdkServerTlsConfig.class, sdkRpcConfig, "instance");
        setStaticField(RpcClusterServerTlsConfig.class, clusterConfig, "instance");
        invokeStaticMethod("init");
        assertNotNull(RpcServerSslContextRefresherHolder.getClusterInstance());
        assertNotNull(RpcServerSslContextRefresherHolder.getSdkInstance());
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
    
    private void invokeStaticMethod(String methodName) {
        try {
            Class<?> clazz = RpcServerSslContextRefresherHolder.class;
            Method privateStaticMethod = clazz.getDeclaredMethod(methodName);
            privateStaticMethod.setAccessible(true);
            privateStaticMethod.invoke(null);
            privateStaticMethod.setAccessible(false);
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            e.printStackTrace();
        }
    }
}
