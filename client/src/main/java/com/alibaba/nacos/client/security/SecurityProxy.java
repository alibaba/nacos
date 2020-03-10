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


import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.client.config.impl.SpasAdapter;
import com.alibaba.nacos.client.naming.net.HttpClient;
import com.alibaba.nacos.client.naming.utils.SignUtil;
import com.alibaba.nacos.client.utils.AppNameUtils;
import com.alibaba.nacos.client.utils.TemplateUtils;
import com.alibaba.nacos.common.utils.HttpMethod;
import org.apache.commons.codec.Charsets;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.HttpURLConnection;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import static com.alibaba.nacos.client.utils.LogUtils.NAMING_LOGGER;

/**
 * Security proxy to update security information
 *
 * @author nkorange
 * @since 1.2.0
 */
public class SecurityProxy {

    private static final Logger SECURITY_LOGGER = LoggerFactory.getLogger(SecurityProxy.class);

    private static final String LOGIN_URL = "/v1/auth/users/login";

    private String contextPath;

    /**
     * User's name
     */
    private String username;

    /**
     * User's password
     */
    private String password;

    /**
     * A token to take with when sending request to Nacos server
     */
    private String accessToken;

    /**
     * TTL of token in seconds
     */
    private long tokenTtl;

    /**
     * Last timestamp refresh security info from server
     */
    private long lastRefreshTime;

    /**
     * time window to refresh security info in seconds
     */
    private long tokenRefreshWindow;

    private Properties properties;

    /**
     * Construct from properties, keeping flexibility
     *
     * @param properties a bunch of properties to read
     */
    public SecurityProxy(Properties properties) {
        this.properties = properties;
        username = properties.getProperty(PropertyKeyConst.USERNAME, StringUtils.EMPTY);
        password = properties.getProperty(PropertyKeyConst.PASSWORD, StringUtils.EMPTY);
        contextPath = properties.getProperty(PropertyKeyConst.CONTEXT_PATH, "/nacos");
    }

    public boolean login(List<String> servers) {

        try {
            if ((System.currentTimeMillis() - lastRefreshTime) < TimeUnit.SECONDS.toMillis(tokenTtl - tokenRefreshWindow)) {
                return true;
            }

            for (String server : servers) {
                if (login(server)) {
                    lastRefreshTime = System.currentTimeMillis();
                    return true;
                }
            }
        } catch (Throwable t) {
        }

        return false;
    }

    public boolean login(String server) {

        if (StringUtils.isNotBlank(username)) {
            Map<String, String> params = new HashMap<String, String>(2);
            params.put("username", username);
            String body = "password=" + password;
            String url = "http://" + server + contextPath + LOGIN_URL;

            if (server.contains(Constants.HTTP_PREFIX)) {
                url = server + contextPath + LOGIN_URL;
            }

            HttpClient.HttpResult result = HttpClient.request(url, new ArrayList<String>(2),
                params, body, Charsets.UTF_8.name(), HttpMethod.POST);

            if (result.code != HttpURLConnection.HTTP_OK) {
                SECURITY_LOGGER.error("login failed: {}", JSON.toJSONString(result));
                return false;
            }

            JSONObject obj = JSON.parseObject(result.content);
            if (obj.containsKey(Constants.ACCESS_TOKEN)) {
                accessToken = obj.getString(Constants.ACCESS_TOKEN);
                tokenTtl = obj.getIntValue(Constants.TOKEN_TTL);
                tokenRefreshWindow = tokenTtl / 10;
            }
        }
        return true;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public Map<String, String> getSecurityInfo() {

        Map<String, String> params = new HashMap<String, String>(16);
        // Inject token if exist:
        if (StringUtils.isNotBlank(getAccessToken())) {
            params.put(Constants.ACCESS_TOKEN, getAccessToken());
        }

        // Inject ak/sk if exist:
        String ak = getAccessKey();
        String sk = getSecretKey();
        params.put("app", AppNameUtils.getAppName());
        if (StringUtils.isNotBlank(ak) && StringUtils.isNotBlank(sk)) {
            try {
                String signData = getSignData(params.get("serviceName"));
                String signature = SignUtil.sign(signData, sk);
                params.put("signature", signature);
                params.put("data", signData);
                params.put("ak", ak);
            } catch (Exception e) {
                NAMING_LOGGER.error("inject ak/sk failed.", e);
            }
        }

        return params;
    }

    private static String getSignData(String serviceName) {
        return StringUtils.isNotEmpty(serviceName)
            ? System.currentTimeMillis() + "@@" + serviceName
            : String.valueOf(System.currentTimeMillis());
    }

    public String getAccessKey() {
        if (properties == null) {

            return SpasAdapter.getAk();
        }

        return TemplateUtils.stringEmptyAndThenExecute(properties.getProperty(PropertyKeyConst.ACCESS_KEY), new Callable<String>() {

            @Override
            public String call() {
                return SpasAdapter.getAk();
            }
        });
    }

    public String getSecretKey() {
        if (properties == null) {

            return SpasAdapter.getSk();
        }

        return TemplateUtils.stringEmptyAndThenExecute(properties.getProperty(PropertyKeyConst.SECRET_KEY), new Callable<String>() {
            @Override
            public String call() throws Exception {
                return SpasAdapter.getSk();
            }
        });
    }

}
