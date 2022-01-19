package com.alibaba.nacos.console.utils;

import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Util to get config about the OIDC.
 *
 * @author kicey
 */
public class OidcUtil {
    
    /**
     * OIDC keys used in configuration file.
     */
    private static final String DOMAIN = "domain";
    
    private static final String OIDPS = "oidps";
    
    private static final String OIDP = "oidp";
    
    private static final String NAME = "name";
    
    private static final String AUTH_URL = "authUrl";
    
    private static final String EXCHANGE_TOKEN_URL = "exchangeTokenUrl";
    
    private static final String USER_INFO_URL = "userInfoUrl";
    
    private static final String CLIENT_ID = "clientId";
    
    private static final String CLIENT_SECRET = "clientSecret";
    
    private static final String SCOPES = "scopes";
    
    public static String getDomain() {
        return EnvUtil.getProperty(String.format("%s.%s", OIDP, DOMAIN));
    }
    
    public static String getOidcCallbackUrl() {
        return String.format("http://%s:%d/nacos/v1/auth/oidc/callback", getDomain(), EnvUtil.getPort());
    }
    
    public static List<String> getOidpList() {
        return EnvUtil.getProperty(OIDPS, List.class);
    }
    
    public static String getAuthUrl(String oidp) {
        return EnvUtil.getProperty(String.format("%s.%s.%s", OIDP, oidp, AUTH_URL));
    }
    
    public static String getTokenExchangeUrl(String oidp) {
        return EnvUtil.getProperty(String.format("%s.%s.%s", OIDP, oidp, EXCHANGE_TOKEN_URL));
    }
    
    public static String getUserInfoUrl(String oidp) {
        return EnvUtil.getProperty(String.format("%s.%s.%s", OIDP, oidp, USER_INFO_URL));
    }
    
    public static String getClientId(String oidp) {
        return EnvUtil.getProperty(String.format("%s.%s.%s", OIDP, oidp, CLIENT_ID));
    }
    
    public static String getClientSecret(String oidp) {
        return EnvUtil.getProperty(String.format("%s.%s.%s", OIDP, oidp, CLIENT_SECRET));
    }
    
    public static String getName(String oidp) {
        return EnvUtil.getProperty(String.format("%s.%s.%s", OIDP, oidp, NAME));
    }
    
    public static List<String> getScopes(String oidp) {
        return EnvUtil.getProperty(String.format("%s.%s.%s", OIDP, oidp, SCOPES), List.class);
    }
    
    public static String getJsonpath(String oidp) {
        return EnvUtil.getProperty("oidp." + oidp + ".jsonpath");
    }
    
    public static Map<String, String> getExchangeTokenParams(String oidp, String code) {
        Map<String, String> params = new HashMap<>(16);
        params.put("grant_type", "authorization_code");
        params.put("client_id", OidcUtil.getClientId(oidp));
        params.put("client_secret", OidcUtil.getClientSecret(oidp));
        params.put("code", code);
        params.put("redirect_uri", getOidcCallbackUrl());
        return params;
    }
    
    public static Header getExchangeTokenHeader() {
        Header tokenHeader = Header.newInstance();
        tokenHeader.addParam("Accept", "application/json");
        tokenHeader.addParam("Content-Type", "application/x-www-form-urlencoded");
        return tokenHeader;
    }
    
    public static String getCompletedExchangeTokenUrl(String oidp) {
        String rawExchangeTokenUrl = getTokenExchangeUrl(oidp);
        UriComponentsBuilder tokenUriBuilder = UriComponentsBuilder.fromHttpUrl(rawExchangeTokenUrl);
        return tokenUriBuilder.encode().toUriString();
    }
}
