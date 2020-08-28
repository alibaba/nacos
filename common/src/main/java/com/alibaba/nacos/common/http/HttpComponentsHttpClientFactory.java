package com.alibaba.nacos.common.http;

import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.alibaba.nacos.common.http.client.request.DefaultHttpClientRequest;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;

/**
 * apache http client implements.
 *
 * @author mai.jh
 */
public abstract class HttpComponentsHttpClientFactory implements HttpClientFactory {
    
    @Override
    public NacosRestTemplate createNacosRestTemplate() {
        final HttpClientConfig httpClientConfig = buildHttpClientConfig();
        
        final RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(httpClientConfig.getConTimeOutMillis())
                .setSocketTimeout(httpClientConfig.getReadTimeOutMillis())
                .setMaxRedirects(httpClientConfig.getMaxRedirects()).build();
        
        return new NacosRestTemplate(assignLogger(),
                new DefaultHttpClientRequest(HttpClients.custom().setDefaultRequestConfig(requestConfig).build()));
    }
    
    /**
     * build http client config.
     *
     * @return HttpClientConfig
     */
    protected abstract HttpClientConfig buildHttpClientConfig();
    
    /**
     * assign Logger.
     *
     * @return Logger
     */
    protected abstract Logger assignLogger();
}
