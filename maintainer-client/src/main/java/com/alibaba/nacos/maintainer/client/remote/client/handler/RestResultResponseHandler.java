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

package com.alibaba.nacos.maintainer.client.remote.client.handler;

import com.alibaba.nacos.maintainer.client.remote.HttpRestResult;
import com.alibaba.nacos.maintainer.client.remote.client.response.HttpClientResponse;
import com.alibaba.nacos.maintainer.client.remote.param.Header;
import com.alibaba.nacos.maintainer.client.result.Result;
import com.alibaba.nacos.maintainer.client.utils.JacksonUtils;

import java.lang.reflect.Type;

/**
 * RestResult response handler, Mainly converter response type as {@link Result} type.
 *
 * @author Nacos
 */
public class RestResultResponseHandler<T> extends AbstractResponseHandler<T> {
    
    @Override
    public HttpRestResult<T> convertResult(HttpClientResponse response, Type responseType) throws Exception {
        final Header headers = response.getHeaders();
        T extractBody = JacksonUtils.toObj(response.getBody(), responseType);
        HttpRestResult<T> httpRestResult = convert((Result<T>) extractBody);
        httpRestResult.setHeader(headers);
        return httpRestResult;
    }
    
    private static <T> HttpRestResult<T> convert(Result<T> restResult) {
        HttpRestResult<T> httpRestResult = new HttpRestResult<>();
        httpRestResult.setCode(restResult.getCode());
        httpRestResult.setData(restResult.getData());
        httpRestResult.setMessage(restResult.getMessage());
        return httpRestResult;
    }
    
}
