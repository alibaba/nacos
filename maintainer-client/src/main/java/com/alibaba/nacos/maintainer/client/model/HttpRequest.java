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

import com.alibaba.nacos.plugin.auth.api.RequestResource;

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
    
    private String body;
    
    private RequestResource resource;
    
    private byte[] fileBytes;
    
    private String fileName;
    
    private String fileFieldName;
    
    public HttpRequest(String httpMethod, String path, Map<String, String> headers,
        Map<String, String> paramValues,
        String body, RequestResource resource) {
        this(httpMethod, path, headers, paramValues, body, resource, null, null, null);
    }
    
    public HttpRequest(String httpMethod, String path, Map<String, String> headers,
        Map<String, String> paramValues,
        String body, RequestResource resource, byte[] fileBytes, String fileName,
        String fileFieldName) {
        this.httpMethod = httpMethod;
        this.path = path;
        this.headers = headers;
        this.paramValues = paramValues;
        this.body = body;
        this.resource = resource;
        this.fileBytes = fileBytes;
        this.fileName = fileName;
        this.fileFieldName = fileFieldName;
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
    
    public String getBody() {
        return body;
    }
    
    public void setBody(String body) {
        this.body = body;
    }
    
    public RequestResource getResource() {
        return resource;
    }
    
    public void setResource(RequestResource resource) {
        this.resource = resource;
    }
    
    public byte[] getFileBytes() {
        return fileBytes;
    }
    
    public String getFileName() {
        return fileName;
    }
    
    public String getFileFieldName() {
        return fileFieldName;
    }
    
    public boolean isFileUpload() {
        return fileBytes != null && fileBytes.length > 0;
    }
    
    public static class Builder {
        
        private String httpMethod;
        
        private String path;
        
        private final Map<String, String> headers = new HashMap<>();
        
        private final Map<String, String> paramValues = new HashMap<>();
        
        private String body;
        
        private RequestResource resource;
        
        private byte[] fileBytes;
        
        private String fileName;
        
        private String fileFieldName;
        
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
        
        public Builder setBody(String body) {
            this.body = body;
            return this;
        }
        
        public Builder setResource(RequestResource resource) {
            this.resource = resource;
            return this;
        }
        
        /**
         * Set file upload data for multipart/form-data requests.
         *
         * @param fileBytes     raw bytes of the file
         * @param fileName      file name sent in Content-Disposition
         * @param fileFieldName form field name (e.g. "file")
         * @return this builder
         */
        public Builder setFileUpload(byte[] fileBytes, String fileName, String fileFieldName) {
            this.fileBytes = fileBytes;
            this.fileName = fileName;
            this.fileFieldName = fileFieldName;
            return this;
        }
        
        /** Build the HTTP request. */
        public HttpRequest build() {
            return new HttpRequest(httpMethod, path, headers, paramValues, body, resource,
                fileBytes, fileName,
                fileFieldName);
        }
    }
}
