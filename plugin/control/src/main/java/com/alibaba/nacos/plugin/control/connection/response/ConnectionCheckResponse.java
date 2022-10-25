package com.alibaba.nacos.plugin.control.connection.response;

public class ConnectionCheckResponse {
    
    private boolean success;
    
    private String message;
    
    private ConnectionCheckCode checkCode;
    
    public boolean isSuccess() {
        return success;
    }
    
    public void setSuccess(boolean success) {
        this.success = success;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public ConnectionCheckCode getCheckCode() {
        return checkCode;
    }
    
    public void setCheckCode(ConnectionCheckCode checkCode) {
        this.checkCode = checkCode;
    }
}
