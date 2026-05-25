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

package com.alibaba.nacos.naming.monitor.collector;

import com.alibaba.nacos.common.notify.EventPublisher;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.naming.core.v2.client.Client;
import com.alibaba.nacos.naming.core.v2.client.manager.impl.ConnectionBasedClientManager;
import com.alibaba.nacos.naming.core.v2.client.manager.impl.EphemeralIpPortClientManager;
import com.alibaba.nacos.naming.core.v2.client.manager.impl.PersistentIpPortClientManager;
import com.alibaba.nacos.naming.core.v2.event.service.ServiceEvent;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.monitor.MetricsMonitor;
import com.alibaba.nacos.naming.push.v2.NamingSubscriberServiceV2Impl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(MockitoExtension.class)
class NamingMetricsCollectorTest {
    
    private ScheduledExecutorService originalSubAndPubExecutor;
    
    private ScheduledExecutorService originalServiceEventQueueExecutor;
    
    private ScheduledExecutorService originalPushPendingTaskExecutor;
    
    private EventPublisher originalSubscribedPublisher;
    
    private EventPublisher originalChangedPublisher;
    
    @AfterEach
    void tearDown() {
        if (originalSubAndPubExecutor != null) {
            ReflectionTestUtils.setField(NamingSubAndPubMetricsCollector.class, "executorService",
                originalSubAndPubExecutor);
        }
        if (originalServiceEventQueueExecutor != null) {
            ReflectionTestUtils.setField(ServiceEventQueueSizeMetricsCollector.class,
                "executorService",
                originalServiceEventQueueExecutor);
        }
        if (originalPushPendingTaskExecutor != null) {
            ReflectionTestUtils.setField(PushPendingTaskCountMetricsCollector.class,
                "executorService",
                originalPushPendingTaskExecutor);
        }
        restorePublisher(ServiceEvent.ServiceSubscribedEvent.class, originalSubscribedPublisher);
        restorePublisher(ServiceEvent.ServiceChangedEvent.class, originalChangedPublisher);
        MetricsMonitor.getNamingSubscriber("v1").set(0);
        MetricsMonitor.getNamingPublisher("v1").set(0);
        MetricsMonitor.getNamingSubscriber("v2").set(0);
        MetricsMonitor.getNamingPublisher("v2").set(0);
        MetricsMonitor.getServiceSubscribedEventQueueSize().set(0);
        MetricsMonitor.getServiceChangedEventQueueSize().set(0);
        MetricsMonitor.getPushPendingTaskCount().set(0);
    }
    
    @Test
    void testNamingSubAndPubMetricsCollectorCollectsClientMetrics() throws Exception {
        ScheduledExecutorService executorService = Mockito.mock(ScheduledExecutorService.class);
        AtomicReference<Runnable> scheduledTask = mockScheduleWithFixedDelay(executorService);
        originalSubAndPubExecutor =
            (ScheduledExecutorService) ReflectionTestUtils.getField(
                NamingSubAndPubMetricsCollector.class,
                "executorService");
        runNoopOnOriginalExecutor(originalSubAndPubExecutor);
        ReflectionTestUtils.setField(NamingSubAndPubMetricsCollector.class, "executorService",
            executorService);
        
        EphemeralIpPortClientManager ephemeralManager =
            Mockito.mock(EphemeralIpPortClientManager.class);
        PersistentIpPortClientManager persistentManager =
            Mockito.mock(PersistentIpPortClientManager.class);
        ConnectionBasedClientManager connectionManager =
            Mockito.mock(ConnectionBasedClientManager.class);
        Client ephemeralClient = mockClient(2, 1);
        Client persistentClient = mockClient(1, 2);
        Client connectionClient = mockClient(3, 4);
        Mockito.when(ephemeralManager.allClientId())
            .thenReturn(Arrays.asList("ephemeral", "missing"));
        Mockito.when(ephemeralManager.getClient("ephemeral")).thenReturn(ephemeralClient);
        Mockito.when(persistentManager.allClientId())
            .thenReturn(Collections.singletonList("persistent"));
        Mockito.when(persistentManager.getClient("persistent")).thenReturn(persistentClient);
        Mockito.when(connectionManager.allClientId())
            .thenReturn(Collections.singletonList("connection"));
        Mockito.when(connectionManager.getClient("connection")).thenReturn(connectionClient);
        
        new NamingSubAndPubMetricsCollector(connectionManager, ephemeralManager, persistentManager);
        assertNotNull(scheduledTask.get());
        scheduledTask.get().run();
        
        assertEquals(3, MetricsMonitor.getNamingPublisher("v1").get());
        assertEquals(3, MetricsMonitor.getNamingSubscriber("v1").get());
        assertEquals(3, MetricsMonitor.getNamingPublisher("v2").get());
        assertEquals(4, MetricsMonitor.getNamingSubscriber("v2").get());
        Mockito.verify(executorService)
            .scheduleWithFixedDelay(Mockito.any(Runnable.class), Mockito.eq(5L), Mockito.eq(5L),
                Mockito.eq(TimeUnit.SECONDS));
    }
    
