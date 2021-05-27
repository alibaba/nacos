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
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.utils.HttpClientUtils;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;

import java.io.IOException;
import java.net.URI;

/**
 * {@link AsyncHttpClientRequest} implementation that uses apache async http client to execute streaming requests.
 *
 * @author mai.jh
 */
public class DefaultAsyncHttpClientRequest implements AsyncHttpClientRequest {
    
    private final CloseableHttpAsyncClient asyncClient;
    
    public DefaultAsyncHttpClientRequest(CloseableHttpAsyncClient asyncClient) {
        this.asyncClient = asyncClient;
        if (!this.asyncClient.isRunning()) {
            this.asyncClient.start();
        }
    }
    
    @Override
    public <T> void execute(URI uri, String httpMethod, RequestHttpEntity requestHttpEntity,
            final ResponseHandler<T> responseHandler, final Callback<T> callback) throws Exception {
        HttpRequestBase httpRequestBase = DefaultHttpClientRequest.build(uri, httpMethod, requestHttpEntity);
        asyncClient.execute(httpRequestBase, new FutureCallback<HttpResponse>() {
            @Override
            public void completed(HttpResponse result) {
                DefaultClientHttpResponse response = new DefaultClientHttpResponse(result);
                try {
                    HttpRestResult<T> httpRestResult = responseHandler.handle(response);
                    callback.onReceive(httpRestResult);
                } catch (Exception e) {
                    callback.onError(e);
                } finally {
                    HttpClientUtils.closeQuietly(result);
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
    
    @Override
    public void close() throws IOException {
        this.asyncClient.close();
    }
}
