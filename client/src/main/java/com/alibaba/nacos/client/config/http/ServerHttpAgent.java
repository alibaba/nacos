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
import com.alibaba.nacos.client.config.impl.HttpSimpleClient;
import com.alibaba.nacos.client.config.impl.HttpSimpleClient.HttpResult;
import com.alibaba.nacos.client.config.impl.ServerListManager;
import com.alibaba.nacos.client.config.impl.SpasAdapter;
import com.alibaba.nacos.client.identify.STSConfig;
import com.alibaba.nacos.client.security.SecurityProxy;
import com.alibaba.nacos.client.utils.LogUtils;
import com.alibaba.nacos.client.utils.ParamUtil;
import com.alibaba.nacos.client.utils.TemplateUtils;
import com.alibaba.nacos.common.utils.IoUtils;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.*;

/**
 * Server Agent
 *
 * @author water.lyl
 */
public class ServerHttpAgent implements HttpAgent {

    private static final Logger LOGGER = LogUtils.logger(ServerHttpAgent.class);

    private SecurityProxy securityProxy;

    private String namespaceId;

    private long securityInfoRefreshIntervalMills = TimeUnit.SECONDS.toMillis(5);

    /**
     * @param path          相对于web应用根，以/开头
     * @param headers
     * @param paramValues
     * @param encoding
     * @param readTimeoutMs
     * @return
     * @throws IOException
     */
    @Override
    public HttpResult httpGet(String path, List<String> headers, List<String> paramValues, String encoding,
                              long readTimeoutMs) throws IOException {
        final long endTime = System.currentTimeMillis() + readTimeoutMs;
        final boolean isSSL = false;
        injectSecurityInfo(paramValues);
        String currentServerAddr = serverListMgr.getCurrentServerAddr();
        int maxRetry = this.maxRetry;

        do {
            try {
                List<String> newHeaders = getSpasHeaders(paramValues);
                if (headers != null) {
                    newHeaders.addAll(headers);
                }
                HttpResult result = HttpSimpleClient.httpGet(
                    getUrl(currentServerAddr, path), newHeaders, paramValues, encoding,
                    readTimeoutMs, isSSL);
                if (result.code == HttpURLConnection.HTTP_INTERNAL_ERROR
                    || result.code == HttpURLConnection.HTTP_BAD_GATEWAY
                    || result.code == HttpURLConnection.HTTP_UNAVAILABLE) {
                    LOGGER.error("[NACOS ConnectException] currentServerAddr: {}, httpCode: {}",
                        serverListMgr.getCurrentServerAddr(), result.code);
                } else {
                    // Update the currently available server addr
                    serverListMgr.updateCurrentServerAddr(currentServerAddr);
                    return result;
                }
            } catch (ConnectException ce) {
                LOGGER.error("[NACOS ConnectException httpGet] currentServerAddr:{}, err : {}", serverListMgr.getCurrentServerAddr(), ce.getMessage());
            } catch (SocketTimeoutException stoe) {
                LOGGER.error("[NACOS SocketTimeoutException httpGet] currentServerAddr:{}， err : {}", serverListMgr.getCurrentServerAddr(), stoe.getMessage());
            } catch (IOException ioe) {
                LOGGER.error("[NACOS IOException httpGet] currentServerAddr: " + serverListMgr.getCurrentServerAddr(), ioe);
                throw ioe;
            }

            if (serverListMgr.getIterator().hasNext()) {
                currentServerAddr = serverListMgr.getIterator().next();
            } else {
                maxRetry--;
                if (maxRetry < 0) {
                    throw new ConnectException("[NACOS HTTP-GET] The maximum number of tolerable server reconnection errors has been reached");
                }
                serverListMgr.refreshCurrentServerAddr();
            }

        } while (System.currentTimeMillis() <= endTime);

        LOGGER.error("no available server");
        throw new ConnectException("no available server");
    }

