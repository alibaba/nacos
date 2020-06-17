package com.alibaba.nacos.common.http;

import com.alibaba.nacos.common.http.client.NacosAsyncRestTemplate;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;

/**
 * http Client Factory
 *
 * @author mai.jh
 * @date 2020/6/15
 */
public interface HttpClientFactory {

    /**
     * create new nacost rest
     * @return NacosRestTemplate
     */
    NacosRestTemplate createNacosRestTemplate();

    /**
     * create new nacos async rest
     * @return NacosAsyncRestTemplate
     */
    NacosAsyncRestTemplate createNacosAsyncRestTemplate();

}
