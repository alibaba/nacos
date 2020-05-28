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

import com.alibaba.nacos.common.http.BaseHttpMethod;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.model.RequestHttpEntity;
import org.apache.http.HttpEntity;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.CloseableHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;

/**
 * {@link HttpClientRequest} implementation that uses apache http client to
 * execute streaming requests
 *
 * @author mai.jh
 * @date 2020/5/24
 */
public class ApacheHttpClientRequest implements HttpClientRequest {

    private static final Logger logger = LoggerFactory.getLogger(NacosRestTemplate.class);


    private CloseableHttpClient client;

    public ApacheHttpClientRequest(CloseableHttpClient client) {
        this.client = client;
    }

    @Override
    public HttpClientResponse execute(URI uri, String httpMethod, RequestHttpEntity requestHttpEntity) throws Exception {
        HttpRequestBase request = build(uri, httpMethod, requestHttpEntity);
        CloseableHttpResponse response = client.execute(request);
        if (logger.isDebugEnabled()) {
            logger.debug("Request from server: " + request.getURI().toString());
        }
        return new ApacheClientHttpResponse(response);
    }


    private HttpRequestBase build(URI uri, String method, RequestHttpEntity requestHttpEntity) throws Exception {
        Header headers = requestHttpEntity.getHeaders();
        BaseHttpMethod httpMethod = BaseHttpMethod.sourceOf(method);
        httpMethod.init(uri.toString());
        httpMethod.initHeader(headers);
        httpMethod.initEntity(requestHttpEntity.getBody(), headers.getValue("Content-Type"));
        return httpMethod.getRequestBase();
    }
}
