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
import com.alibaba.nacos.common.utils.StringUtils;

import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * All parameter validation tools
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public final class ValidatorUtils {

	private static final Pattern CONTEXT_PATH_MATCH = Pattern.compile("(\\/)\\1+");
	private static final Pattern IP_MATCH = Pattern.compile("([^\\/:]+)(:\\d+)");

	public static void checkInitParam(Properties properties) {
		checkServerAddr(properties.getProperty(PropertyKeyConst.SERVER_ADDR));
		checkContextPath(properties.getProperty(PropertyKeyConst.CONTEXT_PATH));
	}

	public static void checkServerAddr(String serverAddr) {
		if (StringUtils.isEmpty(serverAddr)) {
			throw new IllegalArgumentException("Please set the serverAddr");
		}
		String[] addrs;
		if (serverAddr.contains(StringUtils.COMMA)) {
			addrs = serverAddr.split(StringUtils.COMMA);
		} else {
			addrs = new String[]{serverAddr};
		}
		for (String addr : addrs) {
			Matcher matcher = IP_MATCH.matcher(addr.trim());
			if (!matcher.find()) {
				throw new IllegalArgumentException("Incorrect serverAddr address : " + addr + ", example should like ip:port or domain:port");
			}
		}
	}

	public static void checkContextPath(String contextPath) {
		if (contextPath == null) {
			return;
		}
		Matcher matcher = CONTEXT_PATH_MATCH.matcher(contextPath);
		if (matcher.find()) {
			throw new IllegalArgumentException("Illegal url path expression");
		}
	}

}
