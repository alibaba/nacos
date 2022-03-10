/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.auth.impl.utils;

import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

/**
 * Util to get config about the OIDC.
 *
 * @author kicey
 */
public class OidcUtil {
    
    private static final String PREFIX = "nacos.core.auth.oidc-idp";
    
    private static final String EXPOSED_HOST = "nacos.core.auth.oidc-exposed-host";
    
    /**
     * IDP-Specified OIDC configuration keys.
     */
    private static final String NAME = "name";
    
    private static final String AUTH_URL = "auth-url";
    
    private static final String EXCHANGE_TOKEN_URL = "exchange-token-url";
    
    private static final String USER_INFO_URL = "userinfo-url";
    
    private static final String USERNAME_JSON_PATH = "username-json-path";
    
    private static final String CLIENT_ID = "client-id";
    
    private static final String CLIENT_SECRET = "client-secret";
    
    private static final String SCOPE = "scope";
    
    /**
     * Callback handling endpoint path.
     */
    public static final String CALLBACK_PATH = "/nacos/v1/auth/oidc/callback";
    
    public static final String STATE_RANDOM_COOKIE_NAME = "state_random";
    
    public static final int STATE_RANDOM_COOKIE_MAX_AGE = 60;
    
    public static String getExposedHost() {
        return EnvUtil.getProperty(EXPOSED_HOST);
    }
    
    public static List<String> getOidpList() {
        return EnvUtil.getProperty(PREFIX, List.class);
    }
    
    public static String getAuthUrl(String oidp) {
        return EnvUtil.getProperty(getIdpCfgKey(oidp, AUTH_URL));
    }
    
    public static String getTokenExchangeUrl(String oidp) {
        return EnvUtil.getProperty(getIdpCfgKey(oidp, EXCHANGE_TOKEN_URL));
    }
    
    public static String getUserInfoUrl(String oidp) {
        return EnvUtil.getProperty(getIdpCfgKey(oidp, USER_INFO_URL));
    }
    
    public static String getClientId(String oidp) {
        return EnvUtil.getProperty(getIdpCfgKey(oidp, CLIENT_ID));
    }
    
    public static String getClientSecret(String oidp) {
        return EnvUtil.getProperty(getIdpCfgKey(oidp, CLIENT_SECRET));
    }
    
    public static String getName(String oidp) {
        return EnvUtil.getProperty(getIdpCfgKey(oidp, NAME));
    }
    
    public static List<String> getScopes(String oidp) {
        return EnvUtil.getProperty(getIdpCfgKey(oidp, SCOPE), List.class);
    }
    
    public static String getJsonpath(String oidp) {
        return EnvUtil.getProperty(getIdpCfgKey(oidp, USERNAME_JSON_PATH));
    }
    
    /**
     * add Accept: application/json, Content-Type:application/x-www-form-urlencoded to header.
     *
     * @return header with Accept and Content-Type
     */
    public static Header getExchangeTokenHeader() {
        Header tokenHeader = Header.newInstance();
        tokenHeader.addParam("Accept", "application/json");
        tokenHeader.addParam("Content-Type", "application/x-www-form-urlencoded");
        return tokenHeader;
    }
    
    public static Header getHeaderWithAccessToken(String accessToken) {
        Header header = Header.newInstance();
        header.addParam("Authorization", String.format("Bearer %s", accessToken));
        return header;
    }
    
    /**
     * get url as {@link String}encoded from config.
     *
     * @param oidp the key of the oidp
     * @return url as {@link String}
     */
    public static String getCompletedExchangeTokenUrl(String oidp) {
        String rawExchangeTokenUrl = getTokenExchangeUrl(oidp);
        UriComponentsBuilder tokenUriBuilder = UriComponentsBuilder.fromHttpUrl(rawExchangeTokenUrl);
        return tokenUriBuilder.encode().toUriString();
    }
    
    public static String getCompletedUserinfoUrl(String oidp) {
        String rawUserinfoUrl = getUserInfoUrl(oidp);
        UriComponentsBuilder userinfoUriBuilder = UriComponentsBuilder.fromHttpUrl(rawUserinfoUrl);
        return userinfoUriBuilder.encode().toUriString();
    }
    
    private static String getIdpCfgKey(String name, String key) {
        return String.format("%s.%s.%s", PREFIX, name, key);
    }
}
