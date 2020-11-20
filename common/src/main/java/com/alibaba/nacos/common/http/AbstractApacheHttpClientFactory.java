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

import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.alibaba.nacos.common.http.client.request.DefaultHttpClientRequest;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.protocol.RequestContent;

/**
 * apache http client factory implements.
 *
 * @author mai.jh
 */
public abstract class AbstractApacheHttpClientFactory extends AbstractHttpClientFactory {
    
    @Override
    public final NacosRestTemplate createNacosRestTemplate() {
        final HttpClientConfig originalRequestConfig = buildHttpClientConfig();
        return new NacosRestTemplate(assignLogger(), new DefaultHttpClientRequest(
                HttpClients.custom()
                        .addInterceptorLast(new RequestContent(true))
                        .setDefaultRequestConfig(getRequestConfig())
                        .setUserAgent(originalRequestConfig.getUserAgent())
                        .setMaxConnTotal(originalRequestConfig.getMaxConnTotal())
                        .setMaxConnPerRoute(originalRequestConfig.getMaxConnPerRoute())
                        .setConnectionTimeToLive(originalRequestConfig.getConnTimeToLive(),
                                originalRequestConfig.getConnTimeToLiveTimeUnit()).build()));
    }
    
}
