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
import com.alibaba.nacos.common.trace.event.naming.NamingTraceEvent;
import com.alibaba.nacos.common.trace.event.naming.RegisterInstanceTraceEvent;
import com.alibaba.nacos.plugin.trace.NacosTracePluginManager;
import com.alibaba.nacos.plugin.trace.spi.NacosTraceSubscriber;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("all")
@RunWith(MockitoJUnitRunner.class)
public class NacosCombinedTraceSubscriberTest {
    
    @Mock
    private NacosTraceSubscriber mockSubscriber;
    
    @Mock
    private NacosTraceSubscriber mockSubscriber2;
    
    private NacosCombinedTraceSubscriber combinedTraceSubscriber;
    
    @Before
    public void setUp() throws Exception {
        Map<String, NacosTraceSubscriber> traceSubscribers = (Map<String, NacosTraceSubscriber>) ReflectionTestUtils
                .getField(NacosTracePluginManager.getInstance(), "traceSubscribers");
        traceSubscribers.put("nacos-combined", mockSubscriber);
        traceSubscribers.put("nacos-combined2", mockSubscriber2);
        List<Class<? extends TraceEvent>> testEvents = new LinkedList<>();
        testEvents.add(RegisterInstanceTraceEvent.class);
        testEvents.add(DeregisterInstanceTraceEvent.class);
        testEvents.add(TraceEvent.class);
        when(mockSubscriber.subscribeTypes()).thenReturn(testEvents);
        when(mockSubscriber2.subscribeTypes()).thenReturn(Collections.singletonList(RegisterInstanceTraceEvent.class));
        combinedTraceSubscriber = new NacosCombinedTraceSubscriber(NamingTraceEvent.class);
    }
    
    @After
    public void tearDown() throws Exception {
        Map<String, NacosTraceSubscriber> traceSubscribers = (Map<String, NacosTraceSubscriber>) ReflectionTestUtils
                .getField(NacosTracePluginManager.getInstance(), "traceSubscribers");
        traceSubscribers.remove("nacos-combined");
        traceSubscribers.remove("nacos-combined2");
        combinedTraceSubscriber.shutdown();
    }
    
    @Test
    public void testSubscribeTypes() {
        List<Class<? extends Event>> actual = combinedTraceSubscriber.subscribeTypes();
        assertEquals(2, actual.size());
        assertTrue(actual.contains(RegisterInstanceTraceEvent.class));
        assertTrue(actual.contains(DeregisterInstanceTraceEvent.class));
    }
    
    @Test
    public void testOnEvent() {
        RegisterInstanceTraceEvent event = new RegisterInstanceTraceEvent(1L, "", true, "", "", "", "", 1);
        doThrow(new RuntimeException("test")).when(mockSubscriber2).onEvent(event);
        combinedTraceSubscriber.onEvent(event);
        verify(mockSubscriber).onEvent(event);
        verify(mockSubscriber2).onEvent(event);
        DeregisterInstanceTraceEvent event1 = new DeregisterInstanceTraceEvent(1L, "", true,
                DeregisterInstanceReason.REQUEST, "", "", "", "", 1);
        combinedTraceSubscriber.onEvent(event1);
        verify(mockSubscriber).onEvent(event1);
        verify(mockSubscriber2, never()).onEvent(event1);
        TraceEvent event2 = new TraceEvent("", 1L, "", "", "");
        combinedTraceSubscriber.onEvent(event2);
        verify(mockSubscriber, never()).onEvent(event2);
        verify(mockSubscriber2, never()).onEvent(event2);
    }
    
    @Test
    public void testOnEventWithExecutor() {
        Executor executor = mock(Executor.class);
        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
                invocationOnMock.getArgument(0, Runnable.class).run();
                return null;
            }
        }).when(executor).execute(any(Runnable.class));
        when(mockSubscriber.executor()).thenReturn(executor);
        RegisterInstanceTraceEvent event = new RegisterInstanceTraceEvent(1L, "", true, "", "", "", "", 1);
        combinedTraceSubscriber.onEvent(event);
        verify(mockSubscriber).onEvent(event);
        verify(mockSubscriber2).onEvent(event);
    }
}
