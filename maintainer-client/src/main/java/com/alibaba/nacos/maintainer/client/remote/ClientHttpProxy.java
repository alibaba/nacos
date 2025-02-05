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

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.client.utils.ContextPathUtil;
import com.alibaba.nacos.common.constant.RequestUrlConstants;
import com.alibaba.nacos.common.http.Callback;
import com.alibaba.nacos.common.http.HttpClientConfig;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.http.client.NacosAsyncRestTemplate;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.common.tls.TlsSystemConfig;
import com.alibaba.nacos.common.utils.HttpMethod;
import com.alibaba.nacos.maintainer.client.address.DefaultServerListManager;
import com.alibaba.nacos.maintainer.client.model.HttpRequest;
import com.alibaba.nacos.maintainer.client.utils.ParamUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.util.Map;
import java.util.Properties;

/**
 * Client Http Proxy.
 *
 * @author Nacos
 */
public class ClientHttpProxy {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientHttpProxy.class);
    
    private final NacosRestTemplate nacosRestTemplate = HttpClientManager.getInstance().getNacosRestTemplate();
    
    private final NacosAsyncRestTemplate nacosAsyncRestTemplate = HttpClientManager.getInstance()
            .getNacosAsyncRestTemplate();
    
    private final boolean enableHttps = Boolean.getBoolean(TlsSystemConfig.TLS_ENABLE);
    
    private final int maxRetry = ParamUtil.getMaxRetryTimes();
    
    private final DefaultServerListManager serverListManager;
    
    public ClientHttpProxy(Properties properties) throws NacosException {
        this.serverListManager = new DefaultServerListManager(NacosClientProperties.PROTOTYPE.derive(properties));
        start();
    }
    
    public void start() throws NacosException {
        serverListManager.start();
    }
    
    /**
     * Execute sync http request.
     *
     * @param request http request
     * @return http result
     * @throws Exception exception
     */
    public HttpRestResult<String> executeSyncHttpRequest(HttpRequest request) throws Exception {
        long endTime = System.currentTimeMillis() + ParamUtil.getReadTimeout();
        String currentServerAddr = serverListManager.getCurrentServer();
        int retryCount = maxRetry;
        
        while (System.currentTimeMillis() <= endTime && retryCount >= 0) {
            try {
                HttpRestResult<String> result = executeSync(request, currentServerAddr);
                if (!isFail(result)) {
                    serverListManager.updateCurrentServerAddr(currentServerAddr);
                    return result;
                }
            } catch (Exception ex) {
                LOGGER.error("[NACOS Exception] Server address: {}, Error: {}", currentServerAddr, ex.getMessage());
            }
            
            currentServerAddr = getNextServerAddress();
            retryCount--;
            
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        throw new ConnectException(
                "No available server after " + maxRetry + " retries, last tried server: " + currentServerAddr);
    }
    
    private HttpRestResult<String> executeSync(HttpRequest request, String serverAddr) throws Exception {
        long readTimeoutMs = ParamUtil.getReadTimeout();
        long connectTimeoutMs = ParamUtil.getConnectTimeout();
        Map<String, String> paramValues = request.getParamValues();
        Map<String, String> headers = request.getHeaders();
        File file = request.getFile();
        
        HttpClientConfig httpConfig = HttpClientConfig.builder()
                .setReadTimeOutMillis(Long.valueOf(readTimeoutMs).intValue())
                .setConTimeOutMillis(Long.valueOf(connectTimeoutMs).intValue()).build();
        Header httpHeaders = Header.newInstance();
        if (headers != null) {
            httpHeaders.addAll(headers);
        }
        Query query = Query.newInstance().initParams(paramValues);
        String url = buildUrl(serverAddr, request.getPath());
        
        switch (request.getHttpMethod()) {
            case HttpMethod.GET:
                return nacosRestTemplate.get(url, httpConfig, httpHeaders, query, String.class);
            case HttpMethod.POST:
                if (file != null) {
                    return nacosRestTemplate.postFile(url, httpConfig, httpHeaders, request.getFile(), String.class);
                } else {
                    return nacosRestTemplate.postForm(url, httpConfig, httpHeaders, paramValues, String.class);
                }
            case HttpMethod.PUT:
                return nacosRestTemplate.putForm(url, httpConfig, httpHeaders, paramValues, String.class);
            case HttpMethod.DELETE:
                return nacosRestTemplate.delete(url, httpConfig, httpHeaders, query, String.class);
            default:
                throw new RuntimeException("Unsupported HTTP method: " + request.getHttpMethod());
        }
    }
    
    /**
     * Execute async http request.
     *
     * @param request http request
     * @throws Exception exception
     */
    public void executeAsyncHttpRequest(HttpRequest request, Callback<String> callback) throws Exception {
        long endTime = System.currentTimeMillis() + ParamUtil.getReadTimeout();
        String currentServerAddr = serverListManager.getCurrentServer();
        executeAsyncWithRetry(request, callback, endTime, currentServerAddr, maxRetry);
    }
    
    private void executeAsyncWithRetry(HttpRequest request, Callback<String> callback, long endTime,
            String currentServerAddr, int retryCount) {
        if (System.currentTimeMillis() > endTime || retryCount < 0) {
            callback.onError(new ConnectException(
                    "No available server after " + maxRetry + " retries, last tried server: " + currentServerAddr));
            return;
        }
        
        try {
            executeAsync(request, currentServerAddr, new Callback<String>() {
                @Override
                public void onReceive(RestResult<String> result) {
                    if (!isFail(result)) {
                        serverListManager.updateCurrentServerAddr(currentServerAddr);
                        callback.onReceive(result);
                    } else {
                        retryAsync(request, callback, endTime, retryCount);
                    }
                }
                
                @Override
                public void onError(Throwable throwable) {
                    retryAsync(request, callback, endTime, retryCount);
                }
                
                @Override
                public void onCancel() {
                    callback.onCancel();
                }
            });
        } catch (Exception ex) {
            LOGGER.error("[NACOS Exception] Server address: {}, Error: {}", currentServerAddr, ex.getMessage());
            retryAsync(request, callback, endTime, retryCount);
        }
    }
    
    private void retryAsync(HttpRequest request, Callback<String> callback, long endTime, int retryCount) {
        String nextServerAddr = getNextServerAddress();
        int remainingRetries = retryCount - 1;
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        executeAsyncWithRetry(request, callback, endTime, nextServerAddr, remainingRetries);
    }
    
    private void executeAsync(HttpRequest request, String serverAddr, Callback<String> callback) throws Exception {
        Map<String, String> paramValues = request.getParamValues();
        Map<String, String> headers = request.getHeaders();
        Header httpHeaders = Header.newInstance();
        if (headers != null) {
            httpHeaders.addAll(headers);
        }
        Query query = Query.newInstance().initParams(paramValues);
        String url = buildUrl(serverAddr, request.getPath());
        
        switch (request.getHttpMethod()) {
            case HttpMethod.GET:
                nacosAsyncRestTemplate.get(url, httpHeaders, query, String.class, callback);
                break;
            case HttpMethod.POST:
                nacosAsyncRestTemplate.postForm(url, httpHeaders, paramValues, String.class, callback);
                break;
            case HttpMethod.PUT:
                nacosAsyncRestTemplate.putForm(url, httpHeaders, paramValues, String.class, callback);
                break;
            case HttpMethod.DELETE:
                nacosAsyncRestTemplate.delete(url, httpHeaders, query, String.class, callback);
                break;
            default:
                throw new RuntimeException("Unsupported HTTP method: " + request.getHttpMethod());
        }
    }
    
    private String getNextServerAddress() {
        if (serverListManager.getIterator().hasNext()) {
            return serverListManager.getIterator().next();
        } else {
            serverListManager.refreshCurrentServerAddr();
            return serverListManager.getCurrentServer();
        }
    }
    
    private String buildUrl(String serverAddr, String relativePath) {
        if (!serverAddr.startsWith(RequestUrlConstants.HTTP_PREFIX) && !serverAddr.startsWith(
                RequestUrlConstants.HTTPS_PREFIX)) {
            serverAddr = getPrefix() + serverAddr;
        }
        String contextPath = serverListManager.getContextPath();
        return serverAddr + ContextPathUtil.normalizeContextPath(contextPath) + relativePath;
    }
    
    public String getPrefix() {
        return enableHttps ? RequestUrlConstants.HTTPS_PREFIX : RequestUrlConstants.HTTP_PREFIX;
    }
    
    private <T extends RestResult<?>> boolean isFail(T result) {
        return result.getCode() == HttpURLConnection.HTTP_INTERNAL_ERROR
                || result.getCode() == HttpURLConnection.HTTP_BAD_GATEWAY
                || result.getCode() == HttpURLConnection.HTTP_UNAVAILABLE
                || result.getCode() == HttpURLConnection.HTTP_NOT_FOUND;
    }
    
    /**
     * shutdown.
     */
    public void shutdown() throws NacosException {
        String className = this.getClass().getName();
        LOGGER.info("{} do shutdown begin", className);
        HttpClientManager.getInstance().shutdown();
        serverListManager.shutdown();
        LOGGER.info("{} do shutdown stop", className);
    }
}
