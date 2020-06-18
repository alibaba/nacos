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

import com.alibaba.nacos.common.http.client.DefaultAsyncHttpClientRequest;
import com.alibaba.nacos.common.http.client.DefaultHttpClientRequest;
import com.alibaba.nacos.common.http.client.NacosAsyncRestTemplate;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.nio.client.HttpAsyncClients;

/**
 * AbstractHttpClientFactory Let the creator only specify the http client config.
 *
 * @author mai.jh
 */
public abstract class AbstractHttpClientFactory implements HttpClientFactory {
    
    @Override
    public final NacosRestTemplate createNacosRestTemplate() {
        RequestConfig requestConfig = getRequestConfig();
        return new NacosRestTemplate(
                new DefaultHttpClientRequest(HttpClients.custom().setDefaultRequestConfig(requestConfig).build()));
    }
    
    @Override
    public final NacosAsyncRestTemplate createNacosAsyncRestTemplate() {
        RequestConfig requestConfig = getRequestConfig();
        return new NacosAsyncRestTemplate(new DefaultAsyncHttpClientRequest(
                HttpAsyncClients.custom().setDefaultRequestConfig(requestConfig).build()));
    }
    
    private RequestConfig getRequestConfig() {
        HttpClientConfig httpClientConfig = buildHttpClientConfig();
        return RequestConfig.custom().setConnectTimeout(httpClientConfig.getConTimeOutMillis())
                .setSocketTimeout(httpClientConfig.getReadTimeOutMillis())
                .setMaxRedirects(httpClientConfig.getMaxRedirects()).build();
    }
    
    /**
     * build http client config.
     *
     * @return HttpClientConfig
     */
    protected abstract HttpClientConfig buildHttpClientConfig();
}
