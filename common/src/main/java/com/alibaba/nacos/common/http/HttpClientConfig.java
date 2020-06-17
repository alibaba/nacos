package com.alibaba.nacos.common.http;

import org.apache.http.client.config.RequestConfig;

/**
 * http client config build
 *
 * @author mai.jh
 * @date 2020/6/14
 */
public class HttpClientConfig {

    private int conTimeOutMillis;

    private int readTimeOutMillis;

    private int maxRedirects;

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
