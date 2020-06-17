package com.alibaba.nacos.client.naming.net;

import com.alibaba.nacos.common.http.*;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;

/**
 * http Manager
 *
 * @author mai.jh
 * @date 2020/6/14
 */
public class NamingHttpClientManager {

    private static final int READ_TIME_OUT_MILLIS = Integer
        .getInteger("com.alibaba.nacos.client.naming.rtimeout", 50000);
    private static final int CON_TIME_OUT_MILLIS = Integer
        .getInteger("com.alibaba.nacos.client.naming.ctimeout", 3000);
    private static final boolean ENABLE_HTTPS = Boolean
        .getBoolean("com.alibaba.nacos.client.naming.tls.enable");
    private static final int MAX_REDIRECTS = 5;

    private static final HttpClientFactory HTTP_CLIENT_FACTORY = new NamingHttpClientFactory();

    public static String getPrefix() {
        if (ENABLE_HTTPS) {
            return "https://";
        }
        return "http://";
    }

    public static NacosRestTemplate getNacosRestTemplate() {
        return HttpClientBeanFactory.getNacosRestTemplate(HTTP_CLIENT_FACTORY);
    }

    private static class NamingHttpClientFactory extends AbstractHttpClientFactoryWrapper {

        @Override
        protected HttpClientConfig buildHttpClientConfig() {
            return HttpClientConfig.builder()
                .setConTimeOutMillis(CON_TIME_OUT_MILLIS)
                .setReadTimeOutMillis(READ_TIME_OUT_MILLIS)
                .setMaxRedirects(MAX_REDIRECTS).build();
        }
    }
}
