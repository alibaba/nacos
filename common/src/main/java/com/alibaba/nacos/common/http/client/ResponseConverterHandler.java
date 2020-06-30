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

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.constant.HttpHeaderConsts;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.utils.IoUtils;
import org.apache.http.HttpStatus;

import java.lang.reflect.Type;
import java.util.List;

/**
 * response converter handler
 * uses the given {@linkplain ResponseConverter entity converters},
 * to convert the response into a type {@link HttpRestResult}.
 *
 * @author mai.jh
 */
public class ResponseConverterHandler<T> implements ResponseHandler<T> {
    
    private Type responseType;
    
    private List<ResponseConverter<?>> responseConverters;
    
    public ResponseConverterHandler(Type responseType, List<ResponseConverter<?>> responseConverters) {
        this.responseType = responseType;
        this.responseConverters = responseConverters;
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public HttpRestResult<T> handle(HttpClientResponse response) throws Exception {
        final Header headers = response.getHeaders();
        String contentType = headers.getValue(HttpHeaderConsts.CONTENT_TYPE);
        if (HttpStatus.SC_OK != response.getStatusCode()) {
            return handleError(response);
        }
        for (ResponseConverter<?> responseConverter : responseConverters) {
            if (responseConverter.canConverter(responseType, contentType)) {
                return (HttpRestResult<T>) responseConverter.converter(response, responseType);
            }
        }
        throw new NacosException(NacosException.HTTP_CLIENT_ERROR_CODE, "Could not handle response: no suitable ResponseConverter found " +
                "for response type [" + this.responseType + "] and content type [" + contentType + "]");
    }
    
    private HttpRestResult<T> handleError(HttpClientResponse response) throws Exception{
        Header headers = response.getHeaders();
        String message = IoUtils.toString(response.getBody(), headers.getCharset());
        return new HttpRestResult<T>(headers, response.getStatusCode(), null, message);
    }
    
}
