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
import com.alibaba.nacos.common.constant.RequestUrlConstants;
import com.alibaba.nacos.common.http.HttpClientConfig;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.tls.TlsSystemConfig;
import com.alibaba.nacos.common.utils.HttpMethod;
import com.alibaba.nacos.maintainer.client.address.DefaultServerListManager;
import com.alibaba.nacos.maintainer.client.env.NacosClientProperties;
import com.alibaba.nacos.maintainer.client.model.HttpRequest;
import com.alibaba.nacos.maintainer.client.utils.ContextPathUtil;
import com.alibaba.nacos.maintainer.client.utils.ParamUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
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
    
    private String encode;
    
    private static final boolean ENABLE_HTTPS = Boolean.getBoolean(TlsSystemConfig.TLS_ENABLE);
    
    private final int maxRetry = 3;
    
    private final DefaultServerListManager defaultServerListManager;
    
    public ClientHttpProxy(Properties properties) throws NacosException {
        this.defaultServerListManager = new DefaultServerListManager(NacosClientProperties.PROTOTYPE.derive(properties));
        start();
    }
    
    public void start() throws NacosException {
        defaultServerListManager.start();
    }
    
    /**
     * invoke http.
     */
    public HttpRestResult<String> executeHttpRequest(HttpRequest httpRequest) throws Exception {
        
        long readTimeoutMs = httpRequest.getReadTimeoutMs() == 0 ? ParamUtil.getReadTimeout() : httpRequest.getReadTimeoutMs();
        long connectTimeoutMs = httpRequest.getConnectTimeoutMs() == 0 ? ParamUtil.getConnectTimeout() : httpRequest.getConnectTimeoutMs();
        String httpMethod = httpRequest.getHttpMethod();
        String path = httpRequest.getPath();
        Map<String, String> headers = httpRequest.getHeaders();
        Map<String, String> paramValues = httpRequest.getParamValues();

        final long endTime = System.currentTimeMillis() + readTimeoutMs;
        String currentServerAddr = defaultServerListManager.getCurrentServer();
        int maxRetry = this.maxRetry;
        HttpClientConfig httpConfig = HttpClientConfig.builder()
                .setReadTimeOutMillis(Long.valueOf(readTimeoutMs).intValue())
                .setConTimeOutMillis(Long.valueOf(connectTimeoutMs).intValue()).build();
        do {
            try {
                Header newHeaders = Header.newInstance();
                if (headers != null) {
                    newHeaders.addAll(headers);
                }
                Query query = Query.newInstance().initParams(paramValues);
                String url = getUrl(currentServerAddr, path);
                
                HttpRestResult<String> httpRestResult = executeHttpRequest(httpMethod, url,  httpConfig, newHeaders, query, paramValues);
                
                if (isFail(httpRestResult)) {
                    LOGGER.error("[NACOS ConnectException] currentServerAddr: {}, httpCode: {}",
                            defaultServerListManager.getCurrentServer(), httpRestResult.getCode());
                } else {
                    // Update the currently available server addr
                    defaultServerListManager.updateCurrentServerAddr(currentServerAddr);
                    return httpRestResult;
                }
            } catch (ConnectException connectException) {
                LOGGER.error("[NACOS ConnectException httpGet] currentServerAddr:{}, err : {}",
                        defaultServerListManager.getCurrentServer(), connectException.getMessage());
            } catch (SocketTimeoutException socketTimeoutException) {
                LOGGER.error("[NACOS SocketTimeoutException httpGet] currentServerAddr:{}， err : {}",
                        defaultServerListManager.getCurrentServer(), socketTimeoutException.getMessage());
            } catch (Exception ex) {
                LOGGER.error("[NACOS Exception httpGet] currentServerAddr: " + defaultServerListManager.getCurrentServer(),
                        ex);
                throw ex;
            }
            
            if (defaultServerListManager.getIterator().hasNext()) {
                currentServerAddr = defaultServerListManager.getIterator().next();
            } else {
                maxRetry--;
                if (maxRetry < 0) {
                    throw new ConnectException(
                            "[NACOS HTTP-GET] The maximum number of tolerable server reconnection errors has been reached");
                }
                defaultServerListManager.refreshCurrentServerAddr();
            }
            
        } while (System.currentTimeMillis() <= endTime);
        
        LOGGER.error("no available server");
        throw new ConnectException("no available server");
    }
    
    private HttpRestResult<String> executeHttpRequest(String httpMethod, String url, HttpClientConfig httpConfig, Header header, Query query,
            Map<String, String> paramValues)
            throws Exception {
        switch (httpMethod) {
            case HttpMethod.GET:
                return nacosRestTemplate.get(url, httpConfig, header, query, String.class);
            case HttpMethod.DELETE:
                return nacosRestTemplate.delete(url, httpConfig, header, query, String.class);
            case HttpMethod.POST:
                return nacosRestTemplate.postForm(url, httpConfig, header, paramValues, String.class);
            case HttpMethod.PUT:
                return nacosRestTemplate.putForm(url, httpConfig, header, paramValues, String.class);
            default:
                throw new RuntimeException("not supported method:" + httpMethod);
        }
    }
    
    private String getUrl(String serverAddr, String relativePath) {
        if (!serverAddr.startsWith(RequestUrlConstants.HTTP_PREFIX) || !serverAddr.startsWith(RequestUrlConstants.HTTPS_PREFIX)) {
            serverAddr = getPrefix() + serverAddr;
        }
        String contextPath = defaultServerListManager.getContextPath();
        return serverAddr + ContextPathUtil.normalizeContextPath(contextPath) + relativePath;
    }
    
    public String getPrefix() {
        return ENABLE_HTTPS ? RequestUrlConstants.HTTPS_PREFIX : RequestUrlConstants.HTTP_PREFIX;
    }
    
    private boolean isFail(HttpRestResult<String> result) {
        return result.getCode() == HttpURLConnection.HTTP_INTERNAL_ERROR
                || result.getCode() == HttpURLConnection.HTTP_BAD_GATEWAY
                || result.getCode() == HttpURLConnection.HTTP_UNAVAILABLE
                || result.getCode() == HttpURLConnection.HTTP_NOT_FOUND;
    }
    
    public static String getAppname() {
        return ParamUtil.getAppName();
    }
    
    public String getEncode() {
        return encode;
    }
    
    /**
     * shutdown.
     */
    public void shutdown() throws NacosException {
        String className = this.getClass().getName();
        LOGGER.info("{} do shutdown begin", className);
        HttpClientManager.getInstance().shutdown();
        defaultServerListManager.shutdown();
        LOGGER.info("{} do shutdown stop", className);
    }
}
