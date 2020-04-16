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

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class NotifyCenter_ITCase {

	private static class TestSlowEvent implements SlowEvent {

		@Override
		public Class<? extends Event> eventType() {
			return TestSlowEvent.class;
		}
	}

	private static class Test2SlowEvent implements Event {

		@Override
		public Class<? extends Event> eventType() {
			return Test2SlowEvent.class;
		}
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

	private static final Subscribe<Test2SlowEvent> subscribe2 = new Subscribe<Test2SlowEvent>() {
		@Override
		public void onEvent(Test2SlowEvent event) {

		}

		@Override
		public Class<? extends Event> subscribeType() {
			return Test2SlowEvent.class;
		}
	};

	static {
		NotifyCenter.registerToSharePublisher(TestSlowEvent::new, TestSlowEvent.class);
		NotifyCenter.registerToSharePublisher(Test2SlowEvent::new, Test2SlowEvent.class);

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
		CountDownLatch latch = new CountDownLatch(2);
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
		NotifyCenter.registerSubscribe(new Subscribe<Test2SlowEvent>() {
			@Override
			public void onEvent(Test2SlowEvent event) {
				latch.countDown();
				count.incrementAndGet();
			}

			@Override
			public Class<? extends Event> subscribeType() {
				return Test2SlowEvent.class;
			}
		});

		latch.await();

		Assert.assertEquals(2, count.get());
	}

}
