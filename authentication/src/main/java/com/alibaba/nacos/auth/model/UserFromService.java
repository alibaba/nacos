package com.alibaba.nacos.auth.model;

public class UserFromService implements User{
    /**
     * Unique string representing user.
     */
    private String userName;
    
    /**
     * user password
     */
    private String password;
    
    /**
     * user token
     */
    private String token;
    
    
    /**
     * private
     * @param userName
     */
    @Override
    public void setUserName(String userName) {
        this.userName = userName;
    }
    @Override
    public void setPassword(String password){ this.password = password; }
    
    
    @Override
    public String getUserName() {
        return userName;
    }
    @Override
    public String getPassword(){ return password; }
    
    public void setToken(String token) {
        this.token = token;
    }
    public String getToken(){return token;}


}
