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
import com.alibaba.nacos.api.ai.model.prompt.Prompt;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.ai.event.PromptChangedEvent;
import com.alibaba.nacos.client.ai.remote.AiClientProxy;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.notify.listener.Subscriber;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NacosPromptCacheHolderTest {
    
    @Mock
    private AiClientProxy aiClientProxy;
    
    private NacosPromptCacheHolder cacheHolder;
    
    private final List<MockPromptEventSubscriber> registeredSubscribers = new ArrayList<>();
    
    @BeforeEach
    void setUp() {
        Properties properties = new Properties();
        properties.put(AiConstants.AI_PROMPT_CACHE_UPDATE_INTERVAL, "100");
        NotifyCenter.registerToPublisher(PromptChangedEvent.class, 16384);
        cacheHolder = new NacosPromptCacheHolder(aiClientProxy,
            NacosClientProperties.PROTOTYPE.derive(properties));
    }
    
    @AfterEach
    void tearDown() throws NacosException {
        for (MockPromptEventSubscriber each : registeredSubscribers) {
            NotifyCenter.deregisterSubscriber(each);
        }
        registeredSubscribers.clear();
        cacheHolder.shutdown();
        NotifyCenter.deregisterPublisher(PromptChangedEvent.class);
    }
    
    @Test
    void subscribePromptShouldReturnNullAndScheduleWhenNotFound() throws Exception {
        when(aiClientProxy.queryPrompt("p1", "1.0.0", null, null))
            .thenThrow(new NacosException(NacosException.NOT_FOUND, "not found"));
        
        Prompt result = cacheHolder.subscribePrompt("p1", "1.0.0", null);
        
        assertNull(result);
        assertEquals(1, getUpdateTaskMap().size());
    }
    
    @Test
    void subscribePromptShouldCacheAndPublishEventWhenFound() throws Exception {
        Prompt prompt = new Prompt("p1", "1.0.0", "v1");
        prompt.setMd5("m1");
        when(aiClientProxy.queryPrompt("p1", "1.0.0", null, null)).thenReturn(prompt);
        MockPromptEventSubscriber subscriber = registerMockSubscriber();
        
        cacheHolder.subscribePrompt("p1", "1.0.0", null);
        
        assertNotNull(getPromptCache().get("p1::version:1.0.0"));
        assertTrue(subscriber.await(5000),
            "Event should be received by subscriber within 5 seconds");
        assertTrue(subscriber.invokedMark.get(), "Subscriber should have been invoked");
    }
    
    @Test
    void updaterShouldIgnoreWhenNotModified() throws Exception {
        Prompt prompt = new Prompt("p1", "1.0.0", "v1");
        prompt.setMd5("m1");
        when(aiClientProxy.queryPrompt("p1", "1.0.0", null, null)).thenReturn(prompt);
        when(aiClientProxy.queryPrompt("p1", "1.0.0", null, "m1"))
            .thenThrow(new NacosException(NacosException.NOT_MODIFIED, "up to date"));
        MockPromptEventSubscriber subscriber = registerMockSubscriber();
        cacheHolder.subscribePrompt("p1", "1.0.0", null);
        assertTrue(subscriber.await(5000),
            "Initial event should be received by subscriber within 5 seconds");
        subscriber.reset();
        
        Runnable updater = getOnlyUpdater();
        updater.run();
        
        assertEquals("v1", getPromptCache().get("p1::version:1.0.0").getTemplate());
        assertFalse(subscriber.await(200), "Not modified prompt should not publish event");
        assertFalse(subscriber.invokedMark.get(), "Subscriber should not be invoked");
    }
    
    @Test
    void updaterShouldEvictAndPublishNullEventWhenNotFound() throws Exception {
        Prompt prompt = new Prompt("p1", "1.0.0", "v1");
        prompt.setMd5("m1");
        when(aiClientProxy.queryPrompt("p1", "1.0.0", null, null)).thenReturn(prompt);
        when(aiClientProxy.queryPrompt("p1", "1.0.0", null, "m1"))
            .thenThrow(new NacosException(NacosException.NOT_FOUND, "not found"));
        cacheHolder.subscribePrompt("p1", "1.0.0", null);
        MockPromptEventSubscriber subscriber = registerMockSubscriber();
        
        Runnable updater = getOnlyUpdater();
        updater.run();
        
        assertNull(getPromptCache().get("p1::version:1.0.0"));
        assertTrue(subscriber.await(5000),
            "Null event should be received by subscriber within 5 seconds");
        assertTrue(subscriber.invokedMark.get());
    }
    
    @Test
    void unsubscribePromptShouldCancelTaskAndRemoveCache() throws Exception {
        Prompt prompt = new Prompt("p1", "1.0.0", "v1");
        when(aiClientProxy.queryPrompt("p1", "1.0.0", null, null)).thenReturn(prompt);
        cacheHolder.subscribePrompt("p1", "1.0.0", null);
        
        cacheHolder.unsubscribePrompt("p1", "1.0.0", null);
        
        assertTrue(getUpdateTaskMap().isEmpty());
        assertTrue(getPromptCache().isEmpty());
        verify(aiClientProxy, never()).queryPrompt("p1", null, null, null);
    }
    
    @Test
    void subscribePromptShouldThrowWhenUnexpectedException() throws Exception {
        when(aiClientProxy.queryPrompt("p1", "1.0.0", null, null))
            .thenThrow(new NacosException(NacosException.SERVER_ERROR, "server error"));
        
        org.junit.jupiter.api.Assertions.assertThrows(NacosException.class,
            () -> cacheHolder.subscribePrompt("p1", "1.0.0", null));
    }
    
    @Test
    void updaterShouldIgnoreGeneralExceptionAndKeepCache() throws Exception {
        Prompt prompt = new Prompt("p1", "1.0.0", "v1");
        prompt.setMd5("m1");
        when(aiClientProxy.queryPrompt("p1", "1.0.0", null, null)).thenReturn(prompt);
        when(aiClientProxy.queryPrompt("p1", "1.0.0", null, "m1"))
            .thenThrow(new NacosException(NacosException.SERVER_ERROR, "server error"));
        cacheHolder.subscribePrompt("p1", "1.0.0", null);
        
        Runnable updater = getOnlyUpdater();
        updater.run();
        
        assertNotNull(getPromptCache().get("p1::version:1.0.0"));
        assertEquals(1, getUpdateTaskMap().size());
    }
    
    @SuppressWarnings("unchecked")
    private Map<String, Prompt> getPromptCache() throws Exception {
        Field field = NacosPromptCacheHolder.class.getDeclaredField("promptCache");
        field.setAccessible(true);
        return (Map<String, Prompt>) field.get(cacheHolder);
    }
    
    @SuppressWarnings("unchecked")
    private Map<String, Object> getUpdateTaskMap() throws Exception {
        Field field = NacosPromptCacheHolder.class.getDeclaredField("updateTaskMap");
        field.setAccessible(true);
        return (Map<String, Object>) field.get(cacheHolder);
    }
    
    private Runnable getOnlyUpdater() throws Exception {
        Object updater = getUpdateTaskMap().values().iterator().next();
        return (Runnable) updater;
    }
    
    private MockPromptEventSubscriber registerMockSubscriber() {
        MockPromptEventSubscriber subscriber = new MockPromptEventSubscriber();
        NotifyCenter.registerSubscriber(subscriber);
        registeredSubscribers.add(subscriber);
        return subscriber;
    }
    
    private static class MockPromptEventSubscriber extends Subscriber<PromptChangedEvent> {
        
        private final AtomicBoolean invokedMark = new AtomicBoolean(false);
        private volatile CountDownLatch latch = new CountDownLatch(1);
        
        @Override
        public void onEvent(PromptChangedEvent event) {
            invokedMark.set(true);
            latch.countDown();
        }
        
        @Override
        public Class<? extends Event> subscribeType() {
            return PromptChangedEvent.class;
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
