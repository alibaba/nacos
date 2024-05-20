/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.common.notify;

import com.alibaba.nacos.common.notify.listener.Subscriber;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultPublisherTest {
    
    private DefaultPublisher publisher;
    
    @Mock
    private Subscriber<MockEvent> subscriber;
    
    @BeforeEach
    void setUp() throws Exception {
        publisher = new DefaultPublisher();
        publisher.init(MockEvent.class, 1);
    }
    
    @AfterEach
    void tearDown() throws Exception {
        try {
            publisher.shutdown();
        } catch (Exception ignored) {
        }
    }
    
    @Test
    void testInitWithIllegalSize() {
        publisher.shutdown();
        publisher = new DefaultPublisher();
        publisher.init(MockEvent.class, -1);
        assertTrue(publisher.isInitialized());
    }
    
    @Test
    void testCheckIsStart() {
        assertThrows(IllegalStateException.class, () -> {
            publisher.shutdown();
            publisher = new DefaultPublisher();
            publisher.checkIsStart();
        });
    }
    
    @Test
    void testCurrentEventSize() {
        assertEquals(0, publisher.currentEventSize());
        publisher.publish(new MockEvent());
        assertEquals(1, publisher.currentEventSize());
    }
    
    @Test
    void testRemoveSubscriber() {
        publisher.addSubscriber(subscriber);
        assertEquals(1, publisher.getSubscribers().size());
        publisher.removeSubscriber(subscriber);
        assertEquals(0, publisher.getSubscribers().size());
    }
    
    @Test
    void publishEventWhenQueueFull() {
        // Stop the publisher thread to mock queue full.
        publisher.shutdown();
        publisher.publish(new MockEvent());
        // Test throw event when no subscribers.
        publisher.publish(new MockEvent());
        verify(subscriber, never()).onEvent(any(MockEvent.class));
        // Add subscriber to test
        publisher.addSubscriber(subscriber);
        publisher.publish(new MockEvent());
        // Test scopeMatches not pass
        verify(subscriber, never()).onEvent(any(MockEvent.class));
        // Test scopeMatches pass
        when(subscriber.scopeMatches(any(MockEvent.class))).thenReturn(true);
        publisher.publish(new MockEvent());
        verify(subscriber).onEvent(any(MockEvent.class));
    }
    
    @Test
    void publishEventQueueNotFull() throws InterruptedException {
        when(subscriber.scopeMatches(any(MockEvent.class))).thenReturn(true);
        MockEvent mockEvent = new MockEvent();
        // Make sure Publisher entry waiting subscribers.
        TimeUnit.MILLISECONDS.sleep(500);
        publisher.addSubscriber(subscriber);
        publisher.publish(mockEvent);
        // Make sure Publisher find the subscribers.
        TimeUnit.MILLISECONDS.sleep(600);
        verify(subscriber).onEvent(mockEvent);
        // Test subscriber ignore expired event.
        publisher.publish(new MockEvent());
        TimeUnit.MILLISECONDS.sleep(100);
        reset(subscriber);
        when(subscriber.scopeMatches(any(MockEvent.class))).thenReturn(true);
        when(subscriber.ignoreExpireEvent()).thenReturn(true);
        publisher.publish(mockEvent);
        TimeUnit.MILLISECONDS.sleep(100);
        verify(subscriber, never()).onEvent(mockEvent);
    }
    
    @Test
    void testHandleEventWithThrowable() throws InterruptedException {
        when(subscriber.scopeMatches(any(MockEvent.class))).thenReturn(true);
        doThrow(new RuntimeException("test")).when(subscriber).onEvent(any(MockEvent.class));
        publisher.addSubscriber(subscriber);
        publisher.publish(new MockEvent());
        TimeUnit.MILLISECONDS.sleep(1100);
        verify(subscriber).onEvent(any(MockEvent.class));
    }
    
    @Test
    void testHandleEventWithExecutor() throws InterruptedException {
        Executor executor = mock(Executor.class);
        when(subscriber.scopeMatches(any(MockEvent.class))).thenReturn(true);
        when(subscriber.executor()).thenReturn(executor);
        publisher.addSubscriber(subscriber);
        publisher.publish(new MockEvent());
        TimeUnit.MILLISECONDS.sleep(1100);
        verify(executor).execute(any(Runnable.class));
    }
    
    @Test
    void testReceiveEventWithException() throws InterruptedException {
        Executor executor = mock(Executor.class);
        when(subscriber.scopeMatches(any(MockEvent.class))).thenReturn(true);
        when(subscriber.executor()).thenThrow(new RuntimeException("test"));
        publisher.addSubscriber(subscriber);
        publisher.publish(new MockEvent());
        TimeUnit.MILLISECONDS.sleep(1100);
        verify(executor, never()).execute(any(Runnable.class));
    }
    
    private static class MockEvent extends Event {
        
        private static final long serialVersionUID = -4081244883427311461L;
    }
}