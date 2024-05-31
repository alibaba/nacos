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

import com.alibaba.nacos.common.remote.client.RpcConstants;
import com.alibaba.nacos.core.remote.grpc.negotiator.NacosGrpcProtocolNegotiator;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.mock.env.MockEnvironment;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Test ClusterDefaultTlsProtocolNegotiatorBuilder.
 *
 * @author stone-98
 * @date 2023/12/25
 */
class ClusterDefaultTlsProtocolNegotiatorBuilderTest {
    
    private ConfigurableEnvironment environment;
    
    private ClusterDefaultTlsProtocolNegotiatorBuilder builder;
    
    @BeforeEach
    void setUp() {
        environment = new MockEnvironment();
        EnvUtil.setEnvironment(environment);
        builder = new ClusterDefaultTlsProtocolNegotiatorBuilder();
    }
    
    @AfterEach
    void tearDown() throws NoSuchFieldException, IllegalAccessException {
    }
    
    @Test
    void testBuildTlsDisabled() {
        assertNull(builder.build());
    }
    
    @Test
    void testBuildTlsEnabled() {
        Properties properties = new Properties();
        properties.setProperty(RpcConstants.NACOS_PEER_RPC + ".enableTls", "true");
        properties.setProperty(RpcConstants.NACOS_PEER_RPC + ".compatibility", "false");
        properties.setProperty(RpcConstants.NACOS_PEER_RPC + ".ciphers", "ECDHE-RSA-AES128-GCM-SHA256,ECDHE-RSA-AES256-GCM-SHA384");
        properties.setProperty(RpcConstants.NACOS_PEER_RPC + ".protocols", "TLSv1.2,TLSv1.3");
        properties.setProperty(RpcConstants.NACOS_PEER_RPC + ".certPrivateKey", "test-server-key.pem");
        properties.setProperty(RpcConstants.NACOS_PEER_RPC + ".certChainFile", "test-server-cert.pem");
        properties.setProperty(RpcConstants.NACOS_PEER_RPC + ".trustCollectionCertFile", "test-ca-cert.pem");
        
        PropertiesPropertySource propertySource = new PropertiesPropertySource("myPropertySource", properties);
        MutablePropertySources propertySources = environment.getPropertySources();
        propertySources.addLast(propertySource);
        
        NacosGrpcProtocolNegotiator negotiator = builder.build();
        assertNotNull(negotiator);
    }
    
}
