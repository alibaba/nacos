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
import com.alibaba.nacos.core.cluster.MemberUtils;
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ServerMemberManager_ITCase {

	private ServerMemberManager memberManager;

	{
		try {
			memberManager = new ServerMemberManager(new MockServletContext());
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Before
	public void init() throws Exception {
		ApplicationUtils.setIsStandalone(true);
		ApplicationUtils.injectEnvironment(new StandardEnvironment());
	}

	@After
	public void after() throws Exception {
		memberManager.shutdown();
	}

	@Test
	public void test_k_isFirst() {
		String firstIp = "127.0.0.1:8847";
		String secondIp = "127.0.0.1:8847";
		String thirdIp = "127.0.0.1:8847";
		ConcurrentSkipListMap<String, Member> map = new ConcurrentSkipListMap<>();
		map.put(secondIp, Member.builder()
				.ip("127.0.0.1")
				.port(8847)
				.build());
		map.put(firstIp, Member.builder()
				.ip("127.0.0.1")
				.port(8848)
				.build());
		map.put(thirdIp, Member.builder()
				.ip("127.0.0.1")
				.port(8849)
				.build());

		List<Member> members = new ArrayList<Member>(map.values());
		Collections.sort(members);
		List<String> ss = MemberUtils.simpleMembers(members);

		Assert.assertEquals(ss.get(0), members.get(0).getAddress());
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

		latch.await(10_000L, TimeUnit.MILLISECONDS);

		Assert.assertEquals(1, integer.get());
	}

}
