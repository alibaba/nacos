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

import com.alibaba.nacos.common.http.Callback;
import com.alibaba.nacos.common.http.HttpUtils;
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
 * Nacos async rest template.
 *
 * @author mai.jh
 * @see AsyncHttpClientRequest
 * @see HttpClientResponse
 */
public class NacosAsyncRestTemplate {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(NacosAsyncRestTemplate.class);
    
    private AsyncHttpClientRequest clientRequest;
    
    public NacosAsyncRestTemplate(AsyncHttpClientRequest clientRequest) {
        this.clientRequest = clientRequest;
    }
    
    /**
     * async http get URL request params are expanded using the given query {@link Query}.
     *
     * <p>{@code responseType} can be an RestResult or RestResult data {@code T} type.
     *
     * <p>{@code callback} Result callback execution,
     * if you need response headers, you can convert the received RestResult to HttpRestResult.
     *
     * @param url          url
     * @param responseType return type
     * @param header       http header param
     * @param query        http query param
     * @param callback     callback {@link Callback#onReceive(com.alibaba.nacos.common.model.RestResult)}
     * @throws Exception ex
     */
    public <T> void get(String url, Header header, Query query, Type responseType, Callback<T> callback)
            throws Exception {
        execute(url, HttpMethod.GET, new RequestHttpEntity(header, query), responseType, callback);
    }
    
    /**
     * async http get URL request params are expanded using the given map {@code paramValues}.
     *
     * <p>{@code responseType} can be an RestResult or RestResult data {@code T} type.
     *
     * <p>{@code callback} Result callback execution
     * if you need response headers, you can convert the received RestResult to HttpRestResult.
     *
     * @param url          url
     * @param header       headers
     * @param paramValues  paramValues
     * @param responseType return type
     * @param callback     callback {@link Callback#onReceive(com.alibaba.nacos.common.model.RestResult)}
     * @throws Exception ex
     */
    public <T> void get(String url, Header header, Map<String, String> paramValues, Type responseType,
            Callback<T> callback) throws Exception {
        execute(url, HttpMethod.GET, new RequestHttpEntity(header, Query.newInstance().initParams(paramValues)),
                responseType, callback);
    }
    
    /**
     * async get request, may be pulling a lot of data URL request params are expanded using the given query {@link
     * Query}, More request parameters can be set via body.
     *
     * <p>{@code responseType} can be an RestResult or RestResult data {@code T} type.
     *
     * <p>{@code callback} Result callback execution,
     * if you need response headers, you can convert the received RestResult to HttpRestResult.
     *
     * @param url          url
     * @param header       http header param
     * @param query        http query param
     * @param body         get with body
     * @param responseType return type
     * @param callback     callback {@link Callback#onReceive(com.alibaba.nacos.common.model.RestResult)}
     * @throws Exception ex
     */
    public <T> void getLarge(String url, Header header, Query query, Object body, Type responseType,
            Callback<T> callback) throws Exception {
        execute(url, HttpMethod.GET_LARGE, new RequestHttpEntity(header, query, body), responseType, callback);
    }
    
    /**
     * async http delete URL request params are expanded using the given query {@link Query},
     *
     * <p>{@code responseType} can be an RestResult or RestResult data {@code T} type
     *
     * <p>{@code callback} Result callback execution,
     * if you need response headers, you can convert the received RestResult to HttpRestResult.
     *
     * @param url          url
     * @param header       http header param
     * @param query        http query param
     * @param responseType return type
     * @param callback     callback {@link Callback#onReceive(com.alibaba.nacos.common.model.RestResult)}
     * @throws Exception ex
     */
    public <T> void delete(String url, Header header, Query query, Type responseType, Callback<T> callback)
            throws Exception {
        execute(url, HttpMethod.DELETE, new RequestHttpEntity(header, query), responseType, callback);
    }
    
