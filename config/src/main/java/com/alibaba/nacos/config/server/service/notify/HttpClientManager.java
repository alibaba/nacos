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

package com.alibaba.nacos.config.server.service.notify;

import com.alibaba.nacos.common.http.AbstractHttpClientFactory;
import com.alibaba.nacos.common.http.HttpClientBeanHolder;
import com.alibaba.nacos.common.http.HttpClientConfig;
import com.alibaba.nacos.common.http.client.NacosAsyncRestTemplate;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.alibaba.nacos.common.utils.ExceptionUtil;
import com.alibaba.nacos.common.utils.ThreadUtils;
import com.alibaba.nacos.config.server.utils.PropertyUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * http client manager.
 *
 * @author mai.jh
 */
public final class HttpClientManager {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpClientManager.class);
    
    /**
     * Connection timeout and socket timeout with other servers.
     */
    private static final int TIMEOUT = 500;
    
    private static final NacosRestTemplate NACOS_REST_TEMPLATE;
    
    private static final NacosAsyncRestTemplate NACOS_ASYNC_REST_TEMPLATE;
    
    static {
        // build nacos rest template
        NACOS_REST_TEMPLATE = HttpClientBeanHolder.getNacosRestTemplate(new ConfigHttpClientFactory(TIMEOUT, TIMEOUT));
        NACOS_ASYNC_REST_TEMPLATE = HttpClientBeanHolder.getNacosAsyncRestTemplate(
                new ConfigHttpClientFactory(PropertyUtil.getNotifyConnectTimeout(),
                        PropertyUtil.getNotifySocketTimeout()));
        
        ThreadUtils.addShutdownHook(new Runnable() {
            @Override
            public void run() {
                shutdown();
            }
        });
    }
    
    public static NacosRestTemplate getNacosRestTemplate() {
        return NACOS_REST_TEMPLATE;
    }
    
    public static NacosAsyncRestTemplate getNacosAsyncRestTemplate() {
        return NACOS_ASYNC_REST_TEMPLATE;
    }
    
    private static void shutdown() {
        LOGGER.warn("[ConfigServer-HttpClientManager] Start destroying NacosRestTemplate");
        try {
            final String httpClientFactoryBeanName = ConfigHttpClientFactory.class.getName();
            HttpClientBeanHolder.shutdownNacostSyncRest(httpClientFactoryBeanName);
            HttpClientBeanHolder.shutdownNacosAsyncRest(httpClientFactoryBeanName);
        } catch (Exception ex) {
            LOGGER.error("[ConfigServer-HttpClientManager] An exception occurred when the HTTP client was closed : {}",
                    ExceptionUtil.getStackTrace(ex));
        }
        LOGGER.warn("[ConfigServer-HttpClientManager] Destruction of the end");
    }
    
    /**
     * http client factory.
     */
    private static class ConfigHttpClientFactory extends AbstractHttpClientFactory {
        
        private final int conTimeOutMillis;
        
        private final int readTimeOutMillis;
        
        public ConfigHttpClientFactory(int conTimeOutMillis, int readTimeOutMillis) {
            this.conTimeOutMillis = conTimeOutMillis;
            this.readTimeOutMillis = readTimeOutMillis;
        }
        
        @Override
        protected HttpClientConfig buildHttpClientConfig() {
            return HttpClientConfig.builder().setConTimeOutMillis(conTimeOutMillis)
                    .setReadTimeOutMillis(readTimeOutMillis).build();
        }
        
        @Override
        protected Logger assignLogger() {
            return LOGGER;
        }
    }
}
