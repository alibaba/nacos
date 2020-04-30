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

package com.alibaba.nacos.test.core.cluster;

import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.MemberChangeEvent;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.core.notify.Event;
import com.alibaba.nacos.core.notify.NotifyCenter;
import com.alibaba.nacos.core.notify.listener.Subscribe;
import com.alibaba.nacos.core.utils.ApplicationUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.mock.web.MockServletContext;

import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ServerMemberManager_ITCase {

	private ServerMemberManager memberManager = new ServerMemberManager(new MockServletContext());

	@Before
	public void init() throws Exception {
		ApplicationUtils.setIsStandalone(true);
		ApplicationUtils.injectEnvironment(new StandardEnvironment());
		memberManager.init();
	}

	@After
	public void after() throws Exception {
		memberManager.shutdown();
	}

	@Test
	public void test_a_member_change() throws Exception {

		AtomicInteger integer = new AtomicInteger(0);
		CountDownLatch latch = new CountDownLatch(1);

		NotifyCenter.registerSubscribe(new Subscribe<MemberChangeEvent>() {
			@Override
			public void onEvent(MemberChangeEvent event) {
				integer.incrementAndGet();
				latch.countDown();
			}

			@Override
			public Class<? extends Event> subscribeType() {
				return MemberChangeEvent.class;
			}
		});
		Collection<Member> members = memberManager.allMembers();

		System.out.println(members);

		memberManager.memberJoin(members);

		members.add(Member.builder()
				.ip("115.159.3.213")
				.port(8848)
				.build());

		boolean changed = memberManager.memberJoin(members);
		Assert.assertTrue(changed);

		latch.await();

		Assert.assertEquals(1, integer.get());
	}

}
