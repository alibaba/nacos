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

package com.alibaba.nacos.client.config.http;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.config.impl.ConfigHttpClientManager;
import com.alibaba.nacos.client.config.impl.ServerListManager;
import com.alibaba.nacos.client.config.impl.SpasAdapter;
import com.alibaba.nacos.client.utils.ContextPathUtil;
import com.alibaba.nacos.client.utils.LogUtils;
import com.alibaba.nacos.client.utils.ParamUtil;
import com.alibaba.nacos.common.http.HttpClientConfig;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.utils.ExceptionUtil;
import org.slf4j.Logger;

import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.util.Map;
import java.util.Properties;

/**
 * Server Agent.
 *
 * @author water.lyl
 */
public class ServerHttpAgent implements HttpAgent {
    
    private static final Logger LOGGER = LogUtils.logger(ServerHttpAgent.class);
    
    private static final NacosRestTemplate NACOS_RESTTEMPLATE = ConfigHttpClientManager.getInstance()
            .getNacosRestTemplate();
    
    private String accessKey;
    
    private String secretKey;
    
    private String encode;
    
    private int maxRetry = 3;
    
    final ServerListManager serverListMgr;
    
    @Override
    public HttpRestResult<String> httpGet(String path, Map<String, String> headers, Map<String, String> paramValues,
            String encode, long readTimeoutMs) throws Exception {
        final long endTime = System.currentTimeMillis() + readTimeoutMs;
        String currentServerAddr = serverListMgr.getCurrentServerAddr();
        int maxRetry = this.maxRetry;
        HttpClientConfig httpConfig = HttpClientConfig.builder()
                .setReadTimeOutMillis(Long.valueOf(readTimeoutMs).intValue())
                .setConTimeOutMillis(ConfigHttpClientManager.getInstance().getConnectTimeoutOrDefault(100)).build();
        do {
            try {
                Header newHeaders = Header.newInstance();
                if (headers != null) {
                    newHeaders.addAll(headers);
                }
                Query query = Query.newInstance().initParams(paramValues);
                HttpRestResult<String> result = NACOS_RESTTEMPLATE
                        .get(getUrl(currentServerAddr, path), httpConfig, newHeaders, query, String.class);
                if (isFail(result)) {
                    LOGGER.error("[NACOS ConnectException] currentServerAddr: {}, httpCode: {}",
                            serverListMgr.getCurrentServerAddr(), result.getCode());
                } else {
                    // Update the currently available server addr
                    serverListMgr.updateCurrentServerAddr(currentServerAddr);
                    return result;
                }
            } catch (ConnectException connectException) {
                LOGGER.error("[NACOS ConnectException httpGet] currentServerAddr:{}, err : {}",
                        serverListMgr.getCurrentServerAddr(), connectException.getMessage());
            } catch (SocketTimeoutException socketTimeoutException) {
                LOGGER.error("[NACOS SocketTimeoutException httpGet] currentServerAddr:{}， err : {}",
                        serverListMgr.getCurrentServerAddr(), socketTimeoutException.getMessage());
            } catch (Exception ex) {
                LOGGER.error("[NACOS Exception httpGet] currentServerAddr: " + serverListMgr.getCurrentServerAddr(),
                        ex);
                throw ex;
            }
            
            if (serverListMgr.getIterator().hasNext()) {
                currentServerAddr = serverListMgr.getIterator().next();
            } else {
                maxRetry--;
                if (maxRetry < 0) {
                    throw new ConnectException(
                            "[NACOS HTTP-GET] The maximum number of tolerable server reconnection errors has been reached");
                }
                serverListMgr.refreshCurrentServerAddr();
            }
            
        } while (System.currentTimeMillis() <= endTime);
        
        LOGGER.error("no available server");
        throw new ConnectException("no available server");
    }
    
    @Override
    public HttpRestResult<String> httpPost(String path, Map<String, String> headers, Map<String, String> paramValues,
            String encode, long readTimeoutMs) throws Exception {
        final long endTime = System.currentTimeMillis() + readTimeoutMs;
        String currentServerAddr = serverListMgr.getCurrentServerAddr();
        int maxRetry = this.maxRetry;
        HttpClientConfig httpConfig = HttpClientConfig.builder()
                .setReadTimeOutMillis(Long.valueOf(readTimeoutMs).intValue())
                .setConTimeOutMillis(ConfigHttpClientManager.getInstance().getConnectTimeoutOrDefault(3000)).build();
        do {
            try {
                Header newHeaders = Header.newInstance();
                if (headers != null) {
                    newHeaders.addAll(headers);
                }
                HttpRestResult<String> result = NACOS_RESTTEMPLATE
                        .postForm(getUrl(currentServerAddr, path), httpConfig, newHeaders, paramValues, String.class);
                
                if (isFail(result)) {
                    LOGGER.error("[NACOS ConnectException] currentServerAddr: {}, httpCode: {}", currentServerAddr,
                            result.getCode());
                } else {
                    // Update the currently available server addr
                    serverListMgr.updateCurrentServerAddr(currentServerAddr);
                    return result;
                }
            } catch (ConnectException connectException) {
                LOGGER.error("[NACOS ConnectException httpPost] currentServerAddr: {}, err : {}", currentServerAddr,
                        connectException.getMessage());
            } catch (SocketTimeoutException socketTimeoutException) {
                LOGGER.error("[NACOS SocketTimeoutException httpPost] currentServerAddr: {}， err : {}",
                        currentServerAddr, socketTimeoutException.getMessage());
            } catch (Exception ex) {
                LOGGER.error("[NACOS Exception httpPost] currentServerAddr: " + currentServerAddr, ex);
                throw ex;
            }
            
            if (serverListMgr.getIterator().hasNext()) {
                currentServerAddr = serverListMgr.getIterator().next();
            } else {
                maxRetry--;
                if (maxRetry < 0) {
                    throw new ConnectException(
                            "[NACOS HTTP-POST] The maximum number of tolerable server reconnection errors has been reached");
                }
                serverListMgr.refreshCurrentServerAddr();
            }
            
        } while (System.currentTimeMillis() <= endTime);
        
        LOGGER.error("no available server, currentServerAddr : {}", currentServerAddr);
        throw new ConnectException("no available server, currentServerAddr : " + currentServerAddr);
    }
    
