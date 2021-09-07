package com.alibaba.nacos.client.auth;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.client.auth.result.GrpcRequest;
import com.alibaba.nacos.client.auth.result.HttpRequest;
import com.alibaba.nacos.client.auth.result.RequestManager;
import com.alibaba.nacos.client.auth.result.ResultConstant;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.alibaba.nacos.common.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * a ClientAuthService implement.
 *
 * @author wuyfee
 */

public class NacosClientAuthServiceImpl implements ClientAuthService {
    
    private static final Logger SECURITY_LOGGER = LoggerFactory.getLogger(NacosClientAuthServiceImpl.class);
    
    private static final String CLIENTAUTHSERVICENAME = "NacosClientAuthServiceImpl";
    
    private final RequestManager request;
    
    /**
     * User's name.
     */
    private final String username;
    
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
    
    /**
     * user information.
     */
    private final Properties properties;
    
    /**
     * Http instance.
     *
     * @param properties        User information.
     * @param nacosRestTemplate HttpRequest template.
     */
    public NacosClientAuthServiceImpl(Properties properties, NacosRestTemplate nacosRestTemplate) {
        this.properties = properties;
        request = new HttpRequest(nacosRestTemplate);
        username = properties.getProperty(PropertyKeyConst.USERNAME, StringUtils.EMPTY);
    }
    
    /**
     * Grpc instance.
     *
     * @param properties User information.
     */
    public NacosClientAuthServiceImpl(Properties properties) {
        this.properties = properties;
        request = new GrpcRequest();
        username = properties.getProperty(PropertyKeyConst.USERNAME, StringUtils.EMPTY);
    }
    
    /**
     * Login to servers.
     *
     * @param servers server list
     * @return true if login successfully
     */
    public boolean login(List<String> servers) {
        
        try {
            if ((System.currentTimeMillis() - lastRefreshTime) < TimeUnit.SECONDS
                    .toMillis(tokenTtl - tokenRefreshWindow)) {
                return true;
            }
            
            for (String server : servers) {
                properties.setProperty(ResultConstant.SERVER, server);
                if (login(properties)) {
                    lastRefreshTime = System.currentTimeMillis();
                    return true;
                }
            }
        } catch (Throwable throwable) {
            SECURITY_LOGGER.warn("[SecurityProxy] login failed, error: ", throwable);
        }
        
        return false;
    }
    
    @Override
    public boolean login(Properties properties) {
        Map<String, String> map = request.getResponse(properties);
        if (map != null && map.containsKey(ResultConstant.ACCESSTOKEN)) {
            accessToken = map.get(ResultConstant.ACCESSTOKEN);
            tokenTtl = Long.parseLong(map.get(ResultConstant.TOKENTTL));
            tokenRefreshWindow = tokenTtl / 10;
            return true;
        }
        return false;
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
