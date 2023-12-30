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

import com.alibaba.nacos.core.remote.CommunicationType;
import com.alibaba.nacos.core.remote.grpc.negotiator.NacosGrpcProtocolNegotiator;
import com.alibaba.nacos.core.remote.tls.RpcClusterServerTlsConfig;
import com.alibaba.nacos.core.remote.tls.RpcSdkServerTlsConfig;
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

/**
 * Test ProtocolNegotiatorBuilderManager.
 *
 * @author stone-98
 */
public class ProtocolNegotiatorBuilderManagerTest {

    @Before
    public void setUp() throws Exception {
        ConfigurableEnvironment environment = new MockEnvironment();
        Properties properties = new Properties();
        properties.setProperty(RpcSdkServerTlsConfig.PREFIX + ".enableTls", "true");
        properties.setProperty(RpcSdkServerTlsConfig.PREFIX + ".compatibility", "false");
        properties.setProperty(RpcSdkServerTlsConfig.PREFIX + ".certChainFile", "test-server-cert.pem");
        properties.setProperty(RpcSdkServerTlsConfig.PREFIX + ".certPrivateKey", "test-server-key.pem");
        properties.setProperty(RpcSdkServerTlsConfig.PREFIX + ".trustCollectionCertFile", "test-ca-cert.pem");
        properties.setProperty(RpcClusterServerTlsConfig.PREFIX + ".enableTls", "true");
        properties.setProperty(RpcClusterServerTlsConfig.PREFIX + ".compatibility", "false");
        properties.setProperty(RpcClusterServerTlsConfig.PREFIX + ".certChainFile", "test-server-cert.pem");
        properties.setProperty(RpcClusterServerTlsConfig.PREFIX + ".certPrivateKey", "test-server-key.pem");
        properties.setProperty(RpcClusterServerTlsConfig.PREFIX + ".trustCollectionCertFile", "test-ca-cert.pem");

        MutablePropertySources propertySources = environment.getPropertySources();
        PropertiesPropertySource propertySource = new PropertiesPropertySource("myPropertySource", properties);
        propertySources.addLast(propertySource);
        EnvUtil.setEnvironment(environment);
        setStaticField(RpcSdkServerTlsConfig.class, null, "instance");
        setStaticField(RpcClusterServerTlsConfig.class, null, "instance");
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void testGetSdkNegotiator() {
        ProtocolNegotiatorBuilderManager manager = ProtocolNegotiatorBuilderManager.getInstance();
        NacosGrpcProtocolNegotiator negotiator = manager.buildGrpcProtocolNegotiator(CommunicationType.SDK);
        assertNotNull("SDK ProtocolNegotiator should not be null", negotiator);
    }

    @Test
    public void testGetClusterNegotiator() {
        ProtocolNegotiatorBuilderManager manager = ProtocolNegotiatorBuilderManager.getInstance();
        NacosGrpcProtocolNegotiator negotiator = manager.buildGrpcProtocolNegotiator(CommunicationType.CLUSTER);
        assertNotNull("Cluster ProtocolNegotiator should not be null", negotiator);
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
