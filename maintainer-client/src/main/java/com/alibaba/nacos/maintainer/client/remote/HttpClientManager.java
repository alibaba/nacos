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

package com.alibaba.nacos.maintainer.client.remote;

import com.alibaba.nacos.maintainer.client.exception.NacosException;
import com.alibaba.nacos.maintainer.client.lifecycle.Closeable;
import com.alibaba.nacos.maintainer.client.remote.client.NacosAsyncRestTemplate;
import com.alibaba.nacos.maintainer.client.remote.client.NacosRestTemplate;
import com.alibaba.nacos.maintainer.client.utils.ExceptionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 *  Http client manager.
 *
 * @author Nacos
 */
public class HttpClientManager implements Closeable {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpClientManager.class);
    
    private static final Map<String, NacosRestTemplate> SINGLETON_REST = new HashMap<>(10);
    
    private static final Map<String, NacosAsyncRestTemplate> SINGLETON_ASYNC_REST = new HashMap<>(10);
    
    private static final HttpClientManager INSTANCE = new HttpClientManager();
    
    private HttpClientManager() {}
    
    public static HttpClientManager getInstance() {
        return HttpClientManager.INSTANCE;
    }
    
    private static final HttpClientFactory HTTP_CLIENT_FACTORY = new DefaultHttpClientFactory();
    
    /**
     * get NacosRestTemplate Instance.
     *
     * @return NacosRestTemplate
     */
    public NacosRestTemplate getNacosRestTemplate() {
        if (HTTP_CLIENT_FACTORY == null) {
            throw new NullPointerException("httpClientFactory is null");
        }
        String factoryName = HTTP_CLIENT_FACTORY.getClass().getName();
        NacosRestTemplate nacosRestTemplate = SINGLETON_REST.get(factoryName);
        if (nacosRestTemplate == null) {
            synchronized (SINGLETON_REST) {
                nacosRestTemplate = SINGLETON_REST.get(factoryName);
                if (nacosRestTemplate != null) {
                    return nacosRestTemplate;
                }
                nacosRestTemplate = HTTP_CLIENT_FACTORY.createNacosRestTemplate();
                SINGLETON_REST.put(factoryName, nacosRestTemplate);
            }
        }
        
        return nacosRestTemplate;
    }
    
    @Override
    public void shutdown() throws NacosException {
        LOGGER.info("[ConfigHttpClientManager] Start destroying NacosRestTemplate");
        try {
            shutdown(HTTP_CLIENT_FACTORY.getClass().getName());
        } catch (Exception ex) {
            LOGGER.error("[ConfigHttpClientManager] An exception occurred when the HTTP client was closed : {}",
                    ExceptionUtil.getStackTrace(ex));
        }
        LOGGER.info("[ConfigHttpClientManager] Completed destruction of NacosRestTemplate");
    }
    
    public static void shutdown(String className) throws Exception {
        shutdownNacosSyncRest(className);
        shutdownNacosAsyncRest(className);
    }
    
    /**
     * Shutdown sync http client holder and remove template.
     *
     * @param className HttpClientFactory implement class name
     * @throws Exception ex
     */
    public static void shutdownNacosSyncRest(String className) throws Exception {
        final NacosRestTemplate nacosRestTemplate = SINGLETON_REST.get(className);
        if (nacosRestTemplate != null) {
            nacosRestTemplate.close();
            SINGLETON_REST.remove(className);
        }
    }
    
    /**
     * Shutdown async http client holder and remove template.
     *
     * @param className HttpClientFactory implement class name
     * @throws Exception ex
     */
    public static void shutdownNacosAsyncRest(String className) throws Exception {
        final NacosAsyncRestTemplate nacosAsyncRestTemplate = SINGLETON_ASYNC_REST.get(className);
        if (nacosAsyncRestTemplate != null) {
            nacosAsyncRestTemplate.close();
            SINGLETON_ASYNC_REST.remove(className);
        }
    }
}
