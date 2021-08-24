package com.alibaba.nacos.client.auth;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.response.ResponseCode;
import com.alibaba.nacos.client.naming.core.ServerListManager;
import com.alibaba.nacos.client.security.SecurityProxy;
import com.alibaba.nacos.client.utils.ContextPathUtil;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.remote.client.RpcClient;
import com.alibaba.nacos.common.remote.client.ServerListFactory;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static com.alibaba.nacos.client.naming.utils.UtilAndComs.HTTP;
import static com.alibaba.nacos.client.naming.utils.UtilAndComs.webContext;

/**
 * a ClientAuthService implement.
 *
 * @author wuyfee
 */

public class NacosClientAuthServiceImpl implements ClientAuthService {
    
    private static final Logger SECURITY_LOGGER = LoggerFactory.getLogger(SecurityProxy.class);
    
    private static final String LOGIN_URL = "/v1/auth/users/login";
    
    private NacosRestTemplate nacosRestTemplate;
    
    private ServerListManager serverListManager;
    
    private RpcClient rpcClient;
    
    private static final String CLIENTAUTHSERVICENAME = "NacosClientAuthServiceImpl";
    
    private final String contextPath;
    
    /**
     * User's name.
     */
    private final String username;
    
    /**
     * User's password.
     */
    private final String password;
    
    /**
     * A token to take with when sending request to Nacos server.
     */
    private volatile String accessToken;
    
    /**
     * TTL of token in seconds.
     */
    private long tokenTtl;
    
    /**
     * Last timestamp refresh security info from server.
     */
    private long lastRefreshTime;
    
    /**
     * time window to refresh security info in seconds.
     */
    private long tokenRefreshWindow;
    
    public NacosClientAuthServiceImpl(Properties properties) {
        username = properties.getProperty(PropertyKeyConst.USERNAME, StringUtils.EMPTY);
        password = properties.getProperty(PropertyKeyConst.PASSWORD, StringUtils.EMPTY);
        contextPath = ContextPathUtil
                .normalizeContextPath(properties.getProperty(PropertyKeyConst.CONTEXT_PATH, webContext));
    }
    
    public void initHttpService(NacosRestTemplate nacosRestTemplate, ServerListManager serverListManager) {
        this.nacosRestTemplate = nacosRestTemplate;
        this.serverListManager = serverListManager;
    }
    
    public void initGrpcService(ServerListFactory serverListFactory) throws NacosException {
        rpcClient.serverListFactory(serverListFactory);
        rpcClient.start();
    }
    
    @Override
    public boolean login(Properties properties) {
        try {
            if ((System.currentTimeMillis() - lastRefreshTime) < TimeUnit.SECONDS
                    .toMillis(tokenTtl - tokenRefreshWindow)) {
                return true;
            }
            List<String> serverList = serverListManager.getServerList();
            for (String server : serverList) {
                if (StringUtils.isNotBlank(username)) {
                    String url = HTTP + server + contextPath + LOGIN_URL;
                    
                    if (server.contains(Constants.HTTP_PREFIX)) {
                        url = server + contextPath + LOGIN_URL;
                    }
                    properties.setProperty("URL", url);
                    properties.setProperty("REQUEST_TYPE", "HTTP");
                    restRequest(properties);
                }
            }
        } catch (Throwable throwable) {
            SECURITY_LOGGER.warn("[SecurityProxy] login failed, error: ", throwable);
        }
        return false;
    }
    
    @Override
    public boolean restRequest(Properties properties) throws NacosException {
        String requestType = properties.getProperty("REQUEST_TYPE", StringUtils.EMPTY);
        
        if ("HTTP".equals(requestType)) {
            return restRequestHttp(properties);
        } else if ("GRPC".equals(requestType)) {
            return restRequestGrpc(properties);
        } else {
            SECURITY_LOGGER.error("[NacosClientAuthService] request type not set");
            return false;
        }
    }
    
    /**
     * get accessToken by HTTP.
     * @param properties login information.
     * @return boolean login success or fail.
     */
    public boolean restRequestHttp(Properties properties) {
        String url = properties.getProperty("URL");
        Map<String, String> params = new HashMap<String, String>(2);
        Map<String, String> bodyMap = new HashMap<String, String>(2);
        params.put(PropertyKeyConst.USERNAME, username);
        bodyMap.put(PropertyKeyConst.PASSWORD, password);
        try {
            HttpRestResult<String> restResult = nacosRestTemplate
                    .postForm(url, Header.EMPTY, Query.newInstance().initParams(params), bodyMap, String.class);
            if (!restResult.ok()) {
                SECURITY_LOGGER.error("login failed: {}", JacksonUtils.toJson(restResult));
                return false;
            }
            JsonNode obj = JacksonUtils.toObj(restResult.getData());
            if (obj.has(Constants.ACCESS_TOKEN)) {
                accessToken = obj.get(Constants.ACCESS_TOKEN).asText();
                tokenTtl = obj.get(Constants.TOKEN_TTL).asInt();
                tokenRefreshWindow = tokenTtl / 10;
            } else {
                SECURITY_LOGGER.info("[NacosClientAuthService] ACCESS_TOKEN is empty from response");
            }
            return true;
        } catch (Exception e) {
            SECURITY_LOGGER.error("[ NacosClientAuthService] login http request failed"
                    + " url: {}, params: {}, bodyMap: {}, errorMsg: {}", url, params, bodyMap, e.getMessage());
            return false;
        }
    }
    
    /**
     * get token by GRPC.
     * @param properties login information.
     * @return boolean login success or fail.
     * @throws NacosException response error code.
     */
    public boolean restRequestGrpc(Properties properties) throws NacosException {
        try {
            Request request = new AuthGrpcRequest(username, password);
            AuthGrpcResponse response = (AuthGrpcResponse) rpcClient.request(request);
            if (ResponseCode.SUCCESS.getCode() != response.getResultCode()) {
                throw new NacosException(response.getErrorCode(), response.getMessage());
            }
            if (StringUtils.isNotBlank(response.getAccessToken())) {
                accessToken = response.getAccessToken();
                tokenTtl = response.getTokenTtl();
                tokenRefreshWindow = tokenTtl / 10;
            } else {
                SECURITY_LOGGER.info("[NacosClientAuthService] ACCESS_TOKEN is empty from response");
            }
            return true;
        } catch (Exception e) {
            SECURITY_LOGGER
                    .error("[ NacosClientAuthService] login grpc request failed" + "errorMsg: {}", e.getMessage());
            return false;
        }
    }
    
    @Override
    public String getClientAuthServiceName() {
        return CLIENTAUTHSERVICENAME;
    }
    
    public String getAccessToken() {
        return accessToken;
    }
    
    public boolean isEnabled() {
        return StringUtils.isNotBlank(this.username);
    }
    
}
