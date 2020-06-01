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
import com.alibaba.nacos.core.utils.DiskUtils;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.core.cluster.lookup.AddressServerMemberLookup;
import com.alibaba.nacos.core.cluster.lookup.FileConfigMemberLookup;
import com.alibaba.nacos.core.cluster.lookup.LookupFactory;
import com.alibaba.nacos.core.cluster.MemberLookup;
import com.alibaba.nacos.core.cluster.lookup.StandaloneMemberLookup;
import com.alibaba.nacos.core.utils.ApplicationUtils;
import com.alibaba.nacos.core.utils.InetUtils;
import com.alibaba.nacos.test.BaseTest;
import org.apache.commons.lang3.StringUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.mock.web.MockServletContext;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Map;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@FixMethodOrder(value = MethodSorters.NAME_ASCENDING)
public class MemberLookup_ITCase extends BaseTest {

	static final String path = Paths.get(System.getProperty("user.home"), "/member_look")
			.toString();

	static final String name = "cluster.conf";

	ServerMemberManager memberManager;

	@Before
	public void before() throws Exception {
		System.setProperty("nacos.home", path);
		ApplicationUtils.injectEnvironment(new StandardEnvironment());
		ApplicationUtils.setIsStandalone(false);
		System.out.println(ApplicationUtils.getStandaloneMode());

		System.out.println(Arrays.toString(LookupFactory.LookupType.values()));
		DiskUtils.forceMkdir(path);
		DiskUtils.forceMkdir(Paths.get(path, "conf").toString());
		File file = Paths.get(path, "conf", name).toFile();
		DiskUtils.touch(file);
		String ip = InetUtils.getSelfIp();
		DiskUtils.writeFile(file, (ip + ":8848," + ip + ":8847," + ip + ":8849").getBytes(
				StandardCharsets.UTF_8), false);

		try {
			memberManager = new ServerMemberManager(
					new MockServletContext());
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}

	@After
	public void after() throws Exception {
		try {
			memberManager.shutdown();
		} catch (Throwable ex) {
			ex.printStackTrace();
		}
		DiskUtils.deleteDirectory(path);
	}

	@Test
	public void test_a_lookup_file_config() throws Exception {
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
	public void test_b_lookup_standalone() throws Exception {
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
	}

	@Test
	public void test_c_lookup_address_server() throws Exception {
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
		} catch (NacosException e) {
			System.out.println(e.getErrMsg());
			Assert.assertTrue(StringUtils.containsIgnoreCase(e.getErrMsg(), "jmenv.tbsite.net"));
		}
	}

	private void func(MemberLookup lookup) throws Exception {
		func(lookup, 3);
	}

	private void func(MemberLookup lookup, int expectSize) throws Exception {
		lookup.start();
		Map<String, Member> tmp = memberManager.getServerList();
		System.out.println(lookup + " : " + tmp);
		Assert.assertEquals(expectSize, tmp.size());
	}

}
