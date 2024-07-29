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
import com.alibaba.nacos.client.address.manager.ConfigServerListManager;
import com.alibaba.nacos.client.config.impl.ConfigHttpClientManager;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.client.utils.ContextPathUtil;
import com.alibaba.nacos.client.utils.LogUtils;
import com.alibaba.nacos.client.utils.ParamUtil;
import com.alibaba.nacos.common.http.HttpClientConfig;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.MediaType;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.model.RequestHttpEntity;
import com.alibaba.nacos.common.utils.ExceptionUtil;
import com.alibaba.nacos.common.utils.HttpMethod;
import org.slf4j.Logger;

import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.util.Map;
import java.util.Properties;
import java.util.function.Function;

/**
 * Server Agent.
 *
 * @author water.lyl
 */
public class ServerHttpAgent implements HttpAgent {
    
    private static final Logger LOGGER = LogUtils.logger(ServerHttpAgent.class);
    
    private NacosRestTemplate nacosRestTemplate = ConfigHttpClientManager.getInstance().getNacosRestTemplate();
    
    private String encode;
    
    private int maxRetry = 3;
    
    final ConfigServerListManager serverListManager;
    
    @Override
    public HttpRestResult<String> httpGet(String path, Map<String, String> headers, Map<String, String> paramValues,
            String encode, long readTimeoutMs) throws Exception {
        Function<HttpClientConfig, RequestHttpEntity> requestEntityCreator = config -> new RequestHttpEntity(config,
                createHeader(headers), createQuery(paramValues));
        return request(HttpMethod.GET, path, requestEntityCreator, readTimeoutMs);
    }
    
    @Override
    public HttpRestResult<String> httpPost(String path, Map<String, String> headers, Map<String, String> paramValues,
            String encode, long readTimeoutMs) throws Exception {
        Function<HttpClientConfig, RequestHttpEntity> requestEntityCreator = config -> {
            Header header = createHeader(headers);
            header.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            return new RequestHttpEntity(config, header, paramValues);
        };
        return request(HttpMethod.POST, path, requestEntityCreator, readTimeoutMs);
    }
    
    @Override
    public HttpRestResult<String> httpDelete(String path, Map<String, String> headers, Map<String, String> paramValues,
            String encode, long readTimeoutMs) throws Exception {
        Function<HttpClientConfig, RequestHttpEntity> requestEntityCreator = config -> new RequestHttpEntity(config,
                createHeader(headers), createQuery(paramValues));
        return request(HttpMethod.DELETE, path, requestEntityCreator, readTimeoutMs);
    }
    
    private HttpRestResult<String> request(String method, String path,
            Function<HttpClientConfig, RequestHttpEntity> requestEntityCreator, long readTimeoutMs) throws Exception {
        final long endTime = System.currentTimeMillis() + readTimeoutMs;
        String currentServer = serverListManager.getCurrentServer();
        int maxRetry = this.maxRetry;
        HttpClientConfig config = HttpClientConfig.builder()
                .setReadTimeOutMillis(Long.valueOf(readTimeoutMs / maxRetry).intValue())
                .setConTimeOutMillis(ConfigHttpClientManager.getInstance().getConnectTimeoutOrDefault(1000)).build();
        do {
            try {
                RequestHttpEntity requestEntity = requestEntityCreator.apply(config);
                HttpRestResult<String> result = nacosRestTemplate.execute(getUrl(currentServer, path), method,
                        requestEntity, String.class);
                if (isFail(result)) {
                    LOGGER.error("[NACOS ConnectException {}] currentServerAddr: {}, httpCode: {}", currentServer,
                            method, result.getCode());
                } else {
                    return result;
                }
            } catch (ConnectException | SocketTimeoutException e) {
                LOGGER.error("[NACOS {} {}] currentServerAddr: {}, err : {}", method, e.getClass().getSimpleName(),
                        currentServer, ExceptionUtil.getStackTrace(e));
            } catch (Exception e) {
                LOGGER.error("[NACOS {} Exception] currentServerAddr: {}", currentServer, method, e);
                throw e;
            }
            maxRetry--;
            if (maxRetry < 0) {
                String message = String.format(
                        "[NACOS HTTP-%s] The maximum number of tolerable server reconnection errors has been reached",
                        method);
                throw new ConnectException(message);
            }
            currentServer = serverListManager.getNextServer();
        } while (System.currentTimeMillis() <= endTime);
        String message = String.format("no available server, currentServerAddr: %s", currentServer);
        LOGGER.error(message);
        throw new ConnectException(message);
    }
    
    private String getUrl(String serverAddr, String relativePath) {
        return serverAddr + ContextPathUtil.normalizeContextPath(serverListManager.getContentPath()) + relativePath;
    }
    
    private Header createHeader(Map<String, String> headers) {
        Header header = Header.newInstance();
        header.addAll(headers);
        return header;
    }
    
    private Query createQuery(Map<String, String> paramValues) {
        return Query.newInstance().initParams(paramValues);
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
    
    public ServerHttpAgent(ConfigServerListManager serverListManager) {
        this.serverListManager = serverListManager;
    }
    
    public ServerHttpAgent(ConfigServerListManager serverListManager, Properties properties) {
        this.serverListManager = serverListManager;
    }
    
    public ServerHttpAgent(Properties properties) throws NacosException {
        this.serverListManager = new ConfigServerListManager(NacosClientProperties.PROTOTYPE.derive(properties));
    }
    
    @Override
    public void start() throws NacosException {
    
    }
    
    @Override
    public String getName() {
        return serverListManager.getName();
    }
    
    @Override
    public String getNamespace() {
        return serverListManager.getNamespace();
    }
    
    @Override
    public String getTenant() {
        return serverListManager.getNamespace();
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
        serverListManager.shutdown();
        LOGGER.info("{} do shutdown stop", className);
    }
}
