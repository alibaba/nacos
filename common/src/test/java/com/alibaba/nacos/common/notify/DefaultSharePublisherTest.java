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

import com.alibaba.nacos.common.notify.listener.SmartSubscriber;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class DefaultSharePublisherTest {
    
    private static final AtomicLong TEST_SEQUENCE = new AtomicLong();
    
    DefaultSharePublisher defaultSharePublisher;
    
    @Mock
    SmartSubscriber smartSubscriber1;
    
    @Mock
    SmartSubscriber smartSubscriber2;
    
    @Before
    public void setUp() throws Exception {
        defaultSharePublisher = new DefaultSharePublisher();
        defaultSharePublisher.init(SlowEvent.class, 2);
    }
    
    @After
    public void tearDown() throws Exception {
        defaultSharePublisher.shutdown();
    }
    
    @Test
    public void testRemoveSubscribers() {
        defaultSharePublisher.addSubscriber(smartSubscriber1, MockSlowEvent1.class);
        defaultSharePublisher.addSubscriber(smartSubscriber1, MockSlowEvent2.class);
        defaultSharePublisher.addSubscriber(smartSubscriber2, MockSlowEvent2.class);
        assertEquals(2, defaultSharePublisher.getSubscribers().size());
        defaultSharePublisher.removeSubscriber(smartSubscriber1, MockSlowEvent1.class);
        defaultSharePublisher.removeSubscriber(smartSubscriber1, MockSlowEvent2.class);
        defaultSharePublisher.removeSubscriber(smartSubscriber2, MockSlowEvent2.class);
        assertEquals(0, defaultSharePublisher.getSubscribers().size());
    }
    
    @Test
    public void testReceiveEventWithoutSubscriber() {
        defaultSharePublisher.addSubscriber(smartSubscriber1, MockSlowEvent1.class);
        defaultSharePublisher.addSubscriber(smartSubscriber2, MockSlowEvent2.class);
        defaultSharePublisher.receiveEvent(new SlowEvent() {
            private static final long serialVersionUID = 5996336354563933789L;
            
            @Override
            public long sequence() {
                return super.sequence();
            }
        });
        verify(smartSubscriber1, never()).onEvent(any(SlowEvent.class));
        verify(smartSubscriber2, never()).onEvent(any(SlowEvent.class));
    }
    
    @Test
    public void testReceiveEventWithSubscriber() {
        defaultSharePublisher.addSubscriber(smartSubscriber1, MockSlowEvent1.class);
        defaultSharePublisher.addSubscriber(smartSubscriber2, MockSlowEvent2.class);
        defaultSharePublisher.receiveEvent(new MockSlowEvent1());
        verify(smartSubscriber1).onEvent(any(MockSlowEvent1.class));
        verify(smartSubscriber2, never()).onEvent(any(MockSlowEvent1.class));
        defaultSharePublisher.receiveEvent(new MockSlowEvent2());
        verify(smartSubscriber1, never()).onEvent(any(MockSlowEvent2.class));
        verify(smartSubscriber2).onEvent(any(MockSlowEvent2.class));
    }
    
    @Test
    public void testIgnoreExpiredEvent() throws InterruptedException {
        MockSlowEvent1 mockSlowEvent1 = new MockSlowEvent1();
        MockSlowEvent2 mockSlowEvent2 = new MockSlowEvent2();
        defaultSharePublisher.addSubscriber(smartSubscriber1, MockSlowEvent1.class);
        defaultSharePublisher.addSubscriber(smartSubscriber2, MockSlowEvent2.class);
        defaultSharePublisher.publish(mockSlowEvent1);
        defaultSharePublisher.publish(mockSlowEvent2);
        TimeUnit.MILLISECONDS.sleep(1100);
        verify(smartSubscriber1).onEvent(mockSlowEvent1);
        verify(smartSubscriber2).onEvent(mockSlowEvent2);
        reset(smartSubscriber1);
        when(smartSubscriber1.ignoreExpireEvent()).thenReturn(true);
        defaultSharePublisher.publish(mockSlowEvent1);
        TimeUnit.MILLISECONDS.sleep(100);
        verify(smartSubscriber1, never()).onEvent(mockSlowEvent1);
    }
    
    private static class MockSlowEvent1 extends SlowEvent {
        
        private static final long serialVersionUID = -951177705152304999L;
        
        private final long sequence = TEST_SEQUENCE.incrementAndGet();
        
        @Override
        public long sequence() {
            return sequence;
        }
    }
    
    private static class MockSlowEvent2 extends SlowEvent {
        
        private static final long serialVersionUID = -951177705152304999L;
        
        private final long sequence = TEST_SEQUENCE.incrementAndGet();
        
        @Override
        public long sequence() {
            return sequence;
        }
    }
}