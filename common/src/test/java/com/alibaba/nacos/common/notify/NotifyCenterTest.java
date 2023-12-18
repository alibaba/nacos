/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.notify.listener.SmartSubscriber;
import com.alibaba.nacos.common.notify.listener.Subscriber;
import com.alibaba.nacos.common.utils.ThreadUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class NotifyCenterTest {
    
    static {
        System.setProperty("nacos.core.notify.share-buffer-size", "8");
    }
    
    private static AtomicInteger count;
    
    SmartSubscriber smartSubscriber;
    
    Subscriber subscriber;
    
    @Mock
    ShardedEventPublisher shardedEventPublisher;
    
    @Before
    public void setUp() throws Exception {
        count = new AtomicInteger();
        NotifyCenter.registerToSharePublisher(TestSlowEvent.class);
        NotifyCenter.registerToPublisher(TestSlowEvent1.class, 10);
        NotifyCenter.registerToPublisher(TestEvent.class, 8);
        NotifyCenter.registerToPublisher(ExpireEvent.class, 16);
        NotifyCenter.registerToPublisher(SharedEvent.class, shardedEventPublisher);
    }
    
    @After
    public void tearDown() throws Exception {
        if (null != smartSubscriber) {
            NotifyCenter.deregisterSubscriber(smartSubscriber);
        }
        if (null != subscriber) {
            NotifyCenter.deregisterSubscriber(subscriber);
        }
        NotifyCenter.deregisterPublisher(TestEvent.class);
        NotifyCenter.deregisterPublisher(ExpireEvent.class);
    }
    
    @Test
    public void testRegisterNullPublisher() {
        int originalSize = NotifyCenter.getPublisherMap().size();
        NotifyCenter.registerToPublisher(NoPublisherEvent.class, null);
        assertEquals(originalSize, NotifyCenter.getPublisherMap().size());
    }
    
    @Test
    public void testGetPublisher() {
        assertEquals(NotifyCenter.getSharePublisher(), NotifyCenter.getPublisher(TestSlowEvent.class));
        assertTrue(NotifyCenter.getPublisher(TestEvent.class) instanceof DefaultPublisher);
    }
    
    @Test
    public void testEventsCanBeSubscribed() {
        subscriber = new MockSubscriber<>(TestEvent.class, false);
        smartSubscriber = new MockSmartSubscriber(Collections.singletonList(TestSlowEvent.class));
        NotifyCenter.registerSubscriber(subscriber);
        NotifyCenter.registerSubscriber(smartSubscriber);
        Assert.assertTrue(NotifyCenter.publishEvent(new TestEvent()));
        Assert.assertTrue(NotifyCenter.publishEvent(new TestSlowEvent()));
        ThreadUtils.sleep(2000L);
        assertEquals(2, count.get());
    }
    
    @Test
    public void testCanIgnoreExpireEvent() throws Exception {
        NotifyCenter.registerToPublisher(ExpireEvent.class, 16);
        CountDownLatch latch = new CountDownLatch(3);
        subscriber = new MockSubscriber<>(ExpireEvent.class, true, latch);
        NotifyCenter.registerSubscriber(subscriber);
        for (int i = 0; i < 3; i++) {
            Assert.assertTrue(NotifyCenter.publishEvent(new ExpireEvent(3 - i)));
        }
        latch.await(5000L, TimeUnit.MILLISECONDS);
        assertEquals(1, count.get());
    }
    
    @Test
    public void testSharePublishEvent() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(20);
        Subscriber subscriber = new MockSubscriber<>(TestSlowEvent.class, false, latch);
        Subscriber subscriber1 = new MockSubscriber<>(TestSlowEvent1.class, false, latch);
        try {
            NotifyCenter.registerSubscriber(subscriber);
            NotifyCenter.registerSubscriber(subscriber1);
            for (int i = 0; i < 10; i++) {
                Assert.assertTrue(NotifyCenter.publishEvent(new TestSlowEvent()));
                Assert.assertTrue(NotifyCenter.publishEvent(new TestSlowEvent1()));
            }
            latch.await(5000L, TimeUnit.MILLISECONDS);
            assertEquals(20, count.get());
        } finally {
            NotifyCenter.deregisterSubscriber(subscriber);
            NotifyCenter.deregisterSubscriber(subscriber1);
        }
    }
    
    @Test
    public void testMutipleSlowEventsListenedBySmartSubscriber() throws Exception {
        List<Class<? extends Event>> subscribedEvents = new LinkedList<>();
        subscribedEvents.add(TestSlowEvent.class);
        subscribedEvents.add(TestSlowEvent1.class);
        CountDownLatch latch = new CountDownLatch(6);
        smartSubscriber = new MockSmartSubscriber(subscribedEvents, latch);
        NotifyCenter.registerSubscriber(smartSubscriber);
        for (int i = 0; i < 3; i++) {
            Assert.assertTrue(NotifyCenter.publishEvent(new TestSlowEvent()));
            Assert.assertTrue(NotifyCenter.publishEvent(new TestSlowEvent1()));
        }
        latch.await(5000L, TimeUnit.MILLISECONDS);
        assertEquals(6, count.get());
    }
    
    @Test
    public void testMutipleKindsEventsCanListenBySmartsubscriber() throws Exception {
        List<Class<? extends Event>> subscribedEvents = new LinkedList<>();
        subscribedEvents.add(TestEvent.class);
        subscribedEvents.add(TestSlowEvent.class);
        CountDownLatch latch = new CountDownLatch(6);
        smartSubscriber = new MockSmartSubscriber(subscribedEvents, latch);
        NotifyCenter.registerSubscriber(smartSubscriber);
        for (int i = 0; i < 3; i++) {
            Assert.assertTrue(NotifyCenter.publishEvent(new TestEvent()));
            Assert.assertTrue(NotifyCenter.publishEvent(new TestSlowEvent()));
        }
        latch.await(5000L, TimeUnit.MILLISECONDS);
        assertEquals(6, count.get());
    }
    
    @Test
    public void testPublishEventByNoPublisher() {
        for (int i = 0; i < 3; i++) {
            Assert.assertFalse(NotifyCenter.publishEvent(new NoPublisherEvent()));
        }
    }
    
    @Test
    public void testPublishEventByPluginEvent() {
        for (int i = 0; i < 3; i++) {
            Assert.assertTrue(NotifyCenter.publishEvent(new PluginEvent()));
        }
    }
    
    @Test
    public void testDeregisterPublisherWithException() throws NacosException {
        final int originalSize = NotifyCenter.getPublisherMap().size();
        doThrow(new RuntimeException("test")).when(shardedEventPublisher).shutdown();
        NotifyCenter.getPublisherMap().put(SharedEvent.class.getCanonicalName(), shardedEventPublisher);
        NotifyCenter.deregisterPublisher(SharedEvent.class);
        assertEquals(originalSize - 1, NotifyCenter.getPublisherMap().size());
    }
    
    @Test
    public void testPublishEventWithException() {
        when(shardedEventPublisher.publish(any(Event.class))).thenThrow(new RuntimeException("test"));
        NotifyCenter.getPublisherMap().put(SharedEvent.class.getCanonicalName(), shardedEventPublisher);
        assertFalse(NotifyCenter.publishEvent(new SharedEvent()));
    }
    
    @Test
    public void testOperateSubscriberForShardedPublisher() {
        subscriber = new MockSubscriber(SharedEvent.class, false);
        NotifyCenter.getPublisherMap().put(SharedEvent.class.getCanonicalName(), shardedEventPublisher);
        NotifyCenter.registerSubscriber(subscriber);
        verify(shardedEventPublisher).addSubscriber(subscriber, SharedEvent.class);
        NotifyCenter.deregisterSubscriber(subscriber);
        verify(shardedEventPublisher).removeSubscriber(subscriber, SharedEvent.class);
    }
    
    @Test(expected = NoSuchElementException.class)
    public void testDeregisterNonExistSubscriber() {
        try {
            subscriber = new MockSubscriber(NoPublisherEvent.class, false);
            NotifyCenter.deregisterSubscriber(subscriber);
        } finally {
            subscriber = null;
        }
    }
    
    private static class MockSubscriber<T extends Event> extends Subscriber<T> {
        
        private final Class<T> subscribedEvent;
        
        private final boolean ignoreExpiredEvent;
        
        private final CountDownLatch latch;
        
        private MockSubscriber(Class<T> subscribedEvent, boolean ignoreExpiredEvent) {
            this(subscribedEvent, ignoreExpiredEvent, null);
        }
        
        public MockSubscriber(Class<T> subscribedEvent, boolean ignoreExpiredEvent, CountDownLatch latch) {
            this.subscribedEvent = subscribedEvent;
            this.ignoreExpiredEvent = ignoreExpiredEvent;
            this.latch = latch;
        }
        
        @Override
        public void onEvent(Event event) {
            count.incrementAndGet();
            if (null != latch) {
                latch.countDown();
            }
        }
        
        @Override
        public Class<? extends Event> subscribeType() {
            return subscribedEvent;
        }
        
        @Override
        public boolean ignoreExpireEvent() {
            return ignoreExpiredEvent;
        }
    }
    
    private static class MockSmartSubscriber extends SmartSubscriber {
        
        private final List<Class<? extends Event>> subscribedEvents;
        
        private final CountDownLatch latch;
        
        private MockSmartSubscriber(List<Class<? extends Event>> subscribedEvents) {
            this(subscribedEvents, null);
        }
        
        public MockSmartSubscriber(List<Class<? extends Event>> subscribedEvents, CountDownLatch latch) {
            this.subscribedEvents = subscribedEvents;
            this.latch = latch;
        }
        
        @Override
        public void onEvent(Event event) {
            count.incrementAndGet();
            if (null != latch) {
                latch.countDown();
            }
        }
        
        @Override
        public List<Class<? extends Event>> subscribeTypes() {
            return subscribedEvents;
        }
    }
    
    private static class TestSlowEvent extends SlowEvent {
        
        private static final long serialVersionUID = 6713279688910446154L;
    }
    
    private static class TestSlowEvent1 extends SlowEvent {
        
        private static final long serialVersionUID = 5946729801676058102L;
    }
    
    private static class TestEvent extends Event {
        
        private static final long serialVersionUID = 2522362576233446960L;
        
        @Override
        public long sequence() {
            return System.currentTimeMillis();
        }
    }
    
    private static class NoPublisherEvent extends Event {
        
        private static final long serialVersionUID = 6532409163269714916L;
    }
    
    private static class SharedEvent extends Event {
    
        private static final long serialVersionUID = 7648766983252000074L;
    }
    
    private static class PluginEvent extends Event {
    
        private static final long serialVersionUID = -7787588724415976798L;
    
        @Override
        public boolean isPluginEvent() {
            return true;
        }
    }
    
    private static class ExpireEvent extends Event {
        
        private static final long serialVersionUID = 3024284255874382548L;
        
        private final long no;
        
        ExpireEvent(long no) {
            this.no = no;
        }
        
        @Override
        public long sequence() {
            return no;
        }
    }
}
