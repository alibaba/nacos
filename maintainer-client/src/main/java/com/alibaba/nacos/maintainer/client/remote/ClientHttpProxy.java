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
import com.alibaba.nacos.common.http.HttpClientConfig;
import com.alibaba.nacos.common.http.HttpRestResult;
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
     * @throws NacosException exception when request
     */
    public HttpRestResult<String> executeSyncHttpRequest(HttpRequest request) throws NacosException {
        long endTime = System.currentTimeMillis() + ParamUtil.getReadTimeout();
        String currentServerAddr = serverListManager.getCurrentServer();
        int retryCount = maxRetry;
        NacosException requestException = null;
        while (System.currentTimeMillis() <= endTime && retryCount >= 0) {
            try {
                HttpRestResult<String> result = executeSync(request, currentServerAddr);
                if (!isFail(result)) {
                    serverListManager.updateCurrentServerAddr(currentServerAddr);
                }
                if (result.ok()) {
                    return result;
                }
                throw new NacosException(result.getCode(), result.getMessage());
            } catch (NacosException nacosException) {
                requestException = nacosException;
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
        
        if (null != requestException) {
            throw requestException;
        }
        throw new NacosException(NacosException.BAD_GATEWAY,
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
                throw new IllegalArgumentException("Unsupported HTTP method: " + request.getHttpMethod());
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
                || result.getCode() == HttpURLConnection.HTTP_GATEWAY_TIMEOUT;
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
