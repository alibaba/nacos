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
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.lifecycle.Closeable;
import com.alibaba.nacos.common.tls.TlsSystemConfig;
import com.alibaba.nacos.common.utils.HttpMethod;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.maintainer.client.address.DefaultServerListManager;
import com.alibaba.nacos.maintainer.client.model.HttpRequest;
import com.alibaba.nacos.maintainer.client.utils.ParamUtil;
import com.alibaba.nacos.api.model.v2.Result;
import com.fasterxml.jackson.core.type.TypeReference;
import com.alibaba.nacos.plugin.auth.api.LoginIdentityContext;
import com.alibaba.nacos.plugin.auth.api.RequestResource;
import com.alibaba.nacos.plugin.auth.spi.client.ClientAuthPluginManager;
import com.alibaba.nacos.plugin.auth.spi.client.ClientAuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
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
    
    private final NacosRestTemplate nacosRestTemplate =
        HttpClientManager.getInstance().getNacosRestTemplate();
    
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
    
    /** Initialize the server list manager. */
    public void initServerListManager(Properties properties) throws NacosException {
        serverListManager =
            new DefaultServerListManager(NacosClientProperties.PROTOTYPE.derive(properties));
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
        executor.scheduleWithFixedDelay(() -> login(properties), 0, this.refreshIntervalMills,
            TimeUnit.MILLISECONDS);
    }
    
    /**
     * Login all available ClientAuthService instance.
     *
     * @param properties login identity information.
     */
    public void login(Properties properties) {
        for (ClientAuthService clientAuthService : clientAuthPluginManager
            .getAuthServiceSpiImplSet()) {
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
    public HttpRestResult<String> executeSyncHttpRequest(HttpRequest request)
        throws NacosException {
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
                throw new NacosException(result.getCode(), resolveErrorMessage(result));
            } catch (NacosException nacosException) {
                requestException = nacosException;
                resultCode = nacosException.getErrCode();
            } catch (Exception ex) {
                LOGGER.error("[NACOS Exception] Server address: {}, Error: {}", currentServerAddr,
                    ex.getMessage());
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
                "No available server after " + maxRetry + " retries, last tried server: "
                    + currentServerAddr
                    + ", last errMsg: " + requestException.getErrMsg());
        }
        throw new NacosException(NacosException.BAD_GATEWAY,
            "No available server after " + maxRetry + " retries, last tried server: "
                + currentServerAddr);
    }
    
    private String resolveErrorMessage(HttpRestResult<String> result) {
        String responseBody = result.getData();
        if (StringUtils.isNotBlank(responseBody)) {
            try {
                Result<Object> response =
                    JacksonUtils.toObj(responseBody, new TypeReference<Result<Object>>() {
                    });
                if (response != null) {
                    String message = response.getMessage();
                    Object data = response.getData();
                    if (data instanceof String && StringUtils.isNotBlank((String) data)) {
                        if (StringUtils.isNotBlank(message) && !data.equals(message)) {
                            return message + ": " + data;
                        }
                        return (String) data;
                    }
                    if (StringUtils.isNotBlank(message)) {
                        return message;
                    }
                }
            } catch (Exception ignored) {
                // Fall back to the raw response body for non-Result payloads.
            }
            return responseBody;
        }
        return result.getMessage();
    }
    
    private HttpRestResult<String> executeSync(HttpRequest request, String serverAddr)
        throws Exception {
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
        
        if (request.isFileUpload()) {
            return executeMultipartPost(url, httpConfig, httpHeaders, query, request);
        }
        
        switch (request.getHttpMethod()) {
            case HttpMethod.GET:
                return nacosRestTemplate.get(url, httpConfig, httpHeaders, query, String.class);
            case HttpMethod.POST:
                if (StringUtils.isNotBlank(request.getBody())) {
                    return nacosRestTemplate.postJson(url, httpHeaders, query, request.getBody(),
                        String.class);
                } else {
                    return nacosRestTemplate.postForm(url, httpConfig, httpHeaders, paramValues,
                        String.class);
                }
            case HttpMethod.PUT:
                return nacosRestTemplate.putForm(url, httpConfig, httpHeaders, paramValues,
                    String.class);
            case HttpMethod.DELETE:
                return nacosRestTemplate.delete(url, httpConfig, httpHeaders, query, String.class);
            default:
                throw new IllegalArgumentException(
                    "Unsupported HTTP method: " + request.getHttpMethod());
        }
    }
    
    private static final String LINE_FEED = "\r\n";
    
    private static final String BOUNDARY_PREFIX = "----NacosBoundary";
    
    private HttpRestResult<String> executeMultipartPost(String url, HttpClientConfig httpConfig,
        Header httpHeaders,
        Query query, HttpRequest request) throws IOException {
        String fullUrl = query != null && !query.isEmpty() ? url + "?" + query.toQueryUrl() : url;
        String boundary = BOUNDARY_PREFIX + System.currentTimeMillis();
        
        java.net.URL urlObj = new java.net.URL(fullUrl);
        HttpURLConnection conn = (HttpURLConnection) urlObj.openConnection();
        conn.setRequestMethod(HttpMethod.POST);
        conn.setConnectTimeout(httpConfig.getConTimeOutMillis());
        conn.setReadTimeout(httpConfig.getReadTimeOutMillis());
        conn.setDoOutput(true);
        
        for (Map.Entry<String, String> entry : httpHeaders.getHeader().entrySet()) {
            conn.setRequestProperty(entry.getKey(), entry.getValue());
        }
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        
        byte[] fileBytes = request.getFileBytes();
        String fieldName = request.getFileFieldName();
        String fileName = request.getFileName();
        
        StringBuilder header = new StringBuilder();
        header.append("--").append(boundary).append(LINE_FEED);
        header.append("Content-Disposition: form-data; name=\"").append(fieldName)
            .append("\"; filename=\"")
            .append(fileName).append("\"").append(LINE_FEED);
        header.append("Content-Type: application/octet-stream").append(LINE_FEED).append(LINE_FEED);
        
        byte[] headerBytes = header.toString().getBytes(StandardCharsets.UTF_8);
        byte[] tailBytes =
            (LINE_FEED + "--" + boundary + "--" + LINE_FEED).getBytes(StandardCharsets.UTF_8);
        
        try (OutputStream outputStream = conn.getOutputStream()) {
            outputStream.write(headerBytes);
            outputStream.write(fileBytes);
            outputStream.write(tailBytes);
            outputStream.flush();
        }
        
        conn.connect();
        
        HttpRestResult<String> result = new HttpRestResult<>();
        result.setCode(conn.getResponseCode());
        result.setMessage(conn.getResponseMessage());
        try {
            java.io.InputStream inputStream = conn.getResponseCode() >= 400
                ? conn.getErrorStream() : conn.getInputStream();
            if (inputStream != null) {
                result.setData(com.alibaba.nacos.common.utils.IoUtils.toString(inputStream,
                    StandardCharsets.UTF_8.name()));
            }
        } finally {
            conn.disconnect();
        }
        return result;
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
        return resultCode == HttpURLConnection.HTTP_INTERNAL_ERROR
            || resultCode == HttpURLConnection.HTTP_BAD_GATEWAY
            || resultCode == HttpURLConnection.HTTP_UNAVAILABLE
            || resultCode == HttpURLConnection.HTTP_GATEWAY_TIMEOUT;
    }
    
    /**
     * Login again to refresh the accessToken.
     */
    public void reLogin() {
        for (ClientAuthService clientAuthService : clientAuthPluginManager
            .getAuthServiceSpiImplSet()) {
            try {
                LoginIdentityContext loginIdentityContext =
                    clientAuthService.getLoginIdentityContext(
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
