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

package com.alibaba.nacos.client.security;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.client.utils.ContextPathUtil;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
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
 * Security proxy to update security information.
 *
 * @author nkorange
 * @since 1.2.0
 */
public class SecurityProxy {
    
    private static final Logger SECURITY_LOGGER = LoggerFactory.getLogger(SecurityProxy.class);
    
    private static final String LOGIN_URL = "/v1/auth/users/login";
    
    private final NacosRestTemplate nacosRestTemplate;
    
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
    
    /**
     * Construct from properties, keeping flexibility.
     *
     * @param properties a bunch of properties to read
     */
    public SecurityProxy(Properties properties, NacosRestTemplate nacosRestTemplate) {
        username = properties.getProperty(PropertyKeyConst.USERNAME, StringUtils.EMPTY);
        password = properties.getProperty(PropertyKeyConst.PASSWORD, StringUtils.EMPTY);
        contextPath = ContextPathUtil
                .normalizeContextPath(properties.getProperty(PropertyKeyConst.CONTEXT_PATH, webContext));
        this.nacosRestTemplate = nacosRestTemplate;
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
                if (login(server)) {
                    lastRefreshTime = System.currentTimeMillis();
                    return true;
                }
            }
        } catch (Throwable throwable) {
            SECURITY_LOGGER.warn("[SecurityProxy] login failed, error: ", throwable);
        }
        
        return false;
    }
    
    /**
     * Login to server.
     *
     * @param server server address
     * @return true if login successfully
     */
    public boolean login(String server) {
        
        if (StringUtils.isNotBlank(username)) {
            Map<String, String> params = new HashMap<String, String>(2);
            Map<String, String> bodyMap = new HashMap<String, String>(2);
            params.put(PropertyKeyConst.USERNAME, username);
            bodyMap.put(PropertyKeyConst.PASSWORD, password);
            String url = HTTP + server + contextPath + LOGIN_URL;
            
            if (server.contains(Constants.HTTP_PREFIX)) {
                url = server + contextPath + LOGIN_URL;
            }
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
                }
            } catch (Exception e) {
                SECURITY_LOGGER.error("[SecurityProxy] login http request failed"
                        + " url: {}, params: {}, bodyMap: {}, errorMsg: {}", url, params, bodyMap, e.getMessage());
                return false;
            }
        }
        return true;
    }
    
    public String getAccessToken() {
        return accessToken;
    }
    
    public boolean isEnabled() {
        return StringUtils.isNotBlank(this.username);
    }
}
