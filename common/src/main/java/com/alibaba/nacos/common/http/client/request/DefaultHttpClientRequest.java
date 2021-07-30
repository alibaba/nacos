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
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.CloseableHttpClient;

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
    
    public DefaultHttpClientRequest(CloseableHttpClient client) {
        this.client = client;
    }
    
    @Override
    public HttpClientResponse execute(URI uri, String httpMethod, RequestHttpEntity requestHttpEntity)
            throws Exception {
        HttpRequestBase request = build(uri, httpMethod, requestHttpEntity);
        CloseableHttpResponse response = client.execute(request);
        return new DefaultClientHttpResponse(response);
    }
    
    static HttpRequestBase build(URI uri, String method, RequestHttpEntity requestHttpEntity) throws Exception {
        final Header headers = requestHttpEntity.getHeaders();
        final BaseHttpMethod httpMethod = BaseHttpMethod.sourceOf(method);
        final HttpRequestBase httpRequestBase = httpMethod.init(uri.toString());
        HttpUtils.initRequestHeader(httpRequestBase, headers);
        if (MediaType.APPLICATION_FORM_URLENCODED.equals(headers.getValue(HttpHeaderConsts.CONTENT_TYPE))
                && requestHttpEntity.getBody() instanceof Map) {
            HttpUtils.initRequestFromEntity(httpRequestBase, (Map<String, String>) requestHttpEntity.getBody(), headers.getCharset());
        } else {
            HttpUtils.initRequestEntity(httpRequestBase, requestHttpEntity.getBody(), headers);
        }
        replaceDefaultConfig(httpRequestBase, requestHttpEntity.getHttpClientConfig());
        return httpRequestBase;
    }
    
    /**
     * Replace the HTTP config created by default with the HTTP config specified in the request.
     *
     * @param requestBase      requestBase
     * @param httpClientConfig http config
     */
    private static void replaceDefaultConfig(HttpRequestBase requestBase, HttpClientConfig httpClientConfig) {
        if (httpClientConfig == null) {
            return;
        }
        requestBase.setConfig(RequestConfig.custom()
                .setConnectTimeout(httpClientConfig.getConTimeOutMillis())
                .setSocketTimeout(httpClientConfig.getReadTimeOutMillis()).build());
    }
    
    @Override
    public void close() throws IOException {
        client.close();
    }
}
