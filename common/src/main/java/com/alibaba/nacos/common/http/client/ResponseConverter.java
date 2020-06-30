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

import java.lang.reflect.Type;

/**
 * response converter
 * Default implementation reference
 * @see BeanResponseConverter
 * @see StringResponseConverter
 *
 * @author mai.jh
 */
public interface ResponseConverter<T> {
    
    /**
     * Indicates whether this converter can convert the given class
     *
     * @param responseType response type
     * @param contentType content type
     * @return boolean
     */
    boolean canConverter(Type responseType, String contentType);
    
    /**
     * {@link #canConverter canConverter} method returned {@code true},
     * Convert HttpClientResponse to a bean of type responseType
     *
     * @param response http response
     * @param responseType converter to response type
     * @return HttpRestResult {@link HttpRestResult}
     * @throws Exception ex
     */
    HttpRestResult<T> converter(HttpClientResponse response, Type responseType) throws Exception;
    
}
