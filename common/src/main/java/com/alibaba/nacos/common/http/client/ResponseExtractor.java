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

/**
 *
 * Generic callback interface used by {@link NacosRestTemplate}'s retrieval methods
 * Implementations of this interface perform the actual work of extracting data
 *
 * @author mai.jh
 * @date 2020/5/27
 */
public interface ResponseExtractor<T> {

    /**
     * Extract data from the given {@code ClientHttpResponse} and return it.
     * @param clientHttpResponse http response
     * @return the extracted data
     * @throws Exception ex
     */
    T extractData(HttpClientResponse clientHttpResponse) throws Exception;
}
