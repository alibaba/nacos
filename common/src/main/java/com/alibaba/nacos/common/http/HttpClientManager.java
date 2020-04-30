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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Use the same HttpClient object in the same space
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@SuppressWarnings("all")
public class HttpClientManager {

	private static final Logger logger = LoggerFactory.getLogger(HttpClientManager.class);

	private static final int TIMEOUT = 5000;

	private static final RequestConfig DEFAULT_CONFIG = RequestConfig.custom()
			.setConnectTimeout(TIMEOUT).setSocketTimeout(TIMEOUT << 1).build();

	private static final Object SYNC_MONITOR = new Object();

	private static final Object ASYNC_MONITOR = new Object();

	private static final Map<String, NSyncHttpClient> HTTP_SYNC_CLIENT_MAP = new HashMap<String, NSyncHttpClient>(
			8);

	private static final Map<String, NAsyncHttpClient> HTTP_ASYNC_CLIENT_MAP = new HashMap<String, NAsyncHttpClient>(
			8);

	private static final NSyncHttpClient SHARE_SYNC_HTTP_CLIENT = new NacosSyncHttpClient(
			HttpClients.custom().setDefaultRequestConfig(DEFAULT_CONFIG).build());

	private static final NAsyncHttpClient SHARE_ASYNC_HTTP_CLIENT = new NacosAsyncHttpClient(
			HttpAsyncClients.custom().setDefaultRequestConfig(DEFAULT_CONFIG).build());

	static {
		ShutdownUtils.addShutdownHook(new Runnable() {
			@Override
			public void run() {
				logger.warn("[NSyncHttpClient] Start destroying HttpClient");
				try {
					for (Map.Entry<String, NSyncHttpClient> entry : HTTP_SYNC_CLIENT_MAP
							.entrySet()) {
						entry.getValue().close();
					}
					SHARE_SYNC_HTTP_CLIENT.close();
					HTTP_SYNC_CLIENT_MAP.clear();
				}
				catch (Exception ignore) {
				}
				logger.warn("[NSyncHttpClient] Destruction of the end");
			}
		});

		ShutdownUtils.addShutdownHook(new Runnable() {
			@Override
			public void run() {
				logger.warn("[NAsyncHttpClient] Start destroying HttpClient");
				try {
					for (Map.Entry<String, NAsyncHttpClient> entry : HTTP_ASYNC_CLIENT_MAP
							.entrySet()) {
						entry.getValue().close();
					}
					SHARE_ASYNC_HTTP_CLIENT.close();
					HTTP_ASYNC_CLIENT_MAP.clear();
				}
				catch (Exception ignore) {
				}
				logger.warn("[NAsyncHttpClient] Destruction of the end");
			}
		});

	}

	public static NSyncHttpClient getShareSyncHttpClient() {
		return SHARE_SYNC_HTTP_CLIENT;
	}

	public static NAsyncHttpClient getShareAsyncHttpClient() {
		return SHARE_ASYNC_HTTP_CLIENT;
	}

	public static NSyncHttpClient newSyncHttpClient(String namespace) {
		synchronized (SYNC_MONITOR) {
			NSyncHttpClient nSyncHttpClient = HTTP_SYNC_CLIENT_MAP.get(namespace);

			if (nSyncHttpClient != null) {
				return nSyncHttpClient;
			}

			nSyncHttpClient = new NacosSyncHttpClient(
					HttpClients.custom().setDefaultRequestConfig(DEFAULT_CONFIG).build());
			HTTP_SYNC_CLIENT_MAP.put(namespace, nSyncHttpClient);
			return nSyncHttpClient;
		}
	}

	public static NAsyncHttpClient newAsyncHttpClient(String namespace) {
		synchronized (ASYNC_MONITOR) {
			NAsyncHttpClient nAsyncHttpClient = HTTP_ASYNC_CLIENT_MAP.get(namespace);

			if (nAsyncHttpClient != null) {
				return nAsyncHttpClient;
			}

			nAsyncHttpClient = new NacosAsyncHttpClient(
					HttpAsyncClients.custom().setDefaultRequestConfig(DEFAULT_CONFIG)
							.build());
			HTTP_ASYNC_CLIENT_MAP.put(namespace, nAsyncHttpClient);
			return nAsyncHttpClient;
		}
	}

	public static NSyncHttpClient newSyncHttpClient(String namespace,
			RequestConfig requestConfig) {
		synchronized (SYNC_MONITOR) {
			NSyncHttpClient nSyncHttpClient = HTTP_SYNC_CLIENT_MAP.get(namespace);

			if (nSyncHttpClient != null) {
				return nSyncHttpClient;
			}

			nSyncHttpClient = new NacosSyncHttpClient(
					HttpClients.custom().setDefaultRequestConfig(requestConfig).build());
			HTTP_SYNC_CLIENT_MAP.put(namespace, nSyncHttpClient);
			return nSyncHttpClient;
		}
	}

	public static NAsyncHttpClient newAsyncHttpClient(String namespace,
			RequestConfig requestConfig) {
		synchronized (ASYNC_MONITOR) {
			NAsyncHttpClient nAsyncHttpClient = HTTP_ASYNC_CLIENT_MAP.get(namespace);

			if (nAsyncHttpClient != null) {
				return nAsyncHttpClient;
			}

			nAsyncHttpClient = new NacosAsyncHttpClient(
					HttpAsyncClients.custom().setDefaultRequestConfig(requestConfig)
							.build());
			HTTP_ASYNC_CLIENT_MAP.put(namespace, nAsyncHttpClient);
			return nAsyncHttpClient;
		}
	}

}
