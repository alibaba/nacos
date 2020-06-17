package com.alibaba.nacos.common.http;

/**
 * default http client factory
 *
 * @author mai.jh
 * @date 2020/6/15
 */
public class DefaultHttpClientFactory extends AbstractHttpClientFactoryWrapper {

    private static final int TIMEOUT = Integer.getInteger("nacos.http.timeout", 5000);

    @Override
    protected HttpClientConfig buildHttpClientConfig() {
        return HttpClientConfig.builder()
            .setConTimeOutMillis(TIMEOUT)
            .setReadTimeOutMillis(TIMEOUT >> 1)
            .build();
    }
}
