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

import com.alibaba.nacos.common.model.RequestHttpEntity;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;

/**
 * Represents a client-side HTTP response.
 * Obtained via an calling of the
 * {@link ClientHttpRequest#execute(URI uri, String httpMethod, RequestHttpEntity requestHttpEntity)}.
 *
 *
 * @author mai.jh
 * @date 2020/5/23
 */
public interface ClientHttpResponse extends HttpMessage, Closeable{

    /**
     * Return the HTTP status code
     * @return the HTTP status as an integer
     * @throws IOException in case of I/O errors
     */
    int getStatusCode() throws IOException;

    /**
     * Return the HTTP status text of the response.
     * @return the HTTP status text
     * @throws IOException in case of I/O errors
     */
    String getStatusText() throws IOException;

    /**
     * close response InputStream
     * @throws IOException ex
     */
    @Override
    void close() throws IOException;
}
