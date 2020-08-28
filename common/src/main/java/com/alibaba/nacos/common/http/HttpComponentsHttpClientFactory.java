package com.alibaba.nacos.common.http;

import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.alibaba.nacos.common.http.client.request.DefaultHttpClientRequest;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.HttpClients;

/**
 * apache http client factory implements.
 *
 * @author mai.jh
 */
public abstract class HttpComponentsHttpClientFactory extends AbstractHttpClientFactory {
    
    @Override
    public final NacosRestTemplate createNacosRestTemplate() {
        final RequestConfig requestConfig = getRequestConfig();
        return new NacosRestTemplate(assignLogger(),
                new DefaultHttpClientRequest(HttpClients.custom().setDefaultRequestConfig(requestConfig).build()));
    }
    
}
