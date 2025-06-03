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
import com.alibaba.nacos.client.auth.impl.NacosAuthLoginConstant;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.client.utils.ContextPathUtil;
import com.alibaba.nacos.common.constant.RequestUrlConstants;
import com.alibaba.nacos.common.executor.NameThreadFactory;
import com.alibaba.nacos.common.http.HttpClientConfig;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.lifecycle.Closeable;
import com.alibaba.nacos.common.tls.TlsSystemConfig;
import com.alibaba.nacos.common.utils.HttpMethod;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.maintainer.client.address.DefaultServerListManager;
import com.alibaba.nacos.maintainer.client.model.HttpRequest;
import com.alibaba.nacos.maintainer.client.utils.ParamUtil;
import com.alibaba.nacos.plugin.auth.api.LoginIdentityContext;
import com.alibaba.nacos.plugin.auth.api.RequestResource;
import com.alibaba.nacos.plugin.auth.spi.client.ClientAuthPluginManager;
import com.alibaba.nacos.plugin.auth.spi.client.ClientAuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.HttpURLConnection;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Client Http Proxy.
 *
 * @author Nacos
 */
public class ClientHttpProxy implements Closeable {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientHttpProxy.class);
    
    private final NacosRestTemplate nacosRestTemplate = HttpClientManager.getInstance().getNacosRestTemplate();
    
    private final boolean enableHttps = Boolean.getBoolean(TlsSystemConfig.TLS_ENABLE);
    
    private final long refreshIntervalMills = ParamUtil.getRefreshIntervalMills();
    
    private final int maxRetry = ParamUtil.getMaxRetryTimes();
    
    private DefaultServerListManager serverListManager;
    
    private ClientAuthPluginManager clientAuthPluginManager;
    
    private ScheduledExecutorService executor;
    
    public ClientHttpProxy(Properties properties) throws NacosException {
        initServerListManager(properties);
        initClientAuthService(properties);
        initScheduledExecutor(properties);
    }
    
    public void initServerListManager(Properties properties) throws NacosException {
        serverListManager = new DefaultServerListManager(NacosClientProperties.PROTOTYPE.derive(properties));
        serverListManager.start();
    }
    
    private void initClientAuthService(Properties properties) {
        clientAuthPluginManager = new ClientAuthPluginManager();
        clientAuthPluginManager.init(serverListManager.getServerList(), nacosRestTemplate);
        login(properties);
    }
    
    private void initScheduledExecutor(Properties properties) {
        executor = new ScheduledThreadPoolExecutor(1,
                new NameThreadFactory("com.alibaba.nacos.maintainer.client.http.proxy"));
        executor.scheduleWithFixedDelay(() -> login(properties), 0, this.refreshIntervalMills, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Login all available ClientAuthService instance.
     *
     * @param properties login identity information.
     */
    public void login(Properties properties) {
        for (ClientAuthService clientAuthService : clientAuthPluginManager.getAuthServiceSpiImplSet()) {
            clientAuthService.login(properties);
        }
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
        int resultCode = 0;
        NacosException requestException = null;
        while (System.currentTimeMillis() <= endTime && retryCount >= 0) {
            try {
                HttpRestResult<String> result = executeSync(request, currentServerAddr);
                if (result.isNoRight()) {
                    reLogin();
                }
                if (result.ok()) {
                    return result;
                }
                throw new NacosException(result.getCode(), result.getMessage());
            } catch (NacosException nacosException) {
                requestException = nacosException;
                resultCode = nacosException.getErrCode();
            } catch (Exception ex) {
                LOGGER.error("[NACOS Exception] Server address: {}, Error: {}", currentServerAddr, ex.getMessage());
                resultCode = HttpURLConnection.HTTP_INTERNAL_ERROR;
            }
            
            if (isFail(resultCode)) {
                currentServerAddr = serverListManager.genNextServer();
            }
            retryCount--;
            
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        if (null != requestException) {
            throw new NacosException(requestException.getErrCode(),
                    "No available server after " + maxRetry + " retries, last tried server: " + currentServerAddr
                            + ", last errMsg: " + requestException.getErrMsg());
        }
        throw new NacosException(NacosException.BAD_GATEWAY,
                "No available server after " + maxRetry + " retries, last tried server: " + currentServerAddr);
    }
    
    private HttpRestResult<String> executeSync(HttpRequest request, String serverAddr) throws Exception {
        long readTimeoutMs = ParamUtil.getReadTimeout();
        long connectTimeoutMs = ParamUtil.getConnectTimeout();
        Map<String, String> paramValues = request.getParamValues();
        Map<String, String> headers = request.getHeaders();
        
        HttpClientConfig httpConfig = HttpClientConfig.builder()
                .setReadTimeOutMillis(Long.valueOf(readTimeoutMs).intValue())
                .setConTimeOutMillis(Long.valueOf(connectTimeoutMs).intValue()).build();
        Header httpHeaders = Header.newInstance();
        addAuthHeader(httpHeaders, request.getResource());
        if (headers != null) {
            httpHeaders.addAll(headers);
        }
        Query query = Query.newInstance().initParams(paramValues);
        String url = buildUrl(serverAddr, request.getPath());
        
        switch (request.getHttpMethod()) {
            case HttpMethod.GET:
                return nacosRestTemplate.get(url, httpConfig, httpHeaders, query, String.class);
            case HttpMethod.POST:
                if (StringUtils.isNotBlank(request.getBody())) {
                    return nacosRestTemplate.postJson(url, httpHeaders, query, request.getBody(), String.class);
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
    
    private void addAuthHeader(Header header, RequestResource resource) {
        clientAuthPluginManager.getAuthServiceSpiImplSet().forEach(clientAuthService -> {
            LoginIdentityContext loginIdentityContext = clientAuthService.getLoginIdentityContext(
                    null == resource ? new RequestResource() : resource);
            for (String key : loginIdentityContext.getAllKey()) {
                header.addParam(key, loginIdentityContext.getParameter(key));
            }
        });
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
    
    private boolean isFail(int resultCode) {
        return resultCode == HttpURLConnection.HTTP_INTERNAL_ERROR || resultCode == HttpURLConnection.HTTP_BAD_GATEWAY
                || resultCode == HttpURLConnection.HTTP_UNAVAILABLE
                || resultCode == HttpURLConnection.HTTP_GATEWAY_TIMEOUT;
    }
    
    /**
     * Login again to refresh the accessToken.
     */
    public void reLogin() {
        for (ClientAuthService clientAuthService : clientAuthPluginManager.getAuthServiceSpiImplSet()) {
            try {
                LoginIdentityContext loginIdentityContext = clientAuthService.getLoginIdentityContext(
                        new RequestResource());
                if (loginIdentityContext != null) {
                    loginIdentityContext.setParameter(NacosAuthLoginConstant.RELOGINFLAG, "true");
                }
            } catch (Exception e) {
                LOGGER.error("[ClientHttpProxy] set reLoginFlag failed.", e);
            }
        }
    }
    
    @Override
    public void shutdown() throws NacosException {
        String className = this.getClass().getName();
        LOGGER.info("{} do shutdown begin", className);
        HttpClientManager.getInstance().shutdown();
        serverListManager.shutdown();
        if (null != clientAuthPluginManager) {
            clientAuthPluginManager.shutdown();
        }
        if (null != executor) {
            executor.shutdown();
        }
        LOGGER.info("{} do shutdown stop", className);
    }
}
