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
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.core.cluster.lookup.AddressServerMemberLookup;
import com.alibaba.nacos.core.cluster.lookup.DiscoveryMemberLookup;
import com.alibaba.nacos.core.cluster.lookup.FileConfigMemberLookup;
import com.alibaba.nacos.core.cluster.lookup.LookupFactory;
import com.alibaba.nacos.core.cluster.lookup.MemberLookup;
import com.alibaba.nacos.core.cluster.lookup.StandaloneMemberLookup;
import com.alibaba.nacos.core.utils.ApplicationUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.mock.web.MockServletContext;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;

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
		System.setProperty("nacos.standalone", "false");
		ApplicationUtils.injectEnvironment(new StandardEnvironment());
		try {
			memberManager.init();
		}
		catch (Throwable ignore) {
		}
		System.out.println(ApplicationUtils.getStandaloneMode());
	}

	@Before
	public void before() throws Exception {
		DiskUtils.forceMkdir(path);
		DiskUtils.forceMkdir(Paths.get(path, "conf").toString());
		File file = Paths.get(path, "conf", name).toFile();
		DiskUtils.touch(file);
		DiskUtils.writeFile(file, "127.0.0.1:8848,127.0.0.1:8847,127.0.0.1:8849".getBytes(
				StandardCharsets.UTF_8), false);
	}

	@After
	public void after() throws Exception {
		DiskUtils.deleteDirectory(path);
	}

	@Test
	public void test_lookup_file_config() throws Exception {
		try {
			LookupFactory.createLookUp();
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
			LookupFactory.createLookUp();
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
			LookupFactory.createLookUp();
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
		System.setProperty("nacos.standalone", "false");
		System.setProperty("nacos.member.discovery", "true");
		System.out.println(ApplicationUtils.getClusterConfFilePath());
		System.out.println(new File(ApplicationUtils.getClusterConfFilePath()).exists());
		try {
			LookupFactory.createLookUp();
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
		CountDownLatch latch = new CountDownLatch(1);
		lookup.addObserver(new Observer() {
			@Override
			public void update(Observable o, Object arg) {
				try {
					Collection<Member> members = lookup.getMembers();
					Assert.assertEquals(expectSize, members.size());
				} finally {
					latch.countDown();
				}
			}
		});

		lookup.start();
		latch.await();
	}

}