    /**
     * async http put Create a new resource by PUTting the given body to http request.
     *
     * <p>URL request params are expanded using the given query {@link Query}.
     *
     * <p>{@code responseType} can be an RestResult or RestResult data {@code T} type
     *
     * <p>{@code callback} Result callback execution,
     * if you need response headers, you can convert the received RestResult to HttpRestResult.
     *
     * @param url          url
     * @param header       http header param
     * @param query        http query param
     * @param body         http body param
     * @param responseType return type
     * @param callback     callback {@link Callback#onReceive(com.alibaba.nacos.common.model.RestResult)}
     * @throws Exception ex
     */
    public <T> void put(String url, Header header, Query query, Object body, Type responseType, Callback<T> callback)
            throws Exception {
        execute(url, HttpMethod.PUT, new RequestHttpEntity(header, query, body), responseType, callback);
    }
    
    /**
     * async http put Json Create a new resource by PUTting the given body to http request, http header contentType
     * default 'application/json;charset=UTF-8'.
     *
     * <p>URL request params are expanded using the given map {@code paramValues}.
     *
     * <p>{@code responseType} can be an RestResult or RestResult data {@code T} type
     *
     * <p>{@code callback} Result callback execution,
     * if you need response headers, you can convert the received RestResult to HttpRestResult.
     *
     * @param url          url
     * @param header       http header param
     * @param paramValues  http query param
     * @param body         http body param
     * @param responseType return type
     * @param callback     callback {@link Callback#onReceive(com.alibaba.nacos.common.model.RestResult)}
     * @throws Exception ex
     */
    public <T> void putJson(String url, Header header, Map<String, String> paramValues, String body, Type responseType,
            Callback<T> callback) throws Exception {
        execute(url, HttpMethod.PUT, new RequestHttpEntity(header.setContentType(MediaType.APPLICATION_JSON),
                Query.newInstance().initParams(paramValues), body), responseType, callback);
    }
    
    /**
     * async http put from Create a new resource by PUTting the given map {@code bodyValues} to http request, http
     * header contentType default 'application/x-www-form-urlencoded;charset=utf-8'.
     *
     * <p>URL request params are expanded using the given query {@link Query}.
     *
     * <p>{@code responseType} can be an RestResult or RestResult data {@code T} type.
     *
     * <p>{@code callback} Result callback execution,
     * if you need response headers, you can convert the received RestResult to HttpRestResult.
     *
     * @param url          url
     * @param header       http header param
     * @param query        http query param
     * @param bodyValues   http body param
     * @param responseType return type
     * @param callback     callback {@link Callback#onReceive(com.alibaba.nacos.common.model.RestResult)}
     * @throws Exception ex
     */
    public <T> void putFrom(String url, Header header, Query query, Map<String, String> bodyValues, Type responseType,
            Callback<T> callback) throws Exception {
        execute(url, HttpMethod.PUT,
                new RequestHttpEntity(header.setContentType(MediaType.APPLICATION_FORM_URLENCODED), query, bodyValues),
                responseType, callback);
    }
    
    /**
     * async http put from Create a new resource by PUTting the given map {@code bodyValues} to http request, http
     * header contentType default 'application/x-www-form-urlencoded;charset=utf-8'.
     *
     * <p>URL request params are expanded using the given map {@code paramValues}.
     *
     * <p>{@code responseType} can be an RestResult or RestResult data {@code T} type.
     *
     * <p>{@code callback} Result callback execution,
     * if you need response headers, you can convert the received RestResult to HttpRestResult.
     *
     * @param url          url
     * @param header       http header param
     * @param paramValues  http query param
     * @param bodyValues   http body param
     * @param responseType return type
     * @param callback     callback {@link Callback#onReceive(com.alibaba.nacos.common.model.RestResult)}
     * @throws Exception ex
     */
    public <T> void putFrom(String url, Header header, Map<String, String> paramValues, Map<String, String> bodyValues,
            Type responseType, Callback<T> callback) throws Exception {
        execute(url, HttpMethod.PUT, new RequestHttpEntity(header.setContentType(MediaType.APPLICATION_FORM_URLENCODED),
                Query.newInstance().initParams(paramValues), bodyValues), responseType, callback);
    }
    
    /**
     * async http post Create a new resource by POSTing the given object to the http request.
     *
     * <p>URL request params are expanded using the given query {@link Query}.
     *
     * <p>{@code responseType} can be an RestResult or RestResult data {@code T} type.
     *
     * <p>{@code callback} Result callback execution,
     * if you need response headers, you can convert the received RestResult to HttpRestResult.
     *
     * @param url          url
     * @param header       http header param
     * @param query        http query param
     * @param body         http body param
     * @param responseType return type
     * @param callback     callback {@link Callback#onReceive(com.alibaba.nacos.common.model.RestResult)}
     * @throws Exception ex
     */
    public <T> void post(String url, Header header, Query query, Object body, Type responseType, Callback<T> callback)
            throws Exception {
        execute(url, HttpMethod.POST, new RequestHttpEntity(header, query, body), responseType, callback);
    }
    
