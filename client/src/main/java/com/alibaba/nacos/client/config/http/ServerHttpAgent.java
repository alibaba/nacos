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

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.config.impl.ConfigHttpClientManager;
import com.alibaba.nacos.client.config.impl.ServerListManager;
import com.alibaba.nacos.client.config.impl.SpasAdapter;
import com.alibaba.nacos.client.identify.StsConfig;
import com.alibaba.nacos.client.security.SecurityProxy;
import com.alibaba.nacos.client.utils.ContextPathUtil;
import com.alibaba.nacos.client.utils.LogUtils;
import com.alibaba.nacos.client.utils.ParamUtil;
import com.alibaba.nacos.client.utils.TemplateUtils;
import com.alibaba.nacos.common.constant.HttpHeaderConsts;
import com.alibaba.nacos.common.http.HttpClientConfig;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.utils.ConvertUtils;
import com.alibaba.nacos.common.utils.ExceptionUtil;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.MD5Utils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.common.utils.ThreadUtils;
import com.alibaba.nacos.common.utils.UuidUtils;
import com.alibaba.nacos.common.utils.VersionUtils;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;

import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.util.Date;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Server Agent.
 *
 * @author water.lyl
 */
public class ServerHttpAgent implements HttpAgent {
    
    private static final Logger LOGGER = LogUtils.logger(ServerHttpAgent.class);
    
    private static final NacosRestTemplate NACOS_RESTTEMPLATE = ConfigHttpClientManager.getInstance()
            .getNacosRestTemplate();
    
    private SecurityProxy securityProxy;
    
    private String namespaceId;
    
    private final long securityInfoRefreshIntervalMills = TimeUnit.SECONDS.toMillis(5);
    
    private ScheduledExecutorService executorService;
    
