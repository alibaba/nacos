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

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Create a rest template
 * to ensure that each custom client config and rest template are in one-to-one correspondence
 *
 * @author mai.jh
 * @date 2020/6/16
 */
@SuppressWarnings("all")
public final class HttpClientBeanHolder {

    private static final Logger logger = LoggerFactory.getLogger(HttpClientManager.class);

    private static final int TIMEOUT = Integer.getInteger("nacos.http.timeout", 5000);

    private static HttpClientConfig HTTP_CLIENT_CONFIG = HttpClientConfig.builder()
        .setConTimeOutMillis(TIMEOUT).setReadTimeOutMillis(TIMEOUT >> 1).build();

    private static final Map<String, NacosRestTemplate> singletonRest = new HashMap<String, NacosRestTemplate>(10);
    private static final Map<String, NacosAsyncRestTemplate> singletonAsyncRest = new HashMap<String, NacosAsyncRestTemplate>(10);

    private static final AtomicBoolean alreadyShutdown = new AtomicBoolean(false);

    static {
        ThreadUtils.addShutdownHook(new Runnable() {
            @Override
            public void run() {
                shutdown();
            }
        });
    }

    public static NacosRestTemplate getNacosRestTemplate() {
        return getNacosRestTemplate(new DefaultHttpClientFactory());
    }

    public static NacosRestTemplate getNacosRestTemplate(HttpClientFactory httpClientFactory) {
        if (httpClientFactory == null) {
            throw new NullPointerException("httpClientFactory is null");
        }
        String factoryName = httpClientFactory.getClass().getName();
        NacosRestTemplate nacosRestTemplate = singletonRest.get(factoryName);
        if (nacosRestTemplate == null) {
            synchronized (singletonRest) {
                nacosRestTemplate = singletonRest.get(factoryName);
                if (nacosRestTemplate != null) {
                    return nacosRestTemplate;
                }
                nacosRestTemplate = httpClientFactory.createNacosRestTemplate();
                singletonRest.put(factoryName, nacosRestTemplate);
            }
        }
        return nacosRestTemplate;
    }

    public static NacosAsyncRestTemplate getNacosAsyncRestTemplate() {
        return getNacosAsyncRestTemplate(new DefaultHttpClientFactory());
    }

    public static NacosAsyncRestTemplate getNacosAsyncRestTemplate(HttpClientFactory httpClientFactory) {
        if (httpClientFactory == null) {
            throw new NullPointerException("httpClientFactory is null");
        }
        String factoryName = httpClientFactory.getClass().getName();
        NacosAsyncRestTemplate nacosAsyncRestTemplate = singletonAsyncRest.get(factoryName);
        if (nacosAsyncRestTemplate == null) {
            synchronized (singletonAsyncRest) {
                nacosAsyncRestTemplate = singletonAsyncRest.get(factoryName);
                if (nacosAsyncRestTemplate != null) {
                    return nacosAsyncRestTemplate;
                }
                nacosAsyncRestTemplate = httpClientFactory.createNacosAsyncRestTemplate();
                singletonAsyncRest.put(factoryName, nacosAsyncRestTemplate);
            }
        }
        return nacosAsyncRestTemplate;
    }

    public static void shutdown() {
        if (!alreadyShutdown.compareAndSet(false, true)) {
            return;
        }
        logger.warn("[HttpClientBeanFactory] Start destroying NacosRestTemplate");
        try {
            nacostRestTemplateShutdown();
            nacosAsyncRestTemplateShutdown();
        }
        catch (Exception ex) {
            logger.error("[HttpClientBeanFactory] An exception occurred when the HTTP client was closed : {}",
                ExceptionUtil.getStackTrace(ex));
        }
        logger.warn("[HttpClientBeanFactory] Destruction of the end");
    }

    private static void nacostRestTemplateShutdown() throws Exception{
        if (!singletonRest.isEmpty()) {
            Collection<NacosRestTemplate> nacosRestTemplates = singletonRest.values();
            for (NacosRestTemplate nacosRestTemplate : nacosRestTemplates) {
                nacosRestTemplate.close();
            }
            singletonRest.clear();
        }
    }

    private static void nacosAsyncRestTemplateShutdown() throws Exception{
        if (!singletonAsyncRest.isEmpty()) {
            Collection<NacosAsyncRestTemplate> nacosAsyncRestTemplates = singletonAsyncRest.values();
            for (NacosAsyncRestTemplate nacosAsyncRestTemplate : nacosAsyncRestTemplates) {
                nacosAsyncRestTemplate.close();
            }
            singletonAsyncRest.clear();
        }
    }
}
