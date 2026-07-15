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

package com.alibaba.nacos.naming.healthcheck.v2.processor;

import com.alibaba.nacos.naming.core.v2.metadata.ClusterMetadata;
import com.alibaba.nacos.naming.core.v2.pojo.HealthCheckInstancePublishInfo;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.healthcheck.v2.HealthCheckTaskV2;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.env.MockEnvironment;

import java.lang.reflect.Constructor;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.when;

/**
 * Regression tests for {@link TcpHealthCheckProcessor} that the SocketChannel is closed on both the
 * successful connect path and the connection-refused path, preventing file descriptor leaks.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TcpHealthCheckProcessorTest {
    
    @Mock
    private HealthCheckCommonV2 healthCheckCommon;
    
    @Mock
    private SwitchDomain switchDomain;
    
    @Mock
    private SwitchDomain.TcpHealthParams healthParams;
    
    @Mock
    private HealthCheckTaskV2 task;
    
    @Mock
    private Service service;
    
    @Mock
    private ClusterMetadata metadata;
    
    @Mock
    private HealthCheckInstancePublishInfo instance;
    
    private TcpHealthCheckProcessor processor;
    
    private Selector selector;
    
    @BeforeEach
    void setUp() throws Exception {
        EnvUtil.setEnvironment(new MockEnvironment());
        when(switchDomain.getTcpHealthParams()).thenReturn(healthParams);
        when(healthParams.getMax()).thenReturn(5000);
        when(task.getStartTime()).thenReturn(System.currentTimeMillis());
        when(service.getNameSpaceGroupedServiceName()).thenReturn("group@@service");
        when(instance.getCluster()).thenReturn("cluster");
        when(instance.getIp()).thenReturn("127.0.0.1");
        when(instance.getPort()).thenReturn(8848);
        processor = new TcpHealthCheckProcessor(healthCheckCommon, switchDomain);
        selector = Selector.open();
    }
    
    @AfterEach
    void tearDown() throws Exception {
        if (selector != null && selector.isOpen()) {
            selector.close();
        }
    }
    
    @Test
    void testChannelClosedOnSuccessfulConnect() throws Exception {
        ServerSocketChannel server = ServerSocketChannel.open();
        try {
            server.socket().bind(new InetSocketAddress("127.0.0.1", 0));
            int port = server.socket().getLocalPort();
            SocketChannel channel = openAndConnect(port);
            SelectionKey key = registerAndAwaitConnectable(channel);
            
            runPostProcessor(key);
            
            assertFalse(channel.isOpen(),
                "channel should be closed after a successful TCP health check");
        } finally {
            server.close();
        }
    }
    
    @Test
    void testChannelClosedOnConnectionRefused() throws Exception {
        int refusedPort = findRefusedPort();
        SocketChannel channel = openAndConnect(refusedPort);
        SelectionKey key = registerAndAwaitConnectable(channel);
        
        runPostProcessor(key);
        
        assertFalse(channel.isOpen(),
            "channel should be closed after a connection-refused TCP health check");
    }
    
    private SocketChannel openAndConnect(int port) throws Exception {
        SocketChannel channel = SocketChannel.open();
        channel.configureBlocking(false);
        channel.connect(new InetSocketAddress("127.0.0.1", port));
        return channel;
    }
    
    private SelectionKey registerAndAwaitConnectable(SocketChannel channel) throws Exception {
        SelectionKey key =
            channel.register(selector, SelectionKey.OP_CONNECT | SelectionKey.OP_READ);
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            selector.select(200);
            if (key.isConnectable()) {
                return key;
            }
        }
        throw new IllegalStateException("channel did not become connectable in time");
    }
    
    private void runPostProcessor(SelectionKey key) throws Exception {
        key.attach(newBeat());
        TcpHealthCheckProcessor.PostProcessor postProcessor = processor.new PostProcessor(key);
        postProcessor.run();
    }
    
    private Object newBeat() throws Exception {
        Class<?> beatClass = Class.forName(
            "com.alibaba.nacos.naming.healthcheck.v2.processor.TcpHealthCheckProcessor$Beat");
        Constructor<?> constructor = beatClass.getDeclaredConstructor(TcpHealthCheckProcessor.class,
            HealthCheckTaskV2.class, Service.class, ClusterMetadata.class,
            HealthCheckInstancePublishInfo.class);
        constructor.setAccessible(true);
        return constructor.newInstance(processor, task, service, metadata, instance);
    }
    
    private int findRefusedPort() throws Exception {
        ServerSocketChannel tmp = ServerSocketChannel.open();
        tmp.socket().bind(new InetSocketAddress("127.0.0.1", 0));
        int port = tmp.socket().getLocalPort();
        tmp.close();
        return port;
    }
}