    @Test
    void testServiceEventQueueSizeMetricsCollectorCollectsPublisherQueueSize() throws Exception {
        ScheduledExecutorService executorService = Mockito.mock(ScheduledExecutorService.class);
        AtomicReference<Runnable> scheduledTask = mockScheduleWithFixedDelay(executorService);
        originalServiceEventQueueExecutor =
            (ScheduledExecutorService) ReflectionTestUtils.getField(
                ServiceEventQueueSizeMetricsCollector.class,
                "executorService");
        runNoopOnOriginalExecutor(originalServiceEventQueueExecutor);
        ReflectionTestUtils.setField(ServiceEventQueueSizeMetricsCollector.class, "executorService",
            executorService);
        EventPublisher subscribedPublisher = Mockito.mock(EventPublisher.class);
        EventPublisher changedPublisher = Mockito.mock(EventPublisher.class);
        Mockito.when(subscribedPublisher.currentEventSize()).thenReturn(7L);
        Mockito.when(changedPublisher.currentEventSize()).thenReturn(11L);
        replacePublisher(ServiceEvent.ServiceSubscribedEvent.class, subscribedPublisher);
        replacePublisher(ServiceEvent.ServiceChangedEvent.class, changedPublisher);
        
        new ServiceEventQueueSizeMetricsCollector();
        assertNotNull(scheduledTask.get());
        scheduledTask.get().run();
        
        assertEquals(7, MetricsMonitor.getServiceSubscribedEventQueueSize().get());
        assertEquals(11, MetricsMonitor.getServiceChangedEventQueueSize().get());
        Mockito.verify(executorService)
            .scheduleWithFixedDelay(Mockito.any(Runnable.class), Mockito.eq(2L), Mockito.eq(2L),
                Mockito.eq(TimeUnit.SECONDS));
    }
    
    @Test
    void testPushPendingTaskCountMetricsCollectorCollectsPendingTaskCount() throws Exception {
        ScheduledExecutorService executorService = Mockito.mock(ScheduledExecutorService.class);
        AtomicReference<Runnable> scheduledTask = mockScheduleWithFixedDelay(executorService);
        originalPushPendingTaskExecutor =
            (ScheduledExecutorService) ReflectionTestUtils.getField(
                PushPendingTaskCountMetricsCollector.class,
                "executorService");
        runNoopOnOriginalExecutor(originalPushPendingTaskExecutor);
        ReflectionTestUtils.setField(PushPendingTaskCountMetricsCollector.class,
            "executorService",
            executorService);
        NamingSubscriberServiceV2Impl namingSubscriberService =
            Mockito.mock(NamingSubscriberServiceV2Impl.class);
        Mockito.when(namingSubscriberService.getPushPendingTaskCount()).thenReturn(17);
        
        new PushPendingTaskCountMetricsCollector(namingSubscriberService);
        assertNotNull(scheduledTask.get());
        scheduledTask.get().run();
        
        assertEquals(17, MetricsMonitor.getPushPendingTaskCount().get());
        Mockito.verify(executorService)
            .scheduleWithFixedDelay(Mockito.any(Runnable.class), Mockito.eq(2L), Mockito.eq(2L),
                Mockito.eq(TimeUnit.SECONDS));
    }
    
    @SuppressWarnings("unchecked")
    private AtomicReference<Runnable> mockScheduleWithFixedDelay(
        ScheduledExecutorService executorService) {
        AtomicReference<Runnable> scheduledTask = new AtomicReference<>();
        ScheduledFuture<Object> future = Mockito.mock(ScheduledFuture.class);
        Answer<ScheduledFuture<Object>> captureTaskAnswer = invocation -> {
            scheduledTask.set(invocation.getArgument(0));
            return future;
        };
        Mockito.when(executorService.scheduleWithFixedDelay(Mockito.any(Runnable.class),
            Mockito.anyLong(), Mockito.anyLong(), Mockito.any(TimeUnit.class)))
            .thenAnswer(captureTaskAnswer);
        return scheduledTask;
    }
    
    private Client mockClient(int publishedCount, int subscribedCount) {
        Client result = Mockito.mock(Client.class);
        Mockito.when(result.getAllPublishedService()).thenReturn(mockServices(publishedCount));
        Mockito.when(result.getAllSubscribeService()).thenReturn(mockServices(subscribedCount));
        return result;
    }
    
    private void runNoopOnOriginalExecutor(ScheduledExecutorService executorService)
        throws Exception {
        executorService.submit(() -> "done").get(5, TimeUnit.SECONDS);
    }
    
    private Collection<Service> mockServices(int count) {
        Collection<Service> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            result.add(Service.newService("namespace", "group", "service" + i));
        }
        return result;
    }
    
    private void replacePublisher(Class<? extends ServiceEvent> eventClass,
        EventPublisher eventPublisher) {
        Map<String, EventPublisher> publisherMap = NotifyCenter.getPublisherMap();
        if (ServiceEvent.ServiceSubscribedEvent.class.equals(eventClass)) {
            originalSubscribedPublisher = publisherMap.get(eventClass.getCanonicalName());
        } else {
            originalChangedPublisher = publisherMap.get(eventClass.getCanonicalName());
        }
        publisherMap.put(eventClass.getCanonicalName(), eventPublisher);
    }
    
    private void restorePublisher(Class<? extends ServiceEvent> eventClass,
        EventPublisher originalPublisher) {
        Map<String, EventPublisher> publisherMap = NotifyCenter.getPublisherMap();
        if (originalPublisher == null) {
            publisherMap.remove(eventClass.getCanonicalName());
        } else {
            publisherMap.put(eventClass.getCanonicalName(), originalPublisher);
        }
    }
}
