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

import com.alibaba.nacos.core.notify.Event;
import com.alibaba.nacos.core.notify.NotifyCenter;
import com.alibaba.nacos.core.notify.SlowEvent;
import com.alibaba.nacos.core.notify.listener.Subscribe;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class NotifyCenter_ITCase {

	private static class TestSlowEvent implements SlowEvent {

	}

	private static class TestEvent implements Event {

	}

	private static final Subscribe<TestSlowEvent> subscribe = new Subscribe<TestSlowEvent>() {
		@Override
		public void onEvent(TestSlowEvent event) {

		}

		@Override
		public Class<? extends Event> subscribeType() {
			return TestSlowEvent.class;
		}
	};

	private static final Subscribe<TestEvent> subscribe2 = new Subscribe<TestEvent>() {
		@Override
		public void onEvent(TestEvent event) {

		}

		@Override
		public Class<? extends Event> subscribeType() {
			return TestEvent.class;
		}
	};

	static {
		NotifyCenter.registerToSharePublisher(TestSlowEvent::new, TestSlowEvent.class);
		NotifyCenter.registerToPublisher(TestEvent::new, TestEvent.class, 8);

		NotifyCenter.registerSubscribe(subscribe);
		NotifyCenter.registerSubscribe(subscribe2);
	}

	@Test
	public void test_success_is_slow_event() {
		Assert.assertTrue(NotifyCenter.getSharePublisher().getSubscribes().contains(subscribe));
		Assert.assertFalse(NotifyCenter.getSharePublisher().getSubscribes().contains(subscribe2));
	}

	@Test
	public void test_event_can_listen() throws Exception {
		CountDownLatch latch = new CountDownLatch(1);
		CountDownLatch latch2 = new CountDownLatch(1);
		AtomicInteger count = new AtomicInteger(0);

		NotifyCenter.registerSubscribe(new Subscribe<TestSlowEvent>() {
			@Override
			public void onEvent(TestSlowEvent event) {
				latch.countDown();
				count.incrementAndGet();
			}

			@Override
			public Class<? extends Event> subscribeType() {
				return TestSlowEvent.class;
			}
		});
		NotifyCenter.registerSubscribe(new Subscribe<TestEvent>() {
			@Override
			public void onEvent(TestEvent event) {
				latch2.countDown();
				count.incrementAndGet();
			}

			@Override
			public Class<? extends Event> subscribeType() {
				return TestEvent.class;
			}
		});

		NotifyCenter.publishEvent(new TestEvent());
		NotifyCenter.publishEvent(new TestSlowEvent());

		NotifyCenter.stopDeferPublish();

		latch.await();
		latch2.await();

		Assert.assertEquals(2, count.get());
	}

	static CountDownLatch latch = new CountDownLatch(3);

	static class ExpireEvent implements Event {

		static AtomicLong sequence = new AtomicLong(3);

		private long no = sequence.getAndDecrement();

		@Override
		public long sequence() {
			latch.countDown();
			return no;
		}
	}

	@Test
	public void test_ignore_expire_event() throws Exception {
		NotifyCenter.registerToPublisher(ExpireEvent::new, ExpireEvent.class, 16);
		AtomicInteger count = new AtomicInteger(0);
		NotifyCenter.registerSubscribe(new Subscribe<ExpireEvent>() {
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

		for (int i = 0; i < 3; i ++) {
			NotifyCenter.publishEvent(new ExpireEvent());
		}

		latch.await();
		Assert.assertEquals(1, count.get());
	}

	static CountDownLatch latch2 = new CountDownLatch(3);

	static class NoExpireEvent implements Event {

		static AtomicLong sequence = new AtomicLong(3);

		private long no = sequence.getAndDecrement();

		@Override
		public long sequence() {
			latch2.countDown();
			return no;
		}
	}

	@Test
	public void test_no_ignore_expire_event() throws Exception {
		NotifyCenter.registerToPublisher(NoExpireEvent::new, NoExpireEvent.class, 16);
		AtomicInteger count = new AtomicInteger(0);
		NotifyCenter.registerSubscribe(new Subscribe<NoExpireEvent>() {
			@Override
			public void onEvent(NoExpireEvent event) {
				count.incrementAndGet();
			}

			@Override
			public Class<? extends Event> subscribeType() {
				return NoExpireEvent.class;
			}

		});

		for (int i = 0; i < 3; i ++) {
			NotifyCenter.publishEvent(new NoExpireEvent());
		}

		latch2.await();
		Assert.assertEquals(3, count.get());
	}

}
