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

package com.alibaba.nacos.common.http.client.response;

import com.alibaba.nacos.common.http.param.Header;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;

/**
 * Represents a client-side HTTP response.
 *
 * @author mai.jh
 */
public interface HttpClientResponse extends Closeable {
    
    /**
     * Return the headers of this message.
     *
     * @return a corresponding HttpHeaders object (never {@code null})
     */
    Header getHeaders();
    
    /**
     * Return the body of the message as an input stream.
     *
     * @return String response body
     * @throws IOException IOException
     */
    InputStream getBody() throws IOException;
    
    /**
     * Return the HTTP status code.
     *
     * @return the HTTP status as an integer
     * @throws IOException IOException
     */
    int getStatusCode() throws IOException;
    
    /**
     * Return the HTTP status text of the response.
     *
     * @return the HTTP status text
     * @throws IOException IOException
     */
    String getStatusText() throws IOException;
    
    /**
     * close response InputStream.
     */
    @Override
    void close();
}
