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

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.alibaba.nacos.common.http.handler.RequestHandler;
import com.alibaba.nacos.common.http.handler.ResponseHandler;
import com.alibaba.nacos.common.http.param.Body;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.model.HttpResResult;
import com.alibaba.nacos.common.model.ResResult;
import com.alibaba.nacos.common.utils.HttpMethod;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpOptions;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpTrace;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.utils.HttpClientUtils;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public abstract class BaseHttpClient {

    protected <T> HttpResResult<T> execute(CloseableHttpClient httpClient,
                                           final TypeReference<ResResult<T>> reference,
                                           HttpUriRequest request)
            throws Exception {
        CloseableHttpResponse response = httpClient.execute(request);
        try {
            final String body = EntityUtils.toString(response.getEntity());
            HttpResResult<T> resResult = new HttpResResult<T>();
            resResult.setHttpCode(response.getStatusLine().getStatusCode());
            if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                ResResult<T> data = ResponseHandler.convert(body, reference);
                if (data != null && data.getCode() == HttpStatus.SC_OK) {
                    resResult.setCode(200);
                    resResult.setData(data.getData());
                    resResult.setErrMsg(body);
                    return resResult;
                } else {
                    resResult.setErrMsg(data != null ? data.getErrMsg() : "");
                }
            } else {
                resResult.setErrMsg(body);
            }
            resResult.setHeader(convertHeader(response.getAllHeaders()));
            return resResult;
        } finally {
            HttpClientUtils.closeQuietly(response);
        }
    }

    protected <T> void execute(CloseableHttpAsyncClient httpAsyncClient,
                               final TypeReference<ResResult<T>> reference,
                               final Callback<T> callback,
                               final HttpUriRequest request) {
        httpAsyncClient.execute(request, new FutureCallback<HttpResponse>() {

            @Override
            public void completed(HttpResponse response) {
                try {
                    final String body = EntityUtils.toString(response.getEntity());
                    HttpResResult<T> resResult = new HttpResResult<T>();
                    resResult.setHttpCode(response.getStatusLine().getStatusCode());
                    if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                        ResResult<T> data = ResponseHandler.convert(body, reference);
                        if (data != null && data.getCode() == HttpStatus.SC_OK) {
                            resResult.setCode(200);
                            resResult.setData(data.getData());
                            resResult.setErrMsg(body);
                        } else {
                            resResult.setErrMsg(data != null ? data.getErrMsg() : "");
                        }
                    } else {
                        resResult.setErrMsg(body);
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
        return baseUrl + "?" + query.toQueryUrl();
    }

    protected HttpRequestBase build(String url, Header header, String method) {
        return build(url, header, Body.EMPTY, method);
    }

    protected HttpRequestBase build(String url, Header header, Body body,
                                    String method) {
        if (HttpMethod.GET.equalsIgnoreCase(method)) {
            HttpGet get = new HttpGet(url);
            initHeader(get, header);
            return get;
        }
        if (HttpMethod.GET.equalsIgnoreCase(method) && body != null) {
            HttpGetWithEntity get = new HttpGetWithEntity(url);
            initHeader(get, header);
            initEntity(get, body, header.getValue("Content-Type"));
            return get;
        }
        if (HttpMethod.DELETE.equalsIgnoreCase(method)) {
            HttpDelete delete = new HttpDelete(url);
            initHeader(delete, header);
            return delete;
        }
        if (HttpMethod.HEAD.equalsIgnoreCase(method)) {
            HttpHead head = new HttpHead(url);
            initHeader(head, header);
            return head;
        }
        if (HttpMethod.OPTIONS.equalsIgnoreCase(method)) {
            HttpOptions options = new HttpOptions(url);
            initHeader(options, header);
            return options;
        }
        if (HttpMethod.PATCH.equalsIgnoreCase(method)) {
            HttpPatch patch = new HttpPatch(url);
            initHeader(patch, header);
            initEntity(patch, body, header.getValue("Content-Type"));
        }
        if (HttpMethod.POST.equalsIgnoreCase(method)) {
            HttpPost post = new HttpPost(url);
            initHeader(post, header);
            initEntity(post, body, header.getValue("Content-Type"));
        }
        if (HttpMethod.PUT.equalsIgnoreCase(method)) {
            HttpPut put = new HttpPut(url);
            initHeader(put, header);
            initEntity(put, body, header.getValue("Content-Type"));
        }
        if (HttpMethod.TRACE.equalsIgnoreCase(method)) {
            HttpTrace trace = new HttpTrace(url);
            initHeader(trace, header);
        }
        throw new IllegalArgumentException("illegal http request method : [" + method + "]");
    }

    private void initHeader(HttpRequestBase requestBase, Header header) {
        Iterator<Map.Entry<String, String>> iterator = header.iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, String> entry = iterator.next();
            requestBase.setHeader(entry.getKey(), entry.getValue());
        }
    }

    private void initEntity(HttpEntityEnclosingRequest request, Body body, String mediaType) {
        ContentType contentType = ContentType.create(mediaType);
        if (ContentType.APPLICATION_FORM_URLENCODED.equals(contentType)) {
            UrlEncodedFormEntity entity = new UrlEncodedFormEntity(toList(body), Charset.defaultCharset());
            request.setEntity(entity);
            return;
        }
        StringEntity entity = new StringEntity(RequestHandler.parse(body.getData()), contentType);
        request.setEntity(entity);
    }

    private List<? extends NameValuePair> toList(Body body) {
        List<NameValuePair> list = new ArrayList<NameValuePair>();
        Iterator<Map.Entry<String, Object>> iterator = body.iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Object> entry = iterator.next();
            NameValuePair pair = new BasicNameValuePair(entry.getKey(), JSON.toJSONString(entry.getValue()));
            list.add(pair);
        }
        return list;
    }

    private Header convertHeader(org.apache.http.Header[] headers) {
        final Header nHeader = Header.newInstance();
        for (org.apache.http.Header header : headers) {
            nHeader.addParam(header.getName(), header.getValue());
        }
        return nHeader;
    }

    private static class HttpGetWithEntity extends HttpEntityEnclosingRequestBase {

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
