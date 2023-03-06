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

import com.alibaba.nacos.common.executor.NameThreadFactory;
import com.alibaba.nacos.common.http.client.NacosAsyncRestTemplate;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.alibaba.nacos.common.http.client.request.DefaultAsyncHttpClientRequest;
import com.alibaba.nacos.common.http.client.request.JdkHttpClientRequest;
import com.alibaba.nacos.common.tls.SelfHostnameVerifier;
import com.alibaba.nacos.common.tls.TlsFileWatcher;
import com.alibaba.nacos.common.tls.TlsHelper;
import com.alibaba.nacos.common.tls.TlsSystemConfig;

import java.util.function.BiConsumer;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.ssl.DefaultHostnameVerifier;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.http.impl.nio.conn.PoolingNHttpClientConnectionManager;
import org.apache.http.impl.nio.reactor.DefaultConnectingIOReactor;
import org.apache.http.impl.nio.reactor.IOReactorConfig;
import org.apache.http.nio.conn.NHttpClientConnectionManager;
import org.apache.http.nio.conn.NoopIOSessionStrategy;
import org.apache.http.nio.conn.SchemeIOSessionStrategy;
import org.apache.http.nio.conn.ssl.SSLIOSessionStrategy;
import org.apache.http.nio.reactor.IOReactorException;
import org.apache.http.nio.reactor.IOReactorExceptionHandler;
import org.apache.http.protocol.RequestContent;
import org.apache.http.ssl.SSLContexts;
import org.slf4j.Logger;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

/**
 * AbstractHttpClientFactory Let the creator only specify the http client config.
 *
 * @author mai.jh
 */
public abstract class AbstractHttpClientFactory implements HttpClientFactory {
    
    private static final String ASYNC_THREAD_NAME = "nacos-http-async-client";
    
    private static final String ASYNC_IO_REACTOR_NAME = ASYNC_THREAD_NAME + "#I/O Reactor";
    
    @Override
    public NacosRestTemplate createNacosRestTemplate() {
        HttpClientConfig httpClientConfig = buildHttpClientConfig();
        final JdkHttpClientRequest clientRequest = new JdkHttpClientRequest(httpClientConfig);
        
        // enable ssl
        initTls((sslContext, hostnameVerifier) -> {
            clientRequest.setSSLContext(loadSSLContext());
            clientRequest.replaceSSLHostnameVerifier(hostnameVerifier);
        }, filePath -> clientRequest.setSSLContext(loadSSLContext()));
        
        return new NacosRestTemplate(assignLogger(), clientRequest);
    }
    
    @Override
    public NacosAsyncRestTemplate createNacosAsyncRestTemplate() {
        final HttpClientConfig originalRequestConfig = buildHttpClientConfig();
        final DefaultConnectingIOReactor ioreactor = getIoReactor(ASYNC_IO_REACTOR_NAME);
        final RequestConfig defaultConfig = getRequestConfig();
        final NHttpClientConnectionManager connectionManager = getConnectionManager(originalRequestConfig, ioreactor);
        monitorAndExtension(connectionManager);
        return new NacosAsyncRestTemplate(assignLogger(), new DefaultAsyncHttpClientRequest(
                HttpAsyncClients.custom().addInterceptorLast(new RequestContent(true))
                        .setThreadFactory(new NameThreadFactory(ASYNC_THREAD_NAME))
                        .setDefaultIOReactorConfig(getIoReactorConfig()).setDefaultRequestConfig(defaultConfig)
                        .setMaxConnTotal(originalRequestConfig.getMaxConnTotal())
                        .setMaxConnPerRoute(originalRequestConfig.getMaxConnPerRoute())
                        .setUserAgent(originalRequestConfig.getUserAgent())
                        .setConnectionManager(connectionManager).build(),
                ioreactor, defaultConfig));
    }
    
    private DefaultConnectingIOReactor getIoReactor(String threadName) {
        final DefaultConnectingIOReactor ioreactor;
        try {
            ioreactor = new DefaultConnectingIOReactor(getIoReactorConfig(), new NameThreadFactory(threadName));
        } catch (IOReactorException e) {
            assignLogger().error("[NHttpClientConnectionManager] Create DefaultConnectingIOReactor failed", e);
            throw new IllegalStateException();
        }
        
        // if the handle return true, then the exception thrown by IOReactor will be ignore, and will not finish the IOReactor.
        ioreactor.setExceptionHandler(new IOReactorExceptionHandler() {
            
            @Override
            public boolean handle(IOException ex) {
                assignLogger().warn("[NHttpClientConnectionManager] handle IOException, ignore it.", ex);
                return true;
            }
            
            @Override
            public boolean handle(RuntimeException ex) {
                assignLogger().warn("[NHttpClientConnectionManager] handle RuntimeException, ignore it.", ex);
                return true;
            }
        });
        
        return ioreactor;
    }
    
