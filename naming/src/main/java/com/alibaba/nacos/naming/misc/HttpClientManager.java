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

package com.alibaba.nacos.naming.misc;

import com.alibaba.nacos.common.http.AbstractApacheHttpClientFactory;
import com.alibaba.nacos.common.http.AbstractHttpClientFactory;
import com.alibaba.nacos.common.http.HttpClientBeanHolder;
import com.alibaba.nacos.common.http.HttpClientConfig;
import com.alibaba.nacos.common.http.HttpClientFactory;
import com.alibaba.nacos.common.http.client.NacosAsyncRestTemplate;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.alibaba.nacos.common.utils.ExceptionUtil;
import com.alibaba.nacos.common.utils.ThreadUtils;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.slf4j.Logger;

import java.util.concurrent.TimeUnit;

import static com.alibaba.nacos.naming.misc.Loggers.SRV_LOG;

/**
 * http Manager.
 *
 * @author mai.jh
 */
public class HttpClientManager {
    
    private static final int TIME_OUT_MILLIS = 10000;
    
    private static final int CON_TIME_OUT_MILLIS = 5000;
    
    private static final HttpClientFactory SYNC_HTTP_CLIENT_FACTORY = new SyncHttpClientFactory();
    
    private static final HttpClientFactory ASYNC_HTTP_CLIENT_FACTORY = new AsyncHttpClientFactory();
    
    private static final HttpClientFactory PROCESSOR_ASYNC_HTTP_CLIENT_FACTORY = new ProcessorHttpClientFactory();
    
    private static final HttpClientFactory APACHE_SYNC_HTTP_CLIENT_FACTORY = new ApacheSyncHttpClientFactory();
    
    private static final NacosRestTemplate NACOS_REST_TEMPLATE;
    
    private static final NacosRestTemplate APACHE_NACOS_REST_TEMPLATE;
    
    private static final NacosAsyncRestTemplate NACOS_ASYNC_REST_TEMPLATE;
    
    private static final NacosAsyncRestTemplate PROCESSOR_NACOS_ASYNC_REST_TEMPLATE;
    
    static {
        // build nacos rest template
        NACOS_REST_TEMPLATE = HttpClientBeanHolder.getNacosRestTemplate(SYNC_HTTP_CLIENT_FACTORY);
        APACHE_NACOS_REST_TEMPLATE = HttpClientBeanHolder.getNacosRestTemplate(APACHE_SYNC_HTTP_CLIENT_FACTORY);
        NACOS_ASYNC_REST_TEMPLATE = HttpClientBeanHolder.getNacosAsyncRestTemplate(ASYNC_HTTP_CLIENT_FACTORY);
        PROCESSOR_NACOS_ASYNC_REST_TEMPLATE = HttpClientBeanHolder
                .getNacosAsyncRestTemplate(PROCESSOR_ASYNC_HTTP_CLIENT_FACTORY);
        
        ThreadUtils.addShutdownHook(HttpClientManager::shutdown);
    }
    
    public static NacosRestTemplate getNacosRestTemplate() {
        return NACOS_REST_TEMPLATE;
    }
    
    /**
     * Use apache http client to achieve.
     *
     * @return NacosRestTemplate
     */
    public static NacosRestTemplate getApacheRestTemplate() {
        return APACHE_NACOS_REST_TEMPLATE;
    }
    
    public static NacosAsyncRestTemplate getAsyncRestTemplate() {
        return NACOS_ASYNC_REST_TEMPLATE;
    }
    
    /**
     * To be compatible with the old version of http client request, this NacosAsyncRestTemplate is only used for
     * HttpHealthCheckProcessor.
     *
     * @return NacosAsyncRestTemplate
     */
    public static NacosAsyncRestTemplate getProcessorNacosAsyncRestTemplate() {
        return PROCESSOR_NACOS_ASYNC_REST_TEMPLATE;
    }
    
    private static void shutdown() {
        SRV_LOG.warn("[NamingServerHttpClientManager] Start destroying HTTP-Client");
        try {
            HttpClientBeanHolder.shutdownNacostSyncRest(SYNC_HTTP_CLIENT_FACTORY.getClass().getName());
            HttpClientBeanHolder.shutdownNacostSyncRest(APACHE_SYNC_HTTP_CLIENT_FACTORY.getClass().getName());
            HttpClientBeanHolder.shutdownNacosAsyncRest(ASYNC_HTTP_CLIENT_FACTORY.getClass().getName());
            HttpClientBeanHolder.shutdownNacosAsyncRest(PROCESSOR_ASYNC_HTTP_CLIENT_FACTORY.getClass().getName());
        } catch (Exception ex) {
            SRV_LOG.error("[NamingServerHttpClientManager] An exception occurred when the HTTP client was closed : {}",
                    ExceptionUtil.getStackTrace(ex));
        }
        SRV_LOG.warn("[NamingServerHttpClientManager] Destruction of the end");
    }
    
    private static class AsyncHttpClientFactory extends AbstractHttpClientFactory {
        
        @Override
        protected HttpClientConfig buildHttpClientConfig() {
            return HttpClientConfig.builder().setConTimeOutMillis(CON_TIME_OUT_MILLIS)
                    .setReadTimeOutMillis(TIME_OUT_MILLIS).setUserAgent(UtilsAndCommons.SERVER_VERSION)
                    .setMaxConnTotal(-1).setMaxConnPerRoute(128).setMaxRedirects(0).build();
        }
        
        @Override
        protected Logger assignLogger() {
            return SRV_LOG;
        }
    }
    
    private static class SyncHttpClientFactory extends AbstractHttpClientFactory {
        
        @Override
        protected HttpClientConfig buildHttpClientConfig() {
            return HttpClientConfig.builder().setConTimeOutMillis(CON_TIME_OUT_MILLIS)
                    .setReadTimeOutMillis(TIME_OUT_MILLIS).setMaxRedirects(0).build();
        }
        
        @Override
        protected Logger assignLogger() {
            return SRV_LOG;
        }
    }
    
    private static class ApacheSyncHttpClientFactory extends AbstractApacheHttpClientFactory {
        
        @Override
        protected HttpClientConfig buildHttpClientConfig() {
            return HttpClientConfig.builder().setConnectionTimeToLive(500, TimeUnit.MILLISECONDS)
                    .setMaxConnTotal(EnvUtil.getAvailableProcessors(2))
                    .setMaxConnPerRoute(EnvUtil.getAvailableProcessors()).setMaxRedirects(0).build();
        }
        
        @Override
        protected Logger assignLogger() {
            return SRV_LOG;
        }
    }
    
    private static class ProcessorHttpClientFactory extends AbstractHttpClientFactory {
        
        @Override
        protected HttpClientConfig buildHttpClientConfig() {
            return HttpClientConfig.builder().setConnectionRequestTimeout(500).setReadTimeOutMillis(500)
                    .setConTimeOutMillis(500).setIoThreadCount(1).setContentCompressionEnabled(false).setMaxRedirects(0)
                    .setMaxConnTotal(-1).setMaxConnPerRoute(-1).setUserAgent("VIPServer").build();
        }
        
        @Override
        protected Logger assignLogger() {
            return SRV_LOG;
        }
    }
}
