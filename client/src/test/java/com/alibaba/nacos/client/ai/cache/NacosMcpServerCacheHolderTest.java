/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.client.ai.cache;

import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.registry.Repository;
import com.alibaba.nacos.api.ai.model.mcp.registry.ServerVersionDetail;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.ai.event.McpServerChangedEvent;
import com.alibaba.nacos.client.ai.remote.AiGrpcClient;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.notify.listener.Subscriber;

@ExtendWith(MockitoExtension.class)
class NacosMcpServerCacheHolderTest {
    
    @Mock
    private AiGrpcClient aiGrpcClient;
    
    NacosMcpServerCacheHolder cacheHolder;
    
    @BeforeEach
    void setUp() {
        Properties properties = new Properties();
        properties.put(AiConstants.AI_MCP_SERVER_CACHE_UPDATE_INTERVAL, "100");
        cacheHolder = new NacosMcpServerCacheHolder(aiGrpcClient, NacosClientProperties.PROTOTYPE.derive(properties));
    }
    
    @AfterEach
    void tearDown() throws NacosException {
        cacheHolder.shutdown();
        NotifyCenter.deregisterPublisher(McpServerChangedEvent.class);
    }
    
    @Test()
    void processMcpServerDetailInfo() throws InterruptedException {
        assertNull(cacheHolder.getMcpServer("test", "1.0.0"));
        MockEventSubscriber subscriber = new MockEventSubscriber();
        NotifyCenter.registerSubscriber(subscriber);
        McpServerDetailInfo mcpServerDetailInfo = new McpServerDetailInfo();
        mcpServerDetailInfo.setName("test");
        mcpServerDetailInfo.setVersionDetail(new ServerVersionDetail());
        mcpServerDetailInfo.getVersionDetail().setVersion("1.0.0");
        mcpServerDetailInfo.getVersionDetail().setIs_latest(true);
        cacheHolder.processMcpServerDetailInfo(mcpServerDetailInfo);
        assertNotNull(cacheHolder.getMcpServer("test", "1.0.0"));
        assertEquals(mcpServerDetailInfo, cacheHolder.getMcpServer("test", "1.0.0"));
        int retry = 0;
        while (retry < 3) {
            TimeUnit.MILLISECONDS.sleep(500);
            if (subscriber.invokedMark.get()) {
                return;
            }
            retry++;
        }
        fail("Subscriber for McpServerChangedEvent don't be invoked.");
    }
    
    @Test()
    void processMcpServerDetailInfoLatest() throws InterruptedException {
        assertNull(cacheHolder.getMcpServer("test", "1.0.0"));
        MockEventSubscriber subscriber = new MockEventSubscriber();
        NotifyCenter.registerSubscriber(subscriber);
        McpServerDetailInfo mcpServerDetailInfo = new McpServerDetailInfo();
        mcpServerDetailInfo.setName("test");
        mcpServerDetailInfo.setVersionDetail(new ServerVersionDetail());
        mcpServerDetailInfo.getVersionDetail().setVersion("1.0.0");
        mcpServerDetailInfo.getVersionDetail().setIs_latest(true);
        cacheHolder.processMcpServerDetailInfo(mcpServerDetailInfo);
        assertNotNull(cacheHolder.getMcpServer("test", "1.0.0"));
        assertNotNull(cacheHolder.getMcpServer("test", null));
        assertEquals(mcpServerDetailInfo, cacheHolder.getMcpServer("test", "1.0.0"));
        int retry = 0;
        while (retry < 3) {
            TimeUnit.MILLISECONDS.sleep(500);
            if (subscriber.invokedMark.get()) {
                return;
            }
            retry++;
        }
        fail("Subscriber for McpServerChangedEvent don't be invoked.");
    }
    
