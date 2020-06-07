/*
 *
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
 *
 */

package com.alibaba.nacos.client;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.AbstractListener;
import com.alibaba.nacos.common.utils.ThreadUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Objects;
import java.util.Properties;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@Ignore
public class ConfigTest {

	private static ConfigService configService;

	public static void main(String[] args) throws Exception {
		before();
		test();
	}

	public static void before() throws Exception {
		Properties properties = new Properties();
		properties.setProperty(PropertyKeyConst.SERVER_ADDR, "127.0.0.1:8848");
		configService = NacosFactory.createConfigService(properties);
	}

	public static void test() throws Exception {
		final String dataId = "lessspring";
		final String group = "lessspring";
		final String content = "lessspring-" + System.currentTimeMillis();
		boolean result = configService.publishConfig(dataId, group, content);
		Assert.assertTrue(result);

		ThreadUtils.sleep(10_000);

		String response = configService.getConfigAndSignListener(dataId, group, 5000, new AbstractListener() {
			@Override
			public void receiveConfigInfo(String configInfo) {
				System.err.println(configInfo);
			}
		});
		Assert.assertEquals(content, response);

		Scanner scanner = new Scanner(System.in);
		System.out.println("input content");
		while (scanner.hasNextLine()){
			String s = scanner.next();
			if (Objects.equals("exit", s)) {
				scanner.close();
				return;
			}
			configService.publishConfig(dataId, group, s);
		}
	}

}
