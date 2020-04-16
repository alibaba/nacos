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

import com.alibaba.nacos.common.utils.DiskUtils;
import com.alibaba.nacos.core.cluster.MemberLookup;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.core.cluster.lookup.AddressServerMemberLookup;
import com.alibaba.nacos.core.cluster.lookup.FileConfigMemberLookup;
import com.alibaba.nacos.core.cluster.lookup.GossipMemberLookup;
import com.alibaba.nacos.core.cluster.lookup.LookupFactory;
import com.alibaba.nacos.core.utils.ApplicationUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.mock.web.MockServletContext;

import java.io.File;
import java.nio.file.Paths;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class MemberLookup_ITCase {

	static final String path = Paths
			.get(System.getProperty("user.home"), "/look").toString();

	static final String name = "cluster.conf";

	static final ServerMemberManager memberManager = new ServerMemberManager(new MockServletContext());

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
	}

	@After
	public void after() throws Exception {
		DiskUtils.deleteDirectory(path);
	}

	@Test
	public void test_lookup_default_file_config() throws Exception {
		try {
			LookupFactory.initLookUp(memberManager);
		} catch (Throwable ignore) { }
		MemberLookup lookup = LookupFactory.getLookUp();
		System.out.println(lookup);
		Assert.assertTrue(lookup instanceof FileConfigMemberLookup);
	}

	@Test
	public void test_lookup_standalone() throws Exception {
		System.setProperty("nacos.standalone", "true");
		try {
			LookupFactory.initLookUp(memberManager);
		} catch (Throwable ignore) {

		}
		MemberLookup lookup = LookupFactory.getLookUp();
		System.out.println(lookup);
		Assert.assertTrue(lookup instanceof FileConfigMemberLookup);
	}

	@Test
	public void test_lookup_address_server() throws Exception {
		System.setProperty("nacos.standalone", "false");
		System.out.println(ApplicationUtils.getClusterConfFilePath());
		DiskUtils.deleteFile(Paths.get(path, "conf").toString(), "cluster.conf");
		System.out.println(new File(ApplicationUtils.getClusterConfFilePath()).exists());
		try {
			LookupFactory.initLookUp(memberManager);
		} catch (Throwable ignore) {

		}
		MemberLookup lookup = LookupFactory.getLookUp();
		System.out.println(lookup);
		Assert.assertTrue(lookup instanceof AddressServerMemberLookup);
	}

	@Test
	public void test_lookup_gossip() throws Exception {
		System.setProperty("nacos.standalone", "false");
		System.setProperty("nacos.gossip", "true");
		System.out.println(ApplicationUtils.getClusterConfFilePath());
		DiskUtils.deleteFile(Paths.get(path, "conf").toString(), "cluster.conf");
		System.out.println(new File(ApplicationUtils.getClusterConfFilePath()).exists());
		try {
			LookupFactory.initLookUp(memberManager);
		} catch (Throwable ignore) {

		}
		System.setProperty("nacos.gossip", "false");
		MemberLookup lookup = LookupFactory.getLookUp();
		System.out.println(lookup);
		Assert.assertTrue(lookup instanceof GossipMemberLookup);
	}

}
