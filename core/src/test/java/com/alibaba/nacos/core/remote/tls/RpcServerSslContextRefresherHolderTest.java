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

package com.alibaba.nacos.core.remote.tls;

import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;

import java.lang.reflect.Method;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * {@link RpcServerSslContextRefresherHolder} unit test.
 */
@ExtendWith(MockitoExtension.class)
public class RpcServerSslContextRefresherHolderTest {
    
    private MockEnvironment mockEnvironment;
    
    private MockedStatic<RpcServerTlsConfigFactory> rpcServerTlsConfigFactoryMockedStatic;
    
    @BeforeEach
    public void setUp() {
        mockEnvironment = new MockEnvironment();
        EnvUtil.setEnvironment(mockEnvironment);
        
        // Mock RpcServerTlsConfigFactory.getInstance() to return mock instances
        rpcServerTlsConfigFactoryMockedStatic = Mockito.mockStatic(RpcServerTlsConfigFactory.class);
        
        // Mock config
        RpcServerTlsConfig sdkServerTlsConfig = Mockito.mock(RpcServerTlsConfig.class);
        RpcServerTlsConfig clusterServerTlsConfig = Mockito.mock(RpcServerTlsConfig.class);
        
        // Mock the static method RpcServerTlsConfigFactory.getInstance()
        rpcServerTlsConfigFactoryMockedStatic.when(RpcServerTlsConfigFactory::getInstance)
                .thenReturn(Mockito.mock(RpcServerTlsConfigFactory.class));
        
        // Mock createSdkConfig method to return the mock sdkServerTlsConfig
        when(RpcServerTlsConfigFactory.getInstance().createSdkConfig(any(Properties.class))).thenReturn(
                sdkServerTlsConfig);
        
        // Mock createClusterConfig method to return the mock clusterServerTlsConfig
        when(RpcServerTlsConfigFactory.getInstance().createClusterConfig(any(Properties.class))).thenReturn(
                clusterServerTlsConfig);
        
        // Mock getSslContextRefresher to return specific names
        when(sdkServerTlsConfig.getSslContextRefresher()).thenReturn(RpcSdkServerSslContextRefresherTest.NAME);
        when(clusterServerTlsConfig.getSslContextRefresher()).thenReturn(RpcClusterServerSslContextRefresherTest.NAME);
    }
    
    /**
     * afterEach.
     */
    @AfterEach
    public void tearDown() {
        // Clear the mocked static instance
        if (rpcServerTlsConfigFactoryMockedStatic != null) {
            rpcServerTlsConfigFactoryMockedStatic.close();
        }
        // Reset the environment
        EnvUtil.setEnvironment(null);
    }
    
    @Test
    public void testInitAndGet() throws Exception {
        // Call init method.
        callInit();
        RpcServerSslContextRefresher sdkInstance = RpcServerSslContextRefresherHolder.getSdkInstance();
        RpcServerSslContextRefresher clusterInstance = RpcServerSslContextRefresherHolder.getClusterInstance();
        
        assertEquals(RpcSdkServerSslContextRefresherTest.NAME, sdkInstance.getName());
        assertEquals(RpcClusterServerSslContextRefresherTest.NAME, clusterInstance.getName());
    }
    
    private void callInit() throws Exception {
        Class<?> clazz = RpcServerSslContextRefresherHolder.class;
        Method method = clazz.getDeclaredMethod("init");
        method.setAccessible(true);
        method.invoke(null);
    }
}
