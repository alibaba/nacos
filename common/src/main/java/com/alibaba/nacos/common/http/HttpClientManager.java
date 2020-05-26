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

package com.alibaba.nacos.common.http;

import com.alibaba.nacos.common.utils.ExceptionUtil;
import com.alibaba.nacos.common.utils.ShutdownUtils;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Use the same HttpClient object in the same space
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@SuppressWarnings("all")
public class HttpClientManager {

	private static final Logger logger = LoggerFactory.getLogger(HttpClientManager.class);

	private static final int TIMEOUT = Integer.getInteger("nacos.http.timeout", 5000);

	private static final RequestConfig DEFAULT_CONFIG = RequestConfig.custom()
			.setConnectTimeout(TIMEOUT).setSocketTimeout(TIMEOUT << 1).build();

	private static final NSyncHttpClient SYNC_HTTP_CLIENT = new NacosSyncHttpClient(
			HttpClients.custom().setDefaultRequestConfig(DEFAULT_CONFIG).build());

	private static final NAsyncHttpClient ASYNC_HTTP_CLIENT = new NacosAsyncHttpClient(
			HttpAsyncClients.custom().setDefaultRequestConfig(DEFAULT_CONFIG).build());

	private static final AtomicBoolean alreadyShutdown = new AtomicBoolean(false);

	static {
		ShutdownUtils.addShutdownHook(new Runnable() {
			@Override
			public void run() {
				shutdown();
			}
		});

	}

	public static NSyncHttpClient getSyncHttpClient() {
		return SYNC_HTTP_CLIENT;
	}

	public static NAsyncHttpClient getAsyncHttpClient() {
		return ASYNC_HTTP_CLIENT;
	}

	public static void shutdown() {
		if (!alreadyShutdown.compareAndSet(false, true)) {
			return;
		}
		logger.warn("[HttpClientManager] Start destroying HttpClient");
		try {
			SYNC_HTTP_CLIENT.close();
			ASYNC_HTTP_CLIENT.close();
		}
		catch (Exception ex) {
			logger.error("An exception occurred when the HTTP client was closed : {}",
					ExceptionUtil.getStackTrace(ex));
		}
		logger.warn("[HttpClientManager] Destruction of the end");
	}

}
