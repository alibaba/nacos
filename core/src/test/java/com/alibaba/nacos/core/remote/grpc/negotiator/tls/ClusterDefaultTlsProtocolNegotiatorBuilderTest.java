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

import com.alibaba.nacos.core.remote.grpc.negotiator.NacosGrpcProtocolNegotiator;
import com.alibaba.nacos.core.remote.tls.RpcClusterServerTlsConfig;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.mock.env.MockEnvironment;

import java.lang.reflect.Field;
import java.util.Properties;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Test ClusterDefaultTlsProtocolNegotiatorBuilder.
 *
 * @author stone-98
 * @date 2023/12/25
 */
public class ClusterDefaultTlsProtocolNegotiatorBuilderTest {
    
    private ConfigurableEnvironment environment;
    
    private ClusterDefaultTlsProtocolNegotiatorBuilder builder;
    
    @Before
    public void setUp() {
        environment = new MockEnvironment();
        EnvUtil.setEnvironment(environment);
        builder = new ClusterDefaultTlsProtocolNegotiatorBuilder();
    }
    
    @After
    public void tearDown() throws NoSuchFieldException, IllegalAccessException {
        resetInstance();
    }
    
    @Test
    public void testBuildTlsDisabled() {
        assertNull(builder.build());
    }
    
    @Test
    public void testBuildTlsEnabled() {
        Properties properties = new Properties();
        properties.setProperty(RpcClusterServerTlsConfig.PREFIX + ".enableTls", "true");
        properties.setProperty(RpcClusterServerTlsConfig.PREFIX + ".compatibility", "false");
        properties.setProperty(RpcClusterServerTlsConfig.PREFIX + ".certChainFile", "test-server-cert.pem");
        properties.setProperty(RpcClusterServerTlsConfig.PREFIX + ".certPrivateKey", "test-server-key.pem");
        properties.setProperty(RpcClusterServerTlsConfig.PREFIX + ".trustCollectionCertFile", "test-ca-cert.pem");
        
        PropertiesPropertySource propertySource = new PropertiesPropertySource("myPropertySource", properties);
        MutablePropertySources propertySources = environment.getPropertySources();
        propertySources.addLast(propertySource);
        
        NacosGrpcProtocolNegotiator negotiator = builder.build();
        assertNotNull(negotiator);
    }
    
    private void resetInstance() throws NoSuchFieldException, IllegalAccessException {
        Field instanceField = RpcClusterServerTlsConfig.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, null);
    }
}