    @Override
    public HttpRestResult<String> httpDelete(String path, Map<String, String> headers, Map<String, String> paramValues,
            String encode, long readTimeoutMs) throws Exception {
        final long endTime = System.currentTimeMillis() + readTimeoutMs;
        String currentServerAddr = serverListMgr.getCurrentServerAddr();
        int maxRetry = this.maxRetry;
        HttpClientConfig httpConfig = HttpClientConfig.builder()
                .setReadTimeOutMillis(Long.valueOf(readTimeoutMs).intValue())
                .setConTimeOutMillis(ConfigHttpClientManager.getInstance().getConnectTimeoutOrDefault(100)).build();
        do {
            try {
                Header newHeaders = Header.newInstance();
                if (headers != null) {
                    newHeaders.addAll(headers);
                }
                Query query = Query.newInstance().initParams(paramValues);
                HttpRestResult<String> result = NACOS_RESTTEMPLATE
                        .delete(getUrl(currentServerAddr, path), httpConfig, newHeaders, query, String.class);
                if (isFail(result)) {
                    LOGGER.error("[NACOS ConnectException] currentServerAddr: {}, httpCode: {}",
                            serverListMgr.getCurrentServerAddr(), result.getCode());
                } else {
                    // Update the currently available server addr
                    serverListMgr.updateCurrentServerAddr(currentServerAddr);
                    return result;
                }
            } catch (ConnectException connectException) {
                LOGGER.error("[NACOS ConnectException httpDelete] currentServerAddr:{}, err : {}",
                        serverListMgr.getCurrentServerAddr(), ExceptionUtil.getStackTrace(connectException));
            } catch (SocketTimeoutException stoe) {
                LOGGER.error("[NACOS SocketTimeoutException httpDelete] currentServerAddr:{}， err : {}",
                        serverListMgr.getCurrentServerAddr(), ExceptionUtil.getStackTrace(stoe));
            } catch (Exception ex) {
                LOGGER.error("[NACOS Exception httpDelete] currentServerAddr: " + serverListMgr.getCurrentServerAddr(),
                        ex);
                throw ex;
            }
            
            if (serverListMgr.getIterator().hasNext()) {
                currentServerAddr = serverListMgr.getIterator().next();
            } else {
                maxRetry--;
                if (maxRetry < 0) {
                    throw new ConnectException(
                            "[NACOS HTTP-DELETE] The maximum number of tolerable server reconnection errors has been reached");
                }
                serverListMgr.refreshCurrentServerAddr();
            }
            
        } while (System.currentTimeMillis() <= endTime);
        
        LOGGER.error("no available server");
        throw new ConnectException("no available server");
    }
    
    private String getUrl(String serverAddr, String relativePath) {
        return serverAddr + ContextPathUtil.normalizeContextPath(serverListMgr.getContentPath()) + relativePath;
    }
    
    private boolean isFail(HttpRestResult<String> result) {
        return result.getCode() == HttpURLConnection.HTTP_INTERNAL_ERROR
                || result.getCode() == HttpURLConnection.HTTP_BAD_GATEWAY
                || result.getCode() == HttpURLConnection.HTTP_UNAVAILABLE;
    }
    
    public static String getAppname() {
        return ParamUtil.getAppName();
    }
    
    public ServerHttpAgent(ServerListManager mgr) {
        this.serverListMgr = mgr;
    }
    
    public ServerHttpAgent(ServerListManager mgr, Properties properties) {
        this.serverListMgr = mgr;
    }
    
    public ServerHttpAgent(Properties properties) throws NacosException {
        this.serverListMgr = new ServerListManager(properties);
    }
    
    @Override
    public void start() throws NacosException {
        serverListMgr.start();
    }
    
    @Override
    public String getName() {
        return serverListMgr.getName();
    }
    
    @Override
    public String getNamespace() {
        return serverListMgr.getNamespace();
    }
    
    @Override
    public String getTenant() {
        return serverListMgr.getTenant();
    }
    
    @Override
    public String getEncode() {
        return encode;
    }
    
    @Override
    public void shutdown() throws NacosException {
        String className = this.getClass().getName();
        LOGGER.info("{} do shutdown begin", className);
        ConfigHttpClientManager.getInstance().shutdown();
        SpasAdapter.freeCredentialInstance();
        LOGGER.info("{} do shutdown stop", className);
    }
}
