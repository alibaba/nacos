/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.trace;

import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.trace.DeregisterInstanceReason;
import com.alibaba.nacos.common.trace.event.TraceEvent;
import com.alibaba.nacos.common.trace.event.naming.DeregisterInstanceTraceEvent;
import com.alibaba.nacos.common.trace.event.naming.DeregisterServiceTraceEvent;
import com.alibaba.nacos.common.trace.event.naming.HealthStateChangeTraceEvent;
import com.alibaba.nacos.common.trace.event.naming.NamingTraceEvent;
import com.alibaba.nacos.common.trace.event.naming.PushServiceTraceEvent;
import com.alibaba.nacos.common.trace.event.naming.RegisterInstanceTraceEvent;
import com.alibaba.nacos.common.trace.event.naming.RegisterServiceTraceEvent;
import com.alibaba.nacos.common.trace.event.naming.SubscribeServiceTraceEvent;
import com.alibaba.nacos.common.trace.event.naming.UnsubscribeServiceTraceEvent;
import com.alibaba.nacos.common.trace.event.naming.UpdateInstanceTraceEvent;
import com.alibaba.nacos.common.trace.event.naming.UpdateServiceTraceEvent;
import com.alibaba.nacos.plugin.trace.NacosTracePluginManager;
import com.alibaba.nacos.plugin.trace.spi.NacosTraceSubscriber;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NacosCombinedTraceSubscriberTest {
    
    @Mock
    private NacosTraceSubscriber mockServiceSubscriber;
    
    @Mock
    private NacosTraceSubscriber mockInstanceSubscriber;
    
    @Mock
    private NacosTraceSubscriber mockOtherSubscriber;
    
    private NacosCombinedTraceSubscriber combinedTraceSubscriber;
    
    @SuppressWarnings("unchecked")
    private Map<String, NacosTraceSubscriber> getTraceSubscribers() {
        return (Map<String, NacosTraceSubscriber>) ReflectionTestUtils.getField(NacosTracePluginManager.getInstance(), "traceSubscribers");
    }
    
    @BeforeEach
    void setUp() throws Exception {
        Map<String, NacosTraceSubscriber> traceSubscribers = getTraceSubscribers();
        traceSubscribers.put("instanceSubscriber", mockInstanceSubscriber);
        traceSubscribers.put("serviceSubscriber", mockServiceSubscriber);
        traceSubscribers.put("otherSubscriber", mockOtherSubscriber);
        // Initialization instance related.
        List<Class<? extends TraceEvent>> instanceEvents = new LinkedList<>();
        instanceEvents.add(RegisterInstanceTraceEvent.class);
        instanceEvents.add(DeregisterInstanceTraceEvent.class);
        instanceEvents.add(UpdateInstanceTraceEvent.class);
        // Initialization service related.
        List<Class<? extends TraceEvent>> serviceEvents = new LinkedList<>();
        serviceEvents.add(PushServiceTraceEvent.class);
        serviceEvents.add(RegisterServiceTraceEvent.class);
        serviceEvents.add(DeregisterServiceTraceEvent.class);
        serviceEvents.add(SubscribeServiceTraceEvent.class);
        serviceEvents.add(UnsubscribeServiceTraceEvent.class);
        serviceEvents.add(UpdateServiceTraceEvent.class);
        // Initialization other related.
        List<Class<? extends TraceEvent>> otherEvents = new LinkedList<>();
        otherEvents.add(HealthStateChangeTraceEvent.class);
        otherEvents.add(TraceEvent.class);
        when(mockServiceSubscriber.subscribeTypes()).thenReturn(serviceEvents);
        when(mockInstanceSubscriber.subscribeTypes()).thenReturn(instanceEvents);
        when(mockOtherSubscriber.subscribeTypes()).thenReturn(otherEvents);
        combinedTraceSubscriber = new NacosCombinedTraceSubscriber(NamingTraceEvent.class);
    }
    
    @AfterEach
    void tearDown() throws Exception {
        Map<String, NacosTraceSubscriber> traceSubscribers = getTraceSubscribers();
        traceSubscribers.remove("serviceSubscriber");
        traceSubscribers.remove("instanceSubscriber");
        traceSubscribers.remove("otherSubscriber");
        combinedTraceSubscriber.shutdown();
    }
    
    @Test
    void testSubscribeTypes() {
        List<Class<? extends Event>> actual = combinedTraceSubscriber.subscribeTypes();
        assertEquals(10, actual.size());
        assertTrue(actual.contains(RegisterInstanceTraceEvent.class));
        assertTrue(actual.contains(DeregisterInstanceTraceEvent.class));
        assertTrue(actual.contains(UpdateInstanceTraceEvent.class));
        assertTrue(actual.contains(RegisterServiceTraceEvent.class));
        assertTrue(actual.contains(DeregisterServiceTraceEvent.class));
        assertTrue(actual.contains(SubscribeServiceTraceEvent.class));
        assertTrue(actual.contains(UnsubscribeServiceTraceEvent.class));
        assertTrue(actual.contains(UpdateServiceTraceEvent.class));
        assertTrue(actual.contains(PushServiceTraceEvent.class));
        assertTrue(actual.contains(HealthStateChangeTraceEvent.class));
    }
    
    @Test
    void testOnEvent() {
        // Test RegisterInstanceTraceEvent.
        RegisterInstanceTraceEvent registerInstanceTraceEvent = new RegisterInstanceTraceEvent(1L, "", true, "", "", "", "", 1);
        doThrow(new RuntimeException("test")).when(mockInstanceSubscriber).onEvent(registerInstanceTraceEvent);
        combinedTraceSubscriber.onEvent(registerInstanceTraceEvent);
        verify(mockInstanceSubscriber, times(1)).onEvent(registerInstanceTraceEvent);
        verify(mockServiceSubscriber, never()).onEvent(registerInstanceTraceEvent);
        verify(mockOtherSubscriber, never()).onEvent(registerInstanceTraceEvent);
        // Test DeregisterInstanceTraceEvent.
        DeregisterInstanceTraceEvent deregisterInstanceTraceEvent = new DeregisterInstanceTraceEvent(1L, "", true,
                DeregisterInstanceReason.REQUEST, "", "", "", "", 1);
        combinedTraceSubscriber.onEvent(deregisterInstanceTraceEvent);
        verify(mockInstanceSubscriber, times(1)).onEvent(deregisterInstanceTraceEvent);
        verify(mockServiceSubscriber, never()).onEvent(deregisterInstanceTraceEvent);
        verify(mockOtherSubscriber, never()).onEvent(deregisterInstanceTraceEvent);
        // Test UpdateInstanceTraceEvent.
        UpdateInstanceTraceEvent updateInstanceTraceEvent = new UpdateInstanceTraceEvent(1L, "", "", "", "", "", 123, null);
        combinedTraceSubscriber.onEvent(updateInstanceTraceEvent);
        verify(mockInstanceSubscriber, times(1)).onEvent(updateInstanceTraceEvent);
        verify(mockServiceSubscriber, never()).onEvent(updateInstanceTraceEvent);
        verify(mockOtherSubscriber, never()).onEvent(updateInstanceTraceEvent);
        // Test RegisterServiceTraceEvent.
        RegisterServiceTraceEvent registerServiceTraceEvent = new RegisterServiceTraceEvent(1L, "", "", "");
        combinedTraceSubscriber.onEvent(registerServiceTraceEvent);
        verify(mockInstanceSubscriber, never()).onEvent(registerServiceTraceEvent);
        verify(mockServiceSubscriber, times(1)).onEvent(registerServiceTraceEvent);
        verify(mockOtherSubscriber, never()).onEvent(registerServiceTraceEvent);
        // Test DeregisterServiceTraceEvent.
        DeregisterServiceTraceEvent deregisterServiceTraceEvent = new DeregisterServiceTraceEvent(1L, "", "", "");
        combinedTraceSubscriber.onEvent(deregisterServiceTraceEvent);
        verify(mockInstanceSubscriber, never()).onEvent(deregisterServiceTraceEvent);
        verify(mockServiceSubscriber, times(1)).onEvent(deregisterServiceTraceEvent);
        verify(mockOtherSubscriber, never()).onEvent(deregisterServiceTraceEvent);
        // Test SubscribeServiceTraceEvent.
        SubscribeServiceTraceEvent subscribeServiceTraceEvent = new SubscribeServiceTraceEvent(1L, "", "", "", "");
        combinedTraceSubscriber.onEvent(subscribeServiceTraceEvent);
        verify(mockInstanceSubscriber, never()).onEvent(subscribeServiceTraceEvent);
        verify(mockServiceSubscriber, times(1)).onEvent(subscribeServiceTraceEvent);
        verify(mockOtherSubscriber, never()).onEvent(subscribeServiceTraceEvent);
        // Test UnsubscribeServiceTraceEvent.
        UnsubscribeServiceTraceEvent unsubscribeServiceTraceEvent = new UnsubscribeServiceTraceEvent(1L, "", "", "", "");
        combinedTraceSubscriber.onEvent(unsubscribeServiceTraceEvent);
        verify(mockInstanceSubscriber, never()).onEvent(unsubscribeServiceTraceEvent);
        verify(mockServiceSubscriber, times(1)).onEvent(unsubscribeServiceTraceEvent);
        verify(mockOtherSubscriber, never()).onEvent(unsubscribeServiceTraceEvent);
        // Test UpdateServiceTraceEvent.
        UpdateServiceTraceEvent updateServiceTraceEvent = new UpdateServiceTraceEvent(1L, "", "", "", null);
        combinedTraceSubscriber.onEvent(updateServiceTraceEvent);
        verify(mockInstanceSubscriber, never()).onEvent(updateServiceTraceEvent);
        verify(mockServiceSubscriber, times(1)).onEvent(updateServiceTraceEvent);
        verify(mockOtherSubscriber, never()).onEvent(updateServiceTraceEvent);
        // Test PushServiceTraceEvent.
        PushServiceTraceEvent pushServiceTraceEvent = new PushServiceTraceEvent(1L, 1L, 1L, 1L, "", "", "", "", 1);
        combinedTraceSubscriber.onEvent(pushServiceTraceEvent);
        verify(mockInstanceSubscriber, never()).onEvent(pushServiceTraceEvent);
        verify(mockServiceSubscriber, times(1)).onEvent(pushServiceTraceEvent);
        verify(mockOtherSubscriber, never()).onEvent(pushServiceTraceEvent);
        // Test HealthStateChangeTraceEvent.
        HealthStateChangeTraceEvent healthStateChangeTraceEvent = new HealthStateChangeTraceEvent(1L, "", "", "", "", 8867, true, "");
        combinedTraceSubscriber.onEvent(healthStateChangeTraceEvent);
        verify(mockInstanceSubscriber, never()).onEvent(healthStateChangeTraceEvent);
        verify(mockServiceSubscriber, never()).onEvent(healthStateChangeTraceEvent);
        verify(mockOtherSubscriber, times(1)).onEvent(healthStateChangeTraceEvent);
        // Test TraceEvent.
        TraceEvent traceEvent = new TraceEvent("", 1L, "", "", "");
        combinedTraceSubscriber.onEvent(traceEvent);
        verify(mockInstanceSubscriber, never()).onEvent(traceEvent);
        verify(mockServiceSubscriber, never()).onEvent(traceEvent);
        verify(mockOtherSubscriber, never()).onEvent(traceEvent);
    }
    
    @Test
    void testOnEventWithExecutor() {
        Executor executor = mock(Executor.class);
        doAnswer(invocationOnMock -> {
            invocationOnMock.getArgument(0, Runnable.class).run();
            return null;
        }).when(executor).execute(any(Runnable.class));
        when(mockInstanceSubscriber.executor()).thenReturn(executor);
        RegisterInstanceTraceEvent event = new RegisterInstanceTraceEvent(1L, "", true, "", "", "", "", 1);
        combinedTraceSubscriber.onEvent(event);
        verify(mockInstanceSubscriber).onEvent(event);
    }
}
