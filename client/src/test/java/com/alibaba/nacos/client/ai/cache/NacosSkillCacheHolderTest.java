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
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.ai.event.SkillChangedEvent;
import com.alibaba.nacos.client.ai.remote.AiClientProxy;
import com.alibaba.nacos.client.ai.remote.SkillQueryResponse;
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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
class NacosSkillCacheHolderTest {
    
    @Mock
    private AiClientProxy aiClientProxy;
    
    private NacosSkillCacheHolder cacheHolder;
    
    private final List<MockSkillEventSubscriber> registeredSubscribers = new ArrayList<>();
    
    @BeforeEach
    void setUp() {
        Properties properties = new Properties();
        properties.put(AiConstants.AI_SKILL_CACHE_UPDATE_INTERVAL, "60000");
        NotifyCenter.registerToPublisher(SkillChangedEvent.class, 16384);
        cacheHolder = new NacosSkillCacheHolder(aiClientProxy,
            NacosClientProperties.PROTOTYPE.derive(properties));
    }
    
    @AfterEach
    void tearDown() throws NacosException {
        for (MockSkillEventSubscriber each : registeredSubscribers) {
            NotifyCenter.deregisterSubscriber(each);
        }
        registeredSubscribers.clear();
        cacheHolder.shutdown();
        NotifyCenter.deregisterPublisher(SkillChangedEvent.class);
    }
    
    @Test
    void subscribeSkillShouldReturnNullAndScheduleWhenNotFound() throws Exception {
        when(aiClientProxy.querySkill("s1", "1.0.0", null, null))
            .thenThrow(new NacosException(NacosException.NOT_FOUND, "not found"));
        
        byte[] result = cacheHolder.subscribeSkill("s1", "1.0.0", null);
        
        assertNull(result);
        assertEquals(1, getUpdateTaskMap().size());
    }
    
    @Test
    void subscribeSkillShouldCacheButNotPublishEventWhenFound() throws Exception {
        byte[] zipBytes = new byte[] {0x50, 0x4B, 0x03, 0x04};
        SkillQueryResponse response = new SkillQueryResponse(zipBytes, "m1", "1.0.0");
        when(aiClientProxy.querySkill("s1", "1.0.0", null, null)).thenReturn(response);
        MockSkillEventSubscriber subscriber = registerMockSubscriber();
        
        byte[] result = cacheHolder.subscribeSkill("s1", "1.0.0", null);
        
        assertArrayEquals(zipBytes, result);
        assertEquals("m1", getMd5Cache().get("s1::version:1.0.0"));
        // Initial subscribe must NOT publish event; the caller (NacosAiService)
        // is responsible for the first listener notification to avoid double-invocation.
        assertFalse(subscriber.await(200),
            "Initial subscribe should not publish event via NotifyCenter");
        assertFalse(subscriber.invokedMark.get(), "Subscriber should not be invoked");
    }
    
    @Test
    void subscribeSkillShouldThrowWhenSkillNameBlank() {
        assertThrows(NacosException.class,
            () -> cacheHolder.subscribeSkill("", "1.0.0", null));
    }
    
    @Test
    void updaterShouldIgnoreWhenNotModified() throws Exception {
        byte[] zipBytes = new byte[] {0x50, 0x4B};
        SkillQueryResponse response = new SkillQueryResponse(zipBytes, "m1", "1.0.0");
        when(aiClientProxy.querySkill("s1", "1.0.0", null, null)).thenReturn(response);
        when(aiClientProxy.querySkill("s1", "1.0.0", null, "m1"))
            .thenThrow(new NacosException(NacosException.NOT_MODIFIED, "up to date"));
        cacheHolder.subscribeSkill("s1", "1.0.0", null);
        MockSkillEventSubscriber subscriber = registerMockSubscriber();
        
        Runnable updater = getOnlyUpdater();
        updater.run();
        
        assertEquals("m1", getMd5Cache().get("s1::version:1.0.0"));
        assertFalse(subscriber.await(200), "Not modified skill should not publish event");
        assertFalse(subscriber.invokedMark.get(), "Subscriber should not be invoked");
    }
    
    @Test
    void updaterShouldEvictAndPublishNullEventWhenNotFound() throws Exception {
        byte[] zipBytes = new byte[] {0x50, 0x4B};
        SkillQueryResponse response = new SkillQueryResponse(zipBytes, "m1", "1.0.0");
        when(aiClientProxy.querySkill("s1", "1.0.0", null, null)).thenReturn(response);
        when(aiClientProxy.querySkill("s1", "1.0.0", null, "m1"))
            .thenThrow(new NacosException(NacosException.NOT_FOUND, "not found"));
        cacheHolder.subscribeSkill("s1", "1.0.0", null);
        MockSkillEventSubscriber subscriber = registerMockSubscriber();
        
        Runnable updater = getOnlyUpdater();
        updater.run();
        
        assertNull(getMd5Cache().get("s1::version:1.0.0"));
        assertFalse(subscriber.await(200),
            "Not found should not trigger an event when response is null");
    }
    
