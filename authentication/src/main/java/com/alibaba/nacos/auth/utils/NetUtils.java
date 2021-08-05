package com.alibaba.nacos.auth.utils;

import com.alibaba.nacos.auth.model.User;

import java.util.Map;

/**
 * get User auth information from request
 * 定义原因：1. 从request中获取用户鉴权信息，无论它是从header或者body部分获取，主要返回包含用户鉴权信息的User即可
 *         2. login登录完成后，需要返回给client一个鉴权信息（如token）或者失败信息
 */
public interface NetUtils {
    
    /**
     * get User auth information from request
     * @param request
     * @return User
     */
    User resolveRequest(Object request);
    
    
    /**
     * if login success, response message to client
     * 作用：如果登录成功，则将token之类的信息返回给客户端。由于不知道用户是否还会返回其他信息，故用Map封装。
     * @param successMessage
     * 它创建一个response并返回一个response给客户端
     */
    void responseLoginSuccess(Map<String, String> successMessage);
    
    /**
     * if login fail, response fial message to client
     * 作用：如果登录失败，则返回失败信息。由于不知道用户是否还会返回其他信息，故用Map封装。
     * @param failMessage
     * 它创建一个response并返回一个response给客户端
     */
    void responseLoginFail(Map<String,  String> failMessage);
    
    
}
