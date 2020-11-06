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

package com.alibaba.nacos.common.http;

import com.alibaba.nacos.common.http.client.NacosAsyncRestTemplate;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.alibaba.nacos.common.utils.ExceptionUtil;
import com.alibaba.nacos.common.utils.ThreadUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Create a rest template to ensure that each custom client config and rest template are in one-to-one correspondence.
 *
 * @author mai.jh
 */
public final class HttpClientBeanHolder {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpClientBeanHolder.class);
    
    private static final Map<String, NacosRestTemplate> SINGLETON_REST = new HashMap<String, NacosRestTemplate>(10);
    
    private static final Map<String, NacosAsyncRestTemplate> SINGLETON_ASYNC_REST = new HashMap<String, NacosAsyncRestTemplate>(
            10);
    
    private static final AtomicBoolean ALREADY_SHUTDOWN = new AtomicBoolean(false);
    
    static {
        ThreadUtils.addShutdownHook(new Runnable() {
            @Override
            public void run() {
                shutdown();
            }
        });
    }
    
    public static NacosRestTemplate getNacosRestTemplate(Logger logger) {
        return getNacosRestTemplate(new DefaultHttpClientFactory(logger));
    }
    
    public static NacosRestTemplate getNacosRestTemplate(HttpClientFactory httpClientFactory) {
        if (httpClientFactory == null) {
            throw new NullPointerException("httpClientFactory is null");
        }
        String factoryName = httpClientFactory.getClass().getName();
        NacosRestTemplate nacosRestTemplate = SINGLETON_REST.get(factoryName);
        if (nacosRestTemplate == null) {
            synchronized (SINGLETON_REST) {
                nacosRestTemplate = SINGLETON_REST.get(factoryName);
                if (nacosRestTemplate != null) {
                    return nacosRestTemplate;
                }
                nacosRestTemplate = httpClientFactory.createNacosRestTemplate();
                SINGLETON_REST.put(factoryName, nacosRestTemplate);
            }
        }
        return nacosRestTemplate;
    }
    
    public static NacosAsyncRestTemplate getNacosAsyncRestTemplate(Logger logger) {
        return getNacosAsyncRestTemplate(new DefaultHttpClientFactory(logger));
    }
    
    public static NacosAsyncRestTemplate getNacosAsyncRestTemplate(HttpClientFactory httpClientFactory) {
        if (httpClientFactory == null) {
            throw new NullPointerException("httpClientFactory is null");
        }
        String factoryName = httpClientFactory.getClass().getName();
        NacosAsyncRestTemplate nacosAsyncRestTemplate = SINGLETON_ASYNC_REST.get(factoryName);
        if (nacosAsyncRestTemplate == null) {
            synchronized (SINGLETON_ASYNC_REST) {
                nacosAsyncRestTemplate = SINGLETON_ASYNC_REST.get(factoryName);
                if (nacosAsyncRestTemplate != null) {
                    return nacosAsyncRestTemplate;
                }
                nacosAsyncRestTemplate = httpClientFactory.createNacosAsyncRestTemplate();
                SINGLETON_ASYNC_REST.put(factoryName, nacosAsyncRestTemplate);
            }
        }
        return nacosAsyncRestTemplate;
    }
    
    /**
     * Shutdown common http client.
     */
    private static void shutdown() {
        if (!ALREADY_SHUTDOWN.compareAndSet(false, true)) {
            return;
        }
        LOGGER.warn("[HttpClientBeanHolder] Start destroying common HttpClient");
        try {
            shutdown(DefaultHttpClientFactory.class.getName());
        } catch (Exception ex) {
            LOGGER.error("An exception occurred when the common HTTP client was closed : {}", ExceptionUtil.getStackTrace(ex));
        }
        LOGGER.warn("[HttpClientBeanHolder] Destruction of the end");
    }
    
    /**
     * Shutdown http client holder and close remove template.
     *
     * @param className HttpClientFactory implement class name
     * @throws Exception ex
     */
    public static void shutdown(String className) throws Exception {
        shutdownNacostSyncRest(className);
        shutdownNacosAsyncRest(className);
    }
    
    /**
     * Shutdown sync http client holder and remove template.
     *
     * @param className HttpClientFactory implement class name
     * @throws Exception ex
     */
    public static void shutdownNacostSyncRest(String className) throws Exception {
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
