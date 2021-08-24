package com.alibaba.nacos.client.auth;

import com.alibaba.nacos.api.remote.request.Request;

public class AuthGrpcRequest extends Request {
    
    private static final String MODULE = "getAuthToken";
    
    private String username;
    
    private String password;
    
    public AuthGrpcRequest() {
    
    }
    
    public AuthGrpcRequest(String username, String password) {
        this.username = username;
        this.password = password;
    }
    
    public String getUsername() {
        return username;
    }
    
    public String getPassword() {
        return password;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }
    
    public void setPassword(String password) {
        this.password = password;
    }
    
    @Override
    public String getModule() {
        return MODULE;
    }
}
