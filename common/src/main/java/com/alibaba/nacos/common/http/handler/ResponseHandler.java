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

package com.alibaba.nacos.common.http.handler;

import com.alibaba.nacos.api.exception.runtime.NacosDeserializationException;
import com.alibaba.nacos.common.constant.HttpHeaderConsts;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.http.client.HttpClientResponse;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.MediaType;
import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.common.utils.IoUtils;
import com.alibaba.nacos.common.utils.JacksonUtils;
import org.apache.http.HttpStatus;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.lang.reflect.Type;

import org.slf4j.Logger;

/**
 * Response handler.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public final class ResponseHandler {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ResponseHandler.class);
    
    public static <T> T convert(String s, Class<T> cls) throws Exception {
        return JacksonUtils.toObj(s, cls);
    }
    
    public static <T> T convert(String s, Type type) throws Exception {
        return JacksonUtils.toObj(s, type);
    }
    
    public static <T> T convert(InputStream inputStream, Type type) throws Exception {
        return JacksonUtils.toObj(inputStream, type);
    }
    
    private static <T> HttpRestResult<T> convert(RestResult<T> restResult) {
        HttpRestResult<T> httpRestResult = new HttpRestResult<T>();
        httpRestResult.setCode(restResult.getCode());
        httpRestResult.setData(restResult.getData());
        httpRestResult.setMessage(restResult.getMessage());
        return httpRestResult;
    }
    
    @SuppressWarnings({"unchecked", "rawtypes", "resource"})
    public static <T> HttpRestResult<T> responseEntityExtractor(HttpClientResponse response, Type type)
            throws Exception {
        Header headers = response.getHeaders();
        String contentType = headers.getValue(HttpHeaderConsts.CONTENT_TYPE);
        InputStream body = response.getBody();
        T extractBody = null;
        if (contentType != null && contentType.startsWith(MediaType.APPLICATION_JSON) && HttpStatus.SC_OK == response
                .getStatusCode()) {
            extractBody = convert(body, type);
        }
        if (extractBody == null) {
            if (!String.class.toString().equals(type.toString())) {
                LOGGER.error(
                        "if the response contentType is not [application/json]," + " only support to java.lang.String");
                throw new NacosDeserializationException(type);
            }
            extractBody = (T) IoUtils.toString(body, headers.getCharset());
        }
        if (extractBody instanceof RestResult) {
            HttpRestResult<T> httpRestResult = convert((RestResult<T>) extractBody);
            httpRestResult.setHeader(headers);
            return httpRestResult;
        }
        return new HttpRestResult<T>(response.getHeaders(), response.getStatusCode(), extractBody);
    }
}