    /**
     * async http post Json Create a new resource by POSTing the given object to the http request, http header
     * contentType default 'application/json;charset=UTF-8'.
     *
     * <p>URL request params are expanded using the given map {@code paramValues}.
     *
     * <p>{@code responseType} can be an RestResult or RestResult data {@code T} type.
     *
     * <p>{@code callback} Result callback execution,
     * if you need response headers, you can convert the received RestResult to HttpRestResult.
     *
     * @param url          url
     * @param header       http header param
     * @param paramValues  http query param
     * @param body         http body param
     * @param responseType return type
     * @param callback     callback {@link Callback#onReceive(com.alibaba.nacos.common.model.RestResult)}
     * @throws Exception ex
     */
    public <T> void postJson(String url, Header header, Map<String, String> paramValues, String body, Type responseType,
            Callback<T> callback) throws Exception {
        execute(url, HttpMethod.POST, new RequestHttpEntity(header.setContentType(MediaType.APPLICATION_JSON),
                Query.newInstance().initParams(paramValues), body), responseType, callback);
    }
    
    /**
     * async http post from Create a new resource by PUTting the given map {@code bodyValues} to http request, http
     * header contentType default 'application/x-www-form-urlencoded;charset=utf-8'.
     *
     * <p>URL request params are expanded using the given query {@link Query}.
     *
     * <p>{@code responseType} can be an RestResult or RestResult data {@code T} type.
     *
     * <p>{@code callback} Result callback execution,
     * if you need response headers, you can convert the received RestResult to HttpRestResult.
     *
     * @param url          url
     * @param header       http header param
     * @param query        http query param
     * @param bodyValues   http body param
     * @param responseType return type
     * @param callback     callback {@link Callback#onReceive(com.alibaba.nacos.common.model.RestResult)}
     * @throws Exception ex
     */
    public <T> void postFrom(String url, Header header, Query query, Map<String, String> bodyValues, Type responseType,
            Callback<T> callback) throws Exception {
        execute(url, HttpMethod.POST,
                new RequestHttpEntity(header.setContentType(MediaType.APPLICATION_FORM_URLENCODED), query, bodyValues),
                responseType, callback);
    }
    
    /**
     * async http post from Create a new resource by PUTting the given map {@code bodyValues} to http request, http
     * header contentType default 'application/x-www-form-urlencoded;charset=utf-8'.
     *
     * <p>URL request params are expanded using the given map {@code paramValues}.
     *
     * <p>{@code responseType} can be an RestResult or RestResult data {@code T} type.
     *
     * <p>{@code callback} Result callback execution,
     * if you need response headers, you can convert the received RestResult to HttpRestResult.
     *
     * @param url          url
     * @param header       http header param
     * @param paramValues  http query param
     * @param bodyValues   http body param
     * @param responseType return type
     * @param callback     callback {@link Callback#onReceive(com.alibaba.nacos.common.model.RestResult)}
     * @throws Exception ex
     */
    public <T> void postFrom(String url, Header header, Map<String, String> paramValues, Map<String, String> bodyValues,
            Type responseType, Callback<T> callback) throws Exception {
        execute(url, HttpMethod.POST,
                new RequestHttpEntity(header.setContentType(MediaType.APPLICATION_FORM_URLENCODED),
                        Query.newInstance().initParams(paramValues), bodyValues), responseType, callback);
        
    }
    
    private <T> void execute(String url, String httpMethod, RequestHttpEntity requestEntity, Type responseType,
            Callback<T> callback) throws Exception {
        URI uri = HttpUtils.buildUri(url, requestEntity.getQuery());
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("HTTP " + httpMethod + " " + url);
        }
        clientRequest.execute(uri, httpMethod, requestEntity, responseType, callback);
    }
    
    /**
     * close request client.
     */
    public void close() throws Exception {
        clientRequest.close();
    }
}