    @Test()
    void processMcpServerDetailInfoDiff() throws InterruptedException {
        McpServerDetailInfo mcpServerDetailInfo = new McpServerDetailInfo();
        mcpServerDetailInfo.setName("test");
        mcpServerDetailInfo.setVersionDetail(new ServerVersionDetail());
        mcpServerDetailInfo.getVersionDetail().setVersion("1.0.0");
        mcpServerDetailInfo.getVersionDetail().setIs_latest(true);
        cacheHolder.processMcpServerDetailInfo(mcpServerDetailInfo);
        mcpServerDetailInfo = new McpServerDetailInfo();
        mcpServerDetailInfo.setName("test");
        mcpServerDetailInfo.setVersionDetail(new ServerVersionDetail());
        mcpServerDetailInfo.getVersionDetail().setVersion("1.0.0");
        mcpServerDetailInfo.getVersionDetail().setIs_latest(true);
        mcpServerDetailInfo.setProtocol(AiConstants.Mcp.MCP_PROTOCOL_STDIO);
        
        MockEventSubscriber subscriber = new MockEventSubscriber();
        NotifyCenter.registerSubscriber(subscriber);
        cacheHolder.processMcpServerDetailInfo(mcpServerDetailInfo);
        assertEquals(mcpServerDetailInfo, cacheHolder.getMcpServer("test", "1.0.0"));
        int retry = 0;
        while (retry < 3) {
            TimeUnit.MILLISECONDS.sleep(500);
            if (subscriber.invokedMark.get()) {
                return;
            }
            retry++;
        }
        fail("Subscriber for McpServerChangedEvent don't be invoked.");
    }
    
    @Test()
    void processMcpServerDetailInfoNoDiff() throws InterruptedException {
        McpServerDetailInfo mcpServerDetailInfo = new McpServerDetailInfo();
        mcpServerDetailInfo.setName("test");
        mcpServerDetailInfo.setVersionDetail(new ServerVersionDetail());
        mcpServerDetailInfo.getVersionDetail().setVersion("1.0.0");
        mcpServerDetailInfo.getVersionDetail().setIs_latest(true);
        cacheHolder.processMcpServerDetailInfo(mcpServerDetailInfo);
        
        MockEventSubscriber subscriber = new MockEventSubscriber();
        NotifyCenter.registerSubscriber(subscriber);
        cacheHolder.processMcpServerDetailInfo(mcpServerDetailInfo);
        assertEquals(mcpServerDetailInfo, cacheHolder.getMcpServer("test", "1.0.0"));
        int retry = 0;
        while (retry < 3) {
            TimeUnit.MILLISECONDS.sleep(500);
            if (subscriber.invokedMark.get()) {
                fail("Subscriber for McpServerChangedEvent should not be invoked, but invoked.");
            }
            retry++;
        }
    }
    
    @Test
    @Disabled
    void processMcpServerDetailInfoWithException() throws InterruptedException {
        McpServerDetailInfo mcpServerDetailInfo = new McpServerDetailInfo();
        mcpServerDetailInfo.setName("test");
        mcpServerDetailInfo.setVersionDetail(new ServerVersionDetail());
        mcpServerDetailInfo.getVersionDetail().setVersion("1.0.0");
        // empty bean Repository will cause json serialize exception
        mcpServerDetailInfo.setRepository(new Repository());
        MockEventSubscriber subscriber = new MockEventSubscriber();
        NotifyCenter.registerSubscriber(subscriber);
        cacheHolder.processMcpServerDetailInfo(mcpServerDetailInfo);
        int retry = 0;
        while (retry < 3) {
            TimeUnit.MILLISECONDS.sleep(500);
            if (subscriber.invokedMark.get()) {
                fail("Subscriber for McpServerChangedEvent should not be invoked, but invoked.");
            }
            retry++;
        }
    }
    
