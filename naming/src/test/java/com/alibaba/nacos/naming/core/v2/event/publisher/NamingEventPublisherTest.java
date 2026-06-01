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

package com.alibaba.nacos.naming.core.v2.event.publisher;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.notify.listener.SmartSubscriber;
import com.alibaba.nacos.common.notify.listener.Subscriber;
import com.alibaba.nacos.common.utils.ThreadUtils;
import com.alibaba.nacos.naming.misc.Loggers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NamingEventPublisherTest {
    
    @Mock
    private Subscriber subscriber;
    
    @Mock
    private SmartSubscriber smartSubscriber;
    
    private NamingEventPublisher namingEventPublisher;
    
    @BeforeEach
    void setUp() throws Exception {
        namingEventPublisher = new NamingEventPublisher();
        namingEventPublisher.init(TestEvent.class, Byte.SIZE);
    }
    
    @AfterEach
    void tearDown() throws Exception {
        namingEventPublisher.shutdown();
    }
    
    @Test
    void testAddSubscriber() {
        namingEventPublisher.addSubscriber(subscriber, TestEvent.TestEvent1.class);
        namingEventPublisher.addSubscriber(smartSubscriber, TestEvent.TestEvent2.class);
        TestEvent.TestEvent1 testEvent1 = new TestEvent.TestEvent1();
        TestEvent.TestEvent2 testEvent2 = new TestEvent.TestEvent2();
        namingEventPublisher.publish(testEvent1);
        namingEventPublisher.publish(testEvent2);
        ThreadUtils.sleep(2000L);
        verify(subscriber).onEvent(testEvent1);
        verify(smartSubscriber).onEvent(testEvent2);
    }
    
    @Test
    void testRemoveSubscriber() {
        namingEventPublisher.addSubscriber(subscriber, TestEvent.TestEvent1.class);
        namingEventPublisher.addSubscriber(smartSubscriber, TestEvent.TestEvent1.class);
        TestEvent.TestEvent1 testEvent1 = new TestEvent.TestEvent1();
        namingEventPublisher.publish(testEvent1);
        ThreadUtils.sleep(2000L);
        verify(subscriber).onEvent(testEvent1);
        verify(smartSubscriber).onEvent(testEvent1);
        namingEventPublisher.removeSubscriber(smartSubscriber, TestEvent.TestEvent1.class);
        testEvent1 = new TestEvent.TestEvent1();
        namingEventPublisher.publish(testEvent1);
        ThreadUtils.sleep(500L);
        verify(subscriber).onEvent(testEvent1);
        verify(smartSubscriber, never()).onEvent(testEvent1);
    }
    
    @Test
    void testAddAndRemoveSubscriberWithSubscribeType() {
        when(subscriber.subscribeType()).thenReturn(TestEvent.TestEvent1.class);
        TestEvent.TestEvent1 testEvent1 = new TestEvent.TestEvent1();
        
        namingEventPublisher.addSubscriber(subscriber);
        namingEventPublisher.removeSubscriber(subscriber);
        namingEventPublisher.publish(testEvent1);
        ThreadUtils.sleep(500L);
        
        verify(subscriber, never()).onEvent(testEvent1);
    }
    
    @Test
    void testPublishOverFlow() {
        TestEvent testEvent = new TestEvent();
        for (int i = 0; i < Byte.SIZE; i++) {
            namingEventPublisher.publish(testEvent);
        }
        namingEventPublisher.addSubscriber(subscriber, TestEvent.class);
        namingEventPublisher.publish(testEvent);
        verify(subscriber, atLeastOnce()).onEvent(testEvent);
    }
    
    @Test
    void testNotifySubscriberWithExecutor() {
        TestEvent testEvent = new TestEvent();
        when(subscriber.executor()).thenReturn(Runnable::run);
        
        namingEventPublisher.notifySubscriber(subscriber, testEvent);
        
        verify(subscriber).onEvent(testEvent);
    }
    
    @Test
    void testNotifySubscriberWhenDebugEnabled() {
        Logger eventLogger = (Logger) Loggers.EVT_LOG;
        Level originalLevel = eventLogger.getLevel();
        try {
            eventLogger.setLevel(Level.DEBUG);
            TestEvent testEvent = new TestEvent();
            
            namingEventPublisher.notifySubscriber(subscriber, testEvent);
            
            verify(subscriber).onEvent(testEvent);
        } finally {
            eventLogger.setLevel(originalLevel);
        }
    }
    
    @Test
    void testHandleEventWithoutSubscriberWhenDebugEnabled() {
        Logger eventLogger = (Logger) Loggers.EVT_LOG;
        Level originalLevel = eventLogger.getLevel();
        try {
            eventLogger.setLevel(Level.DEBUG);
            namingEventPublisher.addSubscriber(subscriber, TestEvent.TestEvent1.class);
            
            namingEventPublisher.publish(new TestEvent.TestEvent2());
            ThreadUtils.sleep(2000L);
            
            assertThat(namingEventPublisher.currentEventSize(), is(0L));
        } finally {
            eventLogger.setLevel(originalLevel);
        }
    }
    
    @Test
    void testNotifySubscriberCatchesCallbackException() {
        TestEvent testEvent = new TestEvent();
        Mockito.doThrow(new RuntimeException("callback failed")).when(subscriber)
            .onEvent(testEvent);
        
        assertDoesNotThrow(() -> namingEventPublisher.notifySubscriber(subscriber, testEvent));
    }
    
    @Test
    void testPublishBeforeInitThrowsException() {
        NamingEventPublisher publisher = new NamingEventPublisher();
        
        assertThrows(IllegalStateException.class, () -> publisher.publish(new TestEvent()));
    }
    
    @Test
    void testRunCatchesUnexpectedException() {
        NamingEventPublisher publisher = new NamingEventPublisher();
        publisher.addSubscriber(subscriber, TestEvent.class);
        
        assertDoesNotThrow(publisher::run);
    }
    
    @Test
    void testRunHandlesInterruptedTake() throws Exception {
        namingEventPublisher.addSubscriber(subscriber, TestEvent.class);
        ThreadUtils.sleep(200L);
        
        namingEventPublisher.shutdown();
        namingEventPublisher.interrupt();
        ThreadUtils.sleep(200L);
        
        assertThat(namingEventPublisher.currentEventSize(), is(0L));
    }
    
    @Test
    void getStatus() throws NacosException {
        namingEventPublisher.publish(new TestEvent());
        namingEventPublisher.publish(new TestEvent.TestEvent1());
        namingEventPublisher.publish(new TestEvent.TestEvent2());
        String expectedStatus =
            "Publisher TestEvent                     : shutdown=false, queue=      3/8      ";
        assertThat(namingEventPublisher.getStatus(), is(expectedStatus));
        namingEventPublisher.addSubscriber(subscriber, TestEvent.TestEvent1.class);
        ThreadUtils.sleep(2000L);
        expectedStatus =
            "Publisher TestEvent                     : shutdown=false, queue=      0/8      ";
        assertThat(namingEventPublisher.getStatus(), is(expectedStatus));
        namingEventPublisher.shutdown();
        expectedStatus =
            "Publisher TestEvent                     : shutdown= true, queue=      0/8      ";
        assertThat(namingEventPublisher.getStatus(), is(expectedStatus));
    }
    
}
