/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.client.remote;

import com.alibaba.nacos.common.constant.HttpHeaderConsts;
import com.alibaba.nacos.common.http.client.HttpClientRequestInterceptor;
import com.alibaba.nacos.common.http.client.response.HttpClientResponse;
import com.alibaba.nacos.common.model.RequestHttpEntity;
import com.alibaba.nacos.common.utils.UuidUtils;

import java.net.URI;

/**
 * Adds a fresh request ID to each outgoing SDK HTTP request.
 *
 * @author Nacos
 */
public class RequestIdHttpClientRequestInterceptor implements HttpClientRequestInterceptor {
    
    @Override
    public boolean isIntercept(URI uri, String httpMethod, RequestHttpEntity requestHttpEntity) {
        requestHttpEntity.getHeaders().addParam(HttpHeaderConsts.NACOS_REQUEST_ID,
            UuidUtils.generateUuid());
        return false;
    }
    
    @Override
    public HttpClientResponse intercept() {
        return null;
    }
}
