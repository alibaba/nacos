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

package com.alibaba.nacos.client.naming.net;

import com.alibaba.nacos.common.http.*;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;

/**
 * http Manager
 *
 * @author mai.jh
 * @date 2020/6/14
 */
public class NamingHttpClientManager {

    private static final int READ_TIME_OUT_MILLIS = Integer
        .getInteger("com.alibaba.nacos.client.naming.rtimeout", 50000);
    private static final int CON_TIME_OUT_MILLIS = Integer
        .getInteger("com.alibaba.nacos.client.naming.ctimeout", 3000);
    private static final boolean ENABLE_HTTPS = Boolean
        .getBoolean("com.alibaba.nacos.client.naming.tls.enable");
    private static final int MAX_REDIRECTS = 5;

    private static final HttpClientFactory HTTP_CLIENT_FACTORY = new NamingHttpClientFactory();

    public static String getPrefix() {
        if (ENABLE_HTTPS) {
            return "https://";
        }
        return "http://";
    }

    public static NacosRestTemplate getNacosRestTemplate() {
        return HttpClientBeanHolder.getNacosRestTemplate(HTTP_CLIENT_FACTORY);
    }

    private static class NamingHttpClientFactory extends AbstractHttpClientFactory {

        @Override
        protected HttpClientConfig buildHttpClientConfig() {
            return HttpClientConfig.builder()
                .setConTimeOutMillis(CON_TIME_OUT_MILLIS)
                .setReadTimeOutMillis(READ_TIME_OUT_MILLIS)
                .setMaxRedirects(MAX_REDIRECTS).build();
        }
    }
}
