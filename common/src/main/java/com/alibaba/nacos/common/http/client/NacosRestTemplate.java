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
 * Nacos rest template Interface specifying a basic set of RESTful operations.
 *
 * @author mai.jh
 * @date 2020/5/24
 * @see HttpClientRequest
 * @see HttpClientResponse
 */
public class NacosRestTemplate {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(NacosRestTemplate.class);
    
    private HttpClientRequest requestClient;
    
    public NacosRestTemplate(HttpClientRequest requestClient) {
        this.requestClient = requestClient;
    }
    
    /**
     * http get URL request params are expanded using the given query {@link Query}.
     *
     * <p>{@code responseType} can be an HttpRestResult or HttpRestResult data {@code T} type.
     *
     * @param url          url
     * @param header       http header param
     * @param query        http query param
     * @param responseType return type
     * @return {@link HttpRestResult}
     * @throws Exception ex
     */
    public <T> HttpRestResult<T> get(String url, Header header, Query query, Type responseType) throws Exception {
        return execute(url, HttpMethod.GET, new RequestHttpEntity(header, query), responseType);
    }
    
    /**
     * http get URL request params are expanded using the given query {@link Query}.
     *
     * <p>{@code responseType} can be an HttpRestResult or HttpRestResult data {@code T} type.
     *
     * @param url          url
     * @param header       headers
     * @param paramValues  paramValues
     * @param responseType return type
     * @return {@link HttpRestResult}
     * @throws Exception ex
     */
    public <T> HttpRestResult<T> get(String url, Header header, Map<String, String> paramValues, Type responseType)
            throws Exception {
        RequestHttpEntity requestHttpEntity = new RequestHttpEntity(header,
                Query.newInstance().initParams(paramValues));
        return execute(url, HttpMethod.GET, requestHttpEntity, responseType);
    }
    
    /**
     * get request, may be pulling a lot of data URL request params are expanded using the given query {@link Query},
     * More request parameters can be set via body.
     *
     * <p>{@code responseType} can be an HttpRestResult or HttpRestResult data {@code T} type.
     *
     * @param url          url
     * @param header       http header param
     * @param query        http query param
     * @param body         get with body
     * @param responseType return type
     * @return {@link HttpRestResult}
     * @throws Exception ex
     */
    public <T> HttpRestResult<T> getLarge(String url, Header header, Query query, Object body, Type responseType)
            throws Exception {
        return execute(url, HttpMethod.GET_LARGE, new RequestHttpEntity(header, query, body), responseType);
    }
    
    /**
     * http delete URL request params are expanded using the given query {@link Query}.
     *
     * <p>{@code responseType} can be an HttpRestResult or HttpRestResult data {@code T} type.
     *
     * @param url          url
     * @param header       http header param
     * @param query        http query param
     * @param responseType return type
     * @return {@link HttpRestResult}
     * @throws Exception ex
     */
    public <T> HttpRestResult<T> delete(String url, Header header, Query query, Type responseType) throws Exception {
        return execute(url, HttpMethod.DELETE, new RequestHttpEntity(header, query), responseType);
    }
    
    /**
     * http put Create a new resource by PUTting the given body to http request.
     *
     * <p>URL request params are expanded using the given query {@link Query}.
     *
     * <p>{@code responseType} can be an HttpRestResult or HttpRestResult data {@code T} type.
     *
     * @param url          url
     * @param header       http header param
     * @param query        http query param
     * @param body         http body param
     * @param responseType return type
     * @return {@link HttpRestResult}
     * @throws Exception ex
     */
    public <T> HttpRestResult<T> put(String url, Header header, Query query, Object body, Type responseType)
            throws Exception {
        return execute(url, HttpMethod.PUT, new RequestHttpEntity(header, query, body), responseType);
    }
    
    /**
     * http put json Create a new resource by PUTting the given body to http request, http header contentType default
     * 'application/json;charset=UTF-8'.
     *
     * <p>URL request params are expanded using the given map {@code paramValues}.
     *
     * <p>{@code responseType} can be an HttpRestResult or HttpRestResult data {@code T} type.
     *
     * @param url          url
     * @param header       http header param
     * @param paramValues  http query param
     * @param body         http body param
     * @param responseType return type
     * @return {@link HttpRestResult}
     * @throws Exception ex
     */
    public <T> HttpRestResult<T> putJson(String url, Header header, Map<String, String> paramValues, String body,
            Type responseType) throws Exception {
        RequestHttpEntity requestHttpEntity = new RequestHttpEntity(header.setContentType(MediaType.APPLICATION_JSON),
                Query.newInstance().initParams(paramValues), body);
        return execute(url, HttpMethod.PUT, requestHttpEntity, responseType);
    }
    
    /**
     * http put from Create a new resource by PUTting the given map {@code bodyValues} to http request, http header
     * contentType default 'application/x-www-form-urlencoded;charset=utf-8'.
     *
     * <p>URL request params are expanded using the given query {@code Query}.
     *
     * <p>{@code responseType} can be an HttpRestResult or HttpRestResult data {@code T} type.
     *
     * @param url          url
     * @param header       http header param
     * @param query        http query param
     * @param bodyValues   http body param
     * @param responseType return type
     * @return {@link HttpRestResult}
     * @throws Exception ex
     */
    public <T> HttpRestResult<T> putFrom(String url, Header header, Query query, Map<String, String> bodyValues,
            Type responseType) throws Exception {
        RequestHttpEntity requestHttpEntity = new RequestHttpEntity(
                header.setContentType(MediaType.APPLICATION_FORM_URLENCODED), query, bodyValues);
        return execute(url, HttpMethod.PUT, requestHttpEntity, responseType);
    }
    
