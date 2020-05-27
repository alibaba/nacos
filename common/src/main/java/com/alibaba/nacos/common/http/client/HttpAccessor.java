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

package com.alibaba.nacos.common.http.client;

/**
 *  HTTP accessing helpers, defining common properties
 *  such as the {@link ClientHttpRequest} to operate on.
 *
 * @author mai.jh
 * @date 2020/5/24
 */
@SuppressWarnings("WeakerAccess")
public class HttpAccessor {

    private ClientHttpRequest requestClient = new DefaultSimpleClientHttpRequest();

    /**
     * set requestClient {@link ClientHttpRequest}
     * <p>The default is a {@link DefaultSimpleClientHttpRequest} based on the JDK's own
     * HTTP libraries ({@link java.net.HttpURLConnection}).
     * @param requestClient ClientHttpRequest
     */
    public void setRequestClient(ClientHttpRequest requestClient) {
        if (requestClient == null) {
            throw new IllegalArgumentException("ClientHttpRequestFactory must not be null");
        }
        this.requestClient = requestClient;
    }

    public ClientHttpRequest getRequestClient() {
        return requestClient;
    }
}
