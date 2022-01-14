package com.alibaba.nacos.console.utils;

import com.alibaba.nacos.sys.env.EnvUtil;

import java.util.List;

/**
 * @author kicey
 */
public class OidcUtil {

    /**
     * OIDC key used in configuration file
     */
    private static final String OIDPS = "oidps";

    private static final String OIDP = "oidp";

    private static final String AUTH_URL = "authUrl";

    private static final String EXCHANGE_TOKEN_URL = "exchangeTokenUrl";

    private static final String UserInfoUrl = "userInfoUrl";

    private static final String CLIENT_ID = "clientId";

    private static final String CLIENT_SECRET = "clientSecret";

    private static final String SCOPE = "scope";

    public static List<String> getOidpList(){
        return EnvUtil.getPropertyList(OIDPS);
    }

    public static String getAuthUrl(String oidp){
        return EnvUtil.getProperty(String.format("%s.%s.%s", OIDP, oidp, AUTH_URL));
    }

    public static String getTokenExchangeUrl(String oidp){
        return EnvUtil.getProperty(String.format("%s.%s.%s", OIDP, oidp, EXCHANGE_TOKEN_URL));
    }

    public static String getUserInfoUrl(String oidp){
        return EnvUtil.getProperty(String.format("%s.%s.%s", OIDP, oidp, UserInfoUrl));
    }

    public static String getClientId(String oidp){
        return EnvUtil.getProperty(String.format("%s.%s.%s", OIDP, oidp, CLIENT_ID));
    }

    public static String getClientSecret(String oidp){
        return EnvUtil.getProperty(String.format("%s.%s.%s", OIDP, oidp, CLIENT_SECRET));
    }

    public static List<String> getScopes(String oidp){
        return EnvUtil.getProperty(String.format("%s.%s.%s", OIDP, oidp, SCOPE), List.class);
    }

    public static String getJsonpath(String oidp){
        return EnvUtil.getProperty("oidp."+oidp+".jsonpath");
    }
}
