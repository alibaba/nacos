package com.alibaba.nacos.auth.model;

/**
 * UserInfo for authentication
 * User接口实现类
 */
//@Data
public class UserFromRequest implements User {
  
    
    /**
     * Unique string representing user.
     */
    private String userName;
    
    /**
     * login password
     */
    private String password;
    
    /**
     * Token in request
     */
    private String authToken;
    
    
    @Override
    public void setUserName(String userName) {
        this.userName = userName;
    }
    @Override
    public void setPassword(String password){ this.password = password; }
    public void setAuthToken(String authToken){this.authToken = authToken;}
    
    
    @Override
    public String getUserName() {
        return userName;
    }
    @Override
    public String getPassword(){ return password; }
    
    
    public String getAuthToken(){return authToken;}
  
    
    
}