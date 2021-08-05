package com.alibaba.nacos.auth.utils;

import com.alibaba.nacos.auth.model.User;
import com.alibaba.nacos.auth.model.UserFromRequest;

import java.util.Map;

public class HttpNet implements NetUtils {
    /**
     * get User auth information from Http request
     * 从request中提取鉴权相关的信息（如username、password、token等）并放入User中返回
     */
    @Override
    public UserFromRequest resolveRequest(Object request){
        // HttpServletRequest req = (HttpServletRequest) request;
        return null;
    }
    
    @Override
    public void responseLoginSuccess(Map<String,  String> successMessage) {
    
    }
    
    @Override
    public void responseLoginFail(Map<String,  String> failMessage) {
    
    }
    
}
