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

package com.alibaba.nacos.test.core.notify;

import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.notify.SlowEvent;
import com.alibaba.nacos.common.notify.listener.Subscriber;
import com.alibaba.nacos.common.notify.listener.SmartSubscriber;
import com.alibaba.nacos.common.utils.ThreadUtils;
import org.junit.Assert;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@FixMethodOrder(value = MethodSorters.NAME_ASCENDING)
public class NotifyCenter_ITCase {

	private static class TestSlowEvent extends SlowEvent {
        @Override
        public long sequence() {
            return System.currentTimeMillis();
        }
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
	public void test_a_event_can_listen() throws Exception {

		NotifyCenter.registerToSharePublisher(TestSlowEvent.class);
		NotifyCenter.registerToPublisher(TestEvent.class, 8);

		final CountDownLatch latch = new CountDownLatch(2);
		final AtomicInteger count = new AtomicInteger(0);

        NotifyCenter.registerSubscriber(new Subscriber<TestSlowEvent>() {
            @Override
            public void onEvent(TestSlowEvent event) {
                try {
                    System.out.println(event);
                    count.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            }

            @Override
            public Class<? extends Event> subscriberType() {
                return TestSlowEvent.class;
            }
        });

        NotifyCenter.registerSubscriber(new Subscriber<TestEvent>() {
            @Override
            public void onEvent(TestEvent event) {
                try {
                    System.out.println(event);
                    count.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            }

            @Override
            public Class<? extends Event> subscriberType() {
                return TestEvent.class;
            }
        });

		Assert.assertTrue(NotifyCenter.publishEvent(new TestEvent()));
		Assert.assertTrue(NotifyCenter.publishEvent(new TestSlowEvent()));

		ThreadUtils.sleep(5_000L);

		System.out.println("TestEvent event num : " + NotifyCenter.getPublisher(TestEvent.class).currentEventSize());
		System.out.println("TestSlowEvent event num : " + NotifyCenter.getPublisher(TestSlowEvent.class).currentEventSize());

		latch.await(5_000L, TimeUnit.MILLISECONDS);

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
	public void test_b_ignore_expire_event() throws Exception {
		NotifyCenter.registerToPublisher(ExpireEvent.class, 16);
		AtomicInteger count = new AtomicInteger(0);

		NotifyCenter.registerSubscriber(new Subscriber<ExpireEvent>() {
            @Override
            public void onEvent(ExpireEvent event) {
                count.incrementAndGet();
            }

            @Override
            public Class<? extends Event> subscriberType() {
                return ExpireEvent.class;
            }

            public boolean ignoreExpireEvent() {
                return true;
            }

        });


		for (int i = 0; i < 3; i ++) {
			Assert.assertTrue(NotifyCenter.publishEvent(new ExpireEvent()));
		}

		latch.await(10_000L, TimeUnit.MILLISECONDS);
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
	public void test_c_no_ignore_expire_event() throws Exception {
		NotifyCenter.registerToPublisher(NoExpireEvent.class, 16);
		AtomicInteger count = new AtomicInteger(0);

		NotifyCenter.registerSubscriber(new Subscriber() {
            @Override
            public void onEvent(Event event) {
                System.out.println(event);
                count.incrementAndGet();
                latch2.countDown();
            }

            @Override
            public Class<? extends Event> subscriberType() {
                return NoExpireEvent.class;
            }
        });

		for (int i = 0; i < 3; i ++) {
			Assert.assertTrue(NotifyCenter.publishEvent(new NoExpireEvent()));
		}

		latch2.await(10_000L, TimeUnit.MILLISECONDS);
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

        @Override
        public long sequence() {
            return System.currentTimeMillis();
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

        @Override
        public long sequence() {
            return System.currentTimeMillis();
        }
	}

	@Test
	public void test_k_two_slowEvent() throws Exception {
		NotifyCenter.registerToSharePublisher(SlowE1.class);
		NotifyCenter.registerToSharePublisher(SlowE2.class);

		CountDownLatch latch1 = new CountDownLatch(15);
		CountDownLatch latch2 = new CountDownLatch(15);

		String[] values = new String[] {null, null};

        NotifyCenter.registerSubscriber(new Subscriber<SlowE1>() {

            @Override
            public void onEvent(SlowE1 event) {
                ThreadUtils.sleep(1000L);
                System.out.println(event);
                values[0] = event.info;
                latch1.countDown();
            }

            @Override
            public Class<? extends Event> subscriberType() {
                return SlowE1.class;
            }
        });

		NotifyCenter.registerSubscriber(new Subscriber<SlowE2>() {
            @Override
            public void onEvent(SlowE2 event) {
                System.out.println(event);
                values[1] = event.info;
                latch2.countDown();
            }

            @Override
            public Class<? extends Event> subscriberType() {
                return SlowE2.class;
            }
        });

		for (int i = 0; i < 30; i ++) {
			NotifyCenter.publishEvent(new SlowE1());
			NotifyCenter.publishEvent(new SlowE2());
		}

		latch1.await();
		latch2.await();

		Assert.assertEquals("SlowE1", values[0]);
		Assert.assertEquals("SlowE2", values[1]);

	}


	static class SmartSlowEvent1 extends SlowEvent{

        @Override
        public long sequence() {
            return System.currentTimeMillis();
        }
    }

    static class SmartSlowEvent2 extends SlowEvent{
        @Override
        public long sequence() {
            return System.currentTimeMillis();
        }
    }

    @Test
    public void test_can_notify_smart_slowEvent() throws Exception {

	    AtomicInteger count1 = new AtomicInteger(0);
	    AtomicInteger count2 = new AtomicInteger(0);

        CountDownLatch latch1 = new CountDownLatch(5);


        NotifyCenter.registerToSharePublisher(SmartSlowEvent1.class);
        NotifyCenter.registerToSharePublisher(SmartSlowEvent2.class);


        NotifyCenter.registerSubscriber(new SmartSubscriber() {
            @Override
            public boolean canNotify(Event event) {
                if (event instanceof SmartSlowEvent1) {
                    return true;
                }

                return false;
            }

            @Override
            public void onEvent(Event event) {
                if (event instanceof SmartSlowEvent1) {
                    count1.incrementAndGet();
                    latch1.countDown();
                }

                if (event instanceof SmartSlowEvent2) {
                    count2.incrementAndGet();
                }
            }
        });

        for (int i = 0; i < 3; i++) {
            Assert.assertTrue(NotifyCenter.publishEvent(new SmartSlowEvent1()));
            Assert.assertTrue(NotifyCenter.publishEvent(new SmartSlowEvent2()));
        }

        latch1.await(3_000L, TimeUnit.MILLISECONDS);

        Assert.assertEquals(3, count1.get());
        Assert.assertEquals(0, count2.get());

    }
}
