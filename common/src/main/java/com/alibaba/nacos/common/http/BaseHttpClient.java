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

import com.alibaba.nacos.common.constant.HttpHeaderConsts;
import com.alibaba.nacos.common.http.handler.ResponseHandler;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.model.RestResult;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.utils.HttpClientUtils;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.util.EntityUtils;

import java.lang.reflect.Type;
import java.net.URI;

/**
 * Base http client.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 * @deprecated Refer to the new {@link com.alibaba.nacos.common.http.client.request.HttpClientRequest}
 */
@Deprecated
public abstract class BaseHttpClient {
    
    protected <T> RestResult<T> execute(CloseableHttpClient httpClient, final Type type, HttpUriRequest request)
            throws Exception {
        CloseableHttpResponse response = httpClient.execute(request);
        try {
            final String body = EntityUtils.toString(response.getEntity());
            RestResult<T> data = ResponseHandler.convert(body, type);
            return data;
        } finally {
            HttpClientUtils.closeQuietly(response);
        }
    }
    
    protected <T> void execute(CloseableHttpAsyncClient httpAsyncClient, final Type type, final Callback<T> callback,
            final HttpUriRequest request) {
        if (!httpAsyncClient.isRunning()) {
            throw new IllegalArgumentException("httpAsyncClient already shutdown");
        }
        httpAsyncClient.execute(request, new FutureCallback<HttpResponse>() {
            
            @Override
            public void completed(HttpResponse response) {
                try {
                    final String body = EntityUtils.toString(response.getEntity());
                    RestResult<T> data = ResponseHandler.convert(body, type);
                    data.setCode(response.getStatusLine().getStatusCode());
                    callback.onReceive(data);
                } catch (Throwable e) {
                    callback.onError(e);
                }
            }
            
            @Override
            public void failed(Exception ex) {
                callback.onError(ex);
            }
            
            @Override
            public void cancelled() {
                callback.onCancel();
            }
        });
    }
    
    protected String buildUrl(String baseUrl, Query query) {
        if (query.isEmpty()) {
            return baseUrl;
        }
        return baseUrl + "?" + query.toQueryUrl();
    }
    
    protected HttpRequestBase build(String url, Header header, String method) throws Exception {
        return build(url, header, null, method);
    }
    
    protected HttpRequestBase build(String url, Header header, Object body, String method) throws Exception {
        
        final BaseHttpMethod httpMethod = BaseHttpMethod.sourceOf(method);
        final HttpRequestBase httpRequestBase = httpMethod.init(url);
        HttpUtils.initRequestHeader(httpRequestBase, header);
        HttpUtils.initRequestEntity(httpRequestBase, body, header.getValue(HttpHeaderConsts.CONTENT_TYPE));
        return httpRequestBase;
    }
    
    public static class HttpGetWithEntity extends HttpEntityEnclosingRequestBase {
        
        public static final String METHOD_NAME = "GET";
        
        public HttpGetWithEntity(String url) {
            super();
            setURI(URI.create(url));
        }
        
        @Override
        public String getMethod() {
            return METHOD_NAME;
        }
    }
    
}
