package com.alibaba.nacos.auth;

public class AuthConfig {
    /**
     * AUTHORIZATION_REGION: HEADER,PARAMETER,HEADER_AND_PARAMETER.
     */
    public static final String AUTHORIZATION_REGION = "HEADER";
    
    /**
     * Authority key set.
     */
    public static final String[] AUTHORITY_KEY = {"Authorization"};
    
    
}