    @Override
    public HttpResult httpPost(String path, List<String> headers, List<String> paramValues, String encoding,
                               long readTimeoutMs) throws IOException {
        final long endTime = System.currentTimeMillis() + readTimeoutMs;
        boolean isSSL = false;
        injectSecurityInfo(paramValues);
        String currentServerAddr = serverListMgr.getCurrentServerAddr();
        int maxRetry = this.maxRetry;

        do {

            try {
                List<String> newHeaders = getSpasHeaders(paramValues);
                if (headers != null) {
                    newHeaders.addAll(headers);
                }

                HttpResult result = HttpSimpleClient.httpPost(
                    getUrl(currentServerAddr, path), newHeaders, paramValues, encoding,
                    readTimeoutMs, isSSL);
                if (result.code == HttpURLConnection.HTTP_INTERNAL_ERROR
                    || result.code == HttpURLConnection.HTTP_BAD_GATEWAY
                    || result.code == HttpURLConnection.HTTP_UNAVAILABLE) {
                    LOGGER.error("[NACOS ConnectException] currentServerAddr: {}, httpCode: {}",
                        currentServerAddr, result.code);
                } else {
                    // Update the currently available server addr
                    serverListMgr.updateCurrentServerAddr(currentServerAddr);
                    return result;
                }
            } catch (ConnectException ce) {
                LOGGER.error("[NACOS ConnectException httpPost] currentServerAddr: {}, err : {}", currentServerAddr, ce.getMessage());
            } catch (SocketTimeoutException stoe) {
                LOGGER.error("[NACOS SocketTimeoutException httpPost] currentServerAddr: {}， err : {}", currentServerAddr, stoe.getMessage());
            } catch (IOException ioe) {
                LOGGER.error("[NACOS IOException httpPost] currentServerAddr: " + currentServerAddr, ioe);
                throw ioe;
            }

            if (serverListMgr.getIterator().hasNext()) {
                currentServerAddr = serverListMgr.getIterator().next();
            } else {
                maxRetry--;
                if (maxRetry < 0) {
                    throw new ConnectException("[NACOS HTTP-POST] The maximum number of tolerable server reconnection errors has been reached");
                }
                serverListMgr.refreshCurrentServerAddr();
            }

        } while (System.currentTimeMillis() <= endTime);

        LOGGER.error("no available server, currentServerAddr : {}", currentServerAddr);
        throw new ConnectException("no available server, currentServerAddr : " + currentServerAddr);
    }

    @Override
    public HttpResult httpDelete(String path, List<String> headers, List<String> paramValues, String encoding,
                                 long readTimeoutMs) throws IOException {
        final long endTime = System.currentTimeMillis() + readTimeoutMs;
        boolean isSSL = false;
        injectSecurityInfo(paramValues);
        String currentServerAddr = serverListMgr.getCurrentServerAddr();
        int maxRetry = this.maxRetry;

        do {
            try {
                List<String> newHeaders = getSpasHeaders(paramValues);
                if (headers != null) {
                    newHeaders.addAll(headers);
                }
                HttpResult result = HttpSimpleClient.httpDelete(
                    getUrl(currentServerAddr, path), newHeaders, paramValues, encoding,
                    readTimeoutMs, isSSL);
                if (result.code == HttpURLConnection.HTTP_INTERNAL_ERROR
                    || result.code == HttpURLConnection.HTTP_BAD_GATEWAY
                    || result.code == HttpURLConnection.HTTP_UNAVAILABLE) {
                    LOGGER.error("[NACOS ConnectException] currentServerAddr: {}, httpCode: {}",
                        serverListMgr.getCurrentServerAddr(), result.code);
                } else {
                    // Update the currently available server addr
                    serverListMgr.updateCurrentServerAddr(currentServerAddr);
                    return result;
                }
            } catch (ConnectException ce) {
                ce.printStackTrace();
                LOGGER.error("[NACOS ConnectException httpDelete] currentServerAddr:{}, err : {}", serverListMgr.getCurrentServerAddr(), ce.getMessage());
            } catch (SocketTimeoutException stoe) {
                stoe.printStackTrace();
                LOGGER.error("[NACOS SocketTimeoutException httpDelete] currentServerAddr:{}， err : {}", serverListMgr.getCurrentServerAddr(), stoe.getMessage());
            } catch (IOException ioe) {
                LOGGER.error("[NACOS IOException httpDelete] currentServerAddr: " + serverListMgr.getCurrentServerAddr(), ioe);
                throw ioe;
            }

            if (serverListMgr.getIterator().hasNext()) {
                currentServerAddr = serverListMgr.getIterator().next();
            } else {
                maxRetry--;
                if (maxRetry < 0) {
                    throw new ConnectException("[NACOS HTTP-DELETE] The maximum number of tolerable server reconnection errors has been reached");
                }
                serverListMgr.refreshCurrentServerAddr();
            }

        } while (System.currentTimeMillis() <= endTime);

        LOGGER.error("no available server");
        throw new ConnectException("no available server");
    }

