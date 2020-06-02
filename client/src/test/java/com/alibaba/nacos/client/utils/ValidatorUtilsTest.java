package com.alibaba.nacos.client.utils;

import org.junit.Test;

public class ValidatorUtilsTest {

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
		String serverAddr = "127.0.0.1:8848";
		ValidatorUtils.checkServerAddr(serverAddr);
		String serverAddrs = "127.0.0.1:8848,127.0.0.1:80,127.0.0.1:8809";
		ValidatorUtils.checkServerAddr(serverAddrs);
	}

	@Test
	public void test_server_addr_k8s() {
		String serverAddr = "busybox-1.busybox-subdomain.default.svc.cluster.local:80";
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