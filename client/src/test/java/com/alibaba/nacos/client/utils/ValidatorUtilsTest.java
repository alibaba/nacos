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

package com.alibaba.nacos.client.utils;

import com.alibaba.nacos.api.PropertyKeyConst;
import org.junit.Test;

import java.util.Properties;

public class ValidatorUtilsTest {

	@Test
	public void test_init_properties_only_serverAddr() {
		Properties properties = new Properties();
		properties.setProperty(PropertyKeyConst.SERVER_ADDR, "127.0.0.1:8848");
		ValidatorUtils.checkInitParam(properties);
	}

	@Test
	public void test_init_properties_only_endpoint() {
		Properties properties = new Properties();
		properties.setProperty(PropertyKeyConst.ENDPOINT, "http://127.0.0.1:8848");
		ValidatorUtils.checkInitParam(properties);
	}

	@Test(expected = IllegalArgumentException.class)
	public void test_init_properties_illegal() {
		Properties properties = new Properties();
		ValidatorUtils.checkInitParam(properties);
	}

	@Test
	public void test_context_path_legal() {
		String contextPath1 = "/nacos";
		ValidatorUtils.checkContextPath(contextPath1);
		String contextPath2 = "nacos";
		ValidatorUtils.checkContextPath(contextPath2);
		String contextPath3 = "/";
		ValidatorUtils.checkContextPath(contextPath3);
		String contextPath4 = "";
		ValidatorUtils.checkContextPath(contextPath4);
	}

	@Test(expected = IllegalArgumentException.class)
	public void test_context_path_illegal_1() {
		String contextPath1 = "//nacos/";
		ValidatorUtils.checkContextPath(contextPath1);
	}

	@Test(expected = IllegalArgumentException.class)
	public void test_context_path_illegal_2() {
		String contextPath2 = "/nacos//";
		ValidatorUtils.checkContextPath(contextPath2);
	}

	@Test(expected = IllegalArgumentException.class)
	public void test_context_path_illegal_3() {
		String contextPath3 = "///";
		ValidatorUtils.checkContextPath(contextPath3);
	}

	@Test(expected = IllegalArgumentException.class)
	public void test_context_path_illegal_4() {
		String contextPath4 = "//";
		ValidatorUtils.checkContextPath(contextPath4);
	}

	@Test
	public void test_server_addr() {
		String serverAddr = "http://127.0.0.1:8848";
		ValidatorUtils.checkServerAddr(serverAddr);
		String serverAddrs = "https://127.0.0.1:8848,https://127.0.0.1:80,https://127.0.0.1:8809";
		ValidatorUtils.checkServerAddr(serverAddrs);
	}

	@Test
	public void test_server_addr_k8s() {
		String serverAddr = "https://busybox-1.busybox-subdomain.default.svc.cluster.local:80";
		ValidatorUtils.checkServerAddr(serverAddr);
		String serverAddrs = "busybox-1.busybox-subdomain.default.svc.cluster.local:80,busybox-1.busybox-subdomain.default.svc.cluster.local:8111, busybox-1.busybox-subdomain.default.svc.cluster.local:8098";
		ValidatorUtils.checkServerAddr(serverAddrs);
	}

	@Test(expected = IllegalArgumentException.class)
	public void test_server_addr_err() {
		String serverAddr = "127.0.0.1";
		ValidatorUtils.checkServerAddr(serverAddr);
	}

	@Test(expected = IllegalArgumentException.class)
	public void test_server_addr_illegal_err() {
		String serverAddr = "127.0.0.1:";
		ValidatorUtils.checkServerAddr(serverAddr);
	}

	@Test(expected = IllegalArgumentException.class)
	public void test_server_addrs_err() {
		String serverAddrs = "127.0.0.1:8848,127.0.0.1,127.0.0.1:8809";
		ValidatorUtils.checkServerAddr(serverAddrs);
	}

	@Test(expected = IllegalArgumentException.class)
	public void test_server_addr_k8s_err() {
		String serverAddr = "busybox-1.busybox-subdomain.default.svc.cluster.local";
		ValidatorUtils.checkServerAddr(serverAddr);
	}

	@Test(expected = IllegalArgumentException.class)
	public void test_server_addrs_k8s_err() {
		String serverAddrs = "busybox-1.busybox-subdomain.default.svc.cluster.local,busybox-1.busybox-subdomain.default.svc.cluster.local:8111, busybox-1.busybox-subdomain.default.svc.cluster.local:8098";
		ValidatorUtils.checkServerAddr(serverAddrs);
	}

}
