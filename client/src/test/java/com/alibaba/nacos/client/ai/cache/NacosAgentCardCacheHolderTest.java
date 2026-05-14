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

import com.alibaba.nacos.api.ai.model.a2a.AgentCardDetailInfo;
import com.alibaba.nacos.api.ai.model.a2a.AgentInterface;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.ai.event.AgentCardChangedEvent;
import com.alibaba.nacos.client.ai.remote.AiGrpcClient;
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
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NacosAgentCardCacheHolderTest {
    
    @Mock
    private AiGrpcClient aiGrpcClient;
    
    private NacosAgentCardCacheHolder cacheHolder;
    
    private TestAgentCardSubscriber subscriber;
    
    @BeforeEach
    void setUp() {
        NacosClientProperties properties = NacosClientProperties.PROTOTYPE.derive();
        cacheHolder = new NacosAgentCardCacheHolder(aiGrpcClient, properties);
        subscriber = new TestAgentCardSubscriber();
        NotifyCenter.registerSubscriber(subscriber);
    }
    
    @AfterEach
    void tearDown() throws Exception {
        NotifyCenter.deregisterSubscriber(subscriber);
        cacheHolder.shutdown();
    }
    
    @Test
    void testProcessNewAgentCardShouldPublishEvent() throws InterruptedException {
        AgentCardDetailInfo detailInfo = buildDetailInfo("test-agent", "1.0", true);
        cacheHolder.processAgentCardDetailInfo(detailInfo);
        assertTrue(subscriber.latch.await(3, TimeUnit.SECONDS));
        assertNotNull(subscriber.lastEvent.get());
        assertEquals("test-agent", subscriber.lastEvent.get().getAgentName());
    }
    
    @Test
    void testProcessSameAgentCardShouldNotPublishEvent() throws InterruptedException {
        CountingAgentCardSubscriber countingSubscriber = new CountingAgentCardSubscriber();
        NotifyCenter.registerSubscriber(countingSubscriber);
        try {
            AgentCardDetailInfo first = buildDetailInfo("test-agent", "1.0", true);
            first.setSupportedInterfaces(
                Collections.singletonList(buildInterface("http://a", "jsonrpc", "1.0")));
            cacheHolder.processAgentCardDetailInfo(first);
            assertTrue(countingSubscriber.firstLatch.await(3, TimeUnit.SECONDS));
            assertEquals(1, countingSubscriber.eventCount.get());
            
            AgentCardDetailInfo second = buildDetailInfo("test-agent", "1.0", true);
            second.setSupportedInterfaces(
                Collections.singletonList(buildInterface("http://a", "jsonrpc", "1.0")));
            cacheHolder.processAgentCardDetailInfo(second);
            Thread.sleep(500);
            assertEquals(1, countingSubscriber.eventCount.get(),
                "Should NOT publish event for identical agent card");
        } finally {
            NotifyCenter.deregisterSubscriber(countingSubscriber);
        }
    }
    
    @Test
    void testVersionChangeShouldPublishEvent() throws InterruptedException {
        AgentCardDetailInfo first = buildDetailInfo("test-agent", "1.0", true);
        cacheHolder.processAgentCardDetailInfo(first);
        assertTrue(subscriber.latch.await(3, TimeUnit.SECONDS));
        
        TestAgentCardSubscriber secondSubscriber = new TestAgentCardSubscriber();
        NotifyCenter.registerSubscriber(secondSubscriber);
        try {
            AgentCardDetailInfo second = buildDetailInfo("test-agent", "2.0", true);
            cacheHolder.processAgentCardDetailInfo(second);
            assertTrue(secondSubscriber.latch.await(3, TimeUnit.SECONDS));
            assertNotNull(secondSubscriber.lastEvent.get());
        } finally {
            NotifyCenter.deregisterSubscriber(secondSubscriber);
        }
    }
    
    @Test
    void testSupportedInterfacesChangeShouldPublishEvent() throws InterruptedException {
        AgentCardDetailInfo first = buildDetailInfo("test-agent", "1.0", true);
        first.setSupportedInterfaces(
            Collections.singletonList(buildInterface("http://a", "jsonrpc", "1.0")));
        cacheHolder.processAgentCardDetailInfo(first);
        assertTrue(subscriber.latch.await(3, TimeUnit.SECONDS));
        
        TestAgentCardSubscriber secondSubscriber = new TestAgentCardSubscriber();
        NotifyCenter.registerSubscriber(secondSubscriber);
        try {
            AgentCardDetailInfo second = buildDetailInfo("test-agent", "1.0", true);
            second.setSupportedInterfaces(
                Collections.singletonList(buildInterface("http://b", "jsonrpc", "1.0")));
            cacheHolder.processAgentCardDetailInfo(second);
            assertTrue(secondSubscriber.latch.await(3, TimeUnit.SECONDS));
            assertNotNull(secondSubscriber.lastEvent.get());
        } finally {
            NotifyCenter.deregisterSubscriber(secondSubscriber);
        }
    }
    
    @Test
    void testSupportedInterfacesAppearsShouldPublishEvent() throws InterruptedException {
        AgentCardDetailInfo first = buildDetailInfo("test-agent", "1.0", true);
        first.setUrl("http://a");
        cacheHolder.processAgentCardDetailInfo(first);
        assertTrue(subscriber.latch.await(3, TimeUnit.SECONDS));
        
        TestAgentCardSubscriber secondSubscriber = new TestAgentCardSubscriber();
        NotifyCenter.registerSubscriber(secondSubscriber);
        try {
            AgentCardDetailInfo second = buildDetailInfo("test-agent", "1.0", true);
            second.setSupportedInterfaces(
                Collections.singletonList(buildInterface("http://a", "jsonrpc", "1.0")));
            cacheHolder.processAgentCardDetailInfo(second);
            assertTrue(secondSubscriber.latch.await(3, TimeUnit.SECONDS));
            assertNotNull(secondSubscriber.lastEvent.get());
        } finally {
            NotifyCenter.deregisterSubscriber(secondSubscriber);
        }
    }
    
    @Test
    void testLegacyUrlChangeShouldPublishEvent() throws InterruptedException {
        AgentCardDetailInfo first = buildDetailInfo("test-agent", "1.0", true);
        first.setUrl("http://old");
        cacheHolder.processAgentCardDetailInfo(first);
        assertTrue(subscriber.latch.await(3, TimeUnit.SECONDS));
        
        TestAgentCardSubscriber secondSubscriber = new TestAgentCardSubscriber();
        NotifyCenter.registerSubscriber(secondSubscriber);
        try {
            AgentCardDetailInfo second = buildDetailInfo("test-agent", "1.0", true);
            second.setUrl("http://new");
            cacheHolder.processAgentCardDetailInfo(second);
            assertTrue(secondSubscriber.latch.await(3, TimeUnit.SECONDS));
            assertNotNull(secondSubscriber.lastEvent.get());
        } finally {
            NotifyCenter.deregisterSubscriber(secondSubscriber);
        }
    }
    
    @Test
    void testLegacyAdditionalInterfacesChangeShouldPublishEvent() throws InterruptedException {
        AgentCardDetailInfo first = buildDetailInfo("test-agent", "1.0", true);
        first.setUrl("http://a");
        first.setAdditionalInterfaces(
            Collections.singletonList(buildInterface("http://b", "jsonrpc", "1.0")));
        cacheHolder.processAgentCardDetailInfo(first);
        assertTrue(subscriber.latch.await(3, TimeUnit.SECONDS));
        
        TestAgentCardSubscriber secondSubscriber = new TestAgentCardSubscriber();
        NotifyCenter.registerSubscriber(secondSubscriber);
        try {
            AgentCardDetailInfo second = buildDetailInfo("test-agent", "1.0", true);
            second.setUrl("http://a");
            second.setAdditionalInterfaces(
                Arrays.asList(buildInterface("http://b", "jsonrpc", "1.0"),
                    buildInterface("http://c", "jsonrpc", "1.0")));
            cacheHolder.processAgentCardDetailInfo(second);
            assertTrue(secondSubscriber.latch.await(3, TimeUnit.SECONDS));
            assertNotNull(secondSubscriber.lastEvent.get());
        } finally {
            NotifyCenter.deregisterSubscriber(secondSubscriber);
        }
    }
    
    @Test
    void testGetAgentCardFromCache() {
        assertNull(cacheHolder.getAgentCard("test-agent", "1.0"));
        AgentCardDetailInfo detailInfo = buildDetailInfo("test-agent", "1.0", true);
        cacheHolder.processAgentCardDetailInfo(detailInfo);
        assertNotNull(cacheHolder.getAgentCard("test-agent", "1.0"));
        assertNotNull(cacheHolder.getAgentCard("test-agent", null));
    }
    
    @Test
    void testGetAgentCardNotLatestShouldNotCacheLatest() {
        AgentCardDetailInfo detailInfo = buildDetailInfo("test-agent", "1.0", false);
        cacheHolder.processAgentCardDetailInfo(detailInfo);
        assertNotNull(cacheHolder.getAgentCard("test-agent", "1.0"));
        assertNull(cacheHolder.getAgentCard("test-agent", null));
    }
    
    @SuppressWarnings("unchecked")
    private static <T> T readField(Object target, String name) throws Exception {
        Field field = findField(target.getClass(), name);
        field.setAccessible(true);
        return (T) field.get(target);
    }
    
    private static Field findField(Class<?> clazz, String name) throws NoSuchFieldException {
        Class<?> c = clazz;
        while (c != null) {
            for (Field f : c.getDeclaredFields()) {
                if (f.getName().equals(name)) {
                    return f;
                }
            }
            c = c.getSuperclass();
        }
        throw new NoSuchFieldException(name);
    }
    
    @Test
    void testAddAndRemoveAgentCardUpdateTask() throws Exception {
        cacheHolder.addAgentCardUpdateTask("test-agent", "1.0");
        Map<String, ?> taskMap = readField(cacheHolder, "updateTaskMap");
        assertNotNull(taskMap);
        assertEquals(1, taskMap.size());
        // adding same key is a no-op (computeIfAbsent)
        cacheHolder.addAgentCardUpdateTask("test-agent", "1.0");
        assertEquals(1, taskMap.size());
        cacheHolder.removeAgentCardUpdateTask("test-agent", "1.0");
        assertEquals(0, taskMap.size());
    }
    
    @Test
    void testRemoveAgentCardUpdateTaskNonExistentNoOp() throws Exception {
        // No task registered → remove should be a no-op
        cacheHolder.removeAgentCardUpdateTask("non-existent", "1.0");
        Map<String, ?> taskMap = readField(cacheHolder, "updateTaskMap");
        assertEquals(0, taskMap.size());
    }
    
    @Test
    void testAgentCardUpdaterRunFetchesAndProcesses() throws Exception {
        AgentCardDetailInfo detailInfo = buildDetailInfo("test-agent", "1.0", true);
        when(aiGrpcClient.getAgentCard(anyString(), anyString(), anyString()))
            .thenReturn(detailInfo);
        Runnable updater = newUpdater("test-agent", "1.0");
        updater.run();
        // After run, the detail info should be in the cache
        assertNotNull(cacheHolder.getAgentCard("test-agent", "1.0"));
    }
    
    @Test
    void testAgentCardUpdaterRunSwallowsNotFound() throws Exception {
        when(aiGrpcClient.getAgentCard(anyString(), anyString(), anyString()))
            .thenThrow(new NacosException(NacosException.NOT_FOUND, "missing"));
        Runnable updater = newUpdater("test-agent", "1.0");
        updater.run();
        // Should not throw and the cache stays empty
        assertNull(cacheHolder.getAgentCard("test-agent", "1.0"));
    }
    
    @Test
    void testAgentCardUpdaterRunSwallowsOtherException() throws Exception {
        when(aiGrpcClient.getAgentCard(anyString(), anyString(), anyString()))
            .thenThrow(new RuntimeException("boom"));
        Runnable updater = newUpdater("test-agent", "1.0");
        // Should not throw, caught and rescheduled
        updater.run();
    }
    
    @Test
    void testAgentCardUpdaterCancelExitsEarly() throws Exception {
        Runnable updater = newUpdater("test-agent", "1.0");
        AtomicBoolean cancel = readField(updater, "cancel");
        cancel.set(true);
        // Should not call aiGrpcClient at all because cancel=true
        updater.run();
        org.mockito.Mockito.verifyNoInteractions(aiGrpcClient);
    }
    
    @Test
    void testAgentCardUpdaterCancelMethod() throws Exception {
        Runnable updater = newUpdater("test-agent", "1.0");
        AtomicBoolean cancel = readField(updater, "cancel");
        assertFalse(cancel.get());
        Method cancelMethod = updater.getClass().getDeclaredMethod("cancel");
        cancelMethod.setAccessible(true);
        cancelMethod.invoke(updater);
        assertTrue(cancel.get());
    }
    
    private Runnable newUpdater(String agentName, String version) throws Exception {
        Class<?> updaterClass = Class.forName(
            "com.alibaba.nacos.client.ai.cache.NacosAgentCardCacheHolder$AgentCardUpdater");
        java.lang.reflect.Constructor<?> ctor = updaterClass.getDeclaredConstructor(
            NacosAgentCardCacheHolder.class, String.class, String.class);
        ctor.setAccessible(true);
        return (Runnable) ctor.newInstance(cacheHolder, agentName, version);
    }
    
    private AgentCardDetailInfo buildDetailInfo(String name, String version, boolean isLatest) {
        AgentCardDetailInfo detail = new AgentCardDetailInfo();
        detail.setName(name);
        detail.setVersion(version);
        detail.setLatestVersion(isLatest);
        return detail;
    }
    
    private AgentInterface buildInterface(String url, String protocolBinding,
        String protocolVersion) {
        AgentInterface iface = new AgentInterface();
        iface.setUrl(url);
        iface.setProtocolBinding(protocolBinding);
        iface.setProtocolVersion(protocolVersion);
        return iface;
    }
    
    private static class TestAgentCardSubscriber extends Subscriber<AgentCardChangedEvent> {
        
        final CountDownLatch latch = new CountDownLatch(1);
        
        final AtomicReference<AgentCardChangedEvent> lastEvent = new AtomicReference<>();
        
        @Override
        public void onEvent(AgentCardChangedEvent event) {
            lastEvent.set(event);
            latch.countDown();
        }
        
        @Override
        public Class<? extends Event> subscribeType() {
            return AgentCardChangedEvent.class;
        }
    }
    
    private static class CountingAgentCardSubscriber extends Subscriber<AgentCardChangedEvent> {
        
        final AtomicInteger eventCount = new AtomicInteger(0);
        
        final CountDownLatch firstLatch = new CountDownLatch(1);
        
        @Override
        public void onEvent(AgentCardChangedEvent event) {
            eventCount.incrementAndGet();
            firstLatch.countDown();
        }
        
        @Override
        public Class<? extends Event> subscribeType() {
            return AgentCardChangedEvent.class;
        }
    }
}
