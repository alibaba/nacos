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

import com.alibaba.nacos.common.http.Callback;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.http.client.handler.ResponseHandler;
import com.alibaba.nacos.common.http.client.response.DefaultClientHttpResponse;
import com.alibaba.nacos.common.model.RequestHttpEntity;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.reactor.DefaultConnectingIOReactor;
import org.apache.hc.core5.reactor.IOReactorStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;

/**
 * {@link AsyncHttpClientRequest} implementation that uses apache async http client to execute streaming requests.
 *
 * @author mai.jh
 */
public class DefaultAsyncHttpClientRequest implements AsyncHttpClientRequest {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultAsyncHttpClientRequest.class);
    
    private final CloseableHttpAsyncClient asyncClient;
    
    private final RequestConfig defaultConfig;
    
    public DefaultAsyncHttpClientRequest(CloseableHttpAsyncClient asyncClient, DefaultConnectingIOReactor ioReactor, RequestConfig defaultConfig) {
        this.asyncClient = asyncClient;
        this.defaultConfig = defaultConfig;
        if (this.asyncClient.getStatus() != IOReactorStatus.ACTIVE) {
            this.asyncClient.start();
        }
    }
    
    @Override
    public <T> void execute(URI uri, String httpMethod, RequestHttpEntity requestHttpEntity,
            final ResponseHandler<T> responseHandler, final Callback<T> callback) throws Exception {
        HttpUriRequestBase httpRequestBase = DefaultHttpClientRequest.build(uri, httpMethod, requestHttpEntity, defaultConfig);
        // IllegalStateException has been removed from ver.5.0, should catch it in DefaultConnectingIOReactor callback
        FutureCallback<SimpleHttpResponse> futureCallback = new FutureCallback<SimpleHttpResponse>() {
            @Override
            public void completed(SimpleHttpResponse result) {
                // SimpleHttpResponse doesn't need to close
                DefaultClientHttpResponse response = new DefaultClientHttpResponse(result);
                try {
                    HttpRestResult<T> httpRestResult = responseHandler.handle(response);
                    callback.onReceive(httpRestResult);
                } catch (Exception e) {
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
        };
        asyncClient.execute(SimpleHttpRequest.copy(httpRequestBase), futureCallback);
    }
    
    @Override
    public void close() throws IOException {
        this.asyncClient.close();
    }
}
