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

import com.alibaba.nacos.common.http.client.request.HttpClientRequest;
import com.alibaba.nacos.common.http.client.response.HttpClientResponse;
import com.alibaba.nacos.common.model.RequestHttpEntity;

import java.io.IOException;
import java.net.URI;
import java.util.Iterator;

/**
 * Wrap http client request and perform corresponding interception.
 *
 * @author mai.jh
 */
public class InterceptingHttpClientRequest implements HttpClientRequest {
    
    private final HttpClientRequest httpClientRequest;
    
    private final Iterator<HttpClientRequestInterceptor> interceptors;
    
    public InterceptingHttpClientRequest(HttpClientRequest httpClientRequest,
            Iterator<HttpClientRequestInterceptor> interceptors) {
        this.httpClientRequest = httpClientRequest;
        this.interceptors = interceptors;
    }
    
    @Override
    public HttpClientResponse execute(URI uri, String httpMethod, RequestHttpEntity requestHttpEntity)
            throws Exception {
        while (interceptors.hasNext()) {
            HttpClientRequestInterceptor nextInterceptor = interceptors.next();
            if (nextInterceptor.isIntercept(uri, httpMethod, requestHttpEntity)) {
                return nextInterceptor.intercept();
            }
        }
        return httpClientRequest.execute(uri, httpMethod, requestHttpEntity);
    }
    
    @Override
    public void close() throws IOException {
        httpClientRequest.close();
    }
}
