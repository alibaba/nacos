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

import java.lang.reflect.Type;

/**
 * Response Handler abstract interface,
 * the actual processing of the response conversion is done by a concrete implementation class.
 *
 * @author mai.jh
 */
public interface ResponseHandler<T> {
    
    /**
     * set response type.
     *
     * @param responseType responseType
     */
    void setResponseType(Type responseType);
    
    /**
     * handle response convert to HttpRestResult.
     *
     * @param response http response
     * @return HttpRestResult {@link HttpRestResult}
     * @throws Exception ex
     */
    HttpRestResult<T> handle(HttpClientResponse response) throws Exception;
    
}
