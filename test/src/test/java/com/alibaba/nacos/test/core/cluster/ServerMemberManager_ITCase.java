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

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.alibaba.nacos.Nacos;
import com.alibaba.nacos.common.http.HttpClientManager;
import com.alibaba.nacos.common.http.HttpUtils;
import com.alibaba.nacos.common.http.NSyncHttpClient;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.core.cluster.IsolationEvent;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.NodeState;
import com.alibaba.nacos.core.cluster.RecoverEvent;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.core.notify.Event;
import com.alibaba.nacos.core.notify.NotifyCenter;
import com.alibaba.nacos.core.notify.listener.Subscribe;
import com.alibaba.nacos.core.utils.Commons;
import com.alibaba.nacos.core.utils.GenericType;
import com.alibaba.nacos.core.utils.InetUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@RunWith(SpringRunner.class)
@SpringBootTest(classes = Nacos.class, properties = {"server.servlet.context-path=/nacos", "server.port=7001"},
		webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
public class ServerMemberManager_ITCase {

	@Autowired
	private ServerMemberManager memberManager;

	@LocalServerPort
	private int port;

	private static NSyncHttpClient httpClient;

	@Before
	public void init() throws Exception {
		TimeUnit.SECONDS.sleep(5L);
		if (httpClient == null) {
			httpClient = HttpClientManager.newSyncHttpClient(ServerMemberManager_ITCase.class.getCanonicalName());
		}
	}

	@After
	public void after() throws Exception {
		String url = HttpUtils.buildUrl(false, "localhost:" + memberManager.getSelf().getPort(),
				memberManager.getContextPath(),
				Commons.NACOS_CORE_CONTEXT,
				"/cluster/server/leave");
		RestResult<String> result = httpClient.post(url, Header.EMPTY, Query.EMPTY,
				Collections.singletonList(Member
						.builder()
						.ip("1.1.1.1")
						.port(80)
						.build()),
				new GenericType<RestResult<String>>(){}.getType());
		System.out.println(result);
		System.out.println(memberManager.getServerList());
		Assert.assertTrue(result.ok());
	}

	@Test
	public void test_a_member_join_no_sync() throws Exception {
		Collection<Member> members = memberManager.allMembers();
		Assert.assertEquals(members.size(), 1);
		Assert.assertEquals(InetUtils.getSelfIp() + ":" + port, memberManager.getSelf().getAddress());

		RestResult<String> result = memberJoin();

		Assert.assertTrue(result.ok());
		members = memberManager.allMembers();
		Assert.assertEquals(2, members.size());
		members.removeIf(member -> memberManager.isSelf(member));
		Assert.assertEquals(members.size(), 1);
		Member newJoin = members.iterator().next();
		Assert.assertEquals(newJoin.getIp(), "1.1.1.1");
		Assert.assertEquals(newJoin.getPort(), 80);
	}

	@Test
	public void test_b_member_join_with_sync() throws Exception {
		Collection<Member> members = memberManager.allMembers();
		Assert.assertEquals(1, members.size());
		Assert.assertEquals(InetUtils.getSelfIp() + ":" + port, memberManager.getSelf().getAddress());

		RestResult<String> result = memberJoin();

		Assert.assertTrue(result.ok());
		Collection<String> remoteServer = JSON.parseObject(result.getData(), new TypeReference<List<String>>(){});
		Assert.assertTrue(StringUtils.startsWith(remoteServer.iterator().next(), memberManager.getSelf().getAddress()));
		members = memberManager.allMembers();
		Assert.assertEquals(2, members.size());
	}

	@Test
	public void test_d_member_isolation() throws Exception {
		String url = HttpUtils.buildUrl(false, "localhost:" + memberManager.getSelf().getPort(),
				memberManager.getContextPath(),
				Commons.NACOS_CORE_CONTEXT,
				"/cluster/isolation");
		RestResult<String> result = httpClient.post(url, Header.EMPTY, Query.EMPTY, null,
				new GenericType<RestResult<String>>(){}.getType());
		Assert.assertTrue(result.ok());

		CountDownLatch latch = new CountDownLatch(1);

		NotifyCenter.registerSubscribe(new Subscribe<IsolationEvent>() {

			@Override
			public void onEvent(IsolationEvent event) {
				latch.countDown();
			}

			@Override
			public Class<? extends Event> subscribeType() {
				return IsolationEvent.class;
			}
		});

		latch.await();

		url = HttpUtils.buildUrl(false, "localhost:" + memberManager.getSelf().getPort(),
				memberManager.getContextPath(),
				Commons.NACOS_CORE_CONTEXT,
				"/cluster/server/health");
		result = httpClient.get(url, Header.EMPTY, Query.EMPTY, new GenericType<RestResult<String>>(){}.getType());
		Assert.assertTrue(result.ok());
		Assert.assertEquals(NodeState.ISOLATION.name(), result.getData());
	}

	@Test
	public void test_e_member_recover() throws Exception {
		String url = HttpUtils.buildUrl(false, "localhost:" + memberManager.getSelf().getPort(),
				memberManager.getContextPath(),
				Commons.NACOS_CORE_CONTEXT,
				"/cluster/recover");
		RestResult<String> result = httpClient.post(url, Header.EMPTY, Query.EMPTY, null,
				new GenericType<RestResult<String>>(){}.getType());
		Assert.assertTrue(result.ok());

		CountDownLatch latch = new CountDownLatch(1);

		NotifyCenter.registerSubscribe(new Subscribe<RecoverEvent>() {

			@Override
			public void onEvent(RecoverEvent event) {
				latch.countDown();
			}

			@Override
			public Class<? extends Event> subscribeType() {
				return RecoverEvent.class;
			}
		});

		latch.await();

		url = HttpUtils.buildUrl(false, "localhost:" + memberManager.getSelf().getPort(),
				memberManager.getContextPath(),
				Commons.NACOS_CORE_CONTEXT,
				"/cluster/server/health");
		result = httpClient.get(url, Header.EMPTY, Query.EMPTY, new GenericType<RestResult<String>>(){}.getType());
		Assert.assertTrue(result.ok());
		Assert.assertEquals(NodeState.UP.name(), result.getData());
	}

	private RestResult<String> memberJoin() throws Exception {
		final String url = HttpUtils.buildUrl(false, "localhost:" + memberManager.getSelf().getPort(),
				memberManager.getContextPath(),
				Commons.NACOS_CORE_CONTEXT,
				"/cluster/server/report");
		return httpClient.post(url, Header.EMPTY, Query.newInstance().addParam("sync", true), Member
						.builder()
						.ip("1.1.1.1")
						.port(80)
						.build(),
				new GenericType<RestResult<String>>(){}.getType());
	}

}
