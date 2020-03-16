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

import com.alibaba.nacos.common.utils.ShutdownUtils;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.nio.client.HttpAsyncClients;

import java.util.HashMap;
import java.util.Map;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@SuppressWarnings("all")
public class HttpClientManager {

	private static final Object SYNC_MONITOR = new Object();

	private static final Object ASYNC_MONITOR = new Object();

	private static final int TIMEOUT = 5000;

	private static final Map<String, NSyncHttpClient> HTTP_CLIENT_MAP = new HashMap<String, NSyncHttpClient>(
			8);

	private static final Map<String, NAsyncHttpClient> HTTP_ASYNC_CLIENT_MAP = new HashMap<String, NAsyncHttpClient>(
			8);

	static {
		ShutdownUtils.addShutdownHook(new Runnable() {
			@Override public void run() {

				System.out.println("[NSyncHttpClient] Start destroying HttpClient");

				for (Map.Entry<String, NSyncHttpClient> entry : HTTP_CLIENT_MAP
						.entrySet()) {
					try {
						entry.getValue().close();
					}
					catch (Exception ignore) {

					}
				}

				System.out.println("[NSyncHttpClient] Destruction of the end");
			}
		});

		ShutdownUtils.addShutdownHook(new Runnable() {
			@Override public void run() {

				System.out.println("[NAsyncHttpClient] Start destroying HttpClient");

				for (Map.Entry<String, NAsyncHttpClient> entry : HTTP_ASYNC_CLIENT_MAP
						.entrySet()) {
					try {
						entry.getValue().close();
					}
					catch (Exception ignore) {
					}
				}

				System.out.println("[NAsyncHttpClient] Destruction of the end");
			}
		});

	}

	private static final RequestConfig DEFAULT_CONFIG = RequestConfig.custom()
			.setConnectTimeout(TIMEOUT).setSocketTimeout(TIMEOUT << 1).build();

	public static NSyncHttpClient newHttpClient(String owner) {
		synchronized (SYNC_MONITOR) {

			NSyncHttpClient nSyncHttpClient = HTTP_CLIENT_MAP.get(owner);

			if (nSyncHttpClient != null) {
				return nSyncHttpClient;
			}

			nSyncHttpClient = new NacosSyncHttpClient(
					HttpClients.custom().setDefaultRequestConfig(DEFAULT_CONFIG).build());
			HTTP_CLIENT_MAP.put(owner, nSyncHttpClient);
			return nSyncHttpClient;
		}
	}

	public static NAsyncHttpClient newAsyncHttpClient(String owner) {
		synchronized (ASYNC_MONITOR) {

			NAsyncHttpClient nAsyncHttpClient = HTTP_ASYNC_CLIENT_MAP.get(owner);

			if (nAsyncHttpClient != null) {
				return nAsyncHttpClient;
			}

			nAsyncHttpClient = new NacosAsyncHttpClient(
					HttpAsyncClients.custom().setDefaultRequestConfig(DEFAULT_CONFIG)
							.build());
			HTTP_ASYNC_CLIENT_MAP.put(owner, nAsyncHttpClient);
			return nAsyncHttpClient;
		}
	}

	public static NSyncHttpClient newHttpClient(String owner,
			RequestConfig requestConfig) {
		synchronized (SYNC_MONITOR) {

			NSyncHttpClient nSyncHttpClient = HTTP_CLIENT_MAP.get(owner);

			if (nSyncHttpClient != null) {
				return nSyncHttpClient;
			}

			nSyncHttpClient = new NacosSyncHttpClient(
					HttpClients.custom().setDefaultRequestConfig(requestConfig).build());
			HTTP_CLIENT_MAP.put(owner, nSyncHttpClient);
			return nSyncHttpClient;
		}
	}

	public static NAsyncHttpClient newAsyncHttpClient(String owner,
			RequestConfig requestConfig) {
		synchronized (ASYNC_MONITOR) {
			NAsyncHttpClient nAsyncHttpClient = HTTP_ASYNC_CLIENT_MAP.get(owner);

			if (nAsyncHttpClient != null) {
				return nAsyncHttpClient;
			}

			nAsyncHttpClient = new NacosAsyncHttpClient(
					HttpAsyncClients.custom().setDefaultRequestConfig(requestConfig)
							.build());
			HTTP_ASYNC_CLIENT_MAP.put(owner, nAsyncHttpClient);
			return nAsyncHttpClient;
		}
	}
}
