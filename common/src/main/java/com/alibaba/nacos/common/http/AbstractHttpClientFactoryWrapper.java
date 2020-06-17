package com.alibaba.nacos.common.http;

import com.alibaba.nacos.common.http.client.DefaultAsyncHttpClientRequest;
import com.alibaba.nacos.common.http.client.DefaultHttpClientRequest;
import com.alibaba.nacos.common.http.client.NacosAsyncRestTemplate;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.nio.client.HttpAsyncClients;

/**
 * AbstractHttpClientFactory
 * Let the creator only specify the http client config
 *
 * @author mai.jh
 * @date 2020/6/15
 */
public abstract class AbstractHttpClientFactoryWrapper implements HttpClientFactory {

    @Override
    public final NacosRestTemplate createNacosRestTemplate() {
        RequestConfig requestConfig = getRequestConfig();
        return new NacosRestTemplate(
            new DefaultHttpClientRequest(
                HttpClients.custom()
                    .setDefaultRequestConfig(requestConfig).build()));
    }

    @Override
    public final NacosAsyncRestTemplate createNacosAsyncRestTemplate() {
        RequestConfig requestConfig = getRequestConfig();
        return new NacosAsyncRestTemplate(
            new DefaultAsyncHttpClientRequest(
                HttpAsyncClients.custom()
                    .setDefaultRequestConfig(requestConfig).build()));
    }

    private RequestConfig getRequestConfig() {
        HttpClientConfig httpClientConfig = buildHttpClientConfig();
        return RequestConfig.custom()
            .setConnectTimeout(httpClientConfig.getConTimeOutMillis())
            .setSocketTimeout(httpClientConfig.getReadTimeOutMillis())
            .setMaxRedirects(httpClientConfig.getMaxRedirects())
            .build();
    }

    /**
     * build http client config
     * @return HttpClientConfig
     */
    protected abstract HttpClientConfig buildHttpClientConfig();
}