    @Override
    public HttpRestResult<String> httpGet(String path, Map<String, String> headers, Map<String, String> paramValues,
            String encode, long readTimeoutMs) throws Exception {
        final long endTime = System.currentTimeMillis() + readTimeoutMs;
        injectSecurityInfo(paramValues);
        String currentServerAddr = serverListMgr.getCurrentServerAddr();
        int maxRetry = this.maxRetry;
        HttpClientConfig httpConfig = HttpClientConfig.builder()
                .setReadTimeOutMillis(Long.valueOf(readTimeoutMs).intValue())
                .setConTimeOutMillis(ConfigHttpClientManager.getInstance().getConnectTimeoutOrDefault(100)).build();
        do {
            try {
                Header newHeaders = getSpasHeaders(paramValues, encode);
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
        injectSecurityInfo(paramValues);
        String currentServerAddr = serverListMgr.getCurrentServerAddr();
        int maxRetry = this.maxRetry;
        HttpClientConfig httpConfig = HttpClientConfig.builder()
                .setReadTimeOutMillis(Long.valueOf(readTimeoutMs).intValue())
                .setConTimeOutMillis(ConfigHttpClientManager.getInstance().getConnectTimeoutOrDefault(3000)).build();
        do {
            
            try {
                Header newHeaders = getSpasHeaders(paramValues, encode);
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
        injectSecurityInfo(paramValues);
        String currentServerAddr = serverListMgr.getCurrentServerAddr();
        int maxRetry = this.maxRetry;
        HttpClientConfig httpConfig = HttpClientConfig.builder()
                .setReadTimeOutMillis(Long.valueOf(readTimeoutMs).intValue())
                .setConTimeOutMillis(ConfigHttpClientManager.getInstance().getConnectTimeoutOrDefault(100)).build();
        do {
            try {
                Header newHeaders = getSpasHeaders(paramValues, encode);
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
                LOGGER.error(
                        "[NACOS Exception httpDelete] currentServerAddr: " + serverListMgr.getCurrentServerAddr(),
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
        init(properties);
    }
    
    public ServerHttpAgent(Properties properties) throws NacosException {
        this.serverListMgr = new ServerListManager(properties);
        this.securityProxy = new SecurityProxy(properties, NACOS_RESTTEMPLATE);
        this.namespaceId = properties.getProperty(PropertyKeyConst.NAMESPACE);
        init(properties);
        this.securityProxy.login(this.serverListMgr.getServerUrls());
        
        // init executorService
        this.executorService = new ScheduledThreadPoolExecutor(1, new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r);
                t.setName("com.alibaba.nacos.client.config.security.updater");
                t.setDaemon(true);
                return t;
            }
        });
        
        this.executorService.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                securityProxy.login(serverListMgr.getServerUrls());
            }
        }, 0, this.securityInfoRefreshIntervalMills, TimeUnit.MILLISECONDS);
        
    }
    
    private void injectSecurityInfo(Map<String, String> params) {
        if (StringUtils.isNotBlank(securityProxy.getAccessToken())) {
            params.put(Constants.ACCESS_TOKEN, securityProxy.getAccessToken());
        }
        if (StringUtils.isNotBlank(namespaceId) && !params.containsKey(SpasAdapter.TENANT_KEY)) {
            params.put(SpasAdapter.TENANT_KEY, namespaceId);
        }
    }
    
    private void init(Properties properties) {
        initEncode(properties);
        initAkSk(properties);
        initMaxRetry(properties);
    }
    
    private void initEncode(Properties properties) {
        encode = TemplateUtils
                .stringEmptyAndThenExecute(properties.getProperty(PropertyKeyConst.ENCODE), new Callable<String>() {
                    @Override
                    public String call() throws Exception {
                        return Constants.ENCODE;
                    }
                });
    }
    
    private void initAkSk(Properties properties) {
        String ramRoleName = properties.getProperty(PropertyKeyConst.RAM_ROLE_NAME);
        if (!StringUtils.isBlank(ramRoleName)) {
            StsConfig.getInstance().setRamRoleName(ramRoleName);
        }
        
        String ak = properties.getProperty(PropertyKeyConst.ACCESS_KEY);
        if (StringUtils.isBlank(ak)) {
            accessKey = SpasAdapter.getAk();
        } else {
            accessKey = ak;
        }
        
        String sk = properties.getProperty(PropertyKeyConst.SECRET_KEY);
        if (StringUtils.isBlank(sk)) {
            secretKey = SpasAdapter.getSk();
        } else {
            secretKey = sk;
        }
    }
    
    private void initMaxRetry(Properties properties) {
        maxRetry = ConvertUtils.toInt(String.valueOf(properties.get(PropertyKeyConst.MAX_RETRY)), Constants.MAX_RETRY);
    }
    
    @Override
    public void start() throws NacosException {
        serverListMgr.start();
    }
    
    private Header getSpasHeaders(Map<String, String> paramValues, String encode) throws Exception {
        Header header = Header.newInstance();
        // STS 临时凭证鉴权的优先级高于 AK/SK 鉴权
        if (StsConfig.getInstance().isStsOn()) {
            StsCredential stsCredential = getStsCredential();
            accessKey = stsCredential.accessKeyId;
            secretKey = stsCredential.accessKeySecret;
            header.addParam("Spas-SecurityToken", stsCredential.securityToken);
        }
        
        if (StringUtils.isNotEmpty(accessKey) && StringUtils.isNotEmpty(secretKey)) {
            header.addParam("Spas-AccessKey", accessKey);
            Map<String, String> signHeaders = SpasAdapter.getSignHeaders(paramValues, secretKey);
            if (signHeaders != null) {
                header.addAll(signHeaders);
            }
        }
        String ts = String.valueOf(System.currentTimeMillis());
        String token = MD5Utils.md5Hex(ts + ParamUtil.getAppKey(), Constants.ENCODE);
        
        header.addParam(Constants.CLIENT_APPNAME_HEADER, ParamUtil.getAppName());
        header.addParam(Constants.CLIENT_REQUEST_TS_HEADER, ts);
        header.addParam(Constants.CLIENT_REQUEST_TOKEN_HEADER, token);
        header.addParam(HttpHeaderConsts.CLIENT_VERSION_HEADER, VersionUtils.version);
        header.addParam("exConfigInfo", "true");
        header.addParam(HttpHeaderConsts.REQUEST_ID, UuidUtils.generateUuid());
        header.addParam(HttpHeaderConsts.ACCEPT_CHARSET, encode);
        return header;
    }
    
    private StsCredential getStsCredential() throws Exception {
        boolean cacheSecurityCredentials = StsConfig.getInstance().isCacheSecurityCredentials();
        if (cacheSecurityCredentials && stsCredential != null) {
            long currentTime = System.currentTimeMillis();
            long expirationTime = stsCredential.expiration.getTime();
            int timeToRefreshInMillisecond = StsConfig.getInstance().getTimeToRefreshInMillisecond();
            if (expirationTime - currentTime > timeToRefreshInMillisecond) {
                return stsCredential;
            }
        }
        String stsResponse = getStsResponse();
        StsCredential stsCredentialTmp = JacksonUtils.toObj(stsResponse, new TypeReference<StsCredential>() {
        });
        stsCredential = stsCredentialTmp;
        LOGGER.info("[getSTSCredential] code:{}, accessKeyId:{}, lastUpdated:{}, expiration:{}",
                stsCredential.getCode(), stsCredential.getAccessKeyId(), stsCredential.getLastUpdated(),
                stsCredential.getExpiration());
        return stsCredential;
    }
    
    private static String getStsResponse() throws Exception {
        String securityCredentials = StsConfig.getInstance().getSecurityCredentials();
        if (securityCredentials != null) {
            return securityCredentials;
        }
        String securityCredentialsUrl = StsConfig.getInstance().getSecurityCredentialsUrl();
        try {
            HttpRestResult<String> result = NACOS_RESTTEMPLATE
                    .get(securityCredentialsUrl, Header.EMPTY, Query.EMPTY, String.class);
            
            if (!result.ok()) {
                LOGGER.error(
                        "can not get security credentials, securityCredentialsUrl: {}, responseCode: {}, response: {}",
                        securityCredentialsUrl, result.getCode(), result.getMessage());
                throw new NacosException(NacosException.SERVER_ERROR,
                        "can not get security credentials, responseCode: " + result.getCode() + ", response: " + result
                                .getMessage());
            }
            return result.getData();
        } catch (Exception e) {
            LOGGER.error("can not get security credentials", e);
            throw e;
        }
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
        ThreadUtils.shutdownThreadPool(executorService, LOGGER);
        ConfigHttpClientManager.getInstance().shutdown();
        SpasAdapter.freeCredentialInstance();
        LOGGER.info("{} do shutdown stop", className);
    }
    
    private static class StsCredential {
        
        @JsonProperty(value = "AccessKeyId")
        private String accessKeyId;
        
        @JsonProperty(value = "AccessKeySecret")
        private String accessKeySecret;
        
        @JsonProperty(value = "Expiration")
        private Date expiration;
        
        @JsonProperty(value = "SecurityToken")
        private String securityToken;
        
        @JsonProperty(value = "LastUpdated")
        private Date lastUpdated;
        
        @JsonProperty(value = "Code")
        private String code;
        
        public String getAccessKeyId() {
            return accessKeyId;
        }
        
        public Date getExpiration() {
            return expiration;
        }
        
        public Date getLastUpdated() {
            return lastUpdated;
        }
        
        public String getCode() {
            return code;
        }
        
        @Override
        public String toString() {
            return "STSCredential{" + "accessKeyId='" + accessKeyId + '\'' + ", accessKeySecret='" + accessKeySecret
                    + '\'' + ", expiration=" + expiration + ", securityToken='" + securityToken + '\''
                    + ", lastUpdated=" + lastUpdated + ", code='" + code + '\'' + '}';
        }
    }
    
    private String accessKey;
    
    private String secretKey;
    
    private String encode;
    
    private int maxRetry = 3;
    
    private volatile StsCredential stsCredential;
    
    final ServerListManager serverListMgr;
    
}
