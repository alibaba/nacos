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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.mock.env.MockEnvironment;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
// todo remove this
@MockitoSettings(strictness = Strictness.LENIENT)
class SdkDefaultTlsProtocolNegotiatorBuilderTest {
    
    private ConfigurableEnvironment environment;
    
    private SdkDefaultTlsProtocolNegotiatorBuilder builder;
    
    @Mock
    private Properties properties;
    
    @BeforeEach
    void setUp() throws Exception {
        environment = new MockEnvironment();
        EnvUtil.setEnvironment(environment);
        builder = new SdkDefaultTlsProtocolNegotiatorBuilder();
    }
    
    @AfterEach
    void tearDown() throws Exception {
    }
    
    @Test
    void testBuildDisabled() {
        assertNull(builder.build());
    }
    
    @Test
    void testBuildEnabled() {
        try (final MockedStatic<EnvUtil> envUtilMockedStatic = Mockito.mockStatic(EnvUtil.class)) {
            when(EnvUtil.getProperties()).thenReturn(properties);
            when(properties.getProperty("nacos.remote.server.rpc.tls.enableTls")).thenReturn("true");
            when(properties.getProperty("nacos.remote.server.rpc.tls.certPrivateKey")).thenReturn("test-server-key.pem");
            when(properties.getProperty("nacos.remote.server.rpc.tls.certChainFile")).thenReturn("test-server-cert.pem");
            assertNotNull(builder.build());
        }
    }
}
