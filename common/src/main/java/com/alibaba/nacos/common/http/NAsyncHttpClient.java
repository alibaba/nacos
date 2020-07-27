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

package com.alibaba.nacos.common.http;

import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;

import java.lang.reflect.Type;

/**
 * Nacos async http client interface.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@SuppressWarnings("all")
public interface NAsyncHttpClient extends NHttpClient {
    
    /**
     * http get
     *
     * @param url      url
     * @param header   http header param
     * @param query    http query param
     * @param token    return type
     * @param callback {@link Callback#onReceive(com.alibaba.nacos.common.model.RestResult)}
     */
    <T> void get(String url, Header header, Query query, Type token, Callback<T> callback) throws Exception;
    
    /**
     * get request, may be pulling a lot of data
     *
     * @param url      url
     * @param header   http header param
     * @param query    http query param
     * @param body     get with body
     * @param token    return type
     * @param callback {@link Callback#onReceive(com.alibaba.nacos.common.model.RestResult)}
     */
    <T> void getLarge(String url, Header header, Query query, Object body, Type token, Callback<T> callback)
            throws Exception;
    
    /**
     * http delete
     *
     * @param url      url
     * @param header   http header param
     * @param query    http query param
     * @param token    return type
     * @param callback {@link Callback#onReceive(com.alibaba.nacos.common.model.RestResult)}
     */
    <T> void delete(String url, Header header, Query query, Type token, Callback<T> callback) throws Exception;
    
    /**
     * http put
     *
     * @param url      url
     * @param header   http header param
     * @param query    http query param
     * @param body     http body param
     * @param token    return type
     * @param callback {@link Callback#onReceive(com.alibaba.nacos.common.model.RestResult)}
     */
    <T> void put(String url, Header header, Query query, Object body, Type token, Callback<T> callback)
            throws Exception;
    
    /**
     * http post
     *
     * @param url      url
     * @param header   http header param
     * @param query    http query param
     * @param body     http body param
     * @param token    return type
     * @param callback {@link Callback#onReceive(com.alibaba.nacos.common.model.RestResult)}
     */
    <T> void post(String url, Header header, Query query, Object body, Type token, Callback<T> callback)
            throws Exception;
    
}