    @Test
    void addMcpServerUpdateTask() throws NacosException, InterruptedException {
        assertNull(cacheHolder.getMcpServer("test", "1.0.0"));
        McpServerDetailInfo mcpServerDetailInfo = new McpServerDetailInfo();
        mcpServerDetailInfo.setName("test");
        mcpServerDetailInfo.setVersionDetail(new ServerVersionDetail());
        mcpServerDetailInfo.getVersionDetail().setVersion("1.0.0");
        when(aiGrpcClient.queryMcpServer("test", "1.0.0")).thenReturn(mcpServerDetailInfo);
        cacheHolder.addMcpServerUpdateTask("test", "1.0.0");
        TimeUnit.MILLISECONDS.sleep(110);
        assertNotNull(cacheHolder.getMcpServer("test", "1.0.0"));
        TimeUnit.MILLISECONDS.sleep(110);
        verify(aiGrpcClient, times(2)).queryMcpServer("test", "1.0.0");
    }
    
    @Test
    void runUpdateTaskWithException() throws NacosException, InterruptedException {
        assertNull(cacheHolder.getMcpServer("test", "1.0.0"));
        McpServerDetailInfo mcpServerDetailInfo = new McpServerDetailInfo();
        mcpServerDetailInfo.setName("test");
        mcpServerDetailInfo.setVersionDetail(new ServerVersionDetail());
        mcpServerDetailInfo.getVersionDetail().setVersion("1.0.0");
        when(aiGrpcClient.queryMcpServer("test", "1.0.0")).thenThrow(new RuntimeException("test"));
        cacheHolder.addMcpServerUpdateTask("test", "1.0.0");
        TimeUnit.MILLISECONDS.sleep(110);
        assertNull(cacheHolder.getMcpServer("test", "1.0.0"));
        TimeUnit.MILLISECONDS.sleep(110);
        verify(aiGrpcClient, times(2)).queryMcpServer("test", "1.0.0");
    }
    
    @Test
    void removeMcpServerUpdateTask() throws NacosException, InterruptedException {
        assertNull(cacheHolder.getMcpServer("test", "1.0.0"));
        McpServerDetailInfo mcpServerDetailInfo = new McpServerDetailInfo();
        mcpServerDetailInfo.setName("test");
        mcpServerDetailInfo.setVersionDetail(new ServerVersionDetail());
        mcpServerDetailInfo.getVersionDetail().setVersion("1.0.0");
        when(aiGrpcClient.queryMcpServer("test", "1.0.0")).thenReturn(mcpServerDetailInfo);
        cacheHolder.addMcpServerUpdateTask("test", "1.0.0");
        TimeUnit.MILLISECONDS.sleep(110);
        assertNotNull(cacheHolder.getMcpServer("test", "1.0.0"));
        cacheHolder.removeMcpServerUpdateTask("test", "1.0.0");
        TimeUnit.MILLISECONDS.sleep(110);
        verify(aiGrpcClient).queryMcpServer("test", "1.0.0");
    }
    
    @Test
    void removeMcpServerUpdateTaskImmediately() throws NacosException, InterruptedException {
        assertNull(cacheHolder.getMcpServer("test", "1.0.0"));
        McpServerDetailInfo mcpServerDetailInfo = new McpServerDetailInfo();
        mcpServerDetailInfo.setName("test");
        mcpServerDetailInfo.setVersionDetail(new ServerVersionDetail());
        mcpServerDetailInfo.getVersionDetail().setVersion("1.0.0");
        cacheHolder.addMcpServerUpdateTask("test", "1.0.0");
        cacheHolder.removeMcpServerUpdateTask("test", "1.0.0");
        TimeUnit.MILLISECONDS.sleep(110);
        verify(aiGrpcClient, never()).queryMcpServer("test", null);
    }
    
    private static class MockEventSubscriber extends Subscriber<McpServerChangedEvent> {
        
        private final AtomicBoolean invokedMark;
        
        private MockEventSubscriber() {
            this.invokedMark = new AtomicBoolean(false);
        }
        
        @Override
        public void onEvent(McpServerChangedEvent event) {
            invokedMark.set(true);
        }
        
        @Override
        public Class<? extends Event> subscribeType() {
            return McpServerChangedEvent.class;
        }
    }
}