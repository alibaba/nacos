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

import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.utils.HttpMethod;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;

import java.io.IOException;
import java.lang.reflect.Type;

/**
 * Nacos async http client.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 * @deprecated Refer to the new {@link com.alibaba.nacos.common.http.client.request.DefaultAsyncHttpClientRequest}
 */
@Deprecated
class NacosAsyncHttpClient extends BaseHttpClient implements NAsyncHttpClient {
    
    private CloseableHttpAsyncClient asyncClient;
    
    NacosAsyncHttpClient(CloseableHttpAsyncClient asyncClient) {
        this.asyncClient = asyncClient;
        this.asyncClient.start();
    }
    
    @Override
    public <T> void get(final String url, final Header header, final Query query, final Type token,
            final Callback<T> callback) throws Exception {
        HttpRequestBase requestBase = build(buildUrl(url, query), header, HttpMethod.GET);
        execute(asyncClient, token, callback, requestBase);
    }
    
    @Override
    public <T> void getLarge(final String url, final Header header, final Query query, final Object body,
            final Type token, final Callback<T> callback) throws Exception {
        HttpRequestBase requestBase = build(buildUrl(url, query), header, body, HttpMethod.GET_LARGE);
        execute(asyncClient, token, callback, requestBase);
    }
    
    @Override
    public <T> void delete(final String url, final Header header, final Query query, final Type token,
            final Callback<T> callback) throws Exception {
        HttpRequestBase requestBase = build(buildUrl(url, query), header, HttpMethod.DELETE);
        execute(asyncClient, token, callback, requestBase);
    }
    
    @Override
    public <T> void put(final String url, final Header header, final Query query, final Object body, final Type token,
            final Callback<T> callback) throws Exception {
        HttpRequestBase requestBase = build(buildUrl(url, query), header, body, HttpMethod.PUT);
        execute(asyncClient, token, callback, requestBase);
    }
    
    @Override
    public <T> void post(final String url, final Header header, final Query query, final Object body, final Type token,
            final Callback<T> callback) throws Exception {
        HttpRequestBase requestBase = build(buildUrl(url, query), header, body, HttpMethod.POST);
        execute(asyncClient, token, callback, requestBase);
    }
    
    @Override
    public void close() throws IOException {
        asyncClient.close();
    }
}
