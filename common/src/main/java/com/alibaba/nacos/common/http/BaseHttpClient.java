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
import com.alibaba.nacos.common.http.handler.ResponseHandler;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.model.HttpRestResult;
import com.alibaba.nacos.common.model.RestResult;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.utils.HttpClientUtils;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.net.URI;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public abstract class BaseHttpClient {

    protected <T> HttpRestResult<T> execute(CloseableHttpClient httpClient,
                                            final TypeReference<RestResult<T>> reference,
                                            HttpUriRequest request)
            throws Exception {
        CloseableHttpResponse response = httpClient.execute(request);
        try {
            final String body = EntityUtils.toString(response.getEntity());
            HttpRestResult<T> resResult = new HttpRestResult<T>();
            resResult.setHttpCode(response.getStatusLine().getStatusCode());
            if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                RestResult<T> data = ResponseHandler.convert(body, reference);
                if (data != null && data.getCode() == HttpStatus.SC_OK) {
                    resResult.setCode(data.getCode());
                    resResult.setData(data.getData());
                    return resResult;
                } else {
                    resResult.setCode(response.getStatusLine().getStatusCode());
                    resResult.setMessage(data != null ? data.getMessage() : "");
                }
            } else {
                resResult.setMessage(body);
            }
            resResult.setHeader(convertHeader(response.getAllHeaders()));
            return resResult;
        } finally {
            HttpClientUtils.closeQuietly(response);
        }
    }

    protected <T> void execute(CloseableHttpAsyncClient httpAsyncClient,
                               final TypeReference<RestResult<T>> reference,
                               final Callback<T> callback,
                               final HttpUriRequest request) {
        httpAsyncClient.execute(request, new FutureCallback<HttpResponse>() {

            @Override
            public void completed(HttpResponse response) {
                try {
                    final String body = EntityUtils.toString(response.getEntity());
                    HttpRestResult<T> resResult = new HttpRestResult<T>();
                    resResult.setHttpCode(response.getStatusLine().getStatusCode());
                    if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                        RestResult<T> data = ResponseHandler.convert(body, reference);
                        if (data != null && data.getCode() == HttpStatus.SC_OK) {
                            resResult.setCode(200);
                            resResult.setData(data.getData());
                        } else {
                            resResult.setCode(response.getStatusLine().getStatusCode());
                            resResult.setMessage(data != null ? data.getMessage() : "");
                        }
                    } else {
                        resResult.setMessage(body);
                    }
                    resResult.setHeader(convertHeader(response.getAllHeaders()));
                    callback.onReceive(resResult);
                } catch (IOException e) {
                    callback.onError(e);
                }
            }

            @Override
            public void failed(Exception ex) {
                callback.onError(ex);
            }

            @Override
            public void cancelled() {

            }
        });
    }

    protected String buildUrl(String baseUrl, Query query) {
        String url = baseUrl + "?" + query.toQueryUrl();
        return url;
    }

    protected HttpRequestBase build(String url, Header header, String method) {
        return build(url, header, null, method);
    }

    protected HttpRequestBase build(String url, Header header, Object body,
                                    String method) {

        BaseHttpMethod httpMethod = BaseHttpMethod.sourceOf(method);
        httpMethod.init(url);
        httpMethod.initHeader(header);
        httpMethod.initEntity(body, header.getValue("Content-Type"));
        return httpMethod.getRequestBase();
    }

    private Header convertHeader(org.apache.http.Header[] headers) {
        final Header nHeader = Header.newInstance();
        for (org.apache.http.Header header : headers) {
            nHeader.addParam(header.getName(), header.getValue());
        }
        return nHeader;
    }

    public static class HttpGetWithEntity extends HttpEntityEnclosingRequestBase {

        public final static String METHOD_NAME = "GET";

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
