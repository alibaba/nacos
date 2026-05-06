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

package com.alibaba.nacos.client.remote;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.http.AbstractHttpClientFactory;
import com.alibaba.nacos.common.http.HttpClientBeanHolder;
import com.alibaba.nacos.common.http.HttpClientConfig;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.alibaba.nacos.common.lifecycle.Closeable;
import com.alibaba.nacos.common.utils.ExceptionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * http Manager.
 *
 * @author Nacos
 */
public class HttpClientManager implements Closeable {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpClientManager.class);
    
    private static final HttpClientFactory HTTP_CLIENT_FACTORY = new HttpClientFactory();
    
    private static final int CON_TIME_OUT_MILLIS = 1000;
    
    private static final int READ_TIME_OUT_MILLIS = 3000;
    
    private static class HttpClientManagerInstance {
        
        private static final HttpClientManager INSTANCE = new HttpClientManager();
    }
    
    public static HttpClientManager getInstance() {
        return HttpClientManagerInstance.INSTANCE;
    }
    
    @Override
    public void shutdown() throws NacosException {
        LOGGER.info("[HttpClientManager] Start destroying NacosRestTemplate");
        try {
            HttpClientBeanHolder.shutdownNacosSyncRest(HTTP_CLIENT_FACTORY.getClass().getName());
        } catch (Exception ex) {
            LOGGER.error("[HttpClientManager] An exception occurred when the HTTP client was closed : {}",
                    ExceptionUtil.getStackTrace(ex));
        }
        LOGGER.info("[HttpClientManager] Completed destruction of NacosRestTemplate");
    }
    
    /**
     * get connectTimeout.
     *
     * @param connectTimeout connectTimeout
     * @return int return max timeout
     */
    public int getConnectTimeoutOrDefault(int connectTimeout) {
        return Math.max(CON_TIME_OUT_MILLIS, connectTimeout);
    }
    
    /**
     * get NacosRestTemplate Instance.
     *
     * @return NacosRestTemplate
     */
    public NacosRestTemplate getNacosRestTemplate() {
        return HttpClientBeanHolder.getNacosRestTemplate(HTTP_CLIENT_FACTORY);
    }
    
    /**
     * HttpClientFactory.
     */
    private static class HttpClientFactory extends AbstractHttpClientFactory {
        
        @Override
        protected HttpClientConfig buildHttpClientConfig() {
            return HttpClientConfig.builder().setConTimeOutMillis(CON_TIME_OUT_MILLIS)
                    .setReadTimeOutMillis(READ_TIME_OUT_MILLIS).build();
        }
        
        @Override
        protected Logger assignLogger() {
            return LOGGER;
        }
    }
}
