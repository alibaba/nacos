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

package com.alibaba.nacos.test.core;

import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.notify.listener.Subscriber;
import com.alibaba.nacos.sys.utils.InetUtils;
import org.junit.Assert;
import org.junit.Test;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static com.alibaba.nacos.sys.env.Constants.NACOS_SERVER_IP;


/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class InetUtils_ITCase {

	static {
		System.setProperty("nacos.core.inet.auto-refresh", "3");
		// For load InetUtils.class
		InetUtils.getSelfIP();
	}

	@Test
	public void test_InternetAddress_Change() throws Exception {
		String testIp = "1.1.1.1";
		System.setProperty(NACOS_SERVER_IP, testIp);
		CountDownLatch latch = new CountDownLatch(1);

		AtomicReference<String> reference = new AtomicReference<>(null);

		Subscriber<InetUtils.IPChangeEvent> subscribe = new Subscriber<InetUtils.IPChangeEvent>() {
			@Override
			public void onEvent(InetUtils.IPChangeEvent event) {
				if (Objects.nonNull(event.getOldIP())) {
					try {
						System.out.println(event);
						reference.set(event.getNewIP());
					}
					finally {
						latch.countDown();
					}
				}
			}

			@Override
			public Class<? extends Event> subscribeType() {
				return InetUtils.IPChangeEvent.class;
			}
		};

		NotifyCenter.registerSubscriber(subscribe);
		latch.await(10_000L, TimeUnit.MILLISECONDS);

		Assert.assertEquals(testIp, reference.get());
		Assert.assertEquals(testIp, InetUtils.getSelfIP());
	}

}
