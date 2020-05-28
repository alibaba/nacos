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

import com.alibaba.nacos.common.constant.HttpHeaderConsts;
import com.alibaba.nacos.common.http.handler.ResponseHandler;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.MediaType;
import com.alibaba.nacos.common.utils.IoUtils;

import java.lang.reflect.Type;

/**
 * HTTP Message Converter
 * to convert the response into a type {@code T}.
 *
 * @author mai.jh
 * @date 2020/5/27
 */
@SuppressWarnings({"unchecked", "rawtypes", "resource"})
public class HttpMessageConverterExtractor<T> implements ResponseExtractor<T>{

    private Type responseType;

    public HttpMessageConverterExtractor(Type responseType) {
        this.responseType = responseType;
    }

    @Override
    public T extractData(HttpClientResponse clientHttpResponse) throws Exception {
        Header headers = clientHttpResponse.getHeaders();
        String value = headers.getValue(HttpHeaderConsts.CONTENT_TYPE);
        String body = IoUtils.toString(clientHttpResponse.getBody(), headers.getCharset());
        if (MediaType.APPLICATION_JSON.equals(value)) {
            return ResponseHandler.convert(body, responseType);
        }
        return (T) body;
    }


}
