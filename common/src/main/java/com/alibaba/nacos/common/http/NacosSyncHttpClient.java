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
import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.common.utils.HttpMethod;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.CloseableHttpClient;

import java.io.IOException;
import java.lang.reflect.Type;

/**
 * Nacos sync http client.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 * @deprecated Refer to the new {@link com.alibaba.nacos.common.http.client.request.JdkHttpClientRequest}
 */
@Deprecated
class NacosSyncHttpClient extends BaseHttpClient implements NSyncHttpClient {
    
    private CloseableHttpClient client;
    
    NacosSyncHttpClient(CloseableHttpClient client) {
        this.client = client;
    }
    
    @Override
    public <T> RestResult<T> get(final String url, final Header header, final Query query, final Type token)
            throws Exception {
        HttpRequestBase requestBase = build(buildUrl(url, query), header, HttpMethod.GET);
        return execute(client, token, requestBase);
    }
    
    @Override
    public <T> RestResult<T> getLarge(String url, Header header, Query query, Object body, Type token)
            throws Exception {
        HttpRequestBase requestBase = build(buildUrl(url, query), header, body, HttpMethod.GET_LARGE);
        return execute(client, token, requestBase);
    }
    
    @Override
    public <T> RestResult<T> delete(final String url, final Header header, final Query query, final Type token)
            throws Exception {
        HttpRequestBase requestBase = build(buildUrl(url, query), header, HttpMethod.DELETE);
        return execute(client, token, requestBase);
    }
    
    @Override
    public <T> RestResult<T> put(final String url, final Header header, final Query query, final Object body,
            final Type token) throws Exception {
        HttpRequestBase requestBase = build(buildUrl(url, query), header, body, HttpMethod.PUT);
        return execute(client, token, requestBase);
    }
    
    @Override
    public <T> RestResult<T> post(final String url, final Header header, final Query query, final Object body,
            final Type token) throws Exception {
        HttpRequestBase requestBase = build(buildUrl(url, query), header, body, HttpMethod.POST);
        return execute(client, token, requestBase);
    }
    
    @Override
    public void close() throws IOException {
        client.close();
    }
}
