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

package com.alibaba.nacos.common.http;

/**
 * http client config build.
 *
 * @author mai.jh
 */
public class HttpClientConfig {
    
    private final int conTimeOutMillis;
    
    private final int readTimeOutMillis;
    
    private final int maxRedirects;
    
    public HttpClientConfig(int conTimeOutMillis, int readTimeOutMillis, int maxRedirects) {
        this.conTimeOutMillis = conTimeOutMillis;
        this.readTimeOutMillis = readTimeOutMillis;
        this.maxRedirects = maxRedirects;
    }
    
    public int getConTimeOutMillis() {
        return conTimeOutMillis;
    }
    
    public int getReadTimeOutMillis() {
        return readTimeOutMillis;
    }
    
    public int getMaxRedirects() {
        return maxRedirects;
    }
    
    public static HttpClientConfigBuilder builder() {
        return new HttpClientConfigBuilder();
    }
    
    public static final class HttpClientConfigBuilder {
        
        private int conTimeOutMillis = -1;
        
        private int readTimeOutMillis = -1;
        
        private int maxRedirects = 50;
        
        public HttpClientConfigBuilder setConTimeOutMillis(int conTimeOutMillis) {
            this.conTimeOutMillis = conTimeOutMillis;
            return this;
        }
        
        public HttpClientConfigBuilder setReadTimeOutMillis(int readTimeOutMillis) {
            this.readTimeOutMillis = readTimeOutMillis;
            return this;
        }
        
        public HttpClientConfigBuilder setMaxRedirects(int maxRedirects) {
            this.maxRedirects = maxRedirects;
            return this;
        }
        
        public HttpClientConfig build() {
            return new HttpClientConfig(conTimeOutMillis, readTimeOutMillis, maxRedirects);
        }
    }
}
