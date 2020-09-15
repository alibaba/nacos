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

import com.alibaba.nacos.common.notify.listener.SmartSubscriber;
import com.alibaba.nacos.common.notify.listener.Subscriber;
import com.alibaba.nacos.common.utils.ThreadUtils;
import org.junit.Assert;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@FixMethodOrder(value = MethodSorters.NAME_ASCENDING)
public class NotifyCenterTest {
    
    private static class TestSlowEvent extends SlowEvent {
    
    }
    
    private static class TestEvent extends Event {
        
        @Override
        public long sequence() {
            return System.currentTimeMillis();
        }
    }
    
    static {
        System.setProperty("nacos.core.notify.share-buffer-size", "8");
    }
    
    @Test
    public void testEventsCanBeSubscribed() throws Exception {
        
        NotifyCenter.registerToSharePublisher(TestSlowEvent.class);
        NotifyCenter.registerToPublisher(TestEvent.class, 8);
        
        final CountDownLatch latch = new CountDownLatch(2);
        final AtomicInteger count = new AtomicInteger(0);
        
        NotifyCenter.registerSubscriber(new Subscriber<TestSlowEvent>() {
            @Override
            public void onEvent(TestSlowEvent event) {
                try {
                    count.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            }
            
            @Override
            public Class<? extends Event> subscribeType() {
                return TestSlowEvent.class;
            }
        });
        
        NotifyCenter.registerSubscriber(new Subscriber<TestEvent>() {
            @Override
            public void onEvent(TestEvent event) {
                try {
                    count.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            }
            
            @Override
            public Class<? extends Event> subscribeType() {
                return TestEvent.class;
            }
        });
        
        Assert.assertTrue(NotifyCenter.publishEvent(new TestEvent()));
        Assert.assertTrue(NotifyCenter.publishEvent(new TestSlowEvent()));
        
        ThreadUtils.sleep(5000L);
        
        latch.await(5000L, TimeUnit.MILLISECONDS);
        
        Assert.assertEquals(2, count.get());
    }
    
    static CountDownLatch latch = new CountDownLatch(3);
    
    static class ExpireEvent extends Event {
        
        static AtomicLong sequence = new AtomicLong(3);
        
        private long no = sequence.getAndDecrement();
        
        @Override
        public long sequence() {
            latch.countDown();
            return no;
        }
    }
    
    @Test
    public void testCanIgnoreExpireEvent() throws Exception {
        NotifyCenter.registerToPublisher(ExpireEvent.class, 16);
        final AtomicInteger count = new AtomicInteger(0);
        
        NotifyCenter.registerSubscriber(new Subscriber<ExpireEvent>() {
            @Override
            public void onEvent(ExpireEvent event) {
                count.incrementAndGet();
            }
            
            @Override
            public Class<? extends Event> subscribeType() {
                return ExpireEvent.class;
            }
            
            @Override
            public boolean ignoreExpireEvent() {
                return true;
            }
            
        });
        
        for (int i = 0; i < 3; i++) {
            Assert.assertTrue(NotifyCenter.publishEvent(new ExpireEvent()));
        }
        
        latch.await(10000L, TimeUnit.MILLISECONDS);
        Assert.assertEquals(1, count.get());
    }
    
    static CountDownLatch latch2 = new CountDownLatch(3);
    
    static class NoExpireEvent extends Event {
        
        static AtomicLong sequence = new AtomicLong(3);
        
        private long no = sequence.getAndDecrement();
        
        @Override
        public long sequence() {
            return no;
        }
    }
    
    @Test
    public void testNoIgnoreExpireEvent() throws Exception {
        NotifyCenter.registerToPublisher(NoExpireEvent.class, 16);
        final AtomicInteger count = new AtomicInteger(0);
        
        NotifyCenter.registerSubscriber(new Subscriber() {
            @Override
            public void onEvent(Event event) {
                count.incrementAndGet();
                latch2.countDown();
            }
            
            @Override
            public Class<? extends Event> subscribeType() {
                return NoExpireEvent.class;
            }
        });
        
        for (int i = 0; i < 3; i++) {
            Assert.assertTrue(NotifyCenter.publishEvent(new NoExpireEvent()));
        }
        
        latch2.await(10000L, TimeUnit.MILLISECONDS);
        Assert.assertEquals(3, count.get());
    }
    
    private static class SlowE1 extends SlowEvent {
        
        private String info = "SlowE1";
        
        public String getInfo() {
            return info;
        }
        
        public void setInfo(String info) {
            this.info = info;
        }
        
    }
    
    private static class SlowE2 extends SlowEvent {
        
        private String info = "SlowE2";
        
        public String getInfo() {
            return info;
        }
        
        public void setInfo(String info) {
            this.info = info;
        }
        
    }
    
    @Test
    public void testSharePublishTwoSlowEvents() throws Exception {
        NotifyCenter.registerToSharePublisher(SlowE1.class);
        NotifyCenter.registerToSharePublisher(SlowE2.class);
        
        final CountDownLatch latch1 = new CountDownLatch(15);
        final CountDownLatch latch2 = new CountDownLatch(15);
        
        final String[] values = new String[] {null, null};
        
        NotifyCenter.registerSubscriber(new Subscriber<SlowE1>() {
            
            @Override
            public void onEvent(SlowE1 event) {
                ThreadUtils.sleep(1000L);
                values[0] = event.info;
                latch1.countDown();
            }
            
            @Override
            public Class<? extends Event> subscribeType() {
                return SlowE1.class;
            }
        });
        
        NotifyCenter.registerSubscriber(new Subscriber<SlowE2>() {
            @Override
            public void onEvent(SlowE2 event) {
                values[1] = event.info;
                latch2.countDown();
            }
            
            @Override
            public Class<? extends Event> subscribeType() {
                return SlowE2.class;
            }
        });
        
        for (int i = 0; i < 30; i++) {
            NotifyCenter.publishEvent(new SlowE1());
            NotifyCenter.publishEvent(new SlowE2());
        }
        
        latch1.await();
        latch2.await();
        
        Assert.assertEquals("SlowE1", values[0]);
        Assert.assertEquals("SlowE2", values[1]);
        
    }
    
    static class SmartEvent1 extends Event {
        
        @Override
        public long sequence() {
            return System.currentTimeMillis();
        }
    }
    
    static class SmartEvent2 extends Event {
        
        @Override
        public long sequence() {
            return System.currentTimeMillis();
        }
    }
    
    @Test
    public void testSeveralEventsPublishedBySinglePublisher() throws Exception {
        
        final AtomicInteger count1 = new AtomicInteger(0);
        final AtomicInteger count2 = new AtomicInteger(0);
        
        final CountDownLatch latch1 = new CountDownLatch(3);
        final CountDownLatch latch2 = new CountDownLatch(3);
        
        NotifyCenter.registerToPublisher(SmartEvent1.class, 1024);
        NotifyCenter.registerToPublisher(SmartEvent2.class, 1024);
        
        NotifyCenter.registerSubscriber(new SmartSubscriber() {
            @Override
            public List<Class<? extends Event>> subscribeTypes() {
                List<Class<? extends Event>> list = new ArrayList<Class<? extends Event>>();
                list.add(SmartEvent1.class);
                list.add(SmartEvent2.class);
                return list;
            }
            
            @Override
            public void onEvent(Event event) {
                if (event instanceof SmartEvent1) {
                    count1.incrementAndGet();
                    latch1.countDown();
                }
                
                if (event instanceof SmartEvent2) {
                    count2.incrementAndGet();
                    latch2.countDown();
                    
                }
            }
        });
        
        for (int i = 0; i < 3; i++) {
            Assert.assertTrue(NotifyCenter.publishEvent(new SmartEvent1()));
            Assert.assertTrue(NotifyCenter.publishEvent(new SmartEvent2()));
        }
        
        latch1.await(3000L, TimeUnit.MILLISECONDS);
        latch2.await(3000L, TimeUnit.MILLISECONDS);
        
        Assert.assertEquals(3, count1.get());
        Assert.assertEquals(3, count2.get());
        
    }
    
    private static class TestSlowEvent1 extends SlowEvent {
    
    }
    
    private static class TestSlowEvent2 extends SlowEvent {
    
    }
    
    @Test
    public void testMutipleSlowEventsListenedBySubscriber() throws Exception {
        
        NotifyCenter.registerToSharePublisher(TestSlowEvent1.class);
        NotifyCenter.registerToSharePublisher(TestSlowEvent2.class);
        
        final AtomicInteger count1 = new AtomicInteger(0);
        final AtomicInteger count2 = new AtomicInteger(0);
        
        final CountDownLatch latch1 = new CountDownLatch(3);
        final CountDownLatch latch2 = new CountDownLatch(3);
        
        NotifyCenter.registerSubscriber(new Subscriber<TestSlowEvent1>() {
            @Override
            public void onEvent(TestSlowEvent1 event) {
                count1.incrementAndGet();
                latch1.countDown();
            }
            
            @Override
            public Class<? extends Event> subscribeType() {
                return TestSlowEvent1.class;
            }
        });
        
        NotifyCenter.registerSubscriber(new Subscriber<TestSlowEvent2>() {
            @Override
            public void onEvent(TestSlowEvent2 event) {
                count2.incrementAndGet();
                latch2.countDown();
                
            }
            
            @Override
            public Class<? extends Event> subscribeType() {
                return TestSlowEvent2.class;
            }
        });
        
        for (int i = 0; i < 3; i++) {
            Assert.assertTrue(NotifyCenter.publishEvent(new TestSlowEvent1()));
            Assert.assertTrue(NotifyCenter.publishEvent(new TestSlowEvent2()));
        }
        
        ThreadUtils.sleep(2000L);
        
        latch1.await(3000L, TimeUnit.MILLISECONDS);
        latch2.await(3000L, TimeUnit.MILLISECONDS);
        
        Assert.assertEquals(3, count1.get());
        Assert.assertEquals(3, count2.get());
        
    }
    
    private static class TestSlowEvent3 extends SlowEvent {
    
    }
    
    private static class TestSlowEvent4 extends SlowEvent {
    
    }
    
    @Test
    public void testMutipleSlowEventsListenedBySmartsubscriber() throws Exception {
        
        NotifyCenter.registerToSharePublisher(TestSlowEvent3.class);
        NotifyCenter.registerToSharePublisher(TestSlowEvent4.class);
        
        final AtomicInteger count1 = new AtomicInteger(0);
        final AtomicInteger count2 = new AtomicInteger(0);
        
        final CountDownLatch latch1 = new CountDownLatch(3);
        final CountDownLatch latch2 = new CountDownLatch(3);
        
        NotifyCenter.registerSubscriber(new SmartSubscriber() {
            
            @Override
            public void onEvent(Event event) {
                if (event instanceof TestSlowEvent3) {
                    count1.incrementAndGet();
                    latch1.countDown();
                }
                
                if (event instanceof TestSlowEvent4) {
                    count2.incrementAndGet();
                    latch2.countDown();
                }
            }
            
            @Override
            public List<Class<? extends Event>> subscribeTypes() {
                List<Class<? extends Event>> subTypes = new ArrayList<Class<? extends Event>>();
                subTypes.add(TestSlowEvent3.class);
                subTypes.add(TestSlowEvent4.class);
                return subTypes;
            }
        });
        
        for (int i = 0; i < 3; i++) {
            Assert.assertTrue(NotifyCenter.publishEvent(new TestSlowEvent3()));
            Assert.assertTrue(NotifyCenter.publishEvent(new TestSlowEvent4()));
        }
        
        ThreadUtils.sleep(2000L);
        
        latch1.await(3000L, TimeUnit.MILLISECONDS);
        latch2.await(3000L, TimeUnit.MILLISECONDS);
        
        Assert.assertEquals(3, count1.get());
        Assert.assertEquals(3, count2.get());
        
    }
    
    private static class TestSlowEvent5 extends SlowEvent {
    
    }
    
    private static class TestEvent6 extends Event {
    
    }
    
    @Test
    public void testMutipleKindsEventsCanListenBySmartsubscriber() throws Exception {
        
        NotifyCenter.registerToSharePublisher(TestSlowEvent5.class);
        NotifyCenter.registerToPublisher(TestEvent6.class, 1024);
        
        final AtomicInteger count1 = new AtomicInteger(0);
        final AtomicInteger count2 = new AtomicInteger(0);
        
        final CountDownLatch latch1 = new CountDownLatch(3);
        final CountDownLatch latch2 = new CountDownLatch(3);
        
        NotifyCenter.registerSubscriber(new SmartSubscriber() {
            
            @Override
            public void onEvent(Event event) {
                if (event instanceof TestSlowEvent5) {
                    count1.incrementAndGet();
                    latch1.countDown();
                }
                
                if (event instanceof TestEvent6) {
                    count2.incrementAndGet();
                    latch2.countDown();
                }
            }
            
            @Override
            public List<Class<? extends Event>> subscribeTypes() {
                List<Class<? extends Event>> subTypes = new ArrayList<Class<? extends Event>>();
                subTypes.add(TestSlowEvent5.class);
                subTypes.add(TestEvent6.class);
                return subTypes;
            }
        });
        
        for (int i = 0; i < 3; i++) {
            Assert.assertTrue(NotifyCenter.publishEvent(new TestSlowEvent5()));
            Assert.assertTrue(NotifyCenter.publishEvent(new TestEvent6()));
        }
        
        ThreadUtils.sleep(3000L);
        
        latch1.await(3000L, TimeUnit.MILLISECONDS);
        latch2.await(3000L, TimeUnit.MILLISECONDS);
        
        Assert.assertEquals(3, count1.get());
        Assert.assertEquals(3, count2.get());
        
    }
    
    private static class TestEvent7 extends Event {
    
    }
    
    @Test
    public void testPublishEventByNoSubscriber() {
        
        for (int i = 0; i < 3; i++) {
            Assert.assertFalse(NotifyCenter.publishEvent(new TestEvent7()));
        }
    }
}
