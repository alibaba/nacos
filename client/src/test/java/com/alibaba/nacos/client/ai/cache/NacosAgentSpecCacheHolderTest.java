/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpec;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.ai.event.AgentSpecChangedEvent;
import com.alibaba.nacos.client.ai.remote.AgentSpecQueryResponse;
import com.alibaba.nacos.client.ai.remote.AiClientProxy;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.notify.listener.Subscriber;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NacosAgentSpecCacheHolderTest {
    
    private static final AtomicLong SPEC_SEQUENCE = new AtomicLong();
    
    @Mock
    private AiClientProxy aiClientProxy;
    
    private NacosAgentSpecCacheHolder cacheHolder;
    
    private final List<MockAgentSpecEventSubscriber> registeredSubscribers = new ArrayList<>();
    
    private String specName;
    
    @BeforeEach
    void setUp(TestInfo testInfo) {
        specName = testInfo.getTestMethod().orElseThrow().getName() + "-"
            + SPEC_SEQUENCE.incrementAndGet();
        Properties properties = new Properties();
        properties.put(AiConstants.AI_AGENTSPEC_CACHE_UPDATE_INTERVAL, "60000");
        NotifyCenter.registerToPublisher(AgentSpecChangedEvent.class, 16384);
        cacheHolder = new NacosAgentSpecCacheHolder(aiClientProxy,
            NacosClientProperties.PROTOTYPE.derive(properties));
    }
    
    @AfterEach
    void tearDown() throws NacosException {
        for (MockAgentSpecEventSubscriber each : registeredSubscribers) {
            NotifyCenter.deregisterSubscriber(each);
        }
        registeredSubscribers.clear();
        cacheHolder.shutdown();
        NotifyCenter.deregisterPublisher(AgentSpecChangedEvent.class);
    }
    
    @Test
    void queryAgentSpecShouldReturnAgentSpec() throws Exception {
        AgentSpec spec = new AgentSpec();
        spec.setName(specName);
        AgentSpecQueryResponse response = new AgentSpecQueryResponse(spec, "md5a", "1.0.0");
        when(aiClientProxy.queryAgentSpec(specName, null, null, null))
            .thenReturn(response);
        
        AgentSpec result = cacheHolder.queryAgentSpec(specName);
        
        assertNotNull(result);
        assertEquals(specName, result.getName());
    }
    
    @Test
    void queryAgentSpecShouldReturnNullWhenNotFound() throws Exception {
        when(aiClientProxy.queryAgentSpec(specName, null, null, null))
            .thenThrow(new NacosException(NacosException.NOT_FOUND, "not found"));
        
        AgentSpec result = cacheHolder.queryAgentSpec(specName);
        
        assertNull(result);
    }
    
    @Test
    void queryAgentSpecShouldThrowWhenBlankName() {
        assertThrows(NacosException.class, () -> cacheHolder.queryAgentSpec(""));
    }
    
    @Test
    void subscribeAgentSpecShouldReturnNullAndScheduleWhenNotFound() throws Exception {
        when(aiClientProxy.queryAgentSpec(specName, null, null, null))
            .thenThrow(new NacosException(NacosException.NOT_FOUND, "not found"));
        
        AgentSpec result = cacheHolder.subscribeAgentSpec(specName);
        
        assertNull(result);
        assertEquals(1, getUpdateTaskMap().size());
    }
    
    @Test
    void subscribeAgentSpecShouldCacheButNotPublishEvent() throws Exception {
        AgentSpec spec = new AgentSpec();
        spec.setName(specName);
        AgentSpecQueryResponse response = new AgentSpecQueryResponse(spec, "md5a", "1.0.0");
        when(aiClientProxy.queryAgentSpec(specName, null, null, null))
            .thenReturn(response);
        MockAgentSpecEventSubscriber subscriber = registerMockSubscriber();
        
        AgentSpec result = cacheHolder.subscribeAgentSpec(specName);
        
        assertNotNull(result);
        assertEquals("md5a", getMd5Cache().get(specName));
        // Initial subscribe must NOT publish event; the caller (NacosAiService)
        // is responsible for the first listener notification to avoid double-invocation.
        assertFalse(subscriber.await(200),
            "Initial subscribe should not publish event via NotifyCenter");
        assertFalse(subscriber.invokedMark.get());
    }
    
    @Test
    void subscribeAgentSpecShouldThrowWhenBlankName() {
        assertThrows(NacosException.class, () -> cacheHolder.subscribeAgentSpec(""));
    }
    
    @Test
    void updaterShouldIgnoreWhenNotModified() throws Exception {
        AgentSpec spec = new AgentSpec();
        spec.setName(specName);
        AgentSpecQueryResponse response = new AgentSpecQueryResponse(spec, "md5a", "1.0.0");
        when(aiClientProxy.queryAgentSpec(specName, null, null, null))
            .thenReturn(response);
        when(aiClientProxy.queryAgentSpec(specName, null, null, "md5a"))
            .thenThrow(new NacosException(NacosException.NOT_MODIFIED, "up to date"));
        cacheHolder.subscribeAgentSpec(specName);
        MockAgentSpecEventSubscriber subscriber = registerMockSubscriber();
        
        Runnable updater = getOnlyUpdater();
        updater.run();
        
        assertEquals("md5a", getMd5Cache().get(specName));
        assertFalse(subscriber.await(200));
        assertFalse(subscriber.invokedMark.get());
    }
    
    @Test
    void updaterShouldEvictCacheWhenNotFound() throws Exception {
        AgentSpec spec = new AgentSpec();
        spec.setName(specName);
        AgentSpecQueryResponse response = new AgentSpecQueryResponse(spec, "md5a", "1.0.0");
        when(aiClientProxy.queryAgentSpec(specName, null, null, null))
            .thenReturn(response);
        when(aiClientProxy.queryAgentSpec(specName, null, null, "md5a"))
            .thenThrow(new NacosException(NacosException.NOT_FOUND, "not found"));
        cacheHolder.subscribeAgentSpec(specName);
        MockAgentSpecEventSubscriber subscriber = registerMockSubscriber();
        
        Runnable updater = getOnlyUpdater();
        updater.run();
        
        assertNull(getMd5Cache().get(specName));
        assertFalse(subscriber.await(200));
    }
    
    @Test
    void updaterShouldPublishEventWhenMd5Changed() throws Exception {
        AgentSpec spec1 = new AgentSpec();
        spec1.setName(specName);
        AgentSpec spec2 = new AgentSpec();
        spec2.setName(specName);
        spec2.setDescription("updated");
        AgentSpecQueryResponse first = new AgentSpecQueryResponse(spec1, "md5a", "1.0.0");
        AgentSpecQueryResponse second = new AgentSpecQueryResponse(spec2, "md5b", "1.0.1");
        when(aiClientProxy.queryAgentSpec(specName, null, null, null))
            .thenReturn(first);
        when(aiClientProxy.queryAgentSpec(specName, null, null, "md5a"))
            .thenReturn(second);
        cacheHolder.subscribeAgentSpec(specName);
        MockAgentSpecEventSubscriber subscriber = registerMockSubscriber();
        
        Runnable updater = getOnlyUpdater();
        updater.run();
        
        assertEquals("md5b", getMd5Cache().get(specName));
        assertTrue(subscriber.await(5000));
        assertTrue(subscriber.invokedMark.get());
    }
    
    @Test
    void unsubscribeAgentSpecShouldCancelTaskAndRemoveCache() throws Exception {
        AgentSpec spec = new AgentSpec();
        spec.setName(specName);
        AgentSpecQueryResponse response = new AgentSpecQueryResponse(spec, "md5a", "1.0.0");
        when(aiClientProxy.queryAgentSpec(specName, null, null, null))
            .thenReturn(response);
        cacheHolder.subscribeAgentSpec(specName);
        
        cacheHolder.unsubscribeAgentSpec(specName);
        
        assertTrue(getUpdateTaskMap().isEmpty());
        assertNull(getMd5Cache().get(specName));
        verify(aiClientProxy, never()).queryAgentSpec(specName, null, null, "md5a");
    }
    
    @Test
    void subscribeAgentSpecShouldThrowOnUnexpectedException() throws Exception {
        when(aiClientProxy.queryAgentSpec(specName, null, null, null))
            .thenThrow(new NacosException(NacosException.SERVER_ERROR, "server error"));
        
        assertThrows(NacosException.class, () -> cacheHolder.subscribeAgentSpec(specName));
    }
    
    @Test
    void updaterShouldIgnoreGeneralExceptionAndKeepCache() throws Exception {
        AgentSpec spec = new AgentSpec();
        spec.setName(specName);
        AgentSpecQueryResponse response = new AgentSpecQueryResponse(spec, "md5a", "1.0.0");
        when(aiClientProxy.queryAgentSpec(specName, null, null, null))
            .thenReturn(response);
        when(aiClientProxy.queryAgentSpec(specName, null, null, "md5a"))
            .thenThrow(new NacosException(NacosException.SERVER_ERROR, "server error"));
        cacheHolder.subscribeAgentSpec(specName);
        
        Runnable updater = getOnlyUpdater();
        updater.run();
        
        assertNotNull(getMd5Cache().get(specName));
        assertEquals(1, getUpdateTaskMap().size());
    }
    
    @SuppressWarnings("unchecked")
    private Map<String, String> getMd5Cache() throws Exception {
        Field field = NacosAgentSpecCacheHolder.class.getDeclaredField("md5Cache");
        field.setAccessible(true);
        return (Map<String, String>) field.get(cacheHolder);
    }
    
    @SuppressWarnings("unchecked")
    private Map<String, Object> getUpdateTaskMap() throws Exception {
        Field field = NacosAgentSpecCacheHolder.class.getDeclaredField("updateTaskMap");
        field.setAccessible(true);
        return (Map<String, Object>) field.get(cacheHolder);
    }
    
    private Runnable getOnlyUpdater() throws Exception {
        Object updater = getUpdateTaskMap().values().iterator().next();
        return (Runnable) updater;
    }
    
    private MockAgentSpecEventSubscriber registerMockSubscriber() {
        MockAgentSpecEventSubscriber subscriber = new MockAgentSpecEventSubscriber(specName);
        NotifyCenter.registerSubscriber(subscriber);
        registeredSubscribers.add(subscriber);
        return subscriber;
    }
    
    private static class MockAgentSpecEventSubscriber
        extends Subscriber<AgentSpecChangedEvent> {
        
        private final String expectedAgentSpecName;
        private final AtomicBoolean invokedMark = new AtomicBoolean(false);
        private volatile CountDownLatch latch = new CountDownLatch(1);
        
        MockAgentSpecEventSubscriber(String expectedAgentSpecName) {
            this.expectedAgentSpecName = expectedAgentSpecName;
        }
        
        @Override
        public void onEvent(AgentSpecChangedEvent event) {
            if (!expectedAgentSpecName.equals(event.getAgentSpecName())) {
                return;
            }
            invokedMark.set(true);
            latch.countDown();
        }
        
        @Override
        public Class<? extends Event> subscribeType() {
            return AgentSpecChangedEvent.class;
        }
        
        boolean await(long timeoutMs) throws InterruptedException {
            return latch.await(timeoutMs, TimeUnit.MILLISECONDS);
        }
        
        void reset() {
            invokedMark.set(false);
            latch = new CountDownLatch(1);
        }
    }
}
