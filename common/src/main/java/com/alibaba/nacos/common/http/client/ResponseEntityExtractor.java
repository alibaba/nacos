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

import com.alibaba.nacos.common.model.RestResult;

import java.lang.reflect.Type;

/**
 * Response extractor for
 *
 * @author mai.jh
 * @date 2020/5/27
 */
@SuppressWarnings({"unchecked", "rawtypes", "resource"})
public class ResponseEntityExtractor<T> implements ResponseExtractor<T> {

    private Type responseType;

    private final HttpMessageConverterExtractor<T> delegate;

    public ResponseEntityExtractor(Type responseType) {
        this.responseType = responseType;
        this.delegate = new HttpMessageConverterExtractor(responseType);
    }

    @Override
    public T extractData(HttpClientResponse response) throws Exception {
        T body = this.delegate.extractData(response);
        if (body instanceof RestResult) {
            return body;
        }
        return (T) new RestResult<>(response.getHeaders(), response.getStatusCode(), body);
    }
}