    @Test
    void updaterShouldPublishEventWhenMd5Changed() throws Exception {
        byte[] zip1 = new byte[] {0x01};
        byte[] zip2 = new byte[] {0x02};
        SkillQueryResponse first = new SkillQueryResponse(zip1, "m1", "1.0.0");
        SkillQueryResponse second = new SkillQueryResponse(zip2, "m2", "1.0.0");
        when(aiClientProxy.querySkill("s1", "1.0.0", null, null)).thenReturn(first);
        when(aiClientProxy.querySkill("s1", "1.0.0", null, "m1")).thenReturn(second);
        cacheHolder.subscribeSkill("s1", "1.0.0", null);
        MockSkillEventSubscriber subscriber = registerMockSubscriber();
        
        Runnable updater = getOnlyUpdater();
        updater.run();
        
        assertEquals("m2", getMd5Cache().get("s1::version:1.0.0"));
        assertTrue(subscriber.await(5000), "Changed skill should publish event");
        assertTrue(subscriber.invokedMark.get());
    }
    
    @Test
    void unsubscribeSkillShouldCancelTaskAndRemoveCache() throws Exception {
        SkillQueryResponse response =
            new SkillQueryResponse(new byte[] {0x01}, "m1", "1.0.0");
        when(aiClientProxy.querySkill("s1", "1.0.0", null, null)).thenReturn(response);
        cacheHolder.subscribeSkill("s1", "1.0.0", null);
        
        cacheHolder.unsubscribeSkill("s1", "1.0.0", null);
        
        assertTrue(getUpdateTaskMap().isEmpty());
        assertNull(getMd5Cache().get("s1::version:1.0.0"));
        verify(aiClientProxy, never()).querySkill("s1", null, null, null);
    }
    
    @Test
    void subscribeSkillShouldThrowWhenUnexpectedException() throws Exception {
        when(aiClientProxy.querySkill("s1", "1.0.0", null, null))
            .thenThrow(new NacosException(NacosException.SERVER_ERROR, "server error"));
        
        assertThrows(NacosException.class,
            () -> cacheHolder.subscribeSkill("s1", "1.0.0", null));
    }
    
    @Test
    void updaterShouldIgnoreGeneralExceptionAndKeepCache() throws Exception {
        SkillQueryResponse response =
            new SkillQueryResponse(new byte[] {0x01}, "m1", "1.0.0");
        when(aiClientProxy.querySkill("s1", "1.0.0", null, null)).thenReturn(response);
        when(aiClientProxy.querySkill("s1", "1.0.0", null, "m1"))
            .thenThrow(new NacosException(NacosException.SERVER_ERROR, "server error"));
        cacheHolder.subscribeSkill("s1", "1.0.0", null);
        
        Runnable updater = getOnlyUpdater();
        updater.run();
        
        assertNotNull(getMd5Cache().get("s1::version:1.0.0"));
        assertEquals(1, getUpdateTaskMap().size());
    }
    
    @SuppressWarnings("unchecked")
    private Map<String, String> getMd5Cache() throws Exception {
        Field field = NacosSkillCacheHolder.class.getDeclaredField("skillMd5Cache");
        field.setAccessible(true);
        return (Map<String, String>) field.get(cacheHolder);
    }
    
    @SuppressWarnings("unchecked")
    private Map<String, Object> getUpdateTaskMap() throws Exception {
        Field field = NacosSkillCacheHolder.class.getDeclaredField("updateTaskMap");
        field.setAccessible(true);
        return (Map<String, Object>) field.get(cacheHolder);
    }
    
    private Runnable getOnlyUpdater() throws Exception {
        Object updater = getUpdateTaskMap().values().iterator().next();
        return (Runnable) updater;
    }
    
    private MockSkillEventSubscriber registerMockSubscriber() {
        MockSkillEventSubscriber subscriber = new MockSkillEventSubscriber();
        NotifyCenter.registerSubscriber(subscriber);
        registeredSubscribers.add(subscriber);
        return subscriber;
    }
    
    private static class MockSkillEventSubscriber extends Subscriber<SkillChangedEvent> {
        
        private final AtomicBoolean invokedMark = new AtomicBoolean(false);
        private volatile CountDownLatch latch = new CountDownLatch(1);
        
        @Override
        public void onEvent(SkillChangedEvent event) {
            invokedMark.set(true);
            latch.countDown();
        }
        
        @Override
        public Class<? extends Event> subscribeType() {
            return SkillChangedEvent.class;
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
