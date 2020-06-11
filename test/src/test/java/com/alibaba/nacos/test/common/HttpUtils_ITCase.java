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

package com.alibaba.nacos.test.common;

import com.alibaba.nacos.common.http.HttpUtils;
import com.alibaba.nacos.common.http.handler.ResponseHandler;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.core.utils.ClassUtils;
import com.alibaba.nacos.core.utils.GenericType;
import org.junit.Assert;
import org.junit.Test;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class HttpUtils_ITCase {

	String exceptUrl = "http://127.0.0.1:8080/v1/api/test";

	private ArrayList<Integer> list = new ArrayList<Integer>(
			Arrays.asList(1, 2, 3, 4, 5, 6));

	@Test
	public void test_deserialization_type() throws Exception {
		String json = JacksonUtils.toJson(list);
		ArrayList<Integer> tmp = ResponseHandler.convert(json, new GenericType<List<Integer>>(){}.getType());
		Assert.assertEquals(list, tmp);
	}

	@Test
	public void test_rest_result() throws Exception {
		String json = "{\"code\":200,\"message\":null,\"data\":[{\"USERNAME\":\"nacos\",\"PASSWORD\":\"$2a$10$EuWPZHzz32dJN7jexM34MOeYirDdFAZm2kuWj7VEOJhhZkDrxfvUu\",\"ENABLED\":true}]}";
		RestResult<Object> result = ResponseHandler.convert(json, new GenericType<RestResult<Object>>(){}.getType());
		System.out.println(result);
	}

	@Test
	public void test_deserialization_class() throws Exception {
		String json = JacksonUtils.toJson(list);
		ArrayList<Integer> tmp = ResponseHandler.convert(json, ClassUtils.resolveGenericType(new GenericType<List<Integer>>(){}.getClass()));
		Assert.assertEquals(list, tmp);
	}


	@Test
	public void test_query_str() throws Exception {
		Query query = Query.newInstance().addParam("key-1", "value-1")
				.addParam("key-2", "value-2");
		String s1 = query.toQueryUrl();
		String s2 = "key-1=" + URLEncoder.encode("value-1", StandardCharsets.UTF_8.name())
				+ "&key-2=" + URLEncoder.encode("value-2", StandardCharsets.UTF_8.name());
		Assert.assertEquals(s1, s2);
	}

	@Test
	public void test_build_httpUrl() {
		String targetUrl = HttpUtils.buildUrl(false, "127.0.0.1:8080", "/v1/api/test");
		Assert.assertEquals(exceptUrl, targetUrl);
		targetUrl = HttpUtils.buildUrl(false, "127.0.0.1:8080", "v1/api/test");
		Assert.assertEquals(exceptUrl, targetUrl);
		targetUrl = HttpUtils.buildUrl(false, "127.0.0.1:8080", "/v1", "/api/test");
		Assert.assertEquals(exceptUrl, targetUrl);
		targetUrl = HttpUtils.buildUrl(false, "127.0.0.1:8080", "/v1", "/api", "/test");
		Assert.assertEquals(exceptUrl, targetUrl);
		targetUrl = HttpUtils.buildUrl(false, "127.0.0.1:8080", "/v1", "/api/", "/test");
		Assert.assertEquals(exceptUrl, targetUrl);
		targetUrl = HttpUtils.buildUrl(false, "127.0.0.1:8080", "/v1", "", "/api/", "/test");
		Assert.assertEquals(exceptUrl, targetUrl);
		targetUrl = HttpUtils.buildUrl(false, "127.0.0.1:8080", "/v1", "", null, "/api/", "/test");
		Assert.assertEquals(exceptUrl, targetUrl);
	}

	@Test(expected = IllegalArgumentException.class)
	public void test_build_httpUrl_fail_1() {
		String targetUrl = HttpUtils.buildUrl(false, "127.0.0.1:8080", "//v1/api/test");
		Assert.assertNotEquals(exceptUrl, targetUrl);
	}

	@Test(expected = IllegalArgumentException.class)
	public void test_build_httpUrl_fail_2() {
		String targetUrl = HttpUtils.buildUrl(false, "127.0.0.1:8080", "/v1", "/api//", "test");
		Assert.assertNotEquals(exceptUrl, targetUrl);
	}

}
