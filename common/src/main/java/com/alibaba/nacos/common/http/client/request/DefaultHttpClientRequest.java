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

package com.alibaba.nacos.common.http.client.request;

import com.alibaba.nacos.common.constant.HttpHeaderConsts;
import com.alibaba.nacos.common.http.BaseHttpMethod;
import com.alibaba.nacos.common.http.HttpClientConfig;
import com.alibaba.nacos.common.http.HttpUtils;
import com.alibaba.nacos.common.http.client.response.DefaultClientHttpResponse;
import com.alibaba.nacos.common.http.client.response.HttpClientResponse;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.MediaType;
import com.alibaba.nacos.common.model.RequestHttpEntity;
import com.alibaba.nacos.common.utils.IoUtils;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.util.Timeout;

import java.io.IOException;
import java.net.URI;
import java.util.Map;

/**
 * {@link HttpClientRequest} implementation that uses apache http client to execute streaming requests.
 *
 * @author mai.jh
 */
@SuppressWarnings({"unchecked", "resource"})
public class DefaultHttpClientRequest implements HttpClientRequest {
    
    private final CloseableHttpClient client;
    
    private final RequestConfig defaultConfig;
    
    public DefaultHttpClientRequest(CloseableHttpClient client, RequestConfig defaultConfig) {
        this.client = client;
        this.defaultConfig = defaultConfig;
    }
    
    @Override
    public HttpClientResponse execute(URI uri, String httpMethod, RequestHttpEntity requestHttpEntity)
            throws Exception {
        HttpUriRequestBase request = build(uri, httpMethod, requestHttpEntity, defaultConfig);
        // copy http response to simple type
        SimpleHttpResponse response = client.execute(request, httpResponse -> {
            SimpleHttpResponse simpleHttpResponse = SimpleHttpResponse.copy(httpResponse);
            ContentType contentType = ContentType.parse(httpResponse.getEntity().getContentType());
            String body = IoUtils.toString(httpResponse.getEntity().getContent(), null);
            simpleHttpResponse.setBody(body, contentType);
            return simpleHttpResponse;
        });
        return new DefaultClientHttpResponse(response);
    }
    
    static HttpUriRequestBase build(URI uri, String method, RequestHttpEntity requestHttpEntity,
            RequestConfig defaultConfig) throws Exception {
        final Header headers = requestHttpEntity.getHeaders();
        final BaseHttpMethod httpMethod = BaseHttpMethod.sourceOf(method);
        final HttpUriRequestBase httpRequestBase = httpMethod.init(uri.toString());
        
        HttpUtils.initRequestHeader(httpRequestBase, headers);
        if (MediaType.APPLICATION_FORM_URLENCODED.equals(headers.getValue(HttpHeaderConsts.CONTENT_TYPE))
                && requestHttpEntity.getBody() instanceof Map) {
            HttpUtils.initRequestFromEntity(httpRequestBase, (Map<String, String>) requestHttpEntity.getBody(),
                    headers.getCharset());
        } else {
            HttpUtils.initRequestEntity(httpRequestBase, requestHttpEntity.getBody(), headers);
        }
        
        mergeDefaultConfig(httpRequestBase, requestHttpEntity.getHttpClientConfig(), defaultConfig);
        return httpRequestBase;
    }
    
    /**
     * Merge the HTTP config created by default with the HTTP config specified in the request.
     *
     * @param requestBase      requestBase
     * @param httpClientConfig http config
     */
    private static void mergeDefaultConfig(HttpUriRequestBase requestBase, HttpClientConfig httpClientConfig,
            RequestConfig defaultConfig) {
        if (httpClientConfig == null) {
            return;
        }
        requestBase.setConfig(RequestConfig.copy(defaultConfig)
                .setConnectionRequestTimeout(Timeout.ofMilliseconds(httpClientConfig.getConTimeOutMillis()))
                .setResponseTimeout(Timeout.ofMilliseconds(httpClientConfig.getReadTimeOutMillis())).build());
    }
    
    @Override
    public void close() throws IOException {
        client.close();
    }
}
