package com.alibaba.nacos.plugin.control.tps.response;

/**
 * tps request.
 */
public class TpsCheckResponse {
    
    private boolean success;
    
    private int code;
    
    private String message;
    
    public TpsCheckResponse(boolean success, int code, String message) {
        this.success = success;
        this.code = code;
        this.message = message;
    }
    
    public int getCode() {
        return code;
    }
    
    public void setCode(int code) {
        this.code = code;
    }
    
    public void setSuccess(boolean success) {
        this.success = success;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public String getMessage() {
        return message;
    }
}
