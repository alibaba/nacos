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

package com.alibaba.nacos.client.naming.remote.http;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.http.AbstractHttpClientFactory;
import com.alibaba.nacos.common.http.HttpClientBeanHolder;
import com.alibaba.nacos.common.http.HttpClientConfig;
import com.alibaba.nacos.common.http.HttpClientFactory;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.alibaba.nacos.common.lifecycle.Closeable;
import com.alibaba.nacos.common.tls.TlsSystemConfig;
import com.alibaba.nacos.common.utils.ExceptionUtil;
import org.slf4j.Logger;

import static com.alibaba.nacos.client.utils.LogUtils.NAMING_LOGGER;

/**
 * http Manager.
 *
 * @author mai.jh
 */
public class NamingHttpClientManager implements Closeable {
    
    private static final int READ_TIME_OUT_MILLIS = Integer
            .getInteger("com.alibaba.nacos.client.naming.rtimeout", 50000);
    
    private static final int CON_TIME_OUT_MILLIS = Integer.getInteger("com.alibaba.nacos.client.naming.ctimeout", 3000);
    
    private static final boolean ENABLE_HTTPS = Boolean.getBoolean(TlsSystemConfig.TLS_ENABLE);
    
    private static final int MAX_REDIRECTS = 5;
    
    private static final HttpClientFactory HTTP_CLIENT_FACTORY = new NamingHttpClientFactory();
    
    private static class NamingHttpClientManagerInstance {
        
        private static final NamingHttpClientManager INSTANCE = new NamingHttpClientManager();
    }
    
    public static NamingHttpClientManager getInstance() {
        return NamingHttpClientManagerInstance.INSTANCE;
    }
    
    public String getPrefix() {
        if (ENABLE_HTTPS) {
            return "https://";
        }
        return "http://";
    }
    
    public NacosRestTemplate getNacosRestTemplate() {
        return HttpClientBeanHolder.getNacosRestTemplate(HTTP_CLIENT_FACTORY);
    }
    
    @Override
    public void shutdown() throws NacosException {
        NAMING_LOGGER.warn("[NamingHttpClientManager] Start destroying NacosRestTemplate");
        try {
            HttpClientBeanHolder.shutdownNacostSyncRest(HTTP_CLIENT_FACTORY.getClass().getName());
        } catch (Exception ex) {
            NAMING_LOGGER.error("[NamingHttpClientManager] An exception occurred when the HTTP client was closed : {}",
                    ExceptionUtil.getStackTrace(ex));
        }
        NAMING_LOGGER.warn("[NamingHttpClientManager] Destruction of the end");
    }
    
    private static class NamingHttpClientFactory extends AbstractHttpClientFactory {
        
        @Override
        protected HttpClientConfig buildHttpClientConfig() {
            return HttpClientConfig.builder().setConTimeOutMillis(CON_TIME_OUT_MILLIS)
                    .setReadTimeOutMillis(READ_TIME_OUT_MILLIS).setMaxRedirects(MAX_REDIRECTS).build();
        }
        
        @Override
        protected Logger assignLogger() {
            return NAMING_LOGGER;
        }
    }
}