    private String getUrl(String serverAddr, String relativePath) {
        String contextPath = serverListMgr.getContentPath().startsWith("/") ?
                serverListMgr.getContentPath() : "/" + serverListMgr.getContentPath();
        return serverAddr + contextPath + relativePath;
    }

    public static String getAppname() {
        return ParamUtil.getAppName();
    }

    public ServerHttpAgent(ServerListManager mgr) {
        serverListMgr = mgr;
    }

    public ServerHttpAgent(ServerListManager mgr, Properties properties) {
        serverListMgr = mgr;
        init(properties);
    }

    public ServerHttpAgent(Properties properties) throws NacosException {
        serverListMgr = new ServerListManager(properties);
        securityProxy = new SecurityProxy(properties);
        namespaceId = properties.getProperty(PropertyKeyConst.NAMESPACE);
        init(properties);
        securityProxy.login(serverListMgr.getServerUrls());

        ScheduledExecutorService executorService = new ScheduledThreadPoolExecutor(1, new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r);
                t.setName("com.alibaba.nacos.client.config.security.updater");
                t.setDaemon(true);
                return t;
            }
        });

        executorService.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                securityProxy.login(serverListMgr.getServerUrls());
            }
        }, 0, securityInfoRefreshIntervalMills, TimeUnit.MILLISECONDS);
    }

    private void injectSecurityInfo(List<String> params) {
        if (StringUtils.isNotBlank(securityProxy.getAccessToken())) {
            params.add(Constants.ACCESS_TOKEN);
            params.add(securityProxy.getAccessToken());
        }
        if (StringUtils.isNotBlank(namespaceId) && !params.contains(SpasAdapter.TENANT_KEY)) {
            params.add(SpasAdapter.TENANT_KEY);
            params.add(namespaceId);
        }
    }

    private void init(Properties properties) {
        initEncode(properties);
        initAkSk(properties);
        initMaxRetry(properties);
    }

    private void initEncode(Properties properties) {
        encode = TemplateUtils.stringEmptyAndThenExecute(properties.getProperty(PropertyKeyConst.ENCODE), new Callable<String>() {
            @Override
            public String call() throws Exception {
                return Constants.ENCODE;
            }
        });
    }

    private void initAkSk(Properties properties) {
        String ramRoleName = properties.getProperty(PropertyKeyConst.RAM_ROLE_NAME);
        if (!StringUtils.isBlank(ramRoleName)) {
            STSConfig.getInstance().setRamRoleName(ramRoleName);
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
        maxRetry = NumberUtils.toInt(String.valueOf(properties.get(PropertyKeyConst.MAX_RETRY)), Constants.MAX_RETRY);
    }

    @Override
    public synchronized void start() throws NacosException {
        serverListMgr.start();
    }

    private List<String> getSpasHeaders(List<String> paramValues) throws IOException {
        List<String> newHeaders = new ArrayList<String>();
        // STS 临时凭证鉴权的优先级高于 AK/SK 鉴权
        if (STSConfig.getInstance().isSTSOn()) {
            STSCredential sTSCredential = getSTSCredential();
            accessKey = sTSCredential.accessKeyId;
            secretKey = sTSCredential.accessKeySecret;
            newHeaders.add("Spas-SecurityToken");
            newHeaders.add(sTSCredential.securityToken);
        }

        if (StringUtils.isNotEmpty(accessKey) && StringUtils.isNotEmpty(secretKey)) {
            newHeaders.add("Spas-AccessKey");
            newHeaders.add(accessKey);
            List<String> signHeaders = SpasAdapter.getSignHeaders(paramValues, secretKey);
            if (signHeaders != null) {
                newHeaders.addAll(signHeaders);
            }
        }
        return newHeaders;
    }

    private STSCredential getSTSCredential() throws IOException {
        boolean cacheSecurityCredentials = STSConfig.getInstance().isCacheSecurityCredentials();
        if (cacheSecurityCredentials && sTSCredential != null) {
            long currentTime = System.currentTimeMillis();
            long expirationTime = sTSCredential.expiration.getTime();
            int timeToRefreshInMillisecond = STSConfig.getInstance().getTimeToRefreshInMillisecond();
            if (expirationTime - currentTime > timeToRefreshInMillisecond) {
                return sTSCredential;
            }
        }
        String stsResponse = getSTSResponse();
        STSCredential stsCredentialTmp = JacksonUtils.toObj(stsResponse, new TypeReference<STSCredential>() {
            });
        sTSCredential = stsCredentialTmp;
        LOGGER.info("[getSTSCredential] code:{}, accessKeyId:{}, lastUpdated:{}, expiration:{}", sTSCredential.getCode(),
            sTSCredential.getAccessKeyId(), sTSCredential.getLastUpdated(), sTSCredential.getExpiration());
        return sTSCredential;
    }

    private static String getSTSResponse() throws IOException {
        String securityCredentials = STSConfig.getInstance().getSecurityCredentials();
        if (securityCredentials != null) {
            return securityCredentials;
        }
        String securityCredentialsUrl = STSConfig.getInstance().getSecurityCredentialsUrl();
        HttpURLConnection conn = null;
        int respCode;
        String response;
        try {
            conn = (HttpURLConnection) new URL(securityCredentialsUrl).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(ParamUtil.getConnectTimeout() > 100 ? ParamUtil.getConnectTimeout() : 100);
            conn.setReadTimeout(1000);
            conn.connect();
            respCode = conn.getResponseCode();
            if (HttpURLConnection.HTTP_OK == respCode) {
                response = IoUtils.toString(conn.getInputStream(), Constants.ENCODE);
            } else {
                response = IoUtils.toString(conn.getErrorStream(), Constants.ENCODE);
            }
        } catch (IOException e) {
            LOGGER.error("can not get security credentials", e);
            throw e;
        } finally {
            IoUtils.closeQuietly(conn);
        }
        if (HttpURLConnection.HTTP_OK == respCode) {
            return response;
        }
        LOGGER.error("can not get security credentials, securityCredentialsUrl: {}, responseCode: {}, response: {}",
            securityCredentialsUrl, respCode, response);
        throw new IOException(
            "can not get security credentials, responseCode: " + respCode + ", response: " + response);
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

    @SuppressWarnings("PMD.ClassNamingShouldBeCamelRule")
    private static class STSCredential {
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
            return "STSCredential{" +
                "accessKeyId='" + accessKeyId + '\'' +
                ", accessKeySecret='" + accessKeySecret + '\'' +
                ", expiration=" + expiration +
                ", securityToken='" + securityToken + '\'' +
                ", lastUpdated=" + lastUpdated +
                ", code='" + code + '\'' +
                '}';
        }
    }

    private String accessKey;
    private String secretKey;
    private String encode;
    private int maxRetry = 3;
    private volatile STSCredential sTSCredential;
    final ServerListManager serverListMgr;

}
