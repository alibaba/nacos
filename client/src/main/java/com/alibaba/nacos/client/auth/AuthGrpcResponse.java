package com.alibaba.nacos.client.auth;

import com.alibaba.nacos.api.remote.response.Response;

public class AuthGrpcResponse extends Response {
    
    /**
     * A token to take with when sending request to Nacos server.
     */
    private String accessToken;
    
    /**
     * TTL of token in seconds.
     */
    private long tokenTtl;
    
    public AuthGrpcResponse() {
    
    }
    
    public AuthGrpcResponse(String accessToken, long tokenTtl) {
        this.accessToken = accessToken;
        this.tokenTtl = tokenTtl;
    }
    
    public String getAccessToken() {
        return accessToken;
    }
    
    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }
    
    public long getTokenTtl() {
        return tokenTtl;
    }
    
    public void setTokenTtl(long tokenTtl) {
        this.tokenTtl = tokenTtl;
    }
}
