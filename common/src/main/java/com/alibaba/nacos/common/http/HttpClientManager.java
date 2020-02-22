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
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class HttpClientManager {

    private static final Object MONITOR = new Object();

    private static final Object ASYNC_MONITOR = new Object();

    private static final int TIMEOUT = 5000;

    private static final Map<String, Set<NSyncHttpClient>> HTTP_CLIENT_MAP = new ConcurrentHashMap<String, Set<NSyncHttpClient>>(8);

    private static final Map<String, Set<NAsyncHttpClient>> HTTP_ASYNC_CLIENT_MAP = new ConcurrentHashMap<String, Set<NAsyncHttpClient>>(8);

    static {
        ShutdownUtils.addShutdownHook(new Runnable() {
            @Override
            public void run() {
                for (Map.Entry<String, Set<NSyncHttpClient>> entry : HTTP_CLIENT_MAP.entrySet()) {
                    for (NSyncHttpClient httpClient : entry.getValue()) {
                        try {
                            httpClient.close();
                        } catch (Exception ignore) {

                        }
                    }
                }
            }
        });

        ShutdownUtils.addShutdownHook(new Runnable() {
            @Override
            public void run() {
                for (Map.Entry<String, Set<NAsyncHttpClient>> entry : HTTP_ASYNC_CLIENT_MAP.entrySet()) {
                    for (NAsyncHttpClient httpClient : entry.getValue()) {
                        try {
                            httpClient.close();
                        } catch (Exception ignore) {
                        }
                    }
                }
            }
        });

    }

    private static final RequestConfig DEFAULT_CONFIG = RequestConfig.custom()
        .setConnectTimeout(TIMEOUT)
        .setSocketTimeout(TIMEOUT << 1)
        .build();

    public static NSyncHttpClient newHttpClient(String owner) {
        checkExist(owner, false);
        CloseableHttpClient client = HttpClients.custom().setDefaultRequestConfig(DEFAULT_CONFIG)
            .build();
        NSyncHttpClient nSyncHttpClient = new NacosSyncHttpClient(client);
        HTTP_CLIENT_MAP.get(owner).add(nSyncHttpClient);
        return nSyncHttpClient;
    }

    public static NAsyncHttpClient newAsyncHttpClient(String owner) {
        checkExist(owner, true);
        CloseableHttpAsyncClient asyncClient = HttpAsyncClients.custom().setDefaultRequestConfig(DEFAULT_CONFIG)
            .build();
        NAsyncHttpClient nAsyncHttpClient = new NacosAsyncHttpClient(asyncClient);
        HTTP_ASYNC_CLIENT_MAP.get(owner).add(nAsyncHttpClient);
        return nAsyncHttpClient;
    }

    public static NSyncHttpClient newHttpClient(String owner, RequestConfig requestConfig) {
        checkExist(owner, false);
        CloseableHttpClient client = HttpClients.custom().setDefaultRequestConfig(requestConfig)
            .build();
        NSyncHttpClient nSyncHttpClient = new NacosSyncHttpClient(client);
        HTTP_CLIENT_MAP.get(owner).add(nSyncHttpClient);
        return nSyncHttpClient;
    }

    public static NAsyncHttpClient newAsyncHttpClient(String owner, RequestConfig requestConfig) {
        checkExist(owner, true);
        CloseableHttpAsyncClient asyncClient = HttpAsyncClients.custom().setDefaultRequestConfig(requestConfig)
            .build();
        NAsyncHttpClient nAsyncHttpClient = new NacosAsyncHttpClient(asyncClient);
        HTTP_ASYNC_CLIENT_MAP.get(owner).add(nAsyncHttpClient);
        return nAsyncHttpClient;
    }

    private static void checkExist(String owner, boolean async) {
        if (async) {
            synchronized (ASYNC_MONITOR) {
                if (!HTTP_ASYNC_CLIENT_MAP.containsKey(owner)) {
                    HTTP_ASYNC_CLIENT_MAP.put(owner, new CopyOnWriteArraySet<NAsyncHttpClient>());
                }
            }
        } else {
            synchronized (MONITOR) {
                if (!HTTP_CLIENT_MAP.containsKey(owner)) {
                    HTTP_CLIENT_MAP.put(owner, new CopyOnWriteArraySet<NSyncHttpClient>());
                }
            }
        }
    }
}
