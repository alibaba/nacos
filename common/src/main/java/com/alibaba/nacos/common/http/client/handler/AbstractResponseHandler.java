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

package com.alibaba.nacos.common.http.client.handler;

import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.http.client.response.HttpClientResponse;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.utils.IoUtils;
import org.apache.http.HttpStatus;

import java.lang.reflect.Type;

/**
 * Abstract response handler.
 *
 * @author mai.jh
 */
public abstract class AbstractResponseHandler<T> implements ResponseHandler<T> {
    
    private Type responseType;
    
    @Override
    public final void setResponseType(Type responseType) {
        this.responseType = responseType;
    }
    
    @Override
    public final HttpRestResult<T> handle(HttpClientResponse response) throws Exception {
        if (HttpStatus.SC_OK != response.getStatusCode()) {
            return handleError(response);
        }
        return convertResult(response, this.responseType);
    }
    
    private HttpRestResult<T> handleError(HttpClientResponse response) throws Exception {
        Header headers = response.getHeaders();
        String message = IoUtils.toString(response.getBody(), headers.getCharset());
        return new HttpRestResult<T>(headers, response.getStatusCode(), null, message);
    }
    
    /**
     * Abstract convertResult method, Different types of converters for expansion.
     *
     * @param response     http client response
     * @param responseType responseType
     * @return HttpRestResult
     * @throws Exception ex
     */
    public abstract HttpRestResult<T> convertResult(HttpClientResponse response, Type responseType) throws Exception;
    
}
