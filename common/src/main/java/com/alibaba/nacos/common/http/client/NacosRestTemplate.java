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

import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.http.HttpUtils;
import com.alibaba.nacos.common.http.handler.ResponseHandler;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.MediaType;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.model.RequestHttpEntity;
import com.alibaba.nacos.common.utils.HttpMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.net.URI;
import java.util.Map;

/**
 * NacosRestTemplate
 *
 * @author mai.jh
 * @date 2020/5/24
 * @see HttpClientRequest
 * @see HttpClientResponse
 */
public class NacosRestTemplate implements RestOperations {

    private static final Logger logger = LoggerFactory.getLogger(NacosRestTemplate.class);

    private HttpClientRequest requestClient;

    public NacosRestTemplate(HttpClientRequest requestClient) {
        this.requestClient = requestClient;
    }

    @Override
    public <T> HttpRestResult<T> get(String url, Header header, Query query, Type responseType) throws Exception {
        return execute(url, HttpMethod.GET, new RequestHttpEntity(header, query), responseType);
    }

    @Override
    public <T> HttpRestResult<T> get(String url, Header header, Map<String, String> paramValues, Type responseType) throws Exception {
        RequestHttpEntity requestHttpEntity = new RequestHttpEntity(header, Query.newInstance().initParams(paramValues));

        return execute(url, HttpMethod.GET, requestHttpEntity, responseType);
    }

    @Override
    public <T> HttpRestResult<T> getLarge(String url, Header header, Query query, Object body, Type responseType) throws Exception {
        return execute(url, HttpMethod.GET_LARGE, new RequestHttpEntity(header, query, body), responseType);
    }

    @Override
    public <T> HttpRestResult<T> delete(String url, Header header, Query query, Type responseType) throws Exception {
        return execute(url, HttpMethod.DELETE, new RequestHttpEntity(header, query), responseType);
    }

    @Override
    public <T> HttpRestResult<T> put(String url, Header header, Query query, Object body, Type responseType) throws Exception {
        return execute(url, HttpMethod.PUT, new RequestHttpEntity(header, query, body), responseType);
    }

    @Override
    public <T> HttpRestResult<T> putJson(String url, Header header, Map<String, String> paramValues, String body, Type responseType) throws Exception {
        RequestHttpEntity requestHttpEntity = new RequestHttpEntity(
            header.setContentType(MediaType.APPLICATION_JSON),
            Query.newInstance().initParams(paramValues),
            body);

        return execute(url, HttpMethod.PUT, requestHttpEntity, responseType);
    }

    @Override
    public <T> HttpRestResult<T> putFrom(String url, Header header, Query query, Map<String, String> bodyValues, Type responseType) throws Exception {
        RequestHttpEntity requestHttpEntity = new RequestHttpEntity(
            header.setContentType(MediaType.APPLICATION_FORM_URLENCODED), query, bodyValues);
        return execute(url, HttpMethod.PUT, requestHttpEntity, responseType);
    }

    @Override
    public <T> HttpRestResult<T> putFrom(String url, Header header, Map<String, String> paramValues, Map<String, String> bodyValues, Type responseType) throws Exception {
        RequestHttpEntity requestHttpEntity = new RequestHttpEntity(
            header.setContentType(MediaType.APPLICATION_FORM_URLENCODED),
            Query.newInstance().initParams(paramValues),
            bodyValues);

        return execute(url, HttpMethod.PUT, requestHttpEntity, responseType);
    }

    @Override
    public <T> HttpRestResult<T> post(String url, Header header, Query query, Object body, Type responseType) throws Exception {
        return execute(url, HttpMethod.POST, new RequestHttpEntity(header, query, body),
            responseType);
    }

    @Override
    public <T> HttpRestResult<T> postJson(String url, Header header, Map<String, String> paramValues, String body, Type responseType) throws Exception {
        RequestHttpEntity requestHttpEntity = new RequestHttpEntity(
            header.setContentType(MediaType.APPLICATION_JSON),
            Query.newInstance().initParams(paramValues),
            body);

        return execute(url, HttpMethod.POST, requestHttpEntity, responseType);
    }

    @Override
    public <T> HttpRestResult<T> postFrom(String url, Header header, Query query, Map<String, String> bodyValues, Type responseType) throws Exception {
        RequestHttpEntity requestHttpEntity = new RequestHttpEntity(
            header.setContentType(MediaType.APPLICATION_FORM_URLENCODED), query, bodyValues);

        return execute(url, HttpMethod.POST, requestHttpEntity, responseType);
    }

    @Override
    public <T> HttpRestResult<T> postFrom(String url, Header header, Map<String, String> paramValues, Map<String, String> bodyValues, Type responseType) throws Exception {
        RequestHttpEntity requestHttpEntity = new RequestHttpEntity(
            header.setContentType(MediaType.APPLICATION_FORM_URLENCODED),
            Query.newInstance().initParams(paramValues),
            bodyValues);

        return execute(url, HttpMethod.POST, requestHttpEntity, responseType);
    }


    private <T> HttpRestResult<T> execute(String url, String httpMethod, RequestHttpEntity requestEntity,
                          Type responseType) throws Exception {
        URI uri = HttpUtils.buildUri(url, requestEntity.getQuery());
        if (logger.isDebugEnabled()) {
            logger.debug("HTTP " + httpMethod + " " + url);
        }
        HttpClientResponse response = null;
        try {
            response = requestClient.execute(uri, httpMethod, requestEntity);
            return ResponseHandler.responseEntityExtractor(response, responseType);
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }

    /**
     * close request client
     */
    public void close() throws Exception{
        requestClient.close();
    }



}
