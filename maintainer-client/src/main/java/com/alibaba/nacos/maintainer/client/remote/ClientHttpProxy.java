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

import com.alibaba.nacos.maintainer.client.address.DefaultServerListManager;
import com.alibaba.nacos.maintainer.client.env.NacosClientProperties;
import com.alibaba.nacos.maintainer.client.exception.NacosException;
import com.alibaba.nacos.maintainer.client.remote.client.NacosRestTemplate;
import com.alibaba.nacos.maintainer.client.remote.param.Header;
import com.alibaba.nacos.maintainer.client.remote.param.Query;
import com.alibaba.nacos.maintainer.client.utils.ContextPathUtil;
import com.alibaba.nacos.maintainer.client.utils.ParamUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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
@Service
public class ClientHttpProxy {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientHttpProxy.class);
    
    private final NacosRestTemplate nacosRestTemplate = HttpClientManager.getInstance().getNacosRestTemplate();
    
    private String encode;
    
    private int maxRetry = 3;
    
    private final DefaultServerListManager defaultServerListManager;
    
//    public ClientHttpProxy(DefaultServerListManager mgr) {
//        this.defaultServerListManager = mgr;
//    }
//
//    public ClientHttpProxy(DefaultServerListManager mgr, Properties properties) {
//        this.defaultServerListManager = mgr;
//    }
    
    public ClientHttpProxy(Properties properties) throws NacosException {
        this.defaultServerListManager = new DefaultServerListManager(NacosClientProperties.PROTOTYPE.derive(properties));
        start();
    }
    
    public void start() throws NacosException {
        defaultServerListManager.start();
    }
    
    /**
     * http get.
     */
    public HttpRestResult<String> httpGet(String path, Map<String, String> headers, Map<String, String> paramValues,
            long readTimeoutMs) throws Exception {
        final long endTime = System.currentTimeMillis() + readTimeoutMs;
        String currentServerAddr = defaultServerListManager.getCurrentServer();
        int maxRetry = this.maxRetry;
        HttpClientConfig httpConfig = HttpClientConfig.builder()
                .setReadTimeOutMillis(Long.valueOf(readTimeoutMs).intValue())
                .setConTimeOutMillis(100).build();
        do {
            try {
                Header newHeaders = Header.newInstance();
                if (headers != null) {
                    newHeaders.addAll(headers);
                }
                Query query = Query.newInstance().initParams(paramValues);
                HttpRestResult<String> result = nacosRestTemplate.get(getUrl(currentServerAddr, path), httpConfig,
                        newHeaders, query, String.class);
                if (isFail(result)) {
                    LOGGER.error("[NACOS ConnectException] currentServerAddr: {}, httpCode: {}",
                            defaultServerListManager.getCurrentServer(), result.getCode());
                } else {
                    // Update the currently available server addr
                    defaultServerListManager.updateCurrentServerAddr(currentServerAddr);
                    return result;
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
    
//    public HttpRestResult<String> httpPost(String path, Map<String, String> headers, Map<String, String> paramValues,
//            String encode, long readTimeoutMs) throws Exception {
//        final long endTime = System.currentTimeMillis() + readTimeoutMs;
//        String currentServerAddr = defaultServerListManager.getCurrentServer();
//        int maxRetry = this.maxRetry;
//        HttpClientConfig httpConfig = HttpClientConfig.builder()
//                .setReadTimeOutMillis(Long.valueOf(readTimeoutMs).intValue())
//                .setConTimeOutMillis(ConfigHttpClientManager.getInstance().getConnectTimeoutOrDefault(3000)).build();
//        do {
//            try {
//                Header newHeaders = Header.newInstance();
//                if (headers != null) {
//                    newHeaders.addAll(headers);
//                }
//                HttpRestResult<String> result = nacosRestTemplate.postForm(getUrl(currentServerAddr, path), httpConfig,
//                        newHeaders, paramValues, String.class);
//
//                if (isFail(result)) {
//                    LOGGER.error("[NACOS ConnectException] currentServerAddr: {}, httpCode: {}", currentServerAddr,
//                            result.getCode());
//                } else {
//                    // Update the currently available server addr
//                    defaultServerListManager.updateCurrentServerAddr(currentServerAddr);
//                    return result;
//                }
//            } catch (ConnectException connectException) {
//                LOGGER.error("[NACOS ConnectException httpPost] currentServerAddr: {}, err : {}", currentServerAddr,
//                        connectException.getMessage());
//            } catch (SocketTimeoutException socketTimeoutException) {
//                LOGGER.error("[NACOS SocketTimeoutException httpPost] currentServerAddr: {}， err : {}",
//                        currentServerAddr, socketTimeoutException.getMessage());
//            } catch (Exception ex) {
//                LOGGER.error("[NACOS Exception httpPost] currentServerAddr: " + currentServerAddr, ex);
//                throw ex;
//            }
//
//            if (defaultServerListManager.getIterator().hasNext()) {
//                currentServerAddr = defaultServerListManager.getIterator().next();
//            } else {
//                maxRetry--;
//                if (maxRetry < 0) {
//                    throw new ConnectException(
//                            "[NACOS HTTP-POST] The maximum number of tolerable server reconnection errors has been reached");
//                }
//                defaultServerListManager.refreshCurrentServerAddr();
//            }
//
//        } while (System.currentTimeMillis() <= endTime);
//
//        LOGGER.error("no available server, currentServerAddr : {}", currentServerAddr);
//        throw new ConnectException("no available server, currentServerAddr : " + currentServerAddr);
//    }
//
//    public HttpRestResult<String> httpDelete(String path, Map<String, String> headers, Map<String, String> paramValues,
//            String encode, long readTimeoutMs) throws Exception {
//        final long endTime = System.currentTimeMillis() + readTimeoutMs;
//        String currentServerAddr = defaultServerListManager.getCurrentServer();
//        int maxRetry = this.maxRetry;
//        HttpClientConfig httpConfig = HttpClientConfig.builder()
//                .setReadTimeOutMillis(Long.valueOf(readTimeoutMs).intValue())
//                .setConTimeOutMillis(ConfigHttpClientManager.getInstance().getConnectTimeoutOrDefault(100)).build();
//        do {
//            try {
//                Header newHeaders = Header.newInstance();
//                if (headers != null) {
//                    newHeaders.addAll(headers);
//                }
//                Query query = Query.newInstance().initParams(paramValues);
//                HttpRestResult<String> result = nacosRestTemplate.delete(getUrl(currentServerAddr, path), httpConfig,
//                        newHeaders, query, String.class);
//                if (isFail(result)) {
//                    LOGGER.error("[NACOS ConnectException] currentServerAddr: {}, httpCode: {}",
//                            defaultServerListManager.getCurrentServer(), result.getCode());
//                } else {
//                    // Update the currently available server addr
//                    defaultServerListManager.updateCurrentServerAddr(currentServerAddr);
//                    return result;
//                }
//            } catch (ConnectException connectException) {
//                LOGGER.error("[NACOS ConnectException httpDelete] currentServerAddr:{}, err : {}",
//                        defaultServerListManager.getCurrentServer(), ExceptionUtil.getStackTrace(connectException));
//            } catch (SocketTimeoutException stoe) {
//                LOGGER.error("[NACOS SocketTimeoutException httpDelete] currentServerAddr:{}， err : {}",
//                        defaultServerListManager.getCurrentServer(), ExceptionUtil.getStackTrace(stoe));
//            } catch (Exception ex) {
//                LOGGER.error("[NACOS Exception httpDelete] currentServerAddr: " + defaultServerListManager.getCurrentServer(),
//                        ex);
//                throw ex;
//            }
//
//            if (defaultServerListManager.getIterator().hasNext()) {
//                currentServerAddr = defaultServerListManager.getIterator().next();
//            } else {
//                maxRetry--;
//                if (maxRetry < 0) {
//                    throw new ConnectException(
//                            "[NACOS HTTP-DELETE] The maximum number of tolerable server reconnection errors has been reached");
//                }
//                defaultServerListManager.refreshCurrentServerAddr();
//            }
//
//        } while (System.currentTimeMillis() <= endTime);
//
//        LOGGER.error("no available server");
//        throw new ConnectException("no available server");
//    }
    
    private String getUrl(String serverAddr, String relativePath) {
        String contextPath = defaultServerListManager.getContextPath();
        return serverAddr + ContextPathUtil.normalizeContextPath(contextPath) + relativePath;
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
    
    public String getName() {
        return defaultServerListManager.getName();
    }
    
    public String getNamespace() {
        return defaultServerListManager.getNamespace();
    }
    
    public String getTenant() {
        return defaultServerListManager.getTenant();
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
