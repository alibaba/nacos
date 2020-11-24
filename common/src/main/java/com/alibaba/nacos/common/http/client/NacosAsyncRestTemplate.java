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
import com.alibaba.nacos.common.http.client.handler.ResponseHandler;
import com.alibaba.nacos.common.http.client.request.AsyncHttpClientRequest;
import com.alibaba.nacos.common.http.client.response.HttpClientResponse;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.MediaType;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.model.RequestHttpEntity;
import com.alibaba.nacos.common.utils.HttpMethod;
import org.slf4j.Logger;

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
public class NacosAsyncRestTemplate extends AbstractNacosRestTemplate {
    
    private final AsyncHttpClientRequest clientRequest;
    
    public NacosAsyncRestTemplate(Logger logger, AsyncHttpClientRequest clientRequest) {
        super(logger);
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
     */
    public <T> void get(String url, Header header, Query query, Type responseType, Callback<T> callback) {
        execute(url, HttpMethod.GET, new RequestHttpEntity(header, query), responseType, callback);
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
     */
    public <T> void getLarge(String url, Header header, Query query, Object body, Type responseType,
            Callback<T> callback) {
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
     */
    public <T> void delete(String url, Header header, Query query, Type responseType, Callback<T> callback) {
        execute(url, HttpMethod.DELETE, new RequestHttpEntity(header, query), responseType, callback);
    }
    
    /**
     * async http delete large request, when the parameter exceeds the URL limit, you can use this method to put the
     * parameter into the body pass.
     *
     * <p>{@code responseType} can be an RestResult or RestResult data {@code T} type
     *
     * <p>{@code callback} Result callback execution,
     * if you need response headers, you can convert the received RestResult to HttpRestResult.
     *
     * @param url          url
     * @param header       http header param
     * @param body         body
     * @param responseType return type
     * @param callback     callback {@link Callback#onReceive(com.alibaba.nacos.common.model.RestResult)}
     */
    public <T> void delete(String url, Header header, String body, Type responseType, Callback<T> callback) {
        execute(url, HttpMethod.DELETE_LARGE,
                new RequestHttpEntity(header.setContentType(MediaType.APPLICATION_JSON), Query.EMPTY, body),
                responseType, callback);
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
     */
    public <T> void put(String url, Header header, Query query, Object body, Type responseType, Callback<T> callback) {
        execute(url, HttpMethod.PUT, new RequestHttpEntity(header, query, body), responseType, callback);
    }
    
    /**
     * async http put Json Create a new resource by PUTting the given body to http request, http header contentType
     * default 'application/json;charset=UTF-8'.
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
     */
    public <T> void putJson(String url, Header header, Query query, String body, Type responseType,
            Callback<T> callback) {
        execute(url, HttpMethod.PUT,
                new RequestHttpEntity(header.setContentType(MediaType.APPLICATION_JSON), query, body), responseType,
                callback);
    }
    
    /**
     * async http put Json Create a new resource by PUTting the given body to http request, http header contentType
     * default 'application/json;charset=UTF-8'.
     *
     * <p>{@code responseType} can be an RestResult or RestResult data {@code T} type
     *
     * <p>{@code callback} Result callback execution,
     * if you need response headers, you can convert the received RestResult to HttpRestResult.
     *
     * @param url          url
     * @param header       http header param
     * @param body         http body param
     * @param responseType return type
     * @param callback     callback {@link Callback#onReceive(com.alibaba.nacos.common.model.RestResult)}
     */
    public <T> void putJson(String url, Header header, String body, Type responseType, Callback<T> callback) {
        execute(url, HttpMethod.PUT, new RequestHttpEntity(header.setContentType(MediaType.APPLICATION_JSON), body),
                responseType, callback);
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
     */
    public <T> void putForm(String url, Header header, Query query, Map<String, String> bodyValues, Type responseType,
            Callback<T> callback) {
        execute(url, HttpMethod.PUT,
                new RequestHttpEntity(header.setContentType(MediaType.APPLICATION_FORM_URLENCODED), query, bodyValues),
                responseType, callback);
    }
    
    /**
     * async http put from Create a new resource by PUTting the given map {@code bodyValues} to http request, http
     * header contentType default 'application/x-www-form-urlencoded;charset=utf-8'.
     *
     * <p>{@code responseType} can be an RestResult or RestResult data {@code T} type.
     *
     * <p>{@code callback} Result callback execution,
     * if you need response headers, you can convert the received RestResult to HttpRestResult.
     *
     * @param url          url
     * @param header       http header param
     * @param bodyValues   http body param
     * @param responseType return type
     * @param callback     callback {@link Callback#onReceive(com.alibaba.nacos.common.model.RestResult)}
     */
    public <T> void putForm(String url, Header header, Map<String, String> bodyValues, Type responseType,
            Callback<T> callback) {
        execute(url, HttpMethod.PUT,
                new RequestHttpEntity(header.setContentType(MediaType.APPLICATION_FORM_URLENCODED), bodyValues),
                responseType, callback);
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
     */
    public <T> void post(String url, Header header, Query query, Object body, Type responseType, Callback<T> callback) {
        execute(url, HttpMethod.POST, new RequestHttpEntity(header, query, body), responseType, callback);
    }
    
    /**
     * async http post Json Create a new resource by POSTing the given object to the http request, http header
     * contentType default 'application/json;charset=UTF-8'.
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
     */
    public <T> void postJson(String url, Header header, Query query, String body, Type responseType,
            Callback<T> callback) {
        execute(url, HttpMethod.POST,
                new RequestHttpEntity(header.setContentType(MediaType.APPLICATION_JSON), query, body), responseType,
                callback);
    }
    
    /**
     * async http post Json Create a new resource by POSTing the given object to the http request, http header
     * contentType default 'application/json;charset=UTF-8'.
     *
     * <p>{@code responseType} can be an RestResult or RestResult data {@code T} type.
     *
     * <p>{@code callback} Result callback execution,
     * if you need response headers, you can convert the received RestResult to HttpRestResult.
     *
     * @param url          url
     * @param header       http header param
     * @param body         http body param
     * @param responseType return type
     * @param callback     callback {@link Callback#onReceive(com.alibaba.nacos.common.model.RestResult)}
     */
    public <T> void postJson(String url, Header header, String body, Type responseType, Callback<T> callback) {
        execute(url, HttpMethod.POST, new RequestHttpEntity(header.setContentType(MediaType.APPLICATION_JSON), body),
                responseType, callback);
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
     */
    public <T> void postForm(String url, Header header, Query query, Map<String, String> bodyValues, Type responseType,
            Callback<T> callback) {
        execute(url, HttpMethod.POST,
                new RequestHttpEntity(header.setContentType(MediaType.APPLICATION_FORM_URLENCODED), query, bodyValues),
                responseType, callback);
    }
    
    /**
     * async http post from Create a new resource by PUTting the given map {@code bodyValues} to http request, http
     * header contentType default 'application/x-www-form-urlencoded;charset=utf-8'.
     *
     * <p>{@code responseType} can be an RestResult or RestResult data {@code T} type.
     *
     * <p>{@code callback} Result callback execution,
     * if you need response headers, you can convert the received RestResult to HttpRestResult.
     *
     * @param url          url
     * @param header       http header param
     * @param bodyValues   http body param
     * @param responseType return type
     * @param callback     callback {@link Callback#onReceive(com.alibaba.nacos.common.model.RestResult)}
     */
    public <T> void postForm(String url, Header header, Map<String, String> bodyValues, Type responseType,
            Callback<T> callback) {
        execute(url, HttpMethod.POST,
                new RequestHttpEntity(header.setContentType(MediaType.APPLICATION_FORM_URLENCODED), bodyValues),
                responseType, callback);
    }
    
    @SuppressWarnings("unchecked")
    private <T> void execute(String url, String httpMethod, RequestHttpEntity requestEntity, Type type,
            Callback<T> callback) {
        try {
            URI uri = HttpUtils.buildUri(url, requestEntity.getQuery());
            if (logger.isDebugEnabled()) {
                logger.debug("HTTP method: {}, url: {}, body: {}", httpMethod, uri, requestEntity.getBody());
            }
            ResponseHandler<T> responseHandler = super.selectResponseHandler(type);
            clientRequest.execute(uri, httpMethod, requestEntity, responseHandler, callback);
        } catch (Exception e) {
            // When an exception occurs, use Callback to pass it instead of throw it directly.
            callback.onError(e);
        }
    }
    
    /**
     * close request client.
     */
    public void close() throws Exception {
        clientRequest.close();
    }
}
