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

package com.alibaba.nacos.common.http.client.request;

import com.alibaba.nacos.common.http.client.response.HttpClientResponse;
import com.alibaba.nacos.common.model.RequestHttpEntity;

import java.io.Closeable;
import java.net.URI;

/**
 * Represents a client-side HTTP request. Created via an implementation execute.
 *
 * @author mai.jh
 */
public interface HttpClientRequest extends Closeable {
    
    /**
     * execute http request.
     *
     * @param uri               http url
     * @param httpMethod        http request method
     * @param requestHttpEntity http request entity
     * @return HttpClientResponse
     * @throws Exception ex
     */
    HttpClientResponse execute(URI uri, String httpMethod, RequestHttpEntity requestHttpEntity) throws Exception;
}
