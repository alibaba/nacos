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

package com.alibaba.nacos.test;

import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.naming.healthcheck.RsInfo;
import org.junit.Test;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 * @author yangyi
 *
 * @deprecated It seems no necessary for super test class, will be removed.
 */
@Deprecated
public class BaseTest {

	@Test
	public void test_rs_json() {
		String json = "{\"cluster\":\"DEFAULT\",\"ip\":\"127.0.0.1\",\"metadata\":{},\"port\":60000,\"scheduled\":true,\"serviceName\":\"DEFAULT_GROUP@@jinhan9J7ye.Vj6hx.net\",\"weight\":1.0}";
		RsInfo client = JacksonUtils.toObj(json, RsInfo.class);
		System.out.println(client);
	}

}
