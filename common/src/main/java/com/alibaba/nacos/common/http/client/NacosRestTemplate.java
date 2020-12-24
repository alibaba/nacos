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

import com.alibaba.nacos.common.http.HttpClientConfig;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.http.HttpUtils;
import com.alibaba.nacos.common.http.client.handler.ResponseHandler;
import com.alibaba.nacos.common.http.client.request.DefaultHttpClientRequest;
import com.alibaba.nacos.common.http.client.request.HttpClientRequest;
import com.alibaba.nacos.common.http.client.request.JdkHttpClientRequest;
import com.alibaba.nacos.common.http.client.response.HttpClientResponse;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.MediaType;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.model.RequestHttpEntity;
import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.common.utils.HttpMethod;
import org.slf4j.Logger;

import java.lang.reflect.Type;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Nacos rest template Interface specifying a basic set of RESTful operations.
 *
 * @author mai.jh
 * @see HttpClientRequest
 * @see HttpClientResponse
 */
public class NacosRestTemplate extends AbstractNacosRestTemplate {
    
    private final HttpClientRequest requestClient;
    
    private final List<HttpClientRequestInterceptor> interceptors = new ArrayList<HttpClientRequestInterceptor>();
    
    public NacosRestTemplate(Logger logger, HttpClientRequest requestClient) {
        super(logger);
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
     * <p>{@code config} Specify the request config via {@link HttpClientConfig}
     *
     * @param url          url
     * @param config       http config
     * @param header       headers
     * @param query        http query param
     * @param responseType return type
     * @return {@link HttpRestResult}
     * @throws Exception ex
     */
    public <T> HttpRestResult<T> get(String url, HttpClientConfig config, Header header, Query query, Type responseType)
            throws Exception {
        RequestHttpEntity requestHttpEntity = new RequestHttpEntity(config, header, query);
        return execute(url, HttpMethod.GET, requestHttpEntity, responseType);
    }
    
    /**
     * get request, may be pulling a lot of data URL request params are expanded using the given query {@link Query},
     * More request parameters can be set via body.
     *
     * <p>This method can only be used when HttpClientRequest is implemented by {@link DefaultHttpClientRequest}, note:
     * {@link JdkHttpClientRequest} Implementation does not support this method.
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
     * http delete URL request params are expanded using the given query {@link Query}.
     *
     * <p>{@code responseType} can be an HttpRestResult or HttpRestResult data {@code T} type.
     *
     * <p>{@code config} Specify the request config via {@link HttpClientConfig}
     *
     * @param url          url
     * @param config       http config
     * @param header       http header param
     * @param query        http query param
     * @param responseType return type
     * @return {@link HttpRestResult}
     * @throws Exception ex
     */
    public <T> HttpRestResult<T> delete(String url, HttpClientConfig config, Header header, Query query,
            Type responseType) throws Exception {
        return execute(url, HttpMethod.DELETE, new RequestHttpEntity(config, header, query), responseType);
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
    public <T> HttpRestResult<T> putJson(String url, Header header, Query query, String body, Type responseType)
            throws Exception {
        RequestHttpEntity requestHttpEntity = new RequestHttpEntity(header.setContentType(MediaType.APPLICATION_JSON),
                query, body);
        return execute(url, HttpMethod.PUT, requestHttpEntity, responseType);
    }
    
    /**
     * http put json Create a new resource by PUTting the given body to http request, http header contentType default
     * 'application/json;charset=UTF-8'.
     *
     * <p>{@code responseType} can be an HttpRestResult or HttpRestResult data {@code T} type.
     *
     * @param url          url
     * @param header       http header param
     * @param body         http body param
     * @param responseType return type
     * @return {@link HttpRestResult}
     * @throws Exception ex
     */
    public <T> HttpRestResult<T> putJson(String url, Header header, String body, Type responseType) throws Exception {
        RequestHttpEntity requestHttpEntity = new RequestHttpEntity(header.setContentType(MediaType.APPLICATION_JSON),
                body);
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
    public <T> HttpRestResult<T> putForm(String url, Header header, Query query, Map<String, String> bodyValues,
            Type responseType) throws Exception {
        RequestHttpEntity requestHttpEntity = new RequestHttpEntity(
                header.setContentType(MediaType.APPLICATION_FORM_URLENCODED), query, bodyValues);
        return execute(url, HttpMethod.PUT, requestHttpEntity, responseType);
    }
    
    /**
     * http put from Create a new resource by PUTting the given map {@code bodyValues} to http request, http header
     * contentType default 'application/x-www-form-urlencoded;charset=utf-8'.
     *
     * <p>{@code responseType} can be an HttpRestResult or HttpRestResult data {@code T} type.
     *
     * @param url          url
     * @param header       http header param
     * @param bodyValues   http body param
     * @param responseType return type
     * @return {@link HttpRestResult}
     * @throws Exception ex
     */
    public <T> HttpRestResult<T> putForm(String url, Header header, Map<String, String> bodyValues, Type responseType)
            throws Exception {
        RequestHttpEntity requestHttpEntity = new RequestHttpEntity(
                header.setContentType(MediaType.APPLICATION_FORM_URLENCODED), bodyValues);
        return execute(url, HttpMethod.PUT, requestHttpEntity, responseType);
    }
    
    /**
     * http put from Create a new resource by PUTting the given map {@code bodyValues} to http request, http header
     * contentType default 'application/x-www-form-urlencoded;charset=utf-8'.
     *
     * <p>{@code responseType} can be an HttpRestResult or HttpRestResult data {@code T} type.
     *
     * <p>{@code config} Specify the request config via {@link HttpClientConfig}
     *
     * @param url          url
     * @param config       http config
     * @param header       http header param
     * @param bodyValues   http body param
     * @param responseType return type
     * @return {@link HttpRestResult}
     * @throws Exception ex
     */
    public <T> HttpRestResult<T> putForm(String url, HttpClientConfig config, Header header,
            Map<String, String> bodyValues, Type responseType) throws Exception {
        RequestHttpEntity requestHttpEntity = new RequestHttpEntity(config,
                header.setContentType(MediaType.APPLICATION_FORM_URLENCODED), bodyValues);
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
    public <T> HttpRestResult<T> postJson(String url, Header header, Query query, String body, Type responseType)
            throws Exception {
        RequestHttpEntity requestHttpEntity = new RequestHttpEntity(header.setContentType(MediaType.APPLICATION_JSON),
                query, body);
        return execute(url, HttpMethod.POST, requestHttpEntity, responseType);
    }
    
    /**
     * http post json Create a new resource by POSTing the given object to the http request, http header contentType
     * default 'application/json;charset=UTF-8'.
     *
     * <p>{@code responseType} can be an HttpRestResult or HttpRestResult data {@code T} type.
     *
     * @param url          url
     * @param header       http header param
     * @param body         http body param
     * @param responseType return type
     * @return {@link HttpRestResult}
     * @throws Exception ex
     */
    public <T> HttpRestResult<T> postJson(String url, Header header, String body, Type responseType) throws Exception {
        RequestHttpEntity requestHttpEntity = new RequestHttpEntity(header.setContentType(MediaType.APPLICATION_JSON),
                body);
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
    public <T> HttpRestResult<T> postForm(String url, Header header, Query query, Map<String, String> bodyValues,
            Type responseType) throws Exception {
        RequestHttpEntity requestHttpEntity = new RequestHttpEntity(
                header.setContentType(MediaType.APPLICATION_FORM_URLENCODED), query, bodyValues);
        return execute(url, HttpMethod.POST, requestHttpEntity, responseType);
    }
    
    /**
     * http post from Create a new resource by PUTting the given map {@code bodyValues} to http request, http header
     * contentType default 'application/x-www-form-urlencoded;charset=utf-8'.
     *
     * <p>{@code responseType} can be an HttpRestResult or HttpRestResult data {@code T} type.
     *
     * @param url          url
     * @param header       http header param
     * @param bodyValues   http body param
     * @param responseType return type
     * @return {@link HttpRestResult}
     * @throws Exception ex
     */
    public <T> HttpRestResult<T> postForm(String url, Header header, Map<String, String> bodyValues, Type responseType)
            throws Exception {
        RequestHttpEntity requestHttpEntity = new RequestHttpEntity(
                header.setContentType(MediaType.APPLICATION_FORM_URLENCODED), bodyValues);
        return execute(url, HttpMethod.POST, requestHttpEntity, responseType);
    }
    
    /**
     * http post from Create a new resource by PUTting the given map {@code bodyValues} to http request, http header
     * contentType default 'application/x-www-form-urlencoded;charset=utf-8'.
     *
     * <p>{@code responseType} can be an HttpRestResult or HttpRestResult data {@code T} type.
     *
     * <p>{@code config} Specify the request config via {@link HttpClientConfig}
     *
     * @param url          url
     * @param config       http config
     * @param header       http header param
     * @param bodyValues   http body param
     * @param responseType return type
     * @return {@link HttpRestResult}
     * @throws Exception ex
     */
    public <T> HttpRestResult<T> postForm(String url, HttpClientConfig config, Header header,
            Map<String, String> bodyValues, Type responseType) throws Exception {
        RequestHttpEntity requestHttpEntity = new RequestHttpEntity(config,
                header.setContentType(MediaType.APPLICATION_FORM_URLENCODED), bodyValues);
        return execute(url, HttpMethod.POST, requestHttpEntity, responseType);
    }
    
    /**
     * Execute the HTTP method to the given URI template, writing the given request entity to the request, and returns
     * the response as {@link HttpRestResult}.
     *
     * @param url          url
     * @param header       http header param
     * @param query        http query param
     * @param bodyValues   http body param
     * @param httpMethod   http method
     * @param responseType return type
     * @return {@link HttpRestResult}
     * @throws Exception ex
     */
    public <T> HttpRestResult<T> exchangeForm(String url, Header header, Query query, Map<String, String> bodyValues,
            String httpMethod, Type responseType) throws Exception {
        RequestHttpEntity requestHttpEntity = new RequestHttpEntity(
                header.setContentType(MediaType.APPLICATION_FORM_URLENCODED), query, bodyValues);
        return execute(url, httpMethod, requestHttpEntity, responseType);
    }
    
    /**
     * Execute the HTTP method to the given URI template, writing the given request entity to the request, and returns
     * the response as {@link HttpRestResult}.
     *
     * @param url          url
     * @param config       HttpClientConfig
     * @param header       http header param
     * @param query        http query param
     * @param body         http body param
     * @param httpMethod   http method
     * @param responseType return type
     * @return {@link HttpRestResult}
     * @throws Exception ex
     */
    public <T> HttpRestResult<T> exchange(String url, HttpClientConfig config, Header header, Query query,
            Object body, String httpMethod, Type responseType) throws Exception {
        RequestHttpEntity requestHttpEntity = new RequestHttpEntity(config, header, query, body);
        return execute(url, httpMethod, requestHttpEntity, responseType);
    }
    
    /**
     * Set the request interceptors that this accessor should use.
     *
     * @param interceptors {@link HttpClientRequestInterceptor}
     */
    public void setInterceptors(List<HttpClientRequestInterceptor> interceptors) {
        if (this.interceptors != interceptors) {
            this.interceptors.clear();
            this.interceptors.addAll(interceptors);
        }
    }
    
    /**
     * Return the request interceptors that this accessor uses.
     *
     * <p>The returned {@link List} is active and may get appended to.
     */
    public List<HttpClientRequestInterceptor> getInterceptors() {
        return interceptors;
    }
    
    @SuppressWarnings("unchecked")
    private <T> HttpRestResult<T> execute(String url, String httpMethod, RequestHttpEntity requestEntity,
            Type responseType) throws Exception {
        URI uri = HttpUtils.buildUri(url, requestEntity.getQuery());
        if (logger.isDebugEnabled()) {
            logger.debug("HTTP method: {}, url: {}, body: {}", httpMethod, uri, requestEntity.getBody());
        }
        
        ResponseHandler<T> responseHandler = super.selectResponseHandler(responseType);
        HttpClientResponse response = null;
        try {
            response = this.requestClient().execute(uri, httpMethod, requestEntity);
            return responseHandler.handle(response);
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }
    
    private HttpClientRequest requestClient() {
        if (CollectionUtils.isNotEmpty(interceptors)) {
            if (logger.isDebugEnabled()) {
                logger.debug("Execute via interceptors :{}", interceptors);
            }
            return new InterceptingHttpClientRequest(requestClient, interceptors.iterator());
        }
        return requestClient;
    }
    
    /**
     * close request client.
     */
    public void close() throws Exception {
        requestClient.close();
    }
    
}
