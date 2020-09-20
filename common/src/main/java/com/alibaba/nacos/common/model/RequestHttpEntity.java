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

package com.alibaba.nacos.common.model;

import com.alibaba.nacos.common.http.HttpClientConfig;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;

import java.util.Map;

/**
 * Represents an HTTP request , consisting of headers and body.
 *
 * @author mai.jh
 */
public class RequestHttpEntity {
    
    private final Header headers = Header.newInstance();
    
    private final HttpClientConfig httpClientConfig;
    
    private final Query query;
    
    private final Object body;
    
    public RequestHttpEntity(Header header, Query query) {
        this(null, header, query);
    }
    
    public RequestHttpEntity(Header header, Object body) {
        this(null, header, null, body);
    }
    
    public RequestHttpEntity(Header header, Query query, Object body) {
        this(null, header, query, body);
    }
    
    public RequestHttpEntity(HttpClientConfig httpClientConfig, Header header, Query query) {
        this(httpClientConfig, header, query, null);
    }
    
    public RequestHttpEntity(HttpClientConfig httpClientConfig, Header header, Object body) {
        this(httpClientConfig, header, null, body);
    }
    
    public RequestHttpEntity(HttpClientConfig httpClientConfig, Header header, Query query, Object body) {
        handleHeader(header);
        this.httpClientConfig = httpClientConfig;
        this.query = query;
        this.body = body;
    }
    
    private void handleHeader(Header header) {
        if (header != null && !header.getHeader().isEmpty()) {
            Map<String, String> headerMap = header.getHeader();
            headers.addAll(headerMap);
        }
    }
    
    public Header getHeaders() {
        return headers;
    }
    
    public Query getQuery() {
        return query;
    }
    
    public Object getBody() {
        return body;
    }
    
    public HttpClientConfig getHttpClientConfig() {
        return httpClientConfig;
    }
    
    public boolean isEmptyBody() {
        return body == null;
    }
    
}