    /**
     * create the {@link NHttpClientConnectionManager}, the code mainly from {@link HttpAsyncClientBuilder#build()}. we
     * add the {@link IOReactorExceptionHandler} to handle the {@link IOException} and {@link RuntimeException} thrown
     * by the {@link org.apache.http.impl.nio.reactor.BaseIOReactor} when process the event of Network. Using this way
     * to avoid the {@link DefaultConnectingIOReactor} killed by unknown error of network.
     *
     * @param originalRequestConfig request config.
     * @param ioreactor             I/O reactor.
     * @return {@link NHttpClientConnectionManager}.
     */
    private NHttpClientConnectionManager getConnectionManager(HttpClientConfig originalRequestConfig,
            DefaultConnectingIOReactor ioreactor) {
        SSLContext sslcontext = SSLContexts.createDefault();
        HostnameVerifier hostnameVerifier = new DefaultHostnameVerifier();
        SchemeIOSessionStrategy sslStrategy = new SSLIOSessionStrategy(sslcontext, null, null, hostnameVerifier);
        
        Registry<SchemeIOSessionStrategy> registry = RegistryBuilder.<SchemeIOSessionStrategy>create()
                .register("http", NoopIOSessionStrategy.INSTANCE).register("https", sslStrategy).build();
        final PoolingNHttpClientConnectionManager poolingmgr = new PoolingNHttpClientConnectionManager(ioreactor,
                registry);
        
        int maxTotal = originalRequestConfig.getMaxConnTotal();
        if (maxTotal > 0) {
            poolingmgr.setMaxTotal(maxTotal);
        }
        
        int maxPerRoute = originalRequestConfig.getMaxConnPerRoute();
        if (maxPerRoute > 0) {
            poolingmgr.setDefaultMaxPerRoute(maxPerRoute);
        }
        return poolingmgr;
    }
    
    protected IOReactorConfig getIoReactorConfig() {
        HttpClientConfig httpClientConfig = buildHttpClientConfig();
        return IOReactorConfig.custom().setIoThreadCount(httpClientConfig.getIoThreadCount()).build();
    }
    
    protected RequestConfig getRequestConfig() {
        HttpClientConfig httpClientConfig = buildHttpClientConfig();
        return RequestConfig.custom().setConnectTimeout(httpClientConfig.getConTimeOutMillis())
                .setSocketTimeout(httpClientConfig.getReadTimeOutMillis())
                .setConnectionRequestTimeout(httpClientConfig.getConnectionRequestTimeout())
                .setContentCompressionEnabled(httpClientConfig.getContentCompressionEnabled())
                .setMaxRedirects(httpClientConfig.getMaxRedirects()).build();
    }
    
    protected void initTls(BiConsumer<SSLContext, HostnameVerifier> initTlsBiFunc,
            TlsFileWatcher.FileChangeListener tlsChangeListener) {
        if (!TlsSystemConfig.tlsEnable) {
            return;
        }
        
        final HostnameVerifier hv = HttpsURLConnection.getDefaultHostnameVerifier();
        final SelfHostnameVerifier selfHostnameVerifier = new SelfHostnameVerifier(hv);
        
        initTlsBiFunc.accept(loadSSLContext(), selfHostnameVerifier);
        
        if (tlsChangeListener != null) {
            try {
                TlsFileWatcher.getInstance()
                        .addFileChangeListener(tlsChangeListener, TlsSystemConfig.tlsClientTrustCertPath,
                                TlsSystemConfig.tlsClientKeyPath);
            } catch (IOException e) {
                assignLogger().error("add tls file listener fail", e);
            }
        }
    }
    
    @SuppressWarnings("checkstyle:abbreviationaswordinname")
    protected synchronized SSLContext loadSSLContext() {
        if (!TlsSystemConfig.tlsEnable) {
            return null;
        }
        try {
            return TlsHelper.buildSslContext(true);
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            assignLogger().error("Failed to create SSLContext", e);
        }
        return null;
    }
    
    /**
     * build http client config.
     *
     * @return HttpClientConfig
     */
    protected abstract HttpClientConfig buildHttpClientConfig();
    
    /**
     * assign Logger.
     *
     * @return Logger
     */
    protected abstract Logger assignLogger();
    
    /**
     * add some monitor and do some extension. default empty implementation, implemented by subclass
     */
    protected void monitorAndExtension(NHttpClientConnectionManager connectionManager) {
    }
}
