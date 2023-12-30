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
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.core.env.ConfigurableEnvironment;

import java.lang.reflect.Field;

import static com.alibaba.nacos.core.remote.grpc.negotiator.tls.ClusterDefaultTlsProtocolNegotiatorBuilder.CLUSTER_TYPE_DEFAULT_TLS;
import static com.alibaba.nacos.core.remote.grpc.negotiator.tls.ProtocolNegotiatorBuilderManager.CLUSTER_TYPE_PROPERTY_KEY;
import static com.alibaba.nacos.core.remote.grpc.negotiator.tls.ProtocolNegotiatorBuilderManager.SDK_TYPE_PROPERTY_KEY;
import static com.alibaba.nacos.core.remote.grpc.negotiator.tls.SdkDefaultTlsProtocolNegotiatorBuilder.SDK_TYPE_DEFAULT_TLS;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.when;

/**
 * Test ProtocolNegotiatorBuilderManager.
 *
 * @author stone-98
 */
@RunWith(MockitoJUnitRunner.class)
public class ProtocolNegotiatorBuilderManagerTest {

    @Mock
    private RpcSdkServerTlsConfig sdkConfig;

    @Mock
    private RpcClusterServerTlsConfig clusterConfig;

    @Mock
    private ConfigurableEnvironment environment;

    @Before
    public void setUp() {
        EnvUtil.setEnvironment(environment);
    }

    @After
    public void tearDown() throws Exception {
        EnvUtil.setEnvironment(null);
        reset();
    }

    private void reset() {
        setStaticField(RpcClusterServerTlsConfig.class, null, "instance");
        setStaticField(RpcSdkServerTlsConfig.class, null, "instance");
        setStaticField(ProtocolNegotiatorBuilderManager.class, null, "builderMap");
        setStaticField(ProtocolNegotiatorBuilderManager.class, null, "actualTypeMap");
    }

    @Test
    public void testGetSdkNegotiator() {
        when(environment.getProperty(SDK_TYPE_PROPERTY_KEY, SDK_TYPE_DEFAULT_TLS)).thenReturn(SDK_TYPE_DEFAULT_TLS);
        when(environment.getProperty(CLUSTER_TYPE_PROPERTY_KEY, CLUSTER_TYPE_DEFAULT_TLS)).thenReturn(CLUSTER_TYPE_DEFAULT_TLS);
        when(sdkConfig.getEnableTls()).thenReturn(true);
        when(sdkConfig.getCertChainFile()).thenReturn("test-ca-cert.pem");
        when(sdkConfig.getCertPrivateKey()).thenReturn("test-server-key.pem");
        setStaticField(RpcSdkServerTlsConfig.class, sdkConfig, "instance");
        ProtocolNegotiatorBuilderManager manager = ProtocolNegotiatorBuilderManager.getInstance();
        NacosGrpcProtocolNegotiator negotiator = manager.get(CommunicationType.SDK);
        assertNotNull("SDK ProtocolNegotiator should not be null", negotiator);
    }

    @Test
    public void testGetClusterNegotiator() {
        when(environment.getProperty(SDK_TYPE_PROPERTY_KEY, SDK_TYPE_DEFAULT_TLS)).thenReturn(SDK_TYPE_DEFAULT_TLS);
        when(environment.getProperty(CLUSTER_TYPE_PROPERTY_KEY, CLUSTER_TYPE_DEFAULT_TLS)).thenReturn(CLUSTER_TYPE_DEFAULT_TLS);
        when(clusterConfig.getEnableTls()).thenReturn(true);
        when(clusterConfig.getCertChainFile()).thenReturn("test-ca-cert.pem");
        when(clusterConfig.getCertPrivateKey()).thenReturn("test-server-key.pem");
        setStaticField(RpcClusterServerTlsConfig.class, clusterConfig, "instance");
        ProtocolNegotiatorBuilderManager manager = ProtocolNegotiatorBuilderManager.getInstance();
        NacosGrpcProtocolNegotiator negotiator = manager.get(CommunicationType.CLUSTER);
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
