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

import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.mock.env.MockEnvironment;

import java.util.Properties;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class SdkDefaultTlsProtocolNegotiatorBuilderTest {

    private ConfigurableEnvironment environment;

    private SdkDefaultTlsProtocolNegotiatorBuilder builder;

    @Mock
    private Properties properties;

    @Before
    public void setUp() throws Exception {
        environment = new MockEnvironment();
        EnvUtil.setEnvironment(environment);
        builder = new SdkDefaultTlsProtocolNegotiatorBuilder();
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void testBuildDisabled() {
        assertNull(builder.build());
    }

    @Test
    public void testBuildEnabled() {
        final MockedStatic<EnvUtil> envUtilMockedStatic = Mockito.mockStatic(EnvUtil.class);
        when(EnvUtil.getProperties()).thenReturn(properties);
        when(properties.getProperty("nacos.remote.server.rpc.tls.enableTls")).thenReturn("true");
        when(properties.getProperty("nacos.remote.server.rpc.tls.certPrivateKey")).thenReturn("test-server-key.pem");
        when(properties.getProperty("nacos.remote.server.rpc.tls.certChainFile")).thenReturn("test-server-cert.pem");
        assertNotNull(builder.build());
        envUtilMockedStatic.close();
    }
}
