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

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.utils.DiskUtils;
import com.alibaba.nacos.common.utils.Observable;
import com.alibaba.nacos.common.utils.Observer;
import com.alibaba.nacos.common.utils.ThreadUtils;
import com.alibaba.nacos.config.server.utils.ThreadUtil;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.MemberUtils;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.core.cluster.lookup.AddressServerMemberLookup;
import com.alibaba.nacos.core.cluster.lookup.DiscoveryMemberLookup;
import com.alibaba.nacos.core.cluster.lookup.FileConfigMemberLookup;
import com.alibaba.nacos.core.cluster.lookup.LookupFactory;
import com.alibaba.nacos.core.cluster.lookup.MemberLookup;
import com.alibaba.nacos.core.cluster.lookup.StandaloneMemberLookup;
import com.alibaba.nacos.core.utils.ApplicationUtils;
import com.alibaba.nacos.core.utils.GlobalExecutor;
import com.alibaba.nacos.core.utils.InetUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.mock.web.MockServletContext;

import java.io.File;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class MemberLookup_ITCase {

	static final String path = Paths.get(System.getProperty("user.home"), "/look")
			.toString();

	static final String name = "cluster.conf";

	static final ServerMemberManager memberManager = new ServerMemberManager(
			new MockServletContext());

	static {
		System.setProperty("nacos.home", path);
		ApplicationUtils.injectEnvironment(new StandardEnvironment());
		ApplicationUtils.setIsStandalone(false);
		try {
			memberManager.init();
		}
		catch (Throwable ignore) {
		}
		System.out.println(ApplicationUtils.getStandaloneMode());

		System.out.println(Arrays.toString(LookupFactory.LookupType.values()));
	}

	@Before
	public void before() throws Exception {
		DiskUtils.forceMkdir(path);
		DiskUtils.forceMkdir(Paths.get(path, "conf").toString());
		File file = Paths.get(path, "conf", name).toFile();
		DiskUtils.touch(file);
		String ip = InetUtils.getSelfIp();
		DiskUtils.writeFile(file, (ip + ":8848," + ip + ":8847," + ip + ":8849").getBytes(
				StandardCharsets.UTF_8), false);
	}

	@After
	public void after() throws Exception {
		DiskUtils.deleteDirectory(path);
	}

	@Test
	public void test_lookup_file_config() throws Exception {
		try {
			LookupFactory.createLookUp(memberManager);
		}
		catch (Throwable ignore) {
		}
		MemberLookup lookup = LookupFactory.getLookUp();
		System.out.println(lookup);
		Assert.assertTrue(lookup instanceof FileConfigMemberLookup);
		func(lookup);
	}

	@Test
	public void test_lookup_standalone() throws Exception {
		ApplicationUtils.setIsStandalone(true);
		try {
			LookupFactory.createLookUp(memberManager);
		}
		catch (Throwable ignore) {

		} finally {
			ApplicationUtils.setIsStandalone(false);
		}
		MemberLookup lookup = LookupFactory.getLookUp();
		System.out.println(lookup);
		Assert.assertTrue(lookup instanceof StandaloneMemberLookup);
		func(lookup, 1);
	}

	@Test
	public void test_lookup_address_server() throws Exception {
		ApplicationUtils.setIsStandalone(false);
		System.out.println(ApplicationUtils.getClusterConfFilePath());
		DiskUtils.deleteFile(Paths.get(path, "conf").toString(), "cluster.conf");
		System.out.println(new File(ApplicationUtils.getClusterConfFilePath()).exists());
		try {
			LookupFactory.createLookUp(memberManager);
		}
		catch (Throwable ignore) {
		}
		MemberLookup lookup = LookupFactory.getLookUp();
		System.out.println(lookup);
		Assert.assertTrue(lookup instanceof AddressServerMemberLookup);
		try {
			func(lookup);
		} catch (NacosException ignore) {
			Assert.assertEquals("ErrCode:500, ErrMsg:jmenv.tbsite.net", ignore.toString());
		}
	}

	@Test
	public void test_lookup_discovery() throws Exception {
		ApplicationUtils.setIsStandalone(false);
		System.setProperty("nacos.member.discovery", "true");
		System.out.println(ApplicationUtils.getClusterConfFilePath());
		System.out.println(new File(ApplicationUtils.getClusterConfFilePath()).exists());
		try {
			LookupFactory.createLookUp(memberManager);
		}
		catch (Throwable ignore) {

		}
		System.setProperty("nacos.member.discovery", "false");
		MemberLookup lookup = LookupFactory.getLookUp();
		System.out.println(lookup);
		Assert.assertTrue(lookup instanceof DiscoveryMemberLookup);
		func(lookup);
	}

	private void func(MemberLookup lookup) throws Exception {
		func(lookup, 3);
	}

	private void func(MemberLookup lookup, int expectSize) throws Exception {
		lookup.start();
		Map<String, Member> tmp = memberManager.getServerList();
		Assert.assertEquals(expectSize, tmp.size());
	}

	@Test
	public void test_lookup_file_change() throws Throwable {
		File file = Paths.get(path, "conf", name).toFile();

		CountDownLatch[] latches = new CountDownLatch[] {
				new CountDownLatch(1),
				new CountDownLatch(1),
				new CountDownLatch(1)
		};

		AtomicInteger index = new AtomicInteger(0);
		AtomicReference<Collection<Member>>[] reference = new AtomicReference[] {
				new AtomicReference<Collection<Member>>(Collections.emptyList()),
				new AtomicReference<Collection<Member>>(Collections.emptyList()),
				new AtomicReference<Collection<Member>>(Collections.emptyList())
		};

		FileConfigMemberLookup lookup = new FileConfigMemberLookup() {
			@Override
			public void afterLookup(Collection<Member> members) {
				System.out.println("test : " + members);
				int i = index.getAndIncrement();
				try {
					reference[i].set(members);
				} finally {
					latches[i].countDown();
				}
			}
		};

		lookup.start();

		String ip = InetUtils.getSelfIp();
		String ips = ip + ":8848," + ip + ":8847," + ip + ":8849";

		latches[0].await();

		Collection<Member> members = MemberUtils.readServerConf(ApplicationUtils.analyzeClusterConf(new StringReader(ips)));
		Set<Member> set = new HashSet<>(members);
		System.out.println("1 : " + reference[0].get());
		set.removeAll(reference[0].get());
		Assert.assertEquals(0, set.size());

		// test for write file -1

		ips = ip + ":8848," + ip + ":8847," + ip + ":8849," + ip + ":8850";
		DiskUtils.writeFile(file, ips.getBytes(StandardCharsets.UTF_8), false);
		latches[1].await();

		ThreadUtils.sleep(5_000L);
		members = MemberUtils.readServerConf(ApplicationUtils.analyzeClusterConf(new StringReader(ips)));
		set = new HashSet<>(members);
		System.out.println("2 : " + reference[1].get());
		set.removeAll(reference[1].get());
		Assert.assertEquals(0, set.size());

		// test for write file -2

		ips = ip + ":8848," + ip + ":8847," + ip + ":8849";
		DiskUtils.writeFile(file, ips.getBytes(StandardCharsets.UTF_8), false);
		latches[2].await();

		ThreadUtils.sleep(5_000L);
		members = MemberUtils.readServerConf(ApplicationUtils.analyzeClusterConf(new StringReader(ips)));
		set = new HashSet<>(members);
		System.out.println("3: " + reference[2].get());
		set.removeAll(reference[2].get());
		Assert.assertEquals(0, set.size());
	}

}
