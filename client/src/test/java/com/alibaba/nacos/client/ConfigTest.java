/*
 *
 *  * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *      http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package com.alibaba.nacos.client;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.AbstractListener;
import com.alibaba.nacos.common.utils.ThreadUtils;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Properties;
import java.util.concurrent.CountDownLatch;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@Ignore
public class ConfigTest {

	@Test
	public void testServiceList() throws Exception {

		Properties properties = new Properties();
		properties.put(PropertyKeyConst.SERVER_ADDR, "console.nacos.io:80");
		properties.put(PropertyKeyConst.USERNAME, "nacos");
		properties.put(PropertyKeyConst.PASSWORD, "nacos");

		ConfigService configService = NacosFactory.createConfigService(properties);

		final String dataId = "chuntaohjun";
		final String group = "chuntaohjun";
		final String content = "this.is.test=chuntaojun";

		boolean result = configService.publishConfig(dataId, group, content);
		Assert.assertTrue(result);

		configService.addListener(dataId, group, new AbstractListener() {
			@Override
			public void receiveConfigInfo(String configInfo) {
				System.out.println(configInfo);
			}
		});

		for (int i = 0; i < 5; i ++) {
			publish(dataId, group, content + System.currentTimeMillis(), configService);
			ThreadUtils.sleep(10_000L);
		}

		CountDownLatch latch = new CountDownLatch(1);
		latch.await();
	}

	private void publish(String dataId, String group, String content, ConfigService configService) {
		try {
			configService.publishConfig(dataId, group, content + System.currentTimeMillis());
		} catch (Throwable ignore) {

		}
	}

}
