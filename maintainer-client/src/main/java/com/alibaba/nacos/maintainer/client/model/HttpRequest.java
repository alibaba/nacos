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

package com.alibaba.nacos.maintainer.client.model;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * HttpRequest.
 *
 * @author Nacos
 */
public class HttpRequest {
    
    private String httpMethod;
    
    private String path;
    
    private Map<String, String> headers;
    
    private Map<String, String> paramValues;
    
    private File file;
    
    public HttpRequest(String httpMethod, String path, Map<String, String> headers, Map<String, String> paramValues, File file) {
        this.httpMethod = httpMethod;
        this.path = path;
        this.headers = headers;
        this.paramValues = paramValues;
        this.file = file;
    }
    
    public String getHttpMethod() {
        return httpMethod;
    }
    
    public void setHttpMethod(String httpMethod) {
        this.httpMethod = httpMethod;
    }
    
    public String getPath() {
        return path;
    }
    
    public void setPath(String path) {
        this.path = path;
    }
    
    public Map<String, String> getHeaders() {
        return headers;
    }
    
    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }
    
    public Map<String, String> getParamValues() {
        return paramValues;
    }
    
    public void setParamValues(Map<String, String> paramValues) {
        this.paramValues = paramValues;
    }
    
    public File getFile() {
        return file;
    }
    
    public void setFile(File file) {
        this.file = file;
    }
    
    public static class Builder {
        
        private String httpMethod;
        
        private String path;
        
        private final Map<String, String> headers = new HashMap<>();
        
        private final Map<String, String> paramValues = new HashMap<>();
        
        private File file;
        
        private long readTimeoutMs;
        
        public Builder setHttpMethod(String httpMethod) {
            this.httpMethod = httpMethod;
            return this;
        }
        
        public Builder setPath(String path) {
            this.path = path;
            return this;
        }
        
        public Builder addHeader(Map<String, String> header) {
            headers.putAll(header);
            return this;
        }
        
        public Builder setParamValue(Map<String, String> params) {
            paramValues.putAll(params);
            return this;
        }
        
        public Builder setFile(File file) {
            this.file = file;
            return this;
        }
        
        public HttpRequest build() {
            return new HttpRequest(httpMethod, path, headers, paramValues, file);
        }
    }
}