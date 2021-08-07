package com.alibaba.nacos.auth.model;


/**
 * UserInfo for authentication
 * 该接口用于获取用户鉴权信息（从request获取或者nacos服务器端获取）
 * 为什么要定义接口：1. 精准的获取request鉴权信息（而不是将所有信息打包为map
 *                2. 鉴权信息可能不只是有username和password，有可能还有group、role等
 */
public interface User {
    /**
     * Unique string representing user.
     * 注：这里的username可以代表用户名、用户id等可以标记用户的字段
     */
    String userName = null;
    
    /**
     * login password
     */
    String password = null;
    
    /**
     * private
     * @param userName
     */
    
    void setUserName(String userName);
    void setPassword(String password);
    
    String getUserName();
    String getPassword();
    
}
