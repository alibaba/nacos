/*
 * Copyright 1999-$toady.year Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.service.query;

import com.alibaba.nacos.api.config.remote.request.ConfigQueryRequest;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainRequest;

import javax.servlet.http.HttpServletRequest;

/**
 * Interface for extracting configuration query chain requests from different sources.
 *
 * @author Nacos
 */
public interface ConfigQueryChainRequestExtractor {

    /**
     * Gets the name of the current implementation.
     *
     * @return the name of the current implementation
     */
    String getName();

    /**
     * Extracts a configuration query chain request from an HTTP request.
     *
     * @param request the HTTP request object
     * @return the extracted configuration query chain request
     */
    ConfigQueryChainRequest extract(HttpServletRequest request);

    /**
     * Extracts a configuration query chain request from a configuration query request object.
     *
     * @param request      the configuration query request object
     * @param requestMeta  the request metadata
     * @return the extracted configuration query chain request
     */
    ConfigQueryChainRequest extract(ConfigQueryRequest request, RequestMeta requestMeta);
}
