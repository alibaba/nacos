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

import com.alibaba.nacos.maintainer.client.executor.NameThreadFactory;
import com.alibaba.nacos.maintainer.client.remote.client.NacosAsyncRestTemplate;
import com.alibaba.nacos.maintainer.client.remote.client.NacosRestTemplate;
import com.alibaba.nacos.maintainer.client.remote.client.request.DefaultAsyncHttpClientRequest;
import com.alibaba.nacos.maintainer.client.remote.client.request.JdkHttpClientRequest;
import com.alibaba.nacos.maintainer.client.tls.SelfHostnameVerifier;
import com.alibaba.nacos.maintainer.client.tls.TlsFileWatcher;
import com.alibaba.nacos.maintainer.client.tls.TlsHelper;
import com.alibaba.nacos.maintainer.client.tls.TlsSystemConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.nio.AsyncClientConnectionManager;
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
import org.apache.hc.client5.http.ssl.DefaultHostnameVerifier;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.http.protocol.RequestContent;
import org.apache.hc.core5.reactor.DefaultConnectingIOReactor;
import org.apache.hc.core5.reactor.IOEventHandler;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/**
 * AbstractHttpClientFactory Let the creator only specify the http client config.
 *
 * @author Nacos
 */
public abstract class AbstractHttpClientFactory implements HttpClientFactory {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractHttpClientFactory.class);
    
    private static final String ASYNC_THREAD_NAME = "nacos-http-async-client";
    
    private static final String ASYNC_IO_REACTOR_NAME = ASYNC_THREAD_NAME + "#I/O Reactor";
    
    @Override
    public NacosRestTemplate createNacosRestTemplate() {
        HttpClientConfig httpClientConfig = buildHttpClientConfig();
        final JdkHttpClientRequest clientRequest = new JdkHttpClientRequest(httpClientConfig);
        
        // enable ssl
        initTls((sslContext, hostnameVerifier) -> {
            clientRequest.setSslContext(loadSslContext());
            clientRequest.replaceSslHostnameVerifier(hostnameVerifier);
        }, filePath -> clientRequest.setSslContext(loadSslContext()));
        
        return new NacosRestTemplate(clientRequest);
    }
    
    @Override
    public NacosAsyncRestTemplate createNacosAsyncRestTemplate() {
        final IOReactorConfig ioReactorConfig = getIoReactorConfig();
        final HttpClientConfig originalRequestConfig = buildHttpClientConfig();
        final DefaultConnectingIOReactor ioreactor = getIoReactor(ASYNC_IO_REACTOR_NAME);
        final RequestConfig defaultConfig = getRequestConfig();
        final AsyncClientConnectionManager connectionManager = getConnectionManager(originalRequestConfig);
        monitorAndExtension(connectionManager);
        
        // issue#12028 upgrade to httpclient5
        return new NacosAsyncRestTemplate(new DefaultAsyncHttpClientRequest(
                HttpAsyncClients.custom()
                        .addRequestInterceptorLast(new RequestContent(true))
                        .setThreadFactory(new NameThreadFactory(ASYNC_THREAD_NAME))
                        .setIOReactorConfig(ioReactorConfig)
                        // catch all exceptions here instead of in DefaultConnectingIOReactor
                        .setIoReactorExceptionCallback((ex) -> {
                        
                        })
                        .setDefaultRequestConfig(defaultConfig)
                        .setUserAgent(originalRequestConfig.getUserAgent())
                        .setConnectionManager(connectionManager)
                        .build(),
                ioreactor, defaultConfig)
        );
    }
    
    private DefaultConnectingIOReactor getIoReactor(String threadName) {
        return new DefaultConnectingIOReactor(
                (session, ojb) -> new IOEventHandler() {
                    @Override
                    public void connected(IOSession ioSession) throws IOException {
                    
                    }
                    
                    @Override
                    public void inputReady(IOSession ioSession, ByteBuffer byteBuffer) throws IOException {
                    
                    }
                    
                    @Override
                    public void outputReady(IOSession ioSession) throws IOException {
                    
                    }
                    
                    @Override
                    public void timeout(IOSession ioSession, Timeout timeout) throws IOException {
                    
                    }
                    
                    @Override
                    public void exception(IOSession ioSession, Exception e) {
                    
                    }
                    
                    @Override
                    public void disconnected(IOSession ioSession) {
                    
                    }
                },
                getIoReactorConfig(),
                new NameThreadFactory(threadName),
                null,
                // handle exception in io reactor
                (ex) -> {
                    if (ex instanceof IOException) {
                        LOGGER.warn("[AsyncClientConnectionManager] handle IOException, ignore it.", ex);
                    } else if (ex instanceof RuntimeException) {
                        LOGGER.warn("[AsyncClientConnectionManager] handle RuntimeException, ignore it.", ex);
                    } else {
                        LOGGER.error("[DefaultConnectingIOReactor] Exception! I/O Reactor error time: {}",
                                System.currentTimeMillis(), ex.getCause());
                    }
                },
                null,
                null
        );
    }
    
    /**
     * create the {@link AsyncClientConnectionManager}, the code mainly from {@link PoolingAsyncClientConnectionManagerBuilder#build()}. we
     * add the {@link Callback} to handle the {@link IOException} and {@link RuntimeException} thrown
     * by the {@link DefaultConnectingIOReactor} when process the event of Network. Using this way
     * to avoid the {@link DefaultConnectingIOReactor} killed by unknown error of network.
     *
     * @param originalRequestConfig request config.
     * @return {@link AsyncClientConnectionManager}.
     */
    private AsyncClientConnectionManager getConnectionManager(HttpClientConfig originalRequestConfig) {
        try {
            SSLContext sslcontext = SSLContext.getDefault();
            HostnameVerifier hostnameVerifier = new DefaultHostnameVerifier();
            TlsStrategy sslStrategy = new DefaultClientTlsStrategy(sslcontext, hostnameVerifier);
            // manager no more needs IOReactor
            return PoolingAsyncClientConnectionManagerBuilder
                    // old method Registry::register("http", NoopIOSessionStrategy.INSTANCE) has been a default strategy
                    .create()
                    // refers to old Registry::register("https", sslStrategy)
                    .setTlsStrategy(sslStrategy)
                    // setMaxTotal now can be used in builder
                    .setMaxConnTotal(originalRequestConfig.getMaxConnTotal())
                    // setDefaultMaxPerRoute now can be used in builder
                    .setMaxConnPerRoute(originalRequestConfig.getMaxConnPerRoute())
                    .build();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
    
    protected IOReactorConfig getIoReactorConfig() {
        HttpClientConfig httpClientConfig = buildHttpClientConfig();
        return IOReactorConfig.custom().setIoThreadCount(httpClientConfig.getIoThreadCount()).build();
    }
    
    protected RequestConfig getRequestConfig() {
        HttpClientConfig httpClientConfig = buildHttpClientConfig();
        return RequestConfig
                .custom()
                .setConnectTimeout(httpClientConfig.getConTimeOutMillis(), TimeUnit.MILLISECONDS)
                .setResponseTimeout(httpClientConfig.getReadTimeOutMillis(), TimeUnit.MILLISECONDS)
                .setConnectionRequestTimeout(httpClientConfig.getConnectionRequestTimeout(), TimeUnit.MILLISECONDS)
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
        
        initTlsBiFunc.accept(loadSslContext(), selfHostnameVerifier);
        
        if (tlsChangeListener != null) {
            try {
                TlsFileWatcher.getInstance()
                        .addFileChangeListener(tlsChangeListener, TlsSystemConfig.tlsClientTrustCertPath,
                                TlsSystemConfig.tlsClientKeyPath);
            } catch (IOException e) {
                LOGGER.error("add tls file listener fail", e);
            }
        }
    }
    
    @SuppressWarnings("checkstyle:abbreviationaswordinname")
    protected synchronized SSLContext loadSslContext() {
        try {
            return TlsHelper.buildSslContext(true);
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            LOGGER.error("Failed to create SSLContext", e);
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
     * add some monitor and do some extension. default empty implementation, implemented by subclass
     */
    protected void monitorAndExtension(AsyncClientConnectionManager connectionManager) {
    }
}
