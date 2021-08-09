package com.alibaba.nacos.auth.utils;

import com.alibaba.nacos.auth.model.User;

import java.util.Map;

public class NetContext {
    private NetUtils requestUtils;
    
    public NetContext(NetUtils requestUtils){
        this.requestUtils = requestUtils;
    }
    public User getReuqestUser(Object request){
        return requestUtils.resolveRequest(request);
    }
    
    public void responseLoginSuccess(Map<String,  String> successMessage) {
        requestUtils.responseLoginSuccess(successMessage);
    }
    
    public void responseLoginFail(Map<String,  String> failMessage) {
        requestUtils.responseLoginFail(failMessage);
    }
}
