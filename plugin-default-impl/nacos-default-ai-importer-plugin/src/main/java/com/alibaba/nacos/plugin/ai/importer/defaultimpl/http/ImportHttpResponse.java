/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.ai.importer.defaultimpl.http;

import java.net.http.HttpHeaders;

/**
 * HTTP response fetched by default AI importers.
 *
 * @author xiweng.yy
 * @since 3.2.1
 */
public class ImportHttpResponse {
    
    private final String url;
    
    private final int statusCode;
    
    private final HttpHeaders headers;
    
    private final byte[] body;
    
    public ImportHttpResponse(String url, int statusCode, HttpHeaders headers, byte[] body) {
        this.url = url;
        this.statusCode = statusCode;
        this.headers = headers;
        this.body = body == null ? new byte[0] : body;
    }
    
    public boolean isSuccess() {
        return statusCode >= 200 && statusCode < 300;
    }
    
    public String getUrl() {
        return url;
    }
    
    public int getStatusCode() {
        return statusCode;
    }
    
    public byte[] getBody() {
        return body;
    }
    
    public String getContentType() {
        return headers.firstValue("Content-Type").orElse("");
    }
}
