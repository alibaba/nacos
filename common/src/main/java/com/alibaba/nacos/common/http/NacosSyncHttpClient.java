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

import com.alibaba.fastjson.TypeReference;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.model.ResResult;
import com.alibaba.nacos.common.utils.HttpMethod;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.CloseableHttpClient;

import java.io.IOException;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class NacosSyncHttpClient extends BaseHttpClient implements NSyncHttpClient {

    private CloseableHttpClient client;

    public NacosSyncHttpClient(CloseableHttpClient client) {
        this.client = client;
    }

    @Override
    public <T> ResResult<T> get(final String url,
                                final Header header,
                                final Query query,
                                final TypeReference<ResResult<T>> token) throws Exception {
        HttpRequestBase requestBase = build(buildUrl(url, query), header, HttpMethod.GET);
        return execute(client, token, requestBase);
    }

    @Override
    public <T> ResResult<T> getLarge(String url, Header header, Query query, ResResult body, TypeReference<ResResult<T>> token) throws Exception {
        HttpRequestBase requestBase = build(buildUrl(url, query), header, body, HttpMethod.GET_LARGE);
        return execute(client, token, requestBase);
    }

    @Override
    public <T> ResResult<T> delete(final String url,
                                   final Header header,
                                   final Query query,
                                   final TypeReference<ResResult<T>> token) throws Exception {
        HttpRequestBase requestBase = build(buildUrl(url, query), header, HttpMethod.DELETE);
        return execute(client, token, requestBase);
    }

    @Override
    public <T> ResResult<T> put(final String url,
                                final Header header,
                                final Query query,
                                final ResResult body,
                                final TypeReference<ResResult<T>> token) throws Exception {
        HttpRequestBase requestBase = build(buildUrl(url, query), header, body, HttpMethod.PUT);
        return execute(client, token, requestBase);
    }

    @Override
    public <T> ResResult<T> post(final String url,
                                 final Header header,
                                 final Query query,
                                 final ResResult body,
                                 final TypeReference<ResResult<T>> token) throws Exception {
        HttpRequestBase requestBase = build(buildUrl(url, query), header, body, HttpMethod.POST);
        return execute(client, token, requestBase);
    }

    @Override
    public void close() throws IOException {
        client.close();
    }
}
