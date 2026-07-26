/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.api.remote;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.response.Response;

/**
 * connection interface,define basic operation.
 *
 * @author liuzunfei
 * @version $Id: Requester.java, v 0.1 2020年09月11日 4:05 PM liuzunfei Exp $
 */
public interface Requester {
    
    /**
     * send request.
     *
     * @param request      request.
     * @param timeoutMills mills of timeouts.
     * @return response  response returned.
     * @throws NacosException exception throw.
     */
    Response request(Request request, long timeoutMills) throws NacosException;
    
    /**
     * send request.
     *
     * @param request request.
     * @return request future.
     * @throws NacosException exception throw.
     */
    RequestFuture requestFuture(Request request) throws NacosException;
    
    /**
     * send async request.
     *
     * @param request         request.
     * @param requestCallBack callback of request.
     * @throws NacosException exception throw.
     */
    void asyncRequest(Request request, RequestCallBack requestCallBack) throws NacosException;
    
    /**
     * close connection.
     */
    void close();
    
}
