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

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.config.impl.ConfigHttpClientManager;
import com.alibaba.nacos.client.serverlist.ServerListManager;
import com.alibaba.nacos.client.utils.ContextPathUtil;
import com.alibaba.nacos.client.utils.LogUtils;
import com.alibaba.nacos.client.utils.ParamUtil;
import com.alibaba.nacos.common.http.HttpClientConfig;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.utils.ExceptionUtil;
import com.alibaba.nacos.common.utils.HttpMethod;
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
    
    private final NacosRestTemplate nacosRestTemplate = ConfigHttpClientManager.getInstance().getNacosRestTemplate();
    
    private String encode;
    
    private int maxRetry = 3;
    
    final ServerListManager serverListMgr;
    
    @Override
    public HttpRestResult<String> httpGet(String path, Map<String, String> headers, Map<String, String> paramValues,
            String encode, long readTimeoutMs) throws Exception {
        final long endTime = System.currentTimeMillis() + readTimeoutMs;
        String currentServerAddr = serverListMgr.getCurrentServer();
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
                HttpRestResult<String> result = nacosRestTemplate.get(getUrl(currentServerAddr, path), httpConfig,
                        newHeaders, query, String.class);
                if (isFail(result)) {
                    LOGGER.error("[NACOS ConnectException] currentServerAddr: {}, httpCode: {}",
                            currentServerAddr, result.getCode());
                } else {
                    return result;
                }
            } catch (ConnectException connectException) {
                LOGGER.error("[NACOS ConnectException httpGet] currentServerAddr:{}, err : {}",
                        currentServerAddr, connectException.getMessage());
            } catch (SocketTimeoutException socketTimeoutException) {
                LOGGER.error("[NACOS SocketTimeoutException httpGet] currentServerAddr:{}， err : {}",
                        currentServerAddr, socketTimeoutException.getMessage());
            } catch (Exception ex) {
                LOGGER.error("[NACOS Exception httpGet] currentServerAddr: " + currentServerAddr,
                        ex);
                throw ex;
            }

            currentServerAddr = acquireNextServer(HttpMethod.GET, maxRetry);
            maxRetry--;

        } while (System.currentTimeMillis() <= endTime);
        
        LOGGER.error("no available server");
        throw new ConnectException("no available server");
    }
    
    @Override
    public HttpRestResult<String> httpPost(String path, Map<String, String> headers, Map<String, String> paramValues,
            String encode, long readTimeoutMs) throws Exception {
        final long endTime = System.currentTimeMillis() + readTimeoutMs;
        String currentServerAddr = serverListMgr.getCurrentServer();
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
                HttpRestResult<String> result = nacosRestTemplate.postForm(getUrl(currentServerAddr, path), httpConfig,
                        newHeaders, paramValues, String.class);
                
                if (isFail(result)) {
                    LOGGER.error("[NACOS ConnectException] currentServerAddr: {}, httpCode: {}", currentServerAddr,
                            result.getCode());
                } else {
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
            
            currentServerAddr = acquireNextServer(HttpMethod.POST, maxRetry);
            maxRetry--;

        } while (System.currentTimeMillis() <= endTime);
        
        LOGGER.error("no available server, currentServerAddr : {}", currentServerAddr);
        throw new ConnectException("no available server, currentServerAddr : " + currentServerAddr);
    }
    
    @Override
    public HttpRestResult<String> httpDelete(String path, Map<String, String> headers, Map<String, String> paramValues,
            String encode, long readTimeoutMs) throws Exception {
        final long endTime = System.currentTimeMillis() + readTimeoutMs;
        String currentServerAddr = serverListMgr.getCurrentServer();
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
                HttpRestResult<String> result = nacosRestTemplate.delete(getUrl(currentServerAddr, path), httpConfig,
                        newHeaders, query, String.class);
                if (isFail(result)) {
                    LOGGER.error("[NACOS ConnectException] currentServerAddr: {}, httpCode: {}",
                            serverListMgr.getCurrentServer(), result.getCode());
                } else {
                    return result;
                }
            } catch (ConnectException connectException) {
                LOGGER.error("[NACOS ConnectException httpDelete] currentServerAddr:{}, err : {}",
                        currentServerAddr, ExceptionUtil.getStackTrace(connectException));
            } catch (SocketTimeoutException stoe) {
                LOGGER.error("[NACOS SocketTimeoutException httpDelete] currentServerAddr:{}， err : {}",
                        currentServerAddr, ExceptionUtil.getStackTrace(stoe));
            } catch (Exception ex) {
                LOGGER.error("[NACOS Exception httpDelete] currentServerAddr: " + currentServerAddr,
                        ex);
                throw ex;
            }
            
            currentServerAddr = acquireNextServer(HttpMethod.DELETE, maxRetry);
            maxRetry--;

        } while (System.currentTimeMillis() <= endTime);
        
        LOGGER.error("no available server");
        throw new ConnectException("no available server");
    }

    private String acquireNextServer(String acquireMethod, int maxRetry) throws ConnectException {
        if (serverListMgr.hasNext()) {
            return serverListMgr.genNextServer();
        }
        maxRetry--;
        if (maxRetry < 0) {
            String exceptionMsg = String.format(
                    "[NACOS HTTP-%s] The maximum number of tolerable server reconnection errors has been reached", acquireMethod);
            throw new ConnectException(exceptionMsg);
        }
        serverListMgr.refreshCurrentServerAddr();
        return serverListMgr.getCurrentServer();
    }
    
    private String getUrl(String serverAddr, String relativePath) {
        return serverAddr + ContextPathUtil.normalizeContextPath(serverListMgr.getContextPath()) + relativePath;
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
    
    public ServerHttpAgent(ServerListManager mgr) {
        this.serverListMgr = mgr;
    }
    
    public ServerHttpAgent(ServerListManager mgr, Properties properties) {
        this.serverListMgr = mgr;
    }
    
    public ServerHttpAgent(Properties properties) throws NacosException {
        this.serverListMgr = new ServerListManager(properties, Constants.Config.CONFIG_MODULE);
    }
    
    @Override
    public void start() throws NacosException {

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
        return serverListMgr.getNamespace();
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
        serverListMgr.shutdown();
        LOGGER.info("{} do shutdown stop", className);
    }
}
