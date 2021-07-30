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

import com.alibaba.nacos.common.utils.ThreadUtils;

import java.util.concurrent.TimeUnit;

/**
 * http client config build.
 *
 * @author mai.jh
 */
public class HttpClientConfig {
    
    /**
     * connect time out.
     */
    private final int conTimeOutMillis;
    
    /**
     * read time out.
     */
    private final int readTimeOutMillis;
    
    /**
     * connTimeToLive.
     */
    private final long connTimeToLive;
    
    /**
     * connTimeToLiveTimeUnit.
     */
    private final TimeUnit connTimeToLiveTimeUnit;
    
    /**
     * connectionRequestTimeout.
     */
    private final int connectionRequestTimeout;
    
    /**
     * max redirect.
     */
    private final int maxRedirects;
    
    /**
     * max connect total.
     */
    private final int maxConnTotal;
    
    /**
     * Assigns maximum connection per route value.
     */
    private final int maxConnPerRoute;
    
    /**
     * is HTTP compression enabled.
     */
    private final boolean contentCompressionEnabled;
    
    /**
     * io thread count.
     */
    private final int ioThreadCount;
    
    /**
     * user agent.
     */
    private final String userAgent;
    
    public HttpClientConfig(int conTimeOutMillis, int readTimeOutMillis, long connTimeToLive, TimeUnit timeUnit,
            int connectionRequestTimeout, int maxRedirects, int maxConnTotal, int maxConnPerRoute,
            boolean contentCompressionEnabled, int ioThreadCount, String userAgent) {
        this.conTimeOutMillis = conTimeOutMillis;
        this.readTimeOutMillis = readTimeOutMillis;
        this.connTimeToLive = connTimeToLive;
        this.connTimeToLiveTimeUnit = timeUnit;
        this.connectionRequestTimeout = connectionRequestTimeout;
        this.maxRedirects = maxRedirects;
        this.maxConnTotal = maxConnTotal;
        this.maxConnPerRoute = maxConnPerRoute;
        this.contentCompressionEnabled = contentCompressionEnabled;
        this.ioThreadCount = ioThreadCount;
        this.userAgent = userAgent;
    }
    
    public int getConTimeOutMillis() {
        return conTimeOutMillis;
    }
    
    public int getReadTimeOutMillis() {
        return readTimeOutMillis;
    }
    
    public long getConnTimeToLive() {
        return connTimeToLive;
    }
    
    public TimeUnit getConnTimeToLiveTimeUnit() {
        return connTimeToLiveTimeUnit;
    }
    
    public int getConnectionRequestTimeout() {
        return connectionRequestTimeout;
    }
    
    public int getMaxRedirects() {
        return maxRedirects;
    }
    
    public int getMaxConnTotal() {
        return maxConnTotal;
    }
    
    public int getMaxConnPerRoute() {
        return maxConnPerRoute;
    }
    
    public boolean getContentCompressionEnabled() {
        return contentCompressionEnabled;
    }
    
    public int getIoThreadCount() {
        return ioThreadCount;
    }
    
    public String getUserAgent() {
        return userAgent;
    }
    
    public static HttpClientConfigBuilder builder() {
        return new HttpClientConfigBuilder();
    }
    
    public static final class HttpClientConfigBuilder {
        
        private int conTimeOutMillis = -1;
        
        private int readTimeOutMillis = -1;
        
        private long connTimeToLive = -1;
        
        private TimeUnit connTimeToLiveTimeUnit = TimeUnit.MILLISECONDS;
        
        private int connectionRequestTimeout = -1;
        
        private int maxRedirects = 50;
        
        private int maxConnTotal = 0;
        
        private int maxConnPerRoute = 0;
        
        private boolean contentCompressionEnabled = true;
        
        private int ioThreadCount = ThreadUtils.getSuitableThreadCount(1);
        
        private String userAgent;
        
        public HttpClientConfigBuilder setConTimeOutMillis(int conTimeOutMillis) {
            this.conTimeOutMillis = conTimeOutMillis;
            return this;
        }
        
        public HttpClientConfigBuilder setReadTimeOutMillis(int readTimeOutMillis) {
            this.readTimeOutMillis = readTimeOutMillis;
            return this;
        }
        
        public HttpClientConfigBuilder setConnectionTimeToLive(long connTimeToLive, TimeUnit connTimeToLiveTimeUnit) {
            this.connTimeToLive = connTimeToLive;
            this.connTimeToLiveTimeUnit = connTimeToLiveTimeUnit;
            return this;
        }
        
        public HttpClientConfigBuilder setConnectionRequestTimeout(int connectionRequestTimeout) {
            this.connectionRequestTimeout = connectionRequestTimeout;
            return this;
        }
        
        public HttpClientConfigBuilder setMaxRedirects(int maxRedirects) {
            this.maxRedirects = maxRedirects;
            return this;
        }
        
        public HttpClientConfigBuilder setMaxConnTotal(int maxConnTotal) {
            this.maxConnTotal = maxConnTotal;
            return this;
        }
        
        public HttpClientConfigBuilder setMaxConnPerRoute(int maxConnPerRoute) {
            this.maxConnPerRoute = maxConnPerRoute;
            return this;
        }
        
        public HttpClientConfigBuilder setContentCompressionEnabled(boolean contentCompressionEnabled) {
            this.contentCompressionEnabled = contentCompressionEnabled;
            return this;
        }
        
        public HttpClientConfigBuilder setIoThreadCount(int ioThreadCount) {
            this.ioThreadCount = ioThreadCount;
            return this;
        }
        
        public HttpClientConfigBuilder setUserAgent(String userAgent) {
            this.userAgent = userAgent;
            return this;
        }
    
        /**
         * build http client config.
         *
         * @return HttpClientConfig
         */
        public HttpClientConfig build() {
            return new HttpClientConfig(conTimeOutMillis, readTimeOutMillis, connTimeToLive, connTimeToLiveTimeUnit,
                    connectionRequestTimeout, maxRedirects, maxConnTotal, maxConnPerRoute, contentCompressionEnabled,
                    ioThreadCount, userAgent);
        }
    }
}
