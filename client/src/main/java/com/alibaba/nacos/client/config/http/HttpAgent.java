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
package com.alibaba.nacos.client.config.http;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.config.impl.HttpSimpleClient.HttpResult;

import java.io.IOException;
import java.util.List;


/**
 * HttpAgent
 *
 * @author Nacos
 */
public interface HttpAgent {
    /**
     * start to get nacos ip list
     * @return Nothing.
     * @throws NacosException on get ip list error.
     */
    void start() throws NacosException;

    /**
     * invoke http get method
     * @param path http path
     * @param headers http headers
     * @param paramValues http paramValues http
     * @param encoding http encode
     * @param readTimeoutMs http timeout
     * @return HttpResult http response
     * @throws IOException If an input or output exception occurred
     */

    HttpResult httpGet(String path, List<String> headers, List<String> paramValues, String encoding, long readTimeoutMs) throws IOException;

    /**
     * invoke http post method
     * @param path http path
     * @param headers http headers
     * @param paramValues http paramValues http
     * @param encoding http encode
     * @param readTimeoutMs http timeout
     * @return HttpResult http response
     * @throws IOException If an input or output exception occurred
     */
    HttpResult httpPost(String path, List<String> headers, List<String> paramValues, String encoding, long readTimeoutMs) throws IOException;

    /**
     * invoke http delete method
     * @param path http path
     * @param headers http headers
     * @param paramValues http paramValues http
     * @param encoding http encode
     * @param readTimeoutMs http timeout
     * @return HttpResult http response
     * @throws IOException If an input or output exception occurred
     */
    HttpResult httpDelete(String path, List<String> headers, List<String> paramValues, String encoding, long readTimeoutMs) throws IOException;

    /**
     * get name
     * @return String
     */
    String getName();

    /**
     * get namespace
     * @return String
     */
    String getNamespace();

    /**
     * get tenant
     * @return String
     */
    String getTenant();

    /**
     * get encode
     * @return String
     */
    String getEncode();
}
