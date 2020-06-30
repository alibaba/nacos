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

import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.MediaType;
import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.common.utils.JacksonUtils;

import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * RestResult response converter
 * Mainly converter response type as bean type, Contain responseType as {@link RestResult}.
 *
 * @author mai.jh
 */
public class BeanResponseConverter<T> implements ResponseConverter<T> {
    
    private static final Set<Class<?>> NON_BEAN_CLASSES = Collections
            .unmodifiableSet(new HashSet<Class<?>>(Arrays.asList(Object.class, Class.class)));
    
    @Override
    public boolean canConverter(Type responseType, String contentType) {
        return contentType != null
                && contentType.startsWith(MediaType.APPLICATION_JSON)
                && isBindableBean(JacksonUtils.constructJavaType(responseType).getRawClass());
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public HttpRestResult<T> converter(HttpClientResponse response, Type responseType) throws Exception {
        final Header headers = response.getHeaders();
        InputStream body = response.getBody();
        T extractBody = com.alibaba.nacos.common.http.handler.ResponseHandler.convert(body, responseType);
        if (extractBody instanceof RestResult) {
            HttpRestResult<T> httpRestResult = convert((RestResult<T>) extractBody);
            httpRestResult.setHeader(headers);
            return httpRestResult;
        }
        return new HttpRestResult<T>(headers, response.getStatusCode(), extractBody, null);
    }
    
    private static boolean isBindableBean(Class<?> resolved) {
        if (!resolved.isPrimitive() && !NON_BEAN_CLASSES.contains(resolved)) {
            return true;
        }
        return !resolved.getName().startsWith("java.");
    }
    
    private static <T> HttpRestResult<T> convert(RestResult<T> restResult) {
        HttpRestResult<T> httpRestResult = new HttpRestResult<T>();
        httpRestResult.setCode(restResult.getCode());
        httpRestResult.setData(restResult.getData());
        httpRestResult.setMessage(restResult.getMessage());
        return httpRestResult;
    }
}