    /**
     * http put from Create a new resource by PUTting the given map {@code bodyValues} to http request, http header
     * contentType default 'application/x-www-form-urlencoded;charset=utf-8'.
     *
     * <p>URL request params are expanded using the given map {@code paramValues}.
     *
     * <p>{@code responseType} can be an HttpRestResult or HttpRestResult data {@code T} type.
     *
     * @param url          url
     * @param header       http header param
     * @param paramValues  http query param
     * @param bodyValues   http body param
     * @param responseType return type
     * @return {@link HttpRestResult}
     * @throws Exception ex
     */
    public <T> HttpRestResult<T> putFrom(String url, Header header, Map<String, String> paramValues,
            Map<String, String> bodyValues, Type responseType) throws Exception {
        RequestHttpEntity requestHttpEntity = new RequestHttpEntity(
                header.setContentType(MediaType.APPLICATION_FORM_URLENCODED),
                Query.newInstance().initParams(paramValues), bodyValues);
        return execute(url, HttpMethod.PUT, requestHttpEntity, responseType);
    }
    
    /**
     * http post Create a new resource by POSTing the given object to the http request.
     *
     * <p>URL request params are expanded using the given query {@link Query}.
     *
     * <p>{@code responseType} can be an HttpRestResult or HttpRestResult data {@code T} type.
     *
     * @param url          url
     * @param header       http header param
     * @param query        http query param
     * @param body         http body param
     * @param responseType return type
     * @return {@link HttpRestResult}
     * @throws Exception ex
     */
    public <T> HttpRestResult<T> post(String url, Header header, Query query, Object body, Type responseType)
            throws Exception {
        return execute(url, HttpMethod.POST, new RequestHttpEntity(header, query, body), responseType);
    }
    
    /**
     * http post json Create a new resource by POSTing the given object to the http request, http header contentType
     * default 'application/json;charset=UTF-8'.
     *
     * <p>URL request params are expanded using the given map {@code paramValues}.
     *
     * <p>{@code responseType} can be an HttpRestResult or HttpRestResult data {@code T} type.
     *
     * @param url          url
     * @param header       http header param
     * @param paramValues  http query param
     * @param body         http body param
     * @param responseType return type
     * @return {@link HttpRestResult}
     * @throws Exception ex
     */
    public <T> HttpRestResult<T> postJson(String url, Header header, Map<String, String> paramValues, String body,
            Type responseType) throws Exception {
        RequestHttpEntity requestHttpEntity = new RequestHttpEntity(header.setContentType(MediaType.APPLICATION_JSON),
                Query.newInstance().initParams(paramValues), body);
        return execute(url, HttpMethod.POST, requestHttpEntity, responseType);
    }
    
    /**
     * http post from Create a new resource by PUTting the given map {@code bodyValues} to http request, http header
     * contentType default 'application/x-www-form-urlencoded;charset=utf-8'.
     *
     * <p>URL request params are expanded using the given query {@link Query}.
     *
     * <p>{@code responseType} can be an HttpRestResult or HttpRestResult data {@code T} type.
     *
     * @param url          url
     * @param header       http header param
     * @param query        http query param
     * @param bodyValues   http body param
     * @param responseType return type
     * @return {@link HttpRestResult}
     * @throws Exception ex
     */
    public <T> HttpRestResult<T> postFrom(String url, Header header, Query query, Map<String, String> bodyValues,
            Type responseType) throws Exception {
        RequestHttpEntity requestHttpEntity = new RequestHttpEntity(
                header.setContentType(MediaType.APPLICATION_FORM_URLENCODED), query, bodyValues);
        return execute(url, HttpMethod.POST, requestHttpEntity, responseType);
    }
    
    /**
     * http post from Create a new resource by PUTting the given map {@code bodyValues} to http request, http header
     * contentType default 'application/x-www-form-urlencoded;charset=utf-8'.
     *
     * <p>URL request params are expanded using the given map {@code paramValues}.
     *
     * <p>{@code responseType} can be an HttpRestResult or HttpRestResult data {@code T} type.
     *
     * @param url          url
     * @param header       http header param
     * @param paramValues  http query param
     * @param bodyValues   http body param
     * @param responseType return type
     * @return {@link HttpRestResult}
     * @throws Exception ex
     */
    public <T> HttpRestResult<T> postFrom(String url, Header header, Map<String, String> paramValues,
            Map<String, String> bodyValues, Type responseType) throws Exception {
        RequestHttpEntity requestHttpEntity = new RequestHttpEntity(
                header.setContentType(MediaType.APPLICATION_FORM_URLENCODED),
                Query.newInstance().initParams(paramValues), bodyValues);
        return execute(url, HttpMethod.POST, requestHttpEntity, responseType);
    }
    
    private <T> HttpRestResult<T> execute(String url, String httpMethod, RequestHttpEntity requestEntity,
            Type responseType) throws Exception {
        URI uri = HttpUtils.buildUri(url, requestEntity.getQuery());
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("HTTP " + httpMethod + " " + url);
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
     * close request client.
     */
    public void close() throws Exception {
        requestClient.close();
    }
    
}
