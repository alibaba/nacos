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

import com.alibaba.nacos.core.remote.BaseRpcServer;
import com.alibaba.nacos.core.remote.tls.RpcClusterServerTlsConfig;
import com.alibaba.nacos.core.remote.tls.RpcSdkServerTlsConfig;
import com.alibaba.nacos.core.remote.tls.RpcServerSslContextRefresher;
import com.alibaba.nacos.core.remote.tls.RpcServerSslContextRefresherHolder;
import com.alibaba.nacos.core.remote.tls.SslContextChangeAware;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.mock.env.MockEnvironment;

import java.util.Properties;

import static org.junit.Assert.assertNotNull;

/**
 * Test RpcServerSslContextRefresherHolderTest.
 *
 * @author stone-98
 */
public class RpcServerSslContextRefresherHolderTest {

    private ConfigurableEnvironment environment;

    @Before
    public void setUp() {
        environment = new MockEnvironment();
        MutablePropertySources propertySources = environment.getPropertySources();
        Properties properties = new Properties();
        properties.setProperty(RpcSdkServerTlsConfig.PREFIX + ".sslContextRefresher", "sdk-test-refresher");
        properties.setProperty(RpcClusterServerTlsConfig.PREFIX + ".sslContextRefresher", "cluster-test-refresher");

        PropertiesPropertySource propertySource = new PropertiesPropertySource("myPropertySource", properties);
        propertySources.addLast(propertySource);
        EnvUtil.setEnvironment(environment);
    }

    @After
    public void tearDown() {
    }

    @Test
    public void testInit() {
        // Call init to initialize the holders
        RpcServerSslContextRefresherHolder.init();

        // Check if instances are not null after initialization
        assertNotNull(RpcServerSslContextRefresherHolder.getClusterInstance());
        assertNotNull(RpcServerSslContextRefresherHolder.getSdkInstance());
    }

    public static class SdkRpcServerSslContextRefresherTest implements RpcServerSslContextRefresher {

        @Override
        public SslContextChangeAware refresh(BaseRpcServer baseRpcServer) {
            return new SslContextChangeAware() {
                @Override
                public void init(BaseRpcServer baseRpcServer) {

                }

                @Override
                public void onSslContextChange() {

                }

                @Override
                public void shutdown() {

                }
            };
        }

        @Override
        public String getName() {
            return "sdk-test-refresher";
        }
    }

    public static class ClusterRpcServerSslContextRefresherTest implements RpcServerSslContextRefresher {

        @Override
        public SslContextChangeAware refresh(BaseRpcServer baseRpcServer) {
            return new SslContextChangeAware() {
                @Override
                public void init(BaseRpcServer baseRpcServer) {

                }

                @Override
                public void onSslContextChange() {

                }

                @Override
                public void shutdown() {

                }
            };
        }

        @Override
        public String getName() {
            return "cluster-test-refresher";
        }
    }
}
