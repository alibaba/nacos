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

import com.alibaba.nacos.common.constant.HttpHeaderConsts;
import com.alibaba.nacos.common.http.Callback;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.MediaType;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.model.RequestHttpEntity;
import com.alibaba.nacos.common.utils.HttpMethod;
import com.alibaba.nacos.common.utils.UriUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.net.URI;
import java.util.Map;

/**
 * NacosAsyncRestTemplate async
 *
 * @author mai.jh
 * @date 2020/5/29
 * @see AsyncHttpClientRequest
 * @see HttpClientResponse
 */
public class NacosAsyncRestTemplate implements AsyncRestOperations {

    private static final Logger logger = LoggerFactory.getLogger(NacosAsyncRestTemplate.class);

    private AsyncHttpClientRequest clientRequest;

    public NacosAsyncRestTemplate(AsyncHttpClientRequest clientRequest) {
        this.clientRequest = clientRequest;
    }


    @Override
    public <T> void get(String url, Header header, Query query, Type responseType, Callback<T> callback) throws Exception {

        execute(url, HttpMethod.GET, new RequestHttpEntity(header, query), responseType, callback);
    }

    @Override
    public <T> void get(String url, Header header, Map<String, String> paramValues,
                        Type responseType, Callback<T> callback) throws Exception {

        execute(url, HttpMethod.GET, new RequestHttpEntity(header,
            Query.newInstance().initParams(paramValues)), responseType, callback);
    }

    @Override
    public <T> void getLarge(String url, Header header, Query query, Object body,
                             Type responseType, Callback<T> callback) throws Exception {

        execute(url, HttpMethod.GET_LARGE,
            new RequestHttpEntity(header, query, body), responseType, callback);
    }

    @Override
    public <T> void delete(String url, Header header, Query query,
                           Type responseType, Callback<T> callback) throws Exception {

        execute(url, HttpMethod.DELETE,
            new RequestHttpEntity(header, query), responseType, callback);
    }

    @Override
    public <T> void put(String url, Header header, Query query, Object body,
                        Type responseType, Callback<T> callback) throws Exception {

        execute(url, HttpMethod.PUT,
            new RequestHttpEntity(header, query, body), responseType, callback);
    }

    @Override
    public <T> void putJson(String url, Header header, Map<String, String> paramValues,
                            String body, Type responseType, Callback<T> callback) throws Exception {

        execute(url, HttpMethod.PUT, new RequestHttpEntity(
            header, Query.newInstance().initParams(paramValues), body), responseType, callback);

    }

    @Override
    public <T> void putFrom(String url, Header header, Query query, Map<String, String> bodyValues,
                            Type responseType, Callback<T> callback) throws Exception {

        execute(url, HttpMethod.PUT, new RequestHttpEntity(
            header.addParam(HttpHeaderConsts.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED),
            query, bodyValues), responseType, callback);
    }

    @Override
    public <T> void putFrom(String url, Header header, Map<String, String> paramValues,
                            Map<String, String> bodyValues, Type responseType, Callback<T> callback) throws Exception {

        execute(url, HttpMethod.PUT, new RequestHttpEntity(
            header.addParam(HttpHeaderConsts.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED),
            Query.newInstance().initParams(paramValues), bodyValues), responseType, callback);
    }

    @Override
    public <T> void post(String url, Header header, Query query, Object body,
                         Type responseType, Callback<T> callback) throws Exception {

        execute(url, HttpMethod.POST, new RequestHttpEntity(
            header, query, body), responseType, callback);
    }

    @Override
    public <T> void postJson(String url, Header header, Map<String, String> paramValues,
                             String body, Type responseType, Callback<T> callback) throws Exception {

        execute(url, HttpMethod.POST, new RequestHttpEntity(
            header, Query.newInstance().initParams(paramValues), body), responseType, callback);
    }

    @Override
    public <T> void postFrom(String url, Header header, Query query, Map<String, String> bodyValues,
                             Type responseType, Callback<T> callback) throws Exception {

        execute(url, HttpMethod.POST, new RequestHttpEntity(
                header.addParam(HttpHeaderConsts.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED), query, bodyValues),
            responseType, callback);
    }

    @Override
    public <T> void postFrom(String url, Header header, Map<String, String> paramValues,
                             Map<String, String> bodyValues, Type responseType, Callback<T> callback) throws Exception {

        execute(url, HttpMethod.POST, new RequestHttpEntity(
            header.addParam(HttpHeaderConsts.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED),
            Query.newInstance().initParams(paramValues),
            bodyValues), responseType, callback);

    }

    private <T> void execute(String url, String httpMethod, RequestHttpEntity requestEntity,
                             Type responseType, Callback<T> callback) throws Exception {

        URI uri = UriUtils.buildUri(url, requestEntity.getQuery());
        if (logger.isDebugEnabled()) {
            logger.debug("HTTP " + httpMethod + " " + url);
        }
        clientRequest.execute(uri, httpMethod, requestEntity, responseType, callback);
    }

    /**
     * close request client
     */
    public void close() throws Exception {
        clientRequest.close();
    }
}
