/*
 * Copyright 1999-$toady.year Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.common.remote.client;

import com.alibaba.nacos.common.remote.client.grpc.GrpcClientConfig;
import com.alibaba.nacos.common.remote.client.grpc.GrpcConstants;
import com.alibaba.nacos.common.utils.ThreadUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * RpcClientConfigFactory.
 *
 * @author Nacos
 */
public class RpcClientConfigFactoryTest {
    
    private RpcClientConfigFactory factory;
    
    @BeforeEach
    void setUp() {
        factory = RpcClientConfigFactory.getInstance();
    }
    
    @Test
    void testGetInstanceSingletonPattern() {
        RpcClientConfigFactory instance1 = RpcClientConfigFactory.getInstance();
        RpcClientConfigFactory instance2 = RpcClientConfigFactory.getInstance();
        assertEquals(instance1, instance2);
    }
    
    @Test
    void testCreateGrpcClientConfig() {
        Properties properties = new Properties();
        properties.setProperty(GrpcConstants.GRPC_NAME, "testClient");
        properties.setProperty(GrpcConstants.GRPC_CHANNEL_CAPABILITY_NEGOTIATION_TIMEOUT, "1000");
        properties.setProperty(GrpcConstants.GRPC_CHANNEL_KEEP_ALIVE_TIME, "60");
        properties.setProperty(GrpcConstants.GRPC_CHANNEL_KEEP_ALIVE_TIMEOUT, "300");
        properties.setProperty(GrpcConstants.GRPC_CONNECT_KEEP_ALIVE_TIME, "120");
        properties.setProperty(GrpcConstants.GRPC_HEALTHCHECK_RETRY_TIMES, "3");
        properties.setProperty(GrpcConstants.GRPC_HEALTHCHECK_TIMEOUT, "500");
        properties.setProperty(GrpcConstants.GRPC_MAX_INBOUND_MESSAGE_SIZE, "1024");
        properties.setProperty(GrpcConstants.GRPC_RETRY_TIMES, "5");
        properties.setProperty(GrpcConstants.GRPC_SERVER_CHECK_TIMEOUT, "1000");
        properties.setProperty(GrpcConstants.GRPC_THREADPOOL_CORE_SIZE, "10");
        properties.setProperty(GrpcConstants.GRPC_THREADPOOL_KEEPALIVETIME, "60");
        properties.setProperty(GrpcConstants.GRPC_THREADPOOL_MAX_SIZE, "20");
        properties.setProperty(GrpcConstants.GRPC_QUEUESIZE, "100");
        properties.setProperty(GrpcConstants.GRPC_TIMEOUT_MILLS, "2000");
        
        Map<String, String> labels = new HashMap<>();
        labels.put("labelKey1", "labelValue1");
        labels.put("labelKey2", "labelValue2");
        labels.put("tls.enable", "false");
        
        GrpcClientConfig config = factory.createGrpcClientConfig(properties, labels);
        
        assertEquals(labels, config.labels());
        assertEquals(1000, config.capabilityNegotiationTimeout());
        assertEquals(60, config.channelKeepAlive());
        assertEquals(300, config.channelKeepAliveTimeout());
        assertEquals(120, config.connectionKeepAlive());
        assertEquals(3, config.healthCheckRetryTimes());
        assertEquals(500, config.healthCheckTimeOut());
        assertEquals(1024, config.maxInboundMessageSize());
        assertEquals("testClient", config.name());
        assertEquals(5, config.retryTimes());
        assertEquals(1000, config.serverCheckTimeOut());
        assertEquals(10, config.threadPoolCoreSize());
        assertEquals(60, config.threadPoolKeepAlive());
        assertEquals(20, config.threadPoolMaxSize());
        assertEquals(100, config.threadPoolQueueSize());
        assertEquals(2000, config.timeOutMills());
    }
    
    @Test
    void testDefaultGrpcClientConfig() {
        Properties properties = new Properties();
        Map<String, String> labels = new HashMap<>();
        labels.put("tls.enable", "false");
        GrpcClientConfig config = factory.createGrpcClientConfig(properties, labels);
        
        assertEquals(labels, config.labels());
        assertEquals(5000, config.capabilityNegotiationTimeout());
        assertEquals(360000, config.channelKeepAlive()); // 6 * 60 * 1000
        assertEquals(20000, config.channelKeepAliveTimeout()); // TimeUnit.SECONDS.toMillis(20L)
        assertEquals(5000, config.connectionKeepAlive());
        assertEquals(3, config.healthCheckRetryTimes());
        assertEquals(3000, config.healthCheckTimeOut());
        assertEquals(10485760, config.maxInboundMessageSize()); // 10 * 1024 * 1024
        assertEquals(null, config.name());
        assertEquals(3, config.retryTimes());
        assertEquals(3000, config.serverCheckTimeOut());
        assertEquals(ThreadUtils.getSuitableThreadCount(2), config.threadPoolCoreSize());
        assertEquals(10000, config.threadPoolKeepAlive());
        assertEquals(ThreadUtils.getSuitableThreadCount(8), config.threadPoolMaxSize());
        assertEquals(10000, config.threadPoolQueueSize());
        assertEquals(3000, config.timeOutMills());
    }
}