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

import com.alibaba.nacos.common.http.param.Query;
import org.junit.Assert;
import org.junit.Test;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class Query_ITCase {

	@Test public void test_query_str() throws Exception {
		Query query = Query.newInstance().addParam("key-1", "value-1")
				.addParam("key-2", "value-2");
		String s1 = query.toQueryUrl();
		String s2 = "key-1=" + URLEncoder.encode("value-1", StandardCharsets.UTF_8.name())
				+ "&key-2=" + URLEncoder.encode("value-2", StandardCharsets.UTF_8.name());
		Assert.assertEquals(s1, s2);
	}

}
